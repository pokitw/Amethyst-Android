package net.kdt.pojavlaunch.ui.game

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.screenshot.GameScreenshot
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.ui.theme.SlotWell
import java.io.File

/**
 * Takes screenshots on behalf of the game activity, and says so.
 *
 * The activity is Java and gets one method: [take]. Everything from arming the render thread to
 * telling the player where the picture went happens here.
 *
 * <b>Why there is a confirmation at all.</b> A screenshot has no visible result — the game carries
 * on exactly as it was, and the file lands in a folder nobody has open. Without a word on screen
 * the only way to find out whether the button worked is to leave the game and go looking, which is
 * the opposite of what a one-tap capture is for. The handbook's rule is that the thing you touched
 * is the thing that responds; the nearest this can get is responding immediately, in place, and
 * naming the file so it can be recognised later.
 *
 * <b>Why it is its own view.</b> Same reason the dictation overlay is: a `ComposeView` stretched
 * over the game surface takes every touch the game is waiting for, so this one is only as tall as
 * the card, is anchored clear of the middle of the screen, and is `GONE` whenever it has nothing
 * to say (§12.9).
 *
 * <b>The shutter is the other half of this.</b> Taking a screenshot from inside the control center
 * means the sheet is covering the very thing being photographed, so the one place the capture
 * really belongs is over the running game. That is what a bound control button gives you and it
 * remains the best answer for anyone willing to spend a minute in the editor; the shutter is the
 * same thing without the minute — turned on from the sheet, it stays over the game until it is
 * turned off, and the sheet is nowhere near it.
 *
 * @param toastView  the confirmation card, top-anchored and sized to its content
 * @param shutterView the floating shutter, anchored to the middle of the right edge
 */
class ScreenshotHost(
    private val view: ComposeView,
    private val shutterView: ComposeView
) {

    /**
     * The last thing said, kept after it has been dismissed.
     *
     * Split from [showing] rather than nulled, because the card animates out and a composition
     * that had lost its text would spend the whole exit animating an empty box.
     */
    private var message by mutableStateOf(ScreenshotMessage(R.string.screenshot_saved, null, true))
    private var showing by mutableStateOf(false)

    private val handler = Handler(Looper.getMainLooper())

    private val hide = Runnable {
        showing = false
        // Held for the exit animation, then taken out of the way of the game's touches.
        handler.postDelayed({ if (!showing) view.visibility = View.GONE }, EXIT_MS)
    }

    private var shutter by mutableStateOf(false)

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            AmethystXTheme { ScreenshotToast(showing, message) }
        }
        shutterView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        shutterView.setContent {
            AmethystXTheme { Shutter(shutter, ::take) { applyShutter(false) } }
        }
    }

    /** Whether the floating shutter is on screen, so the control center can say so. */
    fun isShutterVisible(): Boolean = shutter

    /**
     * Show or hide the floating shutter.
     *
     * Named apart from the [shutter] property: a Kotlin property still emits its JVM accessors, so
     * a `setShutter` here and a `var shutter` there are the same signature twice (§16.9).
     */
    fun applyShutter(visible: Boolean) {
        shutter = visible
        if (visible) {
            shutterView.visibility = View.VISIBLE
        } else {
            // Held for the exit animation, then out of the way of the game's touches.
            handler.postDelayed({ if (!shutter) shutterView.visibility = View.GONE }, EXIT_MS)
        }
    }

    fun toggleShutter() = applyShutter(!shutter)

    /** Capture the frame the game is presenting, then report where it went. */
    fun take() {
        GameScreenshot.take(object : GameScreenshot.Callback {
            override fun onScreenshotSaved(file: File) {
                show(ScreenshotMessage(R.string.screenshot_saved, file.name, true))
            }

            override fun onScreenshotFailed(messageRes: Int) {
                show(ScreenshotMessage(messageRes, null, false))
            }
        })
    }

    /**
     * Drop everything pending, so a finishing activity does not post to a dead view.
     *
     * Everything, not just [hide]: both overlays also post an unnamed lambda to take themselves
     * GONE once their exit animation is over, and those cannot be removed by reference.
     */
    fun release() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun show(next: ScreenshotMessage) {
        message = next
        showing = true
        view.visibility = View.VISIBLE
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, VISIBLE_MS)
    }

    private companion object {
        /** Long enough to read a filename, short enough not to sit over a fight. */
        const val VISIBLE_MS = 2600L
        const val EXIT_MS = 260L
    }
}

/** What the card is saying: a heading, the filename when there is one, and whether it went well. */
private class ScreenshotMessage(val titleRes: Int, val detail: String?, val ok: Boolean)

/**
 * The shutter, floating over the running game.
 *
 * A ring around a disc, because that is what a shutter has looked like since long before phones
 * and it needs no label to be understood. It is the only round control in the launcher, which is
 * the point — nothing else on screen could be mistaken for it.
 *
 * The disc shrinks under the finger rather than the whole button, so the ring stays put and the
 * press reads as a shutter closing. 140ms, the same press feedback everything else uses.
 *
 * Dismissal is a small cross above it rather than a tap anywhere else, because "anywhere else" is
 * the game and taking a screenshot must never cost a swing of the pickaxe.
 */
@Composable
private fun Shutter(visible: Boolean, onCapture: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it } +
                fadeIn(tween(180)),
        exit = slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it } +
                fadeOut(tween(180))
    ) {
        Column(
            Modifier.padding(end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer.copy(alpha = 0.85f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.screenshot_shutter_hide),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val inner by animateFloatAsState(
                targetValue = if (pressed) 0.74f else 1f,
                animationSpec = tween(140, easing = FastOutSlowInEasing),
                label = "shutterPress"
            )
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    // Faintly filled rather than transparent, so the ring still reads against a
                    // bright sky — the same problem the filled control glyphs exist for.
                    .background(colors.scrim.copy(alpha = 0.35f))
                    .border(2.5.dp, colors.onSurface.copy(alpha = 0.9f), CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onCapture,
                        onClickLabel = stringResource(R.string.control_center_screenshot)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(42.dp * inner)
                        .clip(CircleShape)
                        .background(colors.primary)
                )
            }
        }
    }
}

/**
 * The card itself.
 *
 * Top-anchored, because the bottom of the screen in landscape is where both thumbs and both of
 * the other overlays already are, and a capture confirmation must never be the thing covering the
 * hotbar. It is not interactive — nothing here is worth a second tap — so it takes no touches and
 * the game stays playable underneath.
 */
@Composable
private fun ScreenshotToast(showing: Boolean, shown: ScreenshotMessage) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
        AnimatedVisibility(
            visible = showing,
            enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it } +
                    fadeIn(tween(180)),
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it } +
                    fadeOut(tween(200))
        ) {
            Row(
                Modifier
                    .widthIn(max = 360.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SlotWell(size = 34.dp) {
                    Icon(
                        painterResource(
                            if (shown.ok) R.drawable.ic_x_camera else R.drawable.ic_x_info
                        ),
                        contentDescription = null,
                        tint = if (shown.ok) colors.primary else colors.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        stringResource(shown.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                    if (shown.detail != null) {
                        Text(
                            shown.detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
