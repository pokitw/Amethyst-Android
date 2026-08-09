package net.kdt.pojavlaunch.ui.game

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.kdt.pojavlaunch.customcontrols.keyboard.CharacterSenderStrategy
import net.kdt.pojavlaunch.customcontrols.keyboard.LiveTyper
import net.kdt.pojavlaunch.customcontrols.keyboard.VoiceInput
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Drives voice typing from the game activity.
 *
 * Typing in Minecraft on a phone means holding the device in landscape with both hands and then
 * finding a way to use a keyboard that covers half of it. Talking is the obvious answer and has
 * been the whole time; this is the part that was missing.
 *
 * The activity is Java and knows nothing about Compose or about recognisers, so what it gets is
 * four methods: the two edges of a press on a voice button, the same for the chat-key shortcut,
 * and the lifecycle calls. Everything between a finger going down and words appearing in chat
 * happens here.
 *
 * <b>Press semantics.</b> One button covers both ways people dictate. A tap starts listening and
 * stays listening, which is what a long sentence wants; holding the button listens only while it
 * is held, which is what a two-word reply wants. They are told apart by how long the finger stayed
 * down — which is why the button hands over both edges rather than deciding for itself.
 */
class VoiceInputHost(
    private val view: ComposeView,
    private val voice: VoiceInput,
    private val sender: CharacterSenderStrategy,
    private val gate: Gate
) : VoiceInput.Listener {

    /** The two questions this cannot answer for itself, both of which belong to the activity. */
    interface Gate {
        /**
         * @return 0 when dictation may start, or a string resource explaining why it may not —
         *         shown in the overlay rather than swallowed, because a microphone that silently
         *         does nothing is indistinguishable from a broken one.
         */
        fun voiceBlockedReason(): Int

        /**
         * @return true when the microphone permission is already held. False asks for it, and the
         *         system prompt is the feedback; the player presses again afterwards.
         */
        fun ensureMicrophonePermission(): Boolean
    }

    private var state by mutableStateOf(VoiceUiState())
    private val handler = Handler(Looper.getMainLooper())

    /**
     * The model of the chat box being written into. Lives here, not in the composition: the
     * characters it has sent are already inside the game, so nothing about it is UI state.
     */
    private val typer = LiveTyper(sender)

    private var pressedAt = 0L
    /** Whether the press currently under a finger is the one that started this session. */
    private var startedByThisPress = false

    private val hideMessage = Runnable { if (!voice.isListening()) clearOverlay() }

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            AmethystXTheme { VoiceOverlay(state, ::stopPressed) }
        }
        voice.setListener(this)
    }

    fun isListening(): Boolean = voice.isListening()

    /* ------------------------------------------------------------------ the voice button */

    fun onButtonDown() {
        pressedAt = SystemClock.uptimeMillis()
        // A press that lands on a live session is the tap that ends it, and that is decided on
        // the release — holding it should not stop and restart.
        startedByThisPress = if (voice.isListening()) false else start()
    }

    fun onButtonUp() {
        val held = SystemClock.uptimeMillis() - pressedAt
        if (!startedByThisPress) {
            if (voice.isListening()) finish()
            return
        }
        startedByThisPress = false
        if (held >= HOLD_MS) finish()
    }

    /* ------------------------------------------------------------------ the chat-key shortcut */

    /**
     * Push-to-talk from an ordinary control button, held rather than tapped.
     *
     * Unconditionally push-to-talk: the player is holding a chat key, so the release is the only
     * signal that they have finished, and there is no tap to distinguish it from.
     */
    fun onShortcut(down: Boolean) {
        if (down) {
            // Guarded, unlike the voice button: a session already running was started some other
            // way, and restarting the overlay under it would throw away the words on screen.
            if (!voice.isListening()) start()
        } else if (voice.isListening()) {
            finish()
        }
    }

    /* ------------------------------------------------------------------ lifecycle */

    /**
     * Stop listening and take back anything typed so far.
     *
     * The one thing that undoes a dictation, and it is reached only by the back key, because back
     * is the gesture that already means "never mind" everywhere else. The overlay's own stop
     * control does not do this: a control labelled "stop" that also deleted the sentence would be
     * the wrong answer to the far more common "that will do".
     */
    fun cancel() {
        // Undone before the recogniser is stopped, not after: VoiceInput reports the stop
        // synchronously, and a callback that found text still tracked here would take it for the
        // transcript and press Enter on the sentence being thrown away.
        typer.clear()
        if (voice.isListening()) voice.cancel()
        clearOverlay()
    }

    /** Stop listening for a reason that is not the player's decision, keeping what was typed. */
    fun dismiss() {
        typer.forget()
        if (voice.isListening()) voice.cancel()
        clearOverlay()
    }

    /** For an activity going away: drop the service binding along with everything else. */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        voice.setListener(null)
        voice.release()
        typer.forget()
        state = VoiceUiState()
        view.visibility = View.GONE
    }

    /* ------------------------------------------------------------------ internals */

    private fun start(): Boolean {
        val blocked = gate.voiceBlockedReason()
        if (blocked != 0) {
            showMessage(blocked)
            return false
        }
        if (!gate.ensureMicrophonePermission()) return false

        // A previous dictation's text belongs to the player now. Tracking it into this one would
        // let the first correction backspace over words they typed themselves.
        typer.forget()
        handler.removeCallbacks(hideMessage)
        state = VoiceUiState(listening = true)
        view.visibility = View.VISIBLE
        voice.start(null)
        return voice.isListening()
    }

    /** End a dictation and keep what was heard, which is what a release or a second tap means. */
    private fun finish() {
        voice.stop()
    }

    /** The overlay's stop control: end the dictation, keep the words. */
    private fun stopPressed() {
        if (voice.isListening()) finish() else clearOverlay()
    }

    private fun showMessage(messageRes: Int) {
        state = VoiceUiState(listening = false, messageRes = messageRes)
        view.visibility = View.VISIBLE
        handler.removeCallbacks(hideMessage)
        handler.postDelayed(hideMessage, MESSAGE_MS)
    }

    private fun clearOverlay() {
        handler.removeCallbacks(hideMessage)
        state = VoiceUiState()
        // Held for the exit animation, then GONE: a ComposeView over the game that is merely
        // invisible still takes every touch the game is waiting for.
        handler.postDelayed({
            if (!state.showing) view.visibility = View.GONE
        }, EXIT_MS)
    }

    /* ------------------------------------------------------------------ recogniser callbacks */

    override fun onVoiceStarted() {
        state = state.copy(listening = true, text = "", level = 0f, messageRes = 0)
        view.visibility = View.VISIBLE
    }

    override fun onVoiceText(text: String, isFinal: Boolean) {
        state = state.copy(text = text, messageRes = 0)
        // With live typing off, a guess only updates the overlay; the final transcript still goes
        // through the same call, which then types it in one go because nothing preceded it.
        if (!isFinal && !LauncherPreferences.PREF_VOICE_LIVE_TYPING) return

        typer.set(text)
        if (!isFinal) return

        val typed = typer.typed()
        typer.forget()
        if (typed.isNotEmpty() && LauncherPreferences.PREF_VOICE_AUTO_SEND) sender.sendEnter()
    }

    override fun onVoiceStopped(errorRes: Int) {
        // An error does not take the words back. Text the player watched appear should not vanish
        // because the recogniser gave up on the sentence — it is right there to edit, and undoing
        // it for them is the more startling of the two behaviours. Only [cancel] undoes anything.
        //
        // Still holding text here means no final transcript ever arrived, which some recognisers
        // simply do not send: the last guess is all there is, so it counts as what was said. On
        // the ordinary path the final has already been typed and forgotten, which is what stops
        // this sending a second Enter.
        if (errorRes == 0 && typer.typed().isNotEmpty()
            && LauncherPreferences.PREF_VOICE_AUTO_SEND) sender.sendEnter()
        typer.forget()
        if (errorRes != 0) showMessage(errorRes) else clearOverlay()
    }

    override fun onVoiceLevel(level: Float) {
        if (state.listening) state = state.copy(level = level)
    }

    private companion object {
        /** Past this a press is a hold, so the release ends the dictation rather than ignoring it. */
        const val HOLD_MS = 350L
        /** How long an error stays up. Long enough to read, short enough not to sit over the game. */
        const val MESSAGE_MS = 2800L
        const val EXIT_MS = 260L
    }
}
