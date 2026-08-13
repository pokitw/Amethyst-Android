package net.kdt.pojavlaunch.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.kdt.pojavlaunch.customcontrols.ControlLayout
import net.kdt.pojavlaunch.customcontrols.ControlTestBridge
import net.kdt.pojavlaunch.ui.controls.keyDisplayName

/**
 * Runs a control layout for real, with no game underneath it.
 *
 * <b>The ask was to launch Minecraft from the editor to try the controls out.</b> What is built
 * instead is the layout itself, live, because a real launch answers the question worse and much
 * more slowly. It costs a version download, a JVM and half a minute <i>per iteration</i>, which is
 * not a loop anybody will use to nudge a button by four dp; and once you are in a world, the only
 * way to find out which key a button actually sent is to watch the wrong thing happen and reason
 * backwards. Here the key is named the instant it is pressed.
 *
 * <p>What the real game genuinely has over this is the furniture the controls have to share the
 * screen with, so that is drawn: [ControlLayout.setTestMode] puts Minecraft's own hotbar and
 * crosshair behind the layout at the game's automatic GUI scale. Placing a button over the hotbar
 * is the mistake this catches, and it is the mistake that moved a column of the shipped Bedrock
 * layout.
 *
 * <p>Everything else is the game: buttons take their own touches, drawers open, the joystick
 * sends its four directions, a slide arms a repeat, a sequence runs on its clock, and the
 * visibility rules apply so a layout that would strand you at the title screen strands you here.
 */
class ControlTestHost(
    private val view: ComposeView,
    private val layout: ControlLayout,
    private val onFinished: Runnable
) : ControlTestBridge.Listener {

    private var running = false
    private var held by mutableStateOf(listOf<Int>())
    private var last by mutableStateOf("")
    private var playing by mutableStateOf(true)
    private var guides by mutableStateOf(true)
    private var expanded by mutableStateOf(true)

    init {
        // Same strategy the control center uses: a ComposeView in an XML layout keeps its
        // composition alive past the view tree otherwise.
        view.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        view.setContent {
            ControlTestPanel(
                state = ControlTestState(held, last, playing, guides, expanded),
                onTogglePlaying = ::togglePlaying,
                onToggleGuides = ::toggleGuides,
                onToggleExpanded = { expanded = !expanded },
                onDone = ::stop
            )
        }
    }

    fun isRunning(): Boolean = running

    fun start() {
        if (running) return
        running = true
        held = listOf()
        last = ""
        playing = true
        guides = true
        expanded = true
        layout.applyTestGuides(true)
        layout.setTestMode(true)
        ControlTestBridge.attach(this)
        view.visibility = View.VISIBLE
    }

    fun stop() {
        if (!running) return
        running = false
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
        ControlTestBridge.detach(this)
        running = false
    }

    private fun togglePlaying() {
        playing = !playing
        layout.applyTestGrabState(playing)
    }

    private fun toggleGuides() {
        guides = !guides
        layout.applyTestGuides(guides)
    }

    /**
     * What a control just sent.
     *
     * Held keys are a list rather than a set so they read in the order they went down, which is
     * the order somebody pressing a combination expects to see. A key already in it is not added
     * twice: two buttons can be bound to the same key, and releasing one of them would otherwise
     * leave a ghost entry that nothing can clear.
     */
    override fun onControlKey(keycode: Int, isDown: Boolean) {
        val name = keyDisplayName(keycode)
        if (isDown) {
            if (!held.contains(keycode)) held = held + keycode
            if (name.isNotEmpty()) last = name
        } else {
            held = held.filter { it != keycode }
        }
    }
}
