package net.kdt.pojavlaunch.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kdt.mcgui.ProgressLayout
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.progresskeeper.ProgressListener
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener

/**
 * What the launcher is busy doing, if anything.
 *
 * [busy] deliberately follows the task count rather than the observed keys, because that is the
 * same condition the launch path refuses on: whenever this is true, pressing Play would only
 * produce "tasks are in progress", so the button reports the work instead of pretending to be
 * ready. [label] and [percent] are best effort on top of that.
 */
data class LaunchProgress(
    val busy: Boolean = false,
    val percent: Int = -1,
    val label: String? = null
)

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

/** The latest report from one key, timestamped so the newest one is the one shown. */
private data class KeyProgress(val percent: Int, val label: String?, val at: Long)

/**
 * Follow the launcher's progress for as long as this is in the composition.
 *
 * Progress is submitted from whichever thread is doing the work, which snapshot state handles;
 * recomposition is still scheduled on the main thread.
 */
@Composable
fun rememberLaunchProgress(): LaunchProgress {
    val context = LocalContext.current
    var taskCount by remember { mutableIntStateOf(ProgressKeeper.getTaskCount()) }
    // Kept per key rather than as one value, because registering a listener for an idle key
    // reports that immediately and would otherwise wipe a live report from another key.
    val reports = remember { mutableStateMapOf<String, KeyProgress>() }

    DisposableEffect(Unit) {
        val countListener = TaskCountListener { count -> taskCount = count }
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
                    reports[key] = KeyProgress(progress, text, System.nanoTime())
                }

                override fun onProgressEnded() {
                    reports.remove(key)
                }
            }
        }

        listeners.forEach { (key, listener) -> ProgressKeeper.addListener(key, listener) }
        ProgressKeeper.addTaskCountListener(countListener)
        onDispose {
            listeners.forEach { (key, listener) -> ProgressKeeper.removeListener(key, listener) }
            ProgressKeeper.removeTaskCountListener(countListener)
        }
    }

    if (taskCount <= 0) return LaunchProgress()
    val newest = reports.values.maxByOrNull { it.at }
    return LaunchProgress(busy = true, percent = newest?.percent ?: -1, label = newest?.label)
}
