package net.kdt.pojavlaunch

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModInstall
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthMods
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache
import net.kdt.pojavlaunch.ui.mods.InstallState
import net.kdt.pojavlaunch.ui.mods.ModBrowserScreen
import net.kdt.pojavlaunch.ui.mods.ModBrowserState
import net.kdt.pojavlaunch.ui.mods.ModRow
import net.kdt.pojavlaunch.ui.mods.ModTarget
import net.kdt.pojavlaunch.ui.mods.currentModTarget
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Hosts the Modrinth browser.
 *
 * Its own activity, like Game files and the recordings gallery, because it has nothing to do with
 * the launch path. It is reached from the add button in Game files, which is where someone who
 * wants another mod already is.
 *
 * <b>Every request is off the main thread and every state write is on it.</b> Modrinth is a
 * network call and the install is a download; both run on IO. The state that the composition reads
 * is a single `var` written only from the main dispatcher, so there is one place to look when a
 * row does not update.
 *
 * <b>Searches replace each other.</b> Typing is faster than a round trip, so an in-flight search
 * is cancelled when the next one starts. Without that the results that land last win, and the last
 * to land is not reliably the last typed.
 */
class ModBrowserActivity : BaseActivity() {
    /** BaseActivity hides the system bars by default, which is right for the game and not here. */
    override fun setFullscreen(): Boolean = false

    // Starts empty and is filled in from IO. currentModTarget reads a profile and can parse a
    // version manifest off disk, which is not work for the thread that draws the first frame.
    private var state by mutableStateOf(ModBrowserState(target = ModTarget("", null, null, null, null), loading = true))
    private var searchJob: Job? = null

    /**
     * The icon loader, built on first use and stopped in onDestroy.
     *
     * Nullable rather than lazy, for two reasons. Its pool holds ten core threads that never time
     * out, so an owner that is opened and thrown away has to be able to ask whether there is
     * anything to stop without building one to find out. And its constructor makes a directory and
     * throws when it cannot, which is a reason to draw the fallback icon and not a reason to take
     * the search down with it.
     */
    private var icons: ModIconCache? = null
    private var iconsTried = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmethystXTheme {
                ModBrowserScreen(
                    state = state,
                    onQuery = { state = state.copy(query = it) },
                    onSearch = { search(reset = true) },
                    onToggleFilter = {
                        state = state.copy(filtered = !state.filtered)
                        search(reset = true)
                    },
                    onInstall = ::install,
                    onNeedIcon = ::loadIcon,
                    onLoadMore = { search(reset = false) },
                    onBack = ::finish
                )
            }
        }
        refreshTarget(thenSearch = true)
    }

    override fun onResume() {
        super.onResume()
        // The profile can be switched, and the folder written to, while this screen is in the
        // background. Both are wrong to keep across a pause; neither is worth re-searching for.
        if (state.rows.isNotEmpty() || state.failed) refreshTarget(thenSearch = false)
    }

    /**
     * Re-read which profile is selected, then optionally search for it.
     *
     * Off the main thread because it can end up parsing a version manifest, and a modded manifest
     * is not a small file. The first search waits for it, since searching before the filter is
     * known would spend a round trip on the wrong query and then immediately replace it.
     */
    private fun refreshTarget(thenSearch: Boolean) {
        lifecycleScope.launch {
            val target = withContext(Dispatchers.IO) { currentModTarget() }
            state = state.copy(target = target, targetKnown = true)
            if (thenSearch) search(reset = true) else target.modsFolder?.let(::refreshInstalled)
        }
    }

    /**
     * Run a search.
     *
     * @param reset true for a new query, false to append the next page. The offset is the only
     *              difference, but the two feed different spinners: replacing the list while
     *              showing a bottom spinner would look like the list had jumped.
     */
    private fun search(reset: Boolean) {
        if (!reset && (state.loadingMore || !state.hasMore)) return
        searchJob?.cancel()
        val offset = if (reset) 0 else state.nextOffset
        // Captured before the list is cleared. Reading it after would give an empty map, which is
        // what made the reuse below inoperative: every icon was fetched again, and a row that was
        // mid-install came back IDLE and could be started a second time.
        val existing = state.rows.associateBy { it.hit.projectId }
        state = if (reset) {
            // loadingMore is cleared too: a reset that interrupts a pagination used to leave it
            // set, drawing the bottom spinner underneath the main one forever.
            state.copy(loading = true, loadingMore = false, failed = false, empty = false,
                rows = emptyList())
        } else {
            state.copy(loadingMore = true)
        }
        val query = state.query
        val mcVersion = state.effectiveVersion
        val loader = state.effectiveLoader
        val folder = state.target.modsFolder

        searchJob = lifecycleScope.launch {
            val page = withContext(Dispatchers.IO) {
                runCatching {
                    ModrinthMods.search(query, mcVersion, loader, offset, "mod")
                }.getOrNull()
            }
            if (page == null) {
                state = state.copy(
                    loading = false, loadingMore = false, failed = true,
                    failure = ModrinthMods.lastFailure()
                )
                return@launch
            }
            val fresh = page.hits
                // A second page can repeat a hit when the index shifts under the offset, and a
                // LazyColumn with two items under one key is a crash, not a cosmetic problem.
                .filter { reset || !existing.containsKey(it.projectId) }
                // The whole old row, not just its icon: an install running against this project
                // is still running, and drawing it as if it were not is how it gets started twice.
                .map { hit -> existing[hit.projectId]?.copy(hit = hit) ?: ModRow(hit) }
            val rows = if (reset) fresh else state.rows + fresh
            state = state.copy(
                rows = rows,
                loading = false,
                loadingMore = false,
                failed = false,
                empty = rows.isEmpty(),
                nextOffset = page.nextOffset,
                hasMore = page.hasMore()
            )
            if (folder != null) refreshInstalled(folder)
        }
    }

    /**
     * Mark the rows whose jar is already in the folder.
     *
     * Needs the file name, which the search does not return, so it costs one version lookup per
     * row and is done in the background after the list is already on screen. The tick appearing a
     * moment late is far better than the list appearing a moment late.
     */
    private fun refreshInstalled(folder: java.io.File) {
        lifecycleScope.launch {
            val names = withContext(Dispatchers.IO) {
                runCatching { folder.list()?.toSet() ?: emptySet() }.getOrDefault(emptySet())
            }
            state = state.copy(rows = state.rows.map { row ->
                // Matched on the project slug appearing in a file name, which is what Modrinth's
                // own file names are built from. Deliberately loose: a false tick is a mod the
                // player installs again over the top, and a missed one is a duplicate jar.
                val slug = row.hit.slug
                val present = slug.isNotEmpty() && names.any { it.lowercase().contains(slug) }
                // Assigned, not or-ed in. The flag disables the install button, so a one-way flag
                // meant a mod deleted in Game files stayed ticked and could never be reinstalled.
                // A row mid-install keeps its own state; the folder has not caught up with it yet.
                if (row.state == InstallState.WORKING) row else row.copy(installed = present)
            })
        }
    }

    /** Projects whose icon has been asked for, so scrolling a row out and back asks once. */
    private val iconsRequested = HashSet<String>()

    private fun iconCache(): ModIconCache? {
        icons?.let { return it }
        if (iconsTried) return null
        iconsTried = true
        icons = runCatching { ModIconCache() }
            .onFailure { Log.w("ModBrowser", "No icon cache, rows keep the fallback icon", it) }
            .getOrNull()
        return icons
    }

    private fun loadIcon(row: ModRow) {
        val url = row.hit.iconUrl ?: return
        if (url.isEmpty()) return
        val cache = iconCache() ?: return
        if (!iconsRequested.add(row.hit.projectId)) return
        cache.getImage({ bitmap ->
            // Released on failure, or one dropped request on a flaky connection would leave that
            // row grey until the activity was finished and reopened.
            if (bitmap == null) {
                runOnUiThread { iconsRequested.remove(row.hit.projectId) }
                return@getImage
            }
            runOnUiThread {
                state = state.copy(rows = state.rows.map {
                    if (it.hit.projectId == row.hit.projectId && it.icon == null) {
                        it.copy(icon = bitmap.asImageBitmap())
                    } else it
                })
            }
        }, row.hit.iconCacheTag(), url)
    }

    /**
     * Resolve the best version for this profile, then install it and its required dependencies.
     *
     * One tap does the whole thing on purpose. The version table on a mod page is the step this
     * screen exists to remove, and re-presenting it as a dialog would be putting it back.
     */
    private fun install(row: ModRow) {
        val folder = state.target.modsFolder
        if (folder == null) {
            update(row) { it.copy(state = InstallState.FAILED,
                note = getString(R.string.mods_browse_no_profile)) }
            return
        }
        update(row) { it.copy(state = InstallState.WORKING, note = null) }
        val mcVersion = state.effectiveVersion
        val loader = state.effectiveLoader

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val files = ModrinthMods.versions(row.hit.projectId, mcVersion, loader)
                    val best = ModrinthMods.best(files) ?: return@runCatching null
                    ModInstall.install(folder, best, mcVersion, loader)
                }.getOrNull()
            }
            when {
                outcome == null -> update(row) {
                    it.copy(state = InstallState.FAILED,
                        note = getString(R.string.mods_browse_no_version))
                }
                // Complete, not merely ok: a mod whose required dependency did not arrive crashes
                // the game on the next launch, and a green tick here is the one thing that would
                // stop anyone connecting that crash back to this tap.
                outcome.complete() -> update(row) {
                    val extra = outcome.installed.size - 1
                    it.copy(
                        state = InstallState.DONE,
                        installed = true,
                        note = if (extra > 0) {
                            resources.getQuantityString(
                                R.plurals.mods_browse_with_dependencies, extra, extra)
                        } else null
                    )
                }
                outcome.ok() -> update(row) {
                    it.copy(
                        state = InstallState.DONE,
                        installed = true,
                        note = resources.getQuantityString(
                            R.plurals.mods_browse_missing_dependencies,
                            outcome.missing.size, outcome.missing.size)
                    )
                }
                else -> update(row) {
                    it.copy(state = InstallState.FAILED, note = getString(
                        when (outcome.error) {
                            ModInstall.ERROR_FOLDER -> R.string.mods_browse_no_folder
                            else -> R.string.mods_browse_install_failed
                        }
                    ))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ten core threads that do not time out, against one cache per time the browser is opened.
        // Upstream's adapter lives as long as its fragment and so has never had to care; this does.
        icons?.shutdown()
        icons = null
    }

    private fun update(row: ModRow, change: (ModRow) -> ModRow) {
        state = state.copy(rows = state.rows.map {
            if (it.hit.projectId == row.hit.projectId) change(it) else it
        })
    }
}
