package net.kdt.pojavlaunch

import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.ui.content.ContentItem
import net.kdt.pojavlaunch.ui.content.ContentKind
import net.kdt.pojavlaunch.ui.content.ContentScreen
import net.kdt.pojavlaunch.ui.content.ContentSection
import net.kdt.pojavlaunch.ui.content.ContentState
import net.kdt.pojavlaunch.ui.content.ImportResult
import net.kdt.pojavlaunch.ui.content.deleteContent
import net.kdt.pojavlaunch.ui.content.describe
import net.kdt.pojavlaunch.ui.content.importContent
import net.kdt.pojavlaunch.ui.content.measure
import net.kdt.pojavlaunch.ui.content.readThumbnail
import net.kdt.pojavlaunch.ui.content.scanContent
import net.kdt.pojavlaunch.ui.content.setContentEnabled
import net.kdt.pojavlaunch.ui.content.summarise
import net.kdt.pojavlaunch.ui.home.currentGameDirectory
import net.kdt.pojavlaunch.ui.home.currentProfileLabel
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import java.io.File
import java.util.Locale

/**
 * Hosts the one screen that owns everything a profile has.
 *
 * Its own activity, like the recordings gallery, because it is reached from the home screen and
 * from Settings and has nothing to do with the launch path. The game directory is resolved once
 * here, on the main thread, because that goes through `LauncherProfiles` — everything under
 * `ui/content` takes the resolved folder as an argument for that reason.
 *
 * <b>Three passes, in order of how long they take.</b> The directory listing is instant and lands
 * first. Opening each item for its real name is not, so that follows in batches. Measuring a world
 * folder means walking thousands of region files, so that goes last and on its own — it is the
 * only genuinely slow part of reading a profile, and it must not hold up the names, which are what
 * anyone opened the screen to see. Pictures are not in any of the three: a row asks for its own
 * when it first appears, so a profile with four hundred screenshots decodes the eight on screen.
 *
 * <b>The list is filtered here, not in the composition.</b> Search and category live in this
 * activity so that a batch landing recomputes one list once, rather than making every `remember`
 * in the screen miss and re-filtering inside a recomposition.
 */
class ContentActivity : BaseActivity() {
    /** BaseActivity hides the system bars by default, which is right for the game and not here. */
    override fun setFullscreen(): Boolean = false

    private var items by mutableStateOf<List<ContentItem>>(emptyList())
    private var loading by mutableStateOf(true)
    private var query by mutableStateOf("")
    private var filter by mutableStateOf<ContentKind?>(null)
    private var profileLabel by mutableStateOf<String?>(null)
    private var storageLine by mutableStateOf("")

    private lateinit var gameDir: File
    private var detailJob: Job? = null
    private var freeSpace = ""

    /** Ids already handed to a thumbnail load, so a row scrolling back does not ask twice. */
    private val thumbnailsAsked = HashSet<String>()

    /** What each item looked like on disk when it was read, so a later scan can skip it. */
    private val fingerprints = HashMap<String, Long>()

    // Made once rather than per recomposition. A row skips recomposing only if every argument it
    // was given is equal to the last one, and a fresh method reference on each pass is not — which
    // would mean every visible row rebuilding each time a thumbnail lands, while scrolling.
    private val toggleItem: (ContentItem, Boolean) -> Unit = { item, on -> toggle(item, on) }
    private val deleteItem: (ContentItem) -> Unit = { item -> delete(item) }
    private val openItem: (ContentItem) -> Unit = { item -> openPath(item.file) }
    private val needThumbnail: (ContentItem) -> Unit = { item -> loadThumbnail(item) }

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
                val lowered = query.trim().lowercase(Locale.getDefault())
                // Keyed on the three things that can change it, so a batch of metadata landing
                // costs one pass over the list rather than one per row.
                val sections = remember(items, lowered, filter) { buildSections(lowered) }
                ContentScreen(
                    state = ContentState(
                        sections = sections,
                        total = items.size,
                        loading = loading,
                        profileLabel = profileLabel,
                        storageLine = storageLine,
                        filterLabel = filter?.name ?: "all",
                        searching = lowered.isNotEmpty(),
                        filtered = filter != null
                    ),
                    query = query,
                    onQuery = { query = it },
                    onFilter = { value ->
                        filter = ContentKind.values().firstOrNull { it.name == value }
                    },
                    onAdd = ::pick,
                    onToggle = toggleItem,
                    onDelete = deleteItem,
                    onOpen = openItem,
                    onNeedThumbnail = needThumbnail,
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

    private fun buildSections(lowered: String): List<ContentSection> {
        val result = ArrayList<ContentSection>(ContentKind.values().size)
        for (kind in ContentKind.values()) {
            if (filter != null && filter != kind) continue
            val ofKind = items.filter { it.kind == kind && it.matches(lowered) }
            if (ofKind.isEmpty()) continue
            val bytes = ofKind.sumOf { it.sizeBytes }
            val heading = getString(kind.titleRes).uppercase(Locale.getDefault()) +
                    if (bytes > 0L) " · " + Formatter.formatShortFileSize(this, bytes) else ""
            result.add(ContentSection(kind, heading, ofKind))
        }
        return result
    }

    /**
     * Re-read the folder, keeping everything that has not changed on disk.
     *
     * This runs on every resume, which is also every return from a screenshot viewer or a file
     * manager. Reading it from scratch each time meant reopening two hundred jars and decoding
     * every picture again to end up with the same screen, so an item whose file has not been
     * touched since it was read is carried over whole — the visible cost of coming back is then a
     * directory listing.
     */
    private fun reload() {
        detailJob?.cancel()
        lifecycleScope.launch {
            val previous = HashMap<String, ContentItem>(items.size * 2)
            for (item in items) previous[key(item)] = item
            // Everything that touches the disk happens here; the two bookkeeping sets are only
            // ever read and written on the main thread, which is what keeps them plain maps.
            val scanned = withContext(Dispatchers.IO) {
                ContentKind.values().flatMap { scanContent(gameDir, it) }
                    .map { fresh ->
                        Triple(
                            fresh.copy(summary = summarise(this@ContentActivity, fresh)),
                            key(fresh),
                            fingerprint(fresh)
                        )
                    }
            }
            items = scanned.map { (fresh, id, mark) ->
                val kept = previous[id]
                if (kept != null && kept.detailed && fingerprints[id] == mark) {
                    kept
                } else {
                    fingerprints[id] = mark
                    thumbnailsAsked.remove(id)
                    fresh
                }
            }
            val present = HashSet<String>(scanned.size * 2)
            for ((_, id, _) in scanned) present.add(id)
            fingerprints.keys.retainAll(present)
            thumbnailsAsked.retainAll(present)
            loading = false
            freeSpace = runCatching {
                Formatter.formatShortFileSize(this@ContentActivity, gameDir.usableSpace)
            }.getOrDefault("")
            updateStorageLine()
            describeAll()
        }
    }

    /**
     * What on disk decides whether an item still needs reading.
     *
     * A world is a folder, and a folder's timestamp does not move when a region file deep inside
     * it does — but `level.dat` is rewritten every time the world is saved, so that is the file to
     * ask. Everything else is the item itself.
     */
    private fun fingerprint(item: ContentItem): Long {
        val file =
            if (item.kind == ContentKind.WORLD) File(item.file, "level.dat") else item.file
        return file.lastModified() * 31L + file.length()
    }

    /**
     * Fill in the names, then the sizes.
     *
     * One coroutine for both so they cannot overlap, and the whole list is replaced once per batch
     * rather than once per item — a single write is one recomposition and one re-filter, and four
     * hundred writes would be four hundred of each.
     */
    private fun describeAll() {
        detailJob = lifecycleScope.launch {
            for (batch in items.filterNot { it.detailed }.chunked(BATCH)) {
                val described = withContext(Dispatchers.IO) {
                    batch.map { describe(it) }
                        .map { it.copy(summary = summarise(this@ContentActivity, it)) }
                }
                applyAll(described)
            }
            updateStorageLine()

            // Last, and only for the things whose size is not simply a file length. This is the
            // slow one: a world is thousands of region files.
            val unmeasured = items.filter { it.file.isDirectory && it.sizeBytes == 0L }
            for (batch in unmeasured.chunked(SIZE_BATCH)) {
                val measured = withContext(Dispatchers.IO) {
                    batch.map { item ->
                        val sized = item.copy(sizeBytes = measure(item))
                        sized.copy(summary = summarise(this@ContentActivity, sized))
                    }
                }
                applyAll(measured)
                updateStorageLine()
            }
        }
    }

    /**
     * Replace a batch of rows in one write, keyed by kind and id rather than by position.
     *
     * A batch is worked out from a snapshot taken before it went to the IO thread, so the three
     * fields the user can change in the meantime — the switch, the rename behind it, and the
     * picture that arrived — are taken from the row as it stands now. Otherwise a mod turned off
     * while its jar was being read would turn itself back on a moment later.
     */
    private fun applyAll(updated: List<ContentItem>) {
        if (updated.isEmpty()) return
        val byKey = HashMap<String, ContentItem>(updated.size * 2)
        for (item in updated) byKey[key(item)] = item
        items = items.map { current ->
            val fresh = byKey[key(current)] ?: return@map current
            fresh.copy(
                file = current.file,
                enabled = current.enabled,
                thumbnail = current.thumbnail ?: fresh.thumbnail,
                thumbnailRequested = current.thumbnailRequested || fresh.thumbnailRequested
            )
        }
    }

    /**
     * Write one row straight through, without the merge [applyAll] does.
     *
     * This is the path for a change the row itself just made — a switch flipped, a picture that
     * arrived. Those are exactly the fields the merge protects, so putting them through it would
     * hand back the values they were replacing.
     */
    private fun put(item: ContentItem) {
        val id = key(item)
        items = items.map { if (key(it) == id) item else it }
    }

    private fun key(item: ContentItem) = item.kind.name + "/" + item.id

    private fun updateStorageLine() {
        val used = items.sumOf { it.sizeBytes }
        storageLine = getString(
            R.string.content_storage_line,
            Formatter.formatShortFileSize(this, used),
            freeSpace
        )
    }

    /**
     * Read one item's picture, because a row asked for it.
     *
     * The set is what stops a second read, not the flag on the item: marking the item would be a
     * state change, and a state change here rebuilds the list and recomposes every visible row.
     * Scrolling asks for a picture per row, so that would have been two list rebuilds per row
     * instead of one — and the second would land in the middle of the fling.
     */
    private fun loadThumbnail(item: ContentItem) {
        if (!thumbnailsAsked.add(key(item))) return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { readThumbnail(item) } ?: return@launch
            val current = items.firstOrNull { key(it) == key(item) } ?: return@launch
            put(current.copy(thumbnail = bitmap, thumbnailRequested = true))
        }
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
     * otherwise be rescanned, and every picture dropped and fetched again, to flip one switch.
     */
    private fun toggle(item: ContentItem, enabled: Boolean) {
        val renamed = setContentEnabled(item, enabled)
        if (renamed == null) {
            toast(getString(R.string.content_toggle_failed))
            return
        }
        // The rename changes the file's timestamp, so the fingerprint has to follow it or the next
        // resume would decide the jar is new and read it again.
        val moved = item.copy(file = renamed, enabled = enabled)
        fingerprints[key(moved)] = fingerprint(moved)
        put(moved)
    }

    private fun delete(item: ContentItem) {
        if (!deleteContent(item)) {
            toast(getString(R.string.content_delete_failed))
            return
        }
        thumbnailsAsked.remove(key(item))
        fingerprints.remove(key(item))
        items = items.filterNot { key(it) == key(item) }
        updateStorageLine()
    }

    /** Hand a file to whatever the device opens it with, through the launcher's own provider. */
    private fun openPath(file: File) {
        runCatching { Tools.openPath(this, file, false) }
            .onFailure { toast(getString(R.string.content_open_failed)) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        /** Enough that the list visibly fills in, few enough that it is not one write per row. */
        const val BATCH = 12
        /** Folder walks are slower, so fewer of them per write. */
        const val SIZE_BATCH = 4
    }
}
