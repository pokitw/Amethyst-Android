package net.kdt.pojavlaunch.ui.game

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.screenshot.GameScreenshot
import net.kdt.pojavlaunch.screenshot.ScreenshotPreferences
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
     * The screenshot settings as they stood when the game launched.
     *
     * Read once, here, rather than per capture: the game is its own process and these cannot
     * change under it, and reading the shutter's side and size per frame to lay out a button
     * would be exactly the mistake §16.11 records.
     */
    private val settings = ScreenshotPreferences.load(shutterView.context)

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
        // Anchored to the top left and then moved with a translation, rather than hung off an
        // edge with a gravity. Where it sits is the player's, so it is a position rather than a
        // side, and translation moves a view without asking the parent to lay out again.
        (shutterView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.gravity = Gravity.TOP or Gravity.START
            shutterView.layoutParams = params
        }
        // A rotation or a resolution change resizes the parent under it, and a translation that
        // was inside the old bounds can be outside the new ones.
        shutterView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
        shutterView.setContent {
            AmethystXTheme {
                Shutter(
                    visible = shutter,
                    diameter = settings.shutterDiameterDp().dp,
                    onCapture = ::take,
                    onGrab = ::onGrab,
                    onMove = ::onMove,
                    onDrop = ::onDrop,
                    onClose = { applyShutter(false) }
                )
            }
        }
        if (settings.shutterAtStart) applyShutter(true)
    }

    /* ------------------------------------------------------------------ dragging */

    /**
     * Where it was left last time, as fractions of the room it has to move in.
     *
     * Initialised here rather than in the init block above, because Kotlin runs initialisers in
     * declaration order and anything assigned up there would be overwritten by these. Everything
     * that reads them is posted or driven by a layout pass, so both have landed by then.
     */
    private val startPosition = ScreenshotPreferences.loadShutterPosition(shutterView.context)
    private var fractionX = startPosition[0]
    private var fractionY = startPosition[1]
    private var dragging = false

    /**
     * Put the shutter where the fractions say, within what the parent currently allows.
     *
     * Skipped mid-drag: the finger is the authority then, and recomputing from fractions that are
     * only written on release would drag the button back out from under it.
     */
    private fun place() {
        if (dragging) return
        val parent = shutterView.parent as? View ?: return
        val freeX = (parent.width - shutterView.width).coerceAtLeast(0)
        val freeY = (parent.height - shutterView.height).coerceAtLeast(0)
        shutterView.translationX = freeX * fractionX
        shutterView.translationY = freeY * fractionY
    }

    private fun onGrab() {
        dragging = true
    }

    /**
     * Move by what the finger has moved since it grabbed.
     *
     * The offset arriving here is the pointer's position within the view minus where it took hold,
     * and it is added to the translation rather than assigned. That matters: moving the view moves
     * its own coordinate space, so a naive delta between consecutive local positions reads zero
     * from the second event onwards and the button sticks. Measuring against the grab point
     * instead is self-correcting — once the view has caught up, the offset is zero again.
     */
    private fun onMove(dx: Float, dy: Float) {
        val parent = shutterView.parent as? View ?: return
        val freeX = (parent.width - shutterView.width).toFloat().coerceAtLeast(0f)
        val freeY = (parent.height - shutterView.height).toFloat().coerceAtLeast(0f)
        // Clamped to the parent, so it can never be pushed somewhere it cannot be grabbed back
        // from. That is also why there is no "reset position" anywhere: it cannot get lost.
        shutterView.translationX = (shutterView.translationX + dx).coerceIn(0f, freeX)
        shutterView.translationY = (shutterView.translationY + dy).coerceIn(0f, freeY)
    }

    private fun onDrop() {
        dragging = false
        val parent = shutterView.parent as? View ?: return
        val freeX = (parent.width - shutterView.width).toFloat()
        val freeY = (parent.height - shutterView.height).toFloat()
        fractionX = if (freeX > 0f) shutterView.translationX / freeX else 0f
        fractionY = if (freeY > 0f) shutterView.translationY / freeY else 0f
        ScreenshotPreferences.saveShutterPosition(shutterView.context, fractionX, fractionY)
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
            // Posted, because a view that has been GONE has no measured size to place against
            // until the next layout pass has run.
            shutterView.post { place() }
        } else {
            // Held for the exit animation, then out of the way of the game's touches.
            handler.postDelayed({ if (!shutter) shutterView.visibility = View.GONE }, EXIT_MS)
        }
    }

    fun toggleShutter() = applyShutter(!shutter)

    /** Capture the frame the game is presenting, then report where it went. */
    fun take() {
        GameScreenshot.take(view.context, object : GameScreenshot.Callback {
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

/** How long after a drag a release still counts as the end of that drag rather than a tap. */
private const val DRAG_CLICK_GUARD_MS = 300L

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
private fun Shutter(
    visible: Boolean,
    diameter: Dp,
    onCapture: () -> Unit,
    onGrab: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onDrop: () -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.7f) +
                fadeIn(tween(180)),
        exit = scaleOut(tween(180, easing = FastOutSlowInEasing), targetScale = 0.7f) +
                fadeOut(tween(160))
    ) {
        Column(
            // Padding all round rather than against one edge: it can be dropped anywhere now, so
            // there is no edge it belongs to.
            Modifier.padding(8.dp),
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
            var held by remember { mutableStateOf(false) }
            val inner by animateFloatAsState(
                targetValue = if (pressed) 0.74f else 1f,
                animationSpec = tween(140, easing = FastOutSlowInEasing),
                label = "shutterPress"
            )
            // Lifts while it is being carried, so it is obvious the thing under the finger is now
            // moving rather than about to fire.
            val lift by animateFloatAsState(
                targetValue = if (held) 1.12f else 1f,
                animationSpec = tween(140, easing = FastOutSlowInEasing),
                label = "shutterLift"
            )
            val haptics = LocalHapticFeedback.current
            // When the drag last did anything.
            //
            // `clickable` fires on the release whatever came before it — there is no time limit on
            // a tap — so a long press to move the shutter would also take a picture on the way
            // out. Any release within a moment of dragging is the end of a drag, not a tap.
            var lastDrag by remember { mutableStateOf(0L) }
            Box(
                Modifier
                    .size(diameter)
                    .scale(lift)
                    .clip(CircleShape)
                    // Faintly filled rather than transparent, so the ring still reads against a
                    // bright sky — the same problem the filled control glyphs exist for.
                    .background(colors.scrim.copy(alpha = 0.35f))
                    .border(
                        2.5.dp,
                        if (held) colors.primary else colors.onSurface.copy(alpha = 0.9f),
                        CircleShape
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            if (SystemClock.uptimeMillis() - lastDrag > DRAG_CLICK_GUARD_MS) {
                                onCapture()
                            }
                        },
                        onClickLabel = stringResource(R.string.control_center_screenshot)
                    )
                    // Move on a long press, not on a plain drag. A shutter is jabbed at during a
                    // fight and a drag threshold would send it wandering; a long press is also
                    // what everything else in the launcher uses for a second meaning.
                    .pointerInput(Unit) {
                        var grab = Offset.Zero
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                grab = it
                                held = true
                                lastDrag = SystemClock.uptimeMillis()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onGrab()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                lastDrag = SystemClock.uptimeMillis()
                                val offset = change.position - grab
                                onMove(offset.x, offset.y)
                            },
                            onDragEnd = {
                                held = false
                                lastDrag = SystemClock.uptimeMillis()
                                onDrop()
                            },
                            onDragCancel = {
                                held = false
                                lastDrag = SystemClock.uptimeMillis()
                                onDrop()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // The disc keeps its share of whatever diameter was chosen, so the ring stays a
                // ring at every size rather than closing up at the small one.
                Box(
                    Modifier
                        .size(diameter * 0.72f * inner)
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
