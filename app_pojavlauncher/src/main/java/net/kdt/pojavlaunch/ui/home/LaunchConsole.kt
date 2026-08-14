package net.kdt.pojavlaunch.ui.home

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.Success70

/**
 * The hero card while a launch is running.
 *
 * The old presentation was one line of text inside the Play button. What a launch deserves is the
 * treatment a console gives it: the identity of the thing being started, a reading of how far
 * along it is, and a timeline of what has actually been done. Everything shown here is the
 * launcher's own state, none of it invented: the stages are the downloader's reports promoted
 * from a single overwritten line to a running record, and the facts line is the same renderer,
 * memory and version summary the idle card shows, because they are what this launch is using.
 *
 * There is deliberately no completion state. When the download finishes the game process starts
 * and the launcher's own process is killed mid-frame (`ContextAwareDoneListener`), so the honest
 * end of this screen is the game appearing over it; anything designed for that moment would only
 * ever be seen when the launch had failed.
 */
@Composable
fun LaunchConsole(profile: GameProfile?, progress: LaunchProgress, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val determinate = progress.percent >= 0
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    // One animated value drives the ring, the bar and the number, so the three can never say
    // three different things. An Animatable rather than animateFloatAsState so the first target
    // is swept to from zero: the ring drawing itself in is the entrance.
    val fill = remember { Animatable(0f) }
    LaunchedEffect(progress.percent, reduced) {
        if (progress.percent >= 0) {
            val target = (progress.percent / 100f).coerceIn(0f, 1f)
            if (reduced) fill.snapTo(target)
            else fill.animateTo(target, tween(500, easing = FastOutSlowInEasing))
        }
    }

    // Kept as State and only read inside draw lambdas: an infinite value read during composition
    // would recompose the whole console every frame for as long as it is on screen, when all it
    // ever changes is two strokes of paint.
    val spin: State<Float>
    val sweep: State<Float>
    if (!reduced) {
        val infinite = rememberInfiniteTransition(label = "launchConsole")
        spin = infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
            label = "launchSpin"
        )
        sweep = infinite.animateFloat(
            initialValue = -0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
            label = "launchSweep"
        )
    } else {
        spin = remember { mutableStateOf(0f) }
        sweep = remember { mutableStateOf(-0.45f) }
    }

    Column(modifier.fillMaxWidth().padding(start = 12.dp, top = 14.dp, end = 14.dp, bottom = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(64.dp)) {
                    drawLaunchRing(
                        trackColor = track,
                        accent = accent,
                        determinate = determinate,
                        fill = fill.value,
                        spinAngle = spin.value
                    )
                }
                ProfileIcon(profile?.icon, size = 42.dp, corner = 12.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (progress.requested) R.string.home_launching
                        else R.string.home_getting_ready
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    profile?.title ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        profile.details(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AnimatedVisibility(
                visible = determinate,
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(140))
            ) {
                Text(
                    stringResource(
                        R.string.home_percent, (fill.value * 100).roundToInt().coerceIn(0, 100)
                    ),
                    style = MaterialTheme.typography.displaySmall
                        .copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(track)
        ) {
            when {
                determinate -> Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill.value.coerceIn(0f, 1f))
                        .background(accent)
                )
                // With animations off a travelling band cannot travel, so the whole track wears a
                // quiet wash instead: still visibly busy, nothing pretending to be a percentage.
                reduced -> Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.30f)))
                else -> Box(
                    Modifier.fillMaxSize().drawBehind {
                        drawSweep(sweep.value, accent.copy(alpha = 0.85f))
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // The synthetic first line covers the moment between the press and the first report, so
        // the timeline is never empty while the card is claiming to be busy.
        val lines = progress.stages.ifEmpty {
            listOf(LaunchStage("requested", stringResource(R.string.home_launch_starting), -1))
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            lines.forEachIndexed { index, stage ->
                key(stage.id) {
                    StageLine(
                        text = stage.text ?: stringResource(R.string.home_working),
                        live = index == lines.lastIndex,
                        reduced = reduced
                    )
                }
            }
        }
    }
}

/**
 * A track circle and the progress arc over it, from twelve o'clock. While no percentage exists
 * yet the arc becomes a short segment going round on the continuous-motion clock, and with
 * animations off it simply rests at the top, which reads as "busy" without moving.
 */
private fun DrawScope.drawLaunchRing(
    trackColor: Color,
    accent: Color,
    determinate: Boolean,
    fill: Float,
    spinAngle: Float
) {
    val strokeWidth = 4.dp.toPx()
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val inset = strokeWidth / 2f
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    drawArc(
        color = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = stroke
    )
    if (determinate && fill <= 0f) return
    drawArc(
        color = accent,
        startAngle = if (determinate) -90f else spinAngle - 90f,
        sweepAngle = if (determinate) 360f * fill.coerceIn(0f, 1f) else 100f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = stroke
    )
}

/**
 * One stage of the timeline: a pulsing dot while it is the one running, a check once something
 * newer has taken over. Tabular figures because the live line carries counts and speeds that
 * change several times a second, and digits that keep their width stop it shimmering.
 */
@Composable
private fun StageLine(text: String, live: Boolean, reduced: Boolean) {
    var shown by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "stageEnter"
    )
    val risePx = with(LocalDensity.current) { 6.dp.toPx() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer {
            alpha = enter
            translationY = (1f - enter) * risePx
        }
    ) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            Crossfade(targetState = live, animationSpec = tween(300), label = "stageMark") { now ->
                if (now) {
                    // Read in the draw lambda, not here, so the breathing costs redraws of a
                    // seven dp circle rather than recompositions of the line it sits on.
                    val pulse: State<Float> = if (reduced) {
                        remember { mutableStateOf(1f) }
                    } else {
                        rememberInfiniteTransition(label = "stagePulse").animateFloat(
                            initialValue = 0.35f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                            label = "stagePulseAlpha"
                        )
                    }
                    val dotColor = MaterialTheme.colorScheme.primary
                    Box(
                        Modifier.size(7.dp).drawBehind {
                            drawCircle(dotColor.copy(alpha = pulse.value))
                        }
                    )
                } else {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Success70,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = if (live) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A soft band travelling across the bar; the gradient clamps, so nothing spills either side. */
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

/**
 * The platform's reduced-motion signal, read once when the composition owner appears rather than
 * per frame (handbook 16.11). A settings provider that throws means "animate", never a launcher
 * that cannot start.
 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        }.getOrDefault(false)
    }
}
