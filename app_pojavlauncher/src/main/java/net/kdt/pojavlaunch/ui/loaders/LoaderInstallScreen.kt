package net.kdt.pojavlaunch.ui.loaders

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppEmptyState
import net.kdt.pojavlaunch.ui.common.AppSearchField
import net.kdt.pojavlaunch.ui.common.AppSheetHeading
import net.kdt.pojavlaunch.ui.common.LazyAppScaffold
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.theme.Amethyst20

/**
 * Installing a mod loader, asked the way people actually ask it.
 *
 * <b>The screen this replaces has the question inside out.</b> It makes you choose a loader before
 * it can tell you what that loader supports, then find your Minecraft version in a spinner of
 * seven hundred entries, then a build in a second list, on one of four near-identical screens.
 * Nobody thinks "I want Forge, and separately, for what version?" They think "I want to play
 * 1.20.1 with mods", and every loader that can do that is one fact.
 *
 * So a row is a Minecraft version and the loaders that support it sit on it, each showing the
 * build it would install. Availability is the thing you can see at a glance, which is what the
 * old flow made you discover by elimination, and the common case is now two taps rather than a
 * screen each.
 *
 * The build picker is behind the tap rather than in the row: almost nobody wants a specific
 * loader build, and the ones who do know they want it. Putting it in front of everybody would be
 * rebuilding the second spinner this exists to remove.
 */
@Immutable
class LoaderBuild(
    val id: String,
    val label: String,
    val stable: Boolean
)

@Immutable
class LoaderOption(
    /** Matches LoaderIndex.Loader.name, which is what the install call takes back. */
    val loader: String,
    val name: String,
    /** The build that will be installed unless another is chosen. */
    val recommended: LoaderBuild,
    val builds: List<LoaderBuild>,
    /** Whether a profile for this loader and version already exists on the device. */
    val installed: Boolean
)

@Immutable
class LoaderRow(
    val gameVersion: String,
    val release: Boolean,
    val loaders: List<LoaderOption>
)

@Immutable
class LoaderInstallState(
    val loading: Boolean = true,
    val rows: List<LoaderRow> = emptyList(),
    val query: String = "",
    val showSnapshots: Boolean = false,
    /** Loaders whose version list could not be fetched, named rather than silently missing. */
    val unavailable: List<String> = emptyList(),
    val error: String? = null,
    /** The row and loader whose sheet is open. */
    val chosen: Pair<LoaderRow, LoaderOption>? = null,
    val installing: Boolean = false
)

@Composable
fun LoaderInstallScreen(
    state: LoaderInstallState,
    onQuery: (String) -> Unit,
    onSnapshots: (Boolean) -> Unit,
    onChoose: (LoaderRow, LoaderOption) -> Unit,
    onDismiss: () -> Unit,
    onInstall: (LoaderRow, LoaderOption, LoaderBuild) -> Unit,
    onRetry: () -> Unit,
    onRunJar: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        LazyAppScaffold(
            title = stringResource(R.string.loader_install_title),
            subtitle = stringResource(R.string.loader_install_subtitle),
            onBack = onBack
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                AppSearchField(
                    query = state.query,
                    onQueryChange = onQuery,
                    placeholder = stringResource(R.string.loader_install_search_hint)
                )
                Spacer(Modifier.height(10.dp))
                SnapshotToggle(state.showSnapshots, onSnapshots)
                if (state.unavailable.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(
                            R.string.loader_install_unavailable,
                            state.unavailable.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp, color = colors.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else if (state.rows.isEmpty()) {
                item {
                    AppEmptyState(
                        iconRes = R.drawable.ic_x_install,
                        title = stringResource(
                            if (state.error != null) R.string.loader_install_failed_title
                            else R.string.loader_install_none_title
                        ),
                        body = state.error ?: stringResource(R.string.loader_install_none_body),
                        action = if (state.error != null) {
                            { RetryButton(onRetry) }
                        } else null
                    )
                }
            } else {
                // Every row is its own lazy item, the way Game files does it: one card around the
                // whole list would compose four hundred rows the moment it scrolled into view,
                // which is a LazyColumn that is not lazy.
                items(state.rows.size) { index ->
                    VersionRow(state.rows[index], onChoose)
                    Spacer(Modifier.height(8.dp))
                }
            }
            // The old behaviour of the Install tile, kept and made findable rather than left on a
            // long press nobody announces. It is the only route for an installer jar the index
            // does not carry: an OptiFine build, a modpack's own installer, something handed over
            // by a friend.
            item {
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.loader_install_run_jar),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onRunJar)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
                Spacer(Modifier.height(28.dp))
            }
        }

        val chosen = state.chosen
        if (chosen != null) {
            InstallSheet(chosen.first, chosen.second, state.installing, onDismiss) { build ->
                onInstall(chosen.first, chosen.second, build)
            }
        }
    }
}

@Composable
private fun SnapshotToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (on) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainer)
            .clickable { onChange(!on) }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.loader_install_snapshots),
            style = MaterialTheme.typography.labelLarge,
            color = if (on) colors.primary else colors.onSurfaceVariant
        )
    }
}

/**
 * One Minecraft version, with what can be put on it.
 *
 * The loaders are pills rather than a list, because the useful thing about them here is which
 * ones exist, and four short words read faster than four rows.
 */
@Composable
private fun VersionRow(row: LoaderRow, onChoose: (LoaderRow, LoaderOption) -> Unit) {
    val colors = MaterialTheme.colorScheme
    SettingsCard {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.gameVersion,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!row.release) {
                    Text(
                        stringResource(R.string.loader_install_snapshot_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            // Wrapped by hand rather than with a flow layout: FlowRow is experimental in this
            // Compose version and four pills never need more than two lines.
            row.loaders.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pair.forEach { option ->
                        LoaderPill(option, Modifier.weight(1f)) { onChoose(row, option) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LoaderPill(option: LoaderOption, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (option.installed) colors.primary.copy(alpha = 0.13f)
                else colors.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                option.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (option.installed) colors.primary else colors.onSurface,
                maxLines = 1
            )
            Text(
                // The build it would install, said before the tap rather than after it. This is
                // the whole of what the second spinner used to be for.
                if (option.installed) stringResource(R.string.loader_install_have)
                else option.recommended.label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallSheet(
    row: LoaderRow,
    option: LoaderOption,
    installing: Boolean,
    onDismiss: () -> Unit,
    onInstall: (LoaderBuild) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = { if (!installing) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
        contentColor = colors.onSurface
    ) {
        AppSheetHeading(
            stringResource(R.string.loader_install_sheet_title, option.name, row.gameVersion),
            stringResource(
                if (option.installed) R.string.loader_install_sheet_have
                else R.string.loader_install_sheet_hint
            )
        )
        if (installing) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.5.dp, color = colors.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                SettingsCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        option.builds.take(20).forEach { build ->
                            BuildRow(build, build.id == option.recommended.id) { onInstall(build) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BuildRow(build: LoaderBuild, recommended: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                build.label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (recommended || !build.stable) {
                Text(
                    stringResource(
                        if (recommended) R.string.loader_install_recommended
                        else R.string.loader_install_prerelease
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (recommended) colors.primary else colors.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (recommended) colors.primary else colors.surfaceContainerHighest
                )
                .padding(horizontal = 15.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.loader_install_action),
                style = MaterialTheme.typography.labelLarge,
                color = if (recommended) Amethyst20 else colors.onSurface
            )
        }
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(
            stringResource(R.string.loader_install_retry),
            style = MaterialTheme.typography.labelLarge,
            color = Amethyst20
        )
    }
}
