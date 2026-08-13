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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.controls.keyDisplayName
import net.kdt.pojavlaunch.ui.theme.Amethyst70
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.ui.theme.Danger70
import net.kdt.pojavlaunch.ui.theme.Success70
import java.util.Locale

/** Which of the three things the panel is showing under its header. */
enum class PanelMode { KEYS, LOG, LAYOUT }

@Immutable
class ControlTestState(
    val held: List<Int> = emptyList(),
    val log: List<ControlEvent> = emptyList(),
    val stats: ControlTestStats = ControlTestStats(),
    val report: LayoutReport = LayoutReport(),
    val playing: Boolean = true,
    val guides: Boolean = true,
    val panel: PanelMode = PanelMode.KEYS,
    val atBottom: Boolean = false
)

/**
 * The console that floats over a layout being tested.
 *
 * <b>It is `wrap_content` and pinned by gravity</b>, never stretched over the screen: a full-size
 * view here would sit in front of every touch the buttons underneath are waiting for (12.9), which
 * would make the one screen whose entire purpose is pressing those buttons the one screen where
 * they cannot be pressed. It covers whichever controls sit at its end, which is why it moves to
 * the other end on request rather than growing a drag nobody asked for.
 *
 * Three panels behind one header, because they answer three different questions and only one of
 * them is being asked at a time. **Keys** is the plain answer to "what does this button send" and
 * is what the session opens on. **Log** is the same thing with history, for a combination or a
 * repeat that is over before it can be read. **Layout** is the half that pressing cannot do at
 * all: keys bound twice, controls bound to nothing, controls off the screen or too small to hit.
 */
@Composable
fun ControlTestPanel(
    state: ControlTestState,
    onTogglePlaying: () -> Unit,
    onToggleGuides: () -> Unit,
    onPanel: (PanelMode) -> Unit,
    onMove: () -> Unit,
    onClearLog: () -> Unit,
    onDone: () -> Unit
) {
    AmethystXTheme {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .padding(vertical = 8.dp, horizontal = 8.dp)
                .widthIn(max = 620.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainer.copy(alpha = 0.95f))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            TestHeader(state, onTogglePlaying, onToggleGuides, onPanel, onMove, onDone)
            Spacer(Modifier.height(9.dp))
            when (state.panel) {
                PanelMode.KEYS -> KeysPanel(state)
                PanelMode.LOG -> LogPanel(state, onClearLog)
                PanelMode.LAYOUT -> LayoutPanel(state.report)
            }
        }
    }
}

@Composable
private fun TestHeader(
    state: ControlTestState,
    onTogglePlaying: () -> Unit,
    onToggleGuides: () -> Unit,
    onPanel: (PanelMode) -> Unit,
    onMove: () -> Unit,
    onDone: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Amethyst70)
        )
        Spacer(Modifier.width(9.dp))
        // The three panels as a segmented control, which is what they are: one of three, always
        // exactly one, and the chosen one carries the accent the way a selection does everywhere
        // else in the launcher.
        Segment(stringResource(R.string.control_test_tab_keys), state.panel == PanelMode.KEYS) {
            onPanel(PanelMode.KEYS)
        }
        Spacer(Modifier.width(5.dp))
        Segment(stringResource(R.string.control_test_tab_log), state.panel == PanelMode.LOG) {
            onPanel(PanelMode.LOG)
        }
        Spacer(Modifier.width(5.dp))
        Segment(
            stringResource(R.string.control_test_tab_layout),
            state.panel == PanelMode.LAYOUT,
            // A dot rather than a count: the number is on the panel, and the header only has to
            // say whether opening it is worth the tap.
            dot = !state.report.healthy
        ) { onPanel(PanelMode.LAYOUT) }

        Spacer(Modifier.width(12.dp))
        Chip(
            stringResource(
                if (state.playing) R.string.control_test_playing else R.string.control_test_menu
            ),
            on = true,
            onClick = onTogglePlaying
        )
        Spacer(Modifier.width(5.dp))
        Chip(stringResource(R.string.control_test_guides), state.guides, onClick = onToggleGuides)
        Spacer(Modifier.width(5.dp))
        Chip(
            stringResource(
                if (state.atBottom) R.string.control_test_move_up
                else R.string.control_test_move_down
            ),
            on = false,
            onClick = onMove
        )
        Spacer(Modifier.width(5.dp))
        Chip(stringResource(R.string.control_test_done), on = true, accent = true, onClick = onDone)
    }
}

/** What is down right now, big enough to read at arm's length, with the number underneath. */
@Composable
private fun KeysPanel(state: ControlTestState) {
    val colors = MaterialTheme.colorScheme
    Column {
        if (state.held.isEmpty()) {
            Text(
                stringResource(R.string.control_test_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.held.take(7).forEach { keycode -> HeldKey(keycode) }
                if (state.held.size > 7) {
                    Text(
                        "+${state.held.size - 7}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        TechLine(state)
    }
}

/**
 * The technical line, in the spirit of the game's own F3.
 *
 * Monospace so the numbers hold still while they change: a readout that shifts sideways with
 * every digit is unreadable in motion, which is the only state it is ever read in.
 */
@Composable
private fun TechLine(state: ControlTestState) {
    val stats = state.stats
    Text(
        buildString {
            append(if (state.playing) "aim" else "menu")
            if (stats.lastHeldMs >= 0) append("  held ").append(stats.lastHeldMs).append("ms")
            if (stats.perSecond > 0f) {
                append("  ")
                append(String.format(Locale.ROOT, "%.1f", stats.perSecond))
                append("/s")
            }
            if (stats.modifiers.isNotEmpty()) append("  ").append(stats.modifiers)
            append("  ev ").append(stats.events)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        maxLines = 1
    )
}

/**
 * Every edge, newest last, with the clock and the held time.
 *
 * A history was drafted out of the first version and is back for a reason: a slide-to-repeat
 * button at twenty presses a second, or a four-step sequence, is over before the live line can be
 * read, and "did all four steps fire, and how far apart" is exactly the question those features
 * make people ask.
 */
@Composable
private fun LogPanel(state: ControlTestState, onClearLog: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(state.log.size) {
        if (state.log.isNotEmpty()) listState.animateScrollToItem(state.log.size - 1)
    }
    Column {
        Box(Modifier.heightIn(max = 132.dp)) {
            if (state.log.isEmpty()) {
                Text(
                    stringResource(R.string.control_test_log_empty),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
            } else {
                LazyColumn(state = listState) {
                    items(state.log.size) { index ->
                        val event = state.log[index]
                        Text(
                            buildString {
                                append(String.format(Locale.ROOT, "%6.2f", event.atMs / 1000f))
                                append(if (event.down) "  v  " else "  ^  ")
                                append(event.name)
                                append(' ').append(event.keycode)
                                if (event.heldMs >= 0) {
                                    append("  ").append(event.heldMs).append("ms")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (event.down) colors.primary else colors.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(stringResource(R.string.control_test_clear), on = false, onClick = onClearLog)
            Spacer(Modifier.width(9.dp))
            TechLine(state)
        }
    }
}

/** What reading the layout found, and nothing about what has been pressed. */
@Composable
private fun LayoutPanel(report: LayoutReport) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            stringResource(
                R.string.control_test_layout_counts, report.controls, report.bound
            ),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(7.dp))
        if (report.healthy) {
            Finding(stringResource(R.string.control_test_layout_ok), good = true)
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (report.clashes.isNotEmpty()) {
                Finding(
                    stringResource(
                        R.string.control_test_layout_clash,
                        report.clashes.take(4).joinToString(", ")
                    )
                )
            }
            if (report.unbound > 0) {
                Finding(stringResource(R.string.control_test_layout_unbound, report.unbound))
            }
            if (report.offScreen > 0) {
                Finding(stringResource(R.string.control_test_layout_offscreen, report.offScreen))
            }
            if (report.tiny > 0) {
                Finding(stringResource(R.string.control_test_layout_tiny, report.tiny))
            }
            if (report.invisible > 0) {
                Finding(stringResource(R.string.control_test_layout_invisible, report.invisible))
            }
        }
    }
}

@Composable
private fun Finding(text: String, good: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (good) Success70 else Danger70)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One key that is down: what it is called, and the number a layout file would call it. */
@Composable
private fun HeldKey(keycode: Int) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.primaryContainer)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            keyDisplayName(keycode).ifEmpty { "?" },
            style = MaterialTheme.typography.titleSmall,
            color = colors.onPrimaryContainer,
            maxLines = 1
        )
        Text(
            keycode.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onPrimaryContainer.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, dot: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(
                if (selected) Amethyst70.copy(alpha = 0.16f) else colors.surfaceContainerHighest
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1
        )
        if (dot) {
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Danger70)
            )
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, accent: Boolean = false, onClick: () -> Unit) {
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
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
