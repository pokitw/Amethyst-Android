package net.kdt.pojavlaunch.ui.game

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.Amethyst50
import net.kdt.pojavlaunch.ui.theme.Amethyst70
import net.kdt.pojavlaunch.ui.theme.RecordingRed
import net.kdt.pojavlaunch.ui.theme.SlotWell
import java.util.Locale

/** What the control center shows about the running recording. */
data class RecordingUiState(
    val active: Boolean = false,
    val elapsedMs: Long = 0L,
    val bytes: Long = 0L,
    val maxBytes: Long = 1L,
    /** Resolution, frame rate and audio, as the settings currently have them. */
    val summary: String = ""
)

/**
 * Everything the control center can do, implemented by the game activity.
 *
 * Kept as an interface rather than a bag of lambdas because the caller is Java, and because every
 * one of these already exists there — the sheet only changes how they are reached.
 */
interface ControlCenterCallbacks {
    fun onToggleRecording()

    /** Take one now, of whatever the game is presenting behind this sheet. */
    fun onScreenshot()

    /** Show or hide the shutter that floats over the running game. */
    fun onToggleShutter()
    fun onCustomControls()
    fun onSendKeycode()
    fun onQuickSettings()
    fun onLogOutput()
    fun onForceClose()

    fun onEditorAddButton()
    fun onEditorAddDrawer()
    fun onEditorAddJoystick()
    fun onEditorLoad()
    fun onEditorSave()
    fun onEditorTest()
    fun onEditorSetDefault()
    fun onEditorShare()
    fun onEditorExit()

    /** How long the running recording has lasted, or zero when nothing is recording. */
    fun recordingElapsedMs(): Long

    /** Roughly how much the running recording has written so far, in bytes. */
    fun recordingBytes(): Long
}

/**
 * The in-game control center.
 *
 * It replaces a 200dp right-edge drawer of plain text rows. Three things drove the shape: the
 * actions are reached with a thumb while the other hand holds the device, so they come up from
 * the bottom rather than in from the right; recording is the feature this build exists for, so it
 * is a card at the top rather than the sixth row of a list; and force close is destructive and
 * rare, so it stops being the very first thing under your finger.
 */
@Composable
fun ControlCenter(
    visible: Boolean,
    editorMode: Boolean,
    canTest: Boolean,
    recording: RecordingUiState,
    shutterOn: Boolean,
    callbacks: ControlCenterCallbacks,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                    // No ripple: this is a dismissal target, not a control.
                    .clickable(interaction, indication = null, onClick = onDismiss)
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it } +
                    fadeIn(tween(180)),
            exit = slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it } +
                    fadeOut(tween(200))
        ) {
            Sheet(editorMode, canTest, recording, shutterOn, callbacks)
        }
    }
}

@Composable
private fun Sheet(
    editorMode: Boolean,
    canTest: Boolean,
    recording: RecordingUiState,
    shutterOn: Boolean,
    callbacks: ControlCenterCallbacks
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // The game is landscape, so the sheet has width to spare and almost no height. Left to
        // stack, the card, the capture row, the tiles and force close come to more than a phone
        // is tall and the sheet covers the whole screen — which is the one thing a sheet over a
        // running game must not do. Past 600dp of width it lays out in two columns instead, and
        // the cap is raised to let them be real columns rather than two narrow ones.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val twoColumns = maxWidth >= 600.dp
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 14.dp)
                    // A backstop, not the plan: on a screen too short even for the two-column
                    // layout the sheet stops here and scrolls, so nothing is ever unreachable.
                    .heightIn(max = screenHeight * 0.8f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .width(34.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Spacer(Modifier.height(12.dp))
                Column(Modifier.widthIn(max = if (twoColumns) 900.dp else 620.dp)) {
                    when {
                        editorMode -> EditorLayout(twoColumns, canTest, callbacks)
                        twoColumns -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RecordingCard(recording, callbacks::onToggleRecording)
                                CaptureCard(
                                    shutterOn,
                                    callbacks::onScreenshot,
                                    callbacks::onToggleShutter
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                GameActions(callbacks)
                                ForceClose(callbacks::onForceClose)
                            }
                        }
                        else -> Column {
                            RecordingCard(recording, callbacks::onToggleRecording)
                            Spacer(Modifier.height(10.dp))
                            CaptureCard(
                                shutterOn,
                                callbacks::onScreenshot,
                                callbacks::onToggleShutter
                            )
                            Spacer(Modifier.height(12.dp))
                            GameActions(callbacks)
                            ForceClose(callbacks::onForceClose)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Recording, as the thing this screen is mostly for.
 *
 * Idle it carries the only gradient on the sheet and says what you are about to get. Live it drops
 * the gradient entirely for the timer, the size against the cap, and a stop button — in a red that
 * is deliberately outside the accent ramp, so a running recording can never be mistaken for
 * ordinary chrome.
 */
@Composable
private fun RecordingCard(state: RecordingUiState, onToggle: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    if (!state.active) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.linearGradient(listOf(Amethyst70, Amethyst50)))
                .clickable(onClick = onToggle)
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlotWell(color = Amethyst20.copy(alpha = 0.22f)) {
                Icon(
                    painterResource(R.drawable.ic_x_recordings),
                    contentDescription = null,
                    tint = Amethyst20,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.control_start_recording),
                    style = MaterialTheme.typography.titleLarge,
                    color = Amethyst20
                )
                if (state.summary.isNotEmpty()) {
                    Text(
                        state.summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = Amethyst20.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        return
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlotWell(color = RecordingRed.copy(alpha = 0.14f)) { LiveDot(11.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                formatElapsed(state.elapsedMs),
                // Tabular figures, so the timer does not jitter as the digits change.
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(
                    R.string.control_center_size,
                    Formatter.formatShortFileSize(LocalContext.current, state.bytes),
                    Formatter.formatShortFileSize(LocalContext.current, state.maxBytes)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            CapacityBar(state.bytes, state.maxBytes)
        }
        Spacer(Modifier.width(14.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(RecordingRed)
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 9.dp)
        ) {
            Text(
                stringResource(R.string.control_center_stop),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }
}

/**
 * Stills, for when a video is more than you wanted. One card, two ways in.
 *
 * <b>Both, because they answer different questions.</b> The row itself takes the picture now,
 * which is what you want when the thing worth keeping is already on screen and you only came in
 * here to say so. The button on the end puts a shutter over the running game, which is what you
 * want when you need to see the shot before taking it — this sheet is covering the very thing
 * being photographed, and no arrangement of it will ever fix that.
 *
 * One card rather than two rows because they are one subject, and because vertical space in
 * landscape is the thing this sheet has least of.
 *
 * Quiet on purpose. The gradient above is the sheet's one bold element and this sits directly
 * underneath, so it borrows the grouping without competing for it.
 */
@Composable
private fun CaptureCard(
    shutterOn: Boolean,
    onScreenshot: () -> Unit,
    onToggleShutter: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerLow)
            .clickable(onClick = onScreenshot)
            .padding(start = 15.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlotWell {
            Icon(
                painterResource(R.drawable.ic_x_camera),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.control_center_screenshot),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Text(
                stringResource(
                    if (shutterOn) R.string.control_center_shutter_hint_on
                    else R.string.control_center_shutter_hint
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        // Its own target inside the row, and filled when it is on, because it is the one control
        // here whose state you cannot see from the sheet — the thing it turns on is behind it.
        Box(
            Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(if (shutterOn) colors.primary else colors.surfaceContainerHigh)
                .clickable(
                    onClick = onToggleShutter,
                    onClickLabel = stringResource(R.string.control_center_shutter)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_x_shutter),
                contentDescription = stringResource(R.string.control_center_shutter),
                tint = if (shutterOn) Amethyst20 else colors.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/** How full the file is against the size it will be closed off at. */
@Composable
private fun CapacityBar(bytes: Long, maxBytes: Long) {
    val fraction = if (maxBytes <= 0L) 0f else (bytes.toFloat() / maxBytes).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                .height(3.dp)
                .clip(CircleShape)
                .background(RecordingRed)
        )
    }
}

/** Slow, shallow pulse. Enough to read as live from the corner of the eye, not enough to nag. */
@Composable
private fun LiveDot(size: androidx.compose.ui.unit.Dp) {
    val alpha by rememberInfiniteTransition(label = "liveDot").animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "liveDotAlpha"
    )
    Box(
        Modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(RecordingRed)
    )
}

/**
 * The hotbar.
 *
 * Four equal actions in a row of equal slots, which is the one place a Minecraft reference is
 * also simply the right control: it is reachable by either thumb in landscape, and the shape is
 * one every player's hands already know.
 */
@Composable
private fun GameActions(callbacks: ControlCenterCallbacks) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile(
            R.drawable.ic_x_controls, R.string.control_center_tile_controls,
            callbacks::onCustomControls
        )
        ActionTile(
            R.drawable.ic_x_keycode, R.string.control_center_tile_keycode,
            callbacks::onSendKeycode
        )
        ActionTile(R.drawable.ic_x_tune, R.string.quick_setting_title, callbacks::onQuickSettings)
        ActionTile(R.drawable.ic_x_log, R.string.control_viewout, callbacks::onLogOutput)
    }
}

/**
 * The editor's half of the sheet.
 *
 * Six tiles in one row where there is room for them, two rows of three where there is not. The
 * banner stays across the top either way — it is a sentence, and a sentence in a column half the
 * width would wrap to four lines and cost more height than the row it saved.
 */
@Composable
private fun EditorLayout(
    twoColumns: Boolean,
    canTest: Boolean,
    callbacks: ControlCenterCallbacks
) {
    Column {
        EditorBanner(callbacks::onEditorShare, callbacks::onEditorExit)
        // A row of its own rather than a seventh tile, on the same reasoning that makes recording
        // the card at the top of the in-game sheet (14): trying the layout out is what the editor
        // is for, and a grid of seven equal squares would say it was one of seven equal chores.
        // It also keeps the grid at two rows of three, which is what fits a phone in landscape.
        if (canTest) {
            Spacer(Modifier.height(10.dp))
            TestRow(callbacks::onEditorTest)
        }
        Spacer(Modifier.height(12.dp))
        if (twoColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { EditorTiles(callbacks) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(
                        R.drawable.ic_x_add_button, R.string.customctrl_addbutton,
                        callbacks::onEditorAddButton
                    )
                    ActionTile(
                        R.drawable.ic_x_add_drawer, R.string.control_center_add_drawer,
                        callbacks::onEditorAddDrawer
                    )
                    ActionTile(
                        R.drawable.ic_x_joystick, R.string.customctrl_addbutton_joystick,
                        callbacks::onEditorAddJoystick
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(R.drawable.ic_x_files, R.string.global_load, callbacks::onEditorLoad)
                    ActionTile(
                        R.drawable.ic_x_install, R.string.global_save, callbacks::onEditorSave
                    )
                    ActionTile(
                        R.drawable.ic_x_star, R.string.control_center_set_default,
                        callbacks::onEditorSetDefault
                    )
                }
            }
        }
    }
}

/**
 * Try the layout out, for real, without a game.
 *
 * The one thing the editor could never answer for itself: whether the buttons you have arranged
 * actually do what you meant, land where your thumbs are, and leave the game's own hotbar alone.
 */
@Composable
private fun TestRow(onTest: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerLow)
            .clickable(onClick = onTest)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlotWell(color = Amethyst70.copy(alpha = 0.14f)) {
            // A core Material glyph, which the handbook allows for the universal ones (9), and
            // play is as universal as they come.
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.control_center_test),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Text(
                stringResource(R.string.control_center_test_hint),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RowScope.EditorTiles(callbacks: ControlCenterCallbacks) {
    ActionTile(
        R.drawable.ic_x_add_button, R.string.customctrl_addbutton, callbacks::onEditorAddButton
    )
    ActionTile(
        R.drawable.ic_x_add_drawer, R.string.control_center_add_drawer, callbacks::onEditorAddDrawer
    )
    ActionTile(
        R.drawable.ic_x_joystick, R.string.customctrl_addbutton_joystick,
        callbacks::onEditorAddJoystick
    )
    ActionTile(R.drawable.ic_x_files, R.string.global_load, callbacks::onEditorLoad)
    ActionTile(R.drawable.ic_x_install, R.string.global_save, callbacks::onEditorSave)
    ActionTile(
        R.drawable.ic_x_star, R.string.control_center_set_default, callbacks::onEditorSetDefault
    )
}

@Composable
private fun RowScope.ActionTile(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SlotWell {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EditorBanner(onShare: () -> Unit, onExit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlotWell(color = Amethyst70.copy(alpha = 0.14f)) {
            Icon(
                painterResource(R.drawable.ic_x_controls),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.control_center_editing_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.control_center_editing_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        // Sharing a layout is a real action but a rare one, so it is an icon beside the way out
        // rather than a seventh tile competing with the six you use while actually editing.
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onShare),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_x_share),
                contentDescription = stringResource(R.string.control_center_share),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onExit)
                .padding(horizontal = 20.dp, vertical = 9.dp)
        ) {
            Text(
                stringResource(R.string.control_center_exit_editor),
                style = MaterialTheme.typography.labelLarge,
                color = Amethyst20
            )
        }
    }
}

/**
 * Force close.
 *
 * It used to be the first row of the menu, one mis-tap from the edge of the screen, mid-game. Last,
 * quiet, and in the error colour is where an irreversible action belongs; the confirmation dialog
 * behind it is unchanged.
 */
@Composable
private fun ForceClose(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.control_forceclose),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

/** The recording pill that sits beside the pull tab while the sheet is closed. */
@Composable
fun RecordingPill(elapsedMs: Long) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.82f))
            .padding(start = 8.dp, top = 4.dp, end = 11.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveDot(7.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            formatElapsed(elapsedMs),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = Color.White
        )
    }
}

/** `MM:SS`, or `H:MM:SS` once a recording has been running for an hour. */
private fun formatElapsed(elapsedMs: Long): String {
    val total = elapsedMs / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
