package net.kdt.pojavlaunch.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringArrayResource
import kotlinx.coroutines.delay
import net.kdt.pojavlaunch.splash.SplashPicker

/**
 * A line from a table, changed every so often, shared by every screen that makes you wait.
 *
 * One function rather than one per screen, because the three waits in this launcher are the same
 * wait at three lengths: the launcher getting ready, a version downloading, and the JVM starting
 * up behind a black screen. A player who meets a different idiom at each of them is being told
 * three times that these are three different programs.
 *
 * The table is a `string-array` so it lives in `strings.xml` with every other piece of copy, and
 * the ordering is [SplashPicker]'s, which shows every line once before repeating any.
 */
@Composable
fun rememberRotatingLine(arrayRes: Int, periodMs: Long = 7_000L): String {
    val lines = stringArrayResource(arrayRes)
    // Seeded from the clock inside remember, so the sequence differs between launches and is
    // drawn once rather than per recomposition. The picker is the thing being remembered: it
    // carries the bag, and rebuilding it each frame would make the rotation plain random again.
    val picker = remember(lines.size) { SplashPicker(lines.size, System.nanoTime()) }
    var index by remember(picker) { mutableStateOf(picker.next()) }
    LaunchedEffect(picker, periodMs) {
        while (true) {
            delay(periodMs)
            index = picker.next()
        }
    }
    return lines.getOrNull(index).orEmpty()
}
