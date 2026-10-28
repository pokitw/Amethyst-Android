package net.kdt.pojavlaunch.ui.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.runtime.Immutable
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.modmeta.ModRequirements
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
    val detailed: Boolean = false,
    /**
     * The accent line under the title — version, loader, size, when it was last touched.
     *
     * Built where a `Context` is available rather than in the composition, because formatting a
     * file size needs one and doing it per frame for four hundred rows is four hundred lookups
     * of the locale's byte suffixes.
     */
    val summary: String = "",
    /**
     * What this mod says it needs, kept so the folder can be judged as a whole.
     *
     * Null for everything that is not a mod, and for a jar nothing could be read from.
     */
    val requirements: ModRequirements? = null,
    /**
     * The one thing wrong with this row, in words, replacing the accent line when there is one.
     *
     * Deliberately not a second line and not a red badge. The accent line is already where a row
     * reports its state, a mod that will not load has nothing more useful to say than why, and a
     * row that grew a third line would make four hundred of them taller for the sake of the two
     * that are broken.
     */
    val warning: String? = null,
    /**
     * Enabled mods that need this one, by name, so switching it off can say who breaks.
     *
     * On the row rather than worked out when asked, because it comes from the same single pass
     * that fills [warning] and a second resolve could disagree with the first.
     */
    val neededBy: List<String> = emptyList(),
    /** Required dependency ids nothing installed provides, so a row can offer to go and get one. */
    val missing: List<String> = emptyList(),
    /**
     * Set once the picture has been read, so a row scrolling back into view does not ask again.
     *
     * Only an early exit — the screen's host keeps the real record, because an item with no
     * picture at all never gets this set and must still not be opened twice.
     */
    val thumbnailRequested: Boolean = false
) {
    /** Precomputed: this is read every time a row without a picture is drawn. */
    val initial: String = title.take(1).uppercase(Locale.getDefault())

    /** Everything search looks at, lowercased once so matching is a plain substring test. */
    val haystack: String = buildString {
        append(title.lowercase(Locale.getDefault()))
        detail?.let { append(' ').append(it.lowercase(Locale.getDefault())) }
        version?.let { append(' ').append(it.lowercase(Locale.getDefault())) }
        badge?.let { append(' ').append(it.lowercase(Locale.getDefault())) }
        append(' ').append(file.name.lowercase(Locale.getDefault()))
        // A mod is as often looked for by the id in its crash log as by its display name.
        requirements?.modId?.let { append(' ').append(it) }
        warning?.let { append(' ').append(it.lowercase(Locale.getDefault())) }
    }

    fun matches(query: String): Boolean = query.isEmpty() || haystack.contains(query)
}

/**
 * Everything under the title, in one line.
 *
 * Deliberately not a row of chips. The launcher's rows put the state in one accent line under the
 * description, and this is that line.
 *
 * The badge is **not** in it. A survival world reads "SURVIVAL · 1.20.1 · 412 MB · 2 days ago",
 * which is four parts and does not fit a phone's row width — so the badge, which is the shortest
 * and the most recognisable of them, sits beside the title instead and the line keeps three.
 */
fun summarise(context: Context, item: ContentItem): String {
    val parts = ArrayList<String>(3)
    item.version?.let { parts.add(it) }
    if (item.sizeBytes > 0L) parts.add(Formatter.formatShortFileSize(context, item.sizeBytes))
    // Recency matters for the things you come back to and not for the things you install once.
    if (item.modifiedMs > 0L && (item.kind == ContentKind.WORLD || item.kind == ContentKind.SCREENSHOT)) {
        parts.add(
            DateUtils.getRelativeTimeSpanString(
                item.modifiedMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            ).toString()
        )
    }
    return parts.joinToString(" · ")
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
    // Unsorted on the way out: a world's real timestamp and a mod's real size only arrive in the
    // slow pass, so ordering here would be ordering on placeholders and the list would visibly
    // reshuffle underneath a thumb. The screen sorts what it is about to draw.
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
    return items
}

/**
 * How a list is ordered, chosen by the player and remembered.
 *
 * [RECENT] is the default for everything, and for mods it is the one that was missing: a jar's
 * timestamp is the moment it was written, so "newest first" is literally "what I installed
 * last", which is what somebody is looking for when they have just added four mods and want to
 * see whether they landed. The old default buried them alphabetically among two hundred others.
 */
enum class ContentSort { RECENT, NAME, LARGEST }

fun orderingFor(sort: ContentSort): Comparator<ContentItem> = when (sort) {
    // Ties broken by name, so a folder of files copied in one go by a restore, which all carry
    // the same timestamp to the second, does not shuffle between two reads of the same folder.
    ContentSort.RECENT -> compareByDescending<ContentItem> { it.modifiedMs }
        .thenBy { it.title.lowercase(Locale.getDefault()) }
    ContentSort.NAME -> compareBy { it.title.lowercase(Locale.getDefault()) }
    ContentSort.LARGEST -> compareByDescending<ContentItem> { it.sizeBytes }
        .thenBy { it.title.lowercase(Locale.getDefault()) }
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

/**
 * How much space a folder takes, measured separately from everything else.
 *
 * A world is thousands of region files and this is the only part of reading a profile that is
 * genuinely slow, so it is not allowed to hold up the names — which are what the screen is for.
 * It runs last, and the sizes appear when they appear.
 */
fun measure(item: ContentItem): Long =
    if (item.file.isDirectory) directorySize(item.file) else item.file.length()

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
        requirements = meta.requirements,
        detailed = true
    )
}

private fun describePack(item: ContentItem): ContentItem {
    val meta = readPackMetadata(item.file)
    return item.copy(
        detail = meta?.description,
        badge = meta?.format?.let { "PACK $it" },
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
    /** Placed by hand, in a folder the player picked. */
    data class Copied(val name: String, val folder: String) : ImportResult()
    object Failed : ImportResult()
}

/**
 * A file that has been read and identified, staged in the cache, not yet placed anywhere.
 *
 * Importing used to be one call that decided and copied in the same breath, which is the right
 * shape only if the decision is always right and never worth questioning. It is neither: the
 * launcher guesses from what is inside the file, that guess is occasionally wrong, and the two
 * things a player most wants to be asked about, "this is going in mods, is that what you meant"
 * and "you already have this mod", both happen after the deciding and before the writing. So the
 * deciding stops there and hands back this.
 *
 * The staged copy lives in the cache because a `content://` stream is not seekable and reading a
 * zip's central directory means seeking to its end. Whoever makes a plan owns deleting it.
 */
class ImportPlan(
    val staged: File,
    val displayName: String,
    /** Where it belongs, or null when nothing inside it says. */
    val kind: ContentKind?,
    /** The name it would land under, with any collision already resolved. */
    val landingName: String,
    /** Set when this is a world archive, which is unpacked into a folder rather than copied. */
    val unpacks: Boolean = false,
    /** An installed mod declaring the same id, by the name a player would recognise. */
    val duplicateOf: String? = null,
    /** The jar that duplicate lives in, so the import can offer to replace it. */
    val duplicateFile: File? = null,
    /** The prefix inside a world archive that the world's own files sit under. */
    internal val worldPrefix: String = ""
)

/**
 * Read a file, work out what it is, and stop.
 *
 * Nothing is written to the game folder here. Call [commitImport] to put it where it belongs, or
 * [commitImportInto] to put it in a folder the player chose, or [discardImport] to throw it away.
 *
 * @return null when the file could not even be copied out of the provider
 */
fun planImport(context: Context, gameDir: File, uri: Uri): ImportPlan? {
    val displayName = safeName(queryDisplayName(context, uri))
    val staged: File
    try {
        staged = File.createTempFile("amethyst-import", null, context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            staged.outputStream().use { output -> input.copyTo(output) }
        }
    } catch (t: Throwable) {
        return null
    }

    val lower = displayName.lowercase(Locale.getDefault())
    return try {
        when {
            lower.endsWith(".jar") -> modPlan(staged, gameDir, displayName)
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ->
                plainPlan(staged, gameDir, ContentKind.SCREENSHOT, displayName)
            else -> archivePlan(staged, gameDir, displayName)
        }
    } catch (t: Throwable) {
        ImportPlan(staged, displayName, null, displayName)
    }
}

/**
 * Put the file in the folder the launcher decided on.
 *
 * @param replaceDuplicate delete the jar named by [ImportPlan.duplicateFile] first, which is
 *        what "update this mod" means: two copies of one mod id is a loader that refuses to
 *        start, so adding the new one beside the old one is the thing to avoid rather than the
 *        default. Nothing is deleted unless this is asked for.
 */
fun commitImport(plan: ImportPlan, gameDir: File, replaceDuplicate: Boolean = false): ImportResult {
    val kind = plan.kind ?: return ImportResult.Unrecognised
    if (replaceDuplicate) plan.duplicateFile?.delete()
    return if (plan.unpacks) {
        extractWorld(plan.staged, gameDir, plan.worldPrefix, plan.displayName)
    } else {
        // Resolved again rather than reusing landingName: a dialog can sit open for as long as
        // somebody leaves it, and the folder may have gained that name in the meantime. The
        // name shown was the truth when it was shown, and this is the truth when it is written.
        place(plan.staged, gameDir, kind, plan.displayName)
    }
}

/**
 * Put the file in a folder the player picked, wherever the launcher thought it belonged.
 *
 * Offered for everything and not only for what could not be identified, which is the half that
 * was missing: the guess is right nearly always and "nearly" is the problem, and a datapack, a
 * config or a jar for a loader this does not model has nowhere else to go at all.
 *
 * <b>It never overwrites and never leaves the game folder.</b> The destination is checked to be
 * inside it by canonical path, because the picker walks real directories and a symlink inside
 * the game folder is the one way a chosen path could point outside it.
 */
fun commitImportInto(plan: ImportPlan, gameDir: File, folder: File): ImportResult {
    return try {
        val root = gameDir.canonicalFile
        val target = folder.canonicalFile
        if (target != root && !target.path.startsWith(root.path + File.separator)) {
            return ImportResult.Failed
        }
        if (!target.isDirectory) return ImportResult.Failed
        val destination = uniqueFile(target, plan.displayName)
        // Belt and braces after the name has been cleaned: the only thing that may be written
        // is a direct child of the folder that was chosen.
        if (destination.canonicalFile.parentFile != target) return ImportResult.Failed
        plan.staged.inputStream().use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        ImportResult.Copied(destination.name, relativeLabel(root, target))
    } catch (t: Throwable) {
        ImportResult.Failed
    }
}

/** Throw away a plan nobody acted on. Safe to call twice. */
fun discardImport(plan: ImportPlan) {
    runCatching { plan.staged.delete() }
}

private fun plainPlan(
    staged: File, gameDir: File, kind: ContentKind, displayName: String
): ImportPlan = ImportPlan(
    staged = staged,
    displayName = displayName,
    kind = kind,
    landingName = uniqueFile(contentFolder(gameDir, kind), displayName).name
)

/**
 * A jar, plus whether the profile already has the mod it declares.
 *
 * Matched on the declared id rather than the file name, exactly as the folder's own duplicate
 * check is: "sodium-0.6.jar" beside "sodium-0.5.jar" is the case this exists for and no amount
 * of comparing names tells it apart from "reeses-sodium-options.jar". A jar whose metadata will
 * not parse simply has no duplicate, which is the safe direction.
 */
private fun modPlan(staged: File, gameDir: File, displayName: String): ImportPlan {
    val folder = contentFolder(gameDir, ContentKind.MOD)
    val incoming = runCatching { readModMetadata(staged)?.requirements?.modId }.getOrNull()
    var duplicateOf: String? = null
    var duplicateFile: File? = null
    if (!incoming.isNullOrEmpty()) {
        val existing = folder.listFiles()
        if (existing != null) for (candidate in existing) {
            if (!candidate.isFile) continue
            val name = candidate.name.lowercase(Locale.getDefault())
            // A .disabled jar is not in the game, so it is not a clash, and offering to replace
            // one would be deleting a file somebody deliberately set aside.
            if (!name.endsWith(".jar")) continue
            val meta = runCatching { readModMetadata(candidate) }.getOrNull() ?: continue
            if (meta.requirements?.modId == incoming) {
                duplicateOf = meta.name ?: candidate.name.removeSuffix(".jar")
                duplicateFile = candidate
                break
            }
        }
    }
    return ImportPlan(
        staged = staged,
        displayName = displayName,
        kind = ContentKind.MOD,
        landingName = uniqueFile(folder, displayName).name,
        duplicateOf = duplicateOf,
        duplicateFile = duplicateFile
    )
}

private fun archivePlan(staged: File, gameDir: File, displayName: String): ImportPlan {
    val shape = inspectArchive(staged)
        ?: return ImportPlan(staged, displayName, null, displayName)
    return when (shape) {
        is ArchiveShape.Pack -> {
            val name = ensureExtension(displayName, ".zip")
            ImportPlan(
                staged = staged,
                displayName = name,
                kind = shape.kind,
                landingName = uniqueFile(contentFolder(gameDir, shape.kind), name).name
            )
        }
        is ArchiveShape.World -> {
            val folderName = displayName.removeSuffix(".zip").ifBlank { "World" }
            ImportPlan(
                staged = staged,
                displayName = displayName,
                kind = ContentKind.WORLD,
                landingName = uniqueFile(contentFolder(gameDir, ContentKind.WORLD), folderName).name,
                unpacks = true,
                worldPrefix = shape.prefix
            )
        }
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

/**
 * A file name that cannot be anything but a file name.
 *
 * The display name comes from whichever provider served the document, so it is a stranger's
 * string: `File(folder, name)` with "../x" in it resolves outside the folder the player chose,
 * and with ".." in it resolves to the parent directory. Separators of both kinds are cut and the
 * two directory names are refused outright, which leaves a plain name or the fallback.
 *
 * Applied once, in planImport, and the cleaned name is what every later step uses: the dialog
 * shows it, the collision check resolves against it and the writer writes it. A name cleaned
 * twice in two places is a name two places can disagree about.
 */
private fun safeName(raw: String?): String {
    val trimmed = (raw ?: "").substringAfterLast('/').substringAfterLast('\\').trim()
    if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return "import"
    return trimmed
}

/** The folders inside one, for the picker to walk. Hidden ones are left out. */
fun subFolders(folder: File): List<File> =
    (folder.listFiles() ?: emptyArray())
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }

/** Where a folder is, said the way a player would read it, rooted at the game folder. */
fun relativeLabel(gameDir: File, folder: File): String {
    val root = runCatching { gameDir.canonicalFile }.getOrDefault(gameDir)
    val here = runCatching { folder.canonicalFile }.getOrDefault(folder)
    if (here == root) return ".minecraft"
    val path = here.path.removePrefix(root.path).trimStart(File.separatorChar)
    return if (path.isEmpty()) ".minecraft" else ".minecraft/" + path
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
