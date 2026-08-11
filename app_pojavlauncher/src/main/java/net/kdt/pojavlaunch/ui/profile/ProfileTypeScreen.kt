package net.kdt.pojavlaunch.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppScaffold
import net.kdt.pojavlaunch.ui.settings.SectionLabel

/** Which kind of profile is being created. The host maps this back to the install fragments. */
enum class ProfileType { VANILLA, LOADERS, FABRIC, QUILT, FORGE, NEOFORGE, OPTIFINE, LWJGL3IFY, BTA, MODPACK }

@Immutable
class ProfileTypeEntry(
    val type: ProfileType,
    val nameRes: Int,
    val blurbRes: Int
)

private val VANILLA_LIKE = listOf(
    ProfileTypeEntry(ProfileType.VANILLA, R.string.profile_type_vanilla, R.string.profile_type_vanilla_blurb),
    ProfileTypeEntry(ProfileType.OPTIFINE, R.string.profile_type_optifine, R.string.profile_type_optifine_blurb),
    ProfileTypeEntry(ProfileType.BTA, R.string.profile_type_bta, R.string.profile_type_bta_blurb)
)

private val MODDED = listOf(
    // First, because it answers the question the four below it make you answer backwards: they
    // ask which loader before anything can tell you what that loader supports. They stay, because
    // somebody who already knows exactly which build they want should not be made to search for
    // a Minecraft version to reach it.
    ProfileTypeEntry(ProfileType.LOADERS, R.string.profile_type_loaders,
        R.string.profile_type_loaders_blurb),
    ProfileTypeEntry(ProfileType.FABRIC, R.string.profile_type_fabric, R.string.profile_type_fabric_blurb),
    ProfileTypeEntry(ProfileType.QUILT, R.string.profile_type_quilt, R.string.profile_type_quilt_blurb),
    ProfileTypeEntry(ProfileType.FORGE, R.string.profile_type_forge, R.string.profile_type_forge_blurb),
    ProfileTypeEntry(ProfileType.NEOFORGE, R.string.profile_type_neoforge, R.string.profile_type_neoforge_blurb),
    ProfileTypeEntry(ProfileType.LWJGL3IFY, R.string.profile_type_lwjgl3ify, R.string.profile_type_lwjgl3ify_blurb),
    ProfileTypeEntry(ProfileType.MODPACK, R.string.profile_type_modpack, R.string.profile_type_modpack_blurb)
)

/**
 * What kind of profile to create.
 *
 * It was nine buttons of identical weight under two headings, each labelled with a full sentence
 * ("Create Neoforge profile") and none of them saying what the thing actually *is* — so the choice
 * between Fabric and Quilt came down to already knowing. Here each option is a tile carrying its
 * name and one line of what it is for, and the two headings finally mean something: the first
 * group changes how the game looks, the second changes what you can add to it.
 */
@Composable
fun ProfileTypeScreen(onPick: (ProfileType) -> Unit, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            title = stringResource(R.string.create_profile),
            subtitle = stringResource(R.string.profile_type_subtitle),
            onBack = onBack
        ) {
            SectionLabel(stringResource(R.string.create_profile_vanilla_like_versions))
            TypeGrid(VANILLA_LIKE, onPick)

            SectionLabel(stringResource(R.string.create_profile_modded_versions))
            TypeGrid(MODDED, onPick)
        }
    }
}

/** Two to a row, so a tile is wide enough to carry a line of explanation under its name. */
@Composable
private fun TypeGrid(entries: List<ProfileTypeEntry>, onPick: (ProfileType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { entry -> TypeTile(entry, onPick) }
                // Keeps a lone tile on a half-width column rather than letting it stretch across.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.TypeTile(entry: ProfileTypeEntry, onPick: (ProfileType) -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onPick(entry.type) }
            .padding(start = 14.dp, top = 13.dp, end = 14.dp, bottom = 14.dp)
    ) {
        Text(
            stringResource(entry.nameRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            stringResource(entry.blurbRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
