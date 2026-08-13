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
    /* Whether this host's editor can offer a test session. False inside a running game, where the
     * layout is already live over the world and "test it" means closing the sheet. */
    private var testable by mutableStateOf(false)
    private var recording by mutableStateOf(RecordingUiState())
    // Named apart from setShutterOn(): a property of that name would clash with it on the JVM.
    private var shutter by mutableStateOf(false)

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
                ControlCenter(visible, editing, testable, recording, shutter, callbacks, ::close)
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
        sheetView.visibility = View.VISIBLE
        visible = true
    }

    fun close() {
        if (!visible) return
        visible = false
        // Held on screen for the exit animation; going straight to GONE would cut it in half.
        handler.postDelayed({
            if (!visible) sheetView.visibility = View.GONE
        }, EXIT_MS)
    }

    fun isOpen(): Boolean = visible

    /** Swap the actions for the ones the control layout editor needs while it is open. */
    fun setEditorMode(editor: Boolean) {
        editing = editor
    }

    /**
     * Offer the test session, which only the editor reached from Settings can run.
     *
     * A separate setter rather than a second argument on the one above, so no existing call site
     * moves. Adding a parameter to something several places already call is how eight positional
     * callbacks quietly bound to the wrong slot once before (16.18).
     *
     * Named apart from its property on purpose: `testable` already emits `setTestable` as its JVM
     * setter, private or not, and a method of that name beside it is a platform declaration clash
     * rather than an override. That has cost two build cycles here (16.9), which is why the
     * shutter's mutator two methods down is called `applyShutterOn`.
     */
    fun applyTestable(canTest: Boolean) {
        testable = canTest
    }

    /**
     * Whether the floating shutter is on screen.
     *
     * The sheet only reads this; the shutter itself lives in its own host, because it has to
     * outlive the sheet — the whole point of it is being there once this has gone.
     */
    fun applyShutterOn(on: Boolean) {
        shutter = on
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
    }

    private companion object {
        const val TICK_MS = 500L
        const val EXIT_MS = 300L
    }
}
