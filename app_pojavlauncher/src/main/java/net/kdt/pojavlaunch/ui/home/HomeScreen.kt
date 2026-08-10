package net.kdt.pojavlaunch.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.Amethyst50
import net.kdt.pojavlaunch.ui.theme.Amethyst70

/** Everything the home screen can set in motion. Held together so the screen stays declarative. */
@Immutable
class HomeActions(
    val onPlay: () -> Unit = {},
    val onSelectProfile: (GameProfile) -> Unit = {},
    val onEditProfile: (GameProfile) -> Unit = {},
    val onCreateProfile: () -> Unit = {},
    val onSelectAccount: (Account) -> Unit = {},
    val onDeleteAccount: (Account) -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onControls: () -> Unit = {},
    val onRecordings: () -> Unit = {},
    val onSkins: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onInstallWithArguments: () -> Unit = {},
    val onFiles: () -> Unit = {},
    val onWiki: () -> Unit = {},
    val onGamepadMapper: () -> Unit = {},
    val onDiscord: () -> Unit = {},
    val onShareLogs: () -> Unit = {}
)

/**
 * The launcher's home screen.
 *
 * Three things happen here, and they happen at wildly different rates: you press Play every single
 * time, you change what you are playing occasionally, and you manage something rarely. The layout
 * ranks them in that order — one hero card, a grid under a heading, and a line of quiet links —
 * rather than giving nine controls the same weight and leaving the eye to sort it out.
 */
@Composable
fun HomeScreen(
    profiles: List<GameProfile>,
    selectedKey: String?,
    accounts: List<Account>,
    currentAccount: String?,
    recordingCount: Int,
    progress: LaunchProgress,
    actions: HomeActions
) {
    var versionSheet by remember { mutableStateOf(false) }
    var accountSheet by remember { mutableStateOf(false) }

    val selected = profiles.firstOrNull { it.key == selectedKey } ?: profiles.firstOrNull()
    val account = accounts.firstOrNull { it.username == currentAccount }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // The launcher lays its window out in the full screen when it is told to ignore
                // the notch, so the header asks for whatever inset has not already been applied
                // further up. Where the decor has handled it this comes back as nothing.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            HomeHeader(
                account = account,
                onAccount = { accountSheet = true },
                onSettings = actions.onSettings
            )
            Spacer(Modifier.height(20.dp))

            // The middle scrolls so the header and the links stay put on short screens, and so a
            // large font scale can never push the Play button out of reach.
            val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
            if (landscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        LaunchCard(selected, progress, { versionSheet = true }, actions.onPlay)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ManageSection(recordingCount, actions)
                    }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    LaunchCard(selected, progress, { versionSheet = true }, actions.onPlay)
                    Spacer(Modifier.height(24.dp))
                    ManageSection(recordingCount, actions)
                    Spacer(Modifier.height(16.dp))
                }
            }

            FooterLinks(actions)
            Spacer(Modifier.height(12.dp))
        }
    }

    if (versionSheet) {
        VersionSheet(
            profiles = profiles,
            selectedKey = selected?.key,
            onDismiss = { versionSheet = false },
            onSelect = { versionSheet = false; actions.onSelectProfile(it) },
            onEdit = { versionSheet = false; actions.onEditProfile(it) },
            onCreate = { versionSheet = false; actions.onCreateProfile() }
        )
    }

    if (accountSheet) {
        AccountSheet(
            accounts = accounts,
            currentAccount = currentAccount,
            onDismiss = { accountSheet = false },
            onSelect = { accountSheet = false; actions.onSelectAccount(it) },
            onDelete = actions.onDeleteAccount,
            onAdd = { accountSheet = false; actions.onAddAccount() }
        )
    }
}

@Composable
private fun HomeHeader(account: Account?, onAccount: () -> Unit, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.ic_x_gem),
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.home_brand_name))
                append(' ')
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                    append(stringResource(R.string.home_brand_mark))
                }
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        AccountChip(account, onAccount)
        Spacer(Modifier.width(8.dp))
        IconTile(R.drawable.ic_x_settings, R.string.home_settings, onSettings)
    }
}

/**
 * The account, as a chip.
 *
 * It used to be a full width bar above everything else — the most dominant element on the screen,
 * for something set once and then ignored for months. What it is actually for is confirming you
 * are the right person, which takes a glance.
 */
@Composable
private fun AccountChip(account: Account?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 168.dp)
    ) {
        Row(
            Modifier.padding(start = 5.dp, top = 5.dp, end = 12.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(account?.face, account?.username)
            Spacer(Modifier.width(8.dp))
            Text(
                account?.username ?: stringResource(R.string.home_sign_in),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The player's head, or their initial on the brand gradient while no skin has been cached. */
@Composable
fun Avatar(face: ImageBitmap?, username: String?, size: Dp = 26.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(Brush.linearGradient(listOf(Amethyst70, Amethyst50))),
        contentAlignment = Alignment.Center
    ) {
        when {
            face != null -> Image(
                bitmap = face,
                contentDescription = null,
                // Skin faces are tiny; leaving them unsmoothed keeps the pixel art crisp.
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(size)
            )
            username.isNullOrEmpty() -> Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Amethyst20,
                modifier = Modifier.size(size * 0.6f)
            )
            else -> Text(
                username.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = Amethyst20
            )
        }
    }
}

@Composable
private fun IconTile(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(iconRes),
                contentDescription = stringResource(labelRes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * The four things worth reaching for during a session.
 *
 * Previously six buttons of identical weight sat here, two of which were external links and one a
 * diagnostic. Splitting them by what they are — features here, everything else in the footer —
 * is what makes the grid scannable. Recordings joins them because in this build it is a feature
 * rather than something buried in settings.
 */
@Composable
private fun ManageSection(recordingCount: Int, actions: HomeActions) {
    Column {
        Text(
            stringResource(R.string.home_manage),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(
                    R.drawable.ic_x_controls,
                    stringResource(R.string.mcl_option_customcontrol),
                    stringResource(R.string.home_tile_controls_sub),
                    Modifier.weight(1f),
                    onClick = actions.onControls
                )
                Tile(
                    R.drawable.ic_x_recordings,
                    stringResource(R.string.preference_recorder_title),
                    if (recordingCount == 0) stringResource(R.string.home_tile_recordings_empty)
                    else pluralStringResource(
                        R.plurals.home_tile_recordings_sub, recordingCount, recordingCount
                    ),
                    Modifier.weight(1f),
                    onClick = actions.onRecordings
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(
                    R.drawable.ic_x_install,
                    stringResource(R.string.home_tile_install),
                    stringResource(R.string.home_tile_install_sub),
                    Modifier.weight(1f),
                    onClick = actions.onInstall,
                    onLongClick = actions.onInstallWithArguments
                )
                Tile(
                    R.drawable.ic_x_files,
                    stringResource(R.string.home_tile_files),
                    stringResource(R.string.home_tile_files_sub),
                    Modifier.weight(1f),
                    onClick = actions.onFiles
                )
            }
            // Full width rather than a fifth square in a ragged row. Skins are the one thing
            // here that is entirely a picture, so the tile that opens them has room to be one.
            Tile(
                R.drawable.ic_x_skin,
                stringResource(R.string.skin_home_tile),
                stringResource(R.string.skin_home_tile_sub),
                Modifier.fillMaxWidth(),
                onClick = actions.onSkins
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    iconRes: Int,
    name: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 15.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Reachable, but not competing: two external links and the one thing you need when a launch fails. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FooterLinks(actions: HomeActions) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FooterLink(
            stringResource(R.string.mcl_tab_wiki),
            onClick = actions.onWiki,
            onLongClick = actions.onGamepadMapper
        )
        FooterDot()
        FooterLink(stringResource(R.string.mcl_button_discord), onClick = actions.onDiscord)
        FooterDot()
        FooterLink(stringResource(R.string.main_share_logs), onClick = actions.onShareLogs)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FooterLink(text: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun FooterDot() {
    Box(
        Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline)
    )
}
