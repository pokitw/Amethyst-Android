package net.kdt.pojavlaunch.ui.mods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import net.kdt.pojavlaunch.ui.home.currentGameDirectory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

/** Which loader a jar declares itself for, which is the only honest way to know. */
enum class ModLoader { FABRIC, QUILT, FORGE, NEOFORGE, UNKNOWN }

/**
 * One mod jar in the profile's mods folder.
 *
 * @param file where it is, including the `.disabled` suffix when it has one
 * @param enabled whether the game will load it
 * @param icon read out of the jar later, because opening every jar twice on the first frame would
 *             stall the list
 */
@Immutable
data class Mod(
    val file: File,
    val enabled: Boolean,
    val id: String?,
    val name: String,
    val version: String?,
    val description: String?,
    val authors: String?,
    val loader: ModLoader,
    val sizeBytes: Long,
    val iconPath: String? = null,
    val icon: Bitmap? = null,
    /** True once the jar has been opened, so a jar with genuinely no icon is not retried. */
    val iconRead: Boolean = false
) {
    /** Stable across an enable/disable, which renames the file. */
    val key: String get() = baseName(file)
}

/** The suffix every loader agrees means "do not load this". */
private const val DISABLED_SUFFIX = ".disabled"

/**
 * The folder the game reads mods from, for whichever profile is selected.
 *
 * Must be called on the main thread — it goes through `LauncherProfiles`, which is unsynchronised
 * static state shared with the launch path. Everything below takes the resolved folder as an
 * argument for exactly that reason.
 */
fun modsDirectory(): File = File(currentGameDirectory(), "mods")

/** The file name with any `.disabled` taken off, which is what identifies a mod across a toggle. */
private fun baseName(file: File): String =
    file.name.removeSuffix(DISABLED_SUFFIX)

/**
 * Every jar in the folder, named and described.
 *
 * Opens each jar once, which is why this belongs on a background thread. Anything that cannot be
 * read still appears — a mod the launcher failed to parse is still a mod the game will try to
 * load, and hiding it would make a crash impossible to explain.
 */
fun loadMods(directory: File): List<Mod> {
    val files = directory.listFiles() ?: return emptyList()
    return files
        .filter { it.isFile && isModJar(it) }
        .map { readMod(it) }
        .sortedWith(compareBy({ !it.enabled }, { it.name.lowercase(Locale.getDefault()) }))
}

private fun isModJar(file: File): Boolean {
    val name = file.name.lowercase(Locale.getDefault())
    return name.endsWith(".jar") || name.endsWith(".jar$DISABLED_SUFFIX")
}

/**
 * Read what a jar says about itself.
 *
 * Four formats, because four generations of the ecosystem wrote four. They are tried in the order
 * that resolves ambiguity: Quilt jars usually ship a `fabric.mod.json` for compatibility, and
 * NeoForge jars often ship the old `mods.toml` as well, so the more specific file is looked for
 * first in both pairs or every one of them would be misreported as the older loader.
 *
 * Everything here is best-effort. A jar that cannot be opened at all still becomes a [Mod] named
 * after its file, because that is what the game will do with it too.
 */
fun readMod(file: File): Mod {
    val enabled = !file.name.endsWith(DISABLED_SUFFIX)
    val fallback = Mod(
        file = file,
        enabled = enabled,
        id = null,
        name = baseName(file).removeSuffix(".jar"),
        version = null,
        description = null,
        authors = null,
        loader = ModLoader.UNKNOWN,
        sizeBytes = file.length()
    )
    return try {
        ZipFile(file).use { zip ->
            readQuilt(zip, fallback)
                ?: readFabric(zip, fallback)
                ?: readToml(zip, fallback, "META-INF/neoforge.mods.toml", ModLoader.NEOFORGE)
                ?: readToml(zip, fallback, "META-INF/mods.toml", ModLoader.FORGE)
                ?: readLegacyForge(zip, fallback)
                ?: fallback
        }
    } catch (t: Throwable) {
        fallback
    }
}

/** The icon a mod ships, decoded small. Null when it has none or the jar will not open. */
fun readModIcon(file: File, path: String, maxPixels: Int = 128): Bitmap? = try {
    ZipFile(file).use { zip ->
        val entry = zip.getEntry(path) ?: zip.getEntry(path.removePrefix("/"))
        if (entry == null) null else {
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply {
                // Mod icons are routinely 512px squares and this draws them at 44dp. Sampling on
                // the way in keeps a folder of two hundred mods out of the bitmap heap.
                inSampleSize = if (longest > maxPixels) longest / maxPixels else 1
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
} catch (t: Throwable) {
    null
}

/* ---------------------------------------------------------------- the four metadata formats */

private fun readFabric(zip: ZipFile, fallback: Mod): Mod? {
    val json = zip.readJson("fabric.mod.json") ?: return null
    return fallback.copy(
        id = json.optStringOrNull("id"),
        name = json.optStringOrNull("name") ?: fallback.name,
        version = json.optStringOrNull("version"),
        description = json.optStringOrNull("description"),
        authors = json.optArrayOfNames("authors"),
        loader = ModLoader.FABRIC,
        iconPath = json.optIconPath()
    )
}

private fun readQuilt(zip: ZipFile, fallback: Mod): Mod? {
    val root = zip.readJson("quilt.mod.json") ?: return null
    val loader = root.optJSONObject("quilt_loader") ?: return null
    val metadata = loader.optJSONObject("metadata")
    return fallback.copy(
        id = loader.optStringOrNull("id"),
        name = metadata?.optStringOrNull("name") ?: fallback.name,
        version = loader.optStringOrNull("version"),
        description = metadata?.optStringOrNull("description"),
        authors = metadata?.optJSONObject("contributors")?.keys()?.asSequence()
            ?.joinToString(", ")?.ifEmpty { null },
        loader = ModLoader.QUILT,
        iconPath = metadata?.optIconPath()
    )
}

private fun readLegacyForge(zip: ZipFile, fallback: Mod): Mod? {
    val text = zip.readText("mcmod.info") ?: return null
    val entry = try {
        // Written both ways in the wild: a bare array, and an object with a modList in it.
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) JSONArray(trimmed).optJSONObject(0)
        else JSONObject(trimmed).optJSONArray("modList")?.optJSONObject(0)
    } catch (t: Throwable) {
        null
    } ?: return null
    return fallback.copy(
        id = entry.optStringOrNull("modid"),
        name = entry.optStringOrNull("name") ?: fallback.name,
        version = entry.optStringOrNull("version"),
        description = entry.optStringOrNull("description"),
        authors = entry.optArrayOfNames("authorList") ?: entry.optArrayOfNames("authors"),
        loader = ModLoader.FORGE,
        iconPath = entry.optStringOrNull("logoFile")
    )
}

/**
 * Enough TOML to read a `mods.toml`, and no more.
 *
 * A real parser would be a dependency, and this file has one shape: a `[[mods]]` table of
 * `key = "value"` lines, with descriptions written as triple-quoted blocks. Anything it does not
 * understand is skipped rather than guessed at, and the mod still lists under its file name.
 */
private fun readToml(zip: ZipFile, fallback: Mod, path: String, loader: ModLoader): Mod? {
    val text = zip.readText(path) ?: return null
    val fields = parseFirstModsTable(text)
    if (fields.isEmpty()) return null
    return fallback.copy(
        id = fields["modId"],
        name = fields["displayName"] ?: fallback.name,
        // Forge writes a literal ${file.jarVersion} here, resolved from the manifest at build time.
        version = fields["version"]?.takeUnless { it.contains("\${") },
        description = fields["description"],
        authors = fields["authors"],
        loader = loader,
        iconPath = fields["logoFile"]
    )
}

private fun parseFirstModsTable(text: String): Map<String, String> {
    val fields = LinkedHashMap<String, String>()
    var inMods = false
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        i++
        if (line.startsWith("#") || line.isEmpty()) continue
        if (line.startsWith("[")) {
            // The first [[mods]] table is the mod; a second one is a second mod in the same jar,
            // and a [[dependencies...]] table is not a mod at all.
            if (line.startsWith("[[mods]]")) {
                if (inMods) break
                inMods = true
            } else if (inMods) {
                break
            }
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

/* ---------------------------------------------------------------- actions */

/**
 * Turn a mod on or off by renaming it.
 *
 * Both loaders read the folder and ignore anything that is not a `.jar`, so the suffix is the
 * whole mechanism — and it is reversible, which deleting is not.
 *
 * @return the renamed file, or null when the rename failed
 */
fun setModEnabled(mod: Mod, enabled: Boolean): File? {
    if (mod.enabled == enabled) return mod.file
    val target = File(
        mod.file.parentFile,
        if (enabled) baseName(mod.file) else baseName(mod.file) + DISABLED_SUFFIX
    )
    if (target.exists()) return null
    return if (mod.file.renameTo(target)) target else null
}

fun deleteMod(mod: Mod): Boolean = mod.file.delete()

/**
 * Copy a jar the player picked into the mods folder.
 *
 * The name comes from the document provider rather than from the URI, because a URI from Drive or
 * Downloads carries an opaque id and the file name is what the player will look for later. A name
 * already taken gets a `(2)` rather than overwriting: two jars of the same mod at different
 * versions is a mistake worth seeing rather than one silently winning.
 *
 * @return the installed file, or null if it could not be read
 */
fun installMod(context: Context, directory: File, uri: Uri): File? {
    val displayName = queryDisplayName(context, uri) ?: "mod.jar"
    val name = if (displayName.lowercase(Locale.getDefault()).endsWith(".jar")) displayName
    else "$displayName.jar"
    if (!directory.exists() && !directory.mkdirs()) return null
    val target = uniqueFile(directory, name)
    return try {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target
    } catch (t: Throwable) {
        target.delete()
        null
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.substringAfterLast('/') else null
        }
} catch (t: Throwable) {
    null
}

private fun uniqueFile(directory: File, name: String): File {
    var candidate = File(directory, name)
    if (!candidate.exists() && !File(directory, name + DISABLED_SUFFIX).exists()) return candidate
    val stem = name.substringBeforeLast('.')
    val extension = name.substringAfterLast('.', "jar")
    var index = 2
    while (index < 1000) {
        candidate = File(directory, "$stem ($index).$extension")
        if (!candidate.exists()) return candidate
        index++
    }
    return candidate
}

/* ---------------------------------------------------------------- small JSON helpers */

private fun ZipFile.readText(path: String): String? = try {
    val entry = getEntry(path) ?: return null
    getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
} catch (t: Throwable) {
    null
}

private fun ZipFile.readJson(path: String): JSONObject? = try {
    readText(path)?.let { JSONObject(it) }
} catch (t: Throwable) {
    // Fabric permits comments and trailing commas that org.json rejects. A mod whose metadata
    // will not parse still lists, under its file name.
    null
}

private fun JSONObject.optStringOrNull(key: String): String? {
    val value = optString(key, "").trim()
    return value.ifEmpty { null }
}

/** Authors are written as strings in some jars and as `{name, contact}` objects in others. */
private fun JSONObject.optArrayOfNames(key: String): String? {
    // Not optStringOrNull as the fallback: org.json's optString renders a nested object as its
    // own JSON text, so a jar that wrote authors as a map would list `{"Someone":{}}` as a name.
    val array = optJSONArray(key) ?: return (opt(key) as? String)?.trim()?.ifEmpty { null }
    val names = (0 until array.length()).mapNotNull { index ->
        when (val item = array.opt(index)) {
            is String -> item.trim().ifEmpty { null }
            is JSONObject -> item.optStringOrNull("name")
            else -> null
        }
    }
    return names.joinToString(", ").ifEmpty { null }
}

/**
 * The icon path, which Fabric writes either as a string or as a map of sizes to paths.
 * The largest is taken, since it is scaled down on the way in anyway.
 */
private fun JSONObject.optIconPath(): String? = when (val value = opt("icon")) {
    // Matched on the actual type rather than read through optString, which would hand back the
    // whole size map rendered as JSON text and then try to open a file by that name.
    is String -> value.trim().ifEmpty { null }
    is JSONObject -> {
        val largest = value.keys().asSequence().mapNotNull { it.toIntOrNull() }.maxOrNull()
        if (largest != null) value.optStringOrNull(largest.toString())
        else value.keys().asSequence().firstOrNull()?.let { value.optStringOrNull(it) }
    }
    else -> null
}
