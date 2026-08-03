package net.kdt.pojavlaunch.ui.game

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_SPACE
import net.kdt.pojavlaunch.R

/**
 * The on-screen keyboard, for sending a key the control layout has no button for.
 *
 * It replaces an alphabetical `AlertDialog` list of 106 entries named things like `DPAD_UP` and
 * `NUMPAD_SUBTRACT`, which asked the player to know Android's name for a key before they could
 * press it, dismissed itself after every single one, and could not send two keys at once at all.
 *
 * Three things follow from being a keyboard rather than a list. Keys sit where the hands already
 * expect them, so finding one is recognition rather than reading. It stays up, so pressing F3
 * twice is two taps rather than two trips through a menu. And because any cap can be held, the
 * combinations the game actually asks for — F3 + G, Ctrl + Q, Shift held through a menu — are
 * reachable for the first time.
 *
 * It is drawn inline over the game rather than as a dialog, for the same reason the control center
 * around it is: a real dialog window risks dropping the game out of immersive fullscreen. The
 * scrim behind it is deliberately lighter than the control center's, because the point of pressing
 * F3 is to watch what F3 did.
 */
@Composable
fun KeyboardPanel(state: GameKeyboardState, onClose: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Sized to what is left rather than to a fixed number, so the board is comfortable on a
        // tablet and still leaves a strip of game visible on a short phone.
        val rowHeight = ((maxHeight * BOARD_SHARE - CHROME_HEIGHT) / TOTAL_ROWS)
            .coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT)

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .width(34.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Spacer(Modifier.height(10.dp))
                // Wider than this and the far keys stop being reachable with a thumb.
                Column(Modifier.widthIn(max = 1000.dp)) {
                    Header(state, onClose)
                    Spacer(Modifier.height(10.dp))
                    Crossfade(
                        targetState = state.numeric,
                        animationSpec = tween(160),
                        label = "keyboardBoard"
                    ) { numeric ->
                        Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                            for (row in boardRows(numeric)) KeyRow(row, rowHeight, state)
                        }
                    }
                    Spacer(Modifier.height(KEY_GAP))
                    // Outside the fade on purpose: the row that does not change should not blink.
                    KeyRow(bottomRow(state.numeric), rowHeight, state)
                }
            }
        }
    }
}

/**
 * The title, what is currently held down, and the way out.
 *
 * The held keys are the whole reason this row exists. A latched Ctrl is invisible state inside the
 * game, and invisible state that changes what the next tap does is the kind that turns into a bug
 * report. Each one is a chip that lets its key back up when tapped, so undoing a latch never means
 * hunting for the cap that set it.
 */
@Composable
private fun Header(state: GameKeyboardState, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.control_keyboard_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val held = state.held
            if (held.isEmpty()) {
                Text(
                    stringResource(R.string.control_keyboard_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (code in held.sorted()) HeldChip(code, state::releaseKey)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .keyTouch(onTap = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.control_keyboard_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** One latched key, named, and tappable to let it up. */
@Composable
private fun HeldChip(code: Short, onRelease: (Short) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .keyTouch(onTap = { onRelease(code) })
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            keyLabel(code),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun KeyRow(row: List<Key>, rowHeight: Dp, state: GameKeyboardState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        for (key in row) {
            if (key.kind == KeyKind.SPACER) Spacer(Modifier.weight(key.weight))
            else KeyCap(key, state, Modifier.weight(key.weight))
        }
    }
}

/**
 * One cap.
 *
 * The press response is a scale inside a `graphicsLayer` rather than a ripple: a ripple on a cap
 * this small reads as a smudge, and scaling in the layer redraws without laying the row out again,
 * which matters when a fast player is producing several of these a second.
 */
@Composable
private fun KeyCap(key: Key, state: GameKeyboardState, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val held = key.code in state.held
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(140),
        label = "keyCapScale"
    )

    val colors = MaterialTheme.colorScheme
    val background = when {
        pressed -> colors.primary.copy(alpha = 0.32f)
        held -> colors.primary.copy(alpha = 0.20f)
        key.kind == KeyKind.LAYOUT -> colors.surfaceContainerHighest
        key.kind == KeyKind.CHARACTER -> colors.surfaceContainerHigh
        else -> colors.surfaceContainerLow
    }
    val foreground = when {
        pressed || held -> colors.primary
        key.kind == KeyKind.CHARACTER -> colors.onSurface
        else -> colors.onSurfaceVariant
    }

    Box(
        modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(KEY_SHAPE)
            .background(background)
            .keyTouch(
                interaction = interaction,
                onTap = { state.tap(key) },
                onHold = { state.longPress(key) }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (key.code == GLFW_KEY_SPACE) {
            // A legend on the space bar would only be clutter; the bar itself is the legend.
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(foreground.copy(alpha = 0.45f))
            )
        } else {
            val legend = state.legend(key)
            Text(
                legend,
                style = when {
                    legend.length <= 1 -> MaterialTheme.typography.titleMedium
                    legend.length <= 3 -> MaterialTheme.typography.labelLarge
                    else -> MaterialTheme.typography.labelMedium
                },
                color = foreground,
                maxLines = 1
            )
        }
    }
}

/**
 * Tap and hold on one target, with the tick a keyboard is expected to give back.
 *
 * `indication` is left null on purpose — every caller draws its own press state — but the
 * interaction source is still wired up, so the press is there to animate against and the target
 * keeps the button semantics a screen reader needs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.keyTouch(
    interaction: MutableInteractionSource? = null,
    onTap: () -> Unit,
    onHold: (() -> Unit)? = null
): Modifier {
    val view = LocalView.current
    // Remembered unconditionally: a `remember` reached only down one branch would give the call
    // site two different shapes in the slot table.
    val fallback = remember { MutableInteractionSource() }
    val longClick: (() -> Unit)? = onHold?.let { hold ->
        {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            hold()
        }
    }
    return combinedClickable(
        interactionSource = interaction ?: fallback,
        indication = null,
        onLongClick = longClick,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onTap()
        }
    )
}

/** Squarish, like the slot wells everywhere else, but small enough to read as a key. */
private val KEY_SHAPE = RoundedCornerShape(9.dp)

private val KEY_GAP = 4.dp
private val MIN_ROW_HEIGHT = 26.dp
private val MAX_ROW_HEIGHT = 44.dp

/** Five rows of board, plus the one that never changes. */
private const val TOTAL_ROWS = 6

/** Handle, header, gaps, padding and the navigation bar — everything that is not a key row. */
private val CHROME_HEIGHT = 126.dp

/** How much of the screen the keyboard may take, so the rest of the game stays visible. */
private const val BOARD_SHARE = 0.82f
