package net.kdt.pojavlaunch.ui.game

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.screenshot.GameScreenshot
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.ui.theme.SlotWell
import java.io.File

/**
 * Takes screenshots on behalf of the game activity, and says so.
 *
 * The activity is Java and gets one method: [take]. Everything from arming the render thread to
 * telling the player where the picture went happens here.
 *
 * <b>Why there is a confirmation at all.</b> A screenshot has no visible result — the game carries
 * on exactly as it was, and the file lands in a folder nobody has open. Without a word on screen
 * the only way to find out whether the button worked is to leave the game and go looking, which is
 * the opposite of what a one-tap capture is for. The handbook's rule is that the thing you touched
 * is the thing that responds; the nearest this can get is responding immediately, in place, and
 * naming the file so it can be recognised later.
 *
 * <b>Why it is its own view.</b> Same reason the dictation overlay is: a `ComposeView` stretched
 * over the game surface takes every touch the game is waiting for, so this one is only as tall as
 * the card, is anchored clear of the middle of the screen, and is `GONE` whenever it has nothing
 * to say (§12.9).
 */
class ScreenshotHost(private val view: ComposeView) {

    /**
     * The last thing said, kept after it has been dismissed.
     *
     * Split from [showing] rather than nulled, because the card animates out and a composition
     * that had lost its text would spend the whole exit animating an empty box.
     */
    private var message by mutableStateOf(ScreenshotMessage(R.string.screenshot_saved, null, true))
    private var showing by mutableStateOf(false)

    private val handler = Handler(Looper.getMainLooper())

    private val hide = Runnable {
        showing = false
        // Held for the exit animation, then taken out of the way of the game's touches.
        handler.postDelayed({ if (!showing) view.visibility = View.GONE }, EXIT_MS)
    }

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            AmethystXTheme { ScreenshotToast(showing, message) }
        }
    }

    /** Capture the frame the game is presenting, then report where it went. */
    fun take() {
        GameScreenshot.take(object : GameScreenshot.Callback {
            override fun onScreenshotSaved(file: File) {
                show(ScreenshotMessage(R.string.screenshot_saved, file.name, true))
            }

            override fun onScreenshotFailed(messageRes: Int) {
                show(ScreenshotMessage(messageRes, null, false))
            }
        })
    }

    /** Drop the pending hide, so a finishing activity does not post to a dead view. */
    fun release() {
        handler.removeCallbacks(hide)
    }

    private fun show(next: ScreenshotMessage) {
        message = next
        showing = true
        view.visibility = View.VISIBLE
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, VISIBLE_MS)
    }

    private companion object {
        /** Long enough to read a filename, short enough not to sit over a fight. */
        const val VISIBLE_MS = 2600L
        const val EXIT_MS = 260L
    }
}

/** What the card is saying: a heading, the filename when there is one, and whether it went well. */
private class ScreenshotMessage(val titleRes: Int, val detail: String?, val ok: Boolean)

/**
 * The card itself.
 *
 * Top-anchored, because the bottom of the screen in landscape is where both thumbs and both of
 * the other overlays already are, and a capture confirmation must never be the thing covering the
 * hotbar. It is not interactive — nothing here is worth a second tap — so it takes no touches and
 * the game stays playable underneath.
 */
@Composable
private fun ScreenshotToast(showing: Boolean, shown: ScreenshotMessage) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
        AnimatedVisibility(
            visible = showing,
            enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it } +
                    fadeIn(tween(180)),
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it } +
                    fadeOut(tween(200))
        ) {
            Row(
                Modifier
                    .widthIn(max = 360.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SlotWell(size = 34.dp) {
                    Icon(
                        painterResource(
                            if (shown.ok) R.drawable.ic_x_camera else R.drawable.ic_x_info
                        ),
                        contentDescription = null,
                        tint = if (shown.ok) colors.primary else colors.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        stringResource(shown.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                    if (shown.detail != null) {
                        Text(
                            shown.detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
