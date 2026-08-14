package net.kdt.pojavlaunch.ui.logs

import androidx.compose.runtime.Immutable
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.logs.LogParser
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Finding the log and holding what was read.
 *
 * The parsing itself is deliberately not here: it lives in [LogParser], in Java, because Java is
 * the only thing this project can compile and drive without a device (`scripts/logsim`), and a
 * level filter that quietly files an error as INFO is exactly the kind of wrong that never shows
 * up until somebody needs it.
 */

typealias LogLevel = LogParser.Level
typealias LogLine = LogParser.Line

/**
 * The loaded window onto a log file.
 *
 * [fileBytes] is the whole file and [shownBytes] is what was read, so the screen can say plainly
 * when it is showing a tail rather than everything.
 */
@Immutable
class LogDocument(
    val lines: List<LogLine> = emptyList(),
    val fileBytes: Long = 0L,
    val shownBytes: Long = 0L,
    /** Whether any line declared a level at all; see [readLog] for why this is asked. */
    val hasLevels: Boolean = false,
    /** Whether there was a log file to read in the first place. */
    val present: Boolean = false
) {
    val truncated: Boolean get() = shownBytes < fileBytes
}

/**
 * How much of the end is loaded.
 *
 * A modded session writes tens of megabytes and a phone should not hold all of it as strings, but
 * the end is where a crash puts its reason, so the tail is the half worth having. One mebibyte is
 * on the order of ten thousand lines, which a `LazyColumn` scrolls without complaint and a search
 * crosses in a millisecond.
 */
private const val TAIL_BYTES = 1L shl 20

/** The log the launcher and the game share, or null when the game directory is not ready. */
fun launcherLogFile(): File? = Tools.DIR_GAME_HOME?.let { File(it, "latestlog.txt") }

/**
 * Read the end of the log.
 *
 * Decoded as UTF-8 rather than byte for byte, because this is read by a person: chat, world names
 * and mod names all carry characters that would otherwise arrive as mojibake. A tail can begin
 * halfway through a multi-byte character, which lands as one replacement character on the first
 * line and nothing worse.
 *
 * [LogDocument.hasLevels] is asked because the format is Minecraft's and not ours. If a future
 * version stops writing levels the parse finds none, and a filter offered in that state would
 * hide the entire log while claiming to show its errors. The screen leaves the filter out
 * instead.
 */
fun readLog(file: File? = launcherLogFile()): LogDocument = runCatching {
    if (file == null || !file.isFile) return@runCatching LogDocument()
    val text: String
    val length: Long
    val read: Long
    RandomAccessFile(file, "r").use { raf ->
        length = raf.length()
        val start = maxOf(0L, length - TAIL_BYTES)
        read = length - start
        if (read <= 0L) return@runCatching LogDocument(present = true)
        raf.seek(start)
        val buffer = ByteArray(read.toInt())
        raf.readFully(buffer)
        text = String(buffer, StandardCharsets.UTF_8)
    }
    val lines = LogParser.parse(text)
    LogDocument(
        lines = lines,
        fileBytes = length,
        shownBytes = read,
        hasLevels = LogParser.declaresLevels(lines),
        present = true
    )
}.getOrDefault(LogDocument())

/** Which lines to show, as indices into [LogDocument.lines]. See [LogParser.visible]. */
fun visibleLines(document: LogDocument, query: String, minimum: LogLevel): List<Int> =
    LogParser.visible(document.lines, query, minimum).toList()
