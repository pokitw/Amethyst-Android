package net.kdt.pojavlaunch.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 *
 * While a launch is running the whole interior becomes the [LaunchConsole]: the thing that was
 * pressed is the thing that transforms, and swapping the version row out with it is what stops a
 * profile being switched under a download that has already decided what it is fetching.
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
    // The wash deepens while a launch runs: the ground under the console says something is
    // happening before any text does, without a second gradient appearing anywhere.
    val washAlpha by animateFloatAsState(
        targetValue = if (progress.active) 0.42f else 0.30f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "launchWash"
    )
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
                            colors = listOf(Amethyst50.copy(alpha = washAlpha), Color.Transparent),
                            center = Offset.Zero,
                            radius = size.maxDimension * 1.05f
                        )
                    )
                }
                .padding(6.dp)
        ) {
            AnimatedContent(
                targetState = progress.active,
                transitionSpec = {
                    (fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 8 })
                        .togetherWith(fadeOut(tween(140)))
                        .using(SizeTransform { _, _ -> tween(300, easing = FastOutSlowInEasing) })
                },
                label = "launchCardMode"
            ) { active ->
                if (active) {
                    LaunchConsole(profile, progress)
                } else {
                    Column {
                        VersionRow(profile, innerShape, onPickVersion)
                        PlayButton(profile != null, innerShape, onPlay)
                    }
                }
            }
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

/** The profile's picture in its well; the console borrows it at ring size. */
@Composable
internal fun ProfileIcon(icon: ImageBitmap?, size: Dp = 54.dp, corner: Dp = 14.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
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
 * Play.
 *
 * Purely the action now: the moment it is pressed the card hands over to the console, so the
 * button no longer needs a busy costume of its own. The tick on the press is the same physical
 * acknowledgement a key gives, for the one tap here that starts something big.
 */
@Composable
private fun PlayButton(
    enabled: Boolean,
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
    val view = LocalView.current

    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .scale(squish)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Amethyst70, Amethyst50)))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onPlay()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Amethyst20,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.main_play),
                style = MaterialTheme.typography.titleMedium,
                color = Amethyst20,
                maxLines = 1
            )
        }
    }
}
