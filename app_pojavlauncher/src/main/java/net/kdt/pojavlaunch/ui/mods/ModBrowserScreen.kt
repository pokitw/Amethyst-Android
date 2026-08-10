package net.kdt.pojavlaunch.ui.mods

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthMods
import net.kdt.pojavlaunch.ui.common.AppEmptyState
import net.kdt.pojavlaunch.ui.common.AppSearchField
import net.kdt.pojavlaunch.ui.common.LazyAppScaffold
import net.kdt.pojavlaunch.ui.settings.ChoiceRow
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.settings.SwitchRow
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.theme.SlotWell
import java.util.Locale

/**
 * Finding a mod and putting it in the profile, without leaving the launcher.
 *
 * <b>Why this is not the mod search that already existed.</b> The old one searched modpacks, and
 * installing one built a whole new profile around it. There has never been a way to add a single
 * mod to a profile you already have, which is what almost everyone actually wants; the way it is
 * done today is a browser, a download, and the Game files importer over a folder Android will not
 * let you browse.
 *
 * <b>The filter is the feature.</b> The profile knows its Minecraft version and its loader, so
 * the search is narrowed to what will actually run before a single character is typed. That is
 * the difference between one tap and a search-and-compare across a mod page's version table. The
 * chip that says so is also the switch that turns it off, because a player who knows better than
 * the index should not be stopped by the launcher being careful for them.
 *
 * <b>Drawn with the settings components.</b> Same grouped card, same row metrics, same section
 * labels as Game files, which is the screen most people will arrive from. This is a place to add
 * to that folder, not a different application.
 */
@Composable
fun ModBrowserScreen(
    state: ModBrowserState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleFilter: () -> Unit,
    onSort: (String) -> Unit,
    onCategory: (String) -> Unit,
    onInstall: (ModRow) -> Unit,
    onOpen: (ModRow) -> Unit,
    onNeedIcon: (ModRow) -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val focus = LocalFocusManager.current

    // Paging watched through snapshotFlow rather than read during composition: layoutInfo changes
    // on every measure pass, so reading it in the composable body would recompose the whole screen
    // continuously while scrolling. The comparison is against total item count, not row count,
    // because the list carries header items the row indices know nothing about.
    LaunchedEffect(listState, state.hasMore, state.rows.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (state.hasMore && !state.loadingMore && !state.loading &&
                total > 0 && last >= total - 3
            ) {
                onLoadMore()
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyAppScaffold(
            title = stringResource(R.string.mods_browse_title),
            subtitle = state.target.name.ifEmpty { null },
            onBack = onBack,
            state = listState
        ) {
            item(key = "search") {
                Column {
                    Spacer(Modifier.height(14.dp))
                    AppSearchField(
                        query = state.query,
                        onQueryChange = onQuery,
                        placeholder = stringResource(R.string.mods_browse_hint),
                        modifier = Modifier.fillMaxWidth(),
                        onSubmit = { focus.clearFocus(); onSearch() }
                    )
                    Spacer(Modifier.height(12.dp))
                    RefineCard(state, onToggleFilter, onSort, onCategory)
                    Spacer(Modifier.height(18.dp))
                }
            }

            if (state.targetKnown && !state.target.canRunMods) {
                item(key = "vanilla") {
                    Notice(stringResource(R.string.mods_browse_no_loader))
                    Spacer(Modifier.height(14.dp))
                }
            }

            when {
                state.loading -> item(key = "loading") { Centred { Spinner() } }
                state.failed -> item(key = "failed") {
                    Notice(
                        error = true,
                        text = stringResource(
                            when (state.failure) {
                                ModrinthMods.Failure.RATE_LIMITED -> R.string.mods_browse_rate_limited
                                ModrinthMods.Failure.SERVER -> R.string.mods_browse_server_error
                                else -> R.string.mods_browse_failed
                            }
                        )
                    )
                }
                state.empty -> item(key = "empty") {
                    Column {
                        Spacer(Modifier.height(22.dp))
                        AppEmptyState(
                            // The files glyph, deliberately not the install one: the install mark is
                            // on every row's button, and accent-tinted above "Nothing matched" it
                            // would read as an offer to install.
                            iconRes = R.drawable.ic_x_files,
                            title = stringResource(R.string.mods_browse_empty_title),
                            body = stringResource(
                                // Pointing at a filter that is already off would send someone to fix
                                // the one thing that is not the problem, which is the same rule the
                                // failure messages here already follow.
                                if (state.filtered && state.target.filterLabel.isNotEmpty()) {
                                    R.string.mods_browse_empty_filtered
                                } else {
                                    R.string.mods_browse_empty_body
                                }
                            )
                        )
                    }
                }
            }

            if (state.rows.isNotEmpty()) {
                item(key = "label") { SectionLabel(stringResource(R.string.mods_browse_results)) }
                // Each row is its own lazy item and the card's corners are rebuilt from its position,
                // which is the same shape Game files uses: one SettingsCard around the whole list
                // would compose every row the moment the section scrolled into view.
                itemsIndexed(state.rows, key = { _, row -> row.hit.projectId }) { index, row ->
                    LaunchedEffect(row.hit.projectId) { if (row.icon == null) onNeedIcon(row) }
                    ModResultRow(
                        row = row,
                        shape = groupedShape(index, state.rows.size),
                        enabled = state.targetKnown && state.target.canRunMods,
                        onInstall = { onInstall(row) },
                        onOpen = { onOpen(row) }
                    )
                }
            }

            if (state.loadingMore) {
                item(key = "more") { Centred { Spinner() } }
            }
        }
    }
}

/* ------------------------------------------------------------------ pieces */

/**
 * The switch that narrows the search to what this profile can run.
 *
 * <b>A settings row, not a chip.</b> A chip is for identity and not for actions, which is the
 * handbook's rule and the same call Game files made when its category filter became a choice row.
 * It is also the more honest shape: this is a setting that happens to live at the top of a search,
 * and the profile it is filtering to belongs in the accent line where every other current value in
 * the launcher goes.
 */
@Composable
private fun RefineCard(
    state: ModBrowserState,
    onToggle: () -> Unit,
    onSort: (String) -> Unit,
    onCategory: (String) -> Unit
) {
    val label = state.target.filterLabel
    SettingsCard {
        if (label.isNotEmpty()) {
            SwitchRow(
                title = stringResource(R.string.mods_browse_filter_title),
                // The profile being filtered to goes in the description, since SwitchRow has no
                // accent value line: it is the one fact worth reading and it moves with the
                // profile.
                description = if (state.filtered) {
                    stringResource(R.string.mods_browse_filter_on, label)
                } else {
                    stringResource(R.string.mods_browse_filter_description)
                },
                checked = state.filtered,
                onCheckedChange = { onToggle() }
            )
        }
        ChoiceRow(
            title = stringResource(R.string.mods_browse_sort_title),
            names = listOf(
                stringResource(R.string.mods_browse_sort_default),
                stringResource(R.string.mods_browse_sort_downloads),
                stringResource(R.string.mods_browse_sort_follows),
                stringResource(R.string.mods_browse_sort_newest),
                stringResource(R.string.mods_browse_sort_updated)
            ),
            values = MOD_SORTS,
            selected = state.sort,
            onSelect = onSort
        )
        ChoiceRow(
            title = stringResource(R.string.mods_browse_category_title),
            // The tags are the index's own names, tidied rather than translated: they are what
            // the Modrinth site itself shows, and a picker that agrees with the site is the one
            // that can be followed from a mod's own install instructions.
            names = listOf(stringResource(R.string.mods_browse_category_all)) +
                    MOD_CATEGORIES.map { categoryLabel(it) },
            values = listOf("") + MOD_CATEGORIES,
            selected = state.category,
            onSelect = onCategory
        )
    }
}

@Composable
private fun ModResultRow(
    row: ModRow,
    shape: RoundedCornerShape,
    enabled: Boolean,
    onInstall: () -> Unit,
    onOpen: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            // The whole row opens the mod's page; the install button on the end keeps its own
            // click, which wins inside its own bounds. Two targets, one card, the same deal the
            // control center's capture card struck.
            .clickable(onClick = onOpen)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SlotWell(size = 38.dp) {
                val icon = row.icon
                if (icon != null) {
                    Image(
                        BitmapPainter(icon),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_x_files),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.hit.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    row.hit.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                // The row's current-value line, in accent labelLarge like every other row in
                // the launcher. This was the one list whose third line was grey, and that accent
                // line is exactly what makes a long list scannable.
                Text(
                    downloadsLabel(row.hit.downloads),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            InstallButton(row, enabled, onInstall)
        }
        val note = row.note
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = if (row.state == InstallState.FAILED) colors.error else colors.primary
            )
        }
    }
}

@Composable
private fun InstallButton(row: ModRow, enabled: Boolean, onInstall: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val done = row.state == InstallState.DONE || row.installed
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            // Never a filled accent: this is a repeating row control, not the screen's one
            // primary action, and twenty solid violet discs is the accent as decoration.
            .background(
                if (done) colors.primary.copy(alpha = 0.13f)
                else colors.surfaceContainerHighest
            )
            // Always tappable, deliberately. This shipped once as a button that was inert
            // whenever the profile could not run mods, which reads as a broken screen, not as a
            // decision. Whatever the state, a tap now gets an answer: the activity installs, or
            // says in the row why it will not. Only mid-install is a tap ignored, because the
            // spinner is already the answer.
            .then(
                if (row.state != InstallState.WORKING) {
                    Modifier.clickable(onClick = onInstall)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            row.state == InstallState.WORKING -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = colors.primary,
                modifier = Modifier.size(18.dp)
            )
            done -> Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.mods_browse_installed),
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
            else -> Icon(
                painterResource(R.drawable.ic_x_install),
                contentDescription = stringResource(R.string.mods_browse_install),
                tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * A paragraph in a card, for the things that are not a list.
 *
 * The error colour is not decoration: a failure and a piece of advice were drawn identically
 * here, so "Modrinth is rate limiting" read exactly like "this profile has no mod loader". The
 * error token is what every other failure in the launcher wears.
 */
@Composable
private fun Notice(text: String, error: Boolean = false) {
    SettingsCard {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), Alignment.Center) { content() }
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(
        strokeWidth = 2.5.dp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(26.dp)
    )
}

/** The card around a section, rebuilt from each row's position: first rounded above, last below. */
@Composable
private fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val card = MaterialTheme.shapes.medium
    val flat = androidx.compose.foundation.shape.CornerSize(0.dp)
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = if (first) card.topStart else flat,
        topEnd = if (first) card.topEnd else flat,
        bottomStart = if (last) card.bottomStart else flat,
        bottomEnd = if (last) card.bottomEnd else flat
    )
}

/**
 * Downloads, shortened the way every store shortens them.
 *
 * The number is built here and the word around it comes from resources, because the two are
 * different kinds of thing: the shortening is arithmetic and the sentence is language.
 */
@Composable
private fun downloadsLabel(downloads: Int): String {
    val locale = Locale.getDefault()
    val count = when {
        downloads >= 1_000_000 -> String.format(locale, "%.1fM", downloads / 1_000_000f)
        downloads >= 1_000 -> String.format(locale, "%.0fK", downloads / 1_000f)
        else -> String.format(locale, "%d", downloads)
    }
    return stringResource(R.string.mods_browse_downloads, count)
}
