package net.kdt.pojavlaunch.ui.game

import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.kdt.pojavlaunch.customcontrols.ControlLayout
import net.kdt.pojavlaunch.customcontrols.ControlTestBridge
import org.lwjgl.glfw.CallbackBridge

/**
 * Press the controls you have just arranged, for real, and be told exactly what they send.
 *
 * <b>A control in the launcher process cannot simply be pressed</b>, which is why the editor has
 * always intercepted touches before a button saw them: every send goes through
 * {@code CallbackBridge}, whose senders are {@code @CriticalNative} calls into a JVM that is
 * loaded in the {@code :game} process and nowhere else. So the session taps one level above the
 * bridge, at {@code ControlButton.sendSingleKey} and at the joystick's own {@code sendInput}.
 *
 * <p>Once that seam exists, <b>reporting what came through it beats the game at its own job</b>.
 * In a world you find out a button is bound wrongly by watching the wrong thing happen and
 * reasoning backwards; here the key is named, numbered and timed the moment it is pressed, the
 * layout is checked for the mistakes that are invisible until they matter, and there is a log to
 * scroll back through afterwards.
 *
 * <p>The game's own hotbar and crosshair are drawn behind the layout while this runs, which is
 * the one question a bare editor could never answer: a button at the bottom middle looks fine on
 * an empty screen and steals hotbar slot taps in play.
 */
class ControlTestHost(
    private val view: ComposeView,
    private val layout: ControlLayout,
    private val onFinished: Runnable
) : ControlTestBridge.Listener {

    /** How often the polled half refreshes. The event half is instant. */
    private val tickMs = 250L

    /** How much history is kept. Enough to scroll back through a combination, not a session. */
    private val logLimit = 60

    private var running = false
    private var held by mutableStateOf(listOf<Int>())
    private var log by mutableStateOf(listOf<ControlEvent>())
    private var stats by mutableStateOf(ControlTestStats())
    private var report by mutableStateOf(LayoutReport())
    private var playing by mutableStateOf(true)
    private var guides by mutableStateOf(true)
    private var panel by mutableStateOf(PanelMode.KEYS)
    private var atBottom by mutableStateOf(false)

    private var startedAt = 0L
    private val downSince = HashMap<Int, Long>()
    private val downEdges = ArrayDeque<Long>()
    private var totalEvents = 0

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            refreshStats()
            view.postDelayed(this, tickMs)
        }
    }

    init {
        view.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        view.setContent {
            ControlTestPanel(
                state = ControlTestState(
                    held = held,
                    log = log,
                    stats = stats,
                    report = report,
                    playing = playing,
                    guides = guides,
                    panel = panel,
                    atBottom = atBottom
                ),
                onTogglePlaying = ::togglePlaying,
                onToggleGuides = ::toggleGuides,
                onPanel = { panel = it },
                onMove = ::movePanel,
                onClearLog = { log = listOf() },
                onDone = ::stop
            )
        }
    }

    fun isRunning(): Boolean = running

    fun start() {
        if (running) return
        running = true
        startedAt = SystemClock.uptimeMillis()
        held = listOf()
        log = listOf()
        stats = ControlTestStats()
        playing = true
        guides = true
        panel = PanelMode.KEYS
        downSince.clear()
        downEdges.clear()
        totalEvents = 0
        layout.applyTestGuides(true)
        layout.setTestMode(true)
        ControlTestBridge.attach(this, true)
        view.visibility = View.VISIBLE
        // After setTestMode, because that is what applies the visibility rules and therefore what
        // decides where every control has actually ended up.
        view.post {
            if (running) report = inspectLayout(layout, view.resources.displayMetrics.density)
        }
        view.postDelayed(ticker, tickMs)
    }

    fun stop() {
        if (!running) return
        running = false
        view.removeCallbacks(ticker)
        // Detached before the layout goes back to editing, so a press already in flight cannot
        // find the game path on its way out.
        ControlTestBridge.detach(this)
        layout.setTestMode(false)
        view.visibility = View.GONE
        held = listOf()
        onFinished.run()
    }

    /** Called when the activity goes away mid-session, so the static seam is never left attached. */
    fun release() {
        view.removeCallbacks(ticker)
        ControlTestBridge.detach(this)
        running = false
    }

    private fun togglePlaying() {
        playing = !playing
        layout.applyTestGrabState(playing)
        // The visibility rules just moved, so what is on screen and where has moved with them.
        report = inspectLayout(layout, view.resources.displayMetrics.density)
    }

    private fun toggleGuides() {
        guides = !guides
        layout.applyTestGuides(guides)
    }

    /**
     * Put the panel at the other end of the screen.
     *
     * It is `wrap_content` and pinned by gravity, so this is one layout parameter rather than any
     * kind of drag. Two positions, not free movement: the panel is only ever in the way of the
     * controls at one end, and a thing you can drop anywhere is a thing you have to put back.
     */
    private fun movePanel() {
        atBottom = !atBottom
        val params = view.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL or
                if (atBottom) android.view.Gravity.BOTTOM else android.view.Gravity.TOP
        view.layoutParams = params
    }

    /**
     * What a control just sent.
     *
     * Held keys are a list rather than a set so they read in the order they went down, which is
     * the order somebody pressing a combination expects to see. A key already in it is not added
     * twice: two controls can be bound to the same key, and releasing one of them would otherwise
     * leave a ghost entry that nothing can clear. That case is common enough that the layout
     * report calls it out by name.
     */
    override fun onControlKey(keycode: Int, isDown: Boolean) {
        val now = SystemClock.uptimeMillis()
        val at = now - startedAt
        totalEvents++
        var heldMs = -1L
        if (isDown) {
            if (!held.contains(keycode)) held = held + keycode
            downSince[keycode] = now
            downEdges.addLast(now)
            trimEdges(now)
        } else {
            held = held.filter { it != keycode }
            val since = downSince.remove(keycode)
            if (since != null) heldMs = now - since
        }
        log = (log + ControlEvent(keycode, isDown, at, heldMs)).takeLast(logLimit)
        refreshStats(heldMs)
    }

    private fun trimEdges(now: Long) {
        while (downEdges.isNotEmpty() && now - downEdges.first() > 2000) downEdges.removeFirst()
    }

    /**
     * The polled half: the bridge's own statics, so the readout shows the exact values every
     * other input surface in the app writes rather than a private copy that could drift.
     */
    private fun refreshStats(lastHeld: Long = stats.lastHeldMs) {
        val now = SystemClock.uptimeMillis()
        trimEdges(now)
        val mods = buildString {
            if (CallbackBridge.holdingShift) append("Shift ")
            if (CallbackBridge.holdingCtrl) append("Ctrl ")
            if (CallbackBridge.holdingAlt) append("Alt ")
            if (CallbackBridge.holdingCapslock) append("Caps ")
            if (CallbackBridge.holdingNumlock) append("Num ")
        }.trim()
        stats = ControlTestStats(
            // Over two seconds rather than one, so a spam-clicked rate reads steadily instead of
            // flickering between neighbouring integers.
            perSecond = downEdges.size / 2f,
            events = totalEvents,
            modifiers = mods,
            lastHeldMs = lastHeld
        )
    }
}
