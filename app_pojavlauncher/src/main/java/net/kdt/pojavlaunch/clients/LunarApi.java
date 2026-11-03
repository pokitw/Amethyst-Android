package net.kdt.pojavlaunch.clients;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two questions this launcher asks Lunar's launcher API, and how its answers are read.
 *
 * <p>Nothing here does I/O, so the shape of every request and the reading of every answer can be
 * checked by {@code scripts/clientsim} without a phone or a network.
 *
 * <p><b>The request always says x86-64 Linux, and that is not a disguise for the game.</b> The
 * server refuses ARM outright ("Currently only x86 is supported") and only ever serves the x86
 * build. What comes back is two kinds of thing: Java archives, which are the same bytes on every
 * platform, and native libraries, which are x86 machine code. The plan in {@link ClientPlan} keeps
 * the first and never downloads the second, supplying its own ARM replacements instead, so the
 * x86 answer is the only one there is and none of it is executed as x86.
 *
 * <p>The client identifies itself honestly in its user agent. The API accepts that.
 */
public final class LunarApi {
    private LunarApi() {}

    public static final String API_BASE = "https://api.lunarclientprod.com/launcher";
    public static final String VERSIONS_URL = API_BASE + "/metadata/versions/lunar";
    public static final String LAUNCH_URL = API_BASE + "/launch";

    /** The launcher version the API validates requests against. Sent, never claimed as ours. */
    public static final String LAUNCHER_VERSION = "3.4.9";
    public static final String OS = "linux";
    public static final String ARCH = "x64";
    /** What the server's schema accepts for this field; any other value is a validation error. */
    public static final String CANARY_PREFERENCE = "NEUTRAL";
    public static final String DEFAULT_BRANCH = "master";

    public static String userAgent(String appVersion) {
        return "AmethystX/" + (appVersion == null || appVersion.isEmpty() ? "unknown" : appVersion)
                + " (Android)";
    }

    /** Query parameters for the versions listing, which is a GET. */
    public static Map<String, String> versionsQuery(String installationId) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("installation_id", installationId);
        q.put("os", OS);
        q.put("os_release", "6.1.0");
        q.put("arch", ARCH);
        q.put("launcher_version", LAUNCHER_VERSION);
        q.put("branch", DEFAULT_BRANCH);
        q.put("private", "false");
        q.put("language", "en");
        return q;
    }

    /**
     * The launch request body. {@code profileId} must be a UUID: the server's schema rejects
     * anything else, and so does an empty string.
     */
    public static JsonObject launchBody(String installationId, String profileId, String version,
                                        String module) {
        JsonObject b = new JsonObject();
        b.addProperty("installation_id", installationId);
        b.addProperty("os", OS);
        b.addProperty("os_release", "6.1.0");
        b.addProperty("arch", ARCH);
        b.addProperty("launcher_version", LAUNCHER_VERSION);
        b.addProperty("version", version);
        b.addProperty("branch", DEFAULT_BRANCH);
        b.add("args", new JsonArray());
        b.addProperty("module", module);
        b.addProperty("canary_preference", CANARY_PREFERENCE);
        JsonObject profile = new JsonObject();
        profile.addProperty("id", profileId);
        profile.addProperty("name", "Amethyst X");
        b.add("profile", profile);
        return b;
    }

    // ---- versions ----------------------------------------------------------------------------

    public static final class Module {
        public final String id, name, description;
        public final boolean isDefault;

        Module(String id, String name, String description, boolean isDefault) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.isDefault = isDefault;
        }
    }

    public static final class Subversion {
        public final String id;
        /** Mojang's asset index id, which the game is told with --assetIndex. */
        public final String assetIndex;
        public final String assetIndexUrl, assetIndexSha1;
        public final List<Module> modules;

        Subversion(String id, String assetIndex, String assetIndexUrl, String assetIndexSha1,
                   List<Module> modules) {
            this.id = id;
            this.assetIndex = assetIndex;
            this.assetIndexUrl = assetIndexUrl;
            this.assetIndexSha1 = assetIndexSha1;
            this.modules = modules;
        }

        /** The module Lunar itself would pick, or the first one, or null. */
        public Module defaultModule() {
            for (Module m : modules) if (m.isDefault) return m;
            return modules.isEmpty() ? null : modules.get(0);
        }
    }

    public static final class Version {
        public final String id, name;
        public final boolean isDefault;
        public final List<Subversion> subversions;

        Version(String id, String name, boolean isDefault, List<Subversion> subversions) {
            this.id = id;
            this.name = name;
            this.isDefault = isDefault;
            this.subversions = subversions;
        }
    }

    /**
     * Reads the versions listing. A version or subversion with no id is skipped rather than
     * failing the whole list, since one malformed entry should not hide the other nine.
     */
    public static List<Version> parseVersions(JsonObject root) {
        List<Version> out = new ArrayList<>();
        JsonArray versions = array(root, "versions");
        if (versions == null) return out;
        for (JsonElement ve : versions) {
            if (!ve.isJsonObject()) continue;
            JsonObject v = ve.getAsJsonObject();
            String id = string(v, "id");
            if (id == null) continue;
            List<Subversion> subs = new ArrayList<>();
            JsonArray sa = array(v, "subversions");
            if (sa != null) for (JsonElement se : sa) {
                if (!se.isJsonObject()) continue;
                JsonObject s = se.getAsJsonObject();
                String sid = string(s, "id");
                if (sid == null) continue;
                JsonObject assets = object(s, "assets");
                List<Module> modules = new ArrayList<>();
                JsonArray ma = array(s, "modules");
                if (ma != null) for (JsonElement me : ma) {
                    if (!me.isJsonObject()) continue;
                    JsonObject m = me.getAsJsonObject();
                    String mid = string(m, "id");
                    if (mid == null) continue;
                    modules.add(new Module(mid, orElse(string(m, "name"), mid),
                            orElse(string(m, "description"), ""), bool(m, "default")));
                }
                subs.add(new Subversion(sid,
                        assets == null ? null : string(assets, "id"),
                        assets == null ? null : string(assets, "url"),
                        assets == null ? null : string(assets, "sha1"),
                        Collections.unmodifiableList(modules)));
            }
            out.add(new Version(id, orElse(string(v, "name"), id), bool(v, "default"),
                    Collections.unmodifiableList(subs)));
        }
        return out;
    }

    // ---- launch ------------------------------------------------------------------------------

    public enum ArtifactType { CLASS_PATH, EXTERNAL_FILE, NATIVES, OTHER }

    public static final class Artifact {
        public final String name, url, sha1;
        public final long size;
        public final ArtifactType type;

        Artifact(String name, String url, String sha1, long size, ArtifactType type) {
            this.name = name;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
            this.type = type;
        }
    }

    public static final class Launch {
        public final boolean success;
        /** Set when the server refused, e.g. UNSUPPORTED_SYSTEM. */
        public final String errorCode, errorMessage;
        public final String mainClass;
        public final List<Artifact> artifacts;
        /** JVM arguments the official launcher adds, unfiltered. */
        public final List<String> jvmArguments;
        /** The Java major version Lunar's own runtime is, or 0 if it could not be read. */
        public final int javaMajor;

        Launch(boolean success, String errorCode, String errorMessage, String mainClass,
               List<Artifact> artifacts, List<String> jvmArguments, int javaMajor) {
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.mainClass = mainClass;
            this.artifacts = artifacts;
            this.jvmArguments = jvmArguments;
            this.javaMajor = javaMajor;
        }
    }

    private static final Pattern JRE_MAJOR = Pattern.compile("(?:jre|jdk)(\\d{1,2})(?:\\.|u|-|_)");

    /**
     * Reads a launch answer. A refusal is a Launch with {@code success == false} and the server's
     * own code and message, never an exception: "only x86 is supported" is a sentence a player
     * should be shown rather than a stack trace.
     */
    public static Launch parseLaunch(JsonObject root) {
        if (root == null) return failure("EMPTY", "The server sent nothing back.");
        if (!bool(root, "success")) {
            JsonObject err = object(root, "error");
            String code = err == null ? null : string(err, "code");
            String msg = err == null ? null : string(err, "message");
            if (msg == null && err != null) msg = string(err, "short");
            return failure(orElse(code, "REFUSED"), orElse(msg, "The server refused the launch."));
        }
        JsonObject ltd = object(root, "launchTypeData");
        if (ltd == null) return failure("MISSING_LAUNCH_TYPE_DATA", "The answer had no launch data.");
        String mainClass = string(ltd, "mainClass");
        if (mainClass == null) return failure("MISSING_MAIN_CLASS", "The answer named no main class.");

        List<Artifact> artifacts = new ArrayList<>();
        JsonArray aa = array(ltd, "artifacts");
        if (aa != null) for (JsonElement ae : aa) {
            if (!ae.isJsonObject()) continue;
            JsonObject a = ae.getAsJsonObject();
            String name = string(a, "name");
            String url = string(a, "url");
            if (name == null || url == null) continue;
            artifacts.add(new Artifact(name, url, string(a, "sha1"), number(a, "size"),
                    type(string(a, "type"))));
        }

        List<String> jvm = new ArrayList<>();
        int javaMajor = 0;
        JsonObject jre = object(root, "jre");
        if (jre != null) {
            JsonArray xa = array(jre, "extraArguments");
            if (xa != null) for (JsonElement x : xa) if (x.isJsonPrimitive()) jvm.add(x.getAsString());
            JsonObject dl = object(jre, "download");
            String url = dl == null ? null : string(dl, "url");
            if (url != null) {
                Matcher m = JRE_MAJOR.matcher(url);
                if (m.find()) javaMajor = Integer.parseInt(m.group(1));
            }
        }
        return new Launch(true, null, null, mainClass, Collections.unmodifiableList(artifacts),
                Collections.unmodifiableList(jvm), javaMajor);
    }

    private static Launch failure(String code, String message) {
        return new Launch(false, code, message, null, Collections.<Artifact>emptyList(),
                Collections.<String>emptyList(), 0);
    }

    static ArtifactType type(String t) {
        if ("CLASS_PATH".equals(t)) return ArtifactType.CLASS_PATH;
        if ("EXTERNAL_FILE".equals(t)) return ArtifactType.EXTERNAL_FILE;
        if ("NATIVES".equals(t)) return ArtifactType.NATIVES;
        return ArtifactType.OTHER;
    }

    // ---- tolerant readers --------------------------------------------------------------------

    private static String orElse(String s, String fallback) {
        return s == null ? fallback : s;
    }

    static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    static long number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return -1;
        return e.getAsLong();
    }

    static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }
}
