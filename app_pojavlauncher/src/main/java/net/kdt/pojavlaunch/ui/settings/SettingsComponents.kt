package net.kdt.pojavlaunch.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.nativeCanvas
import net.kdt.pojavlaunch.customcontrols.ControlSkin
import net.kdt.pojavlaunch.customcontrols.textures.ControlTexture
import net.kdt.pojavlaunch.customcontrols.textures.ControlTextureDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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

/**
 * Where search left off.
 *
 * Search hands you to the screen a setting lives on, which is only half an answer on a screen of
 * twenty rows — so the row that was searched for lights up and the screen scrolls to it. Rows are
 * matched by their title rather than by a key, because every row already has one and threading an
 * identifier through fifty call sites would buy nothing.
 */
@Stable
class SettingsHighlight {
    var target by mutableStateOf<String?>(null)
        private set

    /** Where the row ended up, in root coordinates, once it has been laid out. */
    var anchor by mutableStateOf<Float?>(null)
        private set

    fun request(title: String) {
        target = title
        anchor = null
    }

    /**
     * Recorded once and then left alone: the row keeps reporting as the screen scrolls, and
     * following that would chase the scroll it just asked for.
     */
    fun report(y: Float) {
        if (anchor == null) anchor = y
    }

    fun clear() {
        target = null
        anchor = null
    }
}

val LocalSettingsHighlight = staticCompositionLocalOf { SettingsHighlight() }

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

/** The accent wash a row wears after search has sent you to it, and the report of where it is. */
@Composable
private fun HighlightBox(title: String?, content: @Composable () -> Unit) {
    val highlight = LocalSettingsHighlight.current
    val active = title != null && title == highlight.target
    val wash by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        else Color.Transparent,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "settingHighlight"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { if (active) highlight.report(it.positionInRoot().y) }
            .background(wash)
    ) {
        content()
    }
}

/** Content is scoped to the Row so rows can give their text block the leftover width. */
@Composable
private fun RowShell(
    title: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    HighlightBox(title) {
        val base = Modifier.fillMaxWidth()
        Box(if (onClick != null) base.clickable(onClick = onClick) else base) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
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

/**
 * A switch.
 *
 * The two optional extras exist for one row and are worth having generally: a switch that stands
 * for more than itself needs to say what it currently amounts to, and the accent value line is
 * exactly the device Settings already uses for that everywhere else.
 *
 * @param value  the state under the description, in the accent, when the switch stands for more
 *               than a boolean
 * @param inert  true when tapping opens something rather than toggling. The switch then reflects
 *               state and never moves on its own, which is right for a row whose real answer
 *               takes a download to arrive at.
 */
@Composable
fun SwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    value: String? = null,
    iconRes: Int? = null,
    inert: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    RowShell(title = title, onClick = { onCheckedChange(!checked) }) {
        if (iconRes != null) {
            SlotWell(size = 34.dp) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(13.dp))
        }
        Box(Modifier.weight(1f)) { RowText(title, description, value) }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            // An inert switch is a report, not a control: the row's own click is what acts, and
            // letting the thumb be dragged would move it before anything had actually happened.
            onCheckedChange = if (inert) null else onCheckedChange,
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
    HighlightBox(title) {
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
}

private fun snap(raw: Float, min: Int, max: Int, step: Int): Int {
    if (step <= 1) return raw.roundToInt().coerceIn(min, max)
    val steps = ((raw - min) / step).roundToInt()
    return (min + steps * step).coerceIn(min, max)
}

/** One of a fixed set of values, chosen in a dialog rather than a spinner glued to the row. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChoiceRow(
    title: String,
    description: String? = null,
    names: List<String>,
    values: List<String>,
    selected: String,
    badge: String? = null,
    /** One line above the options naming a non-obvious gesture, the way every sheet does. */
    hint: String? = null,
    /** A secondary action on a long press of an option; the hint should say so. */
    onLongPress: ((String) -> Unit)? = null,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val index = values.indexOf(selected)
    val label = if (index >= 0 && index < names.size) names[index] else selected
    RowShell(title = title, onClick = { open = true }) {
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
                    if (hint != null) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                        )
                    }
                    names.forEachIndexed { i, name ->
                        val isSelected = i < values.size && values[i] == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        open = false
                                        if (i < values.size) onSelect(values[i])
                                    },
                                    onLongClick = if (onLongPress != null && i < values.size) {
                                        {
                                            open = false
                                            onLongPress(values[i])
                                        }
                                    } else null
                                )
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
    RowShell(title = title, onClick = { open = true }) {
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
    RowShell(title = title, onClick = onClick) {
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
    RowShell(title = title) { Box(Modifier.weight(1f)) { RowText(title, description, value) } }
}

/**
 * The way into search, sitting where a search bar sits everywhere else.
 *
 * It is a button rather than a field: the field belongs on the search screen, where the keyboard
 * has somewhere to push the results to.
 */
@Composable
fun SearchEntry(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.settings_search_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Progressive disclosure.
 *
 * These are settings you touch once, on advice, to fix one device. Behind a separate screen they
 * were undiscoverable; loose in the main list they buried the handful that matter. The count says
 * how much is hiding, so the expander is not a mystery box.
 *
 * It opens by itself when search has sent you to something inside it, since a highlighted row
 * behind a collapsed expander would be an answer you cannot see.
 */
@Composable
fun AdvancedSection(count: Int, titles: List<String> = emptyList(), content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val target = LocalSettingsHighlight.current.target
    // Latched rather than derived from the highlight: the wash fades after a couple of seconds,
    // and a section that closed itself again would take the answer with it.
    LaunchedEffect(target) {
        if (target != null && titles.contains(target)) expanded = true
    }
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

/**
 * One entry in the Button style rail.
 *
 * @param value   what goes into the preference
 * @param name    what is shown under the tile
 * @param texture the pack's artwork, or null for the two styles drawn from the layout's own
 *                numbers rather than from a picture
 */
@Immutable
class ControlStyleOption(
    val value: String,
    val name: String,
    val texture: ControlTexture? = null
)

/**
 * Choosing what the on-screen buttons look like, by looking at them.
 *
 * <b>A rail rather than a [ChoiceRow].</b> Everything else in Settings picks between words, and a
 * dialog listing them is the right shape for that. A button style is not a word: it is a picture,
 * and once the launcher ships eleven of them a list of folder names is a worse way to choose than
 * no list at all. The handbook already says as much about layouts, that a control layout is a
 * picture and not a filename; a texture pack is that argument with nothing else in it.
 *
 * <b>The tiles are drawn by the button's own renderer.</b> A preview that reimplemented the
 * nine-slice would be a third copy of geometry that already exists in Java and in the check
 * script, and the copy that is wrong is always the one nobody looks at. [ControlTextureDrawable]
 * is handed the same bounds a real button would give it, so what is in the tile is what will be
 * on screen, including the corner rounding at that exact size.
 *
 * <b>And they are drawn over sky and grass.</b> Most of these faces are translucent, so against a
 * settings surface they would all read as roughly the same dark rectangle and the one thing worth
 * knowing about a pack — whether it is legible over a bright world — would be invisible. That is
 * not decoration: a face that vanishes over midday is the failure this preview exists to catch.
 */
@Composable
fun ControlStyleRow(
    title: String,
    description: String,
    options: List<ControlStyleOption>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val chosen = options.firstOrNull { it.value == selected }
    HighlightBox(title) {
        Column(Modifier.padding(vertical = 13.dp)) {
            Box(Modifier.padding(horizontal = 15.dp)) {
                RowText(title, description, chosen?.name ?: selected)
            }
            Spacer(Modifier.height(13.dp))
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEach { option ->
                    StyleTile(option, option.value == selected) { onSelect(option.value) }
                }
            }
        }
    }
}

@Composable
private fun StyleTile(option: ControlStyleOption, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // One drawable per pack, kept across recompositions: it holds the nine destination rectangles
    // and recomputes them only when its bounds change, which is the whole reason it is cheap.
    val drawable = remember(option.texture) {
        option.texture?.let { ControlTextureDrawable(it) }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(86.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) colors.primary else colors.outline.copy(alpha = 0.45f),
                    shape = shape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawWorld()
                inset(horizontal = size.width * 0.14f, vertical = size.height * 0.24f) {
                    if (drawable != null) drawTexture(drawable) else drawFlat(option.value)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            option.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Sky over grass, desaturated well below what the game draws.
 *
 * Enough of a world to judge a translucent face against, and not a colour swatch: a settings card
 * is somewhere quiet and eleven tiles of bright green would be the loudest thing on the screen by
 * a distance. The ground is a strip below a high horizon for the same reason.
 */
private fun DrawScope.drawWorld() {
    val horizon = size.height * 0.70f
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF63819C), Color(0xFF8FAAC2)),
            endY = horizon
        ),
        size = Size(size.width, horizon)
    )
    drawRect(
        color = Color(0xFF4C6438),
        topLeft = Offset(0f, horizon),
        size = Size(size.width, size.height - horizon)
    )
}

private fun DrawScope.drawTexture(drawable: ControlTextureDrawable) {
    drawIntoCanvas { canvas ->
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawable.draw(canvas.nativeCanvas)
    }
}

/**
 * The two styles that have no artwork, drawn from the numbers that actually produce them.
 *
 * Layout colours is a fresh {@code ControlData}: square, black at thirty per cent, no keyline.
 * Pocket is {@link ControlSkin}'s own constants. Neither is an impression of the style, they are
 * the style, which is why the two tiles look as different from each other as the buttons do.
 */
private fun DrawScope.drawFlat(style: String) {
    val pocket = style == ControlSkin.STYLE_POCKET
    // CORNER_PERCENT is a percentage of half the shorter side, the way ControlData stores it.
    val radius = if (pocket) size.minDimension * ControlSkin.CORNER_PERCENT / 200f else 0f
    val corner = CornerRadius(radius, radius)
    drawRoundRect(
        color = Color(if (pocket) ControlSkin.FILL else 0x4D000000),
        cornerRadius = corner
    )
    if (pocket) {
        val width = 1.5.dp.toPx()
        drawRoundRect(
            color = Color(ControlSkin.STROKE),
            cornerRadius = corner,
            topLeft = Offset(width / 2f, width / 2f),
            size = Size(size.width - width, size.height - width),
            style = Stroke(width)
        )
    }
}
