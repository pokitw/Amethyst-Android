package net.kdt.pojavlaunch.ui.game

import androidx.compose.runtime.Immutable
import net.kdt.pojavlaunch.customcontrols.ControlData
import net.kdt.pojavlaunch.customcontrols.ControlLayout
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface
import net.kdt.pojavlaunch.customcontrols.buttons.ControlJoystick
import net.kdt.pojavlaunch.customcontrols.buttons.ControlSubButton
import net.kdt.pojavlaunch.ui.controls.keyDisplayName

/** A key going down or coming back up, as the session saw it. */
@Immutable
class ControlEvent(
    val keycode: Int,
    val down: Boolean,
    /** Milliseconds since the session started, which is the only clock worth showing here. */
    val atMs: Long,
    /** How long the key was held, on the release edge. Negative while it is still down. */
    val heldMs: Long
) {
    val name: String get() = keyDisplayName(keycode).ifEmpty { "key $keycode" }
}

/** The numbers that change while you press things. */
@Immutable
class ControlTestStats(
    val perSecond: Float = 0f,
    val events: Int = 0,
    val modifiers: String = "",
    /** The last press, held in milliseconds, or -1 when nothing has been released yet. */
    val lastHeldMs: Long = -1
)

/**
 * What is wrong with this layout, worked out by reading it rather than by playing it.
 *
 * <b>This is the half of debugging a control layout that pressing buttons cannot do.</b> A key
 * bound to two controls, a control with nothing bound at all, one pushed off the edge of the
 * screen by a position expression, one small enough to be unhittable: every one of those is
 * invisible until the moment it matters, and every one is a fact about the file that can simply
 * be looked up. So it is, once, when the session opens.
 */
@Immutable
class LayoutReport(
    val controls: Int = 0,
    val bound: Int = 0,
    val unbound: Int = 0,
    /** Keys sent by more than one control, most-shared first, already named. */
    val clashes: List<String> = emptyList(),
    val offScreen: Int = 0,
    val tiny: Int = 0,
    /** Controls that would be visible neither in play nor in a menu, so never at all. */
    val invisible: Int = 0
) {
    val healthy: Boolean
        get() = unbound == 0 && clashes.isEmpty() && offScreen == 0 && tiny == 0 && invisible == 0
}

/** Anything under this many dp on either side is hard to hit on purpose. */
private const val TINY_DP = 24f

/**
 * Read the layout and say what is odd about it.
 *
 * Deliberately a pure function over the live views: the editor is the only thing that knows where
 * a control actually ended up, because positions are expressions evaluated against the screen, so
 * a report built from the file alone would be answering a different question.
 */
fun inspectLayout(layout: ControlLayout, density: Float): LayoutReport {
    val children: List<ControlInterface> = layout.buttonChildren ?: return LayoutReport()
    val width = layout.width
    val height = layout.height

    var bound = 0
    var unbound = 0
    var offScreen = 0
    var tiny = 0
    var invisible = 0
    val keyOwners = HashMap<Int, Int>()

    for (control in children) {
        // A drawer is a container and a joystick's keys are fixed, so neither can be unbound or
        // clash in any way the player could act on. They still count towards the total.
        val skipKeys = control is ControlDrawer || control is ControlJoystick
        val data: ControlData = control.properties
        if (!skipKeys) {
            val keys = data.keycodes.filter { it != 0 }
            if (keys.isEmpty()) unbound++ else bound++
            for (key in keys) keyOwners[key] = (keyOwners[key] ?: 0) + 1
        } else {
            bound++
        }

        if (!data.displayInGame && !data.displayInMenu) invisible++

        val view = control.controlView
        // Sub-buttons live inside a drawer and are placed by it, often stacked off to one side
        // while closed, so measuring them against the screen would report nonsense.
        if (control !is ControlSubButton && width > 0 && height > 0) {
            val right = view.x + view.width
            val bottom = view.y + view.height
            if (view.x < -1f || view.y < -1f || right > width + 1f || bottom > height + 1f) {
                offScreen++
            }
        }
        if (view.width / density < TINY_DP || view.height / density < TINY_DP) tiny++
    }

    val clashes = keyOwners.entries
        .filter { it.value > 1 }
        .sortedByDescending { it.value }
        .map { "${keyDisplayName(it.key).ifEmpty { "key ${it.key}" }} x${it.value}" }

    return LayoutReport(
        controls = children.size,
        bound = bound,
        unbound = unbound,
        clashes = clashes,
        offScreen = offScreen,
        tiny = tiny,
        invisible = invisible
    )
}
