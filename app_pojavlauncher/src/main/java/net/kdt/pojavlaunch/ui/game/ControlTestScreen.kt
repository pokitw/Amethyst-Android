package net.kdt.pojavlaunch.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.controls.keyDisplayName
import net.kdt.pojavlaunch.ui.theme.Amethyst70
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * What the test session shows, and the whole of what it can say.
 *
 * @param held    the keycodes currently down, in the order they went down
 * @param last    the most recent press, for when nothing is held and the strip would be empty
 * @param playing whether the layout is being shown as it would look in play or with a GUI open
 */
@Immutable
class ControlTestState(
    val held: List<Int> = emptyList(),
    val last: String = "",
    val playing: Boolean = true,
    val guides: Boolean = true,
    val expanded: Boolean = true
)

/**
 * The panel that floats over a layout being tested.
 *
 * <b>It is deliberately `wrap_content` and pinned to the top</b>, not stretched over the screen.
 * A full-size view here would sit in front of every touch the buttons underneath are waiting for
 * (12.9), which would make the one screen whose entire purpose is pressing those buttons the one
 * screen where they could not be pressed. That also means it covers whatever control happens to be
 * top centre, which is why it collapses to a pill.
 */
@Composable
fun ControlTestPanel(
    state: ControlTestState,
    onTogglePlaying: () -> Unit,
    onToggleGuides: () -> Unit,
    onToggleExpanded: () -> Unit,
    onDone: () -> Unit
) {
    AmethystXTheme {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .padding(top = 10.dp)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainer.copy(alpha = 0.94f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Amethyst70)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(R.string.control_test_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface
                )
                Spacer(Modifier.width(14.dp))
                PanelChip(
                    label = stringResource(
                        if (state.playing) R.string.control_test_playing
                        else R.string.control_test_menu
                    ),
                    on = true,
                    onClick = onTogglePlaying
                )
                Spacer(Modifier.width(7.dp))
                PanelChip(
                    label = stringResource(R.string.control_test_guides),
                    on = state.guides,
                    onClick = onToggleGuides
                )
                Spacer(Modifier.width(7.dp))
                IconChip(
                    icon = if (state.expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    onClick = onToggleExpanded
                )
                Spacer(Modifier.width(7.dp))
                PanelChip(
                    label = stringResource(R.string.control_test_done),
                    on = true,
                    accent = true,
                    onClick = onDone
                )
            }

            AnimatedVisibility(
                visible = state.expanded,
                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                exit = fadeOut(tween(110)) + shrinkVertically(tween(160))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    if (state.held.isEmpty()) {
                        val waiting = stringResource(R.string.control_test_waiting)
                        Text(
                            if (state.last.isEmpty()) waiting else state.last,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        // Only what is down right now. A scrolling log was drafted and dropped:
                        // the question being asked of this panel is "did that button send what I
                        // bound it to", which is answered by the press itself, and a history that
                        // grows while you hold a key would push the panel over the controls.
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.held.take(6).forEach { keycode ->
                                HeldKey(keyDisplayName(keycode))
                            }
                            if (state.held.size > 6) HeldKey("+${state.held.size - 6}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelChip(
    label: String,
    on: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = when {
            accent -> colors.onPrimaryContainer
            on -> colors.primary
            else -> colors.onSurfaceVariant
        },
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                when {
                    accent -> colors.primaryContainer
                    on -> Amethyst70.copy(alpha = 0.14f)
                    else -> colors.surfaceContainerHighest
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    )
}

@Composable
private fun IconChip(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** One key that is down, named the way the editor names it rather than by its number. */
@Composable
private fun HeldKey(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
