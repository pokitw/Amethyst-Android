package net.kdt.pojavlaunch.recorder

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.BaseActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.ui.recordings.Recording
import net.kdt.pojavlaunch.ui.recordings.RecordingSort
import net.kdt.pojavlaunch.ui.recordings.RecordingsScreen
import net.kdt.pojavlaunch.ui.recordings.loadRecordings
import net.kdt.pojavlaunch.ui.recordings.readMetadata
import net.kdt.pojavlaunch.ui.recordings.applySort
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import java.io.File

/**
 * Hosts the recordings gallery.
 *
 * Still a [BaseActivity] so it keeps the launcher's locale and fullscreen handling, with Compose
 * supplying the content. Playing and sharing go through the launcher's existing document
 * provider, so a clip is never copied just to be opened.
 */
class RecordingsActivity : BaseActivity() {
    private val recordings = mutableStateListOf<Recording>()
    private var sort by mutableStateOf(RecordingSort.NEWEST)
    private var versionFilter by mutableStateOf<String?>(null)

    /** Files already read, so scrolling back over a row does not read it again. */
    private val metadataRead = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmethystXTheme {
                val shown = recordings
                    .filter { versionFilter == null || it.info.minecraftVersion == versionFilter }
                    .applySort(sort)
                RecordingsScreen(
                    recordings = shown,
                    sort = sort,
                    versionFilter = versionFilter,
                    // Only versions actually present, so a filter can never come back empty.
                    availableVersions = recordings
                        .mapNotNull { it.info.minecraftVersion }
                        .distinct()
                        .sorted(),
                    onSortChange = { sort = it },
                    onVersionFilterChange = { versionFilter = it },
                    onPlay = { open(it.file, share = false) },
                    onShare = { open(it.file, share = true) },
                    onDelete = ::delete,
                    onBack = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { loadRecordings(this@RecordingsActivity) }
            recordings.clear()
            recordings.addAll(found)
            // Rows appear straight away; the parts that need the file opened arrive after.
            found.forEach { readMetadataFor(it) }
        }
    }

    private fun readMetadataFor(recording: Recording) {
        if (!metadataRead.add(recording.key)) return
        lifecycleScope.launch {
            val (duration, thumbnail) = withContext(Dispatchers.IO) { readMetadata(recording.file) }
            if (duration == null && thumbnail == null) return@launch
            update(recording.key, duration, thumbnail)
        }
    }

    private fun update(key: String, duration: Long?, thumbnail: Bitmap?) {
        val index = recordings.indexOfFirst { it.key == key }
        if (index < 0) return
        recordings[index] = recordings[index].copy(durationMs = duration, thumbnail = thumbnail)
    }

    private fun open(file: File, share: Boolean) {
        try {
            Tools.openPath(this, file, share)
        } catch (t: Throwable) {
            Toast.makeText(this, R.string.recordings_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun delete(recording: Recording) {
        if (!recording.file.delete())
            Toast.makeText(this, R.string.recordings_delete_failed, Toast.LENGTH_LONG).show()
        // Otherwise the details would outlive the clip they describe.
        RecordingInfo.delete(recording.file)
        metadataRead.remove(recording.key)
        reload()
    }
}
