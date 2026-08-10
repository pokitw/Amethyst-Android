package net.kdt.pojavlaunch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.optimiser.PerformancePlan
import net.kdt.pojavlaunch.ui.common.AppSheetHeading
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.Amethyst50
import net.kdt.pojavlaunch.ui.theme.Amethyst70

/**
 * What performance mode is about to do, before it does any of it.
 *
 * <b>The preview is the feature as much as the plan is.</b> A switch that changes a dozen settings
 * across three different stores, some of them in a file the launcher does not own, is a switch
 * nobody sensible would touch without being told what it means. So every change is named, with
 * the value it will be given, grouped by where it lands, and the hint at the top says the one
 * thing that makes it safe to try: all of it goes back.
 *
 * The sheet carries the screen's only gradient, on its one button, which is the handbook's rule
 * about spending boldness in a single place. Everything else here is quiet on purpose: this is a
 * list of facts to read, and a list of facts with a gradient on it is an advert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSheet(host: PerformanceHost) {
    if (host.stage == PerformanceHost.Stage.CLOSED) return
    ModalBottomSheet(
        onDismissRequest = {
            // Not while it is working: the download is not cancellable and a sheet that closed
            // on it would leave somebody with no way of knowing when it had finished.
            if (host.stage != PerformanceHost.Stage.WORKING) host.close()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        AppSheetHeading(
            stringResource(R.string.performance_title),
            stringResource(
                when (host.stage) {
                    PerformanceHost.Stage.PREVIEW -> R.string.performance_sheet_hint
                    else -> R.string.performance_note_next_launch
                }
            )
        )
        Column(
            Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            when (host.stage) {
                PerformanceHost.Stage.PREVIEW -> Preview(host)
                PerformanceHost.Stage.ACTIVE -> Lines(host.result)
                PerformanceHost.Stage.WORKING -> Working(host.progress)
                PerformanceHost.Stage.RESULT -> Lines(host.result)
                PerformanceHost.Stage.CLOSED -> Unit
            }
        }
        Buttons(host)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Preview(host: PerformanceHost) {
    val plan = host.plan ?: return
    DeviceCard(host)

    // Held in a local rather than read twice off the Java object: a nullable field reached from
    // Kotlin is a platform type, and relying on a smart cast through one is the kind of thing
    // that only fails in CI.
    val renderer = plan.rendererId
    if (renderer != null || plan.preferences.isNotEmpty()) {
        SectionLabel(stringResource(R.string.performance_section_launcher))
        SettingsCard {
            Column(Modifier.padding(vertical = 4.dp)) {
                if (renderer != null) {
                    PlanRow(stringResource(R.string.performance_renderer), rendererName(renderer))
                }
                plan.preferences.forEach { PlanRow(it) }
            }
        }
    }

    if (plan.options.isNotEmpty()) {
        SectionLabel(stringResource(R.string.performance_section_game))
        SettingsCard {
            Column(Modifier.padding(vertical = 4.dp)) { plan.options.forEach { PlanRow(it) } }
        }
    }

    if (plan.mods.isNotEmpty()) {
        SectionLabel(stringResource(R.string.performance_section_mods))
        SettingsCard {
            Column(Modifier.padding(vertical = 4.dp)) {
                plan.mods.forEach { mod ->
                    Column(Modifier.padding(horizontal = 15.dp, vertical = 9.dp)) {
                        Text(
                            mod.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(mod.summaryRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Note(stringResource(R.string.performance_note_mods_stay))
    } else {
        Note(
            stringResource(
                when (plan.modsSkipped) {
                    PerformancePlan.ModsSkipped.NO_LOADER -> R.string.performance_note_no_loader
                    PerformancePlan.ModsSkipped.NO_VERSION -> R.string.performance_note_no_version
                    PerformancePlan.ModsSkipped.NO_FOLDER -> R.string.performance_note_no_folder
                    else -> R.string.performance_note_next_launch
                }
            )
        )
    }
}

/**
 * What the plan was worked out from.
 *
 * First, and stated in the phone's own terms, because every number below it is a consequence of
 * these three facts and "why is my resolution 70%" has exactly one honest answer: because the
 * panel is 1440 by 3168 and the game does not need all of it.
 */
@Composable
private fun DeviceCard(host: PerformanceHost) {
    val plan = host.plan
    Spacer(Modifier.height(4.dp))
    SettingsCard {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            if (plan != null) {
                Text(
                    stringResource(R.string.performance_tier_line, stringResource(tierLabel(plan))),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                host.deviceLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (host.profileLine.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    host.profileLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun tierLabel(plan: PerformancePlan.Plan): Int = when (plan.tier) {
    net.kdt.pojavlaunch.optimiser.DeviceProfile.Tier.LOW -> R.string.performance_tier_low
    net.kdt.pojavlaunch.optimiser.DeviceProfile.Tier.HIGH -> R.string.performance_tier_high
    net.kdt.pojavlaunch.optimiser.DeviceProfile.Tier.FLAGSHIP -> R.string.performance_tier_flagship
    else -> R.string.performance_tier_mid
}

/** The renderer id read as a name, without asking the whole compatible list for one line. */
@Composable
private fun rendererName(id: String): String = when (id) {
    PerformancePlan.RENDERER_GL4ES -> "GL4ES"
    PerformancePlan.RENDERER_MOBILEGLUES -> "MobileGlues"
    PerformancePlan.RENDERER_ZINK_KOPPER -> "Zink"
    else -> id
}

@Composable
private fun PlanRow(item: PerformancePlan.Item) {
    PlanRow(stringResource(item.labelRes), stringResource(item.valueRes, item.value))
}

/** Label on the left, the value it will be given on the right, in the accent. */
@Composable
private fun PlanRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp)
    )
}

@Composable
private fun Working(progress: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.height(16.dp))
        // Written out rather than through ifEmpty, which would put a composable call inside an
        // inline lambda for no reason at all.
        val text = if (progress.isEmpty()) stringResource(R.string.performance_working) else progress
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Lines(lines: List<String>) {
    Spacer(Modifier.height(4.dp))
    SettingsCard {
        Column(
            Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            lines.forEachIndexed { index, line ->
                Text(
                    line,
                    // The first line is the outcome; the rest qualify it.
                    style = if (index == 0) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodySmall,
                    color = if (index == 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Buttons(host: PerformanceHost) {
    if (host.stage == PerformanceHost.Stage.WORKING) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (host.stage != PerformanceHost.Stage.RESULT) {
            TextButton(onClick = { host.close() }) {
                Text(
                    stringResource(R.string.performance_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                stringResource(
                    if (host.stage == PerformanceHost.Stage.ACTIVE) R.string.performance_turn_off
                    else R.string.performance_apply
                )
            ) { host.onApply() }
        } else {
            Spacer(Modifier.weight(1f))
            PrimaryButton(stringResource(android.R.string.ok)) { host.close() }
        }
    }
}

/** The sheet's one bold element. Everything around it stays quiet, which is what makes it read. */
@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Amethyst70, Amethyst50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Amethyst20)
    }
}
