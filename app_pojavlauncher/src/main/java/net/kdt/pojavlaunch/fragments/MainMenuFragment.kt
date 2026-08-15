package net.kdt.pojavlaunch.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.CustomControlsActivity
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.LogActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.SkinActivity
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.prefs.HeapAdvice
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener
import net.kdt.pojavlaunch.recorder.RecordingsActivity
import net.kdt.pojavlaunch.ui.home.Account
import net.kdt.pojavlaunch.ui.home.GameProfile
import net.kdt.pojavlaunch.ui.home.HomeActions
import net.kdt.pojavlaunch.ui.home.HomeScreen
import net.kdt.pojavlaunch.ContentActivity
import net.kdt.pojavlaunch.ui.home.currentAccountName
import net.kdt.pojavlaunch.ui.home.currentProfileKey
import net.kdt.pojavlaunch.ui.home.formatMemory
import net.kdt.pojavlaunch.ui.home.loadAccounts
import net.kdt.pojavlaunch.ui.home.loadProfiles
import net.kdt.pojavlaunch.ui.home.recordingCount
import net.kdt.pojavlaunch.ui.home.rememberLaunchProgress
import net.kdt.pojavlaunch.ui.home.selectProfile
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.ui.common.ChromeOwner

/**
 * The launcher's home screen.
 *
 * Still a fragment because the whole launcher navigates by swapping this container and popping
 * back to "ROOT"; what changed is that its content is Compose rather than a layout of buttons.
 * Everything it triggers goes through the launcher's existing seams — the launch event, the
 * profile editor, the account spinner — so nothing about how the game starts has moved.
 */
class MainMenuFragment : Fragment(), ChromeOwner {
    companion object {
        const val TAG = "MainMenuFragment"

        /** How long the press echo may claim a launch is coming before conceding it was refused. */
        private const val LAUNCH_ECHO_TIMEOUT_MS = 4000L
    }

    private val profiles = mutableStateListOf<GameProfile>()
    private val accounts = mutableStateListOf<Account>()
    private var selectedKey by mutableStateOf<String?>(null)
    private var currentAccount by mutableStateOf<String?>(null)
    private var recordings by mutableIntStateOf(0)

    /**
     * The press, echoed locally so the card can become the launch console in the same frame the
     * finger lifts. The real busy signal is the task count, but the first task is only submitted
     * once the downloader thread has spun up, and a launch that visibly hesitates first feels
     * like a launch that did not take.
     */
    private var launchRequested by mutableStateOf(false)

    /** Built once: recreating it per recomposition would defeat skipping for no benefit. */
    private val actions by lazy { buildActions() }

    /**
     * Signing in and installing a modpack both write files this screen reads, and both hand
     * control back here while they are still running — so the screen is re-read when the last
     * one finishes rather than only when it is returned to.
     */
    private val tasksFinished = TaskCountListener { count ->
        if (count == 0) Tools.runOnUiThread {
            // Whatever was running is over, so the launch echo has nothing left to stand in for.
            launchRequested = false
            if (isResumed) refresh()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AmethystXTheme {
                HomeScreen(
                    profiles = profiles,
                    selectedKey = selectedKey,
                    accounts = accounts,
                    currentAccount = currentAccount,
                    recordingCount = recordings,
                    progress = rememberLaunchProgress(launchRequested),
                    actions = actions
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // A stale echo cannot survive a return here: with no tasks running there is nothing for
        // the console to be reporting on.
        if (ProgressKeeper.getTaskCount() == 0) launchRequested = false
        // False: the refresh above has already covered the current state.
        ProgressKeeper.addTaskCountListener(tasksFinished, false)
    }

    override fun onPause() {
        super.onPause()
        ProgressKeeper.removeTaskCountListener(tasksFinished)
    }

    /**
     * Re-read everything the screen shows.
     *
     * Profiles and accounts can be changed by any of the screens this one leads to, and the
     * profile editor reports what it saved through [ExtraConstants.REFRESH_VERSION_SPINNER], so
     * this runs on every return rather than only on creation.
     *
     * On the main thread, deliberately. The launcher profiles and the profile icon cache are
     * shared mutable state that the launch path and the editor also touch, so reading them from a
     * background thread would be racing them for no real gain: this is the same small amount of
     * work the profile spinner already did here on every resume.
     */
    private fun refresh() {
        (ExtraCore.consumeValue(ExtraConstants.REFRESH_VERSION_SPINNER) as? String)
            ?.takeIf { it != ProfileEditorFragment.DELETED_PROFILE }
            ?.let { selectProfile(it) }

        val context = requireContext()
        val loaded = loadProfiles(context)
        profiles.clear()
        profiles.addAll(loaded)
        accounts.clear()
        accounts.addAll(loadAccounts())
        recordings = recordingCount()
        currentAccount = currentAccountName(context)

        // A profile can be deleted from under the stored selection, and the launch path reads
        // that stored value rather than what is on screen, so the two are reconciled here.
        val stored = currentProfileKey()
        val resolved = loaded.firstOrNull { it.key == stored } ?: loaded.firstOrNull()
        if (resolved != null && resolved.key != stored) selectProfile(resolved.key)
        selectedKey = resolved?.key
    }

    private fun buildActions() = HomeActions(
        onPlay = ::play,
        onSelectProfile = { profile ->
            selectProfile(profile.key)
            selectedKey = profile.key
        },
        onEditProfile = { profile ->
            // The editor reads the stored selection rather than taking an argument.
            selectProfile(profile.key)
            selectedKey = profile.key
            Tools.swapFragment(
                requireActivity(), ProfileEditorFragment::class.java,
                ProfileEditorFragment.TAG, null
            )
        },
        onCreateProfile = {
            Tools.swapFragment(
                requireActivity(), ProfileTypeSelectFragment::class.java,
                ProfileTypeSelectFragment.TAG, null
            )
        },
        onSelectAccount = { account ->
            launcherActivity()?.selectAccount(account.username)
            currentAccount = account.username
        },
        onDeleteAccount = { account ->
            launcherActivity()?.removeAccount(account.username)
            refresh()
        },
        onAddAccount = { ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true) },
        onSettings = {
            Tools.swapFragment(
                requireActivity(), SettingsFragment::class.java,
                LauncherActivity.SETTING_FRAGMENT_TAG, null, true
            )
        },
        onControls = { startActivity(Intent(requireContext(), CustomControlsActivity::class.java)) },
        onRecordings = { startActivity(Intent(requireContext(), RecordingsActivity::class.java)) },
        onSkins = { startActivity(Intent(requireContext(), SkinActivity::class.java)) },
        onInstall = ::openLoaderInstaller,
        onInstallWithArguments = { runInstaller(true) },
        onFiles = ::openGameFiles,
        onWiki = { Tools.openURL(requireActivity(), Tools.URL_HOME) },
        onGamepadMapper = {
            Tools.swapFragment(
                requireActivity(), GamepadMapperFragment::class.java,
                GamepadMapperFragment.TAG, null
            )
        },
        onDiscord = { Tools.openURL(requireActivity(), getString(R.string.discord_invite)) },
        onViewLog = { startActivity(Intent(requireContext(), LogActivity::class.java)) },
        onShareLogs = { Tools.shareLog(requireContext()) }
    )

    /**
     * The memory question, asked here rather than after the download.
     *
     * The launcher has always checked whether the heap will fit, at the top of
     * `Tools.launchMinecraft`. On a desktop that is a small annoyance; on a phone it lands after
     * several hundred megabytes have been fetched, frequently over mobile data, in the game
     * process, which is started by killing this one, so the settings it advises changing are no
     * longer reachable. And it is an OK-only dialog that launches anyway, so being told changes
     * nothing at all.
     *
     * Asked in front of the button instead, the same fact costs nothing to act on and can carry
     * the fix with it: [HeapAdvice] knows the largest value that would clear the warning, so the
     * dialog offers it as a button rather than a number to go and type in somewhere else.
     *
     * Nothing is checked when the device will not report its memory. A reading of zero from
     * `ActivityManager` is a failure rather than a device with no memory, and a warning built on
     * it would fire on every launch and mean nothing.
     */
    private fun checkMemory(onContinue: () -> Unit) {
        val context = requireContext()
        val total = runCatching { Tools.getTotalDeviceMemory(context) }.getOrDefault(0)
        if (total <= 0) {
            onContinue()
            return
        }
        val available = runCatching { Tools.getFreeDeviceMemory(context) }.getOrDefault(0)
        val advice = HeapAdvice.advise(
            total, available, LauncherPreferences.PREF_RAM_ALLOCATION, Architecture.is32BitsDevice()
        )
        if (advice.level == HeapAdvice.Level.OK) {
            onContinue()
            return
        }

        val allocation = formatMemory(LauncherPreferences.PREF_RAM_ALLOCATION)
        val builder = AlertDialog.Builder(context)
        if (advice.level == HeapAdvice.Level.OVER_CEILING) {
            builder.setTitle(R.string.launch_memory_over_title)
                .setMessage(getString(
                    R.string.launch_memory_over_message, allocation, formatMemory(advice.ceilingMb)
                ))
        } else {
            builder.setTitle(R.string.launch_memory_low_title)
                .setMessage(getString(
                    R.string.launch_memory_low_message, allocation, formatMemory(available)
                ))
        }
        // The reduction goes first because it is the one that fixes anything. Launching anyway
        // stays available on every path: the availability arm is about this moment rather than
        // this device, and somebody who knows they are about to close another app is right.
        if (advice.suggestedMb > 0) {
            builder.setPositiveButton(
                getString(R.string.launch_memory_lower, formatMemory(advice.suggestedMb))
            ) { _, _ ->
                applyAllocation(advice.suggestedMb)
                onContinue()
            }
        }
        builder.setNegativeButton(R.string.launch_memory_continue) { _, _ -> onContinue() }
        builder.show()
    }

    /**
     * Write the heap size and make it true for the launch it was just chosen for.
     *
     * `PREF_RAM_ALLOCATION` is a static cached at `loadPreferences` time, so writing the
     * preference alone would launch with the old value and leave the card showing it too. This is
     * the shape `SettingsStore.write` already uses, for the same reason.
     */
    private fun applyAllocation(megabytes: Int) {
        LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", megabytes).apply()
        LauncherPreferences.loadPreferences(requireContext())
        refresh()
    }

    /** Unchanged from the button this replaces, including the Sodium warning. */
    private fun play() = checkMemory {
        val overridden = LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false)
        if (Tools.hasMods("sodium") && !overridden) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.sodium_warning_title)
                .setMessage(R.string.sodium_warning_message)
                .setNeutralButton(R.string.delete_sodium) { _, _ ->
                    Tools.deleteSodiumMods()
                    requestLaunch()
                }
                .show()
        } else {
            requestLaunch()
        }
    }

    /**
     * Raise the launch event, with the console already on screen when it lands.
     *
     * The echo cannot wait on a task to clear it, because a refused launch (no account signed in,
     * no version selected) never starts one; after a moment with nothing running it gives up
     * quietly and the card offers Play again. On the main looper rather than a view, so a view
     * recreated in between cannot strand the pending check.
     */
    private fun requestLaunch() {
        launchRequested = true
        Handler(Looper.getMainLooper()).postDelayed({
            if (ProgressKeeper.getTaskCount() == 0) launchRequested = false
        }, LAUNCH_ECHO_TIMEOUT_MS)
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true)
    }

    /**
     * The Install tile, which used to open a file picker and ask for an installer jar.
     *
     * That is the wrong first question. Somebody tapping a tile labelled "Forge, Fabric" has not
     * got a jar; they want one, and being asked to supply what they came to fetch is the whole
     * complaint. It opens the loader screen now, which finds the build and downloads it, and
     * offers running a jar from there for the cases the index cannot cover.
     *
     * The online-profile guard and the ongoing-task guard stay exactly where they were: those
     * installers download from services that will not serve an offline account, and the launch
     * path refuses while anything else is downloading.
     */
    private fun openLoaderInstaller() {
        if (!Tools.hasOnlineProfile()) {
            Tools.hasNoOnlineProfileDialog(requireActivity())
            return
        }
        if (ProgressKeeper.getTaskCount() != 0) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show()
            return
        }
        Tools.swapFragment(
            requireActivity(), LoaderInstallFragment::class.java, LoaderInstallFragment.TAG, null
        )
    }

    private fun runInstaller(customJavaArgs: Boolean) {
        if (!Tools.hasOnlineProfile()) {
            Tools.hasNoOnlineProfileDialog(requireActivity())
            return
        }
        if (ProgressKeeper.getTaskCount() != 0) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show()
            return
        }
        Tools.installMod(requireActivity(), customJavaArgs)
    }

    /**
     * The tile no longer hands the player off to a file manager and a path under `Android/data`.
     *
     * It opens the screen that lists what the profile actually owns — worlds, mods, packs,
     * shaders and screenshots — which is what anyone tapping "Game files" was looking for. The
     * folder itself is still one tap further in, for the times when only a file manager will do.
     */
    private fun openGameFiles() {
        startActivity(Intent(requireContext(), ContentActivity::class.java))
    }

    private fun launcherActivity(): LauncherActivity? = activity as? LauncherActivity

    /** The home screen draws its own header and reports downloads inside its Play button. */
    override fun drawsOwnHeader(): Boolean = true

    override fun drawsOwnProgress(): Boolean = true
}
