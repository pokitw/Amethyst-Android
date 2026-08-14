package net.kdt.pojavlaunch.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kdt.mcgui.ProgressLayout
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.progresskeeper.ProgressListener
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener

/**
 * One thing the launch has done or is doing, named by the launcher's own report.
 *
 * [id] is the string resource the report arrived under, which is what makes the timeline honest:
 * the downloader's counts and speeds churn several times a second, and every churn is the same
 * stage updating its line, not a new stage. A report with no resource at all is identified by its
 * progress key instead, so a percent-only task still gets exactly one line.
 */
data class LaunchStage(val id: String, val text: String?, val percent: Int)

/**
 * What the launcher is busy doing, if anything.
 *
 * [busy] deliberately follows the task count rather than the observed keys, because that is the
 * same condition the launch path refuses on: whenever this is true, pressing Play would only
 * produce "tasks are in progress", so the card reports the work instead of pretending to be
 * ready. [requested] is the press itself, echoed locally so the card can respond in the frame
 * the finger lifts rather than whenever the first report lands.
 */
data class LaunchProgress(
    val busy: Boolean = false,
    val percent: Int = -1,
    val requested: Boolean = false,
    val stages: List<LaunchStage> = emptyList()
) {
    /** Whether the card should be showing the launch console rather than the Play button. */
    val active: Boolean get() = busy || requested
}

/** The keys the launcher submits work under. */
private val OBSERVED_KEYS = listOf(
    ProgressLayout.DOWNLOAD_MINECRAFT,
    ProgressLayout.UNPACK_RUNTIME,
    ProgressLayout.INSTALL_MODPACK,
    ProgressLayout.AUTHENTICATE_MICROSOFT,
    ProgressLayout.EXTRACT_COMPONENTS,
    ProgressLayout.EXTRACT_SINGLE_FILES,
    ProgressLayout.DOWNLOAD_VERSION_LIST
)

/** How much of the timeline is kept on screen. A launch worth watching has three or four stages. */
private const val STAGE_LIMIT = 4

/**
 * Follow the launcher's progress for as long as this is in the composition.
 *
 * Progress is submitted from whichever thread is doing the work, which snapshot state handles;
 * recomposition is still scheduled on the main thread. The read-modify-write on the stage list is
 * safe without a lock of its own because every callback below is delivered inside
 * [ProgressKeeper]'s class lock, so two reports can never interleave.
 *
 * Registering a listener for a key that is already running replays its current state, which is
 * what rebuilds the live stage line when home is returned to in the middle of a download.
 */
@Composable
fun rememberLaunchProgress(requested: Boolean = false): LaunchProgress {
    val context = LocalContext.current
    var taskCount by remember { mutableIntStateOf(ProgressKeeper.getTaskCount()) }
    var stages by remember { mutableStateOf(listOf<LaunchStage>()) }
    var percent by remember { mutableIntStateOf(-1) }

    DisposableEffect(Unit) {
        val countListener = TaskCountListener { count ->
            taskCount = count
            // The last launch's timeline must not open the next launch's console.
            if (count == 0) {
                stages = listOf()
                percent = -1
            }
        }
        val listeners = OBSERVED_KEYS.map { key ->
            key to object : ProgressListener {
                override fun onProgressStarted() {}

                override fun onProgressUpdated(progress: Int, resid: Int, vararg va: Any?) {
                    // Mirrors how the progress bar resolves its own text: a string resource with
                    // arguments when there is one, otherwise a message passed through as-is.
                    val text = when {
                        resid != -1 -> runCatching { context.getString(resid, *va) }.getOrNull()
                        va.isNotEmpty() -> va[0] as? String
                        else -> null
                    }
                    val id = if (resid != -1) "r$resid" else "k$key"
                    val current = stages
                    stages = if (current.isNotEmpty() && current.last().id == id) {
                        current.dropLast(1) + LaunchStage(id, text, progress)
                    } else {
                        (current + LaunchStage(id, text, progress)).takeLast(STAGE_LIMIT)
                    }
                    percent = progress
                }

                // Nothing to do: the timeline keeps what happened, and the count going to zero is
                // what clears it. At registration time an idle key lands here, also as a no-op.
                override fun onProgressEnded() {}
            }
        }

        listeners.forEach { (key, listener) -> ProgressKeeper.addListener(key, listener) }
        ProgressKeeper.addTaskCountListener(countListener)
        onDispose {
            listeners.forEach { (key, listener) -> ProgressKeeper.removeListener(key, listener) }
            ProgressKeeper.removeTaskCountListener(countListener)
        }
    }

    val busy = taskCount > 0
    if (!busy && !requested) return LaunchProgress()
    return LaunchProgress(
        busy = busy,
        percent = if (stages.isEmpty()) -1 else percent,
        requested = requested,
        stages = stages
    )
}
