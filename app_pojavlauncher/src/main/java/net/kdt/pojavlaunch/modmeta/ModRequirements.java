package net.kdt.pojavlaunch.modmeta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What a mod jar says it needs, in the four dialects four generations of the ecosystem wrote.
 *
 * <b>There is no "declared Minecraft version" field to read.</b> In every modern format the
 * Minecraft requirement <i>is</i> a dependency entry, sitting beside the mod's other dependencies
 * and written in the same grammar:
 *
 * <pre>
 *   fabric.mod.json   "depends": { "minecraft": "&gt;=1.20.1 &lt;1.21", "fabric-api": "*" }
 *   mods.toml         [[dependencies.jei]] modId="minecraft" versionRange="[1.20.1,1.21)"
 * </pre>
 *
 * So "does this mod match my version" and "is anything this mod needs missing" are one parser and
 * two questions over it, and building either alone means writing both.
 *
 * <p><b>The one rule the whole thing hangs on:</b> anything not fully understood answers
 * {@link VersionPredicate.Verdict#UNKNOWN}, never {@code CONFLICTS}. A missed warning costs
 * nothing, because the player is exactly where they already were. A false one tells somebody to
 * turn off a mod that works, which breaks their game and is the launcher's fault. That asymmetry
 * is the same one {@code Tools.compareSHA1} encodes when it fake-matches on a read error, and it
 * must survive anybody later tidying this file.
 *
 * <p>Plain Java over Gson and {@code java.util.zip}, with no Android types, so
 * {@code scripts/modmetasim} can build real jars on disk and read them back through this exact
 * class. Written in the existing Kotlin readers' idiom it could not be checked by anything: there
 * is no {@code org.json} in the build container and no Kotlin compiler either, so a harness could
 * only ever have driven a copy (§16.25).
 */
public final class ModRequirements {

    /** One thing a mod needs, as the mod itself states it. */
    public static final class Dep {
        /** The mod id depended on, lowercased. */
        public final String id;
        /** The version predicate, in whichever grammar the format uses. Empty means any. */
        public final String range;
        /** Optional dependencies are suggestions, and acting on a suggestion is not this app's job. */
        public final boolean required;

        Dep(String id, String range, boolean required) {
            this.id = id;
            this.range = range == null ? "" : range.trim();
            this.required = required;
        }
    }

    /** Which format this was read from, which is also the loader the mod is built for. */
    public static final String LOADER_FABRIC = "FABRIC";
    public static final String LOADER_QUILT = "QUILT";
    public static final String LOADER_NEOFORGE = "NEOFORGE";
    public static final String LOADER_FORGE = "FORGE";

    /**
     * Ids that are the platform rather than an installed mod.
     *
     * Every mod depends on at least one of these, so a graph that did not exclude them would
     * report a missing dependency on literally every mod in the folder. `fabric-api` is
     * deliberately <b>not</b> here: it is a real mod that a player really can be missing, and
     * missing it is one of the most common reasons a Fabric setup does not start.
     */
    private static final List<String> PLATFORM_IDS = Collections.unmodifiableList(Arrays.asList(
            "minecraft", "java", "forge", "neoforge",
            "fabricloader", "fabric-loader", "quilt_loader", "quiltloader"
    ));

    /** The id every format uses for the game itself. */
    public static final String MINECRAFT_ID = "minecraft";

    public final String modId;
    public final String loader;
    public final List<Dep> dependencies;
    /**
     * Other ids this mod satisfies.
     *
     * <b>Fabric API is why this exists.</b> It ships as roughly forty modules that each declare
     * {@code provides}, and mods depend on those module ids rather than on {@code fabric-api}
     * itself. A graph that reads {@code depends} and not {@code provides} reports a missing
     * dependency on nearly every Fabric mod installed, which looks exactly like the feature
     * working hard.
     */
    public final List<String> provides;

    private ModRequirements(String modId, String loader, List<Dep> dependencies, List<String> provides) {
        this.modId = modId;
        this.loader = loader;
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.provides = Collections.unmodifiableList(provides);
    }

    /** The Minecraft version predicate this mod declares, or null when it declares none. */
    public String minecraftRange() {
        for (Dep dep : dependencies) {
            if (MINECRAFT_ID.equals(dep.id) && dep.range.length() > 0) return dep.range;
        }
        return null;
    }

    /** Whether an id names the platform rather than something that could be installed. */
    public static boolean isPlatform(String id) {
        return id != null && PLATFORM_IDS.contains(id.toLowerCase(Locale.ROOT));
    }

    /** Which of the two version grammars this mod's predicates are written in. */
    public boolean usesMavenRanges() {
        return LOADER_FORGE.equals(loader) || LOADER_NEOFORGE.equals(loader);
    }

    /**
     * Read a jar's own statement of what it needs.
     *
     * <b>Most specific format first</b>, the same order and for the same reason as the existing
     * metadata reader: a Quilt jar almost always also ships a {@code fabric.mod.json} for
     * compatibility, and a NeoForge jar almost always also ships the older {@code mods.toml}.
     * Checking in the obvious order reports both as the older loader, and then judges them against
     * the wrong loader's rules.
     *
     * @return what the jar declares, or null when nothing in it could be read
     */
    public static ModRequirements read(ZipFile zip) {
        ModRequirements quilt = readQuilt(zip);
        if (quilt != null) return quilt;
        ModRequirements fabric = readFabric(zip);
        if (fabric != null) return fabric;
        ModRequirements neoforge = readToml(zip, "META-INF/neoforge.mods.toml", LOADER_NEOFORGE);
        if (neoforge != null) return neoforge;
        ModRequirements forge = readToml(zip, "META-INF/mods.toml", LOADER_FORGE);
        if (forge != null) return forge;
        return readLegacyForge(zip);
    }

    /* ------------------------------------------------------------------ fabric */

    private static ModRequirements readFabric(ZipFile zip) {
        JsonObject json = readJson(zip, "fabric.mod.json");
        if (json == null) return null;
        List<Dep> deps = new ArrayList<>();
        readFabricDepends(json.get("depends"), true, deps);
        // Recommended and suggested are not requirements, and installing on a suggestion would put
        // jars in somebody's folder that they never chose. Read so they are not mistaken for
        // requirements by a later reader, and marked optional so nothing acts on them.
        readFabricDepends(json.get("recommends"), false, deps);
        readFabricDepends(json.get("suggests"), false, deps);
        return new ModRequirements(
                optString(json, "id"), LOADER_FABRIC, deps, readStringArray(json.get("provides")));
    }

    /**
     * Fabric's {@code depends} is a map of id to predicate, and a predicate is either one string
     * or an array of them.
     *
     * The array form is an <b>OR</b>, not an AND: {@code ["1.20.1", "1.20.2"]} means either. Read
     * as a conjunction it would be unsatisfiable and every such mod would report a conflict.
     */
    private static void readFabricDepends(JsonElement element, boolean required, List<Dep> into) {
        if (element == null || !element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String id = lower(entry.getKey());
            if (id == null) continue;
            JsonElement value = entry.getValue();
            String range;
            if (value != null && value.isJsonArray()) {
                StringBuilder builder = new StringBuilder();
                for (JsonElement each : value.getAsJsonArray()) {
                    if (each == null || !each.isJsonPrimitive()) continue;
                    if (builder.length() > 0) builder.append(VersionPredicate.OR_SEPARATOR);
                    builder.append(each.getAsString());
                }
                range = builder.toString();
            } else if (value != null && value.isJsonPrimitive()) {
                range = value.getAsString();
            } else {
                range = "";
            }
            into.add(new Dep(id, range, required));
        }
    }

    /* ------------------------------------------------------------------ quilt */

    private static ModRequirements readQuilt(ZipFile zip) {
        JsonObject json = readJson(zip, "quilt.mod.json");
        if (json == null) return null;
        JsonObject loader = json.getAsJsonObject("quilt_loader");
        if (loader == null) return null;
        List<Dep> deps = new ArrayList<>();
        readQuiltDepends(loader.get("depends"), deps);
        return new ModRequirements(optString(loader, "id"), LOADER_QUILT, deps,
                readQuiltProvides(loader.get("provides")));
    }

    /**
     * Quilt writes each dependency as either a bare id or an object, and marks the ones that are
     * merely suggestions with {@code optional}.
     */
    private static void readQuiltDepends(JsonElement element, List<Dep> into) {
        if (element == null || !element.isJsonArray()) return;
        for (JsonElement each : element.getAsJsonArray()) {
            if (each == null) continue;
            if (each.isJsonPrimitive()) {
                String id = lower(each.getAsString());
                if (id != null) into.add(new Dep(id, "", true));
            } else if (each.isJsonObject()) {
                JsonObject object = each.getAsJsonObject();
                String id = lower(optString(object, "id"));
                if (id == null) continue;
                JsonElement versions = object.get("versions");
                String range = versions != null && versions.isJsonPrimitive()
                        ? versions.getAsString() : "";
                boolean optional = object.has("optional")
                        && object.get("optional").isJsonPrimitive()
                        && object.get("optional").getAsBoolean();
                into.add(new Dep(id, range, !optional));
            }
        }
    }

    /** Quilt's {@code provides} entries are bare ids or objects carrying one. */
    private static List<String> readQuiltProvides(JsonElement element) {
        List<String> provides = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return provides;
        for (JsonElement each : element.getAsJsonArray()) {
            if (each == null) continue;
            String id = null;
            if (each.isJsonPrimitive()) id = lower(each.getAsString());
            else if (each.isJsonObject()) id = lower(optString(each.getAsJsonObject(), "id"));
            if (id != null) provides.add(id);
        }
        return provides;
    }

    /* ------------------------------------------------------------------ forge and neoforge */

    /**
     * Enough TOML to read a mods file, and no more.
     *
     * A real parser would be a dependency this launcher does not ship. What is needed is two
     * shapes: the {@code [[mods]]} array of tables, whose first entry names the mod, and the
     * {@code [[dependencies.<modid>]]} arrays that follow it.
     */
    private static ModRequirements readToml(ZipFile zip, String path, String loader) {
        String text = readText(zip, path);
        if (text == null) return null;
        List<Map<String, String>> tables = parseTomlTables(text);

        String modId = null;
        List<Dep> deps = new ArrayList<>();
        for (Map<String, String> table : tables) {
            String header = table.get(TABLE_HEADER);
            if (header == null) continue;
            if (header.equals("mods")) {
                // The first [[mods]] entry is the mod. A jar can carry several, and the rest are
                // sub-mods that share this file's dependency list.
                if (modId == null) modId = lower(table.get("modId"));
            } else if (header.startsWith("dependencies")) {
                String id = lower(table.get("modId"));
                if (id == null) continue;
                String range = table.get("versionRange");
                deps.add(new Dep(id, range, isTomlRequired(table)));
            }
        }
        if (modId == null && deps.isEmpty()) return null;
        return new ModRequirements(modId, loader, deps, new ArrayList<String>());
    }

    /**
     * Whether a Forge or NeoForge dependency is required.
     *
     * <b>NeoForge renamed this field.</b> Forge writes {@code mandatory = true}; NeoForge writes
     * {@code type = "required"} and accepts {@code "optional"}, {@code "incompatible"} and
     * {@code "discouraged"}. Reading only one of the two spellings makes every dependency in the
     * other format either always required or always optional, and both of those are silent: one
     * warns about everything and one warns about nothing.
     *
     * <p>Absent means required, which is what both loaders default to. An entry that says it is
     * incompatible is not a requirement at all and must never be read as one, or a mod declaring
     * what it will not run beside would be reported as needing it.
     */
    private static boolean isTomlRequired(Map<String, String> table) {
        String type = table.get("type");
        if (type != null) {
            String value = type.trim().toLowerCase(Locale.ROOT);
            return value.equals("required");
        }
        String mandatory = table.get("mandatory");
        if (mandatory != null) return !mandatory.trim().equalsIgnoreCase("false");
        return true;
    }

    /** Where a table's own header is stashed, in a key no TOML file can collide with. */
    private static final String TABLE_HEADER = "]header[";

    /**
     * Split a mods file into its tables, keeping the key/value pairs of each.
     *
     * Values are unquoted and triple-quoted blocks are read to their fence, because a description
     * written as a block would otherwise swallow the keys after it as if they were prose.
     */
    private static List<Map<String, String>> parseTomlTables(String text) {
        List<Map<String, String>> tables = new ArrayList<>();
        Map<String, String> current = null;
        String[] lines = text.split("\r\n|\n|\r", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            i++;
            if (line.length() == 0 || line.charAt(0) == '#') continue;
            if (line.charAt(0) == '[') {
                current = new LinkedHashMap<>();
                current.put(TABLE_HEADER, tableHeader(line));
                tables.add(current);
                continue;
            }
            if (current == null) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (value.startsWith("'''") || value.startsWith("\"\"\"")) {
                String fence = value.substring(0, 3);
                StringBuilder builder = new StringBuilder(value.substring(3));
                while (builder.indexOf(fence) < 0 && i < lines.length) {
                    builder.append('\n').append(lines[i]);
                    i++;
                }
                int end = builder.indexOf(fence);
                value = end < 0 ? builder.toString() : builder.substring(0, end);
            }
            current.put(key, unquote(value));
        }
        return tables;
    }

    /** {@code [[dependencies.jei]]} to {@code dependencies.jei}, brackets and comments removed. */
    private static String tableHeader(String line) {
        String header = line;
        int comment = header.indexOf('#');
        if (comment >= 0) header = header.substring(0, comment);
        header = header.trim();
        while (header.startsWith("[")) header = header.substring(1);
        while (header.endsWith("]")) header = header.substring(0, header.length() - 1);
        return header.trim();
    }

    private static String unquote(String value) {
        String result = value.trim();
        int comment = result.indexOf('#');
        // Only a comment that starts outside a quoted value, which for these files means one that
        // starts after the closing quote.
        if (comment >= 0 && !isInsideQuotes(result, comment)) result = result.substring(0, comment).trim();
        if (result.length() >= 2) {
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                result = result.substring(1, result.length() - 1);
            }
        }
        return result.trim();
    }

    private static boolean isInsideQuotes(String value, int index) {
        int quotes = 0;
        for (int i = 0; i < index; i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\'') quotes++;
        }
        return quotes % 2 == 1;
    }

    /* ------------------------------------------------------------------ legacy forge */

    /**
     * The only format with a standalone Minecraft version field, which is turned into a dependency
     * so everything downstream sees one shape.
     */
    private static ModRequirements readLegacyForge(ZipFile zip) {
        String text = readText(zip, "mcmod.info");
        if (text == null) return null;
        JsonObject entry = null;
        try {
            String trimmed = text.trim();
            // Written both ways in the wild: a bare array, and an object with a modList inside it.
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (parsed.isJsonArray() && parsed.getAsJsonArray().size() > 0) {
                JsonElement first = parsed.getAsJsonArray().get(0);
                if (first.isJsonObject()) entry = first.getAsJsonObject();
            } else if (parsed.isJsonObject()) {
                JsonArray list = parsed.getAsJsonObject().getAsJsonArray("modList");
                if (list != null && list.size() > 0 && list.get(0).isJsonObject()) {
                    entry = list.get(0).getAsJsonObject();
                }
            }
        } catch (Throwable t) {
            return null;
        }
        if (entry == null) return null;

        List<Dep> deps = new ArrayList<>();
        String mcVersion = optString(entry, "mcversion");
        if (mcVersion != null && mcVersion.length() > 0) {
            deps.add(new Dep(MINECRAFT_ID, mcVersion, true));
        }
        for (String required : readStringArray(entry.get("requiredMods"))) {
            // Written as "id@[1.0,)", the id being everything before the marker.
            int at = required.indexOf('@');
            String id = lower(at >= 0 ? required.substring(0, at) : required);
            if (id != null) deps.add(new Dep(id, at >= 0 ? required.substring(at + 1) : "", true));
        }
        return new ModRequirements(
                lower(optString(entry, "modid")), LOADER_FORGE, deps, new ArrayList<String>());
    }

    /* ------------------------------------------------------------------ small helpers */

    private static List<String> readStringArray(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return values;
        for (JsonElement each : element.getAsJsonArray()) {
            if (each != null && each.isJsonPrimitive()) {
                String value = lower(each.getAsString());
                if (value != null) values.add(value);
            }
        }
        return values;
    }

    private static String optString(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return null;
        String text = value.getAsString();
        return text == null || text.trim().length() == 0 ? null : text.trim();
    }

    private static String lower(String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.length() == 0 ? null : trimmed;
    }

    private static JsonObject readJson(ZipFile zip, String path) {
        String text = readText(zip, path);
        if (text == null) return null;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Throwable t) {
            // Fabric permits comments and trailing commas that strict parsers reject. A mod whose
            // metadata will not parse is one nothing can be concluded about, which is a null here
            // and reads as "not checked" everywhere above.
            return null;
        }
    }

    private static String readText(ZipFile zip, String path) {
        try {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) return null;
            InputStream in = zip.getInputStream(entry);
            if (in == null) return null;
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                return new String(out.toByteArray(), "UTF-8");
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
