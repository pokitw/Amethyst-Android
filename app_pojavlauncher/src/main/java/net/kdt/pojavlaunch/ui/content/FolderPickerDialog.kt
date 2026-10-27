package net.kdt.pojavlaunch.ui.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import java.io.File

/**
 * Choose a folder inside the game directory to drop a file the launcher could not place itself.
 *
 * The add button works by looking inside a file and deciding where it belongs, which covers mods,
 * packs, worlds and screenshots and is the reason Game files is one screen rather than five. What
 * it could not do was anything outside that list: a config file, a datapack, an options.txt
 * somebody was sent. Those were refused with a toast, and since Android 11 cannot browse to the
 * game folder at all, a refusal there meant the file could never get in by any route.
 *
 * So the refusal becomes a question. It is a plain directory walk rather than a tree: one folder
 * at a time, up and down, with the destination and the name it will be given both on screen
 * before anything is written.
 *
 * **It cannot leave the game folder.** Up is hidden at the root, and the copy itself re-checks by
 * canonical path, because the two are different guarantees: this one is about what the player can
 * reach, that one is about what a symlink could point at.
 */
@Composable
fun FolderPickerDialog(
    fileName: String,
    gameDir: File,
    onDismiss: () -> Unit,
    onConfirm: (File) -> Unit,
) {
    var here by remember { mutableStateOf(gameDir) }
    val folders = remember(here) { subFolders(here) }
    val atRoot = remember(here) {
        runCatching { here.canonicalFile == gameDir.canonicalFile }.getOrDefault(here == gameDir)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.content_place_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.content_place_body, fileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    relativeLabel(gameDir, here),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                // Capped and lazy: a resourcepacks folder can hold a few hundred entries, and a
                // dialog that grows past the screen cannot be dismissed on a short phone.
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    if (!atRoot) {
                        item("up") {
                            FolderRow(
                                label = stringResource(R.string.content_place_up),
                                onClick = { here = here.parentFile ?: gameDir },
                            )
                        }
                    }
                    items(folders, key = { it.absolutePath }) { folder ->
                        FolderRow(label = folder.name, onClick = { here = folder })
                    }
                    if (folders.isEmpty()) {
                        item("empty") {
                            Text(
                                stringResource(R.string.content_place_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(here) }) {
                Text(stringResource(R.string.content_place_here))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun FolderRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_x_files),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(2.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
