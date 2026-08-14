package net.kdt.pojavlaunch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.ui.logs.LogDocument
import net.kdt.pojavlaunch.ui.logs.LogScreen
import net.kdt.pojavlaunch.ui.logs.readLog
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Reading the log, in the launcher.
 *
 * Its own activity rather than a route inside Settings, because three unrelated places lead here
 * and one of them is the crash screen, which runs after the launcher's own process has gone.
 *
 * The read is a megabyte off the end of a file and happens on IO; until it lands the screen shows
 * a spinner rather than an empty log, because "no lines" and "not read yet" are different facts
 * and only one of them is worth an empty state.
 */
class LogActivity : BaseActivity() {

    /** The game hides the bars; a screen someone reads should not (handbook 16.12). */
    override fun setFullscreen(): Boolean = false

    private var document by mutableStateOf<LogDocument?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmethystXTheme {
                LogScreen(
                    document = document,
                    onBack = { finish() },
                    onShare = { runCatching { Tools.shareLog(this) } },
                    onCopyLine = ::copyLine
                )
            }
        }
        lifecycleScope.launch {
            val read = withContext(Dispatchers.IO) { readLog() }
            document = read
        }
    }

    /**
     * One line onto the clipboard, which is the copy anybody actually wants: the whole file is
     * what the share button is for, and a clipboard holding a megabyte of log helps nobody.
     */
    private fun copyLine(text: String) {
        runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.log_title), text))
            // Android 13 and up shows its own copy confirmation, so a toast there is a second
            // popup saying what the system just said.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
