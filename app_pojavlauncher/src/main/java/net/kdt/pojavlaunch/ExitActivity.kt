package net.kdt.pojavlaunch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import net.kdt.pojavlaunch.diagnosis.CrashDiagnosis
import net.kdt.pojavlaunch.diagnosis.CrashScreen
import net.kdt.pojavlaunch.diagnosis.CrashScreenModel
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import java.io.File

/**
 * What the player sees when the game dies.
 *
 * Started by native code: `nominal_exit` in `jni/stdio_is.c` resolves this class and
 * [showExitMessage]'s exact signature by name, from the dying `:game` process, and the activity
 * itself opens in the `:launcher` process, which is what lets it outlive the game. **The class
 * name, package, and the static method's JVM signature `(Landroid/content/Context;IZ)V` are
 * frozen by that lookup.**
 *
 * It used to be a stock dialog reading "exited with code %d, check latestlog.txt". Now the log's
 * tail is run through [CrashDiagnosis] and the failure is named in words — with the old dialog
 * kept as the fallback at every step, because the screen that explains crashes is the one screen
 * that cannot afford to cause any. The diagnosis runs before composition and the composition
 * renders only precomputed strings, so there is no device state left to fail on.
 */
@Keep
class ExitActivity : BaseActivity() {

    /** The game hides the bars; a screen someone reads should not. */
    override fun setFullscreen(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent?.extras
        val code = extras?.getInt("code", -1) ?: -1
        val isSignal = extras?.getBoolean("isSignal", false) ?: false

        val model = runCatching { buildModel(code, isSignal) }.getOrNull()
        var shown = false
        if (model != null) {
            shown = runCatching {
                setContent {
                    AmethystXTheme {
                        CrashScreen(
                            model = model,
                            onShareLog = { runCatching { Tools.shareLog(this) } },
                            onViewLog = ::viewLog,
                            onBackToLauncher = ::backToLauncher
                        )
                    }
                }
                true
            }.getOrDefault(false)
        }
        if (!shown) legacyDialog(code, isSignal)
    }

    /** Everything risky — the log read, the pattern scan — happens here, behind the caller's catch. */
    private fun buildModel(code: Int, isSignal: Boolean): CrashScreenModel? {
        val logFile = Tools.DIR_GAME_HOME?.let { File(it, "latestlog.txt") }
        val diagnosis = CrashDiagnosis.diagnose(this, code, isSignal, logFile) ?: return null
        val codeLine = getString(
            if (isSignal) R.string.crash_signal_line else R.string.crash_exit_line, code
        )
        return CrashScreenModel(codeLine, diagnosis)
    }

    /**
     * Open the log rather than share it.
     *
     * Wrapped like everything else this screen touches: the one screen that explains crashes is
     * the one that cannot afford to cause any, and a failed start here must leave the crash
     * screen exactly as it was rather than take it down with it.
     */
    private fun viewLog() {
        runCatching { startActivity(Intent(this, LogActivity::class.java)) }
    }

    private fun backToLauncher() {
        runCatching {
            startActivity(Intent(this, LauncherActivity::class.java))
            finish()
        }
    }

    /** The dialog this screen replaced, byte for byte, for when anything above declines to run. */
    @SuppressLint("StringFormatInvalid") // invalid on some translations but valid on most
    private fun legacyDialog(code: Int, isSignal: Boolean) {
        runCatching {
            val message = if (isSignal) R.string.mcn_signal_title else R.string.mcn_exit_title
            AlertDialog.Builder(this)
                .setMessage(getString(message, code))
                .setPositiveButton(R.string.main_share_logs) { _, _ -> Tools.shareLog(this) }
                .setOnDismissListener { finish() }
                .show()
        }.onFailure { finish() }
    }

    companion object {
        /** Called from `nominal_exit` via JNI — the signature is looked up by name at game start. */
        @Keep
        @JvmStatic
        fun showExitMessage(ctx: Context, code: Int, isSignal: Boolean) {
            val intent = Intent(ctx, ExitActivity::class.java)
            intent.putExtra("code", code)
            intent.putExtra("isSignal", isSignal)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }
    }
}
