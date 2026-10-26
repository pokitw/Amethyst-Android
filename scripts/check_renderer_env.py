#!/usr/bin/env python3
"""Every renderer branch in JREUtils must decide its own LIBGL_ES.

MobileGlues shipped being handed a GL ES 2 context, and a GL ES 1 one on rd/indev versions, which
is a concrete mechanism for "block placing and breaking does not work on rd versions with
MobileGlues". The reason it was invisible is worth encoding rather than remembering:

  * JREUtils puts LIBGL_ES into the env map early, from ExtraCore's OPEN_GL_VERSION, and
    OldVersionsUtils.selectOpenGlVersion sets that to "1" or "2" on every single launch.
  * The generic derivation further down, which would have worked out "3", is guarded on
    `!envMap.containsKey("LIBGL_ES")`. That is already false by then, so it never runs.
  * So a renderer branch that does not set LIBGL_ES itself does not fall through to a sensible
    default. It silently inherits a number chosen for a completely different purpose.

That makes "the branch sets it" the invariant, not "something eventually sets it". A new renderer
added without the line is the same bug again, and nothing else in the build would say so.

Zink is excluded by name and on purpose: it does not go through GL ES at all, so LIBGL_ES is not
its business. Everything whose id starts with "opengles" is a GL ES consumer and must be explicit.

Also checks the AMD FSR 1 picker is gone from all five places it lived, because the value array
was inverted (0,4,3,2,1) and a half removal would have mis-mapped quality levels rather than
failing.
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
JRE = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java"

failures = []
checks = 0


def check(ok, message):
    global checks
    checks += 1
    if not ok:
        failures.append(message)


def main():
    source = JRE.read_text(encoding="utf-8")

    # The early population is what makes the derivation unreachable. If this line is ever removed
    # the invariant below stops being load-bearing, and this check should be reconsidered rather
    # than silently continuing to pass.
    early = 'envMap.put("LIBGL_ES", (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION))'
    check(early in source,
          "JREUtils no longer pre-populates LIBGL_ES from OPEN_GL_VERSION. The derivation block "
          "may now be reachable, so re-read this check's premise before trusting it.")

    guard = '!envMap.containsKey("LIBGL_ES")'
    check(guard in source,
          "the generic LIBGL_ES derivation is no longer guarded on containsKey; if it now "
          "overwrites, the per-branch rule below is the wrong invariant")

    # Only the branches inside setJavaEnvironment build the env map. There is a SECOND
    # `if(LOCAL_RENDERER.equals("opengles_mobileglues"))` in loadGraphicsLibrary, which sets
    # MG_DIR_PATH and POJAVEXEC_EGL into the real process environment just before dlopening the
    # renderer, and it correctly does NOT set LIBGL_ES: setJavaEnvironment has already applied the
    # whole map by then. Parsing the file as a whole conflated the two and reported the fixed
    # branch as broken, which is how that second site was found.
    start = source.index("public static void setJavaEnvironment(")
    end = source.index("public static String loadGraphicsLibrary(")
    check(start < end, "setJavaEnvironment no longer precedes loadGraphicsLibrary in the file")
    env_method = source[start:end]

    pattern = re.compile(
        r'if\s*\(\s*LOCAL_RENDERER\.equals\(\s*"([^"]+)"\s*\)\s*\)\s*\{(.*?)\n(\s*)\}',
        re.S)
    branches = {m.group(1): m.group(2) for m in pattern.finditer(env_method)}
    check(len(branches) >= 3,
          "found only %d renderer branches in JREUtils, expected at least 3; the shape this "
          "check parses has changed" % len(branches))

    for renderer, body in sorted(branches.items()):
        if "zink" in renderer:
            # Zink executes GL through Mesa on Vulkan. LIBGL_ES is meaningless to it, and the
            # branch sets MESA_GL_VERSION_OVERRIDE instead.
            check('LIBGL_ES' not in body,
                  "the %s branch sets LIBGL_ES, which does not apply to a Mesa renderer"
                  % renderer)
            continue
        if not renderer.startswith("opengles"):
            continue
        check('envMap.put("LIBGL_ES"' in body,
              'the "%s" branch does not set LIBGL_ES. It will inherit the value OPEN_GL_VERSION '
              'put there, which is "1" or "2" and chosen for a different reason, and the generic '
              'derivation is already skipped by then. This is the MobileGlues bug.' % renderer)

    # The two values that are deliberate, so a later edit that swaps them is visible.
    mobileglues = branches.get("opengles_mobileglues", "")
    check('envMap.put("LIBGL_ES", "3")' in mobileglues,
          "MobileGlues must ask for a GL ES 3 context: it is a GL to GL ES 3 translator")
    gl4es = branches.get("opengles2", "")
    check('envMap.put("LIBGL_ES", "2")' in gl4es,
          "gl4es must keep asking for 2 explicitly; the comment says it crashes with 1")

    # The ordering that makes a single LIBGL_ES line sufficient. setJavaEnvironment applies the
    # whole env map through Os.setenv, and loadGraphicsLibrary dlopens the renderer afterwards;
    # gl_bridge reads LIBGL_ES later still, when it creates the context. Swap these two calls and
    # MobileGlues is loaded before its context version is decided.
    call_env = source.find("setJavaEnvironment(activity, runtimeHome)")
    call_gfx = source.find("loadGraphicsLibrary()")
    check(call_env != -1 and call_gfx != -1,
          "could not find the launch-path calls to setJavaEnvironment and loadGraphicsLibrary")
    if call_env != -1 and call_gfx != -1:
        check(call_env < call_gfx,
              "loadGraphicsLibrary is now called before setJavaEnvironment, so the renderer is "
              "dlopened before LIBGL_ES is in the environment")
    check('Os.setenv(env.getKey(), env.getValue(), true)' in source,
          "the env map is no longer applied with Os.setenv; LIBGL_ES may not reach the native "
          "side that reads it with getenv")

    # The FSR picker upstream removed for doing nothing and breaking some Adrenos. Five places.
    names = {
        "pref row": (ROOT / "app_pojavlauncher/src/main/res/xml/pref_renderer.xml",
                     "mg_renderer_setting_fsr"),
        "config writer": (ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/"
                                 "LauncherPreferences.java", "fsr1Setting"),
        "title string": (ROOT / "app_pojavlauncher/src/main/res/values/strings.xml",
                         "mg_renderer_title_fsr"),
        "name array": (ROOT / "app_pojavlauncher/src/main/res/values/headings_array.xml",
                       "mg_renderer_names_fsr"),
        "value array": (ROOT / "app_pojavlauncher/src/main/res/values/headings_array.xml",
                        "mg_renderer_values_fsr"),
    }
    for where, (path, token) in names.items():
        text = path.read_text(encoding="utf-8") if path.exists() else ""
        check(token not in text,
              "the FSR %s (%s) is still present in %s. The value array was inverted "
              "(0,4,3,2,1), so a partial removal mis-maps quality levels rather than failing."
              % (where, token, path.name))

    print("%d checks" % checks)
    for failure in failures:
        print("FAIL: " + failure)
    if failures:
        print("%d FAILURES" % len(failures))
        return 1
    print("all passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
