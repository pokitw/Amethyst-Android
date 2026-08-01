package net.kdt.pojavlaunch.ui.profile

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppScaffold
import net.kdt.pojavlaunch.ui.settings.ChoiceRow
import net.kdt.pojavlaunch.ui.settings.NavRow
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.settings.TextRow
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.Amethyst50

/** Everything the editor shows that it cannot work out for itself. */
@Immutable
data class ProfileEditorState(
    val name: String = "",
    val versionId: String = "",
    val versionInstalled: Boolean = true,
    val icon: ImageBitmap? = null,
    val rendererNames: List<String> = emptyList(),
    val rendererIds: List<String> = emptyList(),
    val rendererSelected: String = "",
    val runtimes: List<String> = emptyList(),
    val runtimeSelected: String = "",
    val controlLayouts: List<String> = emptyList(),
    val controlSelected: String = "",
    val gameDir: String = "",
    val javaArgs: String = "",
    /** A profile being created has nothing to delete yet, and the last one may not be removed. */
    val deletable: Boolean = true
)

/** What the editor can set in motion. */
@Immutable
class ProfileEditorActions(
    val onName: (String) -> Unit = {},
    val onPickVersion: () -> Unit = {},
    val onPickIcon: () -> Unit = {},
    val onRenderer: (String) -> Unit = {},
    val onRuntime: (String) -> Unit = {},
    val onControl: (String) -> Unit = {},
    val onGameDir: () -> Unit = {},
    val onJavaArgs: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBack: () -> Unit = {}
)

/**
 * The profile editor.
 *
 * It was a single scrolling form of nine controls at equal weight — two spinners, three text
 * fields and four buttons that each opened a file browser — with the version, the one thing a
 * profile is really *about*, third from the top in a plain text field. Here the version and the
 * icon are the header, because together they are how you recognise the profile everywhere else in
 * the launcher; the rest is grouped by how often it is touched, and everything that only matters
 * to someone debugging a launch sits under Advanced.
 */
@Composable
fun ProfileEditorScreen(
    state: ProfileEditorState,
    creating: Boolean,
    actions: ProfileEditorActions
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val defaultLabel = stringResource(R.string.global_default)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            title = stringResource(
                if (creating) R.string.create_profile else R.string.profile_editor_title
            ),
            subtitle = stringResource(R.string.profile_editor_subtitle),
            onBack = actions.onBack,
            barAction = { SaveButton(actions.onSave) }
        ) {
            Spacer(Modifier.height(14.dp))
            ProfileHeader(state, actions)

            SectionLabel(stringResource(R.string.profile_section_basics))
            SettingsCard {
                TextRow(
                    title = stringResource(R.string.global_name),
                    description = stringResource(R.string.profile_name_description),
                    value = state.name,
                    placeholder = stringResource(R.string.profile_name_placeholder),
                    onValueChange = actions.onName
                )
                if (state.rendererIds.isNotEmpty()) {
                    ChoiceRow(
                        title = stringResource(R.string.settings_renderer_title),
                        description = stringResource(R.string.settings_renderer_description),
                        // The empty value stands for "follow the global default", which is how a
                        // profile stores an unset renderer.
                        names = state.rendererNames + defaultLabel,
                        values = state.rendererIds + "",
                        selected = state.rendererSelected,
                        onSelect = actions.onRenderer
                    )
                }
            }

            SectionLabel(stringResource(R.string.profile_section_advanced))
            SettingsCard {
                if (state.runtimes.isNotEmpty()) {
                    ChoiceRow(
                        title = stringResource(R.string.multirt_title),
                        description = stringResource(R.string.profile_runtime_description),
                        names = state.runtimes + defaultLabel,
                        values = state.runtimes + "",
                        selected = state.runtimeSelected,
                        onSelect = actions.onRuntime
                    )
                }
                ChoiceRow(
                    title = stringResource(R.string.preference_control_title),
                    description = stringResource(R.string.profile_control_description),
                    names = state.controlLayouts + defaultLabel,
                    values = state.controlLayouts + "",
                    selected = state.controlSelected,
                    onSelect = actions.onControl
                )
                NavRow(
                    title = stringResource(R.string.profile_game_dir),
                    description = stringResource(R.string.profile_game_dir_description),
                    value = state.gameDir.ifEmpty { defaultLabel },
                    onClick = actions.onGameDir
                )
                TextRow(
                    title = stringResource(R.string.mcl_setting_title_javaargs),
                    description = stringResource(R.string.profile_java_args_description),
                    value = state.javaArgs,
                    placeholder = defaultLabel,
                    onValueChange = actions.onJavaArgs
                )
            }

            // Rare and irreversible, so it is last and quiet rather than a button of equal weight
            // beside Save, which is where it used to sit.
            if (state.deletable && !creating) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.profile_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { confirmDelete = true }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_message, state.name.ifEmpty { state.versionId })) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) {
                    Text(
                        stringResource(R.string.global_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

/**
 * The icon and the version, as one object.
 *
 * These are the two things that identify a profile in the version sheet and on the launch card, so
 * the editor leads with them rather than burying the version in the third text field down.
 */
@Composable
private fun ProfileHeader(state: ProfileEditorState, actions: ProfileEditorActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Amethyst50.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset.Zero,
                        radius = size.maxDimension * 1.05f
                    )
                )
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = actions.onPickIcon),
            contentAlignment = Alignment.Center
        ) {
            if (state.icon != null) {
                Image(
                    bitmap = state.icon,
                    contentDescription = stringResource(R.string.profile_icon),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp)
                )
            } else {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.profile_icon),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = actions.onPickVersion)
                .padding(vertical = 4.dp)
        ) {
            Text(
                stringResource(R.string.profile_version_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                state.versionId.ifEmpty { stringResource(R.string.home_version_unset) },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!state.versionInstalled && state.versionId.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(R.string.home_not_installed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SaveButton(onSave: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onSave)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.global_save),
            style = MaterialTheme.typography.labelLarge,
            color = Amethyst20
        )
    }
}
