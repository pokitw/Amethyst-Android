package net.kdt.pojavlaunch.clients;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything decided about a Lunar launch before anything is downloaded or started.
 *
 * <p>Plain Java with no Android imports, so {@code scripts/clientsim} compiles the real class and
 * drives it. Each rule here was found by running the real client in a desktop container, and the
 * KDoc says which run found it, because none of them can be re-derived from reading.
 *
 * <p><b>The layout is a home folder of its own</b>, so a client never shares a mods folder, an
 * options file or a library tree with a vanilla profile. Lunar's loader writes to the JVM's
 * {@code user.home} in two places it chooses itself, {@code .lunarclient} and {@code .minecraft},
 * so the only way to keep it out of the launcher's folders is to give it a home that is its own.
 */
public final class ClientPlan {
    private ClientPlan() {}

    // ---- layout ------------------------------------------------------------------------------

    /**
     * Where artifacts and external files go. Lunar's loader reads the external files from
     * {@code ~/.lunarclient/offline/multiver} whatever the working directory is, which a run with
     * them anywhere else showed as a NoSuchFileException per file.
     */
    public static File multiverDir(File home) {
        return new File(home, ".lunarclient/offline/multiver");
    }

    /** Lunar's loader fetches the vanilla jar and Mojang's libraries into here on its own. */
    public static File minecraftDir(File home) {
        return new File(home, ".minecraft");
    }

    /** Saves, options and screenshots, the folder the game is told with --gameDir. */
    public static File gameDir(File home) {
        return new File(home, "game");
    }

    /**
     * What the client is told is the web engine's folder. It must hold
     * {@code resources/icudt67l.dat}: the client checks for that one file and refuses to start
     * without it, though the engine that replaces Ultralight never reads it.
     */
    public static File webosrDir(File home) {
        return new File(home, "webosr");
    }

    public static File webosrMarker(File home) {
        return new File(webosrDir(home), "resources/icudt67l.dat");
    }

    public static File texturesDir(File home) {
        return new File(home, ".lunarclient/textures");
    }

    public static File uiDir(File home) {
        return new File(home, ".lunarclient/ui");
    }

    // ---- downloads ---------------------------------------------------------------------------

    public static final class Download {
        public final String name, url, sha1;
        public final long size;
        public final File target;

        Download(String name, String url, String sha1, long size, File target) {
            this.name = name;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
            this.target = target;
        }
    }

    /**
     * Class path jars and external files, in the order the server listed them. Native bundles are
     * never downloaded: every one is x86 machine code, and the largest is 34 MB of a web engine
     * that is replaced rather than run.
     */
    public static List<Download> downloads(LunarApi.Launch launch, File home) {
        List<Download> out = new ArrayList<>();
        File dir = multiverDir(home);
        for (LunarApi.Artifact a : launch.artifacts) {
            if (a.type != LunarApi.ArtifactType.CLASS_PATH && a.type != LunarApi.ArtifactType.EXTERNAL_FILE)
                continue;
            File target = safeChild(dir, a.name);
            if (target == null) continue;
            out.add(new Download(a.name, a.url, a.sha1, a.size, target));
        }
        return out;
    }

    /** The bundles {@link #downloads} leaves out, by name, so the log can say what was skipped. */
    public static List<String> skippedNatives(LunarApi.Launch launch) {
        List<String> out = new ArrayList<>();
        for (LunarApi.Artifact a : launch.artifacts)
            if (a.type == LunarApi.ArtifactType.NATIVES) out.add(a.name);
        return out;
    }

    /**
     * Resolves a server-supplied name inside {@code dir}, or null if it would land anywhere else.
     * External file names carry subfolders ("hypixel/skyblock/garden.json"), so separators are
     * allowed; a name that climbs out, or is absolute, is a name from a stranger and is refused.
     */
    static File safeChild(File dir, String name) {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.startsWith("\\")) return null;
        for (String part : name.split("[/\\\\]")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
        }
        return new File(dir, name);
    }

    // ---- class path --------------------------------------------------------------------------

    /** The JVM class path: the CLASS_PATH artifacts, in the server's order, which is load order. */
    public static List<String> classpath(LunarApi.Launch launch, File home) {
        List<String> out = new ArrayList<>();
        File dir = multiverDir(home);
        for (LunarApi.Artifact a : launch.artifacts)
            if (a.type == LunarApi.ArtifactType.CLASS_PATH) out.add(new File(dir, a.name).getAbsolutePath());
        return out;
    }

    // ---- JVM arguments -----------------------------------------------------------------------

    /** -XX options known to exist in every runtime Amethyst ships. An unknown one stops the JVM. */
    static final Set<String> SAFE_XX = new HashSet<>(Arrays.asList(
            "+UseStringDeduplication"));

    /**
     * The server's JVM arguments, cut down to what Amethyst's runtime can accept.
     *
     * <p>Two kinds of argument stop a JVM before it runs a line of Java, and both are in what the
     * server sends: an {@code -XX} option the runtime was not built with (Flight Recorder's), and
     * a module that does not exist ({@code --add-modules jdk.naming.dns}). A server-chosen list
     * is a list that can grow without notice, so -XX is an allow list and modules are checked
     * against what the runtime declares. {@code -D} properties are always safe and always kept.
     *
     * @param availableModules the runtime's modules, from its {@code release} file; null means
     *                         unknown, and then only {@code java.base} is assumed
     */
    public static List<String> jvmArguments(List<String> fromServer, Set<String> availableModules) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < fromServer.size(); i++) {
            String a = fromServer.get(i);
            if (a == null) continue;
            if (isModuleFlag(a)) {
                String value = i + 1 < fromServer.size() ? fromServer.get(i + 1) : null;
                i++;
                if (value != null && modulesResolvable(a, value, availableModules)) {
                    out.add(a);
                    out.add(value);
                }
                continue;
            }
            if (a.startsWith("-D")) {
                out.add(a);
            } else if (a.startsWith("-XX:")) {
                if (SAFE_XX.contains(a.substring(4))) out.add(a);
            }
            // Anything else (a bare word, a -X option) is dropped: unknown means unsafe here.
        }
        return out;
    }

    private static boolean isModuleFlag(String a) {
        return a.equals("--add-modules") || a.equals("--add-exports") || a.equals("--add-opens")
                || a.equals("--add-reads");
    }

    private static boolean modulesResolvable(String flag, String value, Set<String> available) {
        Set<String> needed = new HashSet<>();
        if (flag.equals("--add-modules")) {
            needed.addAll(Arrays.asList(value.split(",")));
        } else {
            // module/package=target[,target]; the target ALL-UNNAMED is not a module
            int slash = value.indexOf('/');
            if (slash <= 0) return false;
            needed.add(value.substring(0, slash));
            int eq = value.indexOf('=');
            if (eq > 0) for (String t : value.substring(eq + 1).split(","))
                if (!t.equals("ALL-UNNAMED")) needed.add(t);
        }
        for (String m : needed) {
            String name = m.trim();
            if (name.isEmpty()) return false;
            if (name.equals("java.base")) continue;
            if (available == null || !available.contains(name)) return false;
        }
        return true;
    }

    private static final Pattern MODULES = Pattern.compile("^MODULES=\"?([^\"\\n]*)\"?\\s*$",
            Pattern.MULTILINE);

    /** The module list a JDK or JRE declares in its {@code release} file, or null if it does not. */
    public static Set<String> modulesFromRelease(String releaseText) {
        if (releaseText == null) return null;
        Matcher m = MODULES.matcher(releaseText);
        if (!m.find()) return null;
        Set<String> out = new HashSet<>();
        for (String s : m.group(1).trim().split("\\s+")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    // ---- program arguments -------------------------------------------------------------------

    public static final class Account {
        public final String name, uuid, accessToken;

        /** @param accessToken null for an offline account, which the client is told as "0" */
        public Account(String name, String uuid, String accessToken) {
            this.name = name;
            this.uuid = uuid;
            this.accessToken = accessToken;
        }
    }

    /**
     * Nothing listens on this port, and the client only logs that it could not connect: it is the
     * official launcher's own socket, and a run with nothing on it reached the title screen.
     */
    public static final int IPC_PORT = 28190;

    /**
     * The arguments Lunar's loader reads. Names and order follow what its own launcher passes;
     * {@code --ichorClassPath} and {@code --ichorExternalFiles} are bare names, not paths, because
     * the loader resolves them against its working directory, which is {@link #multiverDir}.
     */
    public static List<String> programArguments(LunarApi.Launch launch, String version,
                                                String assetIndex, File home, Account account,
                                                int width, int height, String installationId,
                                                String launchId) {
        List<String> a = new ArrayList<>();
        add(a, "--version", version);
        add(a, "--launcherVersion", LunarApi.LAUNCHER_VERSION);
        add(a, "--installationId", installationId);
        add(a, "--launchId", launchId);
        add(a, "--username", account.name);
        add(a, "--uuid", account.uuid);
        add(a, "--accessToken", account.accessToken == null || account.accessToken.isEmpty()
                ? "0" : account.accessToken);
        add(a, "--userProperties", "{}");
        add(a, "--assetIndex", assetIndex);
        add(a, "--gameDir", gameDir(home).getAbsolutePath());
        add(a, "--texturesDir", texturesDir(home).getAbsolutePath());
        add(a, "--uiDir", uiDir(home).getAbsolutePath());
        add(a, "--webosrDir", webosrDir(home).getAbsolutePath());
        add(a, "--workingDirectory", ".");
        add(a, "--classpathDir", ".");
        add(a, "--width", Integer.toString(Math.max(1, width)));
        add(a, "--height", Integer.toString(Math.max(1, height)));
        add(a, "--ipcPort", Integer.toString(IPC_PORT));
        List<String> cp = new ArrayList<>(), ext = new ArrayList<>();
        for (LunarApi.Artifact x : launch.artifacts) {
            if (x.type == LunarApi.ArtifactType.CLASS_PATH) cp.add(x.name);
            else if (x.type == LunarApi.ArtifactType.EXTERNAL_FILE) ext.add(x.name);
        }
        if (!cp.isEmpty()) add(a, "--ichorClassPath", join(cp));
        if (!ext.isEmpty()) add(a, "--ichorExternalFiles", join(ext));
        return a;
    }

    private static void add(List<String> a, String key, String value) {
        if (value == null) return;
        a.add(key);
        a.add(value);
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        return sb.toString();
    }

    // ---- LWJGL -------------------------------------------------------------------------------

    /**
     * One of Lunar's library jars to be replaced by one of Amethyst's before the first launch.
     *
     * <p>Lunar's loader keeps its own copy of LWJGL in {@code .minecraft/libraries} and loads it
     * child first, so a copy on the JVM class path is ignored, which a run with Amethyst's jars on
     * {@code -cp} showed directly. It also does not re-check a jar it finds already there, so a
     * file put in place before the loader looks is the file it uses. Desktop LWJGL cannot work on
     * Android: its GLFW is the real one, and Amethyst's is a stub over the game surface.
     */
    public static final class Seed {
        /** Relative to {@code .minecraft/libraries}. */
        public final String libraryPath;
        /** A file name inside Amethyst's lwjgl3 folder for the chosen version. */
        public final String amethystJar;

        Seed(String libraryPath, String amethystJar) {
            this.libraryPath = libraryPath;
            this.amethystJar = amethystJar;
        }
    }

    private static final Pattern LWJGL3 = Pattern.compile("^3\\.(\\d+)\\.(\\d+)");

    /**
     * Which of Amethyst's two LWJGL builds serves a game asking for this version, the same rule
     * the vanilla launch uses; null for LWJGL 2, which this path does not run.
     */
    public static String amethystLwjglFolder(String mojangLwjglVersion) {
        if (mojangLwjglVersion == null) return null;
        Matcher m = LWJGL3.matcher(mojangLwjglVersion);
        if (!m.find()) return null;
        int minor = Integer.parseInt(m.group(1)), patch = Integer.parseInt(m.group(2));
        return (minor > 4 || (minor == 4 && patch >= 1)) ? "3.4.1" : "3.3.3";
    }

    /** The version of {@code org.lwjgl:lwjgl} in a Mojang library list, or null. */
    public static String lwjglVersion(List<String> libraryCoordinates) {
        for (String c : libraryCoordinates) {
            String[] p = c.split(":");
            if (p.length == 3 && p[0].equals("org.lwjgl") && p[1].equals("lwjgl")) return p[2];
        }
        return null;
    }

    /**
     * Pairs every LWJGL class jar in a Mojang library list with the Amethyst jar that carries the
     * same packages. GLFW and OpenGL live in Amethyst's merged jar; a module Amethyst does not
     * ship (jemalloc) keeps Mojang's jar, which is pure bindings and is never asked to load its
     * native because Amethyst runs LWJGL with the system allocator. Native classifier jars are
     * not touched.
     */
    public static List<Seed> lwjglSeeds(List<String> libraryCoordinates, String folderVersion,
                                        Set<String> amethystJars) {
        List<Seed> out = new ArrayList<>();
        if (folderVersion == null) return out;
        String merged = "lwjgl-" + folderVersion + "-merged-modules.jar";
        for (String c : libraryCoordinates) {
            String[] p = c.split(":");
            if (p.length != 3 || !p[0].equals("org.lwjgl")) continue;
            String artifact = p[1], version = p[2];
            String jar;
            if (artifact.equals("lwjgl")) jar = "lwjgl.jar";
            else if (artifact.equals("lwjgl-glfw") || artifact.equals("lwjgl-opengl")
                    || artifact.equals("lwjgl-sdl")) jar = merged;
            else jar = artifact + ".jar";
            if (!amethystJars.contains(jar)) continue;
            out.add(new Seed("org/lwjgl/" + artifact + "/" + version + "/" + artifact + "-" + version
                    + ".jar", jar));
        }
        return out;
    }

    // ---- assets ------------------------------------------------------------------------------

    /**
     * Where the game reads Mojang's assets: {@code <gameDir>/assets} and nowhere else. Lunar's
     * loader builds Minecraft's own arguments and ignores an {@code --assetsDir} passed to it,
     * which a run with one pointing at a populated folder showed by failing to open the index in
     * the default place. Android's shared storage does not take symlinks, so the folder is real.
     */
    public static File assetsDir(File home) {
        return new File(gameDir(home), "assets");
    }

    public static final class AssetObject {
        public final String hash;
        public final long size;
        /** {@code objects/ab/abcdef...}, the same relative path in any assets folder. */
        public final String relativePath;

        AssetObject(String hash, long size) {
            this.hash = hash;
            this.size = size;
            this.relativePath = "objects/" + hash.substring(0, 2) + "/" + hash;
        }

        public String url() {
            return "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash;
        }
    }

    private static final Pattern SHA1 = Pattern.compile("^[0-9a-f]{40}$");

    /**
     * Every object an asset index names, each once. A hash that is not forty lowercase hex digits
     * is skipped: it becomes a path, and a path made of a stranger's string has to be one this
     * code can predict.
     */
    public static List<AssetObject> assetObjects(com.google.gson.JsonObject index) {
        List<AssetObject> out = new ArrayList<>();
        com.google.gson.JsonObject objects = LunarApi.object(index, "objects");
        if (objects == null) return out;
        Set<String> seen = new HashSet<>();
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : objects.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            String hash = LunarApi.string(e.getValue().getAsJsonObject(), "hash");
            if (hash == null || !SHA1.matcher(hash).matches() || !seen.add(hash)) continue;
            out.add(new AssetObject(hash, LunarApi.number(e.getValue().getAsJsonObject(), "size")));
        }
        return out;
    }

    // ---- what is offered ---------------------------------------------------------------------

    private static final Pattern RELEASE_1X = Pattern.compile("^1\\.(\\d+)(?:\\.|$)");

    /**
     * Whether a Minecraft version runs on LWJGL 2, from its name alone, so the version list can
     * say so before anything is downloaded. Every 1.x release before 1.13 does; 1.13 moved to
     * LWJGL 3, and so has everything since, including the year-numbered releases. A name this
     * does not recognise answers false, and the library check at install time still has the last
     * word through {@link #unsupportedReason}.
     */
    public static boolean usesLwjgl2(String minecraftVersion) {
        if (minecraftVersion == null) return false;
        Matcher m = RELEASE_1X.matcher(minecraftVersion);
        return m.find() && Integer.parseInt(m.group(1)) < 13;
    }

    /**
     * Why a version cannot be offered here, or null when it can. The one current rule: LWJGL 2
     * versions are out, because Lunar patches LWJGL 2's own desktop window class, and replacing
     * that LWJGL with Amethyst's made the client refuse to start at the patch, in a run that
     * swapped exactly that jar.
     */
    public static String unsupportedReason(String mojangLwjglVersion) {
        if (mojangLwjglVersion == null) return "UNKNOWN_LWJGL";
        if (mojangLwjglVersion.startsWith("2.")) return "LWJGL2";
        return amethystLwjglFolder(mojangLwjglVersion) == null ? "UNKNOWN_LWJGL" : null;
    }
}
