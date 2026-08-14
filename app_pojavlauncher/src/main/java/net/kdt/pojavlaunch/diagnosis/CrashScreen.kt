package net.kdt.pojavlaunch.diagnosis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.Amethyst20

/**
 * Everything the crash screen shows, resolved to plain strings before composition starts.
 *
 * Deliberately dumb: the model is built — log read, patterns matched, resources resolved — in the
 * activity behind a catch, so the composition itself touches nothing that can fail. A crash
 * screen that can crash would be worse than no crash screen.
 */
@Immutable
class CrashScreenModel(
    val codeLine: String,
    val diagnosis: Diagnosis
)

/**
 * What to say when the game dies.
 *
 * This replaces a stock light-themed dialog reading "Application/Game exited with code 1, check
 * latestlog.txt for more details" — an answer that told the person who most needs help to go read
 * a stack trace. The card leads with what happened in words, then what to try, and quotes the log
 * line it concluded from, so the advice is checkable rather than oracular.
 */
@Composable
fun CrashScreen(
    model: CrashScreenModel,
    onShareLog: () -> Unit,
    onViewLog: () -> Unit,
    onBackToLauncher: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier
                    // The game runs landscape, so this screen usually opens landscape too;
                    // capping the column keeps the card readable instead of letting one
                    // sentence stretch across the whole display.
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.crash_screen_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    model.codeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))

                DiagnosisCard(model.diagnosis)

                Spacer(Modifier.height(18.dp))
                // Ranked by what somebody does after a crash: go back and try again, then read
                // the rest of what the card is quoting from, and only then send it to anyone.
                // Reading comes before sharing now that reading is possible at all.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionPill(
                        label = stringResource(R.string.crash_back_to_launcher),
                        accent = true,
                        modifier = Modifier.weight(1f),
                        onClick = onBackToLauncher
                    )
                    ActionPill(
                        label = stringResource(R.string.crash_view_log),
                        accent = false,
                        modifier = Modifier.weight(1f),
                        onClick = onViewLog
                    )
                }
                Spacer(Modifier.height(10.dp))
                ActionPill(
                    label = stringResource(R.string.main_share_logs),
                    accent = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onShareLog
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DiagnosisCard(diagnosis: Diagnosis) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            diagnosis.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            diagnosis.explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.crash_what_to_try),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(5.dp))
        Text(
            diagnosis.advice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        val evidence = diagnosis.evidence
        if (!evidence.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    stringResource(R.string.crash_from_the_log),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    evidence,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(
                if (accent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (accent) Amethyst20 else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
