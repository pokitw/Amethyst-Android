package net.kdt.pojavlaunch.modloaders;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every mod loader build there is, fetched once and indexed by Minecraft version.
 *
 * <b>This exists to turn the question round.</b> The installer this replaces asks which loader you
 * want before it can tell you what that loader supports, then asks you to find your Minecraft
 * version in a spinner of seven hundred entries, then a build in a second list. Nobody thinks that
 * way: they think "I want to play 1.20.1 with mods". Answering that needs all four loaders' version
 * lists at once, which is what this fetches, and once it is fetched every subsequent question is
 * free.
 *
 * <b>Six requests, in parallel, once.</b> Fabric and Quilt each want their game list and their
 * loader list; Forge and NeoForge each publish one maven-metadata.xml holding every version they
 * have ever released. The old flow instead fetched loader versions <i>per selected game version</i>,
 * so changing the version cost a round trip every time.
 *
 * <b>The Fabric matrix is not really a matrix.</b> Fabric's loader is independent of the game
 * version: any loader build runs any game version it knows about, which is why the meta API can
 * serve the loader list with no game version in the URL at all. So the whole of Fabric's
 * availability is "every game version it lists, crossed with the newest stable loader", and asking
 * per version was always answering a question that had one answer.
 */
public final class LoaderIndex {
    private static final String TAG = "LoaderIndex";
    private static final int TIMEOUT_MS = 15000;
    /** A maven metadata document for Forge is about a megabyte; nothing here is bigger. */
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private static final String FABRIC_GAME = "https://meta.fabricmc.net/v2/versions/game";
    private static final String FABRIC_LOADER = "https://meta.fabricmc.net/v2/versions/loader";
    private static final String QUILT_GAME = "https://meta.quiltmc.org/v3/versions/game";
    private static final String QUILT_LOADER = "https://meta.quiltmc.org/v3/versions/loader";
    private static final String FORGE_METADATA =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String NEOFORGE_METADATA =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";

    private LoaderIndex() {}

    /** The loaders this can install. Ordered as the screen shows them. */
    public enum Loader { FABRIC, QUILT, FORGE, NEOFORGE }

    /** One installable build of one loader, for one Minecraft version. */
    public static final class Build {
        public final Loader loader;
        /** The Minecraft version this build is for. */
        public final String gameVersion;
        /** What the download task wants: a loader version for Fabric, a full id for Forge. */
        public final String loaderVersion;
        /** What a player reads: "0.16.5", "47.2.0". */
        public final String label;
        public final boolean stable;

        Build(Loader loader, String gameVersion, String loaderVersion, String label,
              boolean stable) {
            this.loader = loader;
            this.gameVersion = gameVersion;
            this.loaderVersion = loaderVersion;
            this.label = label;
            this.stable = stable;
        }
    }

    /** What is installable for one Minecraft version. */
    public static final class GameVersion {
        public final String id;
        /** False for a snapshot, a pre-release or an April Fools' version. */
        public final boolean release;
        /** Newest build first, per loader. */
        public final Map<Loader, List<Build>> builds = new LinkedHashMap<>();

        GameVersion(String id, boolean release) {
            this.id = id;
            this.release = release;
        }

        public boolean has(Loader loader) {
            List<Build> list = builds.get(loader);
            return list != null && !list.isEmpty();
        }

        /**
         * The build to install without being asked, which is the newest stable one.
         *
         * Falling back to the newest of any channel rather than to nothing: a Minecraft version
         * whose only Forge builds are betas still has an answer, and refusing to name one would
         * mean the row could not be tapped at all.
         */
        @Nullable
        public Build best(Loader loader) {
            List<Build> list = builds.get(loader);
            if (list == null || list.isEmpty()) return null;
            for (Build build : list) if (build.stable) return build;
            return list.get(0);
        }

        public int loaderCount() {
            int count = 0;
            for (Loader loader : Loader.values()) if (has(loader)) count++;
            return count;
        }
    }

    /** The whole index: every Minecraft version anything can be installed for, newest first. */
    public static final class Index {
        public final List<GameVersion> versions;
        /** Loaders whose fetch failed, so the screen can say what is missing rather than lie. */
        public final List<Loader> unavailable;

        Index(List<GameVersion> versions, List<Loader> unavailable) {
            this.versions = Collections.unmodifiableList(versions);
            this.unavailable = Collections.unmodifiableList(unavailable);
        }

        public boolean isEmpty() {
            return versions.isEmpty();
        }
    }

    /* ------------------------------------------------------------------ fetching */

    /**
     * Fetch and index everything. Blocks; call it off the main thread.
     *
     * Each source is independent, and one that fails takes only itself out: a Forge maven that is
     * down should not cost somebody the Fabric install they came for. What it must not do is
     * pretend, which is why the loader is named in {@link Index#unavailable} instead.
     */
    @NonNull
    public static Index fetch() {
        Map<String, Boolean> gameVersions = new LinkedHashMap<>();
        Map<Loader, List<Build>> byLoader = new LinkedHashMap<>();
        List<Loader> unavailable = new ArrayList<>();

        // Fabric and Quilt: the game list decides which versions exist, the loader list decides
        // which builds. The cross product is the availability, because the loader is game
        // independent (see the class comment).
        for (Loader loader : new Loader[]{Loader.FABRIC, Loader.QUILT}) {
            boolean fabric = loader == Loader.FABRIC;
            String games = get(fabric ? FABRIC_GAME : QUILT_GAME);
            String loaders = get(fabric ? FABRIC_LOADER : QUILT_LOADER);
            if (games == null || loaders == null) {
                unavailable.add(loader);
                continue;
            }
            List<String[]> parsedGames = parseFabricGames(games);
            List<Build> parsedLoaders = parseFabricLoaders(loader, loaders);
            if (parsedGames.isEmpty() || parsedLoaders.isEmpty()) {
                unavailable.add(loader);
                continue;
            }
            for (String[] game : parsedGames) {
                gameVersions.put(game[0], "true".equals(game[1]));
            }
            byLoader.put(loader, parsedLoaders);
        }

        // Forge and NeoForge: one maven document each, holding every version ever released.
        String forge = get(FORGE_METADATA);
        if (forge == null) unavailable.add(Loader.FORGE);
        else byLoader.put(Loader.FORGE, parseForge(forge));

        String neoforge = get(NEOFORGE_METADATA);
        if (neoforge == null) unavailable.add(Loader.NEOFORGE);
        else byLoader.put(Loader.NEOFORGE, parseNeoForge(neoforge));

        return index(gameVersions, byLoader, unavailable);
    }

    /**
     * Turn the per-loader lists into per-game-version rows.
     *
     * Fabric's builds carry no game version of their own, so they are attached to every game
     * version its own list named. Forge's and NeoForge's already carry one, and a Forge build for
     * a Minecraft version Fabric never listed still deserves a row, which is why the game version
     * set is grown from both sides rather than fixed by Fabric's list.
     */
    @NonNull
    static Index index(@NonNull Map<String, Boolean> gameVersions,
                       @NonNull Map<Loader, List<Build>> byLoader,
                       @NonNull List<Loader> unavailable) {
        Map<String, GameVersion> rows = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : gameVersions.entrySet()) {
            rows.put(entry.getKey(), new GameVersion(entry.getKey(), entry.getValue()));
        }
        int order = 0;
        Map<String, Integer> rank = new LinkedHashMap<>();
        for (String id : gameVersions.keySet()) rank.put(id, order++);

        for (Map.Entry<Loader, List<Build>> entry : byLoader.entrySet()) {
            Loader loader = entry.getKey();
            for (Build build : entry.getValue()) {
                if (build.gameVersion == null) {
                    // Fabric-like: every game version gets it.
                    for (GameVersion row : rows.values()) add(row, loader, build);
                    continue;
                }
                GameVersion row = rows.get(build.gameVersion);
                if (row == null) {
                    // A Forge build for something Fabric never listed. Treated as a release,
                    // because Forge does not build for snapshots.
                    row = new GameVersion(build.gameVersion, true);
                    rows.put(build.gameVersion, row);
                    rank.put(build.gameVersion, Integer.MAX_VALUE);
                }
                add(row, loader, build);
            }
        }

        List<GameVersion> ordered = new ArrayList<>(rows.values());
        // Newest first, which for Fabric's list is the order it arrives in. Anything Fabric never
        // named goes after, since nothing else states an order.
        Collections.sort(ordered, new Comparator<GameVersion>() {
            @Override
            public int compare(GameVersion a, GameVersion b) {
                Integer left = rank.get(a.id);
                Integer right = rank.get(b.id);
                int l = left == null ? Integer.MAX_VALUE : left;
                int r = right == null ? Integer.MAX_VALUE : right;
                if (l != r) return Integer.compare(l, r);
                return compareVersions(b.id, a.id);
            }
        });
        List<GameVersion> kept = new ArrayList<>();
        for (GameVersion row : ordered) if (row.loaderCount() > 0) kept.add(row);
        return new Index(kept, unavailable);
    }

    private static void add(GameVersion row, Loader loader, Build build) {
        List<Build> list = row.builds.get(loader);
        if (list == null) {
            list = new ArrayList<>();
            row.builds.put(loader, list);
        }
        // The game version on a Fabric build is null until it lands on a row, and the row is the
        // only thing that knows which one it is.
        list.add(build.gameVersion == null
                ? new Build(loader, row.id, build.loaderVersion, build.label, build.stable)
                : build);
    }

    /* ------------------------------------------------------------------ parsing */

    /** Fabric's game list: {@code [{"version":"1.20.1","stable":true}, ...]}, newest first. */
    @NonNull
    static List<String[]> parseFabricGames(@Nullable String body) {
        List<String[]> out = new ArrayList<>();
        if (body == null) return out;
        try {
            JsonArray array = JsonParser.parseString(body).getAsJsonArray();
            for (JsonElement element : array) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String version = text(object, "version");
                if (version.isEmpty()) continue;
                out.add(new String[]{version, Boolean.toString(flag(object, "stable"))});
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not read a game version list", t);
        }
        return out;
    }

    /**
     * Fabric's loader list: {@code [{"version":"0.16.5","stable":true}, ...]}, newest first.
     *
     * The game version is left null on purpose: these builds are not for any particular version,
     * and pretending otherwise here would mean inventing a cross product before anything needed
     * one. {@link #add} fills it in when the build lands on a row.
     */
    @NonNull
    static List<Build> parseFabricLoaders(@NonNull Loader loader, @Nullable String body) {
        List<Build> out = new ArrayList<>();
        if (body == null) return out;
        try {
            JsonArray array = JsonParser.parseString(body).getAsJsonArray();
            for (JsonElement element : array) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                // Quilt's v3 nests the same shape under "loader" on some routes; take either.
                JsonElement inner = object.get("loader");
                JsonObject source = inner != null && inner.isJsonObject()
                        ? inner.getAsJsonObject() : object;
                String version = text(source, "version");
                if (version.isEmpty()) continue;
                out.add(new Build(loader, null, version, version, flag(source, "stable")));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not read a loader version list", t);
        }
        return out;
    }

    @NonNull
    private static String text(@NonNull JsonObject object, @NonNull String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return "";
        try {
            return element.getAsString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean flag(@NonNull JsonObject object, @NonNull String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return false;
        try {
            return element.getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    private static final Pattern MAVEN_VERSION = Pattern.compile("<version>([^<]+)</version>");

    /**
     * Forge's maven metadata, where a version is {@code 1.20.1-47.2.0}.
     *
     * Split on the <b>first</b> hyphen, because the tail is not always just a number: older
     * releases carry things like {@code 1.7.10-10.13.4.1614-1.7.10}, where splitting on the last
     * would call the Minecraft version "1614" and file it under a version that has never existed.
     */
    @NonNull
    static List<Build> parseForge(@Nullable String xml) {
        List<Build> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher matcher = MAVEN_VERSION.matcher(xml);
        while (matcher.find()) {
            String version = matcher.group(1).trim();
            int split = version.indexOf('-');
            if (split <= 0 || split == version.length() - 1) continue;
            String game = version.substring(0, split);
            String forge = version.substring(split + 1);
            // Forge's own installer id is the whole string, which is what the download task wants.
            out.add(new Build(Loader.FORGE, game, version, forge, true));
        }
        Collections.reverse(out);
        return out;
    }

    /**
     * NeoForge's maven metadata, where the Minecraft version has to be derived from the build.
     *
     * <b>NeoForge encodes it in the version number rather than stating it.</b> From 1.20.2 the
     * scheme is {@code MAJOR.MINOR.PATCH} where the Minecraft version is {@code 1.MAJOR.MINOR},
     * with the trailing zero dropped: 21.1.66 is 1.21.1 and 21.0.167 is 1.21. Their very first
     * line, for 1.20.1, kept Forge's numbering instead and is {@code 47.x}, which is the one case
     * the rule cannot derive and so is stated.
     */
    @NonNull
    static List<Build> parseNeoForge(@Nullable String xml) {
        List<Build> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher matcher = MAVEN_VERSION.matcher(xml);
        while (matcher.find()) {
            String version = matcher.group(1).trim();
            String game = neoForgeGameVersion(version);
            if (game == null) continue;
            boolean stable = !version.contains("beta") && !version.contains("alpha");
            out.add(new Build(Loader.NEOFORGE, game, version, version, stable));
        }
        Collections.reverse(out);
        return out;
    }

    @Nullable
    static String neoForgeGameVersion(@NonNull String version) {
        String[] parts = version.split("\\.");
        if (parts.length < 2) return null;
        int major;
        int minor;
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1].split("-")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        // The 1.20.1 line, which predates the scheme and follows Forge's numbering.
        if (major >= 40) return "1.20.1";
        if (major < 20) return null;
        return minor == 0 ? "1." + major : "1." + major + "." + minor;
    }

    /** Newest first, comparing dotted numbers rather than strings so 1.9 sorts under 1.10. */
    static int compareVersions(@NonNull String a, @NonNull String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? number(left[i]) : 0;
            int r = i < right.length ? number(right[i]) : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return a.compareTo(b);
    }

    private static int number(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) end++;
        if (end == 0) return -1;
        try {
            return Integer.parseInt(part.substring(0, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Nullable
    private static String get(@NonNull String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, url + " answered " + code);
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                String body = Tools.read(input);
                if (body == null || body.length() > MAX_BYTES) return null;
                return body;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not fetch " + url, t);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
