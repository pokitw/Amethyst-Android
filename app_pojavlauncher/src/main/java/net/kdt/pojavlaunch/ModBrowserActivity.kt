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
import net.kdt.pojavlaunch.ui.mods.ModProjectScreen
import net.kdt.pojavlaunch.ui.mods.ModTarget
import net.kdt.pojavlaunch.ui.mods.ProjectPage
import net.kdt.pojavlaunch.ui.mods.currentModTarget
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import java.util.Locale

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
    companion object {
        /** A search to open with, sent by whatever knew what the player was looking for. */
        const val EXTRA_QUERY = "query"
    }

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

    /** Back closes the page before it closes the browser, so a mod page is not a one-way trip. */
    private val pageBack = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closePage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, pageBack)
        setContent {
            AmethystXTheme {
                val page = state.page
                if (page != null) {
                    ModProjectScreen(
                        page = page,
                        installEnabled = state.targetKnown && state.target.canRunMods,
                        rowIcon = state.rows.firstOrNull {
                            it.hit.projectId == page.hit.projectId
                        }?.icon,
                        onInstallBest = ::installBestFromPage,
                        onInstallVersion = ::installVersionFromPage,
                        onNeedGallery = ::loadGalleryImage,
                        onBack = ::closePage
                    )
                } else {
                    ModBrowserScreen(
                        state = state,
                        onQuery = { state = state.copy(query = it) },
                        onSearch = { search(reset = true) },
                        onToggleFilter = {
                            state = state.copy(filtered = !state.filtered)
                            search(reset = true)
                        },
                        onSort = {
                            state = state.copy(sort = it)
                            search(reset = true)
                        },
                        onCategory = {
                            state = state.copy(category = it)
                            search(reset = true)
                        },
                        onInstall = ::install,
                        onOpen = ::openPage,
                        onNeedIcon = ::loadIcon,
                        onLoadMore = { search(reset = false) },
                        onBack = ::finish
                    )
                }
            }
        }
        // Opened with something already in mind: Game files sends the id of a dependency it found
        // missing, so the answer to "you need Cloth Config" is the screen that installs it rather
        // than a name to go and type in. Set before the first search rather than searched for
        // afterwards, so there is never a flash of the default results underneath it.
        intent?.getStringExtra(EXTRA_QUERY)?.takeIf { it.isNotEmpty() }?.let {
            state = state.copy(query = it)
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
     * On the main thread on purpose, and synchronously: [LauncherProfiles] is unsynchronised
     * static state shared with the launch path, and the handbook (12.7) says it is read on the
     * main thread only. Reading it on IO here was not a performance win, it was the bug: the
     * store was never loaded in this process, the lookup threw, and the screen spent its whole
     * life claiming the profile could not run mods with an install button that did nothing.
     * The work is a preference, a map lookup and at worst one small manifest read.
     */
    private fun refreshTarget(thenSearch: Boolean) {
        val target = currentModTarget()
        state = state.copy(target = target, targetKnown = true)
        if (thenSearch) search(reset = true) else target.modsFolder?.let(::refreshInstalled)
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
                    ModrinthMods.search(query, mcVersion, loader, offset, "mod",
                        state.sort.ifEmpty { null }, state.category.ifEmpty { null })
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
                val slug = row.hit.slug.lowercase(Locale.getDefault())
                // Both sides lowercased (a slug is lowercase on Modrinth, but nothing guarantees
                // the copy we were handed is), and this screen's own half-written .part files
                // skipped so an install in flight does not tick its own row.
                val present = slug.isNotEmpty() && names.any {
                    val name = it.lowercase(Locale.getDefault())
                    !name.endsWith(".part") && name.contains(slug)
                }
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
        // The button under this is always tappable, so the refusals happen here, in words on the
        // row. A tap on a mod the profile cannot run, or on one already present, must answer.
        if (!state.target.canRunMods) {
            update(row) { it.copy(state = InstallState.FAILED,
                note = getString(R.string.mods_browse_no_loader_short)) }
            return
        }
        // Only a DONE row is refused, and only because this screen put the jar there a moment
        // ago. row.installed is a GUESS, a file name that happens to contain the project's slug,
        // and a guess must never be a veto: a folder holding reeses-sodium-options ticks sodium,
        // and a mod turned off in Game files keeps its name, so vetoing on it would make a real
        // mod permanently uninstallable. Running the install again is cheap and self-correcting,
        // because ModInstall skips a file whose hash already matches and writes nothing.
        if (row.state == InstallState.DONE) {
            update(row) { it.copy(note = getString(R.string.mods_browse_already_installed)) }
            return
        }
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
            // Two outcomes, told apart: no version that fits, or the request never landed.
            // Reporting a rate limit as "no version fits this profile" sends someone to change
            // their profile, which is the one thing that cannot help.
            var reachedIndex = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val files = ModrinthMods.versions(row.hit.projectId, mcVersion, loader)
                    reachedIndex = ModrinthMods.lastFailure() == ModrinthMods.Failure.NONE
                    val best = ModrinthMods.best(files) ?: return@runCatching null
                    ModInstall.install(folder, best, mcVersion, loader)
                }.onFailure {
                    Log.w("ModBrowser", "Install of ${row.hit.title} threw", it)
                }.getOrNull()
            }
            when {
                outcome == null -> update(row) {
                    it.copy(state = InstallState.FAILED, note = getString(
                        if (reachedIndex) R.string.mods_browse_no_version
                        else R.string.mods_browse_install_unreachable
                    ))
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

    /* ------------------------------------------------------------------ the project page */

    /** Gallery URLs already asked for, so a recomposition cannot fetch a picture twice. */
    private val galleryRequested = HashSet<String>()

    private fun openPage(row: ModRow) {
        state = state.copy(page = ProjectPage(hit = row.hit))
        pageBack.isEnabled = true
        val projectId = row.hit.projectId
        val mcVersion = state.effectiveVersion
        val loader = state.effectiveLoader
        lifecycleScope.launch {
            val project = withContext(Dispatchers.IO) {
                runCatching { ModrinthMods.project(projectId) }.getOrNull()
            }
            updatePage(projectId) { it.copy(project = project, projectFailed = project == null) }
            val versions = withContext(Dispatchers.IO) {
                runCatching { ModrinthMods.versions(projectId, mcVersion, loader) }.getOrNull()
            }
            updatePage(projectId) {
                it.copy(versions = versions.orEmpty(), versionsKnown = true)
            }
        }
    }

    private fun closePage() {
        state = state.copy(page = null)
        pageBack.isEnabled = false
        galleryRequested.clear()
        // The page may have installed something; the list's ticks should say so on the way back.
        state.target.modsFolder?.let(::refreshInstalled)
    }

    /**
     * Publish a change to the page, but only the page it was for.
     *
     * A fetch can outlive the page it was started from: close a mod and open another and the
     * first one's project would otherwise land on the second one's screen.
     */
    private fun updatePage(projectId: String, change: (ProjectPage) -> ProjectPage) {
        val page = state.page ?: return
        if (page.hit.projectId != projectId) return
        state = state.copy(page = change(page))
    }

    private fun installBestFromPage() {
        val page = state.page ?: return
        // Order matters. "No version fits this profile" is only true once the versions have
        // actually arrived; said while the request is still in flight it is a lie, and it is the
        // most likely moment for someone to tap, because the header button is on screen before
        // the list is.
        if (!page.versionsKnown) {
            updatePage(page.hit.projectId) {
                it.copy(note = getString(R.string.mods_page_still_loading), noteIsError = false)
            }
            return
        }
        val best = ModrinthMods.best(page.versions)
        if (best == null) {
            updatePage(page.hit.projectId) {
                it.copy(note = getString(R.string.mods_browse_no_version), noteIsError = true)
            }
            return
        }
        installVersionFromPage(best)
    }

    /** Install one exact version from the page's list, with the outcome written on the page. */
    private fun installVersionFromPage(version: ModrinthMods.File) {
        val page = state.page ?: return
        val projectId = page.hit.projectId
        if (!state.target.canRunMods) {
            updatePage(projectId) {
                it.copy(note = getString(R.string.mods_browse_no_loader_short), noteIsError = true)
            }
            return
        }
        val folder = state.target.modsFolder
        if (folder == null) {
            updatePage(projectId) {
                it.copy(note = getString(R.string.mods_browse_no_profile), noteIsError = true)
            }
            return
        }
        // Answers rather than returning: an inert control that says nothing is exactly the bug
        // this screen already shipped once.
        if (page.installingVersion != null) {
            updatePage(projectId) {
                it.copy(note = getString(R.string.mods_page_one_at_a_time), noteIsError = false)
            }
            return
        }
        updatePage(projectId) {
            it.copy(installingVersion = version.versionId, note = null, noteIsError = false)
        }
        val mcVersion = state.effectiveVersion
        val loader = state.effectiveLoader
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { ModInstall.install(folder, version, mcVersion, loader) }.getOrNull()
            }
            updatePage(projectId) { current ->
                val note: String
                val isError: Boolean
                when {
                    outcome == null || !outcome.ok() -> {
                        note = getString(R.string.mods_browse_install_failed); isError = true
                    }
                    outcome.complete() -> {
                        val extra = outcome.installed.size - 1
                        note = if (extra > 0) {
                            resources.getQuantityString(
                                R.plurals.mods_browse_with_dependencies, extra, extra)
                        } else {
                            getString(R.string.mods_page_installed, version.versionNumber)
                        }
                        isError = false
                    }
                    else -> {
                        note = resources.getQuantityString(
                            R.plurals.mods_browse_missing_dependencies,
                            outcome.missing.size, outcome.missing.size)
                        isError = false
                    }
                }
                current.copy(installingVersion = null, note = note, noteIsError = isError)
            }
        }
    }

    /**
     * One gallery picture, decoded small enough to be a strip thumbnail.
     *
     * Bounded on both ends: the bytes are capped before decoding (a gallery URL is a stranger's
     * URL), and the decode is downsampled to roughly the strip's own size, because a 4K
     * screenshot behind a 220dp tile is heap spent on nothing.
     */
    private fun loadGalleryImage(url: String) {
        if (!galleryRequested.add(url)) return
        val projectId = state.page?.hit?.projectId ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { fetchScaledImage(url) }.getOrNull()
            }
            if (bitmap != null) {
                updatePage(projectId) { it.copy(gallery = it.gallery + (url to bitmap)) }
            }
        }
    }

    private fun fetchScaledImage(url: String): androidx.compose.ui.graphics.ImageBitmap? {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        return try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            val bytes = connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(65536)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    total += read
                    // A gallery entry bigger than this is not a screenshot; stop rather than
                    // buffer whatever it actually is.
                    if (total > 15 * 1024 * 1024) return null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            val bounds = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 880) sample *= 2
            val options = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?.asImageBitmap()
        } finally {
            connection.disconnect()
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
