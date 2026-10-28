package net.kdt.pojavlaunch.ui.content

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppSheetHeading
import net.kdt.pojavlaunch.ui.settings.ChoiceRow
import net.kdt.pojavlaunch.ui.settings.InfoRow
import net.kdt.pojavlaunch.ui.settings.NavRow
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.settings.SwitchRow
import net.kdt.pojavlaunch.ui.settings.TextRow

/** The one thing a sheet asked for, handed back to the activity to do off the main thread. */
sealed class WorldAction {
    data class Rename(val name: String) : WorldAction()
    data class SetGameMode(val mode: Int) : WorldAction()
    data class SetDifficulty(val level: Int) : WorldAction()
    data class SetCheats(val on: Boolean) : WorldAction()
    object ClearHardcore : WorldAction()
    object Duplicate : WorldAction()
    object Export : WorldAction()
    object Delete : WorldAction()
}

/**
 * Everything you can do to a world without launching the game.
 *
 * Tapping a world used to hand its folder to whatever app the device offered for a directory,
 * which from Android 11 is nothing at all. So a world was a row you could read and delete, and
 * every other question about it, including the two the game itself refuses to answer, needed a
 * desktop and a file manager.
 *
 * **Built from the settings components**, like the rest of Game files. A sheet with a vocabulary
 * of its own would read as a different application two taps from Settings, which is the mistake
 * this screen already made once and corrected.
 *
 * **Ordered by how reversible each thing is.** Naming and the switches at the top, because they
 * are ordinary and undoable; copying and exporting below them, because they make something new;
 * deleting last, in the error colour, behind a confirmation, the way every destructive action in
 * this launcher is placed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldSheet(
    details: WorldDetails,
    busy: Boolean,
    onAction: (WorldAction) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var confirmHardcore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        AppSheetHeading(
            details.name,
            stringResource(
                if (details.editable) R.string.world_sheet_hint
                else R.string.world_sheet_unreadable
            )
        )
        Column(
            Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard {
                InfoRow(
                    title = stringResource(R.string.world_detail_version),
                    value = details.version ?: stringResource(R.string.world_detail_unknown)
                )
                InfoRow(
                    title = stringResource(R.string.world_detail_size),
                    value = if (details.sizeBytes > 0L) {
                        Formatter.formatShortFileSize(context, details.sizeBytes)
                    } else {
                        stringResource(R.string.world_detail_unknown)
                    }
                )
                InfoRow(
                    title = stringResource(R.string.world_detail_played),
                    value = if (details.lastPlayedMs > 0L) {
                        DateUtils.getRelativeTimeSpanString(
                            details.lastPlayedMs, System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    } else {
                        stringResource(R.string.world_detail_unknown)
                    }
                )
                // Shown rather than editable: changing the seed of a world that has already
                // generated chunks does nothing to the ones that exist and quietly breaks the
                // join between them and everything generated afterwards.
                details.seed?.let {
                    InfoRow(
                        title = stringResource(R.string.world_detail_seed),
                        value = it.toString()
                    )
                }
                // The folder name is worth showing precisely because it is NOT the world's
                // name: somebody reading a crash log or a backup needs to know which folder
                // this is, and the two can differ.
                InfoRow(
                    title = stringResource(R.string.world_detail_folder),
                    value = details.folderName
                )
            }

            if (details.editable) {
                SectionLabel(stringResource(R.string.world_section_settings))
                // A field the file does not carry is shown and not offered. The splice
                // replaces a tag that exists and cannot add one, so a picker over an absent
                // GameType would be a control whose every option reports a failure, which the
                // note below used to say was the thing to avoid and then shipped.
                SettingsCard {
                    if (details.canSetName) {
                        TextRow(
                            title = stringResource(R.string.world_rename_title),
                            description = stringResource(R.string.world_rename_description),
                            value = details.name,
                            placeholder = stringResource(R.string.world_rename_placeholder),
                            onValueChange = { if (!busy) onAction(WorldAction.Rename(it)) }
                        )
                    }
                    if (details.canSetGameMode) {
                        ChoiceRow(
                            title = stringResource(R.string.world_mode_title),
                            description = stringResource(R.string.world_mode_description),
                            names = gameModeNames(),
                            values = MODE_VALUES,
                            selected = details.gameMode.toString(),
                            onSelect = { if (!busy) onAction(WorldAction.SetGameMode(it.toInt())) }
                        )
                    } else {
                        InfoRow(
                            title = stringResource(R.string.world_mode_title),
                            description = stringResource(R.string.world_field_absent),
                            value = stringResource(R.string.world_detail_unknown)
                        )
                    }
                    if (details.canSetDifficulty) {
                        ChoiceRow(
                            title = stringResource(R.string.world_difficulty_title),
                            description = stringResource(R.string.world_difficulty_description),
                            names = difficultyNames(),
                            values = MODE_VALUES,
                            selected = details.difficulty.toString(),
                            onSelect = { if (!busy) onAction(WorldAction.SetDifficulty(it.toInt())) }
                        )
                    } else {
                        InfoRow(
                            title = stringResource(R.string.world_difficulty_title),
                            description = stringResource(R.string.world_field_absent),
                            value = stringResource(R.string.world_detail_unknown)
                        )
                    }
                    if (details.canSetCheats) {
                        SwitchRow(
                            stringResource(R.string.world_cheats_title),
                            stringResource(R.string.world_cheats_description),
                            details.cheats
                        ) { if (!busy) onAction(WorldAction.SetCheats(it)) }
                    }
                }

                // Its own section, not a fourth switch above. Turning hardcore off is the one
                // thing in this sheet the game will never offer and the one thing nobody does
                // by accident, so it is named for what it is for rather than for the flag.
                if (details.hardcore) {
                    SectionLabel(stringResource(R.string.world_section_hardcore))
                    SettingsCard {
                        NavRow(
                            title = stringResource(R.string.world_hardcore_title),
                            description = stringResource(R.string.world_hardcore_description),
                            onClick = { if (!busy) confirmHardcore = true }
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.world_section_copies))
            SettingsCard {
                NavRow(
                    title = stringResource(R.string.world_duplicate_title),
                    description = stringResource(R.string.world_duplicate_description),
                    onClick = { if (!busy) onAction(WorldAction.Duplicate) }
                )
                NavRow(
                    title = stringResource(R.string.world_export_title),
                    description = stringResource(R.string.world_export_description),
                    onClick = { if (!busy) onAction(WorldAction.Export) }
                )
            }

            SectionLabel(stringResource(R.string.world_section_remove))
            SettingsCard {
                NavRow(
                    title = stringResource(R.string.world_delete_title),
                    description = stringResource(R.string.world_delete_description),
                    onClick = { if (!busy) confirmDelete = true }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmHardcore) {
        ConfirmDialog(
            title = stringResource(R.string.world_hardcore_confirm_title),
            body = stringResource(R.string.world_hardcore_confirm_body),
            confirm = stringResource(R.string.world_hardcore_confirm_action),
            onConfirm = { confirmHardcore = false; onAction(WorldAction.ClearHardcore) },
            onDismiss = { confirmHardcore = false }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.content_delete_title, details.name),
            body = stringResource(R.string.content_delete_world),
            confirm = stringResource(R.string.content_delete_confirm),
            onConfirm = { confirmDelete = false; onAction(WorldAction.Delete) },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Minecraft's own numbers, and only the four that can be written.
 *
 * There is no "not set" entry. A picker is shown only where the tag exists, so the current
 * value is always one of these, and an entry that can be picked and cannot be written is the
 * one thing a picker must not have.
 */
private val MODE_VALUES = listOf("0", "1", "2", "3")

@Composable
private fun gameModeNames(): List<String> = listOf(
    stringResource(R.string.world_mode_survival),
    stringResource(R.string.world_mode_creative),
    stringResource(R.string.world_mode_adventure),
    stringResource(R.string.world_mode_spectator)
)

@Composable
private fun difficultyNames(): List<String> = listOf(
    stringResource(R.string.world_difficulty_peaceful),
    stringResource(R.string.world_difficulty_easy),
    stringResource(R.string.world_difficulty_normal),
    stringResource(R.string.world_difficulty_hard)
)
