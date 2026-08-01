package net.kdt.pojavlaunch.fragments

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceRendererSettingsFragment
import net.kdt.pojavlaunch.recorder.RecordingsActivity
import net.kdt.pojavlaunch.ui.home.currentAccount
import net.kdt.pojavlaunch.ui.home.currentGameDirectory
import net.kdt.pojavlaunch.ui.home.currentProfileLabel
import net.kdt.pojavlaunch.ui.settings.SettingsActions
import net.kdt.pojavlaunch.ui.settings.SettingsEnvironment
import net.kdt.pojavlaunch.ui.settings.SettingsRoute
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
            onDiscord = { Tools.openURL(requireActivity(), getString(R.string.discord_invite)) }
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
