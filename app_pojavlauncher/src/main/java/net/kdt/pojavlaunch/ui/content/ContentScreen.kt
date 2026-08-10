package net.kdt.pojavlaunch.ui.content

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.LazyAppScaffold
import net.kdt.pojavlaunch.ui.settings.ChoiceRow
import net.kdt.pojavlaunch.ui.settings.InfoRow
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.SlotWell

/**
 * One category's worth of rows, with the heading already written.
 *
 * [kind] is carried alongside the heading because the heading is not stable — it grows a size as
 * the folders are measured — and a lazy list keyed on text that changes underneath it loses its
 * place mid-scroll.
 */
@Immutable
class ContentSection(val kind: ContentKind, val heading: String, val items: List<ContentItem>)

/** Everything the screen shows. Built by the activity so the screen only has to draw it. */
@Immutable
class ContentState(
    val sections: List<ContentSection>,
    val total: Int,
    val loading: Boolean,
    val profileLabel: String?,
    val storageLine: String,
    val filterLabel: String,
    val searching: Boolean,
    val filtered: Boolean
)

/**
 * Everything a profile owns, in one place.
 *
 * Worlds, mods, resource packs, shader packs and screenshots used to be five different answers to
 * the same question — "what have I got, and how do I add to it?" — and four of them had no answer
 * at all inside the launcher. "Game files" handed you off to a file manager and a path under
 * `Android/data`.
 *
 * The reason they are one screen and not five is the add button. Nobody thinks "I would like to
 * place a file in the resourcepacks directory"; they think "add this". So the categories are a
 * filter over one list rather than five destinations, which is also what lets search cross them.
 *
 * <b>Built out of the settings components on purpose.</b> This first shipped with a gradient
 * storage meter, a row of filter chips and every item on its own floating card, and next to
 * Settings — which is the screen people arrive from — it read as a different application. It uses
 * the same grouped cards, the same section labels, the same row metrics and the same choice rows
 * now. The information it shows is unchanged; only the vocabulary is, and the vocabulary was
 * already decided.
 */
@Composable
fun ContentScreen(
    state: ContentState,
    query: String,
    onQuery: (String) -> Unit,
    onFilter: (String) -> Unit,
    onAdd: () -> Unit,
    onBrowseMods: () -> Unit,
    onToggle: (ContentItem, Boolean) -> Unit,
    onDelete: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onNeedThumbnail: (ContentItem) -> Unit,
    onOpenFolder: () -> Unit,
    onBack: () -> Unit
) {
    var confirming by remember { mutableStateOf<ContentItem?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyAppScaffold(
        title = stringResource(R.string.content_title),
        subtitle = state.profileLabel,
        onBack = onBack,
        barAction = { AddButton { adding = true } }
    ) {
        item(key = "search") {
            Column {
                Spacer(Modifier.height(4.dp))
                SearchField(query, onQuery)
                Spacer(Modifier.height(18.dp))
                SettingsCard {
                    InfoRow(
                        title = stringResource(R.string.content_storage),
                        value = state.storageLine
                    )
                    ChoiceRow(
                        title = stringResource(R.string.content_show),
                        names = filterNames(),
                        values = FILTER_VALUES,
                        selected = state.filterLabel,
                        onSelect = onFilter
                    )
                }
                // The gesture that is not obvious, named once, the way every sheet in the app
                // names its long press. A delete link on every row would put the one irreversible
                // action on this screen under a scrolling thumb four hundred times over.
                Spacer(Modifier.height(9.dp))
                Text(
                    stringResource(R.string.content_hold_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        if (state.loading && state.total == 0) {
            item(key = "skeleton") {
                Column {
                    SectionLabel(stringResource(R.string.content_loading))
                    SkeletonCard()
                }
            }
        } else if (state.sections.isEmpty()) {
            item(key = "empty") {
                Column {
                    Spacer(Modifier.height(22.dp))
                    EmptyState(state.searching, state.filtered) { adding = true }
                }
            }
        } else {
            for (section in state.sections) {
                item(key = "head-" + section.kind.name) { SectionLabel(section.heading) }
                // Every row is its own lazy item, and the card is rebuilt from their corners.
                //
                // Putting the section inside one `SettingsCard` would have read identically and
                // composed every row in it the moment the section scrolled into view — four
                // hundred screenshots in a single item is a LazyColumn that is not lazy, which is
                // the whole reason this screen is not built on `AppScaffold` in the first place.
                itemsIndexed(
                    section.items,
                    key = { _, entry -> entry.kind.name + "/" + entry.id }
                ) { index, entry ->
                    ContentRow(
                        item = entry,
                        shape = groupedShape(index, section.items.size),
                        onToggle = onToggle,
                        onDelete = { confirming = it },
                        onOpen = onOpen,
                        onNeedThumbnail = onNeedThumbnail
                    )
                }
            }
        }

        item(key = "footer") {
            Column {
                Spacer(Modifier.height(24.dp))
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

    if (adding) {
        AddSheet(
            onDismiss = { adding = false },
            onBrowse = { adding = false; onBrowseMods() },
            onPick = { adding = false; onAdd() }
        )
    }

    val target = confirming
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.content_delete_title, target.title)) },
            text = {
                Text(
                    stringResource(
                        when {
                            target.kind == ContentKind.WORLD -> R.string.content_delete_world
                            target.toggleable -> R.string.content_delete_toggleable
                            else -> R.string.content_delete_plain
                        }
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
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

/** The values [ChoiceRow] round-trips through; the names beside them are localised. */
val FILTER_VALUES: List<String> =
    listOf("all") + ContentKind.values().map { it.name }

@Composable
private fun filterNames(): List<String> =
    listOf(stringResource(R.string.content_kind_all)) +
            ContentKind.values().map { stringResource(it.titleRes) }

/**
 * The corner a row wears so that a run of them reads as one card.
 *
 * The card radius, but only on the outside of the run: the top row is rounded above, the bottom
 * row below, everything between is square, and a section of one is a whole card. Taken from the
 * theme rather than written as a number, because the nesting rule in the handbook is stated in
 * terms of the card radius and a literal here would quietly stop tracking it.
 */
@Composable
private fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val card = MaterialTheme.shapes.medium
    val flat = CornerSize(0.dp)
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = if (first) card.topStart else flat,
        topEnd = if (first) card.topEnd else flat,
        bottomEnd = if (last) card.bottomEnd else flat,
        bottomStart = if (last) card.bottomStart else flat
    )
}

/**
 * The search field.
 *
 * Deliberately the same object as Settings' way into search — same circle, same padding, same
 * nineteen-pixel icon, same `bodyLarge` hint. The only difference is that this one is the field
 * rather than a button to one, because there is nowhere else for the results to go.
 */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.content_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.content_search_clear),
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(19.dp)
                    .clickable { onQuery("") }
            )
        }
    }
}

/**
 * One item, in the shape every other row in the launcher has.
 *
 * A settings row is a slot well, a title in `titleSmall`, a description in `bodySmall` and the
 * current value in accent `labelLarge` underneath — and that accent line is what makes a long
 * screen scannable. Here the "value" is what the item is: its version, its loader, its size.
 *
 * The switch is the only part that differs by kind, and only mods have one, because a mod is the
 * only thing here that can be turned off rather than deleted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentRow(
    item: ContentItem,
    shape: RoundedCornerShape,
    onToggle: (ContentItem, Boolean) -> Unit,
    onDelete: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onNeedThumbnail: (ContentItem) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // Asked for when the row first appears, not when the folder is read: a profile with four
    // hundred screenshots would otherwise decode four hundred bitmaps to show eight of them.
    LaunchedEffect(item.kind, item.id) {
        if (!item.thumbnailRequested) onNeedThumbnail(item)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .combinedClickable(
                onClick = {
                    if (item.toggleable) onToggle(item, !item.enabled) else onOpen(item)
                },
                onLongClick = { onDelete(item) }
            )
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumbnail(item)
        Spacer(Modifier.width(14.dp))
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
                // The quiet badge of §9, not the filled one Settings uses for "THIS PROFILE":
                // that one is loud because it appears once, and this appears on every row.
                if (!item.badge.isNullOrEmpty()) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        item.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(colors.primary.copy(alpha = 0.13f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
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
            Spacer(Modifier.height(3.dp))
            Text(
                item.summary,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.toggleable) {
            Spacer(Modifier.width(12.dp))
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

/**
 * The item's own picture, in the same inventory slot every other row's icon sits in.
 *
 * A world's icon, a mod's logo, a pack's `pack.png`, the screenshot itself. Where there is none,
 * the first letter in the same well — a wall of identical placeholder glyphs is harder to scan
 * than a wall of letters, and both are better than a gap.
 */
@Composable
private fun Thumbnail(item: ContentItem) {
    SlotWell(size = 38.dp) {
        val bitmap = item.thumbnail
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
            )
            !item.detailed -> Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .shimmer()
            )
            else -> Text(
                item.initial,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Rows the shape of the real ones, while the folder is still being read.
 *
 * A spinner says "wait"; this says "here is what is coming". The real rows replace these as each
 * item is opened, so the list fills in rather than appearing all at once.
 */
@Composable
private fun SkeletonCard() {
    SettingsCard {
        for (index in 0 until 5) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .shimmer()
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // Varied widths, because five identical bars read as a broken layout rather
                    // than as a list of things with different names.
                    Box(
                        Modifier
                            .fillMaxWidth(0.34f + (index % 3) * 0.16f)
                            .height(11.dp)
                            .clip(CircleShape)
                            .shimmer()
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.2f + (index % 2) * 0.1f)
                            .height(9.dp)
                            .clip(CircleShape)
                            .shimmer()
                    )
                }
            }
        }
    }
}

/** A slow sweep, from one transition, so every placeholder on screen moves together. */
@Composable
private fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerProgress"
    )
    return background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = progress))
}

/** The first thing a new profile shows, so it says what to do rather than that there is nothing. */
@Composable
private fun EmptyState(searching: Boolean, filtered: Boolean, onAdd: () -> Unit) {
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
                    filtered -> R.string.content_empty_filter
                    else -> R.string.content_empty_all
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        if (!searching && !filtered) {
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
            Icons.Filled.Add,
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

/**
 * The two ways to add something to a profile.
 *
 * The add button used to go straight to a file picker, which was the only way there was. Now that
 * mods can be fetched from Modrinth there are two, and they are genuinely different acts rather
 * than two routes to one: one searches an index, the other takes a file you already have. Putting
 * the search first is not alphabetical, it is that almost every add is a mod and almost every mod
 * is on Modrinth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(onDismiss: () -> Unit, onBrowse: () -> Unit, onPick: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                stringResource(R.string.content_add_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            // The arrow landing on a shelf is the download; the folder is the file you already
            // have. They were the wrong way round in the first draft.
            AddChoice(R.drawable.ic_x_install, R.string.content_add_from_modrinth, onBrowse)
            Spacer(Modifier.height(8.dp))
            AddChoice(R.drawable.ic_x_files, R.string.content_add_from_file, onPick)
        }
    }
}

@Composable
private fun AddChoice(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(13.dp))
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface
        )
    }
}
