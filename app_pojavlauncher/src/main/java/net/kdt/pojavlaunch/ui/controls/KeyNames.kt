package net.kdt.pojavlaunch.ui.controls

import androidx.compose.runtime.Immutable
import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.customcontrols.ControlData
import net.kdt.pojavlaunch.customcontrols.ControlGlyphs
import net.kdt.pojavlaunch.ui.game.KeyKind
import net.kdt.pojavlaunch.ui.game.boardRows
import net.kdt.pojavlaunch.ui.game.bottomRow

/**
 * One of the things a control can do instead of sending a key.
 *
 * @param keycode the negative constant written into the layout file
 * @param shortName what fits on a button, and what a freshly bound control is named
 * @param labelRes what it is actually called, for the picker
 * @param glyphRes the icon it carries, or 0
 */
@Immutable
class SpecialAction(
    val keycode: Int,
    val shortName: String,
    val labelRes: Int,
    val glyphRes: Int
)

/**
 * What a bound key is called on the button itself.
 *
 * Short on purpose — this is the legend on a 50dp control, not a description. The cap legends from
 * the on-screen keyboard are reused rather than a second table being written, because a second
 * table is a thing that goes stale: add a key to the board and it is named here too.
 */
fun keyDisplayName(keycode: Int): String = when {
    keycode == NO_KEY -> ""
    keycode < 0 -> SPECIAL_SHORT_NAMES[keycode] ?: "?"
    else -> CAP_LABELS[keycode] ?: fallbackName(keycode)
}

/** Everything a control can be bound to that is not a key, in the order the editor offers them. */
fun specialActions(): List<SpecialAction> = SPECIAL_ACTIONS

/**
 * The keys the old spinner offered that no on-screen board has a cap for.
 *
 * There should be none, and there are none today, but the spinner's list is the contract for what a
 * saved layout may contain and a layout written by another launcher can hold anything. Naming it
 * from its own table beats showing a number.
 */
private fun fallbackName(keycode: Int): String {
    val index = EfficientAndroidLWJGLKeycode.getIndexByValue(keycode)
    val names = EfficientAndroidLWJGLKeycode.generateKeyName()
    return if (index >= 0 && index < names.size) names[index] else keycode.toString()
}

/** Every cap on both boards, by GLFW keycode. */
private val CAP_LABELS: Map<Int, String> by lazy {
    (boardRows(false) + boardRows(true) + listOf(bottomRow(false)))
        .flatten()
        .filter { it.kind != KeyKind.SPACER }
        .associate { it.code.toInt() to it.label }
}

/**
 * The special actions, paired with words for them.
 *
 * The short names come from [ControlData.getSpecialButtons] rather than being repeated here, so the
 * button a player adds from the picker is named exactly what the editor's own list has always
 * called it. The long names are new: "SPECIAL_FWD" told nobody anything.
 *
 * Ordered by what people reach for — the mouse first, because a launcher's whole job on a
 * touchscreen is giving back the two buttons it does not have.
 */
private val SPECIAL_ACTIONS: List<SpecialAction> by lazy {
    val shortNames = ControlData.getSpecialButtons().associate { it.keycodes[0] to it.name }
    val ordered = listOf(
        ControlData.SPECIALBTN_MOUSEPRI to R.string.control_special_mouse_primary,
        ControlData.SPECIALBTN_MOUSESEC to R.string.control_special_mouse_secondary,
        ControlData.SPECIALBTN_MOUSEMID to R.string.control_special_mouse_middle,
        ControlData.SPECIALBTN_SCROLLUP to R.string.control_special_scroll_up,
        ControlData.SPECIALBTN_SCROLLDOWN to R.string.control_special_scroll_down,
        ControlData.SPECIALBTN_MOUSEBCK to R.string.control_special_mouse_back,
        ControlData.SPECIALBTN_MOUSEFWD to R.string.control_special_mouse_forward,
        ControlData.SPECIALBTN_VIRTUALMOUSE to R.string.control_special_virtual_mouse,
        ControlData.SPECIALBTN_GAMEKEYBOARD to R.string.control_special_game_keyboard,
        ControlData.SPECIALBTN_VOICE to R.string.control_special_voice,
        ControlData.SPECIALBTN_KEYBOARD to R.string.control_special_system_keyboard,
        ControlData.SPECIALBTN_MENU to R.string.control_special_menu,
        ControlData.SPECIALBTN_TOGGLECTRL to R.string.control_special_toggle_controls
    )
    ordered.map { (keycode, labelRes) ->
        SpecialAction(
            keycode = keycode,
            shortName = shortNames[keycode] ?: "?",
            labelRes = labelRes,
            glyphRes = ControlGlyphs.glyphForKeycode(keycode)
        )
    }
}

private val SPECIAL_SHORT_NAMES: Map<Int, String> by lazy {
    SPECIAL_ACTIONS.associate { it.keycode to it.shortName }
}
