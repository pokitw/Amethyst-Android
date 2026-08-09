package net.kdt.pojavlaunch.ui.content

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import androidx.compose.foundation.lazy.items
import net.kdt.pojavlaunch.ui.common.LazyAppScaffold
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.Amethyst40
import net.kdt.pojavlaunch.ui.theme.Amethyst50
import net.kdt.pojavlaunch.ui.theme.Amethyst70
import net.kdt.pojavlaunch.ui.theme.Amethyst90
import net.kdt.pojavlaunch.ui.theme.Neutral70
import net.kdt.pojavlaunch.ui.theme.SlotWell
import java.util.Locale

/** Everything the screen shows, gathered so the activity owns the state and the screen is pure. */
class ContentState(
    val items: List<ContentItem>,
    val loading: Boolean,
    val profileLabel: String?,
    val freeSpace: String
)

/**
 * Everything a profile owns, in one place.
 *
 * Worlds, mods, resource packs, shader packs and screenshots used to be five different answers to
 * the same question — "what have I got, and how do I add to it?" — and four of them had no answer
 * at all inside the launcher. "Game files" handed you off to a file manager and a path under
 * `Android/data`.
 *
 * The reason they are one screen and not five tabs is the add button. Nobody thinks "I would like
 * to place a file in the resourcepacks directory"; they think "add this". So there is one button,
 * and what you picked decides where it goes — a jar is a mod, a zip with a `pack.mcmeta` is a
 * resource pack, one with a `level.dat` is a world. The categories along the top are a filter over
 * one list, not five destinations, which is also what lets search cross them: type "sky" and the
 * world and the shader pack both come back.
 *
 * The rows are deliberately the same shape whatever they hold — a picture, a name, a line of
 * detail, at most one control — because that is what makes a mixed list scannable.
 */
@Composable
fun ContentScreen(
    state: ContentState,
    onAdd: () -> Unit,
    onToggle: (ContentItem, Boolean) -> Unit,
    onDelete: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenFolder: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<ContentKind?>(null) }
    var confirming by remember { mutableStateOf<ContentItem?>(null) }

    val lowered = query.trim().lowercase(Locale.getDefault())
    val matching = remember(state.items, lowered) { state.items.filter { it.matches(lowered) } }
    val shown = remember(matching, filter) {
        if (filter == null) matching else matching.filter { it.kind == filter }
    }

    // Grouped only when nothing is filtered, so a single category reads as one list rather than
    // as a list with a redundant heading over it.
    val sections = remember(shown, filter) {
        if (filter != null) listOf<Pair<ContentKind?, List<ContentItem>>>(null to shown)
        else ContentKind.values().mapNotNull { kind ->
            val ofKind = shown.filter { it.kind == kind }
            if (ofKind.isEmpty()) null else kind to ofKind
        }
    }

    LazyAppScaffold(
        title = stringResource(R.string.content_title),
        subtitle = state.profileLabel,
        onBack = onBack,
        barAction = { AddButton(onAdd) }
    ) {
        item(key = "storage") {
            Column {
                Spacer(Modifier.height(4.dp))
                StorageBar(state.items, state.freeSpace, state.loading)
                Spacer(Modifier.height(16.dp))
            }
        }
        // Kept out of the item above so typing does not recompose the storage bar with it.
        item(key = "search") {
            Column {
                SearchField(query) { query = it }
                Spacer(Modifier.height(12.dp))
                KindChips(matching, filter) { filter = it }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (state.loading && state.items.isEmpty()) {
            item(key = "skeleton") { SkeletonList() }
        } else if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyState(filter = filter, searching = lowered.isNotEmpty(), onAdd = onAdd)
            }
        } else {
            for ((kind, ofKind) in sections) {
                if (kind != null) {
                    item(key = "header-" + kind.name) {
                        Text(
                            stringResource(kind.titleRes).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 9.dp)
                        )
                    }
                }
                items(ofKind, key = { it.kind.name + "/" + it.id }) { item ->
                    Column {
                        ContentRow(item, onToggle, { confirming = it }, onOpen)
                        Spacer(Modifier.height(9.dp))
                    }
                }
            }
        }

        item(key = "footer") {
            Column {
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.content_open_folder),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenFolder)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }

    val target = confirming
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.content_delete_title, target.title)) },
            text = {
                Text(
                    stringResource(
                        if (target.kind == ContentKind.WORLD) R.string.content_delete_world
                        else if (target.toggleable) R.string.content_delete_toggleable
                        else R.string.content_delete_plain
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = null; onDelete(target) }) {
                    Text(
                        stringResource(R.string.content_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

/**
 * Where the space went.
 *
 * The screen's one bold element, and the only gradient on it. It earns the place because storage
 * is the constraint people actually hit on a phone — a folder of shader packs quietly being four
 * gigabytes is exactly the kind of thing you cannot see from a list of names.
 */
@Composable
private fun StorageBar(items: List<ContentItem>, freeSpace: String, loading: Boolean) {
    val colors = MaterialTheme.colorScheme
    val totals = remember(items) {
        ContentKind.values().associateWith { kind ->
            items.filter { it.kind == kind }.sumOf { it.sizeBytes }
        }
    }
    val used = totals.values.sum()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (used == 0L && loading) stringResource(R.string.content_measuring)
                else Formatter.formatShortFileSize(LocalContext.current, used),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.content_free, freeSpace),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
        ) {
            if (used > 0L) {
                for (kind in ContentKind.values()) {
                    val share = (totals[kind] ?: 0L).toFloat() / used
                    // Below this a segment is a sliver nobody can read, and rounding it up would
                    // make the bar lie about the big ones.
                    if (share < 0.012f) continue
                    Box(
                        Modifier
                            .weight(share)
                            .fillMaxHeight()
                            .background(kindBrush(kind))
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            for (kind in ContentKind.values()) {
                val bytes = totals[kind] ?: 0L
                if (bytes <= 0L) continue
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(kindBrush(kind))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(kind.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        Formatter.formatShortFileSize(LocalContext.current, bytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurface
                    )
                }
            }
        }
    }
}

/**
 * One accent, five steps of it.
 *
 * Five unrelated hues would turn the bar into a pie chart from 2009. Walking the amethyst ramp
 * keeps the screen on one colour while still telling the segments apart — and the steps are taken
 * from the ramp itself rather than from the theme's secondary and tertiary roles, which are the
 * same value here and would have drawn two of these identically.
 */
private fun kindBrush(kind: ContentKind): Brush {
    val end = when (kind) {
        ContentKind.WORLD -> Amethyst70
        ContentKind.MOD -> Amethyst50
        ContentKind.RESOURCE_PACK -> Amethyst90
        ContentKind.SHADER_PACK -> Amethyst40
        // Screenshots are the one thing here that is not part of the game, so they sit off the
        // accent entirely.
        ContentKind.SCREENSHOT -> Neutral70
    }
    return Brush.horizontalGradient(listOf(end.copy(alpha = 0.7f), end))
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.content_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth()
            )
        }
        AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.content_search_clear),
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQuery("") }
            )
        }
    }
}

/**
 * The categories, with their counts.
 *
 * Counts rather than plain labels because they are the cheapest possible answer to "what have I
 * got" — and they follow the search, so typing narrows them and an empty category disappears
 * rather than offering a dead end.
 */
@Composable
private fun KindChips(
    items: List<ContentItem>,
    selected: ContentKind?,
    onSelect: (ContentKind?) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip(stringResource(R.string.content_kind_all), items.size, selected == null) {
            onSelect(null)
        }
        for (kind in ContentKind.values()) {
            val count = items.count { it.kind == kind }
            if (count == 0) continue
            Chip(stringResource(kind.titleRes), count, selected == kind) { onSelect(kind) }
        }
    }
}

@Composable
private fun Chip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.primary else colors.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.primary else colors.onSurface
        )
    }
}

/**
 * One item, whatever it is.
 *
 * The trailing control is the only part that differs by kind, and only mods have one: a mod can be
 * switched off in place, and nothing else can. Deleting is a quiet link rather than a button,
 * because it is rare and irreversible and should not sit under a thumb that is scrolling.
 */
@Composable
private fun ContentRow(
    item: ContentItem,
    onToggle: (ContentItem, Boolean) -> Unit,
    onDelete: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .clickable {
                if (item.toggleable) onToggle(item, !item.enabled) else onOpen(item)
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumbnail(item)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (item.enabled) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.version != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.version,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (!item.detail.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    item.detail.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.badge != null) {
                    Badge(item.badge)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    buildString {
                        if (item.sizeBytes > 0L) {
                            append(Formatter.formatShortFileSize(context, item.sizeBytes))
                        }
                        if (item.modifiedMs > 0L && item.kind != ContentKind.MOD) {
                            if (isNotEmpty()) append(" · ")
                            append(
                                DateUtils.getRelativeTimeSpanString(
                                    item.modifiedMs, System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                )
                            )
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.content_delete_action),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onDelete(item) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        if (item.toggleable) {
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = item.enabled,
                onCheckedChange = { onToggle(item, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Amethyst20,
                    checkedTrackColor = colors.primary
                )
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/**
 * The item's own picture, in an inventory slot.
 *
 * A world's icon, a mod's logo, a pack's `pack.png`, a screenshot itself. Where there is none, the
 * first letter in the same well — a wall of identical placeholder glyphs is harder to scan than a
 * wall of letters, and both are better than a gap.
 */
@Composable
private fun Thumbnail(item: ContentItem) {
    // Screenshots are the one thing worth showing wide: they are pictures of a place, and a
    // square crop of one tells you much less than the shape it was taken in.
    val wide = item.kind == ContentKind.SCREENSHOT
    val shape = RoundedCornerShape(12.dp)
    val bitmap = item.thumbnail
    if (wide) {
        Box(
            Modifier
                .width(76.dp)
                .height(46.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!item.detailed) {
                ShimmerBox(Modifier.fillMaxSize())
            }
        }
        return
    }
    SlotWell(size = 46.dp) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            !item.detailed -> ShimmerBox(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            else -> Text(
                item.title.take(1).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Rows the shape of the real ones, while the folder is still being read.
 *
 * A spinner says "wait"; this says "here is what is coming", and on a folder of two hundred mods
 * the difference is whether the screen feels instant or feels like it is thinking. The real rows
 * replace these as each one is opened, so the list fills in rather than appearing all at once.
 */
@Composable
private fun SkeletonList() {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        for (index in 0 until 6) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // Varied widths, because six identical bars read as a broken layout rather
                    // than as a list of things with different names.
                    ShimmerBox(
                        Modifier
                            .fillMaxWidth(0.32f + (index % 3) * 0.16f)
                            .height(12.dp)
                            .clip(CircleShape)
                    )
                    ShimmerBox(
                        Modifier
                            .fillMaxWidth(0.2f + (index % 2) * 0.1f)
                            .height(9.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

/** A slow sweep across a placeholder. One transition drives them all, so they move together. */
@Composable
private fun ShimmerBox(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerProgress"
    )
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.background(
            Brush.horizontalGradient(
                listOf(
                    colors.surfaceContainerHigh,
                    colors.surfaceContainerHighest.copy(alpha = 0.4f + progress * 0.6f),
                    colors.surfaceContainerHigh
                )
            )
        )
    )
}

/** The first thing a new profile shows, so it says what to do rather than that there is nothing. */
@Composable
private fun EmptyState(filter: ContentKind?, searching: Boolean, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SlotWell(size = 60.dp) {
            Icon(
                painterResource(R.drawable.ic_x_files),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(
                when {
                    searching -> R.string.content_empty_search
                    filter != null -> filter.emptyRes
                    else -> R.string.content_empty_all
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        if (!searching) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.content_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.content_add),
                style = MaterialTheme.typography.titleSmall,
                color = Amethyst20,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 22.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun AddButton(onAdd: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onAdd)
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = Amethyst20,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.content_add),
            style = MaterialTheme.typography.labelLarge,
            color = Amethyst20
        )
    }
}
