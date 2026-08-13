package net.kdt.pojavlaunch.ui.game

import android.app.Activity
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kdt.mcgui.ProgressLayout
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.progresskeeper.ProgressListener
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader
import net.kdt.pojavlaunch.tasks.MinecraftDownloader
import net.kdt.pojavlaunch.testlaunch.TestLaunch
import net.kdt.pojavlaunch.ui.theme.Amethyst70
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.ui.theme.SlotWell

/**
 * The download that has to happen before the first test launch, done without leaving the editor.
 *
 * <b>The version used to be fetched by the launcher, in front of the launcher's own chrome</b>,
 * which made the test read as three screens: editor, launcher with a progress bar, game. The
 * download is the long part, so it is the part that moves in here: a bubble over the layout,
 * fed by the same {@link ProgressKeeper} record the launcher's bar reads, while the layout stays
 * editable underneath. When it finishes the bubble says where it is going, and only then does
 * the editor hand over, so the launcher's turn is a covered second or two instead of a visible
 * minute.
 *
 * The downloader itself is the launcher's own {@link MinecraftDownloader}, untouched: it already
 * takes any activity, reports through the progress keeper from a worker thread, and is the thing
 * the launch listener runs later to verify the files, which is what makes this a warm-up rather
 * than a second code path.
 */
class TestDownloadHost(
    private val activity: Activity,
    private val view: ComposeView,
    /** Runs on the main thread when the version is ready and the bubble has said its goodbye. */
    private val onReady: Runnable
) : ProgressListener {

    private var visible by mutableStateOf(false)
    private var entering by mutableStateOf(false)
    private var progress by mutableIntStateOf(0)
    private var detail by mutableStateOf("")
    private var active = false

    init {
        view.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        view.setContent {
            AmethystXTheme {
                DownloadBubble(
                    visible = visible,
                    entering = entering,
                    progress = progress,
                    detail = detail,
                    onCancel = ::cancel
                )
            }
        }
    }

    fun isActive(): Boolean = active

    /** Start the download and show the bubble. Safe to call only once per session. */
    fun begin() {
        if (active) return
        active = true
        entering = false
        progress = 0
        detail = ""
        visible = true
        view.visibility = View.VISIBLE
        ProgressKeeper.addListener(ProgressLayout.DOWNLOAD_MINECRAFT, this)
        MinecraftDownloader().start(
            activity,
            AsyncMinecraftDownloader.getListedVersion(TestLaunch.VERSION),
            TestLaunch.VERSION,
            object : AsyncMinecraftDownloader.DoneListener {
                override fun onDownloadDone() {
                    view.post {
                        if (!active) return@post
                        // The goodbye: the bubble becomes the loading screen for a beat, so the
                        // handoff to the covered launcher reads as one continuous motion.
                        entering = true
                        view.postDelayed({
                            if (!active) return@postDelayed
                            stopListening()
                            onReady.run()
                        }, 550)
                    }
                }

                override fun onDownloadFailed(throwable: Throwable) {
                    view.post {
                        if (!active) return@post
                        cancel()
                        if (!activity.isFinishing) Tools.showError(activity, throwable)
                    }
                }
            }
        )
    }

    /**
     * Stop waiting. The download itself has no cancel and keeps running in the background,
     * which is fine: the files land in the shared versions folder, and the next attempt
     * starts from whatever is already there.
     */
    fun cancel() {
        if (!active) return
        stopListening()
        visible = false
        // GONE after the exit animation, for the same reason every overlay here does it (12.9):
        // visible and empty, the view would sit in front of the layout's drags.
        view.postDelayed({ if (!active) view.visibility = View.GONE }, 260)
    }

    fun release() {
        stopListening()
        view.visibility = View.GONE
    }

    private fun stopListening() {
        active = false
        ProgressKeeper.removeListener(ProgressLayout.DOWNLOAD_MINECRAFT, this)
    }

    /* ProgressKeeper calls these on whatever thread submitted the progress. */

    override fun onProgressStarted() {}

    override fun onProgressUpdated(progress: Int, resid: Int, vararg va: Any?) {
        view.post {
            this.progress = progress.coerceIn(0, 100)
            if (resid != 0) {
                detail = try {
                    view.context.getString(resid, *va)
                } catch (ignored: Throwable) {
                    // A malformed format argument must not take the whole bubble down.
                    ""
                }
            }
        }
    }

    override fun onProgressEnded() {}
}

/**
 * The bubble itself: what is being fetched, how far along it is, and the one way out.
 *
 * Below the drawer pull tab and clear of the corners, because every edge of this screen is
 * somebody's button. Compact on purpose; the player is supposed to keep editing under it.
 */
@Composable
private fun DownloadBubble(
    visible: Boolean,
    entering: Boolean,
    progress: Int,
    detail: String,
    onCancel: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { -it } +
                fadeIn(tween(200)),
        exit = slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { -it } +
                fadeOut(tween(180))
    ) {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .padding(top = 30.dp)
                .widthIn(min = 300.dp, max = 420.dp)
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceContainer.copy(alpha = 0.96f))
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Crossfade(entering, animationSpec = tween(240), label = "bubblePhase") { going ->
                if (going) EnteringContent() else FetchingContent(progress, detail, onCancel)
            }
        }
    }
}

@Composable
private fun FetchingContent(progress: Int, detail: String, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SlotWell(color = Amethyst70.copy(alpha = 0.14f)) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.control_test_getting, TestLaunch.VERSION),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface
                )
                Text(
                    // The downloader's own progress line, which is what the launcher's bar
                    // would have shown: files, megabytes, speed.
                    detail.ifEmpty { stringResource(R.string.control_test_getting_hint) },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.control_test_keep_editing),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        Spacer(Modifier.height(11.dp))
        val fill by animateFloatAsState(
            targetValue = progress / 100f,
            animationSpec = tween(500, easing = FastOutSlowInEasing),
            label = "testDownloadFill"
        )
        LinearProgressIndicator(
            progress = { fill },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = colors.primary,
            trackColor = colors.surfaceContainerHighest
        )
    }
}

@Composable
private fun EnteringContent() {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        SlotWell(color = Amethyst70.copy(alpha = 0.14f)) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.test_launch_cover_title),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface
        )
    }
}
