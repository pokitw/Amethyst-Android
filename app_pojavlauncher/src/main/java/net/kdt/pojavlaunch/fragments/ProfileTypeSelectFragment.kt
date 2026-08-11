package net.kdt.pojavlaunch.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.ui.profile.ProfileType
import net.kdt.pojavlaunch.ui.profile.ProfileTypeScreen
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.ui.common.ChromeOwner

/**
 * What kind of profile to create.
 *
 * Which installer each choice opens is unchanged — this only replaces nine identical buttons with
 * tiles that say what the thing is. The online-account guard stays on every modded route for the
 * same reason it was there before: those installers download from services that will not serve an
 * offline account.
 */
class ProfileTypeSelectFragment : Fragment(), ChromeOwner {
    companion object {
        const val TAG = "ProfileTypeSelectFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AmethystXTheme {
                ProfileTypeScreen(
                    onPick = ::open,
                    onBack = { Tools.removeCurrentFragment(requireActivity()) }
                )
            }
        }
    }

    private fun open(type: ProfileType) {
        // Vanilla is the only one that goes straight to the editor: there is nothing to download
        // first, so the bundle marks it as a profile being created rather than edited.
        if (type == ProfileType.VANILLA) {
            Tools.swapFragment(
                requireActivity(), ProfileEditorFragment::class.java,
                ProfileEditorFragment.TAG, Bundle(1)
            )
            return
        }

        // NOTE: these are deliberately not added to the back stack by their own installers; see
        // the comment in FabricInstallFragment.onDownloadFinished() before changing that.
        val target: Pair<Class<out Fragment>, String> = when (type) {
            ProfileType.LOADERS ->
                LoaderInstallFragment::class.java to LoaderInstallFragment.TAG
            ProfileType.FABRIC -> FabricInstallFragment::class.java to FabricInstallFragment.TAG
            ProfileType.QUILT -> QuiltInstallFragment::class.java to QuiltInstallFragment.TAG
            ProfileType.FORGE -> ForgeInstallFragment::class.java to ForgeInstallFragment.TAG
            ProfileType.NEOFORGE ->
                NeoForgeInstallFragment::class.java to NeoForgeInstallFragment.TAG
            ProfileType.OPTIFINE ->
                OptiFineInstallFragment::class.java to OptiFineInstallFragment.TAG
            ProfileType.LWJGL3IFY ->
                LWJGL3ifyInstallFragment::class.java to LWJGL3ifyInstallFragment.TAG
            ProfileType.BTA -> BTAInstallFragment::class.java to BTAInstallFragment.TAG
            ProfileType.MODPACK ->
                ModpackCreateFragment::class.java to ModpackCreateFragment.TAG
            ProfileType.VANILLA -> return
        }

        if (!Tools.hasOnlineProfile()) {
            Tools.hasNoOnlineProfileDialog(requireActivity())
            return
        }
        Tools.swapFragment(requireActivity(), target.first, target.second, null)
    }

    /** The type picker draws its own back bar and title. */
    override fun drawsOwnHeader(): Boolean = true

    override fun drawsOwnProgress(): Boolean = false
}
