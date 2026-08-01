package net.kdt.pojavlaunch.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppScaffold
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.Amethyst50
import net.kdt.pojavlaunch.ui.theme.Amethyst70
import net.kdt.pojavlaunch.ui.theme.SlotWell

/** Why an offline name was refused, so the field can say it in place rather than in a dialog. */
enum class NameError { TOO_SHORT, TOO_LONG, CHARACTERS, TAKEN }

/**
 * Signing in.
 *
 * Two screens became one. It was a pair of equal-weight buttons — "Microsoft account" and "Local
 * account" — that said nothing about the difference, followed, if you chose the second, by a
 * separate screen holding one text field. But these are not equal choices: a Microsoft account is
 * the one that plays online and owns the skin, and an offline account cannot join a server at all.
 * So Microsoft is the card that carries the gradient, offline is the quieter option beneath it,
 * both say what they actually get you, and the username is asked for in place rather than on a
 * screen of its own.
 */
@Composable
fun AuthChooserScreen(
    onMicrosoft: () -> Unit,
    onOffline: (String) -> Unit,
    validate: (String) -> NameError?,
    onBack: () -> Unit
) {
    var offlineOpen by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }

    val error = if (touched) validate(username) else null
    val canSubmit = username.isNotEmpty() && validate(username) == null

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            title = stringResource(R.string.auth_title),
            subtitle = stringResource(R.string.auth_subtitle),
            onBack = onBack
        ) {
            Spacer(Modifier.height(14.dp))

            MicrosoftCard(onMicrosoft)

            Spacer(Modifier.height(12.dp))

            OfflineCard(
                expanded = offlineOpen,
                username = username,
                error = error,
                canSubmit = canSubmit,
                onExpand = { offlineOpen = true },
                onUsername = {
                    // Trimmed as it is typed: a trailing space is the commonest way to land on
                    // "unsuitable username" without being able to see why.
                    username = it.trim()
                    touched = true
                },
                onSubmit = { if (canSubmit) onOffline(username) }
            )

            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.auth_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MicrosoftCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Amethyst50.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset.Zero,
                        radius = size.maxDimension * 1.05f
                    )
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlotWell(size = 48.dp, color = Amethyst70.copy(alpha = 0.16f)) {
            Icon(
                painterResource(R.drawable.ic_x_gem),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.auth_microsoft_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.auth_microsoft_blurb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The offline option, which opens into its own username field rather than another screen.
 *
 * Collapsed it is a row; expanded it is the same row with the field under it, so the decision and
 * the one thing the decision needs stay in the same place.
 */
@Composable
private fun OfflineCard(
    expanded: Boolean,
    username: String,
    error: NameError?,
    canSubmit: Boolean,
    onExpand: () -> Unit,
    onUsername: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !expanded, onClick = onExpand)
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlotWell(size = 40.dp) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.auth_offline_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.auth_offline_blurb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded) {
            Column(Modifier.padding(start = 15.dp, end = 15.dp, bottom = 15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (username.isEmpty()) {
                            Text(
                                stringResource(R.string.login_online_username_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = username,
                            onValueChange = onUsername,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSubmit) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .clickable(enabled = canSubmit, onClick = onSubmit),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.login_online_login_label),
                            tint = if (canSubmit) Amethyst20
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        when (error) {
                            NameError.TOO_SHORT -> R.string.auth_name_short
                            NameError.TOO_LONG -> R.string.auth_name_long
                            NameError.CHARACTERS -> R.string.auth_name_characters
                            NameError.TAKEN -> R.string.auth_name_taken
                            null -> R.string.auth_name_rule
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
