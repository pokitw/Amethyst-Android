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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Drives the on-screen keyboard, on its own, over the game.
 *
 * The board used to live inside the control center's sheet, reachable only by opening that sheet
 * first. It has its own host now for one reason: the control center's view is stretched over the
 * whole screen, so while it was up the game was neither visible nor touchable. A keyboard is not a
 * menu — you poke F3 or type a command and carry on — so this view is only as tall as the board,
 * captures touches only inside itself, and needs no scrim at all. Everything above it stays live.
 *
 * The visibility discipline is the same one the control center follows and for the same reason: a
 * `ComposeView` over the game surface must be GONE unless it is showing something, or it eats
 * every touch the game is waiting for. GONE is delayed by the exit animation's length so the slide
 * out is not cut in half.
 */
class GameKeyboardHost(private val view: ComposeView) {

    private var visible by mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Lives here rather than in the composition because a latched key is not UI state — it is a
     * key-down the game has already been told about, and something has to give it back.
     */
    val keys = GameKeyboardState()

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            AmethystXTheme {
                AnimatedVisibility(
                    visible = visible,
                    modifier = Modifier.fillMaxWidth(),
                    enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } +
                            fadeIn(tween(160)),
                    exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } +
                            fadeOut(tween(180))
                ) {
                    KeyboardPanel(keys, ::close)
                }
            }
        }
    }

    fun isOpen(): Boolean = visible

    fun open() {
        keys.releaseAll()
        keys.showLetters()
        view.visibility = View.VISIBLE
        visible = true
    }

    fun close() {
        if (!visible) return
        visible = false
        // A latched key is down inside the game; nothing leaves the screen still held.
        keys.releaseAll()
        handler.postDelayed({
            if (!visible) {
                view.visibility = View.GONE
                // The panel is still hit-testable while it slides out, so a key latched during
                // those milliseconds would survive the release above. This is what catches it.
                keys.releaseAll()
            }
        }, EXIT_MS)
    }

    /** Give every held key back, for an activity that is going away. */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        keys.releaseAll()
    }

    private companion object {
        const val EXIT_MS = 260L
    }
}
