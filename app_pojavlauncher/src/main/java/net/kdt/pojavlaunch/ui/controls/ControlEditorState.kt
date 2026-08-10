package net.kdt.pojavlaunch.ui.controls

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.customcontrols.ControlData
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface
import net.kdt.pojavlaunch.customcontrols.buttons.ControlJoystick
import net.kdt.pojavlaunch.customcontrols.buttons.ControlSubButton

/** Which of the four things a control can be, and therefore which settings apply to it. */
enum class ControlKind { BUTTON, SUB_BUTTON, DRAWER, JOYSTICK }

/** An empty key slot. Zero rather than -1, which is what a special action would be. */
const val NO_KEY = 0

/** How many keys one control may send at once. Fixed by [ControlData]'s inflated array. */
const val MAX_KEYS = 4

/**
 * What the editor panel is editing, and the only place that writes it back.
 *
 * The control being edited is a live view on screen with a live [ControlData] behind it, and the
 * whole point of editing beside it rather than in a dialog over it is that every change shows up
 * immediately on the real thing. So this is not a form that is filled in and applied: each setter
 * writes the field, then calls whichever of the button's refresh methods that field actually needs.
 * Getting those wrong is invisible until someone drags a slider and nothing moves, which is why
 * they are gathered here rather than spread across the composables.
 *
 * The mirrored properties exist because [ControlData] is a plain mutable Java object and Compose
 * cannot see writes to one. Every field is therefore stored twice, and this class is the only
 * thing allowed to know that.
 */
@Stable
class ControlEditorState(val button: ControlInterface) {

    private val data: ControlData get() = button.properties

    // Order matters: ControlDrawer and ControlSubButton both extend ControlButton, so the plain
    // button has to be the case that is left over rather than the one that is tested for.
    val kind: ControlKind = when (button) {
        is ControlJoystick -> ControlKind.JOYSTICK
        is ControlDrawer -> ControlKind.DRAWER
        is ControlSubButton -> ControlKind.SUB_BUTTON
        else -> ControlKind.BUTTON
    }

    /**
     * Whether this control's size is its own to set.
     *
     * A sub-button in anything but a free-form drawer takes the drawer's size, so offering the
     * fields would be offering a control that does nothing.
     */
    val sizeEditable: Boolean = when (kind) {
        ControlKind.SUB_BUTTON ->
            (button as ControlSubButton).parentDrawer.drawerData.orientation ==
                    ControlDrawerData.Orientation.FREE
        else -> true
    }

    /** Keys belong to things that send keys. A drawer opens; a joystick moves. */
    val keysEditable: Boolean = kind == ControlKind.BUTTON || kind == ControlKind.SUB_BUTTON

    /** A sub-button is shown by its drawer, so it has no visibility of its own. */
    val visibilityEditable: Boolean = kind != ControlKind.SUB_BUTTON

    /** A joystick has no name to show and no corner to round. */
    val nameEditable: Boolean = kind != ControlKind.JOYSTICK
    val cornerEditable: Boolean = kind != ControlKind.JOYSTICK

    /** Toggle, swipe and pass-through are all about a press, which only a button has. */
    val behaviourEditable: Boolean = kind == ControlKind.BUTTON || kind == ControlKind.SUB_BUTTON

    var name by mutableStateOf(data.name.orEmpty())
        private set
    var keys by mutableStateOf(data.keycodes.toList())
        private set
    var width by mutableStateOf(data.getWidth())
        private set
    var height by mutableStateOf(data.getHeight())
        private set
    var opacity by mutableStateOf(data.opacity)
        private set
    var cornerRadius by mutableStateOf(data.cornerRadius)
        private set
    var strokeWidth by mutableStateOf(data.strokeWidth)
        private set
    var fillColor by mutableStateOf(data.bgColor)
        private set
    var strokeColor by mutableStateOf(data.strokeColor)
        private set
    var isToggle by mutableStateOf(data.isToggle)
        private set
    var sequence by mutableStateOf(data.sequence)
        private set
    var sequenceGap by mutableStateOf(if (data.sequenceGap > 0) data.sequenceGap else 50)
        private set
    var isSwipeable by mutableStateOf(data.isSwipeable)
        private set
    var passThrough by mutableStateOf(data.passThruEnabled)
        private set
    var showInGame by mutableStateOf(data.displayInGame)
        private set
    var showInMenu by mutableStateOf(data.displayInMenu)
        private set
    var forwardLock by mutableStateOf((data as? ControlJoystickData)?.forwardLock ?: false)
        private set
    var absoluteTracking by mutableStateOf((data as? ControlJoystickData)?.absolute ?: false)
        private set
    var drawerOrientation by mutableStateOf(
        (button as? ControlDrawer)?.drawerData?.orientation ?: ControlDrawerData.Orientation.RIGHT
    )
        private set

    fun applyName(value: String) {
        name = value
        data.name = value
        // Through setProperties rather than setText: the label and the action glyph are chosen
        // together, and only that call knows which of the two this control should be showing.
        button.setProperties(data, false)
    }

    /**
     * Bind a key into one slot.
     *
     * The rename is the part worth keeping. A button called "New" or still carrying the name of
     * the key it used to send is a button nobody has named, so binding a key names it — which is
     * what makes adding a control a single decision instead of two.
     */
    fun bindKey(slot: Int, keycode: Int) {
        if (slot !in 0 until MAX_KEYS) return
        val previous = keys.getOrElse(slot) { NO_KEY }
        val updated = keys.toMutableList()
        while (updated.size < MAX_KEYS) updated.add(NO_KEY)
        updated[slot] = keycode
        keys = updated.toList()
        // Rebuilt rather than written in place: a ControlData read back from a layout file carries
        // whatever length that file had, and only a freshly constructed one is guaranteed four.
        data.keycodes = IntArray(MAX_KEYS) { updated[it] }

        if (slot == 0 && keycode != NO_KEY && nameEditable && isUnnamed(previous)) {
            applyName(keyDisplayName(keycode))
        } else {
            button.setProperties(data, false)
        }
    }

    /** Whether the name is one nobody chose, and so one this is free to replace. */
    private fun isUnnamed(previousKey: Int): Boolean =
        name.isEmpty() || name == DEFAULT_NAME ||
                (previousKey != NO_KEY && name == keyDisplayName(previousKey))

    fun applySize(newWidth: Float, newHeight: Float) {
        // Joysticks are round, so the two are one number however it was reached.
        val square = kind == ControlKind.JOYSTICK
        val w = newWidth.coerceIn(MIN_SIZE, MAX_SIZE)
        val h = (if (square) w else newHeight).coerceIn(MIN_SIZE, MAX_SIZE)
        width = w
        height = h
        data.setWidth(w)
        data.setHeight(h)
        button.updateProperties()
    }

    fun applyOpacity(value: Float) {
        opacity = value
        data.opacity = value
        // Straight onto the view: this runs on every frame of a drag, and going through
        // setBackground would rebuild the drawable each time to change one channel.
        button.controlView.alpha = value
    }

    fun applyCornerRadius(value: Float) {
        cornerRadius = value
        data.cornerRadius = value
        button.setBackground()
    }

    fun applyStrokeWidth(value: Float) {
        strokeWidth = value
        data.strokeWidth = value
        button.setBackground()
    }

    fun applyFillColor(color: Int) {
        fillColor = color
        data.bgColor = color
        button.setBackground()
    }

    fun applyStrokeColor(color: Int) {
        strokeColor = color
        data.strokeColor = color
        button.setBackground()
    }

    fun applyToggle(value: Boolean) {
        isToggle = value
        data.isToggle = value
        // The two are answers to opposite questions: a toggle holds its keys until the next tap,
        // a sequence presses and releases them on a clock. Turning one on turns the other off,
        // visibly in the panel, rather than leaving a written combination whose meaning nobody
        // could predict from the two switch labels.
        if (value && sequence) applySequence(false)
        // The toggle overlay is tinted differently from the press flash, and that colour is
        // chosen in setProperties.
        button.setProperties(data, false)
    }

    fun applySequence(value: Boolean) {
        sequence = value
        data.sequence = value
        data.sequenceGap = sequenceGap
        if (value && isToggle) applyToggle(false)
    }

    fun applySequenceGap(value: Int) {
        sequenceGap = value
        data.sequenceGap = value
    }

    fun applySwipeable(value: Boolean) {
        isSwipeable = value
        data.isSwipeable = value
    }

    fun applyPassThrough(value: Boolean) {
        passThrough = value
        data.passThruEnabled = value
    }

    fun applyShowInGame(value: Boolean) {
        showInGame = value
        data.displayInGame = value
    }

    fun applyShowInMenu(value: Boolean) {
        showInMenu = value
        data.displayInMenu = value
    }

    fun applyForwardLock(value: Boolean) {
        forwardLock = value
        (data as? ControlJoystickData)?.forwardLock = value
    }

    fun applyAbsoluteTracking(value: Boolean) {
        absoluteTracking = value
        (data as? ControlJoystickData)?.absolute = value
    }

    fun applyDrawerOrientation(value: ControlDrawerData.Orientation) {
        drawerOrientation = value
        val drawer = button as? ControlDrawer ?: return
        drawer.drawerData.orientation = value
        drawer.syncButtons()
    }

    companion object {
        /** The name a freshly added button carries, and therefore one nobody has chosen. */
        const val DEFAULT_NAME = "New"

        /** Small enough to be a corner tap target, large enough not to vanish. */
        const val MIN_SIZE = 20f
        const val MAX_SIZE = 400f
    }
}
