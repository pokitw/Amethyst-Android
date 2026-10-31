package net.kdt.pojavlaunch.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.modmigrate.ModMigration
import net.kdt.pojavlaunch.ui.common.AppSheetHeading
import net.kdt.pojavlaunch.ui.settings.InfoRow
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.theme.Warning70

/**
 * What moving a mod set to another Minecraft version is about to do.
 *
 * <b>The plan is shown before anything is written</b>, which is the whole reason this is a sheet
 * and not a button. The obvious implementation of this feature is one tap that downloads
 * everything, and it would be wrong for the reason the import dialog exists (14): a guess that is
 * right nearly always is still a guess, and the two questions worth asking of it both land in the
 * gap between deciding and writing. Here they are "is this really the mod I think it is" and "what
 * happens to the jars I already have".
 *
 * Drawn with the settings components like the rest of Game files, and grouped by what will happen
 * rather than coloured by it: four sections carry the meaning that four colours would have had to,
 * and a list of forty mods does not need forty coloured lines to be read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModMigrateSheet(
    targetVersion: String,
    plan: ModMigration.Plan?,
    sameFolder: Boolean,
    status: String?,
    outcome: MigrateOutcome?,
    busy: Boolean,
    onMove: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
        contentColor = colors.onSurface
    ) {
        AppSheetHeading(
            stringResource(R.string.content_migrate_title, targetVersion),
            stringResource(R.string.content_migrate_hint)
        )
        Column(
            Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The one line that is always worth saying in a shared folder, because it is what
            // makes the result loadable and it is not something anybody would guess.
            if (sameFolder) {
                Text(
                    stringResource(R.string.content_migrate_shared),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            // The count is the progress, in words, which is what the install band already does
            // ("which file, how far through, how many have finished"). No Material progress
            // component, for the reason ClipEditScreen draws its own: this BOM is pinned and
            // which overload it carries is not a thing to find out in CI.
            if (status != null) SettingsCard { InfoRow(title = status) }

            outcome?.let { Outcome(it) }

            if (plan != null && outcome == null) {
                Section(R.string.content_migrate_section_move, plan, ModMigration.Verdict.MOVE) {
                    stringResource(
                        R.string.content_migrate_change,
                        it.currentVersion.orEmpty(),
                        it.install?.versionNumber.orEmpty()
                    )
                }
                Section(
                    R.string.content_migrate_section_none, plan, ModMigration.Verdict.NO_BUILD
                ) {
                    it.newestElsewhere?.let { newest ->
                        stringResource(R.string.content_migrate_none_value, newest)
                    } ?: stringResource(R.string.content_migrate_none_unknown, targetVersion)
                }
                Section(
                    R.string.content_migrate_section_unknown, plan,
                    ModMigration.Verdict.UNIDENTIFIED,
                    hint = stringResource(R.string.content_migrate_unknown_hint)
                ) { stringResource(R.string.content_migrate_unknown_value) }
                Section(
                    R.string.content_migrate_section_already, plan, ModMigration.Verdict.ALREADY
                ) { stringResource(R.string.content_migrate_already_value, targetVersion) }

                if (plan.steps.isEmpty()) {
                    SettingsCard {
                        InfoRow(title = stringResource(R.string.content_migrate_empty))
                    }
                }
            }

            if (plan != null && outcome == null && !busy) Action(plan, onMove)
            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * One group of the plan, or nothing at all when it is empty.
 *
 * An empty section is a heading with nothing under it, which reads as a thing that failed to load
 * rather than as a count of zero.
 */
@Composable
private fun Section(
    titleRes: Int,
    plan: ModMigration.Plan,
    verdict: ModMigration.Verdict,
    hint: String? = null,
    value: @Composable (ModMigration.Step) -> String
) {
    val steps = plan.steps.filter { it.verdict == verdict }
    if (steps.isEmpty()) return
    Column {
        SectionLabel(stringResource(titleRes) + "  " + steps.size)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        SettingsCard {
            steps.forEach { step ->
                InfoRow(
                    title = step.label(),
                    description = step.local.fileName,
                    value = value(step)
                )
            }
        }
    }
}

/**
 * The primary action, and the one case where it is deliberately not offered.
 *
 * A plan with nothing to move is shown as a dead label rather than a disabled button: there is
 * nothing to press, and a control that does nothing is the thing a settings surface must never
 * show (14).
 */
@Composable
private fun Action(plan: ModMigration.Plan, onMove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val count = plan.count(ModMigration.Verdict.MOVE)
    if (count == 0) {
        Text(
            stringResource(R.string.content_migrate_nothing),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        )
        return
    }
    Text(
        pluralStringResource(R.plurals.content_migrate_action, count, count),
        style = MaterialTheme.typography.titleSmall,
        color = colors.onPrimaryContainer,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(onClick = onMove)
            .padding(vertical = 14.dp)
    )
}

/** What happened, once it has. */
@Composable
private fun Outcome(outcome: MigrateOutcome) {
    val colors = MaterialTheme.colorScheme
    Column {
        SettingsCard {
            InfoRow(
                title = pluralStringResource(
                    R.plurals.content_migrate_done, outcome.moved.size, outcome.moved.size
                ),
                description = outcome.moved.joinToString(", ").ifEmpty { null },
                value = if (outcome.disabled > 0) {
                    pluralStringResource(
                        R.plurals.content_mod_turned_off, outcome.disabled, outcome.disabled
                    )
                } else {
                    null
                }
            )
            // A required dependency that did not arrive is a mod that crashes the game on the
            // next launch, so it is never folded into the success line: that is ModInstall's own
            // distinction between ok() and complete(), carried through to the screen.
            if (outcome.missingDependencies.isNotEmpty()) {
                InfoRow(
                    title = stringResource(R.string.content_mod_needed_title,
                        outcome.missingDependencies.first()),
                    value = outcome.missingDependencies.size.toString()
                )
            }
        }
        if (outcome.failed.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.content_migrate_some_failed, outcome.failed.size),
                style = MaterialTheme.typography.bodySmall,
                color = Warning70
            )
        }
    }
}
