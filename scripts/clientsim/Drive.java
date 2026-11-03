import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kdt.pojavlaunch.clients.ClientPlan;
import net.kdt.pojavlaunch.clients.LunarApi;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/** Drives the shipped LunarApi and ClientPlan. Fixtures copy the server's shapes, not its data. */
public class Drive {
    static int failures, checks;
    static String fixtures;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL " + what); }
    }

    static JsonObject json(String name) throws Exception {
        return JsonParser.parseString(new String(Files.readAllBytes(Paths.get(fixtures, name)),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public static void main(String[] args) throws Exception {
        fixtures = args[0];
        versions();
        launch();
        refusal();
        downloadsLeaveNativesAndStrangersOut();
        classpathKeepsServerOrder();
        jvmArgumentsSurviveTheRuntime();
        programArguments();
        lwjgl();
        assets();
        requests();
        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }

    static void versions() throws Exception {
        List<LunarApi.Version> v = LunarApi.parseVersions(json("versions.json"));
        check(v.size() == 2, "a version with no id is skipped, two remain (got " + v.size() + ")");
        LunarApi.Version first = v.get(0);
        check(first.id.equals("1.21") && first.isDefault, "first version and its default flag");
        check(first.subversions.size() == 2, "subversions without an id or not objects are skipped");
        LunarApi.Subversion s = first.subversions.get(0);
        check(s.id.equals("1.21.8") && "26".equals(s.assetIndex), "asset index id is read");
        check(s.defaultModule().id.equals("fabric"), "the default module is the one flagged default");
        check(first.subversions.get(1).defaultModule().id.equals("lunar"),
                "with nothing flagged, the first module is the default");
        check(first.subversions.get(1).assetIndex == null, "a missing assets block is null, not a crash");
        LunarApi.Subversion old = v.get(1).subversions.get(0);
        check(old.modules.size() == 1, "a module with no id is skipped");
        check(old.modules.get(0).name.equals("lunar"), "a module with no name is named by its id");
        check(LunarApi.parseVersions(new JsonObject()).isEmpty(), "no versions array is an empty list");
    }

    static void launch() throws Exception {
        LunarApi.Launch l = LunarApi.parseLaunch(json("launch-ok.json"));
        check(l.success, "a successful launch reads as successful");
        check("example.Main".equals(l.mainClass), "main class");
        check(l.javaMajor == 21, "Java major is read from the runtime's file name (got " + l.javaMajor + ")");
        int cp = 0, ext = 0, nat = 0, other = 0;
        for (LunarApi.Artifact a : l.artifacts) {
            switch (a.type) {
                case CLASS_PATH: cp++; break;
                case EXTERNAL_FILE: ext++; break;
                case NATIVES: nat++; break;
                default: other++;
            }
        }
        check(cp == 4 && ext == 3 && nat == 1 && other == 1,
                "artifact types (cp " + cp + ", ext " + ext + ", nat " + nat + ", other " + other + ")");
        check(l.jvmArguments.size() == 10, "every server JVM argument is carried before filtering");
        check(LunarApi.parseLaunch(null).success == false, "no answer at all is a failure, not a crash");
    }

    static void refusal() throws Exception {
        LunarApi.Launch r = LunarApi.parseLaunch(json("launch-refused.json"));
        check(!r.success && "UNSUPPORTED_SYSTEM".equals(r.errorCode), "a refusal keeps the server's code");
        check("Placeholder refusal text.".equals(r.errorMessage), "a refusal keeps the server's sentence");
        JsonObject noData = new JsonObject();
        noData.addProperty("success", true);
        check(!LunarApi.parseLaunch(noData).success, "success with no launch data is not a launch");
    }

    static File home() {
        return new File("/data/clients/lunar");
    }

    static void downloadsLeaveNativesAndStrangersOut() throws Exception {
        LunarApi.Launch l = LunarApi.parseLaunch(json("launch-ok.json"));
        List<ClientPlan.Download> d = ClientPlan.downloads(l, home());
        Set<String> names = new HashSet<>();
        for (ClientPlan.Download x : d) names.add(x.name);
        check(!names.contains("natives-x86.zip"), "native bundles are never downloaded");
        check(!names.contains("../escape.json"), "a name that climbs out is refused");
        check(!names.contains("/abs.json"), "an absolute name is refused");
        check(!names.contains("future.bin"), "an artifact type nobody knows is not fetched");
        check(names.equals(new HashSet<>(Arrays.asList("common.jar", "mappings.jar", "loader.jar",
                "data/patterns.json", "client.jar"))), "exactly the jars and the safe external file: " + names);
        String root = ClientPlan.multiverDir(home()).getAbsolutePath() + File.separator;
        for (ClientPlan.Download x : d)
            check(x.target.getAbsolutePath().startsWith(root), "lands inside the multiver folder: " + x.target);
        check(ClientPlan.skippedNatives(l).equals(Collections.singletonList("natives-x86.zip")),
                "the skipped bundles are named");
        for (String bad : new String[]{"..", "a/../b", "a//b", "./a", "a\\..\\b", "", null})
            check(ClientPlan.downloads(withExternal(bad), home()).isEmpty(), "refuses " + bad);
    }

    static LunarApi.Launch withExternal(String name) {
        JsonObject root = JsonParser.parseString("{\"success\":true,\"launchTypeData\":{\"mainClass\":\"m\","
                + "\"artifacts\":[]}}").getAsJsonObject();
        JsonObject a = new JsonObject();
        if (name != null) a.addProperty("name", name);
        a.addProperty("url", "https://example.invalid/x");
        a.addProperty("type", "EXTERNAL_FILE");
        root.getAsJsonObject("launchTypeData").getAsJsonArray("artifacts").add(a);
        return LunarApi.parseLaunch(root);
    }

    static void classpathKeepsServerOrder() throws Exception {
        List<String> cp = ClientPlan.classpath(LunarApi.parseLaunch(json("launch-ok.json")), home());
        List<String> names = new ArrayList<>();
        for (String p : cp) names.add(new File(p).getName());
        check(names.equals(Arrays.asList("common.jar", "mappings.jar", "loader.jar", "client.jar")),
                "class path is the CLASS_PATH artifacts in the server's order: " + names);
    }

    static void jvmArgumentsSurviveTheRuntime() throws Exception {
        List<String> server = LunarApi.parseLaunch(json("launch-ok.json")).jvmArguments;
        Set<String> modules = ClientPlan.modulesFromRelease(new String(Files.readAllBytes(
                Paths.get(fixtures, "release.txt")), StandardCharsets.UTF_8));
        check(modules != null && modules.contains("jdk.naming.dns") && modules.size() == 5,
                "release file modules are read");

        List<String> full = ClientPlan.jvmArguments(server, modules);
        check(full.equals(Arrays.asList(
                "--add-modules", "jdk.naming.dns",
                "--add-exports", "jdk.naming.dns/com.sun.jndi.dns=java.naming",
                "-Dlog4j2.formatMsgNoLookups=true",
                "--add-opens", "java.base/java.io=ALL-UNNAMED",
                "-XX:+UseStringDeduplication",
                "-Dexample.url=file:index.html")), "with every module present: " + full);

        Set<String> lean = new HashSet<>(Arrays.asList("java.base", "java.logging"));
        List<String> cut = ClientPlan.jvmArguments(server, lean);
        check(cut.equals(Arrays.asList(
                "-Dlog4j2.formatMsgNoLookups=true",
                "--add-opens", "java.base/java.io=ALL-UNNAMED",
                "-XX:+UseStringDeduplication",
                "-Dexample.url=file:index.html")), "a missing module takes its flags and values with it: " + cut);

        List<String> unknown = ClientPlan.jvmArguments(server, null);
        check(unknown.equals(cut), "an unreadable module list assumes java.base alone");

        for (String a : cut) check(!a.contains("FlightRecorder"), "Flight Recorder options never pass");
        check(ClientPlan.jvmArguments(Arrays.asList("--add-modules"), modules).isEmpty(),
                "a flag with no value is dropped, not passed bare");
        check(ClientPlan.jvmArguments(Arrays.asList("-Xshare:off", "-XX:+Whatever", "word"), modules).isEmpty(),
                "unknown -X, -XX and bare words are dropped");
        check(ClientPlan.modulesFromRelease("JAVA_VERSION=\"21\"") == null, "no MODULES line is null");
        check(ClientPlan.modulesFromRelease(null) == null, "no file is null");
    }

    static void programArguments() throws Exception {
        LunarApi.Launch l = LunarApi.parseLaunch(json("launch-ok.json"));
        List<String> a = ClientPlan.programArguments(l, "1.21.8", "26", home(),
                new ClientPlan.Account("Steve", "0123", null), 1920, 1080, "inst", "launch");
        Map<String, String> m = new LinkedHashMap<>();
        check(a.size() % 2 == 0, "arguments come in pairs");
        for (int i = 0; i + 1 < a.size(); i += 2) {
            check(a.get(i).startsWith("--"), "a key at " + i + ": " + a.get(i));
            check(!a.get(i + 1).startsWith("--"), "a value at " + (i + 1) + ": " + a.get(i + 1));
            m.put(a.get(i), a.get(i + 1));
        }
        check("0".equals(m.get("--accessToken")), "an offline account is told token 0");
        check("1.21.8".equals(m.get("--version")) && "26".equals(m.get("--assetIndex")), "version and assets");
        check(ClientPlan.gameDir(home()).getAbsolutePath().equals(m.get("--gameDir")), "game dir under the client home");
        check(ClientPlan.webosrDir(home()).getAbsolutePath().equals(m.get("--webosrDir")), "web engine dir");
        check("common.jar,mappings.jar,loader.jar,client.jar".equals(m.get("--ichorClassPath")),
                "class path is passed as bare names in order");
        check(m.get("--ichorExternalFiles") != null && m.get("--ichorExternalFiles").contains("data/patterns.json"),
                "external files are passed");
        check(Integer.toString(ClientPlan.IPC_PORT).equals(m.get("--ipcPort")), "the IPC port is passed");
        List<String> online = ClientPlan.programArguments(l, "1.21.8", "26", home(),
                new ClientPlan.Account("Steve", "0123", "tok"), 0, -5, "i", "l");
        check(online.get(online.indexOf("--accessToken") + 1).equals("tok"), "a real token is passed through");
        check(online.get(online.indexOf("--width") + 1).equals("1"), "a non-positive size is clamped to 1");
    }

    static void lwjgl() {
        check("3.3.3".equals(ClientPlan.amethystLwjglFolder("3.3.3")), "3.3.3 uses 3.3.3");
        check("3.3.3".equals(ClientPlan.amethystLwjglFolder("3.3.4")), "3.3.4 uses 3.3.3");
        check("3.3.3".equals(ClientPlan.amethystLwjglFolder("3.4.0")), "3.4.0 uses 3.3.3");
        check("3.4.1".equals(ClientPlan.amethystLwjglFolder("3.4.1")), "3.4.1 uses 3.4.1");
        check("3.4.1".equals(ClientPlan.amethystLwjglFolder("3.5.0")), "3.5.0 uses 3.4.1");
        check("3.4.1".equals(ClientPlan.amethystLwjglFolder("3.3.3-snapshot".replace("3.3.3", "3.10.0"))),
                "minor compared as a number, not a string");
        check(ClientPlan.amethystLwjglFolder("2.9.4-nightly-20150209") == null, "LWJGL 2 is no folder");
        check(ClientPlan.amethystLwjglFolder(null) == null, "nothing is no folder");

        List<String> libs = Arrays.asList(
                "com.mojang:blocklist:1.0.10",
                "org.lwjgl:lwjgl:3.3.3",
                "org.lwjgl:lwjgl:3.3.3:natives-linux",
                "org.lwjgl:lwjgl-glfw:3.3.3",
                "org.lwjgl:lwjgl-opengl:3.3.3",
                "org.lwjgl:lwjgl-jemalloc:3.3.3",
                "org.lwjgl:lwjgl-openal:3.3.3",
                "org.lwjgl:lwjgl-openal:3.3.3:natives-linux-arm64",
                "org.lwjgl:lwjgl-stb:3.3.3",
                "org.lwjgl:lwjgl-tinyfd:3.3.3",
                "org.lwjgl:lwjgl-freetype:3.3.3");
        check("3.3.3".equals(ClientPlan.lwjglVersion(libs)), "the core version is read, not a classifier's");
        Set<String> shipped = new HashSet<>(Arrays.asList("lwjgl.jar", "lwjgl-3.3.3-merged-modules.jar",
                "lwjgl-openal.jar", "lwjgl-stb.jar", "lwjgl-tinyfd.jar", "lwjgl-freetype.jar",
                "lwjgl-lwjglx.jar", "lwjgl-vulkan.jar"));
        List<ClientPlan.Seed> seeds = ClientPlan.lwjglSeeds(libs, "3.3.3", shipped);
        Map<String, String> m = new LinkedHashMap<>();
        for (ClientPlan.Seed s : seeds) m.put(s.libraryPath, s.amethystJar);
        Map<String, String> want = new LinkedHashMap<>();
        want.put("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar", "lwjgl.jar");
        want.put("org/lwjgl/lwjgl-glfw/3.3.3/lwjgl-glfw-3.3.3.jar", "lwjgl-3.3.3-merged-modules.jar");
        want.put("org/lwjgl/lwjgl-opengl/3.3.3/lwjgl-opengl-3.3.3.jar", "lwjgl-3.3.3-merged-modules.jar");
        want.put("org/lwjgl/lwjgl-openal/3.3.3/lwjgl-openal-3.3.3.jar", "lwjgl-openal.jar");
        want.put("org/lwjgl/lwjgl-stb/3.3.3/lwjgl-stb-3.3.3.jar", "lwjgl-stb.jar");
        want.put("org/lwjgl/lwjgl-tinyfd/3.3.3/lwjgl-tinyfd-3.3.3.jar", "lwjgl-tinyfd.jar");
        want.put("org/lwjgl/lwjgl-freetype/3.3.3/lwjgl-freetype-3.3.3.jar", "lwjgl-freetype.jar");
        check(m.equals(want), "seeds: " + m);
        check(!m.containsKey("org/lwjgl/lwjgl-jemalloc/3.3.3/lwjgl-jemalloc-3.3.3.jar"),
                "a module Amethyst does not ship keeps Mojang's jar");
        for (String p : m.keySet()) check(!p.contains("natives"), "no native classifier jar is seeded: " + p);
        check(ClientPlan.lwjglSeeds(libs, null, shipped).isEmpty(), "no folder, no seeds");

        check(ClientPlan.unsupportedReason("3.3.3") == null, "LWJGL 3 is offered");
        check("LWJGL2".equals(ClientPlan.unsupportedReason("2.9.4-nightly-20150209")), "LWJGL 2 is not");
        check(ClientPlan.unsupportedReason(null) != null, "unknown is not offered");

        for (String v : new String[]{"1.8.9", "1.7.10", "1.12.2", "1.12", "1.8"})
            check(ClientPlan.usesLwjgl2(v), v + " runs on LWJGL 2");
        for (String v : new String[]{"1.13", "1.13.2", "1.21.8", "1.20", "26.1.1", "26.3", "23w14a", "", null})
            check(!ClientPlan.usesLwjgl2(v), v + " does not");
    }

    static void assets() throws Exception {
        List<ClientPlan.AssetObject> a = ClientPlan.assetObjects(json("assets.json"));
        Set<String> hashes = new HashSet<>();
        for (ClientPlan.AssetObject o : a) hashes.add(o.hash);
        check(a.size() == 2 && hashes.size() == 2, "objects are de-duplicated by hash (got " + a.size() + ")");
        for (ClientPlan.AssetObject o : a) {
            check(o.relativePath.equals("objects/" + o.hash.substring(0, 2) + "/" + o.hash), "object path " + o.relativePath);
            check(o.url().endsWith("/" + o.hash.substring(0, 2) + "/" + o.hash), "object url");
            check(!o.relativePath.contains(".."), "no hash becomes a climbing path");
        }
        check(ClientPlan.assetsDir(home()).getPath().endsWith("game" + File.separator + "assets"),
                "assets live in the game dir");
    }

    static void requests() {
        JsonObject b = LunarApi.launchBody("inst", "00000000-0000-4000-8000-000000000000", "1.21.8", "fabric");
        check("NEUTRAL".equals(b.get("canary_preference").getAsString()), "canary preference uses the schema's value");
        check("x64".equals(b.get("arch").getAsString()) && "linux".equals(b.get("os").getAsString()),
                "the only platform the server serves is asked for");
        check(b.getAsJsonObject("profile").get("id").getAsString().length() == 36, "profile id is a UUID");
        check(b.get("args").isJsonArray(), "args is an array");
        check(LunarApi.versionsQuery("inst").get("installation_id").equals("inst"), "versions query carries the id");
        check(LunarApi.userAgent("1.0").startsWith("AmethystX/"), "the user agent says who is asking");
    }
}
