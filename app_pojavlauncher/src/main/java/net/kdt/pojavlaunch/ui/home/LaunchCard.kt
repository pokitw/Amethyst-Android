package net.kdt.pojavlaunch.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.Amethyst50
import net.kdt.pojavlaunch.ui.theme.Amethyst70

/**
 * The launch card.
 *
 * Choosing a version and starting the game were two controls of near-equal weight before, stacked
 * at the bottom of the screen. They are not two decisions though, they are one — play *this* — so
 * they are one object here, and it is the only thing on the screen carrying a gradient. Spending
 * all of the boldness in a single place is what lets everything around it stay quiet.
 */
@Composable
fun LaunchCard(
    profile: GameProfile?,
    progress: LaunchProgress,
    onPickVersion: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val innerShape = RoundedCornerShape(16.dp)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                // A wash rather than a fill: the accent is strongest where the eye enters the
                // card and gone well before it reaches the button.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Amethyst50.copy(alpha = 0.30f), Color.Transparent),
                            center = Offset.Zero,
                            radius = size.maxDimension * 1.05f
                        )
                    )
                }
                .padding(6.dp)
        ) {
            VersionRow(profile, innerShape, onPickVersion)
            PlayButton(profile != null, progress, innerShape, onPlay)
        }
    }
}

@Composable
private fun VersionRow(
    profile: GameProfile?,
    shape: RoundedCornerShape,
    onPickVersion: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onPickVersion)
            .padding(start = 12.dp, top = 16.dp, end = 14.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileIcon(profile?.icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile?.title ?: stringResource(R.string.home_no_profile),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            if (profile == null) {
                Text(
                    stringResource(R.string.home_no_profile_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LaunchState(profile)
            }
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.home_choose_version_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * What pressing Play is about to do.
 *
 * None of this was visible before, so the only way to find out that a profile was on the wrong
 * renderer, or had never been downloaded, was to launch it and watch.
 */
@Composable
private fun LaunchState(profile: GameProfile) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        profile.loader?.let { Badge(it) }
        if (!profile.installed) Badge(stringResource(R.string.home_not_installed), muted = true)
        Text(
            profile.details(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Badge(text: String, muted: Boolean = false) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (muted) MaterialTheme.colorScheme.surfaceContainerHighest
                else Amethyst70.copy(alpha = 0.13f)
            )
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun ProfileIcon(icon: ImageBitmap?) {
    Box(
        Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp)
            )
        } else {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Play, and the progress indicator.
 *
 * Downloads used to appear in a bar pinned to the bottom of the window, disconnected from the
 * action that started them, leaving the button itself looking inert. Here the button you pressed
 * is the thing that reports back: it fills, and its label carries the stage.
 */
@Composable
private fun PlayButton(
    enabled: Boolean,
    progress: LaunchProgress,
    shape: RoundedCornerShape,
    onPlay: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squish by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "playPress"
    )
    val fill by animateFloatAsState(
        targetValue = if (progress.busy && progress.percent >= 0) progress.percent / 100f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "playFill"
    )
    // A slow sweep for the stages that cannot report a percentage, so the button still reads as
    // working rather than stuck.
    val sweep by rememberInfiniteTransition(label = "playSweep").animateFloat(
        initialValue = -0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "playSweepOffset"
    )

    val busy = progress.busy
    val gradient = Brush.linearGradient(listOf(Amethyst70, Amethyst50))
    val idleLabel = stringResource(R.string.main_play)
    val busyLabel = progress.label ?: stringResource(R.string.home_working)

    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .scale(squish)
            .clip(shape)
            .background(
                if (busy) MaterialTheme.colorScheme.surfaceContainerHighest else gradient
            )
            .clickable(
                enabled = enabled && !busy,
                interactionSource = interaction,
                indication = null,
                onClick = onPlay
            ),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            val wash = Amethyst70.copy(alpha = 0.30f)
            if (progress.percent >= 0) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .background(wash)
                )
            } else {
                Box(Modifier.fillMaxSize().drawBehind { drawSweep(sweep, wash) })
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            if (!busy) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Amethyst20,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = if (busy) busyLabel else idleLabel,
                style = MaterialTheme.typography.titleMedium,
                color = if (busy) MaterialTheme.colorScheme.onSurface else Amethyst20,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Gives way rather than pushing the percentage off the end of a long stage name.
                modifier = Modifier.weight(1f, fill = false)
            )
            if (busy && progress.percent >= 0) {
                Text(
                    stringResource(R.string.home_percent, progress.percent),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A soft band travelling across the button; the gradient clamps, so nothing spills either side. */
private fun DrawScope.drawSweep(offset: Float, color: Color) {
    val start = offset * size.width
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, color, Color.Transparent),
            startX = start,
            endX = start + size.width * 0.45f
        )
    )
}
