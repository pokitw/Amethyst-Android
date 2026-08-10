package net.kdt.pojavlaunch.modloaders.modpacks.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modrinth, asked about single mods rather than modpacks.
 *
 * <b>Why this is not {@link ModrinthApi}.</b> That class exists to install a <i>modpack</i>: its
 * {@code installMod} says as much in a TODO, and what it does is download an {@code .mrpack} and
 * build a whole new profile around it. Adding one mod to a profile you already have is a different
 * job with a different shape, and two of the places they would have to differ are places where
 * bending the existing code would quietly break the modpack path:
 * <ul>
 *   <li>it reads {@code game_versions[0]} as "the" Minecraft version, but a mod file routinely
 *       lists a range, and the first entry is the <i>oldest</i>. Filtering a mod list on that
 *       would hide most of what matches;</li>
 *   <li>it reads {@code files[0]} as "the" download, but Modrinth marks one file
 *       {@code primary} and a mod can ship a sources or javadoc jar alongside it. For a modpack
 *       there is only ever one file, so the existing code has never been wrong; for a mod it
 *       would install the wrong jar.</li>
 * </ul>
 * Neither class calls the other, and the modpack path is untouched.
 *
 * <b>Everything is read defensively.</b> This is a third party's JSON over a network, parsed on a
 * phone that cannot be debugged. A missing field returns null or a default and the mod is skipped;
 * nothing here throws on shape.
 */
public final class ModrinthMods {
    private static final String BASE = "https://api.modrinth.com/v2";

    /**
     * Modrinth asks callers to identify themselves, and rate-limits anonymous traffic harder.
     * Naming the project is both the polite thing and the thing that keeps this working.
     */
    private static final String USER_AGENT =
            "pokitw/Amethyst-Android (Minecraft launcher for Android)";

    /** How many results one page asks for. */
    public static final int PAGE_SIZE = 30;

    /** Long enough for a slow phone connection, short enough that a stall is not forever. */
    private static final int TIMEOUT_MS = 15000;

    private ModrinthMods() {}

    /** Why a request came back with nothing, so the screen can say something true about it. */
    public enum Failure { NONE, OFFLINE, RATE_LIMITED, SERVER }

    /**
     * What the last request did.
     *
     * A field rather than a return value because the two calls that matter already return the
     * thing they fetched, and threading a result type through them would be a lot of ceremony for
     * one string on one screen. Written and read from the same background call in practice.
     */
    private static volatile Failure lastFailure = Failure.NONE;

    public static Failure lastFailure() {
        return lastFailure;
    }

    /**
     * One GET, with timeouts and a response code that is actually looked at.
     *
     * {@link ApiHandler} is the shared client and it does neither: no timeout, and every failure
     * becomes null. That is survivable for a modpack install behind a progress bar and not for a
     * search box, where a stalled socket is a spinner that never stops and a 429 is a "check your
     * connection" that insults someone whose connection is fine.
     */
    @Nullable
    private static String fetch(@NonNull String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code == 429) {
                lastFailure = Failure.RATE_LIMITED;
                return null;
            }
            if (code < 200 || code >= 300) {
                lastFailure = Failure.SERVER;
                Log.w("ModrinthMods", "Modrinth answered " + code + " for " + url);
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                lastFailure = Failure.NONE;
                return Tools.read(input);
            }
        } catch (Throwable t) {
            lastFailure = Failure.OFFLINE;
            Log.w("ModrinthMods", "Could not reach Modrinth", t);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Nullable
    private static JsonElement getJson(@NonNull String url) {
        String body = fetch(url);
        if (body == null) return null;
        try {
            return JsonParser.parseString(body);
        } catch (Throwable t) {
            lastFailure = Failure.SERVER;
            Log.w("ModrinthMods", "Modrinth sent something that is not JSON", t);
            return null;
        }
    }

    /** Query string assembly, kept here so every request goes out encoded the same way. */
    @NonNull
    private static String withQuery(@NonNull String url, @NonNull Map<String, Object> params) {
        if (params.isEmpty()) return url;
        StringBuilder built = new StringBuilder(url).append('?');
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) built.append('&');
            first = false;
            built.append(encode(entry.getKey())).append('=')
                    .append(encode(String.valueOf(entry.getValue())));
        }
        return built.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Throwable t) {
            return value;
        }
    }

    /* ------------------------------------------------------------------ models */

    /** One search hit, with only what a row needs to draw itself. */
    public static final class Hit {
        public final String projectId;
        public final String slug;
        public final String title;
        public final String description;
        @Nullable public final String iconUrl;
        public final int downloads;
        /** "mod", "shader", "resourcepack": what folder an install of this belongs in. */
        public final String projectType;

        Hit(String projectId, String slug, String title, String description,
            @Nullable String iconUrl, int downloads, String projectType) {
            this.projectId = projectId;
            this.slug = slug;
            this.title = title;
            this.description = description;
            this.iconUrl = iconUrl;
            this.downloads = downloads;
            this.projectType = projectType;
        }

        /** The key the icon cache is keyed on, shared with the modpack browser's cache. */
        public String iconCacheTag() {
            return "modrinth_" + projectId;
        }
    }

    /** A page of hits, plus what the next page would need. */
    public static final class Page {
        public final List<Hit> hits;
        public final int nextOffset;
        public final int total;

        Page(List<Hit> hits, int nextOffset, int total) {
            this.hits = hits;
            this.nextOffset = nextOffset;
            this.total = total;
        }

        public boolean hasMore() {
            return nextOffset < total && !hits.isEmpty();
        }
    }

    /** One downloadable file of one version of a mod. */
    public static final class File {
        public final String versionId;
        public final String projectId;
        public final String versionNumber;
        /** "release", "beta" or "alpha". A release is preferred wherever there is a choice. */
        public final String channel;
        public final String fileName;
        public final String url;
        @Nullable public final String sha1;
        public final int size;
        public final List<Dependency> dependencies;

        File(String versionId, String projectId, String versionNumber, String channel,
             String fileName, String url, @Nullable String sha1, int size,
             List<Dependency> dependencies) {
            this.versionId = versionId;
            this.projectId = projectId;
            this.versionNumber = versionNumber;
            this.channel = channel;
            this.fileName = fileName;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
            this.dependencies = dependencies;
        }

        public boolean isRelease() {
            return "release".equals(channel);
        }
    }

    /** One thing a version says it needs. */
    public static final class Dependency {
        @Nullable public final String projectId;
        @Nullable public final String versionId;
        public final String type;

        Dependency(@Nullable String projectId, @Nullable String versionId, String type) {
            this.projectId = projectId;
            this.versionId = versionId;
            this.type = type;
        }

        /**
         * Only "required" is installed automatically.
         *
         * "optional" is a suggestion, "incompatible" is a warning and "embedded" is already inside
         * the jar. Installing any of those on the player's behalf would be putting mods in their
         * folder that they did not ask for and cannot easily attribute.
         */
        public boolean isRequired() {
            return "required".equals(type);
        }
    }

    /* ------------------------------------------------------------------ requests */

    /**
     * Search for mods.
     *
     * @param query      what was typed, or empty for the front page
     * @param mcVersion  restrict to this Minecraft version, or null for any
     * @param loaderId   restrict to this loader ("fabric", "forge", ...), or null for any
     * @param offset     how many results have already been seen
     * @param projectType "mod", "shader" or "resourcepack"
     * @return the page, or null when the request failed
     */
    @Nullable
    public static Page search(@NonNull String query, @Nullable String mcVersion,
                              @Nullable String loaderId, int offset,
                              @NonNull String projectType) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("limit", PAGE_SIZE);
        params.put("offset", offset);
        params.put("index", query.isEmpty() ? "downloads" : "relevance");
        params.put("facets", facets(mcVersion, loaderId, projectType));

        JsonObject response = GsonJsonUtils.getJsonObjectSafe(
                getJson(withQuery(BASE + "/search", params)));
        if (response == null) return null;
        JsonArray hits = GsonJsonUtils.getJsonArraySafe(response, "hits");
        if (hits == null) return null;

        List<Hit> results = new ArrayList<>(hits.size());
        for (JsonElement element : hits) {
            JsonObject hit = GsonJsonUtils.getJsonObjectSafe(element);
            if (hit == null) continue;
            String id = GsonJsonUtils.getStringSafe(hit, "project_id");
            String title = GsonJsonUtils.getStringSafe(hit, "title");
            // Without an id there is nothing to install and without a title nothing to show, so a
            // hit missing either is dropped rather than drawn as a blank row.
            if (id == null || title == null) continue;
            results.add(new Hit(
                    id,
                    orEmpty(GsonJsonUtils.getStringSafe(hit, "slug")),
                    title,
                    orEmpty(GsonJsonUtils.getStringSafe(hit, "description")),
                    GsonJsonUtils.getStringSafe(hit, "icon_url"),
                    GsonJsonUtils.getIntSafe(hit, "downloads", 0),
                    orEmpty(GsonJsonUtils.getStringSafe(hit, "project_type"))
            ));
        }
        int total = GsonJsonUtils.getIntSafe(response, "total_hits", results.size());
        return new Page(results, offset + hits.size(), total);
    }

    /**
     * The facet expression, which is how Modrinth's search takes filters.
     *
     * Facets are AND-ed between the outer groups and OR-ed inside them, so each filter is its own
     * single-element group. The loader filter goes under {@code categories} and not under some
     * {@code loaders} key: the search index folds loaders in with the tag list, which is a wrinkle
     * of the search endpoint only. The version endpoint below takes a real {@code loaders} array.
     */
    @NonNull
    static String facets(@Nullable String mcVersion, @Nullable String loaderId,
                         @NonNull String projectType) {
        StringBuilder facets = new StringBuilder("[");
        facets.append(String.format("[\"project_type:%s\"]", projectType));
        if (isSet(mcVersion)) {
            facets.append(String.format(",[\"versions:%s\"]", mcVersion));
        }
        if (isSet(loaderId)) {
            facets.append(String.format(",[\"categories:%s\"]", loaderId));
        }
        return facets.append("]").toString();
    }

    /**
     * Every file of a project that fits, newest first.
     *
     * Filtered by the server rather than here, so a mod with four hundred versions costs one small
     * response instead of a large one this would then throw most of away.
     */
    @NonNull
    public static List<File> versions(@NonNull String projectId, @Nullable String mcVersion,
                                      @Nullable String loaderId) {
        HashMap<String, Object> params = new HashMap<>();
        if (isSet(mcVersion)) params.put("game_versions", jsonArray(mcVersion));
        if (isSet(loaderId)) params.put("loaders", jsonArray(loaderId));

        JsonElement response = getJson(
                withQuery(BASE + "/project/" + projectId + "/version", params));
        return parseVersions(response != null && response.isJsonArray()
                ? response.getAsJsonArray() : null);
    }

    /** One exact version, for a dependency that named the version rather than the project. */
    @Nullable
    public static File version(@NonNull String versionId) {
        return parseVersion(GsonJsonUtils.getJsonObjectSafe(
                getJson(BASE + "/version/" + versionId)));
    }

    /**
     * Parse a {@code /version} array.
     *
     * Split out from the request so the shipped parsing can be driven from fixtures, which is the
     * only way any of this is testable without the network: see {@code scripts/modrinthsim/}.
     */
    @NonNull
    public static List<File> parseVersions(@Nullable JsonArray response) {
        List<File> files = new ArrayList<>();
        if (response == null) return files;
        for (JsonElement element : response) {
            File file = parseVersion(GsonJsonUtils.getJsonObjectSafe(element));
            if (file != null) files.add(file);
        }
        return files;
    }

    @Nullable
    static File parseVersion(@Nullable JsonObject version) {
        if (version == null) return null;
        JsonArray files = GsonJsonUtils.getJsonArraySafe(version, "files");
        if (files == null || files.size() == 0) return null;

        // The primary file, falling back to the first. A mod that ships sources or a javadoc jar
        // lists them here too, and only one of them is the thing to install.
        JsonObject chosen = null;
        for (JsonElement element : files) {
            JsonObject candidate = GsonJsonUtils.getJsonObjectSafe(element);
            if (candidate == null) continue;
            if (chosen == null) chosen = candidate;
            JsonElement primary = GsonJsonUtils.getElementSafe(candidate, "primary");
            if (primary != null && primary.isJsonPrimitive() && primary.getAsBoolean()) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null) return null;

        String url = GsonJsonUtils.getStringSafe(chosen, "url");
        String fileName = GsonJsonUtils.getStringSafe(chosen, "filename");
        String versionId = GsonJsonUtils.getStringSafe(version, "id");
        if (url == null || fileName == null || versionId == null) return null;

        JsonObject hashes = GsonJsonUtils.getJsonObjectSafe(chosen, "hashes");
        String sha1 = hashes == null ? null : GsonJsonUtils.getStringSafe(hashes, "sha1");

        List<Dependency> dependencies = new ArrayList<>();
        JsonArray dependencyArray = GsonJsonUtils.getJsonArraySafe(version, "dependencies");
        if (dependencyArray != null) {
            for (JsonElement element : dependencyArray) {
                JsonObject dependency = GsonJsonUtils.getJsonObjectSafe(element);
                if (dependency == null) continue;
                String type = GsonJsonUtils.getStringSafe(dependency, "dependency_type");
                dependencies.add(new Dependency(
                        GsonJsonUtils.getStringSafe(dependency, "project_id"),
                        GsonJsonUtils.getStringSafe(dependency, "version_id"),
                        type == null ? "" : type
                ));
            }
        }

        String channel = GsonJsonUtils.getStringSafe(version, "version_type");
        return new File(
                versionId,
                orEmpty(GsonJsonUtils.getStringSafe(version, "project_id")),
                orEmpty(GsonJsonUtils.getStringSafe(version, "version_number")),
                channel == null ? "release" : channel,
                fileName,
                url,
                sha1,
                GsonJsonUtils.getIntSafe(chosen, "size", 0),
                dependencies
        );
    }

    /**
     * The one to install out of a list.
     *
     * A release wherever there is one, because a beta arriving silently on a one-tap install is
     * the kind of surprise that gets blamed on the launcher rather than on the mod. Modrinth
     * returns newest first, so the first match is the newest match.
     */
    @Nullable
    public static File best(@NonNull List<File> files) {
        for (File file : files) {
            if (file.isRelease()) return file;
        }
        return files.isEmpty() ? null : files.get(0);
    }

    /* ------------------------------------------------------------------ small helpers */

    private static boolean isSet(@Nullable String value) {
        return value != null && !value.isEmpty();
    }

    /** A one-element JSON array, which is the shape the version endpoint's filters take. */
    private static String jsonArray(String value) {
        return "[\"" + value.toLowerCase(Locale.ROOT) + "\"]";
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
