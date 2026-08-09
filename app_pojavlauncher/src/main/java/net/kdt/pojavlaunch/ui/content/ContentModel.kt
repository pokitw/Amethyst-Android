package net.kdt.pojavlaunch.ui.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import net.kdt.pojavlaunch.R
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * The kinds of thing a profile owns.
 *
 * Ordered as the screen shows them, which is by how often someone comes looking: worlds are what
 * the game is, mods are what people change most, and screenshots are what they come back for
 * afterwards.
 */
enum class ContentKind(
    /** The folder Minecraft reads it from, relative to the game directory. */
    val folder: String,
    val titleRes: Int,
    val emptyRes: Int
) {
    WORLD("saves", R.string.content_kind_worlds, R.string.content_empty_worlds),
    MOD("mods", R.string.content_kind_mods, R.string.content_empty_mods),
    RESOURCE_PACK("resourcepacks", R.string.content_kind_packs, R.string.content_empty_packs),
    SHADER_PACK("shaderpacks", R.string.content_kind_shaders, R.string.content_empty_shaders),
    SCREENSHOT("screenshots", R.string.content_kind_screenshots, R.string.content_empty_screenshots)
}

/** The suffix every mod loader agrees means "do not load this". */
const val DISABLED_SUFFIX = ".disabled"

/**
 * One world, mod, pack or screenshot.
 *
 * Deliberately one type for all five. They differ in what fills the fields, not in what the fields
 * are — every row on the screen is a picture, a name, a line of detail and at most one control —
 * and a separate type per kind would have meant five list implementations to keep in step.
 *
 * Filled in two passes. The first is a directory listing, which is instant; the second opens each
 * item for its real name, its picture and its size, which is not. [detailed] says which one this
 * came from, so the row can show a placeholder rather than a wrong answer.
 */
@Immutable
data class ContentItem(
    val kind: ContentKind,
    val file: File,
    /** Stable across enabling and disabling, which renames the file. */
    val id: String,
    val title: String,
    /** What it is: a mod's description, a world's version and mode, a screenshot's date. */
    val detail: String? = null,
    val version: String? = null,
    /** FABRIC, FORGE, a pack format — whatever this kind labels itself with. */
    val badge: String? = null,
    val sizeBytes: Long = 0L,
    val modifiedMs: Long = 0L,
    val enabled: Boolean = true,
    /** Only mods can be switched off in place; everything else is on or deleted. */
    val toggleable: Boolean = false,
    val thumbnail: Bitmap? = null,
    val detailed: Boolean = false
) {
    /** Everything search looks at, lowercased once so matching is a plain substring test. */
    val haystack: String = buildString {
        append(title.lowercase(Locale.getDefault()))
        detail?.let { append(' ').append(it.lowercase(Locale.getDefault())) }
        version?.let { append(' ').append(it.lowercase(Locale.getDefault())) }
        badge?.let { append(' ').append(it.lowercase(Locale.getDefault())) }
        append(' ').append(file.name.lowercase(Locale.getDefault()))
    }

    fun matches(query: String): Boolean = query.isEmpty() || haystack.contains(query)
}

/** Where each kind lives for the profile that is about to be played. */
fun contentFolder(gameDir: File, kind: ContentKind): File = File(gameDir, kind.folder)

/* ------------------------------------------------------------------ the fast pass */

/**
 * List what is there, without opening anything.
 *
 * A directory listing on a folder of two hundred mods is a couple of milliseconds; opening two
 * hundred jars is not. This is what the screen draws first, so it draws immediately.
 */
fun scanContent(gameDir: File, kind: ContentKind): List<ContentItem> {
    val folder = contentFolder(gameDir, kind)
    val entries = folder.listFiles() ?: return emptyList()
    val items = ArrayList<ContentItem>(entries.size)
    for (entry in entries) {
        val item = when (kind) {
            ContentKind.WORLD -> scanWorld(entry)
            ContentKind.MOD -> scanMod(entry)
            ContentKind.RESOURCE_PACK, ContentKind.SHADER_PACK -> scanPack(entry, kind)
            ContentKind.SCREENSHOT -> scanScreenshot(entry)
        }
        if (item != null) items.add(item)
    }
    return items.sortedWith(ordering(kind))
}

/** Newest first where recency is the point, alphabetical where it is not. */
private fun ordering(kind: ContentKind): Comparator<ContentItem> = when (kind) {
    ContentKind.WORLD, ContentKind.SCREENSHOT -> compareByDescending { it.modifiedMs }
    else -> compareBy({ !it.enabled }, { it.title.lowercase(Locale.getDefault()) })
}

private fun scanWorld(entry: File): ContentItem? {
    if (!entry.isDirectory) return null
    // The one thing that makes a folder a world. Without it the game will not list it either.
    if (!File(entry, "level.dat").isFile) return null
    return ContentItem(
        kind = ContentKind.WORLD,
        file = entry,
        id = entry.name,
        title = entry.name,
        modifiedMs = entry.lastModified()
    )
}

private fun scanMod(entry: File): ContentItem? {
    if (!entry.isFile) return null
    val lower = entry.name.lowercase(Locale.getDefault())
    if (!lower.endsWith(".jar") && !lower.endsWith(".jar$DISABLED_SUFFIX")) return null
    val base = entry.name.removeSuffix(DISABLED_SUFFIX)
    return ContentItem(
        kind = ContentKind.MOD,
        file = entry,
        id = base,
        title = base.removeSuffix(".jar"),
        sizeBytes = entry.length(),
        modifiedMs = entry.lastModified(),
        enabled = !entry.name.endsWith(DISABLED_SUFFIX),
        toggleable = true
    )
}

private fun scanPack(entry: File, kind: ContentKind): ContentItem? {
    val lower = entry.name.lowercase(Locale.getDefault())
    val isZip = entry.isFile && lower.endsWith(".zip")
    if (!isZip && !entry.isDirectory) return null
    return ContentItem(
        kind = kind,
        file = entry,
        id = entry.name,
        title = entry.name.removeSuffix(".zip"),
        sizeBytes = if (entry.isFile) entry.length() else 0L,
        modifiedMs = entry.lastModified()
    )
}

private fun scanScreenshot(entry: File): ContentItem? {
    if (!entry.isFile) return null
    val lower = entry.name.lowercase(Locale.getDefault())
    if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) return null
    return ContentItem(
        kind = ContentKind.SCREENSHOT,
        file = entry,
        id = entry.name,
        title = entry.name.substringBeforeLast('.'),
        sizeBytes = entry.length(),
        modifiedMs = entry.lastModified()
    )
}

/* ------------------------------------------------------------------ the slow pass */

/**
 * Open the item and find out what it actually is.
 *
 * Runs off the main thread, one item at a time, and the row it belongs to is replaced when it
 * lands. Nothing here is allowed to throw: an item whose metadata cannot be read is still an item
 * the game will load, and hiding it would make a crash impossible to explain.
 */
fun describe(item: ContentItem): ContentItem = try {
    when (item.kind) {
        ContentKind.WORLD -> describeWorld(item)
        ContentKind.MOD -> describeMod(item)
        ContentKind.RESOURCE_PACK, ContentKind.SHADER_PACK -> describePack(item)
        ContentKind.SCREENSHOT -> item.copy(detailed = true)
    }
} catch (t: Throwable) {
    item.copy(detailed = true)
}

private fun describeWorld(item: ContentItem): ContentItem {
    val level = NbtReader.read(File(item.file, "level.dat"))
    val name = NbtReader.getString(level, "Data", "LevelName")
    val version = NbtReader.getString(level, "Data", "Version", "Name")
    val hardcore = NbtReader.getLong(level, 0L, "Data", "hardcore") != 0L
    val mode = NbtReader.getLong(level, -1L, "Data", "GameType")
    val lastPlayed = NbtReader.getLong(level, 0L, "Data", "LastPlayed")
    return item.copy(
        title = name ?: item.title,
        version = version,
        badge = when {
            hardcore -> "HARDCORE"
            mode == 1L -> "CREATIVE"
            mode == 2L -> "ADVENTURE"
            mode == 3L -> "SPECTATOR"
            mode == 0L -> "SURVIVAL"
            else -> null
        },
        sizeBytes = directorySize(item.file),
        // The game's own record of when it was last opened beats the folder's timestamp, which
        // any backup tool or file copy will have rewritten.
        modifiedMs = if (lastPlayed > 0L) lastPlayed else item.modifiedMs,
        detailed = true
    )
}

private fun describeMod(item: ContentItem): ContentItem {
    val meta = readModMetadata(item.file) ?: return item.copy(
        sizeBytes = item.file.length(), detailed = true
    )
    return item.copy(
        title = meta.name ?: item.title,
        detail = meta.description,
        version = meta.version,
        badge = meta.loader,
        sizeBytes = item.file.length(),
        detailed = true
    )
}

private fun describePack(item: ContentItem): ContentItem {
    val meta = readPackMetadata(item.file)
    return item.copy(
        detail = meta?.description,
        badge = meta?.format?.let { "PACK $it" },
        sizeBytes = if (item.file.isDirectory) directorySize(item.file) else item.file.length(),
        detailed = true
    )
}

/** Walked rather than asked for, because there is no such thing as a folder's size. */
private fun directorySize(root: File): Long {
    var total = 0L
    val stack = ArrayDeque<File>()
    stack.addLast(root)
    var visited = 0
    while (stack.isNotEmpty()) {
        val next = stack.removeLast()
        // A world is thousands of region files, and a symlink loop is unbounded. Stopping at a
        // number nobody will reach honestly is better than a scan that never ends.
        if (++visited > 40_000) break
        val children = next.listFiles() ?: continue
        for (child in children) {
            if (child.isDirectory) stack.addLast(child) else total += child.length()
        }
    }
    return total
}

/* ------------------------------------------------------------------ pictures */

/**
 * The picture an item carries, decoded small.
 *
 * World icons are 64px, pack icons 128px, screenshots the size of the screen they were taken on —
 * so sampling on the way in is what keeps a folder of two hundred screenshots off the heap.
 */
fun readThumbnail(item: ContentItem, maxPixels: Int = 160): Bitmap? = try {
    when (item.kind) {
        ContentKind.WORLD -> decodeFile(File(item.file, "icon.png"), maxPixels)
        ContentKind.SCREENSHOT -> decodeFile(item.file, maxPixels)
        ContentKind.MOD -> readModIcon(item.file, maxPixels)
        ContentKind.RESOURCE_PACK, ContentKind.SHADER_PACK -> readPackIcon(item.file, maxPixels)
    }
} catch (t: Throwable) {
    null
}

internal fun decodeFile(file: File, maxPixels: Int): Bitmap? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, maxPixels) }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

internal fun decodeBytes(bytes: ByteArray, maxPixels: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, maxPixels) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun sampleFor(bounds: BitmapFactory.Options, maxPixels: Int): Int {
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    return if (longest > maxPixels) maxOf(1, longest / maxPixels) else 1
}

/* ------------------------------------------------------------------ actions */

/**
 * Turn a mod on or off by renaming it.
 *
 * Every loader reads the folder and ignores anything that is not a `.jar`, so the suffix is the
 * whole mechanism — and it is reversible, which deleting is not. That is the thing you actually
 * want at two in the morning with a crash to bisect.
 *
 * @return where the file ended up, or null if the rename failed
 */
fun setContentEnabled(item: ContentItem, enabled: Boolean): File? {
    if (!item.toggleable || item.enabled == enabled) return item.file
    val parent = item.file.parentFile ?: return null
    val base = item.file.name.removeSuffix(DISABLED_SUFFIX)
    val target = File(parent, if (enabled) base else base + DISABLED_SUFFIX)
    if (target.exists()) return null
    return if (item.file.renameTo(target)) target else null
}

fun deleteContent(item: ContentItem): Boolean =
    if (item.file.isDirectory) deleteTree(item.file) else item.file.delete()

private fun deleteTree(root: File): Boolean {
    val children = root.listFiles()
    if (children != null) for (child in children) deleteTree(child)
    return root.delete()
}

/* ------------------------------------------------------------------ adding things */

/** What happened when a file was handed to the launcher. */
sealed class ImportResult {
    data class Added(val kind: ContentKind, val name: String) : ImportResult()
    /** Readable, but nothing in it says what it is meant to be. */
    object Unrecognised : ImportResult()
    object Failed : ImportResult()
}

/**
 * Put a file where it belongs, working out where that is by looking inside it.
 *
 * This is the whole point of one screen rather than five. Nobody thinks "I would like to place a
 * file in the resourcepacks directory"; they think "add this". So there is one button, and the
 * decision it used to ask for is made by opening the file: a jar is a mod, a zip with a
 * `pack.mcmeta` is a resource pack, one with a `shaders` folder is a shader pack, one with a
 * `level.dat` is a world and gets extracted.
 *
 * Names never collide silently. A second copy becomes "(2)", because two versions of the same mod
 * is a mistake worth seeing rather than one quietly winning.
 */
fun importContent(context: Context, gameDir: File, uri: Uri): ImportResult {
    val displayName = queryDisplayName(context, uri) ?: "import"
    val staged: File
    try {
        // Copied out first: a content:// stream is not seekable, and deciding what a zip is means
        // reading its central directory, which is at the end.
        staged = File.createTempFile("amethyst-import", null, context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ImportResult.Failed
            staged.outputStream().use { output -> input.copyTo(output) }
        }
    } catch (t: Throwable) {
        return ImportResult.Failed
    }

    try {
        val lower = displayName.lowercase(Locale.getDefault())
        return when {
            lower.endsWith(".jar") ->
                place(staged, gameDir, ContentKind.MOD, displayName)
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ->
                place(staged, gameDir, ContentKind.SCREENSHOT, displayName)
            else -> importArchive(staged, gameDir, displayName)
        }
    } finally {
        staged.delete()
    }
}

private fun importArchive(staged: File, gameDir: File, displayName: String): ImportResult {
    val shape = inspectArchive(staged) ?: return ImportResult.Unrecognised
    return when (shape) {
        is ArchiveShape.Pack ->
            place(staged, gameDir, shape.kind, ensureExtension(displayName, ".zip"))
        is ArchiveShape.World ->
            extractWorld(staged, gameDir, shape.prefix, displayName)
    }
}

private sealed class ArchiveShape {
    data class Pack(val kind: ContentKind) : ArchiveShape()
    /** @param prefix the folder inside the zip that the world's own files sit under, or "" */
    data class World(val prefix: String) : ArchiveShape()
}

/** Read the entry names and decide what this archive is meant to be. */
private fun inspectArchive(file: File): ArchiveShape? = try {
    ZipFile(file).use { zip ->
        var pack = false
        var shaders = false
        var worldPrefix: String? = null
        val entries = zip.entries()
        var seen = 0
        while (entries.hasMoreElements() && seen < 20_000) {
            seen++
            val name = entries.nextElement().name.replace('\\', '/')
            val lower = name.lowercase(Locale.getDefault())
            if (lower.endsWith("level.dat") && worldPrefix == null) {
                // Zipped either from inside the world folder or from outside it, so the prefix is
                // whatever comes before, and it is stripped on the way out.
                worldPrefix = name.dropLast("level.dat".length)
            }
            if (lower == "pack.mcmeta" || lower.endsWith("/pack.mcmeta")) pack = true
            if (lower.startsWith("shaders/") || lower.contains("/shaders/")) shaders = true
        }
        when {
            // Checked before the pack marker: a world is unambiguous, and some world zips carry a
            // resource pack inside them.
            worldPrefix != null -> ArchiveShape.World(worldPrefix!!)
            shaders -> ArchiveShape.Pack(ContentKind.SHADER_PACK)
            pack -> ArchiveShape.Pack(ContentKind.RESOURCE_PACK)
            else -> null
        }
    }
} catch (t: Throwable) {
    null
}

private fun place(staged: File, gameDir: File, kind: ContentKind, name: String): ImportResult {
    val folder = contentFolder(gameDir, kind)
    if (!folder.exists() && !folder.mkdirs()) return ImportResult.Failed
    val target = uniqueFile(folder, name)
    return try {
        staged.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        ImportResult.Added(kind, target.name)
    } catch (t: Throwable) {
        target.delete()
        ImportResult.Failed
    }
}

/**
 * Unpack a world zip into `saves/`.
 *
 * Entry names come from whoever built the archive, so every one of them is resolved and checked to
 * still be inside the destination before anything is written. A zip whose entries are named
 * `../../` is the oldest trick there is, and this is the only place in the launcher that unpacks
 * something a stranger sent.
 */
private fun extractWorld(
    staged: File, gameDir: File, prefix: String, displayName: String
): ImportResult {
    val saves = contentFolder(gameDir, ContentKind.WORLD)
    if (!saves.exists() && !saves.mkdirs()) return ImportResult.Failed
    val folderName = displayName.removeSuffix(".zip").ifBlank { "World" }
    val target = uniqueFile(saves, folderName)
    if (!target.mkdirs()) return ImportResult.Failed
    val root = target.canonicalPath + File.separator

    try {
        staged.inputStream().use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    if (!name.startsWith(prefix)) continue
                    val relative = name.removePrefix(prefix)
                    if (relative.isEmpty()) continue
                    val out = File(target, relative)
                    if (!out.canonicalPath.startsWith(root)) {
                        // Outside the world folder: refuse the whole archive rather than skip the
                        // entry, because an archive that tried once is not one to trust the rest of.
                        deleteTree(target)
                        return ImportResult.Failed
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { output -> copy(zip, output) }
                    }
                }
            }
        }
    } catch (t: Throwable) {
        deleteTree(target)
        return ImportResult.Failed
    }

    if (!File(target, "level.dat").isFile) {
        deleteTree(target)
        return ImportResult.Failed
    }
    return ImportResult.Added(ContentKind.WORLD, target.name)
}

private fun copy(input: InputStream, output: java.io.OutputStream) {
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        output.write(buffer, 0, read)
    }
}

private fun ensureExtension(name: String, extension: String): String =
    if (name.lowercase(Locale.getDefault()).endsWith(extension)) name else name + extension

private fun uniqueFile(folder: File, name: String): File {
    val first = File(folder, name)
    if (!first.exists() && !File(folder, name + DISABLED_SUFFIX).exists()) return first
    val stem = name.substringBeforeLast('.', name)
    val extension = name.substringAfterLast('.', "")
    var index = 2
    while (index < 1000) {
        val candidate = File(folder, if (extension.isEmpty()) "$stem ($index)" else "$stem ($index).$extension")
        if (!candidate.exists()) return candidate
        index++
    }
    return first
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.substringAfterLast('/') else null
        }
} catch (t: Throwable) {
    null
}
