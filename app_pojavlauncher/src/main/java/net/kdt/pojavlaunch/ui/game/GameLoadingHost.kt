package net.kdt.pojavlaunch.ui.game

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.GameFrames
import net.kdt.pojavlaunch.Logger
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.rememberRotatingLine
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Something to look at while the screen is black.
 *
 * Between the surface being ready and the game's first frame there is a JVM starting, a few
 * hundred classes loading and, with a modpack, a great deal more. For that whole time the screen
 * is black and nothing anywhere says whether the launcher is working or has died, which is the
 * longest unexplained wait in the app and the one place it had nothing at all.
 *
 * **The log is the honest half and the splash is the fun half.** `Logger` is already streaming
 * the game's own output, so the last line it printed is a real answer to "is this stuck", costs
 * nothing to show, and is more interesting than any invented stage would have been. The splash
 * line and the tip sit above it because a true line about class loading is not, on its own,
 * something anybody enjoys reading for ninety seconds.
 *
 * **Three ways out, because being wrong here locks somebody out of their game.** A full screen
 * view over the surface swallows every touch the game is waiting for (§12.9), so this cannot be
 * allowed to outlive the wait: it goes when the first frame is presented, when the player taps
 * it, and when a deadline passes, and the deadline is short rather than generous whenever
 * [GameFrames] cannot answer, because no signal means no business covering anything.
 */
class GameLoadingHost(private val view: ComposeView, versionName: String?) {

    private var showing by mutableStateOf(false)
    private var logLine by mutableStateOf("")
    private val version = versionName.orEmpty()

    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var finished = false
    private var startedAt = 0L

    /**
     * When to give up waiting for a frame that may never be reported.
     *
     * Two values rather than one: with a frame counter answering, the wait can legitimately run
     * into minutes on a cold modpack, and the counter will end it long before that. With no
     * counter there is nothing to end it, so the overlay must take itself off the screen quickly
     * and let the player see whatever is underneath, even if that is still black.
     */
    private var deadlineMs = BLIND_DEADLINE_MS

    private val logListener = Logger.eventLogListener { text ->
        // Called from whichever thread wrote the line. Nothing here touches state directly.
        val line = lastMeaningfulLine(text)
        if (line.isNotEmpty()) handler.post { if (!finished) logLine = line }
    }

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            AmethystXTheme {
                LoadingScreen(
                    showing = showing,
                    version = version,
                    logLine = logLine,
                    onTap = ::finish
                )
            }
        }
    }

    /** Put the screen up and start watching for the game's first frame. */
    fun start() {
        if (started || finished) return
        started = true
        startedAt = android.os.SystemClock.uptimeMillis()
        showing = true
        view.visibility = View.VISIBLE
        Logger.addLogListener(logListener)
        handler.post(poll)
    }

    /**
     * Take it down, from anywhere, more than once if need be.
     *
     * The listener comes off first: `Logger` turns the native callback off again once nothing is
     * listening, and leaving it on would cost every line the game prints for the rest of the
     * session.
     */
    fun finish() {
        if (finished) return
        finished = true
        handler.removeCallbacks(poll)
        if (started) Logger.removeLogListener(logListener)
        showing = false
        // Delayed past the fade, the same bargain the control center makes: the view has to stay
        // visible for the animation and must not be visible for a frame longer (§12.9).
        handler.postDelayed({ view.visibility = View.GONE }, EXIT_MS)
    }

    private val poll = object : Runnable {
        override fun run() {
            if (finished) return
            val presented = GameFrames.presented()
            if (presented > 0) {
                finish()
                return
            }
            deadlineMs = if (presented < 0) BLIND_DEADLINE_MS else WATCHED_DEADLINE_MS
            if (android.os.SystemClock.uptimeMillis() - startedAt >= deadlineMs) {
                finish()
                return
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    companion object {
        private const val POLL_MS = 250L
        private const val EXIT_MS = 260L
        private const val BLIND_DEADLINE_MS = 8_000L
        private const val WATCHED_DEADLINE_MS = 180_000L

        /**
         * The last line worth showing out of whatever the logger just handed over.
         *
         * A write is not a line: the game prints a stack trace in one go and a progress bar a
         * character at a time, so this takes the last non-blank line of the chunk and caps it,
         * because the strip is one line and a 400 character Mixin message would push everything
         * else off the screen.
         */
        @JvmStatic
        fun lastMeaningfulLine(text: String?): String {
            if (text.isNullOrEmpty()) return ""
            val line = text.trimEnd().substringAfterLast('\n').trim()
            return if (line.length <= 140) line else line.substring(0, 140)
        }
    }
}

@Composable
private fun LoadingScreen(
    showing: Boolean,
    version: String,
    logLine: String,
    onTap: () -> Unit
) {
    val fade by animateFloatAsState(
        targetValue = if (showing) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "loadingFade"
    )
    if (fade <= 0.01f) return

    val colors = MaterialTheme.colorScheme
    val splash = rememberRotatingLine(R.array.splash_lines, periodMs = 6_000L)
    val tip = rememberRotatingLine(R.array.loading_tips, periodMs = 9_000L)

    Box(
        Modifier
            .fillMaxSize()
            .alpha(fade)
            .background(colors.background)
            .clickableNoRipple(onTap),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BreathingGem()
            Spacer(Modifier.height(18.dp))
            Text(
                if (version.isEmpty()) stringResource(R.string.loading_waiting)
                else stringResource(R.string.loading_starting, version),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                splash,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))
            TravellingBar()
            Spacer(Modifier.height(14.dp))
            // The one true thing on the screen. Dim and monospace on purpose: it is evidence
            // that work is happening rather than something to read, and a reader who does want
            // to read it is looking at a log and should see it shaped like one.
            Text(
                logLine,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 20.dp)
                .widthIn(max = 560.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.loading_tip_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(colors.primary.copy(alpha = 0.13f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                tip,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The gem, breathing.
 *
 * The one piece of motion here, and it is the brand mark rather than a spinner because the mark
 * is already the thing this screen is about. It does not take a tint (§5).
 */
@Composable
private fun BreathingGem() {
    val transition = rememberInfiniteTransition(label = "gem")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gemPulse"
    )
    Image(
        painter = painterResource(R.drawable.ic_x_gem),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(52.dp)
            .scale(pulse)
    )
}

/** An indeterminate band, on the handbook's continuous-motion clock. */
@Composable
private fun TravellingBar() {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "bar")
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "barSweep"
    )
    Box(
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .height(3.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to colors.primary.copy(alpha = 0f),
                        (position * 0.8f + 0.1f).coerceIn(0.02f, 0.98f) to colors.primary,
                        1f to colors.primary.copy(alpha = 0f)
                    )
                )
        )
    }
}

/**
 * Tappable with no ripple and no indication.
 *
 * It is a way out rather than a control, and a full screen ripple would announce itself as a
 * button on a screen that has nothing to press.
 */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
    )
}
