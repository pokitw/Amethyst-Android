package net.kdt.pojavlaunch.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.Warning70

/** What the player chose to do with a file that has been read but not placed. */
sealed class ImportChoice {
    /** Put it in the folder the launcher worked out. */
    object Place : ImportChoice()
    /** Put it there, deleting the copy of the same mod that is already in it. */
    object Replace : ImportChoice()
    /** Open the folder picker instead. */
    object Choose : ImportChoice()
    object Cancel : ImportChoice()
}

/**
 * Confirm where a file is about to go, before it goes there.
 *
 * The add button works by looking inside a file and deciding where it belongs, and that is still
 * the right default: nobody thinks "I would like to place a file in the resourcepacks
 * directory". What was wrong was that the decision was also the action. A guess that is right
 * nearly always is still a guess, and the two questions most worth asking of an import both
 * arrive in the gap between deciding and writing: is this really a mod, and do you know you
 * already have this one.
 *
 * So the guess is shown with its answer attached. One tap accepts it, which is no more work
 * than before, and the alternative is one tap away instead of impossible.
 *
 * **The duplicate warning is the reason this is worth a dialog rather than a toast
 * afterwards.** Two jars declaring one mod id is a loader that refuses to start, naming an id
 * the player has never seen, and the usual way to get there is the most ordinary thing anybody
 * does: updating a mod by adding the new file and forgetting the old one. Replace is offered
 * first in that case, because it is almost always what was meant.
 */
@Composable
fun ImportChoiceDialog(
    plan: ImportPlan,
    onChoice: (ImportChoice) -> Unit
) {
    val kind = plan.kind
    val destination = if (kind != null) stringResource(kind.titleRes) else ""
    AlertDialog(
        onDismissRequest = { onChoice(ImportChoice.Cancel) },
        title = {
            Text(
                stringResource(R.string.content_import_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        if (plan.unpacks) R.string.content_import_unpack
                        else R.string.content_import_body,
                        plan.landingName,
                        destination
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                plan.duplicateOf?.let { existing ->
                    Text(
                        stringResource(R.string.content_import_duplicate, existing),
                        style = MaterialTheme.typography.bodyMedium,
                        // Warning and not error, the same as every other verdict this screen
                        // reaches by reading somebody else's metadata: nothing has failed yet,
                        // and keeping both copies is a choice rather than a mistake.
                        color = Warning70,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .padding(vertical = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onChoice(
                        if (plan.duplicateOf != null) ImportChoice.Replace else ImportChoice.Place
                    )
                }
            ) {
                Text(
                    stringResource(
                        when {
                            plan.duplicateOf != null -> R.string.content_import_replace
                            plan.unpacks -> R.string.content_import_unpack_action
                            else -> R.string.content_import_place
                        }
                    ),
                    // Replacing deletes the jar that is already there, which is the one thing
                    // in this dialog that cannot be undone by tapping something else.
                    color = if (plan.duplicateOf != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            // Three choices in two slots. Cancel is spelled out rather than left to a tap
            // outside the dialog, because the primary action here can delete a file and an
            // escape that is only a gesture is not an escape everybody knows about.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onChoice(ImportChoice.Cancel) }) {
                    Text(stringResource(android.R.string.cancel))
                }
                // Keeping both is the alternative when there is a duplicate: choosing a folder
                // for a mod you are updating is not something anybody wants to do. Without one
                // it is "choose a folder", which is the escape hatch a wrong guess needs.
                TextButton(
                    onClick = {
                        onChoice(
                            if (plan.duplicateOf != null) ImportChoice.Place else ImportChoice.Choose
                        )
                    }
                ) {
                    Text(
                        stringResource(
                            if (plan.duplicateOf != null) R.string.content_import_keep_both
                            else R.string.content_import_choose
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
