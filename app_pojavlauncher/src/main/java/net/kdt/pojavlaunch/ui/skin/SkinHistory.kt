package net.kdt.pojavlaunch.ui.skin

import android.graphics.Bitmap
import android.util.Log
import net.kdt.pojavlaunch.Tools
import java.io.File
import java.io.FileOutputStream

/**
 * Every skin this launcher has put on an account, kept because Mojang does not keep it.
 *
 * <b>This is the part that had to be built rather than fetched.</b> There is no skin history
 * endpoint and there never has been: the profile response carries a `skins` array shaped as
 * though it were one, and in practice it holds the skin being worn now. So "any skin you have ever
 * worn" is a question only the thing that applied them can answer, and the honest scope of that
 * answer is "since this launcher started keeping the record", which the screen says rather than
 * implying otherwise.
 *
 * <b>An entry owns its pixels.</b> The PNG is copied into the history folder at the moment it is
 * recorded, never referenced where it already sits. A history that pointed at the library would
 * turn "the skin I wore in March" into "whatever that file says today", and the file is one the
 * editor writes to in place. That copy is the whole reason this is a folder and not a list of
 * names.
 *
 * Bounded at [MAX_ENTRIES], oldest dropped first, because a record that grows forever on a phone
 * is a bug with a nice name.
 */
private const val FOLDER = "history"
private const val INDEX = "history.json"
private const val MAX_ENTRIES = 40

/** Where a history entry came from, which is worth knowing when choosing one again. */
enum class SkinOrigin { APPLIED, ACCOUNT, PLAYER }

/** One skin the launcher has seen on the account, with its own copy of the picture. */
class SkinHistoryEntry(
    /** File name inside the history folder. */
    val file: String = "",
    val at: Long = 0L,
    val slim: Boolean = false,
    val origin: String = SkinOrigin.APPLIED.name,
    /** A player name for a looked-up skin, the skin's own name for an applied one. */
    val label: String = ""
) {
    fun originKind(): SkinOrigin =
        runCatching { SkinOrigin.valueOf(origin) }.getOrDefault(SkinOrigin.APPLIED)
}

private class HistoryIndex(val entries: MutableList<SkinHistoryEntry> = mutableListOf())

fun historyFolder(): File = File(skinFolder(), FOLDER)

private fun indexFile(): File = File(historyFolder(), INDEX)

/**
 * Read the record, newest first.
 *
 * An unreadable index is an empty history rather than a crash: this is a convenience sitting
 * beside the thing that actually matters, and it must never be what stops the editor opening.
 */
fun readSkinHistory(): List<SkinHistoryEntry> = runCatching {
    val file = indexFile()
    if (!file.isFile) return@runCatching emptyList()
    val index = Tools.GLOBAL_GSON.fromJson(Tools.read(file.absolutePath), HistoryIndex::class.java)
        ?: return@runCatching emptyList()
    index.entries
        // An entry whose picture has been deleted underneath us is dropped rather than shown as
        // a hole, which is the only state the folder and the index can disagree in.
        .filter { it.file.isNotEmpty() && File(historyFolder(), it.file).isFile }
        .sortedByDescending { it.at }
}.onFailure { Log.w("SkinHistory", "Could not read the skin history", it) }
    .getOrDefault(emptyList())

/**
 * Record a skin, taking a copy of it.
 *
 * @param at  the moment to stamp it with. Passed in rather than read here so the caller owns the
 *            clock, which is what lets the record be driven in a test.
 * @return the entry written, or null when nothing could be
 */
fun recordSkinHistory(
    bitmap: Bitmap,
    slim: Boolean,
    origin: SkinOrigin,
    label: String,
    at: Long
): SkinHistoryEntry? = runCatching {
    val folder = historyFolder()
    if (!folder.isDirectory && !folder.mkdirs()) return@runCatching null
    val name = "$at.png"
    FileOutputStream(File(folder, name)).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val entry = SkinHistoryEntry(name, at, slim, origin.name, label)
    val kept = (listOf(entry) + readSkinHistory()).distinctBy { it.file }.take(MAX_ENTRIES)
    // Anything that fell off the end takes its picture with it, or the folder grows without the
    // index ever admitting to it.
    val keptNames = kept.map { it.file }.toSet()
    folder.listFiles()?.forEach { file ->
        if (file.isFile && file.name.endsWith(".png") && file.name !in keptNames) file.delete()
    }
    Tools.write(indexFile().absolutePath,
        Tools.GLOBAL_GSON.toJson(HistoryIndex(kept.toMutableList())))
    entry
}.onFailure { Log.w("SkinHistory", "Could not record a skin", it) }.getOrNull()

/** Delete one entry and its picture. */
fun forgetSkinHistory(entry: SkinHistoryEntry): Boolean = runCatching {
    File(historyFolder(), entry.file).delete()
    val kept = readSkinHistory().filter { it.file != entry.file }
    Tools.write(indexFile().absolutePath,
        Tools.GLOBAL_GSON.toJson(HistoryIndex(kept.toMutableList())))
    true
}.getOrDefault(false)
