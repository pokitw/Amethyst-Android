#!/usr/bin/env python3
"""Exercise the crash-diagnosis rules against fixture crash logs.

There is no device and no JVM in CI that runs this code before a user does, so this is the one
real check the rule table gets: it parses the `Rule("id", "pattern", ...)` literals straight out
of CrashDiagnosis.kt — the shipped patterns, not a copy — and asserts that each fixture crash
excerpt is matched by the expected rule and, just as importantly, that a healthy log matches
nothing. The patterns deliberately stay in the regex subset Kotlin and Python share.

Run from the repository root:  python3 scripts/check_crash_rules.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KOTLIN = ROOT / (
    "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/diagnosis/CrashDiagnosis.kt"
)

# (name, expected_rule_id_or_None, log_excerpt)
FIXTURES = [
    ("heap_oom", "memory",
     "java.lang.OutOfMemoryError: Java heap space\n"
     "\tat net.minecraft.class_2818.<init>(class_2818.java:112)\n"),
    ("gc_overhead", "memory",
     "java.lang.OutOfMemoryError: GC overhead limit exceeded\n"),
    ("metaspace_oom", "memory",
     "Caused by: java.lang.OutOfMemoryError: Metaspace\n"),
    ("native_alloc", "memory_native",
     "# There is insufficient memory for the Java Runtime Environment to continue.\n"
     "# Native memory allocation (mmap) failed to map 268435456 bytes\n"),
    ("class_version_61", "java_version",
     "java.lang.UnsupportedClassVersionError: net/minecraft/client/main/Main has been "
     "compiled by a more recent version of the Java Runtime (class file version 61.0), this "
     "version of the Java Runtime only recognizes class file versions up to 52.0\n"),
    ("old_forge_modern_java", "java_too_new",
     "java.lang.IllegalAccessError: class cpw.mods.modlauncher.SecureJarHandler cannot access "
     "class sun.security.util.ManifestEntryVerifier (in module java.base) because module "
     "java.base does not export sun.security.util to unnamed module @0x21bcffb5\n"),
    ("fabric_missing_dep", "fabric_dependency",
     "net.fabricmc.loader.impl.FormattedException: net.fabricmc.loader.impl.discovery."
     "ModResolutionException: Mod resolution encountered an incompatible mod set!\n"
     "A potential solution has been determined:\n"
     "\t - Install fabric-api, any version.\n"
     "Unmet dependency listing:\n"
     "\t - Mod 'Sodium' (sodium) 0.5.8 requires any version of fabric-api, which is missing!\n"),
    ("fabric_wrong_mc", "fabric_dependency",
     "net.fabricmc.loader.impl.FormattedException: Mod resolution encountered an "
     "incompatible mod set!\n"
     "\t - Replace mod 'Iris' (iris) 1.7.0+1.20.4 with version 1.6.4+1.20.1 or later.\n"),
    ("forge_missing_dep", "forge_dependency",
     "Missing or unsupported mandatory dependencies:\n"
     "\tMod ID: 'geckolib', Requested by: 'ars_nouveau', Expected range: '[4.4.2,)'\n"),
    ("duplicate_mods_forge", "duplicate_mods",
     "net.minecraftforge.fml.loading.moddiscovery.validation.errors.DuplicateModsError: "
     "Found duplicate mods:\n"
     "\tMod ID: 'jei' from mod files: jei-15.2.jar, jei-15.3.jar\n"),
    ("duplicate_mods_fabric", "duplicate_mods",
     "net.fabricmc.loader.impl.FormattedException: java.lang.RuntimeException: "
     "Duplicate mod id: sodium (from sodium-0.5.8.jar, sodium-0.5.3.jar)\n"),
    ("mixin_injection", "mod_broken",
     "org.spongepowered.asm.mixin.injection.throwables.InjectionError: Critical injection "
     "failure: @Inject annotation on onRender could not find any targets matching 'render'\n"),
    ("mixin_apply", "mod_broken",
     "org.spongepowered.asm.mixin.throwables.MixinApplyError: Mixin "
     "[sodium.mixins.json:MixinChunkBuilder] FAILED during APPLY\n"),
    ("nosuchmethod_mod", "mod_broken",
     "java.lang.NoSuchMethodError: 'void me.jellysquid.mods.sodium.client.render.chunk."
     "RenderSectionManager.markGraphDirty()'\n"
     "\tat me.jellysquid.mods.lithium.mixin.ai.pathing.PathNodeCache.handler(X.java:520)\n"),
    ("egl_failure", "renderer",
     "eglMakeCurrent: error 0x3009 (EGL_BAD_MATCH)\n"
     "org.lwjgl.LWJGLException: Could not create context\n"),
    ("gl_unsupported", "renderer",
     "Caused by: java.lang.IllegalStateException: The driver does not appear to support "
     "OpenGL\n"),
    ("glfw_init", "renderer",
     "java.lang.IllegalStateException: GLFW error before init: [0x10008]Cannot make GL "
     "context current on this thread\n"),
    ("no_space", "storage",
     "java.io.IOException: No space left on device\n"),
    ("enospc", "storage",
     "java.nio.file.FileSystemException: r.0.0.mca: ENOSPC (No space left on device)\n"),
    ("network_resolve", "network",
     "java.net.UnknownHostException: Unable to resolve host \"piston-meta.mojang.com\"\n"),
    ("network_timeout", "network",
     "java.net.SocketTimeoutException: Connect timed out\n"),
    ("auth_expired", "account",
     "com.mojang.authlib.exceptions.InvalidCredentialsException: Status: 401\n"),
    ("corrupt_jar", "corrupt_install",
     "java.util.zip.ZipException: zip END header not found\n"),
    ("main_class_missing", "corrupt_install",
     "Error: Could not find or load main class net.minecraft.client.main.Main\n"
     "Caused by: java.lang.ClassNotFoundException: net.minecraft.client.main.Main\n"),
    # A healthy session's tail must trip nothing at all.
    ("clean_log", None,
     "[17:23:01] [Render thread/INFO]: Stopping!\n"
     "[17:23:01] [Render thread/INFO]: SoundEngine shut down\n"
     "Session terminated, took 4213 ms\n"),
    # An ordinary multiplayer disconnect is not a crash cause and must never match.
    ("server_disconnect", None,
     "[19:02:44] [Netty Client IO/INFO]: Internal Exception: java.io.IOException: "
     "Connection reset by peer\n"
     "[19:02:44] [Render thread/INFO]: [CHAT] Disconnected\n"),
    # An empty log (fatal signal before anything printed) must also trip nothing.
    ("empty_log", None, ""),
]


def parse_rules(source: str):
    """The Rule("id", "pattern", ...) literals, in declaration order.

    Tolerates // comments between the arguments, so annotating a rule inline can never
    silently drop it from this check.
    """
    rules = []
    sep = r'(?:\s|//[^\n]*)*'
    for match in re.finditer(
        r'Rule\(' + sep + r'"([a-z_0-9]+)",' + sep + r'"((?:[^"\\]|\\.)*)"', source
    ):
        rule_id, kotlin_pattern = match.group(1), match.group(2)
        # Undo Kotlin string escaping: \\ -> \ and \" -> "
        pattern = kotlin_pattern.replace('\\\\', '\\').replace('\\"', '"')
        rules.append((rule_id, pattern))
    return rules


def main() -> int:
    source = KOTLIN.read_text(encoding="utf-8")
    rules = parse_rules(source)
    if len(rules) < 10:
        print(f"FAIL: only parsed {len(rules)} rules out of {KOTLIN} — parser or source drifted")
        return 1

    compiled = []
    for rule_id, pattern in rules:
        try:
            compiled.append((rule_id, re.compile(pattern)))
        except re.error as error:
            print(f"FAIL: rule '{rule_id}' pattern does not compile: {error}")
            return 1

    failures = 0
    for name, expected, log in FIXTURES:
        got = next((rid for rid, rx in compiled if rx.search(log)), None)
        if got != expected:
            print(f"FAIL: fixture '{name}' matched {got!r}, expected {expected!r}")
            failures += 1
    if failures:
        return 1
    print(f"ok: {len(rules)} rules, {len(FIXTURES)} fixtures, first-match order holds")
    return 0


if __name__ == "__main__":
    sys.exit(main())
