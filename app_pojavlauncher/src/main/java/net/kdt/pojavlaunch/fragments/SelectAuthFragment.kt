package net.kdt.pojavlaunch.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.ui.auth.AuthChooserScreen
import net.kdt.pojavlaunch.ui.auth.NameError
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.ui.common.ChromeOwner
import java.io.File

/**
 * Signing in.
 *
 * Two screens folded into one: the pair of equal-weight buttons and the separate screen that held
 * a single username field. The seams either side are untouched — Microsoft still hands off to
 * [MicrosoftLoginFragment] for the whole OAuth flow, and an offline account is still created by
 * raising [ExtraConstants.MOJANG_LOGIN_TODO] for the account spinner to pick up, because the
 * spinner stays the source of truth for who is logged in while the launch path reads it.
 */
class SelectAuthFragment : Fragment(), ChromeOwner {
    companion object {
        const val TAG = "AUTH_SELECT_FRAGMENT"

        /** Minecraft's own rule for a name, which the offline field has to match to be usable. */
        private val NAME_PATTERN = Regex("^[a-zA-Z0-9_]*$")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AmethystXTheme {
                AuthChooserScreen(
                    onMicrosoft = {
                        Tools.swapFragment(
                            requireActivity(), MicrosoftLoginFragment::class.java,
                            MicrosoftLoginFragment.TAG, null
                        )
                    },
                    onOffline = ::createOfflineAccount,
                    validate = ::validateName,
                    onBack = { Tools.removeCurrentFragment(requireActivity()) }
                )
            }
        }
    }

    /**
     * Hand the name to the account spinner and go home.
     *
     * The spinner's Mojang listener is what actually writes the account file, so this raises the
     * same event the old username screen did rather than saving the account itself.
     */
    private fun createOfflineAccount(username: String) {
        ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, arrayOf(username, ""))
        Tools.backToMainMenu(requireActivity())
    }

    /**
     * Why a name would be refused, or null when it is fine.
     *
     * Returned rather than shown in a dialog, so the field can say what is wrong while it is
     * being typed instead of only once it has been submitted.
     */
    private fun validateName(name: String): NameError? = when {
        name.length < 3 -> NameError.TOO_SHORT
        name.length > 16 -> NameError.TOO_LONG
        !NAME_PATTERN.matches(name) -> NameError.CHARACTERS
        File(Tools.DIR_ACCOUNT_NEW, "$name.json").exists() -> NameError.TAKEN
        else -> null
    }

    /** The sign-in chooser draws its own header, and an account bar above a screen for choosing an
     * account would be saying the same thing twice. */
    override fun drawsOwnHeader(): Boolean = true

    override fun drawsOwnProgress(): Boolean = false
}
