package net.kdt.pojavlaunch

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.ui.content.ContentItem
import net.kdt.pojavlaunch.ui.content.ContentKind
import net.kdt.pojavlaunch.ui.content.ContentScreen
import net.kdt.pojavlaunch.ui.content.ContentState
import net.kdt.pojavlaunch.ui.content.ImportResult
import net.kdt.pojavlaunch.ui.content.deleteContent
import net.kdt.pojavlaunch.ui.content.describe
import net.kdt.pojavlaunch.ui.content.importContent
import net.kdt.pojavlaunch.ui.content.readThumbnail
import net.kdt.pojavlaunch.ui.content.scanContent
import net.kdt.pojavlaunch.ui.content.setContentEnabled
import net.kdt.pojavlaunch.ui.home.currentGameDirectory
import net.kdt.pojavlaunch.ui.home.currentProfileLabel
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import java.io.File

/**
 * Hosts the one screen that owns everything a profile has.
 *
 * Its own activity, like the recordings gallery, because it is reached from the home screen and
 * from Settings and has nothing to do with the launch path. The game directory is resolved once
 * here, on the main thread, because that goes through `LauncherProfiles` — everything under
 * `ui/content` takes the resolved folder as an argument for that reason.
 *
 * <b>Two passes.</b> The listing is a directory read and lands immediately; opening four hundred
 * jars and worlds for their names, icons and sizes does not, so it happens afterwards in the
 * background and the rows are replaced as the answers arrive. That is why the list appears already
 * populated with skeletons rather than after a spinner: it is real from the first frame and simply
 * gets more detailed.
 */
class ContentActivity : BaseActivity() {
    /** BaseActivity hides the system bars by default, which is right for the game and not here. */
    override fun setFullscreen(): Boolean = false

    private val items = mutableStateListOf<ContentItem>()
    private var loading by mutableStateOf(true)
    private var profileLabel by mutableStateOf<String?>(null)
    private var freeSpace by mutableStateOf("")

    private lateinit var gameDir: File
    private var detailJob: Job? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameDir = runCatching { currentGameDirectory() }.getOrElse { File(Tools.DIR_GAME_NEW) }
        profileLabel = runCatching {
            val label = currentProfileLabel(this)
            listOfNotNull(label?.first, label?.second).joinToString(" · ").ifEmpty { null }
        }.getOrNull()

        setContent {
            AmethystXTheme {
                ContentScreen(
                    state = ContentState(
                        // Copied so the filtering below it is keyed on something that changes;
                        // a snapshot list keeps its identity forever and would never invalidate.
                        items = items.toList(),
                        loading = loading,
                        profileLabel = profileLabel,
                        freeSpace = freeSpace
                    ),
                    onAdd = ::pick,
                    onToggle = ::toggle,
                    onDelete = ::delete,
                    onOpen = ::open,
                    onOpenFolder = { openPath(gameDir) },
                    onBack = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        detailJob?.cancel()
    }

    private fun reload() {
        detailJob?.cancel()
        lifecycleScope.launch {
            val scanned = withContext(Dispatchers.IO) {
                ContentKind.values().flatMap { scanContent(gameDir, it) }
            }
            items.clear()
            items.addAll(scanned)
            loading = false
            freeSpace = runCatching {
                Formatter.formatShortFileSize(this@ContentActivity, gameDir.usableSpace)
            }.getOrDefault("")
            describeAll()
        }
    }

    /**
     * Open every item and fill in what the listing could not know.
     *
     * In batches rather than one at a time: each batch is one hop back to the main thread, and
     * four hundred hops would spend more time scheduling than reading. Small batches, though —
     * the point is that the screen fills in visibly rather than sitting still and then finishing.
     */
    private fun describeAll() {
        detailJob = lifecycleScope.launch {
            val snapshot = items.toList()
            for (batch in snapshot.chunked(BATCH)) {
                val described = withContext(Dispatchers.IO) {
                    batch.map { item ->
                        val detailed = describe(item)
                        detailed to readThumbnail(detailed)
                    }
                }
                for ((detailed, thumbnail) in described) {
                    replace(detailed.copy(thumbnail = thumbnail))
                }
            }
        }
    }

    private fun replace(item: ContentItem) {
        val index = items.indexOfFirst { it.kind == item.kind && it.id == item.id }
        if (index >= 0) items[index] = item
    }

    private fun pick() {
        // Everything, because what it is gets decided by looking inside it rather than by asking
        // the picker to filter. A jar is handed out as octet-stream by most providers anyway.
        runCatching { pickFile.launch(arrayOf("*/*")) }
            .onFailure { toast(getString(R.string.content_add_failed)) }
    }

    private fun importFile(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                importContent(this@ContentActivity, gameDir, uri)
            }
            when (result) {
                is ImportResult.Added -> {
                    toast(
                        getString(
                            R.string.content_added,
                            result.name,
                            getString(result.kind.titleRes)
                        )
                    )
                    reload()
                }
                ImportResult.Unrecognised -> toast(getString(R.string.content_add_unknown))
                ImportResult.Failed -> toast(getString(R.string.content_add_failed))
            }
        }
    }

    /**
     * Turn a mod on or off.
     *
     * The row is replaced rather than the folder re-read: a profile of four hundred items would
     * otherwise be rescanned, and every icon dropped and fetched again, to flip one switch.
     */
    private fun toggle(item: ContentItem, enabled: Boolean) {
        val renamed = setContentEnabled(item, enabled)
        if (renamed == null) {
            toast(getString(R.string.content_toggle_failed))
            return
        }
        replace(item.copy(file = renamed, enabled = enabled))
    }

    private fun delete(item: ContentItem) {
        if (!deleteContent(item)) {
            toast(getString(R.string.content_delete_failed))
            return
        }
        items.removeAll { it.kind == item.kind && it.id == item.id }
    }

    /** Hand the item to whatever the device opens it with, through the launcher's own provider. */
    private fun open(item: ContentItem) = openPath(item.file)

    private fun openPath(file: File) {
        runCatching { Tools.openPath(this, file, false) }
            .onFailure { toast(getString(R.string.content_open_failed)) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val BATCH = 6
    }
}
