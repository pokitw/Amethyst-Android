package net.kdt.pojavlaunch.ui.recordings

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.text.format.Formatter
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.recorder.RecorderPreferences
import net.kdt.pojavlaunch.recorder.RecordingInfo
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How the gallery is ordered. */
enum class RecordingSort(val labelRes: Int) {
    NEWEST(net.kdt.pojavlaunch.R.string.recordings_sort_newest),
    OLDEST(net.kdt.pojavlaunch.R.string.recordings_sort_oldest),
    LARGEST(net.kdt.pojavlaunch.R.string.recordings_sort_largest),
    LONGEST(net.kdt.pojavlaunch.R.string.recordings_sort_longest)
}

/**
 * One clip as the gallery needs it.
 *
 * [durationMs] and [thumbnail] start empty and are filled in once the file has been read, so a
 * row can be shown immediately instead of waiting on the slowest part.
 */
data class Recording(
    val file: File,
    val title: String,
    val sizeLabel: String,
    val modifiedAt: Long,
    val sizeBytes: Long,
    val info: RecordingInfo,
    val durationMs: Long? = null,
    val thumbnail: Bitmap? = null
) {
    val key: String get() = file.absolutePath + "@" + modifiedAt

    /** Version, resolution, frame rate and audio, whichever of them are known. */
    fun details(): String? = buildList {
        info.minecraftVersion?.let { add(it) }
        if (info.width > 0 && info.height > 0) add("${info.width}×${info.height}")
        if (info.frameRate > 0) add("${info.frameRate} FPS")
        info.audio?.let { add(it) }
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The file name is the moment recording began; show it as something readable. */
private val FILENAME_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT)
private val DISPLAY_FORMAT = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())

private fun titleOf(file: File): String {
    val stem = file.name.removeSuffix(".mp4")
    return runCatching {
        synchronized(FILENAME_FORMAT) { FILENAME_FORMAT.parse(stem) }
            ?.let { date: Date -> synchronized(DISPLAY_FORMAT) { DISPLAY_FORMAT.format(date) } }
    }.getOrNull() ?: stem
}

/** Where the current profile's clips live, or null when that cannot be worked out. */
fun recordingsDirectory(): File? = runCatching {
    LauncherProfiles.load()
    RecorderPreferences.recordingsDirectory(Tools.getGameDirPath(LauncherProfiles.getCurrentProfile()))
}.getOrNull()

fun loadRecordings(context: Context): List<Recording> {
    val files = recordingsDirectory()
        ?.listFiles { _, name -> name.lowercase(Locale.ROOT).endsWith(".mp4") }
        ?: return emptyList()
    return files.map { file ->
        Recording(
            file = file,
            title = titleOf(file),
            sizeLabel = Formatter.formatShortFileSize(context, file.length()),
            modifiedAt = file.lastModified(),
            sizeBytes = file.length(),
            info = RecordingInfo.read(file)
        )
    }
}

fun List<Recording>.applySort(sort: RecordingSort): List<Recording> = when (sort) {
    RecordingSort.NEWEST -> sortedByDescending { it.modifiedAt }
    RecordingSort.OLDEST -> sortedBy { it.modifiedAt }
    RecordingSort.LARGEST -> sortedByDescending { it.sizeBytes }
    // Clips still being read fall back to size, which tracks length closely enough at a fixed
    // bitrate to keep the order from jumping about as durations arrive.
    RecordingSort.LONGEST -> sortedByDescending { it.durationMs ?: it.sizeBytes }
}

/** Duration and a preview frame, read off the file itself. Call from a background dispatcher. */
fun readMetadata(file: File): Pair<Long?, Bitmap?> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val duration = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
        // A moment in rather than the very first frame, which is often still a fade.
        val at = duration?.let { minOf(1_000_000L, it * 500L) } ?: 0L
        duration to retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (t: Throwable) {
        // A clip that will not give up its metadata still plays; it just shows without a preview.
        null to null
    } finally {
        runCatching { retriever.release() }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
