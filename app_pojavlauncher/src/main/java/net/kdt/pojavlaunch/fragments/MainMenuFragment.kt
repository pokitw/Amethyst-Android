package net.kdt.pojavlaunch.fragments

import android.content.Intent
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.CustomControlsActivity
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener
import net.kdt.pojavlaunch.recorder.RecordingsActivity
import net.kdt.pojavlaunch.ui.home.Account
import net.kdt.pojavlaunch.ui.home.GameProfile
import net.kdt.pojavlaunch.ui.home.HomeActions
import net.kdt.pojavlaunch.ui.home.HomeScreen
import net.kdt.pojavlaunch.ui.home.currentAccountName
import net.kdt.pojavlaunch.ui.home.currentGameDirectory
import net.kdt.pojavlaunch.ui.home.currentProfileKey
import net.kdt.pojavlaunch.ui.home.loadAccounts
import net.kdt.pojavlaunch.ui.home.loadProfiles
import net.kdt.pojavlaunch.ui.home.recordingCount
import net.kdt.pojavlaunch.ui.home.rememberLaunchProgress
import net.kdt.pojavlaunch.ui.home.selectProfile
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * The launcher's home screen.
 *
 * Still a fragment because the whole launcher navigates by swapping this container and popping
 * back to "ROOT"; what changed is that its content is Compose rather than a layout of buttons.
 * Everything it triggers goes through the launcher's existing seams — the launch event, the
 * profile editor, the account spinner — so nothing about how the game starts has moved.
 */
class MainMenuFragment : Fragment() {
    companion object {
        const val TAG = "MainMenuFragment"
    }

    private val profiles = mutableStateListOf<GameProfile>()
    private val accounts = mutableStateListOf<Account>()
    private var selectedKey by mutableStateOf<String?>(null)
    private var currentAccount by mutableStateOf<String?>(null)
    private var recordings by mutableIntStateOf(0)

    /** Built once: recreating it per recomposition would defeat skipping for no benefit. */
    private val actions by lazy { buildActions() }

    /**
     * Signing in and installing a modpack both write files this screen reads, and both hand
     * control back here while they are still running — so the screen is re-read when the last
     * one finishes rather than only when it is returned to.
     */
    private val tasksFinished = TaskCountListener { count ->
        if (count == 0) Tools.runOnUiThread { if (isResumed) refresh() }
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
                    progress = rememberLaunchProgress(),
                    actions = actions
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
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
     */
    private fun refresh() {
        (ExtraCore.consumeValue(ExtraConstants.REFRESH_VERSION_SPINNER) as? String)
            ?.takeIf { it != ProfileEditorFragment.DELETED_PROFILE }
            ?.let { selectProfile(it) }

        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val loadedProfiles = withContext(Dispatchers.IO) { loadProfiles(context) }
            val loadedAccounts = withContext(Dispatchers.IO) { loadAccounts() }
            val clips = withContext(Dispatchers.IO) { recordingCount() }

            profiles.clear()
            profiles.addAll(loadedProfiles)
            accounts.clear()
            accounts.addAll(loadedAccounts)
            recordings = clips
            currentAccount = currentAccountName(context)

            // A profile can be deleted from under the stored selection, and the launch path reads
            // that stored value rather than what is on screen, so the two are reconciled here.
            val stored = currentProfileKey()
            val resolved = loadedProfiles.firstOrNull { it.key == stored }
                ?: loadedProfiles.firstOrNull()
            if (resolved != null && resolved.key != stored) selectProfile(resolved.key)
            selectedKey = resolved?.key
        }
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
                requireActivity(), LauncherPreferenceFragment::class.java,
                LauncherActivity.SETTING_FRAGMENT_TAG, null
            )
        },
        onControls = { startActivity(Intent(requireContext(), CustomControlsActivity::class.java)) },
        onRecordings = { startActivity(Intent(requireContext(), RecordingsActivity::class.java)) },
        onInstall = { runInstaller(false) },
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
        onShareLogs = { Tools.shareLog(requireContext()) }
    )

    /** Unchanged from the button this replaces, including the Sodium warning. */
    private fun play() {
        val overridden = LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false)
        if (Tools.hasMods("sodium") && !overridden) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.sodium_warning_title)
                .setMessage(R.string.sodium_warning_message)
                .setNeutralButton(R.string.delete_sodium) { _, _ ->
                    Tools.deleteSodiumMods()
                    ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true)
                }
                .show()
        } else {
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true)
        }
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

    private fun launcherActivity(): LauncherActivity? = activity as? LauncherActivity
}
