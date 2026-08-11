package net.kdt.pojavlaunch.ui.skin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppEmptyState
import net.kdt.pojavlaunch.ui.common.AppScaffold
import net.kdt.pojavlaunch.ui.common.AppSearchField
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.theme.Amethyst20

/**
 * Finding a skin that is not already on this device.
 *
 * <b>Three sources, and they are not the same kind of thing, so the screen says which is which.</b>
 *
 * *Any player* is the big one, and it is Mojang's own public API rather than a skin site: a name
 * gives a UUID, a UUID gives the textures. Every account that has ever uploaded a skin is in it,
 * which makes it the largest skin database there is and the one every skin site is built on top
 * of. It is a lookup and not a catalogue, because a name is what it takes.
 *
 * *On your account* is what Mojang says you are wearing right now, which is worth having on its
 * own: a skin set from the website or another device is otherwise invisible here.
 *
 * *Recently worn* is the launcher's own record. <b>It has to be, because Mojang keeps no skin
 * history</b>, and the screen says as much rather than implying the list is complete. It starts
 * when the launcher starts keeping it, and every entry holds its own copy of the picture so it
 * still means something after the library has moved on.
 */
@Immutable
class BrowsedSkin(
    val name: String,
    val image: ImageBitmap?,
    val slim: Boolean,
    /** The line under the name: a UUID, a variant, or where it came from. */
    val detail: String
)

// A data class, because the activity copies it on every keystroke. @Immutable is a promise to
// Compose about stability and does not generate copy(); only `data` does. That distinction has
// already cost one CI run on this screen's sibling.
@Immutable
data class SkinBrowseState(
    val query: String = "",
    val searching: Boolean = false,
    /** The player that was looked up, or null when nothing has been found yet. */
    val found: BrowsedSkin? = null,
    /** Set when a search came back with something to say: no such player, rate limited, offline. */
    val message: String? = null,
    val messageIsError: Boolean = false,
    val account: List<BrowsedSkin> = emptyList(),
    val accountLoading: Boolean = false,
    /** Told plainly when the account cannot be asked at all, which offline accounts cannot. */
    val accountUnavailable: Boolean = false,
    val history: List<BrowsedSkin> = emptyList()
)

@Composable
fun SkinBrowseScreen(
    state: SkinBrowseState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSave: (BrowsedSkin) -> Unit,
    onForget: (BrowsedSkin) -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            title = stringResource(R.string.skin_browse_title),
            subtitle = stringResource(R.string.skin_browse_subtitle),
            onBack = onBack
        ) {
            Spacer(Modifier.height(12.dp))
            AppSearchField(
                query = state.query,
                onQueryChange = onQuery,
                placeholder = stringResource(R.string.skin_browse_search_hint),
                onSubmit = onSearch
            )

            if (state.searching) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp, color = colors.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            val message = state.message
            if (message != null && !state.searching) {
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.messageIsError) colors.error else colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            val found = state.found
            if (found != null && !state.searching) {
                Spacer(Modifier.height(16.dp))
                FoundCard(found, onSave)
            }

            if (state.account.isNotEmpty() || state.accountLoading || state.accountUnavailable) {
                SectionLabel(stringResource(R.string.skin_browse_account))
                when {
                    state.accountLoading -> SettingsCard {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.5.dp, color = colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    // An offline account has no Mojang profile to ask about, which is the same
                    // wall the apply button lives behind. Said once, here, rather than left as an
                    // empty row somebody has to work out the meaning of.
                    state.accountUnavailable -> SettingsCard {
                        Text(
                            stringResource(R.string.skin_browse_account_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(15.dp)
                        )
                    }
                    else -> SkinRail(state.account, onSave, null)
                }
            }

            SectionLabel(stringResource(R.string.skin_browse_history))
            if (state.history.isEmpty()) {
                SettingsCard {
                    Text(
                        stringResource(R.string.skin_browse_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(15.dp)
                    )
                }
            } else {
                SkinRail(state.history, onSave, onForget)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.skin_browse_history_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (state.found == null && state.history.isEmpty() && state.account.isEmpty()
                && !state.searching && !state.accountLoading && state.query.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                AppEmptyState(
                    iconRes = R.drawable.ic_x_skin,
                    title = stringResource(R.string.skin_browse_empty_title),
                    body = stringResource(R.string.skin_browse_empty_body)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The player that was found, on the model.
 *
 * Big, because a skin is entirely a picture and this is the moment somebody decides whether they
 * want it. The name and the model go under it rather than beside it, so a sixteen character name
 * has room and the picture keeps the width.
 */
@Composable
private fun FoundCard(found: BrowsedSkin, onSave: (BrowsedSkin) -> Unit) {
    val colors = MaterialTheme.colorScheme
    SettingsCard {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val image = found.image
                if (image != null) {
                    SkinPreview(
                        skin = image,
                        slim = found.slim,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                } else {
                    Text(
                        stringResource(R.string.skin_browse_no_texture),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        found.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        found.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (found.image != null) {
                    Spacer(Modifier.width(12.dp))
                    SaveButton { onSave(found) }
                }
            }
        }
    }
}

/**
 * A row of skins, drawn as pictures.
 *
 * A rail rather than a list for the same reason the texture packs got one: a skin is entirely a
 * picture, and choosing one from a column of names is absurd. Long press forgets an entry where
 * forgetting means anything, which the section's own note says.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkinRail(
    skins: List<BrowsedSkin>,
    onSave: (BrowsedSkin) -> Unit,
    onForget: ((BrowsedSkin) -> Unit)?
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(skins.size) { index ->
            SkinRailTile(skins[index], onSave, onForget)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkinRailTile(
    skin: BrowsedSkin,
    onSave: (BrowsedSkin) -> Unit,
    onForget: ((BrowsedSkin) -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(112.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .then(
                // Long press forgets, where forgetting means anything. Same gesture the rest of
                // the launcher uses for a second meaning, and the section's note says so.
                if (onForget == null) Modifier.clickable { onSave(skin) }
                else Modifier.combinedClickable(
                    onClick = { onSave(skin) },
                    onLongClick = { onForget(skin) }
                )
            )
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            val image = skin.image
            if (image != null) {
                SkinPreview(
                    skin = image,
                    slim = skin.slim,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            skin.name,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            skin.detail,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SaveButton(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(
            stringResource(R.string.skin_browse_save),
            style = MaterialTheme.typography.labelLarge,
            color = Amethyst20
        )
    }
}
