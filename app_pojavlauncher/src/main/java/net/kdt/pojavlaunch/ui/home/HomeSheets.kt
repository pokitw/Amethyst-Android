package net.kdt.pojavlaunch.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.Amethyst70

/**
 * Choosing what to play.
 *
 * A sheet rather than the dropdown this replaces, because each row has to carry enough of the
 * profile — loader, renderer, whether it is even downloaded — to be chosen on sight rather than
 * by recognising a name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSheet(
    profiles: List<GameProfile>,
    selectedKey: String?,
    onDismiss: () -> Unit,
    onSelect: (GameProfile) -> Unit,
    onEdit: (GameProfile) -> Unit,
    onCreate: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        SheetHeading(
            stringResource(R.string.home_choose_version_title),
            stringResource(R.string.home_choose_version_hint)
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(profiles, key = { it.key }) { profile ->
                ProfileRow(
                    profile = profile,
                    selected = profile.key == selectedKey,
                    onClick = { onSelect(profile) },
                    onLongClick = { onEdit(profile) }
                )
            }
            item {
                SheetActionRow(
                    label = stringResource(R.string.create_profile),
                    onClick = onCreate
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileRow(
    profile: GameProfile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Amethyst70.copy(alpha = 0.11f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val notInstalled = stringResource(R.string.home_not_installed)
        SheetIcon(profile.icon)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildList {
                    profile.loader?.let { add(it) }
                    add(profile.details())
                    if (!profile.installed) add(notInstalled)
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Switching player.
 *
 * The account list used to live inside a spinner dropdown, which meant the only way to remove one
 * was to open the dropdown and find a small icon inside it. Here it is a row action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    accounts: List<Account>,
    currentAccount: String?,
    onDismiss: () -> Unit,
    onSelect: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onAdd: () -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<Account?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        SheetHeading(
            stringResource(R.string.home_choose_account_title),
            stringResource(
                if (accounts.isEmpty()) R.string.home_choose_account_empty
                else R.string.home_choose_account_hint
            )
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 380.dp).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(accounts, key = { it.username }) { account ->
                AccountRow(
                    account = account,
                    selected = account.username == currentAccount,
                    onClick = { onSelect(account) },
                    onRemove = { pendingRemoval = account }
                )
            }
            item {
                SheetActionRow(
                    label = stringResource(R.string.main_add_account),
                    onClick = onAdd
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    pendingRemoval?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(account.username, style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(R.string.warning_remove_account)) },
            confirmButton = {
                TextButton(onClick = { pendingRemoval = null; onDelete(account) }) {
                    Text(
                        stringResource(R.string.global_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

@Composable
private fun AccountRow(
    account: Account,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Amethyst70.copy(alpha = 0.11f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(account.face, account.username, size = 40.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.username,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(
                    when {
                        account.isDemo -> R.string.home_account_demo
                        account.isLocal -> R.string.home_account_offline
                        else -> R.string.home_account_online
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.global_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SheetHeading(title: String, hint: String) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The "and one more" row that closes both sheets: create a profile, add an account. */
@Composable
private fun SheetActionRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(13.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SheetIcon(icon: ImageBitmap?) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}
