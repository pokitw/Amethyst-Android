package net.kdt.pojavlaunch.ui.recordings

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R

/**
 * The recordings gallery.
 *
 * Rows lead with a preview so a clip is recognised before it is read, and the details that would
 * otherwise crowd the row are kept to a single quiet line beneath the essentials.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    recordings: List<Recording>,
    sort: RecordingSort,
    versionFilter: String?,
    availableVersions: List<String>,
    onSortChange: (RecordingSort) -> Unit,
    onVersionFilterChange: (String?) -> Unit,
    onPlay: (Recording) -> Unit,
    onShare: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pendingDeletion by remember { mutableStateOf<Recording?>(null) }
    var actionsFor by remember { mutableStateOf<Recording?>(null) }

    val totalBytes = recordings.sumOf { it.sizeBytes }
    val summary = stringResource(
        R.string.recordings_count,
        recordings.size,
        Formatter.formatShortFileSize(context, totalBytes)
    )

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.preference_recorder_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.recordings_back)
                            )
                        }
                    },
                    actions = {
                        FilterAction(versionFilter, availableVersions, onVersionFilterChange)
                        SortAction(sort, onSortChange)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { insets ->
            if (recordings.isEmpty()) {
                EmptyState(Modifier.padding(insets))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(insets),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recordings, key = { it.key }) { recording ->
                        RecordingRow(
                            recording = recording,
                            onClick = { onPlay(recording) },
                            onMore = { actionsFor = recording }
                        )
                    }
                }
            }
        }
    }

    actionsFor?.let { recording ->
        ActionsDialog(
            recording = recording,
            onDismiss = { actionsFor = null },
            onPlay = { actionsFor = null; onPlay(recording) },
            onShare = { actionsFor = null; onShare(recording) },
            onDelete = { actionsFor = null; pendingDeletion = recording }
        )
    }

    pendingDeletion?.let { recording ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.recordings_action_delete)) },
            text = { Text(stringResource(R.string.recordings_delete_confirm, recording.file.name)) },
            confirmButton = {
                TextButton(onClick = { pendingDeletion = null; onDelete(recording) }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

@Composable
private fun RecordingRow(recording: Recording, onClick: () -> Unit, onMore: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(recording)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    recording.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    recording.sizeLabel + " · " + DateUtils.getRelativeTimeSpanString(
                        recording.modifiedAt, System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                recording.details()?.let { details ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        details,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            IconButton(onClick = onMore) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.recordings_action_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Preview frame with the length over it, or a placeholder while the file is still being read. */
@Composable
private fun Thumbnail(recording: Recording) {
    Box(
        modifier = Modifier
            .width(104.dp)
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val thumbnail = recording.thumbnail
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_setting_recordings),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        recording.durationMs?.let { duration ->
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun SortAction(sort: RecordingSort, onSortChange: (RecordingSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_recordings_sort),
                contentDescription = stringResource(R.string.recordings_sort_title)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecordingSort.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    trailingIcon = { if (option == sort) Icon(Icons.Filled.Check, null) },
                    onClick = { expanded = false; onSortChange(option) }
                )
            }
        }
    }
}

@Composable
private fun FilterAction(
    versionFilter: String?,
    availableVersions: List<String>,
    onVersionFilterChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_recordings_filter),
                contentDescription = stringResource(R.string.recordings_filter_title),
                // Tinted while a filter is on, so a short list is never mistaken for an empty one.
                tint = if (versionFilter == null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.recordings_filter_all)) },
                trailingIcon = { if (versionFilter == null) Icon(Icons.Filled.Check, null) },
                onClick = { expanded = false; onVersionFilterChange(null) }
            )
            availableVersions.forEach { version ->
                DropdownMenuItem(
                    text = { Text(version) },
                    trailingIcon = { if (versionFilter == version) Icon(Icons.Filled.Check, null) },
                    onClick = { expanded = false; onVersionFilterChange(version) }
                )
            }
        }
    }
}

@Composable
private fun ActionsDialog(
    recording: Recording,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recording.title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                DialogAction(Icons.Filled.PlayArrow, R.string.recordings_action_play, onPlay)
                DialogAction(Icons.Filled.Share, R.string.recordings_action_share, onShare)
                DialogAction(
                    Icons.Filled.Delete, R.string.recordings_action_delete, onDelete,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun DialogAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painterResource(R.drawable.ic_setting_recordings),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.recordings_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.recordings_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
