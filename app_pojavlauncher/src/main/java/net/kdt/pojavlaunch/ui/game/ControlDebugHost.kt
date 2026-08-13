package net.kdt.pojavlaunch.ui.game

import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.customcontrols.ControlTestBridge
import net.kdt.pojavlaunch.ui.controls.keyDisplayName
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import org.lwjgl.glfw.CallbackBridge

/**
 * What your buttons are sending, while you are actually playing.
 *
 * <b>The same seam as the editor's test session, in its other mode.</b> That one swallows the
 * press, because there is no game behind it and the senders are native symbols that do not exist
 * in the launcher process; this one lets every press through untouched and only watches it go by.
 * One flag on [ControlTestBridge.attach] is the whole difference, which is why the bridge takes
 * one rather than always swallowing.
 *
 * <p>Two lines, in the spirit of the game's own F3. The first is what is held right now, named.
 * The second is the technical readout: the raw GLFW keycode and held time of the last press, the
 * press rate, the modifier flags as the bridge holds them, the cursor the game is being told
 * about, whether the game has the cursor grabbed, and how many events this session has sent.
 * Every one of those is a fact the launcher owns; nothing here guesses at what Minecraft did
 * with a key, because nothing on this side can know (the same wall LiveTyper lives behind).
 *
 * <p>A strip rather than a panel, drawn top-left where Minecraft's own debug text would go, with
 * no controls on it at all. Anything tappable up here would be a thing to hit by accident while
 * playing, and the way to turn it off is the control center, which is where it was turned on.
 */
class ControlDebugHost(private val view: ComposeView) : ControlTestBridge.Listener {

    /** How often the polled half (cursor, grab, modifiers) refreshes. Event data is instant. */
    private val tickMs = 250L

    private var held by mutableStateOf(listOf<Int>())
    private var lastLine by mutableStateOf("")
    private var stats by mutableStateOf(DebugStats())
    private var showing = false

    /* Press bookkeeping. Main thread only, like the bridge itself. */
    private val downSince = HashMap<Int, Long>()
    private val downEdges = ArrayDeque<Long>()
    private var totalEvents = 0

    private val ticker = object : Runnable {
        override fun run() {
            if (!showing) return
            refreshStats()
            view.postDelayed(this, tickMs)
        }
    }

    init {
        view.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        view.setContent { AmethystXTheme { ControlDebugStrip(held, lastLine, stats) } }
    }

    fun isShowing(): Boolean = showing

    fun toggle() {
        if (showing) hide() else show()
    }

    fun show() {
        if (showing) return
        showing = true
        held = listOf()
        lastLine = ""
        downSince.clear()
        downEdges.clear()
        totalEvents = 0
        // false: watch, never swallow. A debug overlay that ate the presses it was reporting
        // would be the single worst bug this file could have.
        ControlTestBridge.attach(this, false)
        refreshStats()
        view.postDelayed(ticker, tickMs)
        view.visibility = View.VISIBLE
    }

    fun hide() {
        if (!showing) return
        showing = false
        view.removeCallbacks(ticker)
        ControlTestBridge.detach(this)
        view.visibility = View.GONE
        held = listOf()
    }

    /** For the activity going away: the seam is a static and must never outlive its listener. */
    fun release() {
        view.removeCallbacks(ticker)
        ControlTestBridge.detach(this)
        showing = false
    }

    override fun onControlKey(keycode: Int, isDown: Boolean) {
        val now = SystemClock.uptimeMillis()
        totalEvents++
        if (isDown) {
            if (!held.contains(keycode)) held = held + keycode
            downSince[keycode] = now
            downEdges.addLast(now)
            // The rate window: down edges in the last two seconds, halved to a per-second
            // figure. Two seconds rather than one so a spam-clicked rate reads steadily
            // instead of flickering between neighbouring integers.
            while (downEdges.isNotEmpty() && now - downEdges.first() > 2000) {
                downEdges.removeFirst()
            }
            lastLine = "${keyDisplayName(keycode)} $keycode"
        } else {
            held = held.filter { it != keycode }
            val since = downSince.remove(keycode)
            // The release is when the held time becomes a fact rather than a stopwatch.
            if (since != null) {
                lastLine = "${keyDisplayName(keycode)} $keycode, ${now - since} ms"
            }
        }
        refreshStats()
    }

    /**
     * The polled half. These are the bridge's own statics, written by every input surface in
     * the app, so the strip shows the exact values the game is being driven with rather than
     * a private copy that could drift.
     */
    private fun refreshStats() {
        val now = SystemClock.uptimeMillis()
        while (downEdges.isNotEmpty() && now - downEdges.first() > 2000) downEdges.removeFirst()
        val mods = buildString {
            if (CallbackBridge.holdingShift) append("Shift ")
            if (CallbackBridge.holdingCtrl) append("Ctrl ")
            if (CallbackBridge.holdingAlt) append("Alt ")
            if (CallbackBridge.holdingCapslock) append("Caps ")
            if (CallbackBridge.holdingNumlock) append("Num ")
        }.trim()
        stats = DebugStats(
            cursorX = CallbackBridge.mouseX.toInt(),
            cursorY = CallbackBridge.mouseY.toInt(),
            grabbing = CallbackBridge.isGrabbing(),
            modifiers = mods,
            perSecond = downEdges.size / 2f,
            events = totalEvents
        )
    }
}

@Immutable
private data class DebugStats(
    val cursorX: Int = 0,
    val cursorY: Int = 0,
    val grabbing: Boolean = false,
    val modifiers: String = "",
    val perSecond: Float = 0f,
    val events: Int = 0
)

@Composable
private fun ControlDebugStrip(held: List<Int>, lastLine: String, stats: DebugStats) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(start = 10.dp, top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.82f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (held.isEmpty()) {
                Text(
                    lastLine.ifEmpty { "controls" },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                held.take(8).forEach { keycode ->
                    Text(
                        "${keyDisplayName(keycode)} $keycode",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // The technical line. Monospace so the numbers hold still while they change; a cursor
        // readout that wobbles side to side with each digit is unreadable in motion.
        Text(
            buildString {
                append(if (stats.grabbing) "aim" else "menu")
                append("  xy ").append(stats.cursorX).append(',').append(stats.cursorY)
                if (stats.modifiers.isNotEmpty()) append("  ").append(stats.modifiers)
                if (stats.perSecond > 0f) {
                    append("  ")
                    append(String.format(java.util.Locale.ROOT, "%.1f", stats.perSecond))
                    append("/s")
                }
                append("  ev ").append(stats.events)
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
