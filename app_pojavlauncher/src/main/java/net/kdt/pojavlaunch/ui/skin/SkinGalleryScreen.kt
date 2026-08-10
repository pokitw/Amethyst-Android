package net.kdt.pojavlaunch.ui.skin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppEmptyState
import net.kdt.pojavlaunch.ui.common.AppScaffold
import net.kdt.pojavlaunch.ui.common.AppSheetHeading
import net.kdt.pojavlaunch.ui.settings.ChoiceRow
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.SlotWell

/** One row of the gallery, with its picture already decoded by the activity. */
@Immutable
class SkinEntry(
    val stored: StoredSkin,
    val image: ImageBitmap?
)

/** Everything the gallery draws. */
@Immutable
class SkinGalleryState(
    val skins: List<SkinEntry> = emptyList(),
    val selected: SkinEntry? = null,
    val loading: Boolean = true,
    val applying: Boolean = false,
    /** Whether the signed-in account can actually wear a skin. */
    val canApply: Boolean = false,
    val accountName: String? = null,
    val note: String? = null,
    val noteIsError: Boolean = false
)

/**
 * The skins on this device, and the one being worn.
 *
 * <b>Why the launcher keeps skins at all.</b> The request came from someone who could not reach
 * their skin folder, and they were not being lazy: from Android 11 nothing can browse
 * `Android/data`, so a skin written there is a skin that cannot be picked up by anything. The
 * launcher is the only thing that can see that folder, so it is the thing that has to offer
 * them.
 *
 * <b>The offline case is stated, not hidden.</b> A server asks Mojang what a player looks like,
 * so an account that never signed in has nothing to ask about, which is what the community
 * thread concluded before this was built. The screen says so in a sentence and still lets the
 * editor be used, because making a skin is worth doing even when only the maker can see it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinGalleryScreen(
    state: SkinGalleryState,
    onSelect: (SkinEntry) -> Unit,
    onEdit: (SkinEntry) -> Unit,
    onDelete: (SkinEntry) -> Unit,
    onApply: () -> Unit,
    onSlim: (Boolean) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<SkinEntry?>(null) }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            title = stringResource(R.string.skin_title),
            subtitle = state.accountName ?: stringResource(R.string.skin_subtitle),
            onBack = onBack,
            barAction = { AddSkinButton { adding = true } }
        ) {
            Spacer(Modifier.height(14.dp))

            // The model, big, because a skin is entirely a picture and this is the screen where
            // you decide whether you like it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(colors.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                val selected = state.selected
                if (selected?.image != null) {
                    SkinPreview(
                        skin = selected.image,
                        slim = selected.stored.slim,
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
                } else if (state.loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp, color = colors.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            if (state.selected != null) {
                Spacer(Modifier.height(14.dp))
                SettingsCard {
                    ChoiceRow(
                        title = stringResource(R.string.skin_model),
                        description = stringResource(R.string.skin_model_description),
                        names = listOf(
                            stringResource(R.string.skin_model_classic),
                            stringResource(R.string.skin_model_slim)
                        ),
                        values = listOf("classic", "slim"),
                        selected = if (state.selected.stored.slim) "slim" else "classic",
                        onSelect = { onSlim(it == "slim") }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryAction(
                        label = stringResource(
                            if (state.applying) R.string.skin_applying else R.string.skin_apply
                        ),
                        enabled = state.canApply && !state.applying,
                        modifier = Modifier.weight(1f),
                        onClick = onApply
                    )
                    SecondaryAction(
                        label = stringResource(R.string.skin_edit),
                        onClick = { onEdit(state.selected) }
                    )
                }

                if (!state.canApply) {
                    Spacer(Modifier.height(12.dp))
                    OfflineNotice()
                }

                val note = state.note
                if (note != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.noteIsError) colors.error else colors.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            if (state.skins.isEmpty() && !state.loading) {
                Spacer(Modifier.height(22.dp))
                AppEmptyState(
                    iconRes = R.drawable.ic_x_skin,
                    title = stringResource(R.string.skin_empty_title),
                    body = stringResource(R.string.skin_empty_body)
                ) {
                    PrimaryAction(
                        label = stringResource(R.string.skin_new),
                        enabled = true,
                        onClick = onNew
                    )
                }
            } else if (state.skins.isNotEmpty()) {
                SectionLabel(stringResource(R.string.skin_section_yours))
                Text(
                    stringResource(R.string.skin_hold_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                // A rail of faces. A skin is recognised by its face long before its name, which
                // is why the thumbnail is the head and not the file name.
                state.skins.chunked(4).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { entry ->
                            SkinTile(
                                entry = entry,
                                selected = entry.stored.file == state.selected?.stored?.file,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(entry) },
                                onLongClick = { confirming = entry }
                            )
                        }
                        // Keeps a short last row the same tile size as a full one.
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (adding) {
        ModalBottomSheet(
            onDismissRequest = { adding = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceContainer,
            contentColor = colors.onSurface
        ) {
            AppSheetHeading(
                stringResource(R.string.skin_add_title),
                stringResource(R.string.skin_add_hint)
            )
            Column(
                Modifier.padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AddChoice(R.drawable.ic_x_pencil, R.string.skin_new) { adding = false; onNew() }
                AddChoice(R.drawable.ic_x_files, R.string.skin_import) { adding = false; onImport() }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    val target = confirming
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = {
                Text(
                    stringResource(R.string.skin_delete_title, target.stored.name),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = { Text(stringResource(R.string.skin_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirming = null; onDelete(target) }) {
                    Text(stringResource(R.string.skin_delete), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = colors.surfaceContainerHigh
        )
    }
}

/**
 * One skin, shown by its face.
 *
 * The head's front face with the hat drawn over it, scaled up with no smoothing: it is the
 * avatar every Minecraft player already recognises, and it reads at this size where a whole
 * model would not.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkinTile(
    entry: SkinEntry,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) colors.primary.copy(alpha = 0.13f) else colors.surfaceContainer
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SlotWell(size = 52.dp) {
            val image = entry.image
            if (image != null) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))) {
                    FaceLayer(image, 8, 8, 8, 8)
                    FaceLayer(image, 40, 8, 8, 8)
                }
            } else {
                Icon(
                    painterResource(R.drawable.ic_x_skin),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            entry.stored.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

/** One 8 by 8 patch of the atlas, blown up. Two of these stacked make the avatar. */
@Composable
private fun FaceLayer(image: ImageBitmap, x: Int, y: Int, w: Int, h: Int) {
    Image(
        painter = BitmapPainter(
            image,
            srcOffset = androidx.compose.ui.unit.IntOffset(x, y),
            srcSize = androidx.compose.ui.unit.IntSize(w, h),
            filterQuality = FilterQuality.None
        ),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun OfflineNotice() {
    val colors = MaterialTheme.colorScheme
    SettingsCard {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.skin_offline_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.skin_offline_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(CircleShape)
            .background(if (enabled) colors.primary else colors.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) Amethyst20 else colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = colors.primary,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    )
}

@Composable
private fun AddSkinButton(onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            androidx.compose.material.icons.Icons.Filled.Add,
            contentDescription = stringResource(R.string.skin_add_title),
            tint = colors.onPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AddChoice(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(13.dp))
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface
        )
    }
}
