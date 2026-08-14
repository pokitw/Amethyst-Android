package net.kdt.pojavlaunch.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData
import net.kdt.pojavlaunch.customcontrols.ControlSkin
import net.kdt.pojavlaunch.customcontrols.ControlGlyphs
import net.kdt.pojavlaunch.ui.settings.InfoRow
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.settings.SwitchRow
import java.util.Locale
import kotlin.math.roundToInt

/** What the panel can ask the layout to do to the control it is editing. */
interface ControlEditorActions {
    fun onDuplicate()
    fun onDelete()
    /** Only reachable on a drawer, which is the only control that can hold another. */
    fun onAddSubButton()
    fun onClose()
}

/**
 * Everything about one control, edited beside it.
 *
 * It replaces a panel of raw fields: stroke width in tenths of a dp behind a 0–100 seek bar, corner
 * radius as a bare percentage, four keycode spinners, and a colour picker in a second sliding
 * layer. Every one of those asked the player to hold a unit in their head. This asks them to look
 * at the button.
 *
 * The panel is narrow and hugs one edge on purpose — the same edge the old one used, and for the
 * same reason: the control being edited has to stay visible and draggable while it is edited, and
 * in landscape a sheet from the bottom would bury it. Which edge is decided by where the button is,
 * so the panel is never on top of it.
 *
 * Every change is live. There is no apply and no cancel, because the button behind the panel is the
 * preview, and a preview you have to press a button to see is not one.
 */
@Composable
fun ControlEditorPanel(
    state: ControlEditorState,
    onLeft: Boolean,
    actions: ControlEditorActions,
    onPickKey: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (onLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Surface(
            color = colors.surfaceContainer,
            shape = if (onLeft) RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
            else RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp),
            modifier = Modifier
                .width(PANEL_WIDTH)
                .fillMaxHeight()
                // Hit-testable but handling nothing, so a tap on a quiet part of the panel stops
                // here instead of falling through and dragging the control underneath it.
                .pointerInput(Unit) {}
        ) {
            Column(
                Modifier
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp, bottom = 20.dp)
            ) {
                PanelHeader(state, actions::onClose)

                if (state.keysEditable) {
                    SectionLabel(stringResource(R.string.control_editor_section_keys))
                    KeySlots(state, onPickKey)
                }

                if (state.kind == ControlKind.DRAWER) {
                    SectionLabel(stringResource(R.string.control_editor_section_drawer))
                    SettingsCard { OrientationRow(state) }
                }

                if (state.sizeEditable) {
                    SectionLabel(stringResource(R.string.control_editor_section_size))
                    SettingsCard {
                        LiveSlider(
                            title = stringResource(R.string.control_editor_width),
                            value = state.width,
                            range = ControlEditorState.MIN_SIZE..ControlEditorState.MAX_SIZE,
                            format = { "${it.roundToInt()} px" }
                        ) { state.applySize(it, state.height) }
                        if (state.kind != ControlKind.JOYSTICK) {
                            LiveSlider(
                                title = stringResource(R.string.control_editor_height),
                                value = state.height,
                                range = ControlEditorState.MIN_SIZE..ControlEditorState.MAX_SIZE,
                                format = { "${it.roundToInt()} px" }
                            ) { state.applySize(state.width, it) }
                        }
                    }
                }

                SectionLabel(stringResource(R.string.control_editor_section_look))
                SettingsCard {
                    LiveSlider(
                        title = stringResource(R.string.control_editor_opacity),
                        value = state.opacity,
                        range = 0f..1f,
                        format = { "${(it * 100).roundToInt()}%" },
                        onValueChange = state::applyOpacity
                    )
                    // Opacity stays above this line whatever the style is: it is applied as the
                    // view's own alpha and never went through the skin at all.
                    //
                    // The four below it did, and under a style that overrides the layout's
                    // colours they write the file and change nothing on screen — which has been
                    // true of the Pocket skin since it shipped, and is the thing a settings
                    // screen must never do. They are replaced by a line saying who is drawing.
                    if (ControlSkin.isPocket() || ControlSkin.isTextured()) {
                        InfoRow(
                            title = stringResource(R.string.control_editor_style_overridden),
                            description = stringResource(
                                if (ControlSkin.isTextured())
                                    R.string.control_editor_style_texture
                                else R.string.control_editor_style_pocket
                            )
                        )
                    } else {
                        if (state.cornerEditable) {
                            LiveSlider(
                                title = stringResource(R.string.control_editor_corners),
                                value = state.cornerRadius,
                                range = 0f..100f,
                                format = { "${it.roundToInt()}%" },
                                onValueChange = state::applyCornerRadius
                            )
                        }
                        LiveSlider(
                            title = stringResource(R.string.control_editor_border),
                            value = state.strokeWidth,
                            range = 0f..10f,
                            format = { String.format(Locale.getDefault(), "%.1f dp", it) },
                            onValueChange = state::applyStrokeWidth
                        )
                        ColorField(
                            title = stringResource(R.string.control_editor_fill),
                            color = state.fillColor,
                            alphaEnabled = true,
                            onColor = state::applyFillColor
                        )
                        ColorField(
                            title = stringResource(R.string.control_editor_border_colour),
                            color = state.strokeColor,
                            alphaEnabled = false,
                            onColor = state::applyStrokeColor
                        )
                    }
                }

                if (state.behaviourEditable) {
                    SectionLabel(stringResource(R.string.control_editor_section_behaviour))
                    SettingsCard {
                        SwitchRow(
                            stringResource(R.string.control_editor_toggle),
                            stringResource(R.string.control_editor_toggle_description),
                            state.isToggle,
                            onCheckedChange = state::applyToggle
                        )
                        SwitchRow(
                            stringResource(R.string.control_editor_sequence),
                            stringResource(R.string.control_editor_sequence_description),
                            state.sequence,
                            onCheckedChange = state::applySequence
                        )
                        // Only while the mode is on: a slider that does nothing is the one thing
                        // a settings surface must never show. The floor is one game tick, which
                        // is the least that makes two hotbar switches land as two.
                        if (state.sequence) {
                            LiveSlider(
                                title = stringResource(R.string.control_editor_sequence_gap),
                                value = state.sequenceGap.toFloat(),
                                range = 50f..300f,
                                format = { "${it.roundToInt()} ms" },
                                onValueChange = { state.applySequenceGap(it.roundToInt()) }
                            )
                        }
                        SwitchRow(
                            stringResource(R.string.control_editor_slide_repeat),
                            stringResource(R.string.control_editor_slide_repeat_description),
                            state.slideRepeat,
                            onCheckedChange = state::applySlideRepeat
                        )
                        if (state.slideRepeat) {
                            LiveSlider(
                                title = stringResource(R.string.control_editor_slide_distance),
                                value = state.slideDistance,
                                range = 8f..64f,
                                format = { "${it.roundToInt()} dp" },
                                onValueChange = state::applySlideDistance
                            )
                            // Presses a second as well as milliseconds, because that is the
                            // number a player has an opinion about; the floor is one game tick,
                            // which is as fast as Minecraft can see a click at all.
                            LiveSlider(
                                title = stringResource(R.string.control_editor_repeat_speed),
                                value = state.repeatGap.toFloat(),
                                range = 50f..500f,
                                format = {
                                    val ms = it.roundToInt()
                                    "$ms ms, ${(1000f / ms).roundToInt()} a second"
                                },
                                onValueChange = { state.applyRepeatGap(it.roundToInt()) }
                            )
                        }
                        SwitchRow(
                            stringResource(R.string.control_editor_swipe),
                            stringResource(R.string.control_editor_swipe_description),
                            state.isSwipeable,
                            onCheckedChange = state::applySwipeable
                        )
                        SwitchRow(
                            stringResource(R.string.control_editor_passthrough),
                            stringResource(R.string.control_editor_passthrough_description),
                            state.passThrough,
                            onCheckedChange = state::applyPassThrough
                        )
                    }
                }

                if (state.kind == ControlKind.JOYSTICK) {
                    SectionLabel(stringResource(R.string.control_editor_section_behaviour))
                    SettingsCard {
                        SwitchRow(
                            stringResource(R.string.control_editor_forward_lock),
                            stringResource(R.string.control_editor_forward_lock_description),
                            state.forwardLock,
                            onCheckedChange = state::applyForwardLock
                        )
                        SwitchRow(
                            stringResource(R.string.control_editor_absolute),
                            stringResource(R.string.control_editor_absolute_description),
                            state.absoluteTracking,
                            onCheckedChange = state::applyAbsoluteTracking
                        )
                        SwitchRow(
                            stringResource(R.string.control_editor_auto_walk),
                            stringResource(R.string.control_editor_auto_walk_description),
                            state.autoWalk,
                            onCheckedChange = state::applyAutoWalk
                        )
                    }
                }

                if (state.visibilityEditable) {
                    SectionLabel(stringResource(R.string.control_editor_section_visibility))
                    SettingsCard {
                        SwitchRow(
                            stringResource(R.string.control_editor_show_game),
                            stringResource(R.string.control_editor_show_game_description),
                            state.showInGame,
                            onCheckedChange = state::applyShowInGame
                        )
                        SwitchRow(
                            stringResource(R.string.control_editor_show_menu),
                            stringResource(R.string.control_editor_show_menu_description),
                            state.showInMenu,
                            onCheckedChange = state::applyShowInMenu
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                PanelFooter(state, actions)
            }
        }
    }
}

/** The control's name, typed in place, with the way out beside it. */
@Composable
private fun PanelHeader(state: ControlEditorState, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.nameEditable) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (state.name.isEmpty()) {
                    Text(
                        stringResource(R.string.control_editor_name_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = state.name,
                    onValueChange = state::applyName,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.titleMedium.copy(color = colors.onSurface)
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Text(
                stringResource(R.string.control_editor_joystick),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .clickable(onClick = onClose),
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

/**
 * The keys this control sends, as slots rather than as a fixed row of four.
 *
 * Four spinners were four decisions whether or not you wanted any of them. Almost every button
 * sends one key, so one slot is what is shown, and the next appears only once the last is filled.
 */
@Composable
private fun KeySlots(state: ControlEditorState, onPickKey: (Int) -> Unit) {
    val bound = state.keys.filter { it != NO_KEY }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (ordinal in bound.indices) {
                val index = slotIndexOf(state, ordinal)
                val keycode = state.keys[index]
                KeyChip(
                    label = keyDisplayName(keycode),
                    // The same icon the control will wear, so a bound key reads as what it does
                    // rather than only as what it is called.
                    glyphRes = ControlGlyphs.glyphForKeycode(keycode),
                    filled = true,
                    modifier = Modifier.weight(1f)
                ) { onPickKey(index) }
            }
            if (bound.size < MAX_KEYS) {
                KeyChip(
                    label = stringResource(
                        if (bound.isEmpty()) R.string.control_editor_key_none
                        else R.string.control_editor_key_add
                    ),
                    glyphRes = 0,
                    filled = false,
                    modifier = Modifier.weight(1f)
                ) { onPickKey(firstEmptySlot(state)) }
            }
        }
        Text(
            stringResource(R.string.control_editor_keys_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Where the n-th bound key actually lives, since a cleared slot leaves a hole. */
private fun slotIndexOf(state: ControlEditorState, ordinal: Int): Int {
    var seen = 0
    for (i in state.keys.indices) {
        if (state.keys[i] == NO_KEY) continue
        if (seen == ordinal) return i
        seen++
    }
    return 0
}

private fun firstEmptySlot(state: ControlEditorState): Int {
    for (i in 0 until MAX_KEYS) if (state.keys.getOrElse(i) { NO_KEY } == NO_KEY) return i
    return MAX_KEYS - 1
}

@Composable
private fun KeyChip(
    label: String,
    glyphRes: Int,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (filled) Modifier
                else Modifier.border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (!filled) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
        } else if (glyphRes != 0) {
            Icon(
                painterResource(glyphRes),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (filled) colors.primary else colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * A value that follows your thumb.
 *
 * Deliberately not the settings screen's slider, which writes on release: here the button behind
 * the panel is the readout, and a corner radius that only changes once you let go cannot be judged
 * against the shape you were aiming at.
 */
@Composable
private fun LiveSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
        Row {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                format(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

/** A colour, shown as itself, with the palette folding out underneath when tapped. */
@Composable
private fun ColorField(
    title: String,
    color: Int,
    alphaEnabled: Boolean,
    onColor: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Swatch(color, 30.dp)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            ColorPalette(color, alphaEnabled, onColor)
        }
    }
}

@Composable
private fun Swatch(color: Int, size: Dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            // Over a light ground, so a colour with alpha reads as translucent rather than dark.
            .background(Color.White.copy(alpha = 0.28f))
            .border(1.dp, colors.outline, RoundedCornerShape(9.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(Color(color))
        ) {}
    }
}

/**
 * A palette, and three sliders for anything it does not hold.
 *
 * A hue wheel would be prettier and worse: this is picked with a thumb on a phone held in two
 * hands, and eighteen swatches cover almost every control anyone actually draws.
 */
@Composable
private fun ColorPalette(color: Int, alphaEnabled: Boolean, onColor: (Int) -> Unit) {
    val alpha = (color ushr 24) and 0xFF
    var showCustom by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in PALETTE.chunked(6)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (swatch in row) {
                    val merged = (alpha shl 24) or (swatch and 0xFFFFFF)
                    val selected = (color and 0xFFFFFF) == (swatch and 0xFFFFFF)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF000000.toInt() or swatch))
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onColor(if (alphaEnabled) merged else (0xFF shl 24) or (swatch and 0xFFFFFF)) }
                    )
                }
            }
        }
        if (alphaEnabled) {
            LiveSliderCompact(
                title = stringResource(R.string.control_editor_colour_alpha),
                value = alpha / 255f,
                format = { "${(it * 100).roundToInt()}%" }
            ) { onColor(((it * 255).roundToInt() shl 24) or (color and 0xFFFFFF)) }
        }
        Text(
            stringResource(
                if (showCustom) R.string.control_editor_colour_palette
                else R.string.control_editor_colour_custom
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showCustom = !showCustom }
                .padding(vertical = 4.dp)
        )
        if (showCustom) {
            for (channel in 0..2) {
                val shift = 16 - channel * 8
                val current = (color ushr shift) and 0xFF
                LiveSliderCompact(
                    title = stringResource(CHANNEL_LABELS[channel]),
                    value = current / 255f,
                    format = { "${(it * 255).roundToInt()}" }
                ) { fraction ->
                    val byte = (fraction * 255).roundToInt().coerceIn(0, 255)
                    onColor((color and (0xFF shl shift).inv()) or (byte shl shift))
                }
            }
        }
    }
}

@Composable
private fun LiveSliderCompact(
    title: String,
    value: Float,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                format(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
    }
}

/** Which way a drawer's buttons come out of it. */
@Composable
private fun OrientationRow(state: ControlEditorState) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
        Text(
            stringResource(R.string.control_editor_orientation),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in ControlDrawerData.getOrientations().toList().chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (orientation in row) {
                        val selected = orientation == state.drawerOrientation
                        Text(
                            orientation.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) colors.primary else colors.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) colors.primary.copy(alpha = 0.16f)
                                    else colors.surfaceContainerHigh
                                )
                                .clickable { state.applyDrawerOrientation(orientation) }
                                .padding(vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Duplicate, add, delete.
 *
 * Delete is last and is the only thing on the panel in the error colour, because it is the one
 * action here that cannot be undone by dragging something back.
 */
@Composable
private fun PanelFooter(state: ControlEditorState, actions: ControlEditorActions) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.kind == ControlKind.DRAWER) {
            FooterButton(
                stringResource(R.string.control_editor_add_sub),
                colors.onSurface,
                actions::onAddSubButton
            )
        }
        FooterButton(
            stringResource(R.string.control_editor_duplicate),
            colors.onSurface,
            actions::onDuplicate
        )
        FooterButton(
            stringResource(R.string.control_editor_delete),
            colors.error,
            actions::onDelete
        )
    }
}

@Composable
private fun FooterButton(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = tint,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp)
    )
}

/** Wide enough for a slider and a label, narrow enough to leave the control visible beside it. */
private val PANEL_WIDTH = 330.dp

private val CHANNEL_LABELS = intArrayOf(
    R.string.control_editor_colour_red,
    R.string.control_editor_colour_green,
    R.string.control_editor_colour_blue
)

/**
 * Eighteen colours: a neutral ramp, the launcher's own accent, and the primaries a player reaches
 * for when they are colour-coding movement against actions.
 */
private val PALETTE = listOf(
    0x000000, 0x3A3245, 0x6B6478, 0xA79DB4, 0xE9E2EF, 0xFFFFFF,
    0x9649B8, 0xC08CE8, 0x4E7BD6, 0x39A0C8, 0x35A56B, 0x7FD69A,
    0xC8A93A, 0xE0762F, 0xD0463F, 0xE5484D, 0xB05D8E, 0x6E5A46
)
