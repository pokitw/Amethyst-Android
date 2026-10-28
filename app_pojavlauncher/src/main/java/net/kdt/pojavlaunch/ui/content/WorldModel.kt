package net.kdt.pojavlaunch.ui.content

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * What can be done to a world without opening Minecraft.
 *
 * Game files could list worlds and delete them, and that was the whole of it. Everything else a
 * player wants from a world is either inside the game, which means a launch and a world load, or
 * inside `level.dat`, which lives under `Android/data` and from Android 11 cannot be reached by
 * any file manager on the device. So "manage my worlds" meant "launch the game", and for the two
 * things the game itself will not do, rescuing a hardcore world and taking a copy before trying
 * something, it meant nothing at all.
 *
 * Every write here goes through [NbtEditor], which splices one field and verifies the result
 * parses before it replaces anything, keeping the original beside it. That is what makes
 * offering this reasonable: being wrong costs a file that is still on disk.
 *
 * **The folder name is not the world's name.** Minecraft shows `LevelName` from `level.dat` and
 * the folder is whatever it happened to be created as, so renaming the folder alone changes
 * nothing a player sees. Renaming does both, in that order, and reports success on the one that
 * matters.
 */

/** Minecraft's own numbering, which is what is written into the file. */
object GameMode {
    const val SURVIVAL = 0
    const val CREATIVE = 1
    const val ADVENTURE = 2
    const val SPECTATOR = 3
}

/** Everything the manage sheet shows, read in one pass off the main thread. */
@Immutable
data class WorldDetails(
    val name: String,
    val folderName: String,
    val version: String?,
    /** One of [GameMode], or -1 when the file does not say. */
    val gameMode: Int,
    val hardcore: Boolean,
    val cheats: Boolean,
    /** 0 peaceful to 3 hard, or -1 when the file does not say. */
    val difficulty: Int,
    val lastPlayedMs: Long,
    val sizeBytes: Long,
    val seed: Long?,
    /**
     * Whether `level.dat` parsed at all.
     *
     * A world whose file will not read is still a world and still shows, deletes and exports;
     * what it cannot do is be edited, because there is nothing to splice into. The sheet says so
     * rather than offering controls that would refuse.
     */
    val editable: Boolean
)

private const val LEVEL = "level.dat"

fun worldLevelFile(world: File): File = File(world, LEVEL)

/** Read everything the sheet needs. Off the main thread: this walks the folder for its size. */
fun readWorldDetails(world: File): WorldDetails {
    val level = runCatching { NbtReader.read(worldLevelFile(world)) }.getOrNull()
    val size = runCatching { measureTree(world) }.getOrDefault(0L)
    if (level == null) {
        return WorldDetails(
            name = world.name,
            folderName = world.name,
            version = null,
            gameMode = -1,
            hardcore = false,
            cheats = false,
            difficulty = -1,
            lastPlayedMs = world.lastModified(),
            sizeBytes = size,
            seed = null,
            editable = false
        )
    }
    val lastPlayed = NbtReader.getLong(level, 0L, "Data", "LastPlayed")
    return WorldDetails(
        name = NbtReader.getString(level, "Data", "LevelName") ?: world.name,
        folderName = world.name,
        version = NbtReader.getString(level, "Data", "Version", "Name"),
        gameMode = NbtReader.getLong(level, -1L, "Data", "GameType").toInt(),
        hardcore = NbtReader.getLong(level, 0L, "Data", "hardcore") != 0L,
        cheats = NbtReader.getLong(level, 0L, "Data", "allowCommands") != 0L,
        difficulty = NbtReader.getLong(level, -1L, "Data", "Difficulty").toInt(),
        lastPlayedMs = if (lastPlayed > 0L) lastPlayed else world.lastModified(),
        sizeBytes = size,
        // Moved in 1.16 when world generation became data driven. Both are read because the
        // launcher lists worlds back to Beta and neither spelling is wrong, only older.
        seed = NbtReader.getLong(level, Long.MIN_VALUE, "Data", "WorldGenSettings", "seed")
            .takeIf { it != Long.MIN_VALUE }
            ?: NbtReader.getLong(level, Long.MIN_VALUE, "Data", "RandomSeed")
                .takeIf { it != Long.MIN_VALUE },
        editable = true
    )
}

/* ------------------------------------------------------------------ edits */

/**
 * Rename a world, in the file and then on disk.
 *
 * The name in `level.dat` is the one Minecraft shows, so that is the one that has to change and
 * the one success is reported on. The folder is renamed afterwards as a courtesy, because a
 * folder called "New World" holding a world called "Hardcore Attempt 4" is confusing the moment
 * anybody does reach it, and a failure there is not worth undoing the rename that worked: the
 * two are not required to agree and Minecraft never looks at the folder's name.
 */
fun renameWorld(world: File, newName: String): File? {
    val trimmed = newName.trim()
    if (trimmed.isEmpty()) return null
    val changed = NbtEditor.edit(worldLevelFile(world)) { nbt ->
        NbtEditor.setString(nbt, trimmed, "Data", "LevelName")
    }
    if (!changed) return null
    val parent = world.parentFile ?: return world
    val wanted = folderSafeName(trimmed)
    if (wanted.isEmpty() || wanted == world.name) return world
    val target = uniqueChild(parent, wanted)
    return if (world.renameTo(target)) target else world
}

fun setWorldGameMode(world: File, mode: Int): Boolean =
    mode in GameMode.SURVIVAL..GameMode.SPECTATOR && NbtEditor.edit(worldLevelFile(world)) { nbt ->
        NbtEditor.setInt(nbt, mode, "Data", "GameType")
    }

fun setWorldCheats(world: File, on: Boolean): Boolean =
    NbtEditor.edit(worldLevelFile(world)) { nbt ->
        NbtEditor.setByte(nbt, if (on) 1 else 0, "Data", "allowCommands")
    }

fun setWorldDifficulty(world: File, level: Int): Boolean =
    level in 0..3 && NbtEditor.edit(worldLevelFile(world)) { nbt ->
        NbtEditor.setByte(nbt, level, "Data", "Difficulty")
    }

/**
 * Turn hardcore off, which is the one thing the game itself will never offer.
 *
 * A hardcore world after a death is a spectator world forever, and the only routes out are a
 * desktop NBT editor or a command nobody can run because the world cannot be entered. It is
 * one-way inside Minecraft and it does not have to be here.
 *
 * The game mode is set alongside it, because hardcore death also writes GameType 3 and a world
 * that is no longer hardcore but is still locked to spectator is only half rescued. Survival is
 * the only sensible landing: it is what the world was before it died.
 */
fun unsetHardcore(world: File): Boolean {
    val level = worldLevelFile(world)
    val cleared = NbtEditor.edit(level) { nbt -> NbtEditor.setByte(nbt, 0, "Data", "hardcore") }
    if (!cleared) return false
    // Best effort, and deliberately not fatal: a world whose GameType cannot be written is still
    // a world that is no longer hardcore, which is what was asked for.
    NbtEditor.edit(level) { nbt -> NbtEditor.setInt(nbt, GameMode.SURVIVAL, "Data", "GameType") }
    return true
}

/* ------------------------------------------------------------------ copies */

/**
 * Take a copy of a world, under its own name.
 *
 * The thing people actually want before trying something irreversible, and until now the only
 * way to get it was a file manager that cannot reach the folder. The copy's `LevelName` is
 * changed too, or the world list would show two worlds with the same name and no way to tell
 * which is which.
 *
 * @return the new folder, or null if the copy could not be completed. A partial copy is removed
 *         rather than left: half a world in the saves list is worse than none.
 */
fun duplicateWorld(world: File, copyLabel: String): File? {
    val parent = world.parentFile ?: return null
    val details = runCatching { readWorldDetails(world) }.getOrNull()
    val baseName = details?.name ?: world.name
    val newName = "$baseName $copyLabel"
    val target = uniqueChild(parent, folderSafeName(newName).ifEmpty { world.name + " copy" })
    if (!target.mkdirs()) return null
    val copied = runCatching { copyTree(world, target) }.getOrDefault(false)
    if (!copied) {
        deleteTreeQuietly(target)
        return null
    }
    // Only after the files are there: naming it first would leave a world called "X copy" on
    // disk if the copy then failed.
    NbtEditor.edit(worldLevelFile(target)) { nbt ->
        NbtEditor.setString(nbt, newName, "Data", "LevelName")
    }
    return target
}

/** What happened to an export, in the terms the screen reports. */
sealed class WorldExport {
    data class Saved(val fileName: String) : WorldExport()
    object Failed : WorldExport()
}

/**
 * Write a world out to Downloads as a zip.
 *
 * Downloads for the same reason the texture pack exporter uses it: the game folder cannot be
 * browsed to from Android 11, so a backup written beside the world is a backup nobody can get
 * at, copy off the phone or send to anybody. A zip in Downloads is a file every app on the
 * device can see.
 *
 * The world's own folder name is the single entry prefix, so the archive unpacks the way the
 * importer here and every other launcher expects, and importing it back lands a working world.
 */
fun exportWorld(context: Context, world: File): WorldExport {
    val fileName = folderSafeName(world.name).ifEmpty { "world" } + ".zip"
    var uri: Uri? = null
    return try {
        val stream = openDownload(context, fileName) { uri = it } ?: return WorldExport.Failed
        ZipOutputStream(stream).use { zip -> zipTree(world, world.name, zip) }
        finishDownload(context, fileName, uri)
        WorldExport.Saved(fileName)
    } catch (t: Throwable) {
        WorldExport.Failed
    }
}

/* ------------------------------------------------------------------ plumbing */

private fun openDownload(
    context: Context,
    fileName: String,
    remember: (Uri) -> Unit
): OutputStream? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            // Hidden until every byte is there, the promise the texture pack exporter and the
            // gallery copy both make: a truncated zip offered to a file manager is a broken
            // restore waiting to happen, and a world backup is the worst thing to get that wrong.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val inserted = context.contentResolver
            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        remember(inserted)
        return context.contentResolver.openOutputStream(inserted)
    }
    val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!folder.exists() && !folder.mkdirs()) return null
    return FileOutputStream(File(folder, fileName))
}

private fun finishDownload(context: Context, fileName: String, uri: Uri?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val pending = uri ?: return
        context.contentResolver.update(
            pending, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null, null
        )
        return
    }
    val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    MediaScannerConnection.scanFile(
        context, arrayOf(File(folder, fileName).absolutePath), null, null
    )
}

/** Depth first, so a region folder's thousands of files do not all sit open at once. */
private fun zipTree(root: File, prefix: String, zip: ZipOutputStream) {
    val children = root.listFiles() ?: return
    for (child in children) {
        val entry = "$prefix/${child.name}"
        if (child.isDirectory) {
            zipTree(child, entry, zip)
        } else {
            zip.putNextEntry(ZipEntry(entry))
            child.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/**
 * Copy a folder tree.
 *
 * Bounded by a file count rather than trusted: a symlink loop inside a world folder would
 * otherwise fill the device, and a world with a hundred thousand files in it is not one this
 * should be silently chewing through either.
 */
private fun copyTree(from: File, to: File): Boolean {
    var remaining = 60_000
    val stack = ArrayDeque<Pair<File, File>>()
    stack.addLast(from to to)
    while (stack.isNotEmpty()) {
        val (source, target) = stack.removeLast()
        val children = source.listFiles() ?: continue
        for (child in children) {
            if (--remaining <= 0) return false
            val destination = File(target, child.name)
            if (child.isDirectory) {
                if (!destination.mkdirs() && !destination.isDirectory) return false
                stack.addLast(child to destination)
            } else {
                child.inputStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
    return true
}

private fun measureTree(root: File): Long {
    var total = 0L
    val stack = ArrayDeque<File>()
    stack.addLast(root)
    var visited = 0
    while (stack.isNotEmpty()) {
        val next = stack.removeLast()
        if (++visited > 40_000) break
        val children = next.listFiles() ?: continue
        for (child in children) {
            if (child.isDirectory) stack.addLast(child) else total += child.length()
        }
    }
    return total
}

private fun deleteTreeQuietly(root: File) {
    val children = root.listFiles()
    if (children != null) for (child in children) deleteTreeQuietly(child)
    root.delete()
}

/**
 * A world name turned into something that can be a folder.
 *
 * The name comes from a text field, so it is a stranger's string in the same sense an imported
 * file's name is: separators would make it a path and the two directory names would make it the
 * parent. Trailing dots and spaces go too, because a folder ending in either is awkward on every
 * filesystem a phone might be using for external storage.
 */
internal fun folderSafeName(raw: String): String {
    val cleaned = raw
        .replace('/', ' ')
        .replace('\\', ' ')
        .replace(':', ' ')
        .filter { it.code >= 0x20 }
        .trim()
        .trimEnd('.', ' ')
    if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return ""
    return if (cleaned.length > 96) cleaned.substring(0, 96).trimEnd('.', ' ') else cleaned
}

private fun uniqueChild(parent: File, name: String): File {
    val first = File(parent, name)
    if (!first.exists()) return first
    var index = 2
    while (index < 1000) {
        val candidate = File(parent, "$name ($index)")
        if (!candidate.exists()) return candidate
        index++
    }
    return File(parent, name + "-" + System.currentTimeMillis())
}
