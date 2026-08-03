package net.kdt.pojavlaunch.ui.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_0
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_1
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_2
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_3
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_4
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_5
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_6
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_7
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_8
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_9
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_A
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_B
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_BACKSLASH
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_BACKSPACE
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_C
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_COMMA
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_D
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_DELETE
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_DOWN
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_E
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_END
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_ENTER
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_EQUAL
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_ESCAPE
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F1
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F10
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F11
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F12
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F2
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F3
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F4
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F5
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F6
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F7
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F8
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F9
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_G
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_H
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_HOME
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_I
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_INSERT
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_J
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_K
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_0
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_1
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_2
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_3
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_4
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_5
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_6
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_7
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_8
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_9
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_ADD
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_DECIMAL
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_DIVIDE
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_ENTER
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_MULTIPLY
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_KP_SUBTRACT
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_L
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_LEFT
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_M
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_MINUS
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_N
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_O
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_P
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_PAGE_DOWN
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_PAGE_UP
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_PAUSE
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_PERIOD
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_PRINT_SCREEN
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_Q
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_R
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_RIGHT
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_S
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_SCROLL_LOCK
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_SEMICOLON
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_SLASH
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_SPACE
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_T
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_TAB
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_U
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_UNKNOWN
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_UP
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_V
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_W
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_X
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_Y
import net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_Z
import org.lwjgl.glfw.CallbackBridge

/** How a cap behaves, and — because the two go together — how it is drawn. */
enum class KeyKind {
    /** Letters, digits, symbols. The bright caps; these are the ones that type a character. */
    CHARACTER,

    /** Esc, Tab, Enter, the function row, the arrows. Quiet caps that send a key and nothing else. */
    ACTION,

    /** Shift, Ctrl, Alt, the locks. A tap latches them down and a second tap lets them back up. */
    MODIFIER,

    /** The one cap that switches between the two boards instead of sending anything. */
    LAYOUT,

    /** Not a key at all — the shaped gaps that give a board its silhouette. */
    SPACER
}

/**
 * One cap on the on-screen keyboard.
 *
 * @param code the GLFW key this cap sends
 * @param label what is printed on it
 * @param shiftLabel the legend Shift reveals, for caps that carry two — `1` and `!` on one key
 * @param char the character it types; [NO_CHAR] for caps that type nothing
 * @param shiftChar the character it types with Shift down
 * @param weight the cap's share of its row. Every row adds up to [ROW_UNITS], which is what keeps
 *               the columns lined up between rows and between the two boards.
 */
data class Key(
    val code: Short,
    val label: String,
    val shiftLabel: String? = null,
    val char: Char = NO_CHAR,
    val shiftChar: Char = NO_CHAR,
    val weight: Float = 1f,
    val kind: KeyKind = KeyKind.CHARACTER
)

/** A cap that types nothing. Blocked downstream anyway, since it is an ISO control character. */
const val NO_CHAR = '\u0000'

/** Every row adds up to this, so the caps line up in columns down the whole board. */
const val ROW_UNITS = 15f

/**
 * The live state of the on-screen keyboard: which keys are latched down, and which board is up.
 *
 * Held keys are the reason this is a class rather than a handful of functions. A latched Shift is
 * real state inside the *game* — the key-down has been sent and no key-up has followed it — so
 * something has to remember it, show it, and above all give it back. [releaseAll] is that promise,
 * and the host calls it whenever the keyboard leaves the screen: a Ctrl left down after the sheet
 * closed would quietly break every key the player pressed afterwards.
 *
 * Stable in the Compose sense: everything a composable can read here is snapshot state, so a board
 * of eighty caps holding a reference to it is safe and skippable.
 */
@Stable
class GameKeyboardState {

    /** The keys currently latched down, by GLFW keycode. */
    var held: Set<Short> by mutableStateOf(emptySet())
        private set

    /** True while the numpad and navigation board is showing instead of the letters. */
    var numeric: Boolean by mutableStateOf(false)
        private set

    private val shifted: Boolean
        get() = GLFW_KEY_LEFT_SHIFT in held || GLFW_KEY_RIGHT_SHIFT in held

    private val capsLocked: Boolean
        get() = GLFW_KEY_CAPS_LOCK in held

    /** What this cap reads right now — Shift swaps in the second legend where there is one. */
    fun legend(key: Key): String =
        if (shifted && key.shiftLabel != null) key.shiftLabel else key.label

    /**
     * A tap.
     *
     * Tapping a latched key lets it up, which is what makes the latch feel like a switch rather
     * than a trap. Modifiers latch on a plain tap because they are never useful on their own;
     * everything else sends a press and a release, and has to be held deliberately.
     */
    fun tap(key: Key) {
        when {
            key.kind == KeyKind.SPACER -> return
            key.kind == KeyKind.LAYOUT -> numeric = !numeric
            key.code in held -> release(key.code)
            key.kind == KeyKind.MODIFIER -> hold(key.code)
            else -> stroke(key)
        }
    }

    /** A long press. Latches any key down, so F3 can be held while G is tapped beside it. */
    fun longPress(key: Key) {
        if (key.kind == KeyKind.SPACER || key.kind == KeyKind.LAYOUT) return
        if (key.code in held) release(key.code) else hold(key.code)
    }

    /** Let one named key up, for the chips in the header that report what is being held. */
    fun releaseKey(code: Short) {
        if (code in held) release(code)
    }

    /**
     * Start on the letters.
     *
     * A surface that opens onto a different board depending on what was last used reads as broken
     * rather than as helpful, and the numpad is one clearly labelled tap away.
     */
    fun showLetters() {
        numeric = false
    }

    /** Let every latched key back up. Safe to call when nothing is held. */
    fun releaseAll() {
        val latched = held
        if (latched.isEmpty()) return
        held = emptySet()
        // The key-up carries the modifiers that were in force while the key was down, so the
        // flags are cleared only after the last release has gone out.
        for (code in latched) {
            CallbackBridge.sendKeyPress(code.toInt(), CallbackBridge.getCurrentMods(), false)
        }
        for (code in latched) syncModifier(code)
    }

    private fun hold(code: Short) {
        held = held + code
        syncModifier(code)
        CallbackBridge.sendKeyPress(code.toInt(), CallbackBridge.getCurrentMods(), true)
    }

    private fun release(code: Short) {
        held = held - code
        CallbackBridge.sendKeyPress(code.toInt(), CallbackBridge.getCurrentMods(), false)
        syncModifier(code)
    }

    private fun stroke(key: Key) {
        val mods = CallbackBridge.getCurrentMods()
        val typed = charFor(key)
        CallbackBridge.sendKeyPress(key.code.toInt(), typed, 0, mods, true)
        CallbackBridge.sendKeyPress(key.code.toInt(), typed, 0, mods, false)
    }

    /**
     * The character a cap types, or [NO_CHAR].
     *
     * Sending it is what lets this keyboard write in chat and not only trigger keybinds. Letters
     * follow Shift *and* Caps Lock, the way a real one does; everything else follows Shift alone.
     */
    private fun charFor(key: Key): Char {
        if (key.kind != KeyKind.CHARACTER) return NO_CHAR
        val upper = if (key.char.isLetter()) shifted xor capsLocked else shifted
        return if (upper) key.shiftChar else key.char
    }

    /**
     * Mirror one latch into the modifier flags the rest of the input path reads.
     *
     * Deliberately narrow: it touches only the flag belonging to the key that just changed, and
     * derives that flag from [held] rather than from the change. Rewriting all five would stamp on
     * a modifier some other part of the launcher is holding, and reading the change alone would
     * drop the flag when one of a pair of Shifts came up while the other was still down.
     */
    private fun syncModifier(code: Short) {
        when (code) {
            GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT ->
                CallbackBridge.holdingShift = shifted
            GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL ->
                CallbackBridge.holdingCtrl =
                    GLFW_KEY_LEFT_CONTROL in held || GLFW_KEY_RIGHT_CONTROL in held
            GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT ->
                CallbackBridge.holdingAlt =
                    GLFW_KEY_LEFT_ALT in held || GLFW_KEY_RIGHT_ALT in held
            GLFW_KEY_CAPS_LOCK -> CallbackBridge.holdingCapslock = capsLocked
            GLFW_KEY_NUM_LOCK -> CallbackBridge.holdingNumlock = GLFW_KEY_NUM_LOCK in held
        }
    }
}

/**
 * The rows above the bottom row.
 *
 * Takes the board rather than reading it off the state, so a transition between the two can draw
 * both at once without either of them changing under it.
 */
fun boardRows(numeric: Boolean): List<List<Key>> = if (numeric) NUMERIC_ROWS else LETTER_ROWS

/**
 * The legend for a latched key, so the header can name what is being held.
 *
 * Built from the boards themselves rather than kept as a second list, because a second list is a
 * thing that goes stale.
 */
fun keyLabel(code: Short): String = KEY_LABELS[code] ?: "?"

private val KEY_LABELS: Map<Short, String> by lazy {
    (LETTER_ROWS + NUMERIC_ROWS + listOf(BOTTOM_ROW))
        .flatten()
        .filter { it.kind != KeyKind.SPACER }
        .associate { it.code to it.label }
}

/*
 * The boards.
 *
 * Cap legends are written here as plain text rather than as string resources on purpose: they are
 * the markings on a keyboard, which read the same in every language a Latin-script keyboard is
 * sold in, and the shift pairs below are the US layout the game itself assumes. Everything that is
 * actually prose — the title, the hint, the button labels — is in strings.xml where it belongs.
 */

private fun letter(code: Short, label: String, lower: Char) =
    Key(code, label, char = lower, shiftChar = lower.uppercaseChar())

private fun pair(
    code: Short, label: String, plain: Char, shiftLabel: String, shift: Char, weight: Float = 1f
) = Key(code, label, shiftLabel, plain, shift, weight)

private fun act(code: Short, label: String, weight: Float = 1f) =
    Key(code, label, weight = weight, kind = KeyKind.ACTION)

private fun mod(code: Short, label: String, weight: Float = 1f) =
    Key(code, label, weight = weight, kind = KeyKind.MODIFIER)

private fun digit(code: Short, label: String, value: Char, weight: Float = 1f) =
    Key(code, label, char = value, shiftChar = value, weight = weight)

private fun gap(weight: Float) =
    Key(GLFW_KEY_UNKNOWN, "", weight = weight, kind = KeyKind.SPACER)

/** Letters, digits and symbols: the board that is up when the keyboard opens. */
private val LETTER_ROWS: List<List<Key>> = listOf(
    listOf(
        act(GLFW_KEY_ESCAPE, "Esc", 1.4f),
        act(GLFW_KEY_F1, "F1"), act(GLFW_KEY_F2, "F2"), act(GLFW_KEY_F3, "F3"),
        act(GLFW_KEY_F4, "F4"), act(GLFW_KEY_F5, "F5"), act(GLFW_KEY_F6, "F6"),
        act(GLFW_KEY_F7, "F7"), act(GLFW_KEY_F8, "F8"), act(GLFW_KEY_F9, "F9"),
        act(GLFW_KEY_F10, "F10"), act(GLFW_KEY_F11, "F11"), act(GLFW_KEY_F12, "F12"),
        act(GLFW_KEY_DELETE, "Del", 1.6f)
    ),
    listOf(
        pair(GLFW_KEY_GRAVE_ACCENT, "`", '`', "~", '~'),
        pair(GLFW_KEY_1, "1", '1', "!", '!'), pair(GLFW_KEY_2, "2", '2', "@", '@'),
        pair(GLFW_KEY_3, "3", '3', "#", '#'), pair(GLFW_KEY_4, "4", '4', "\$", '$'),
        pair(GLFW_KEY_5, "5", '5', "%", '%'), pair(GLFW_KEY_6, "6", '6', "^", '^'),
        pair(GLFW_KEY_7, "7", '7', "&", '&'), pair(GLFW_KEY_8, "8", '8', "*", '*'),
        pair(GLFW_KEY_9, "9", '9', "(", '('), pair(GLFW_KEY_0, "0", '0', ")", ')'),
        pair(GLFW_KEY_MINUS, "-", '-', "_", '_'),
        pair(GLFW_KEY_EQUAL, "=", '=', "+", '+'),
        act(GLFW_KEY_BACKSPACE, "Bksp", 2f)
    ),
    listOf(
        act(GLFW_KEY_TAB, "Tab", 1.5f),
        letter(GLFW_KEY_Q, "Q", 'q'), letter(GLFW_KEY_W, "W", 'w'),
        letter(GLFW_KEY_E, "E", 'e'), letter(GLFW_KEY_R, "R", 'r'),
        letter(GLFW_KEY_T, "T", 't'), letter(GLFW_KEY_Y, "Y", 'y'),
        letter(GLFW_KEY_U, "U", 'u'), letter(GLFW_KEY_I, "I", 'i'),
        letter(GLFW_KEY_O, "O", 'o'), letter(GLFW_KEY_P, "P", 'p'),
        pair(GLFW_KEY_LEFT_BRACKET, "[", '[', "{", '{'),
        pair(GLFW_KEY_RIGHT_BRACKET, "]", ']', "}", '}'),
        pair(GLFW_KEY_BACKSLASH, "\\", '\\', "|", '|', 1.5f)
    ),
    listOf(
        mod(GLFW_KEY_CAPS_LOCK, "Caps", 1.9f),
        letter(GLFW_KEY_A, "A", 'a'), letter(GLFW_KEY_S, "S", 's'),
        letter(GLFW_KEY_D, "D", 'd'), letter(GLFW_KEY_F, "F", 'f'),
        letter(GLFW_KEY_G, "G", 'g'), letter(GLFW_KEY_H, "H", 'h'),
        letter(GLFW_KEY_J, "J", 'j'), letter(GLFW_KEY_K, "K", 'k'),
        letter(GLFW_KEY_L, "L", 'l'),
        pair(GLFW_KEY_SEMICOLON, ";", ';', ":", ':'),
        pair(GLFW_KEY_APOSTROPHE, "'", '\'', "\"", '"'),
        act(GLFW_KEY_ENTER, "Enter", 2.1f)
    ),
    listOf(
        mod(GLFW_KEY_LEFT_SHIFT, "Shift", 2.5f),
        letter(GLFW_KEY_Z, "Z", 'z'), letter(GLFW_KEY_X, "X", 'x'),
        letter(GLFW_KEY_C, "C", 'c'), letter(GLFW_KEY_V, "V", 'v'),
        letter(GLFW_KEY_B, "B", 'b'), letter(GLFW_KEY_N, "N", 'n'),
        letter(GLFW_KEY_M, "M", 'm'),
        pair(GLFW_KEY_COMMA, ",", ',', "<", '<'),
        pair(GLFW_KEY_PERIOD, ".", '.', ">", '>'),
        pair(GLFW_KEY_SLASH, "/", '/', "?", '?'),
        act(GLFW_KEY_UP, "↑"),
        mod(GLFW_KEY_RIGHT_SHIFT, "Shift", 1.5f)
    )
)

/**
 * The numpad, the navigation cluster and the right-hand modifiers — everything the letter board
 * has no room for. Between the two, every key the old keycode list offered is here, plus Delete,
 * Print Screen and Scroll Lock, which Android has no keycode for and that list therefore could not
 * reach at all.
 */
private val NUMERIC_ROWS: List<List<Key>> = listOf(
    listOf(
        act(GLFW_KEY_INSERT, "Ins", 2f), act(GLFW_KEY_HOME, "Home", 2f),
        act(GLFW_KEY_PAGE_UP, "PgUp", 2f), gap(1f),
        mod(GLFW_KEY_NUM_LOCK, "Num", 2f),
        digit(GLFW_KEY_KP_DIVIDE, "/", '/', 2f),
        digit(GLFW_KEY_KP_MULTIPLY, "*", '*', 2f),
        digit(GLFW_KEY_KP_SUBTRACT, "−", '-', 2f)
    ),
    listOf(
        act(GLFW_KEY_DELETE, "Del", 2f), act(GLFW_KEY_END, "End", 2f),
        act(GLFW_KEY_PAGE_DOWN, "PgDn", 2f), gap(1f),
        digit(GLFW_KEY_KP_7, "7", '7', 2f), digit(GLFW_KEY_KP_8, "8", '8', 2f),
        digit(GLFW_KEY_KP_9, "9", '9', 2f), digit(GLFW_KEY_KP_ADD, "+", '+', 2f)
    ),
    listOf(
        act(GLFW_KEY_PAUSE, "Pause", 2f), act(GLFW_KEY_PRINT_SCREEN, "PrtSc", 2f),
        act(GLFW_KEY_SCROLL_LOCK, "ScrLk", 2f), gap(1f),
        digit(GLFW_KEY_KP_4, "4", '4', 2f), digit(GLFW_KEY_KP_5, "5", '5', 2f),
        digit(GLFW_KEY_KP_6, "6", '6', 2f), digit(GLFW_KEY_KP_DECIMAL, ".", '.', 2f)
    ),
    listOf(
        mod(GLFW_KEY_RIGHT_CONTROL, "Ctrl R", 3f), mod(GLFW_KEY_RIGHT_ALT, "Alt R", 3f), gap(1f),
        digit(GLFW_KEY_KP_1, "1", '1', 2f), digit(GLFW_KEY_KP_2, "2", '2', 2f),
        digit(GLFW_KEY_KP_3, "3", '3', 2f), act(GLFW_KEY_KP_ENTER, "Enter", 2f)
    ),
    listOf(
        gap(7f), digit(GLFW_KEY_KP_0, "0", '0', 8f)
    )
)

/**
 * The row that never changes.
 *
 * The modifiers, the space bar, the board switch and the arrows stay put between the two boards,
 * so the keys reached most often are never somewhere else than where the thumb last left them.
 * Only the switch itself changes, and it changes to say where it goes.
 */
private fun bottomRowWith(switchLabel: String): List<Key> = listOf(
    mod(GLFW_KEY_LEFT_CONTROL, "Ctrl", 2f),
    mod(GLFW_KEY_LEFT_ALT, "Alt", 2f),
    Key(GLFW_KEY_UNKNOWN, switchLabel, weight = 2f, kind = KeyKind.LAYOUT),
    // Named even though the cap draws a bar instead of its legend: held, it still needs a chip.
    Key(GLFW_KEY_SPACE, "Space", char = ' ', shiftChar = ' ', weight = 6f),
    act(GLFW_KEY_LEFT, "←"), act(GLFW_KEY_DOWN, "↓"), act(GLFW_KEY_RIGHT, "→")
)

private val BOTTOM_ROW: List<Key> = bottomRowWith("123")
private val BOTTOM_ROW_NUMERIC: List<Key> = bottomRowWith("ABC")

/**
 * The bottom row as it stands right now.
 *
 * Both versions are built once at class-load rather than derived per frame: this is read on every
 * recomposition, and a keyboard recomposes on every keystroke.
 */
fun bottomRow(numeric: Boolean): List<Key> = if (numeric) BOTTOM_ROW_NUMERIC else BOTTOM_ROW
