package net.kdt.pojavlaunch.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.JavaGUILauncherActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.FabriclikeDownloadTask
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils
import net.kdt.pojavlaunch.modloaders.ForgeDownloadTask
import net.kdt.pojavlaunch.modloaders.ForgeUtils
import net.kdt.pojavlaunch.modloaders.LoaderIndex
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy
import net.kdt.pojavlaunch.modloaders.NeoForgeDownloadTask
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.ui.loaders.LoaderBuild
import net.kdt.pojavlaunch.ui.loaders.LoaderInstallScreen
import net.kdt.pojavlaunch.ui.loaders.LoaderInstallState
import net.kdt.pojavlaunch.ui.loaders.LoaderOption
import net.kdt.pojavlaunch.ui.loaders.LoaderRow
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles
import java.io.File
import java.util.Locale

/**
 * One screen for every mod loader, asked from the Minecraft version rather than the loader.
 *
 * <b>The install machinery is untouched.</b> This drives the same `FabriclikeDownloadTask`,
 * `ForgeDownloadTask` and `NeoForgeDownloadTask` the four old fragments drive, with the same
 * listener proxy and the same completion, including handing the Forge and NeoForge installers to
 * `JavaGUILauncherActivity` to actually run. What changed is how the two versions get chosen. The
 * old fragments are still wired up and still work, which is deliberate: nothing here can be tested
 * on a device from the build container, and a new way in should not be the only way in.
 */
class LoaderInstallFragment : Fragment(), ModloaderDownloadListener {
    companion object {
        const val TAG = "LoaderInstallFragment"
        private const val EXTRA_PROXY = "LoaderInstallFragment_proxy"
    }

    private var state by mutableStateOf(LoaderInstallState())
    private var index: LoaderIndex.Index? = null
    /** Which loader the running install is for, since the completion differs per loader. */
    private var installing: LoaderIndex.Loader? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AmethystXTheme {
                LoaderInstallScreen(
                    state = state,
                    onQuery = { state = rebuild(query = it) },
                    onSnapshots = { state = rebuild(snapshots = it) },
                    onChoose = { row, option -> state = copyOf(chosen = row to option) },
                    onDismiss = { state = copyOf(chosen = null) },
                    onInstall = ::install,
                    onRetry = ::load,
                    // The same call the Install tile used to make on its own. Nothing about the
                    // jar path changed; it stopped being the only thing the tile could do.
                    onRunJar = { Tools.installMod(requireActivity(), false) },
                    onBack = { Tools.removeCurrentFragment(requireActivity()) }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (index == null) load()
    }

    override fun onStop() {
        proxy()?.detachListener()
        super.onStop()
    }

    /* ------------------------------------------------------------------ the index */

    private fun load() {
        state = copyOf(loading = true, error = null)
        lifecycleScope.launch {
            val fetched = withContext(Dispatchers.IO) { runCatching { LoaderIndex.fetch() } }
            if (!isAdded) return@launch
            val result = fetched.getOrNull()
            if (result == null || result.isEmpty) {
                state = copyOf(
                    loading = false,
                    error = getString(R.string.loader_install_failed_body)
                )
                return@launch
            }
            index = result
            // Which loaders already have a profile is read on the main thread: LauncherProfiles is
            // unsynchronised state shared with the launch path (handbook 12.7).
            state = rebuild(loading = false)
        }
    }

    /**
     * Rebuild the rows from the index and the filters.
     *
     * Plain filtering over an already-fetched list, so typing costs nothing. That is most of the
     * point: the flow this replaces went back to the network every time the version changed.
     */
    private fun rebuild(
        query: String = state.query,
        snapshots: Boolean = state.showSnapshots,
        loading: Boolean = state.loading
    ): LoaderInstallState {
        val source = index ?: return copyOf(loading = loading)
        val needle = query.trim().lowercase(Locale.getDefault())
        val existing = installedProfiles()
        val rows = source.versions.asSequence()
            .filter { snapshots || it.release }
            .filter { needle.isEmpty() || it.id.lowercase(Locale.getDefault()).contains(needle) }
            .map { version ->
                LoaderRow(
                    gameVersion = version.id,
                    release = version.release,
                    loaders = LoaderIndex.Loader.values().mapNotNull { loader ->
                        val best = version.best(loader) ?: return@mapNotNull null
                        val builds = version.builds[loader].orEmpty()
                        LoaderOption(
                            loader = loader.name,
                            name = displayName(loader),
                            recommended = LoaderBuild(best.loaderVersion, best.label, best.stable),
                            builds = builds.map {
                                LoaderBuild(it.loaderVersion, it.label, it.stable)
                            },
                            installed = existing.contains(key(loader, version.id))
                        )
                    }
                )
            }
            .filter { it.loaders.isNotEmpty() }
            .take(400)
            .toList()
        return LoaderInstallState(
            loading = loading,
            rows = rows,
            query = query,
            showSnapshots = snapshots,
            unavailable = source.unavailable.map { displayName(it) },
            error = null,
            chosen = state.chosen,
            installing = state.installing
        )
    }

    private fun copyOf(
        loading: Boolean = state.loading,
        error: String? = state.error,
        chosen: Pair<LoaderRow, LoaderOption>? = state.chosen,
        installing: Boolean = state.installing
    ) = LoaderInstallState(
        loading = loading,
        rows = state.rows,
        query = state.query,
        showSnapshots = state.showSnapshots,
        unavailable = state.unavailable,
        error = error,
        chosen = chosen,
        installing = installing
    )

    /**
     * Which loader and version combinations already have a profile.
     *
     * A guess in the same spirit as the mod browser's: the version id of a modded profile carries
     * both facts, so it is matched rather than a record being kept. Being wrong only ever means a
     * pill does not say "installed", which costs a duplicate profile rather than a crash.
     */
    private fun installedProfiles(): Set<String> = runCatching {
        LauncherProfiles.load()
        val out = mutableSetOf<String>()
        LauncherProfiles.mainProfileJson?.profiles?.values?.forEach { profile ->
            val id = profile.lastVersionId?.lowercase(Locale.ROOT) ?: return@forEach
            for (loader in LoaderIndex.Loader.values()) {
                if (!id.contains(loader.name.lowercase(Locale.ROOT))) continue
                // NeoForge ids also contain "forge", so the more specific name has to win, which
                // is the same ordering trap ModTarget documents.
                if (loader == LoaderIndex.Loader.FORGE && id.contains("neoforge")) continue
                index?.versions?.forEach { version ->
                    if (id.contains(version.id)) out.add(key(loader, version.id))
                }
            }
        }
        out
    }.getOrDefault(emptySet())

    private fun key(loader: LoaderIndex.Loader, gameVersion: String) = "${loader.name}:$gameVersion"

    private fun displayName(loader: LoaderIndex.Loader) = when (loader) {
        LoaderIndex.Loader.FABRIC -> "Fabric"
        LoaderIndex.Loader.QUILT -> "Quilt"
        LoaderIndex.Loader.FORGE -> "Forge"
        LoaderIndex.Loader.NEOFORGE -> "NeoForge"
    }

    /* ------------------------------------------------------------------ installing */

    private fun install(row: LoaderRow, option: LoaderOption, build: LoaderBuild) {
        if (ProgressKeeper.hasOngoingTasks()) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show()
            return
        }
        val loader = runCatching { LoaderIndex.Loader.valueOf(option.loader) }.getOrNull() ?: return
        val proxy = ModloaderListenerProxy()
        val task: Runnable = when (loader) {
            // The Fabric-like task takes the game version and the loader version separately and
            // writes the profile itself. The Forge ones take one maven id and produce an installer
            // jar that still has to be run, which is what onDownloadFinished does below.
            LoaderIndex.Loader.FABRIC -> FabriclikeDownloadTask(
                proxy, FabriclikeUtils.FABRIC_UTILS, row.gameVersion, build.id, true)
            LoaderIndex.Loader.QUILT -> FabriclikeDownloadTask(
                proxy, FabriclikeUtils.QUILT_UTILS, row.gameVersion, build.id, true)
            LoaderIndex.Loader.FORGE -> ForgeDownloadTask(proxy, build.id)
            LoaderIndex.Loader.NEOFORGE -> NeoForgeDownloadTask(proxy, build.id)
        }
        installing = loader
        proxy.attachListener(this)
        setProxy(proxy)
        state = copyOf(installing = true)
        Thread(task).start()
    }

    override fun onDownloadFinished(downloadedFile: File?) {
        val loader = installing
        Tools.runOnUiThread {
            proxy()?.detachListener()
            setProxy(null)
            state = copyOf(installing = false, chosen = null)
            installing = null
            val context = context ?: return@runOnUiThread
            when (loader) {
                // Forge and NeoForge ship an installer jar rather than a profile, so the jar is
                // handed to the Java GUI launcher exactly as the old fragments hand it over. The
                // arguments differ between the two and are copied from each one's own fragment.
                LoaderIndex.Loader.FORGE -> {
                    if (downloadedFile == null) return@runOnUiThread
                    val intent = Intent(context, JavaGUILauncherActivity::class.java)
                    ForgeUtils.addAutoInstallArgs(intent, downloadedFile, true)
                    context.startActivity(intent)
                }
                LoaderIndex.Loader.NEOFORGE -> {
                    if (downloadedFile == null) return@runOnUiThread
                    val intent = Intent(context, JavaGUILauncherActivity::class.java)
                        .putExtra("javaArgs",
                            "-jar " + downloadedFile.absolutePath + " --install-client")
                        .putExtra("openLogOutput", true)
                    context.startActivity(intent)
                }
                // Fabric and Quilt are finished: the task wrote the profile, so the only thing
                // left is to get out of the way, the way their fragment does.
                else -> runCatching {
                    parentFragmentManager.popBackStackImmediate()
                }
            }
        }
    }

    override fun onDataNotAvailable() {
        Tools.runOnUiThread {
            proxy()?.detachListener()
            setProxy(null)
            installing = null
            state = copyOf(installing = false)
            context?.let {
                Toast.makeText(it, R.string.loader_install_no_build, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDownloadError(e: Exception?) {
        Tools.runOnUiThread {
            proxy()?.detachListener()
            setProxy(null)
            installing = null
            state = copyOf(installing = false)
            val context = context ?: return@runOnUiThread
            Tools.showError(context, e)
        }
    }

    private fun proxy(): ModloaderListenerProxy? =
        net.kdt.pojavlaunch.extra.ExtraCore.getValue(EXTRA_PROXY) as? ModloaderListenerProxy

    private fun setProxy(proxy: ModloaderListenerProxy?) {
        net.kdt.pojavlaunch.extra.ExtraCore.setValue(EXTRA_PROXY, proxy)
    }

}
