package net.kdt.pojavlaunch.ui.mods

import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppScaffold
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.SlotWell

/**
 * The mods installed for the profile that is about to be played.
 *
 * Installing a mod used to mean leaving the launcher: open a file manager, find
 * `Android/data/org.angelauramc.amethyst/files/…/mods`, and drop a jar in, having first worked out
 * which of several folders that is. Nothing in the app could tell you what was already there, what
 * it was called, or whether it was even loading.
 *
 * So the folder is a screen. Each jar is read for what it says about itself — Fabric, Quilt,
 * Forge and NeoForge each write that somewhere different — and shown with its own icon, its
 * version and what it is for. Turning one off renames it rather than deleting it, which is the
 * thing you actually want when you are bisecting a crash.
 */
@Composable
fun ModsScreen(
    mods: List<Mod>,
    profileName: String?,
    loading: Boolean,
    onAdd: () -> Unit,
    onToggle: (Mod, Boolean) -> Unit,
    onDelete: (Mod) -> Unit,
    onBack: () -> Unit
) {
    var confirming by remember { mutableStateOf<Mod?>(null) }
    val enabled = mods.count { it.enabled }

    AppScaffold(
        title = stringResource(R.string.mods_title),
        subtitle = profileName,
        onBack = onBack,
        barAction = { AddButton(onAdd) }
    ) {
        Spacer(Modifier.height(4.dp))
        if (mods.isEmpty()) {
            EmptyState(loading, onAdd)
        } else {
            Text(
                stringResource(R.string.mods_summary, mods.size, enabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (mod in mods) {
                    ModRow(
                        mod = mod,
                        onToggle = { onToggle(mod, it) },
                        onDelete = { confirming = mod }
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    val target = confirming
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.mods_delete_title, target.name)) },
            text = { Text(stringResource(R.string.mods_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onDelete(target)
                }) {
                    Text(
                        stringResource(R.string.mods_delete_confirm),
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
 * One mod.
 *
 * The switch is the row's main control rather than a menu behind a long press, because turning
 * mods off one at a time is the single thing anyone opens this screen to do. Deleting is a tap on
 * the size — quiet, off to the side, and behind a confirmation.
 */
@Composable
private fun ModRow(mod: Mod, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // A disabled mod is still listed, just dimmed, so a folder half turned off still reads as one
    // list rather than as two.
    val titleColor by animateColorAsState(
        targetValue = if (mod.enabled) colors.onSurface else colors.onSurfaceVariant,
        animationSpec = tween(200),
        label = "modTitle"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .clickable { onToggle(!mod.enabled) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModIcon(mod)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mod.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (mod.version != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        mod.version,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (!mod.description.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    mod.description.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mod.loader != ModLoader.UNKNOWN) {
                    LoaderBadge(mod.loader)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    Formatter.formatShortFileSize(LocalContext.current, mod.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.mods_remove),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = mod.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Amethyst20,
                checkedTrackColor = colors.primary
            )
        )
    }
}

/**
 * The mod's own icon, in an inventory slot.
 *
 * A jar without one falls back to the initial in the same well rather than to a generic puzzle
 * piece, because a wall of identical placeholders is harder to scan than a wall of letters.
 */
@Composable
private fun ModIcon(mod: Mod) {
    SlotWell(size = 46.dp) {
        val bitmap = mod.icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Text(
                mod.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoaderBadge(loader: ModLoader) {
    val label = when (loader) {
        ModLoader.FABRIC -> R.string.mods_loader_fabric
        ModLoader.QUILT -> R.string.mods_loader_quilt
        ModLoader.FORGE -> R.string.mods_loader_forge
        ModLoader.NEOFORGE -> R.string.mods_loader_neoforge
        ModLoader.UNKNOWN -> R.string.mods_loader_unknown
    }
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/** The first thing a new modder sees, so it says what to do rather than that there is nothing. */
@Composable
private fun EmptyState(loading: Boolean, onAdd: () -> Unit) {
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
            stringResource(if (loading) R.string.mods_loading else R.string.mods_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        if (!loading) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.mods_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.mods_add),
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
            stringResource(R.string.mods_add),
            style = MaterialTheme.typography.labelLarge,
            color = Amethyst20
        )
    }
}
