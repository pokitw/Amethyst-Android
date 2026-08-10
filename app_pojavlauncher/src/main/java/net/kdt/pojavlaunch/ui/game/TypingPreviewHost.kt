package net.kdt.pojavlaunch.ui.game

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.customcontrols.keyboard.CharacterSenderStrategy
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Somewhere for the characters going into the game to be seen.
 *
 * The producers, whoever they are, call this and nothing else. It exists so the on-screen keyboard
 * does not have to know that a view is involved.
 */
interface TypingSink {
    /** One character has gone to the game. */
    fun typed(character: Char)

    /** One backspace has gone to the game. */
    fun deleted()

    /** Enter, Escape, or anything else that means the field is finished with. */
    fun committed()

    /**
     * Something happened that this cannot model, so stop claiming to know the text.
     *
     * Not the same as [committed]: the field is still open and still being typed into, it is only
     * the mirror that has lost its place. What follows is still worth showing, with a mark on the
     * front saying there is more in front of it.
     */
    fun forget()
}

/**
 * A strip at the top of the game showing what is being typed.
 *
 * <b>Why this exists.</b> Naming a world, or typing a command, puts an Android keyboard over the
 * bottom half of a landscape screen, and the launcher answers that by panning the game up so the
 * field clears the board. On a short screen the pan is most of the height, and the field it is
 * lifting frequently goes off the top instead. The only way to read what you have written is to
 * close the keyboard, look, and open it again, which is a long way to go to check a typo.
 *
 * <b>What it can honestly show.</b> Not the field. The launcher cannot read a single pixel of what
 * the game draws, so there is no such thing as reading the text back out; the same wall
 * {@code LiveTyper} lives behind. What it can do is remember the characters it sent, which is a
 * different and smaller claim: <i>this is what you have typed</i>, not <i>this is what the field
 * says</i>. Everything below follows from taking that distinction seriously.
 *
 * <b>So it never lies about what it does not know.</b> Backspacing past the first character it saw
 * means the game just deleted something this never had, and from that moment the text on screen is
 * a tail rather than the whole of it. Rather than quietly dropping the backspace, or hiding and
 * taking the feature away at the exact moment it was being used, it puts an ellipsis on the front
 * and carries on: everything after the mark is still true. Losing its place any other way, which is
 * what [forget] is for, lands in the same state.
 *
 * <b>It watches a session, not a lifetime.</b> A session is one spell with a keyboard open, and the
 * text is dropped when the last one closes, when Enter is sent, and when the game grabs the cursor
 * back. That is not a compromise: with no keyboard up there is no pan, so the field is on screen
 * and can be read directly. Carrying text from one session into the next would only ever mean
 * showing an old field's contents over a new field.
 *
 * <b>And it moves the other way to the screen.</b> Every overlay here is a child of the frame that
 * the keyboard pans, so a strip left alone would ride the pan up and leave the screen at exactly
 * the moment it became useful. [applyPan] cancels the frame's own translation out, which pins it to
 * the top of the window while everything behind it slides.
 *
 * @param view the strip, top-anchored and sized to its content
 */
class TypingPreviewHost(private val view: ComposeView) : TypingSink {

    /**
     * Read once, here, like every other in-game setting: this is the game's own process and the
     * value cannot change under it while a game is running (§17).
     */
    private val enabled = LauncherPreferences.PREF_TYPING_PREVIEW

    /** The model: what has been typed this session, and whether there is more in front of it. */
    private var text = ""
    private var truncated = false

    /**
     * What the strip is drawing, which is the model only while the strip is up.
     *
     * Split from the model rather than read straight off it, for the reason the screenshot card
     * splits its message from its visibility: clearing the text is what ends a session, and a
     * composition that had already lost its words would spend the whole exit animation collapsing
     * an empty box instead of sliding the sentence away.
     */
    private var shownText by mutableStateOf("")
    private var shownTruncated by mutableStateOf(false)
    private var showing by mutableStateOf(false)

    /** Which keyboards are up. The strip watches while either is, and resets when both go. */
    private var imeOpen = false
    private var boardOpen = false

    /**
     * Whether anything has been typed since the session began.
     *
     * Latched rather than derived from the text being non-empty, because deleting back to nothing
     * is a normal thing to do halfway through a word and a strip that vanished and came back on
     * every such pass would be worse than no strip. Once it has appeared it stays for the session
     * (§16.13).
     */
    private var started = false

    private val handler = Handler(Looper.getMainLooper())

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            AmethystXTheme { TypingStrip(showing, shownText, shownTruncated) }
        }
    }

    /**
     * Mirror what a sender is about to put into the game.
     *
     * A wrapper rather than a change to the senders themselves: [CharacterSenderStrategy] and its
     * two implementations are upstream files on the input path, and the cheapest change that can be
     * made to a file like that is none at all. Returns the sender untouched when the preference is
     * off, so nothing is wrapped and nothing is paid for a feature that is not on.
     */
    fun watch(inner: CharacterSenderStrategy): CharacterSenderStrategy {
        if (!enabled) return inner
        return object : CharacterSenderStrategy {
            override fun sendBackspace() {
                inner.sendBackspace()
                this@TypingPreviewHost.deleted()
            }

            override fun sendEnter() {
                inner.sendEnter()
                this@TypingPreviewHost.committed()
            }

            override fun sendChar(character: Char) {
                inner.sendChar(character)
                this@TypingPreviewHost.typed(character)
            }
        }
    }

    /* ------------------------------------------------------------------ the sink */

    override fun typed(character: Char) {
        if (!enabled) return
        // Control characters are not text: the IME sends the odd one and they would draw as a box
        // or as nothing at all. Enter arrives through sendEnter, which is handled properly.
        if (character.code < ' '.code || character.code == 127) return
        started = true
        text += character
        if (text.length > MAX_CHARACTERS) {
            text = text.substring(text.length - MAX_CHARACTERS)
            truncated = true
        }
        refresh()
    }

    override fun deleted() {
        if (!enabled) return
        started = true
        // Deleting into text this never saw. The mark is what keeps the rest of the strip true.
        if (text.isEmpty()) truncated = true else text = text.dropLast(1)
        refresh()
    }

    override fun committed() {
        if (!enabled) return
        reset()
    }

    override fun forget() {
        if (!enabled) return
        text = ""
        truncated = true
        refresh()
    }

    /* ------------------------------------------------------- what the activity drives */

    /** The system keyboard has come up or gone away. */
    fun applyImeOpen(open: Boolean) {
        if (!enabled || imeOpen == open) return
        imeOpen = open
        if (!open && !boardOpen) reset() else refresh()
    }

    /** The launcher's own on-screen keyboard has opened or closed. */
    fun applyBoardOpen(open: Boolean) {
        if (!enabled || boardOpen == open) return
        boardOpen = open
        if (!open && !imeOpen) reset() else refresh()
    }

    /**
     * Cancel out the pan the keyboard applied to the frame this sits in.
     *
     * @param pixels how far the frame has been moved, so this is moved back by as much
     */
    fun applyPan(pixels: Int) {
        if (!enabled) return
        view.translationY = -pixels.toFloat()
    }

    /**
     * The game has taken the cursor back, so every text field is closed behind it.
     *
     * The one signal here that is about the game rather than about a keyboard, and the only one
     * that catches a field dismissed without the keyboard being closed first.
     */
    fun applyGrab(grabbing: Boolean) {
        if (!enabled || !grabbing) return
        reset()
    }

    /** Drop anything pending, so a finishing activity does not post to a dead view. */
    fun release() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun reset() {
        text = ""
        truncated = false
        started = false
        refresh()
    }

    private fun refresh() {
        val next = started && (imeOpen || boardOpen)
        // Published only on the way in and while up; on the way out the strip keeps what it had.
        if (next) {
            shownText = text
            shownTruncated = truncated
        }
        if (next == showing) return
        showing = next
        if (next) {
            view.visibility = View.VISIBLE
        } else {
            // Held for the exit animation, then out of the way of the game's touches (§12.9).
            handler.postDelayed({ if (!showing) view.visibility = View.GONE }, EXIT_MS)
        }
    }

    private companion object {
        /**
         * How much of the tail is kept.
         *
         * Chat takes 256 and a world name far less, so this is not a limit anyone types into; it
         * is a bound on a string that is rebuilt on every keystroke and laid out on every frame.
         */
        const val MAX_CHARACTERS = 120
        const val EXIT_MS = 220L
    }
}

/**
 * The strip itself.
 *
 * Deliberately plain. It has no icon, no heading and no caret, because it is read in the middle of
 * doing something else and every extra mark is one more thing to look past. What it is is obvious
 * from the fact that it contains what you just typed.
 *
 * No caret in particular is a decision rather than an omission. A caret would say the launcher
 * knows where in the field the next character lands, and it does not: nothing here can follow an
 * arrow key into text it cannot read. Drawing one would be a promise the strip cannot keep.
 *
 * Padded clear of a display cutout as well as anchored below the top edge, so the strip does not
 * end up under a camera on a phone that puts one on the long edge.
 */
@Composable
private fun TypingStrip(showing: Boolean, text: String, truncated: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.displayCutoutPadding()) {
        AnimatedVisibility(
            visible = showing,
            enter = slideInVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 2 } +
                    fadeIn(tween(160)),
            exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 2 } +
                    fadeOut(tween(160))
        ) {
            Row(
                Modifier
                    .widthIn(max = 420.dp)
                    .clip(MaterialTheme.shapes.medium)
                    // Nearly opaque, because it has to stay legible over whatever the game happens
                    // to be drawing, and the game is as often a bright sky as a cave.
                    .background(colors.surfaceContainerHigh.copy(alpha = 0.94f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scroll = rememberScrollState()
                // Keyed on the scroll extent rather than on the text, because the extent is what
                // changes *after* the new text has been laid out. Keying on the text would scroll
                // to the end of the previous line's worth of it and sit one character behind.
                LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
                Text(
                    buildAnnotatedString {
                        if (truncated) {
                            // Quieter than the text, so it reads as a mark about the text rather
                            // than as three full stops somebody typed.
                            withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append("…") }
                        }
                        append(text)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    // Scrolled programmatically and never by hand: the strip floats over a running
                    // game, and a control that answers a swipe is a control that steals one.
                    modifier = Modifier
                        .horizontalScroll(scroll, enabled = false)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                )
            }
        }
    }
}
