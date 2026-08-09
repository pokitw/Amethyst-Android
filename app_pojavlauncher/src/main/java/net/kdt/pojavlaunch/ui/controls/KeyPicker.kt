package net.kdt.pojavlaunch.ui.controls

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_SPACE
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.game.Key
import net.kdt.pojavlaunch.ui.game.KeyKind
import net.kdt.pojavlaunch.ui.game.boardRows
import net.kdt.pojavlaunch.ui.game.bottomRow

/**
 * Choose what a control sends by pressing it on a keyboard.
 *
 * This replaces a spinner: one flat alphabetical list of a hundred and nineteen entries with names
 * like `NUMPAD_SUBTRACT` and `SPECIAL_FWD`, scrolled one thumb-flick at a time, that asked the
 * player to know the launcher's name for a key before they could bind it. Everyone who has ever
 * wanted to bind Shift already knows where Shift is. So the board *is* the picker: press the key
 * you want that button to press.
 *
 * The board comes straight from the on-screen keyboard — the same [Key] tables, the same layout,
 * the same silhouette. That is deliberate beyond saving code: the keyboard is where a player has
 * already learned where these caps are, and a picker that rearranged them would throw that away.
 *
 * What it does *not* do is latch, shift or type. A cap here reports its keycode and nothing else,
 * which is why this is a separate surface rather than the keyboard in a different mood.
 */
@Composable
fun KeyPicker(
    slot: Int,
    current: Int,
    onPick: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var specials by remember { mutableStateOf(current < 0) }
    // Hoisted out of the board so a trip through the actions tab and back does not silently put
    // the player on the letters again after they went looking for the numpad.
    var numeric by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Sized to what is left rather than to a fixed number, exactly as the keyboard is, so the
        // two surfaces are the same height on the same device.
        val rowHeight = ((maxHeight * BOARD_SHARE - CHROME_HEIGHT) / TOTAL_ROWS)
            .coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            // Hit-testable but handling nothing: a tap on the board's chrome must not reach the
            // scrim behind it and dismiss the very picker it landed on.
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {}
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp)
                    .padding(top = 10.dp, bottom = 12.dp)
            ) {
                PickerHeader(
                    slot = slot,
                    current = current,
                    specials = specials,
                    onToggleSpecials = { specials = it },
                    onClear = onClear,
                    onDismiss = onDismiss
                )
                Spacer(Modifier.height(10.dp))
                Crossfade(specials, animationSpec = tween(180), label = "pickerMode") { actions ->
                    if (actions) SpecialGrid(current, onPick)
                    else PickerBoard(current, numeric, rowHeight, onPick) { numeric = !numeric }
                }
            }
        }
    }
}

/** Which slot is being filled, what is in it, and the two ways out of the board. */
@Composable
private fun PickerHeader(
    slot: Int,
    current: Int,
    specials: Boolean,
    onToggleSpecials: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                stringResource(
                    if (slot == 0) R.string.control_editor_pick_first
                    else R.string.control_editor_pick_extra
                ),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Text(
                if (current == NO_KEY) stringResource(R.string.control_editor_pick_empty)
                else stringResource(R.string.control_editor_pick_current, keyDisplayName(current)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        SegmentedPair(
            leftLabel = stringResource(R.string.control_editor_pick_keys),
            rightLabel = stringResource(R.string.control_editor_pick_actions),
            rightSelected = specials,
            onSelect = onToggleSpecials
        )
        if (current != NO_KEY) {
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.control_editor_pick_clear),
                style = MaterialTheme.typography.labelLarge,
                color = colors.error,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.control_editor_close),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Two mutually exclusive labels in one well, for a choice with exactly two answers. */
@Composable
private fun SegmentedPair(
    leftLabel: String,
    rightLabel: String,
    rightSelected: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (isRight in listOf(false, true)) {
            val selected = isRight == rightSelected
            Text(
                if (isRight) rightLabel else leftLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) colors.primary.copy(alpha = 0.20f) else colors.surfaceContainerHighest)
                    .clickable { onSelect(isRight) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun PickerBoard(
    current: Int,
    numeric: Boolean,
    rowHeight: Dp,
    onPick: (Int) -> Unit,
    onSwitchBoard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        // The numpad, the navigation cluster and the right-hand modifiers are on the second board,
        // reached by the same "123" cap that reaches them on the keyboard. Every key the old
        // spinner offered is on one of the two.
        for (row in boardRows(numeric)) PickRow(row, rowHeight, current, onPick, onSwitchBoard)
        Spacer(Modifier.height(KEY_GAP))
        PickRow(bottomRow(numeric), rowHeight, current, onPick, onSwitchBoard)
    }
}

@Composable
private fun PickRow(
    row: List<Key>,
    rowHeight: Dp,
    current: Int,
    onPick: (Int) -> Unit,
    onSwitchBoard: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        for (key in row) {
            when (key.kind) {
                KeyKind.SPACER -> Spacer(Modifier.weight(key.weight))
                // Binds nothing; it is the way to the other board, exactly as on the keyboard.
                KeyKind.LAYOUT ->
                    PickCap(key, current, Modifier.weight(key.weight)) { onSwitchBoard() }
                else -> PickCap(key, current, Modifier.weight(key.weight)) { onPick(key.code.toInt()) }
            }
        }
    }
}

@Composable
private fun PickCap(key: Key, current: Int, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bound = key.kind != KeyKind.LAYOUT && current == key.code.toInt()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(140),
        label = "pickCapScale"
    )
    val colors = MaterialTheme.colorScheme
    val background = when {
        pressed -> colors.primary.copy(alpha = 0.32f)
        bound -> colors.primary.copy(alpha = 0.20f)
        key.kind == KeyKind.LAYOUT -> colors.surfaceContainerHighest
        key.kind == KeyKind.CHARACTER -> colors.surfaceContainerHigh
        else -> colors.surfaceContainerLow
    }
    val foreground = when {
        pressed || bound -> colors.primary
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
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (key.code == GLFW_KEY_SPACE) {
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(foreground.copy(alpha = 0.45f))
            )
        } else {
            Text(
                key.label,
                style = when {
                    key.label.length <= 1 -> MaterialTheme.typography.titleMedium
                    key.label.length <= 3 -> MaterialTheme.typography.labelLarge
                    else -> MaterialTheme.typography.labelMedium
                },
                color = foreground,
                maxLines = 1
            )
        }
    }
}

/**
 * The things a control can do that no key does — the mouse buttons above all.
 *
 * A row rather than a grid because it sits where the board would, and the board is wide and short.
 * Each one carries the icon the button will actually wear, so the choice is made against what it
 * will look like rather than against a word for it.
 */
@Composable
private fun SpecialGrid(current: Int, onPick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(specialActions(), key = { it.keycode }) { action ->
            val selected = action.keycode == current
            Column(
                Modifier
                    .widthIn(min = 92.dp, max = 116.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) colors.primary.copy(alpha = 0.16f)
                        else colors.surfaceContainerHigh
                    )
                    .clickable { onPick(action.keycode) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (action.glyphRes != 0) {
                        Icon(
                            painterResource(action.glyphRes),
                            contentDescription = null,
                            tint = if (selected) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            action.shortName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) colors.primary else colors.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    stringResource(action.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.primary else colors.onSurface,
                    maxLines = 2
                )
            }
        }
    }
}

private val KEY_SHAPE = RoundedCornerShape(9.dp)
private val KEY_GAP = 4.dp
private val MIN_ROW_HEIGHT = 26.dp
private val MAX_ROW_HEIGHT = 44.dp
private const val TOTAL_ROWS = 6
private val CHROME_HEIGHT = 126.dp
private const val BOARD_SHARE = 0.82f
