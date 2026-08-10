package net.kdt.pojavlaunch.fragments

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import fr.spse.gamepad_remapper.Remapper
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.CustomControlsActivity
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.OnboardingActivity
import net.kdt.pojavlaunch.ContentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import net.kdt.pojavlaunch.customcontrols.keyboard.VoiceInput
import net.kdt.pojavlaunch.customcontrols.textures.ControlTextures
import net.kdt.pojavlaunch.customcontrols.textures.TexturePackImport
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceRendererSettingsFragment
import net.kdt.pojavlaunch.recorder.RecordingsActivity
import net.kdt.pojavlaunch.ui.home.currentAccount
import net.kdt.pojavlaunch.ui.home.currentGameDirectory
import net.kdt.pojavlaunch.ui.home.currentProfileLabel
import net.kdt.pojavlaunch.ui.content.ContentKind
import net.kdt.pojavlaunch.ui.content.contentFolder
import net.kdt.pojavlaunch.ui.settings.SettingsActions
import net.kdt.pojavlaunch.ui.settings.SettingsEnvironment
import net.kdt.pojavlaunch.ui.settings.SettingsRoute
import net.kdt.pojavlaunch.ui.settings.SettingsStore
import net.kdt.pojavlaunch.ui.settings.SettingsScreen
import net.kdt.pojavlaunch.ui.settings.rememberSettingsStore
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Settings.
 *
 * Replaces the root of an eight-screen `PreferenceScreen` tree. The leaves that are genuinely
 * their own thing — the runtime manager, the gamepad remapper, the MobileGlues tuning — are still
 * the screens they always were; this owns the grouping and every plain preference.
 */
class SettingsFragment : Fragment() {
    companion object {
        const val TAG = "SettingsFragment"
    }

    private var route by mutableStateOf(SettingsRoute.HOME)
    private var runtimeDialog: MultiRTConfigDialog? = null

    /**
     * Read on resume rather than during composition: it stats the filesystem and asks the package
     * manager, neither of which should happen again every time a slider moves.
     */
    private var environment by mutableStateOf(SettingsEnvironment())

    private val runtimeInstallLauncher =
        registerForActivityResult(OpenDocumentWithExtension("xz")) { uri ->
            if (uri != null) Tools.installRuntimeFromUri(context, uri)
        }

    /**
     * Anything, rather than a zip filter.
     *
     * Providers disagree about what MIME type a zip is — some hand them out as octet-stream —
     * and Game files already settled this: the picker takes what it is given and what is inside
     * decides what it was.
     */
    private val texturePackLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importTexturePack(uri)
        }

    /**
     * Back returns to the settings home before it leaves settings, so a detail screen is not a
     * one-way trip out of the section.
     */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            route = SettingsRoute.HOME
            isEnabled = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AmethystXTheme {
                    SettingsScreen(
                        route = route,
                        onRoute = { next ->
                            route = next
                            backCallback.isEnabled = next != SettingsRoute.HOME
                        },
                        store = rememberSettingsStore(),
                        environment = environment,
                        actions = actions
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions and free space can both change while another screen was in front.
        environment = readEnvironment()
    }

    /** The device facts the screens show, none of which are snapshot state on their own. */
    private fun readEnvironment(): SettingsEnvironment {
        val context = requireContext()
        val deviceMemory = Tools.getTotalDeviceMemory(context)
        // The same headroom the memory slider always kept, so the device can still breathe.
        val maxMemory = if (Architecture.is32BitsDevice() || deviceMemory < 2048) {
            minOf(1024, deviceMemory)
        } else {
            deviceMemory - if (deviceMemory < 3064) 800 else 1024
        }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        val free = runCatching {
            Formatter.formatShortFileSize(context, currentGameDirectory().usableSpace)
        }.getOrNull().orEmpty()
        // Counted by listing rather than by opening anything: this runs on the main thread in
        // onResume, and the row only needs a number to show that there is something behind it.
        val contentCount = runCatching {
            val dir = currentGameDirectory()
            ContentKind.values().sumOf { kind ->
                contentFolder(dir, kind).listFiles()?.size ?: 0
            }
        }.getOrDefault(0)
        val launcher = activity as? LauncherActivity
        // The header says who is signed in and what they are about to play, which is what the
        // launcher's old account bar used to occupy the top of this screen to say half of.
        val account = runCatching { currentAccount(context) }.getOrNull()
        val profile = runCatching { currentProfileLabel(context) }.getOrNull()
        return SettingsEnvironment(
            versionName = version,
            freeSpace = free,
            deviceMemoryMb = deviceMemory,
            maxMemoryMb = maxMemory,
            gyroAvailable = Tools.deviceSupportsGyro(context),
            voiceAvailable = VoiceInput.isAvailable(context),
            texturePacks = ControlTextures.available(),
            modCount = contentCount,
            notificationPermission = launcher?.checkForNotificationPermission() ?: true,
            microphonePermission = launcher?.checkForMicrophonePermission() ?: true,
            accountName = account?.username,
            accountFace = account?.face,
            accountKindRes = when {
                account == null -> R.string.settings_account_none
                account.isDemo -> R.string.settings_account_demo
                account.isLocal -> R.string.settings_account_local
                else -> R.string.settings_account_microsoft
            },
            profileTitle = profile?.first,
            profileDetail = profile?.second?.let { " · $it" }.orEmpty()
        )
    }

    private val actions by lazy {
        SettingsActions(
            onBack = { Tools.backToMainMenu(requireActivity()) },
            onCustomControls = {
                startActivity(Intent(requireContext(), CustomControlsActivity::class.java))
            },
            onGamepadBindings = {
                Tools.swapFragment(
                    requireActivity(), GamepadMapperFragment::class.java,
                    GamepadMapperFragment.TAG, null
                )
            },
            onResetGamepad = {
                Remapper.wipePreferences(requireContext())
                Toast.makeText(
                    requireContext(), R.string.preference_controller_map_wiped, Toast.LENGTH_SHORT
                ).show()
            },
            onRuntimeManager = {
                val dialog = runtimeDialog ?: MultiRTConfigDialog().also {
                    it.prepare(requireContext(), runtimeInstallLauncher)
                    runtimeDialog = it
                }
                dialog.show()
            },
            onRendererTuning = {
                Tools.swapFragment(
                    requireActivity(), LauncherPreferenceRendererSettingsFragment::class.java,
                    "RENDERER_SETTINGS", null
                )
            },
            onMods = {
                startActivity(Intent(requireContext(), ContentActivity::class.java))
            },
            onRecordings = {
                startActivity(Intent(requireContext(), RecordingsActivity::class.java))
            },
            onGameFiles = ::openGameFiles,
            onNotificationPermission = {
                (activity as? LauncherActivity)?.askForNotificationPermission(null)
            },
            onMicrophonePermission = {
                (activity as? LauncherActivity)?.askForMicrophonePermission(null)
            },
            onShareLog = { Tools.shareLog(requireContext()) },
            onWiki = { Tools.openURL(requireActivity(), Tools.URL_HOME) },
            onDiscord = { Tools.openURL(requireActivity(), getString(R.string.discord_invite)) },
            onReplayWelcome = ::replayWelcome,
            onImportTexturePack = ::pickTexturePack
        )
    }

    /**
     * Show the welcome again.
     *
     * The done flag is deliberately left set. Clearing it would put someone back through the flow
     * on their *next* cold start as well, which is a setting nobody asked for; and
     * [OnboardingActivity] writes the flag itself on the way out, so it is already true when this
     * one ends. The activity is started plainly rather than through `TestStorageActivity`, since
     * storage has clearly been sorted out by the time anyone is reading Settings.
     */
    private fun pickTexturePack() {
        runCatching { texturePackLauncher.launch(arrayOf("*/*")) }
            .onFailure { toast(getString(R.string.preference_control_texture_failed)) }
    }

    /**
     * Unpack a chosen archive, then make it the style straight away.
     *
     * Selecting it for the player is the whole reason this is one action rather than two: nobody
     * imports a pack in order to leave it turned off, and a picker that silently gained a row is
     * the kind of thing you have to go back and check.
     */
    private fun importTexturePack(uri: android.net.Uri) {
        val importer = TexturePackImport()
        when (importer.importFrom(requireContext(), uri)) {
            TexturePackImport.Result.ADDED -> {
                val name = importer.name
                if (name != null) {
                    // Straight to the same store the screen writes through, so the reload of the
                    // PREF_ statics happens exactly as it would have from a tap on the row.
                    SettingsStore(requireContext()).put("controlStyle", name)
                    // The launcher process has the old artwork in memory; the game gets a fresh
                    // registry anyway, but the editor lives here and would keep drawing the last
                    // pack until the process died.
                    ControlTextures.invalidate()
                    environment = readEnvironment()
                    toast(getString(R.string.preference_control_texture_added, name))
                }
            }
            TexturePackImport.Result.NOT_A_PACK ->
                toast(getString(R.string.preference_control_texture_not_a_pack))
            TexturePackImport.Result.FAILED ->
                toast(getString(R.string.preference_control_texture_failed))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun replayWelcome() {
        startActivity(
            Intent(requireContext(), OnboardingActivity::class.java)
                .putExtra(OnboardingActivity.EXTRA_REPLAY, true)
        )
    }

    private fun openGameFiles() {
        val context = requireContext()
        when {
            // A different message on demo, since otherwise they would find the hidden demo folder.
            Tools.isDemoProfile(context) -> Tools.hasNoOnlineProfileDialog(
                requireActivity(),
                getString(R.string.demo_unsupported),
                getString(R.string.change_account)
            )
            !Tools.hasOnlineProfile() -> Tools.hasNoOnlineProfileDialog(requireActivity())
            else -> Tools.openPath(context, currentGameDirectory(), false)
        }
    }
}
