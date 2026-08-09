package net.kdt.pojavlaunch.ui.content

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** What a mod jar says about itself, whichever of the four ways it says it. */
class ModMetadata(
    val name: String?,
    val description: String?,
    val version: String?,
    val loader: String?,
    val iconPath: String?
)

/** What a resource or shader pack says about itself. */
class PackMetadata(val description: String?, val format: Int?)

/**
 * Read a mod jar's own description of itself.
 *
 * Four formats, because four generations of the ecosystem wrote four, and they are tried
 * <b>most specific first</b> for a reason that is not obvious: a Quilt jar almost always also
 * ships a `fabric.mod.json` for compatibility, and a NeoForge jar almost always also ships the
 * older `mods.toml`. Checking in the obvious order reports both as the older loader.
 */
fun readModMetadata(file: File): ModMetadata? = try {
    ZipFile(file).use { zip ->
        readQuilt(zip)
            ?: readFabric(zip)
            ?: readToml(zip, "META-INF/neoforge.mods.toml", "NEOFORGE")
            ?: readToml(zip, "META-INF/mods.toml", "FORGE")
            ?: readLegacyForge(zip)
    }
} catch (t: Throwable) {
    null
}

/** The icon a mod ships, decoded small. */
fun readModIcon(file: File, maxPixels: Int): Bitmap? = try {
    val path = readModMetadata(file)?.iconPath ?: return null
    ZipFile(file).use { zip ->
        val entry = zip.getEntry(path) ?: zip.getEntry(path.removePrefix("/")) ?: return null
        decodeBytes(zip.getInputStream(entry).use { it.readBytes() }, maxPixels)
    }
} catch (t: Throwable) {
    null
}

/**
 * Read a pack's `pack.mcmeta`, whether it is a folder or a zip.
 *
 * Both shapes are legal and the game reads both, so the screen has to as well — a shader pack
 * downloaded from one place arrives zipped and from another arrives already unpacked.
 */
fun readPackMetadata(file: File): PackMetadata? = try {
    val text = readPackEntry(file, "pack.mcmeta")?.toString(Charsets.UTF_8)
    if (text == null) null else {
        val pack = JSONObject(text).optJSONObject("pack")
        if (pack == null) null else PackMetadata(
            description = flattenDescription(pack.opt("description")),
            format = if (pack.has("pack_format")) pack.optInt("pack_format") else null
        )
    }
} catch (t: Throwable) {
    null
}

fun readPackIcon(file: File, maxPixels: Int): Bitmap? = try {
    readPackEntry(file, "pack.png")?.let { decodeBytes(it, maxPixels) }
} catch (t: Throwable) {
    null
}

private fun readPackEntry(file: File, name: String): ByteArray? {
    if (file.isDirectory) {
        val entry = File(file, name)
        return if (entry.isFile) entry.readBytes() else null
    }
    return try {
        ZipFile(file).use { zip ->
            // Zipped from outside the pack folder as often as from inside it, so the entry may be
            // one level down. Only one level: deeper than that and it is something else's copy.
            val direct = zip.getEntry(name)
            val entry = direct ?: zip.entries().asSequence().firstOrNull {
                val parts = it.name.replace('\\', '/').split('/')
                parts.size == 2 && parts[1] == name
            }
            if (entry == null) null else zip.getInputStream(entry).use { it.readBytes() }
        }
    } catch (t: Throwable) {
        null
    }
}

/**
 * A pack description is a plain string in older packs and a chat component — an object, or a list
 * of them — in newer ones. Only the words are wanted.
 */
private fun flattenDescription(value: Any?): String? = when (value) {
    is String -> value.trim().ifEmpty { null }
    is JSONObject -> value.optString("text", "").trim().ifEmpty { null }
    is JSONArray -> (0 until value.length())
        .mapNotNull { flattenDescription(value.opt(it)) }
        .joinToString("")
        .trim()
        .ifEmpty { null }
    else -> null
}

/* ------------------------------------------------------------------ the four mod formats */

private fun readFabric(zip: ZipFile): ModMetadata? {
    val json = zip.readJson("fabric.mod.json") ?: return null
    return ModMetadata(
        name = json.optStringOrNull("name"),
        description = json.optStringOrNull("description"),
        version = json.optStringOrNull("version"),
        loader = "FABRIC",
        iconPath = json.optIconPath()
    )
}

private fun readQuilt(zip: ZipFile): ModMetadata? {
    val loader = zip.readJson("quilt.mod.json")?.optJSONObject("quilt_loader") ?: return null
    val metadata = loader.optJSONObject("metadata")
    return ModMetadata(
        name = metadata?.optStringOrNull("name"),
        description = metadata?.optStringOrNull("description"),
        version = loader.optStringOrNull("version"),
        loader = "QUILT",
        iconPath = metadata?.optIconPath()
    )
}

private fun readLegacyForge(zip: ZipFile): ModMetadata? {
    val text = zip.readText("mcmod.info") ?: return null
    val entry = try {
        val trimmed = text.trim()
        // Written both ways in the wild: a bare array, and an object with a modList inside it.
        if (trimmed.startsWith("[")) JSONArray(trimmed).optJSONObject(0)
        else JSONObject(trimmed).optJSONArray("modList")?.optJSONObject(0)
    } catch (t: Throwable) {
        null
    } ?: return null
    return ModMetadata(
        name = entry.optStringOrNull("name"),
        description = entry.optStringOrNull("description"),
        version = entry.optStringOrNull("version"),
        loader = "FORGE",
        iconPath = entry.optStringOrNull("logoFile")
    )
}

/**
 * Enough TOML to read a `mods.toml`, and no more.
 *
 * A real parser would be a dependency, and this file has one shape: a `[[mods]]` table of
 * `key = "value"` lines, with descriptions written as triple-quoted blocks.
 */
private fun readToml(zip: ZipFile, path: String, loader: String): ModMetadata? {
    val text = zip.readText(path) ?: return null
    val fields = parseFirstModsTable(text)
    if (fields.isEmpty()) return null
    return ModMetadata(
        name = fields["displayName"],
        description = fields["description"],
        // Forge writes a literal ${file.jarVersion} here, filled in from the manifest at build time.
        version = fields["version"]?.takeUnless { it.contains("\${") },
        loader = loader,
        iconPath = fields["logoFile"]
    )
}

private fun parseFirstModsTable(text: String): Map<String, String> {
    val fields = LinkedHashMap<String, String>()
    val lines = text.lines()
    var inMods = false
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        i++
        if (line.isEmpty() || line.startsWith("#")) continue
        if (line.startsWith("[")) {
            // The first [[mods]] table is the mod. A second is a second mod in the same jar, and a
            // [[dependencies]] table is not a mod at all.
            if (line.startsWith("[[mods]]")) {
                if (inMods) break
                inMods = true
            } else if (inMods) break
            continue
        }
        if (!inMods) continue
        val separator = line.indexOf('=')
        if (separator <= 0) continue
        val key = line.substring(0, separator).trim()
        var value = line.substring(separator + 1).trim()
        if (value.startsWith("'''") || value.startsWith("\"\"\"")) {
            val fence = value.take(3)
            val builder = StringBuilder(value.removePrefix(fence))
            while (!builder.contains(fence) && i < lines.size) {
                builder.append('\n').append(lines[i])
                i++
            }
            value = builder.toString().substringBefore(fence)
        }
        fields[key] = value.trim().trim('"', '\'').trim()
    }
    return fields
}

/* ------------------------------------------------------------------ small helpers */

private fun ZipFile.readText(path: String): String? = try {
    getEntry(path)?.let { entry -> getInputStream(entry).use { it.readBytes() } }
        ?.toString(Charsets.UTF_8)
} catch (t: Throwable) {
    null
}

private fun ZipFile.readJson(path: String): JSONObject? = try {
    readText(path)?.let { JSONObject(it) }
} catch (t: Throwable) {
    // Fabric permits comments and trailing commas that org.json rejects. A mod whose metadata will
    // not parse still lists, under its file name.
    null
}

private fun JSONObject.optStringOrNull(key: String): String? =
    (opt(key) as? String)?.trim()?.ifEmpty { null }

/**
 * Fabric writes the icon either as a path or as a map of sizes to paths. Matched on the actual
 * type, because `optString` renders a nested object as its own JSON text and the result would be
 * a filename nothing can open.
 */
private fun JSONObject.optIconPath(): String? = when (val value = opt("icon")) {
    is String -> value.trim().ifEmpty { null }
    is JSONObject -> {
        val largest = value.keys().asSequence().mapNotNull { it.toIntOrNull() }.maxOrNull()
        if (largest != null) value.optStringOrNull(largest.toString())
        else value.keys().asSequence().firstOrNull()?.let { value.optStringOrNull(it) }
    }
    else -> null
}
