package net.kdt.pojavlaunch.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.SlotWell
import kotlin.math.roundToInt

/**
 * The pieces every settings screen is built from.
 *
 * Deliberately plain composables rather than a data-driven spec: each screen then reads as the
 * list of settings it actually is, which is far easier to check against the preference XML it
 * replaces than an interpreter would be.
 *
 * Rows inside a card carry no dividers. The shared surface groups them and the space between the
 * text blocks separates them; hairlines on top of both would only add noise.
 */

/** A quiet all-caps heading. Sections group settings; they are not themselves settings. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 9.dp)
    )
}

/** Rows sharing one surface, so a section reads as a single object. */
@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        content()
    }
}

/** Content is scoped to the Row so rows can give their text block the leftover width. */
@Composable
private fun RowShell(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val base = Modifier.fillMaxWidth()
    Box(if (onClick != null) base.clickable(onClick = onClick) else base) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun RowText(title: String, description: String?, value: String?, badge: String? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Amethyst20,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        if (!description.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!value.isNullOrEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    RowShell(onClick = { onCheckedChange(!checked) }) {
        Box(Modifier.weight(1f)) { RowText(title, description, null) }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Amethyst20,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/**
 * A value you drag.
 *
 * The number sits under the title rather than beside the track, because during a drag your thumb
 * is on the track and would cover it. The preference is written on release, not per pixel.
 */
@Composable
fun SliderRow(
    title: String,
    description: String? = null,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    format: (Int) -> String,
    onValueChange: (Int) -> Unit
) {
    var dragging by remember { mutableStateOf<Int?>(null) }
    val shown = dragging ?: value
    Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
        RowText(title, description, format(shown))
        Spacer(Modifier.height(2.dp))
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = snap(it, min, max, step) },
            valueRange = min.toFloat()..max.toFloat(),
            onValueChangeFinished = {
                dragging?.let { onValueChange(it) }
                dragging = null
            }
        )
    }
}

private fun snap(raw: Float, min: Int, max: Int, step: Int): Int {
    if (step <= 1) return raw.roundToInt().coerceIn(min, max)
    val steps = ((raw - min) / step).roundToInt()
    return (min + steps * step).coerceIn(min, max)
}

/** One of a fixed set of values, chosen in a dialog rather than a spinner glued to the row. */
@Composable
fun ChoiceRow(
    title: String,
    description: String? = null,
    names: List<String>,
    values: List<String>,
    selected: String,
    badge: String? = null,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val index = values.indexOf(selected)
    val label = if (index >= 0 && index < names.size) names[index] else selected
    RowShell(onClick = { open = true }) {
        Box(Modifier.weight(1f)) { RowText(title, description, label, badge) }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    names.forEachIndexed { i, name ->
                        val isSelected = i < values.size && values[i] == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    open = false
                                    if (i < values.size) onSelect(values[i])
                                }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

/** Free text, edited in a dialog. Used only for the JVM arguments. */
@Composable
fun TextRow(
    title: String,
    description: String? = null,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    RowShell(onClick = { open = true }) {
        Box(Modifier.weight(1f)) {
            RowText(title, description, value.ifEmpty { placeholder })
        }
    }
    if (open) {
        var draft by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onValueChange(draft.trim())
                }) { Text(stringResource(R.string.global_save)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

/** A row that leads somewhere: another screen, an activity, or a one-shot action. */
@Composable
fun NavRow(
    title: String,
    description: String? = null,
    value: String? = null,
    iconRes: Int? = null,
    onClick: () -> Unit
) {
    RowShell(onClick = onClick) {
        if (iconRes != null) {
            SlotWell(size = 38.dp) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        Box(Modifier.weight(1f)) { RowText(title, description, value) }
    }
}

/** A read-only statement of fact. No affordance, because there is nothing to press. */
@Composable
fun InfoRow(title: String, description: String? = null, value: String? = null) {
    RowShell { Box(Modifier.weight(1f)) { RowText(title, description, value) } }
}

/**
 * Progressive disclosure.
 *
 * These are settings you touch once, on advice, to fix one device. Behind a separate screen they
 * were undiscoverable; loose in the main list they buried the handful that matter. The count says
 * how much is hiding, so the expander is not a mystery box.
 */
@Composable
fun AdvancedSection(count: Int, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "advancedChevron"
    )
    Column(
        Modifier
            .padding(top = 20.dp)
            .animateContentSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable { expanded = !expanded }
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.settings_advanced),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.settings_advanced_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
