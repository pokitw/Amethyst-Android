package net.kdt.pojavlaunch

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.ui.onboarding.OnboardingScreen
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * The welcome, shown once.
 *
 * It is its own activity rather than a fragment inside [LauncherActivity], and deliberately so.
 * The launcher's chrome — the account bar, the settings button, the progress bar — is put back for
 * any fragment that is not the home screen or settings, so a fragment here would draw the old bar
 * over a brand new welcome. An activity never inflates that layout at all.
 *
 * It also runs *before* [LauncherActivity] exists, which is what keeps it clear of §12: the
 * ExtraCore launch seam, the account spinner that owns the login listeners, and the progress
 * registry are all constructed later. That is why nothing here signs in or launches anything —
 * those events would fire into a bus with no listener attached. The flow ends at the home screen
 * and lets the launcher do its own job.
 */
class OnboardingActivity : BaseActivity() {

    /** BaseActivity hides the system bars for the game's benefit; a page you read wants them. */
    override fun setFullscreen(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back must not drop the user out of the app before the launcher has ever started. Skip is
        // right there and goes to the same place, so this is a dead end rather than a trap.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        setContent {
            AmethystXTheme {
                OnboardingScreen(onFinish = ::finishOnboarding)
            }
        }
    }

    /**
     * Marks the flow done and hands over.
     *
     * The flag is written before the launcher starts so that a crash on the way in cannot put
     * someone back through the welcome a second time.
     *
     * The guard is not paranoia. `finish()` does not tear the activity down there and then — the
     * window keeps input focus until the launcher's own has drawn — so an impatient second tap on
     * Get started would start a *second* LauncherActivity. It is declared `standard`, so both
     * would live, and the first one's LAUNCH_GAME listener is only unregistered in its onDestroy,
     * which never runs. Pressing Play would then fire two downloads of the same version, which is
     * exactly what the launch seam is meant to be protected from.
     */
    private fun finishOnboarding() {
        if (isFinishing) return
        LauncherPreferences.DEFAULT_PREF.edit()
            .putBoolean(LauncherPreferences.PREF_KEY_ONBOARDING_DONE, true)
            .apply()
        startActivity(Intent(this, LauncherActivity::class.java))
        finish()
    }
}
