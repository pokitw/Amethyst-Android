package net.kdt.pojavlaunch.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppBackButton
import net.kdt.pojavlaunch.ui.common.AppSearchField
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.Amethyst70

/**
 * Choosing a Minecraft version.
 *
 * It was a dialog holding a four-level `ExpandableListView`: to reach 1.20.1 you opened "Release"
 * and scrolled a list of six hundred ids with nothing but their names on them, and a version you
 * had already downloaded looked exactly like one you had not. Three things changed. It searches,
 * because you almost always know the number you want. The sections became filter chips rather than
 * collapsible groups, since only one of them is ever interesting at a time. And every row says
 * whether it is on disk and when it came out, so the choice can be made on sight.
 */
@Composable
fun VersionPickerScreen(
    versions: List<VersionEntry>,
    selected: String?,
    onPick: (String) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<VersionKind?>(null) }
    var installedOnly by remember { mutableStateOf(false) }

    val results = remember(versions, kind, query, installedOnly) {
        filterVersions(versions, kind, query, installedOnly)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppBackButton(onBack)
                Spacer(Modifier.width(4.dp))
                AppSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.version_picker_hint),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(stringResource(R.string.version_picker_all), kind == null) { kind = null }
                FilterChip(
                    stringResource(R.string.mcl_setting_veroption_release),
                    kind == VersionKind.RELEASE
                ) { kind = VersionKind.RELEASE }
                FilterChip(
                    stringResource(R.string.mcl_setting_veroption_snapshot),
                    kind == VersionKind.SNAPSHOT
                ) { kind = VersionKind.SNAPSHOT }
                FilterChip(
                    stringResource(R.string.mcl_setting_veroption_oldbeta),
                    kind == VersionKind.BETA
                ) { kind = VersionKind.BETA }
                FilterChip(
                    stringResource(R.string.mcl_setting_veroption_oldalpha),
                    kind == VersionKind.ALPHA
                ) { kind = VersionKind.ALPHA }
                FilterChip(
                    stringResource(R.string.mcl_setting_veroption_installed),
                    installedOnly
                ) { installedOnly = !installedOnly }
            }

            if (results.isEmpty()) {
                EmptyVersions(query)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(results, key = { it.id }) { entry ->
                        VersionRow(entry, entry.id == selected) { onPick(entry.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Amethyst20 else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun VersionRow(entry: VersionEntry, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Amethyst70.copy(alpha = 0.11f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.id,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = buildList {
                if (entry.released.isNotEmpty()) add(entry.released)
                if (entry.kind == VersionKind.INSTALLED) {
                    add(stringResource(R.string.version_picker_custom))
                }
            }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (entry.installed) {
            Text(
                stringResource(R.string.version_picker_downloaded),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Amethyst70.copy(alpha = 0.13f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyVersions(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(
                    if (query.isBlank()) R.string.version_picker_empty
                    else R.string.version_picker_no_match, query.trim()
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.version_picker_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
