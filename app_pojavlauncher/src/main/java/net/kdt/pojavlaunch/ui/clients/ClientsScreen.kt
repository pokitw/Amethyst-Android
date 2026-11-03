package net.kdt.pojavlaunch.ui.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppScaffold
import net.kdt.pojavlaunch.ui.settings.ChoiceRow
import net.kdt.pojavlaunch.ui.settings.InfoRow
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.theme.Danger70

/** One version as the screen offers it: what it is called, and the modules it can be run with. */
@Immutable
data class ClientVersion(
    val id: String,
    val modules: List<ClientModule>,
    val defaultModule: String
)

@Immutable
data class ClientModule(val id: String, val name: String, val description: String)

/** Where the install is, as one line and one fraction; null fraction means no measurable length. */
@Immutable
data class ClientProgress(val stage: String, val fraction: Float?)

@Immutable
data class ClientsState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val versions: List<ClientVersion> = emptyList(),
    /** Versions Lunar offers that this build cannot run yet, named so their absence is explained. */
    val unavailable: List<String> = emptyList(),
    val selectedVersion: String? = null,
    val selectedModule: String? = null,
    val installed: Boolean = false,
    val progress: ClientProgress? = null,
    val installError: String? = null
)

/**
 * Lunar Client, from inside the launcher.
 *
 * Drawn with the settings components (handbook 9) because it is a short form: pick a version,
 * pick how it runs, press one button. The status card at the top is not decoration. It says
 * what this does and does not do yet in the first thing anybody reads, because a client whose
 * own menus are blank is a surprise worth having before the download rather than after it.
 */
@Composable
fun ClientsScreen(
    state: ClientsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectVersion: (String) -> Unit,
    onSelectModule: (String) -> Unit,
    onPlay: () -> Unit
) {
    AppScaffold(
        title = stringResource(R.string.clients_title),
        subtitle = stringResource(R.string.clients_subtitle),
        onBack = onBack
    ) {
        SettingsCard {
            InfoRow(
                title = stringResource(R.string.clients_status_title),
                description = stringResource(R.string.clients_status_body)
            )
        }

        when {
            state.loading -> {
                SectionLabel(stringResource(R.string.clients_section_version))
                SettingsCard { InfoRow(title = stringResource(R.string.clients_loading)) }
            }

            state.loadError != null -> {
                SectionLabel(stringResource(R.string.clients_section_version))
                SettingsCard {
                    InfoRow(
                        title = stringResource(R.string.clients_load_failed),
                        description = state.loadError
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.clients_retry))
                }
            }

            else -> Picker(state, onSelectVersion, onSelectModule, onPlay)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Picker(
    state: ClientsState,
    onSelectVersion: (String) -> Unit,
    onSelectModule: (String) -> Unit,
    onPlay: () -> Unit
) {
    val version = state.versions.firstOrNull { it.id == state.selectedVersion }
    val busy = state.progress != null

    SectionLabel(stringResource(R.string.clients_section_version))
    SettingsCard {
        ChoiceRow(
            title = stringResource(R.string.clients_version),
            names = state.versions.map { it.id },
            values = state.versions.map { it.id },
            selected = state.selectedVersion ?: "",
            onSelect = { if (!busy) onSelectVersion(it) }
        )
        if (version != null && version.modules.size > 1) {
            val module = version.modules.firstOrNull { it.id == state.selectedModule }
            ChoiceRow(
                title = stringResource(R.string.clients_module),
                description = module?.description,
                names = version.modules.map { it.name },
                values = version.modules.map { it.id },
                selected = state.selectedModule ?: "",
                onSelect = { if (!busy) onSelectModule(it) }
            )
        }
        if (state.unavailable.isNotEmpty()) {
            InfoRow(
                title = stringResource(R.string.clients_unavailable_title),
                description = stringResource(
                    R.string.clients_unavailable_body, state.unavailable.joinToString(", ")
                )
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    if (state.progress != null) {
        ProgressBand(state.progress)
    } else {
        Button(
            onClick = onPlay,
            enabled = version != null,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                stringResource(
                    if (state.installed) R.string.clients_play else R.string.clients_install_play
                ),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
    if (state.installError != null) {
        Text(
            state.installError,
            style = MaterialTheme.typography.bodySmall,
            color = Danger70,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp)
        )
    }
}

/**
 * The install's own line and bar. No Material progress component, for the reason the clip editor
 * gives: nothing here should depend on which overload of one this BOM happens to carry.
 */
@Composable
private fun ProgressBand(progress: ClientProgress) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            progress.stage,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val f = progress.fraction
            if (f != null) {
                Box(
                    Modifier
                        .fillMaxWidth(f.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        if (progress.fraction != null) {
            Text(
                "${(progress.fraction.coerceIn(0f, 1f) * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
