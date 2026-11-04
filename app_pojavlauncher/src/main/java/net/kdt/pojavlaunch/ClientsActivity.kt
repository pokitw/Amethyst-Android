package net.kdt.pojavlaunch

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.clients.ClientInstaller
import net.kdt.pojavlaunch.clients.ClientPlan
import net.kdt.pojavlaunch.clients.LunarApi
import net.kdt.pojavlaunch.sodium.GpuProfile
import net.kdt.pojavlaunch.sodium.SodiumSupport
import net.kdt.pojavlaunch.ui.settings.SettingsStore
import net.kdt.pojavlaunch.ui.clients.ClientModule
import net.kdt.pojavlaunch.ui.clients.ClientProgress
import net.kdt.pojavlaunch.ui.clients.ClientVersion
import net.kdt.pojavlaunch.ui.clients.ClientsScreen
import net.kdt.pojavlaunch.ui.clients.ClientsState
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Installing and starting a third-party client, apart from every vanilla profile.
 *
 * Its own activity and its own folder because a client is a different program: it brings its
 * own loader, its own libraries and its own settings, and nothing about it should be able to
 * change what an ordinary profile does. It meets the rest of the launcher at exactly one point,
 * the game activity, which it starts the same way the vanilla download does.
 */
class ClientsActivity : BaseActivity() {

    /** A screen someone reads (handbook 16.12). */
    override fun setFullscreen(): Boolean = false

    private var state by mutableStateOf(ClientsState())
    private var subversions: Map<String, LunarApi.Subversion> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmethystXTheme {
                ClientsScreen(
                    state = state,
                    onBack = { if (state.progress == null) finish() },
                    onRetry = ::load,
                    onSelectVersion = ::selectVersion,
                    onSelectModule = { m ->
                        state = state.copy(
                            selectedModule = m,
                            installed = isInstalled(state.selectedVersion, m),
                            installError = null
                        )
                    },
                    onPlay = ::play
                )
            }
        }
        load()
    }

    private fun load() {
        state = state.copy(loading = true, loadError = null)
        // The renderer is read here, on the main thread, because it goes through LauncherProfiles
        // (handbook 12.7); the device is read beside the network call, off it.
        val renderer = runCatching { SettingsStore(this).currentRenderer() }.getOrNull()
        lifecycleScope.launch {
            val verified = withContext(Dispatchers.IO) {
                val gpu = runCatching { GpuProfile.read(this@ClientsActivity) }
                    .getOrElse { SodiumSupport.Gpu.unknown() }
                SodiumSupport.verdict(renderer, true, gpu) == SodiumSupport.Verdict.VERIFIED
            }
            val result = withContext(Dispatchers.IO) { runCatching { ClientInstaller.fetchVersions() } }
            result.onSuccess { versions ->
                val usable = mutableListOf<ClientVersion>()
                val unavailable = mutableListOf<String>()
                val subs = LinkedHashMap<String, LunarApi.Subversion>()
                for (v in versions) for (s in v.subversions) {
                    if (ClientPlan.usesLwjgl2(s.id)) {
                        unavailable += s.id
                        continue
                    }
                    if (s.modules.isEmpty()) continue
                    subs[s.id] = s
                    usable += ClientVersion(
                        id = s.id,
                        modules = s.modules.map {
                            ClientModule(it.id, it.name, it.description, ClientPlan.bundlesSodium(it))
                        },
                        defaultModule = ClientPlan.preferredModule(s.modules, verified) ?: s.modules[0].id
                    )
                }
                subversions = subs
                val first = usable.firstOrNull()
                state = state.copy(
                    loading = false,
                    sodiumVerified = verified,
                    versions = usable,
                    unavailable = unavailable,
                    selectedVersion = first?.id,
                    selectedModule = first?.defaultModule,
                    installed = isInstalled(first?.id, first?.defaultModule)
                )
            }.onFailure {
                state = state.copy(
                    loading = false,
                    loadError = it.message ?: getString(R.string.clients_network_error)
                )
            }
        }
    }

    private fun selectVersion(id: String) {
        val v = state.versions.firstOrNull { it.id == id } ?: return
        state = state.copy(
            selectedVersion = id,
            selectedModule = v.defaultModule,
            installed = isInstalled(id, v.defaultModule),
            installError = null
        )
    }

    private fun isInstalled(version: String?, module: String?): Boolean =
        version != null && module != null && ClientInstaller.isInstalled(version, module)

    private fun play() {
        val sub = subversions[state.selectedVersion] ?: return
        val module = sub.modules.firstOrNull { it.id == state.selectedModule } ?: sub.defaultModule() ?: return
        state = state.copy(progress = ClientProgress(getString(R.string.clients_starting), null), installError = null)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ClientInstaller.install(sub, module, Throttle()) }
            }
            result.onSuccess { start() }.onFailure {
                state = state.copy(
                    progress = null,
                    installError = it.message ?: getString(R.string.clients_network_error)
                )
            }
        }
    }

    /**
     * Reports reach the main thread only when the line changes or the bar moves a whole percent.
     * The downloader reports per chunk, from several threads, and a hop per report is a flood
     * that looks like ordinary code at both ends (handbook 16.30).
     */
    private inner class Throttle : ClientInstaller.Progress {
        private var lastStage: String? = null
        private var lastFraction = -2f

        @Synchronized
        override fun onProgress(stage: String, fraction: Float) {
            val f = if (fraction < 0f) -1f else fraction
            if (stage == lastStage && (f < 0f || f - lastFraction < 0.01f) && f != 1f) return
            lastStage = stage
            lastFraction = f
            runOnUiThread {
                if (state.progress != null)
                    state = state.copy(progress = ClientProgress(stage, if (f < 0f) null else f))
            }
        }
    }

    /** The same handoff the vanilla download makes: start the game, then end this process. */
    private fun start() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.INTENT_CLIENT_HOME, ClientInstaller.home().absolutePath)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
