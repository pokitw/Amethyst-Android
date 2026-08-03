package net.kdt.pojavlaunch.ui.game

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Drives the in-game control center from the game activity.
 *
 * The activity is Java and knows nothing about Compose, so everything it needs is a plain method
 * here. Both views are hidden rather than removed when idle: a `ComposeView` stretched over the
 * game surface would otherwise sit in front of every touch the game is waiting for.
 *
 * @param sheetView the full-screen host for the sheet, hidden unless it is open
 * @param pillView the small host for the live recording pill, hidden unless recording
 */
class ControlCenterHost(
    private val sheetView: ComposeView,
    private val pillView: ComposeView,
    private val callbacks: ControlCenterCallbacks
) {
    private var visible by mutableStateOf(false)
    // Named apart from setEditorMode(): a property of that name would generate the same JVM setter.
    private var editing by mutableStateOf(false)
    private var showingKeyboard by mutableStateOf(false)
    private var recording by mutableStateOf(RecordingUiState())

    /**
     * Lives here rather than inside the composition because it is not really UI state: a latched
     * key is a key-down the game has already been told about. It has to survive the sheet being
     * recomposed, and above all it has to be given back on the way out, which [close] and
     * [release] are the only two places that can promise.
     */
    private val keyboardState = GameKeyboardState()

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Polls rather than being pushed to, because nothing in the recorder reports per-second
     * progress and adding that to the encode path to drive a label would be the wrong trade.
     */
    private val tick = object : Runnable {
        override fun run() {
            if (!recording.active) return
            recording = recording.copy(
                elapsedMs = callbacks.recordingElapsedMs(),
                bytes = callbacks.recordingBytes()
            )
            handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        sheetView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        sheetView.setContent {
            AmethystXTheme {
                ControlCenter(
                    visible, editing, showingKeyboard, keyboardState, recording, callbacks, ::close
                )
            }
        }
        pillView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        pillView.setContent {
            AmethystXTheme { RecordingPill(recording.elapsedMs) }
        }
    }

    fun open() {
        // The pull tab always means the menu, even if the keyboard was the last thing showing.
        keyboardState.releaseAll()
        showingKeyboard = false
        sheetView.visibility = View.VISIBLE
        visible = true
    }

    /** Bring up the on-screen keyboard in place of the menu. */
    fun openKeyboard() {
        keyboardState.releaseAll()
        keyboardState.showLetters()
        showingKeyboard = true
        sheetView.visibility = View.VISIBLE
        visible = true
    }

    fun close() {
        if (!visible) return
        visible = false
        // A key latched on the keyboard is down inside the game; leaving one that way would
        // quietly break every key pressed afterwards, so nothing leaves the screen still held.
        keyboardState.releaseAll()
        // Held on screen for the exit animation; going straight to GONE would cut it in half.
        handler.postDelayed({
            if (!visible) {
                sheetView.visibility = View.GONE
                // The sliding panel is still hit-testable for the length of that animation, so a
                // second impatient tap can latch a key after the release above has already run.
                // This is the one that catches it — and `close()` cannot, having already returned
                // at its own guard by then.
                keyboardState.releaseAll()
                // Only after it is out of sight, so the sheet is never seen flipping back.
                showingKeyboard = false
            }
        }, EXIT_MS)
    }

    fun isOpen(): Boolean = visible

    /** Swap the actions for the ones the control layout editor needs while it is open. */
    fun setEditorMode(editor: Boolean) {
        editing = editor
    }

    /**
     * What a recording would be if one were started now — resolution, frame rate and audio.
     * Shown on the idle card so the settings do not have to be opened to find out.
     */
    fun setRecordingSummary(summary: String) {
        if (!recording.active) recording = recording.copy(summary = summary)
    }

    fun onRecordingStarted(summary: String, maxBytes: Long) {
        recording = RecordingUiState(
            active = true, elapsedMs = 0L, bytes = 0L, maxBytes = maxBytes, summary = summary
        )
        pillView.visibility = View.VISIBLE
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun onRecordingStopped(summary: String) {
        handler.removeCallbacks(tick)
        recording = RecordingUiState(summary = summary)
        pillView.visibility = View.GONE
    }

    /** Drop the ticker, so a finishing activity does not keep posting to a dead view. */
    fun release() {
        handler.removeCallbacks(tick)
        keyboardState.releaseAll()
    }

    private companion object {
        const val TICK_MS = 500L
        const val EXIT_MS = 300L
    }
}
