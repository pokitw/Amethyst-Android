package net.kdt.pojavlaunch.customcontrols;

import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_A;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_D;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_E;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_ESCAPE;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F1;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F3;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_F5;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_Q;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_S;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_SPACE;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_T;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_TAB;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_UNKNOWN;
import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_W;

import net.kdt.pojavlaunch.R;

/**
 * The icon a control button shows instead of its name.
 *
 * Matched on the key the button sends, not on what it is called, so every layout ever shared picks
 * its icons up without being edited and without the format gaining a field. A button bound to two
 * keys at once deliberately keeps its name: no single icon can honestly stand for "sneak and
 * jump", and a wrong icon is worse than a word.
 *
 * Only the actions a player would recognise at a glance are here. Anything else falls back to its
 * label, which is why this is a short list rather than a map of the whole keyboard.
 */
public final class ControlGlyphs {

    private ControlGlyphs() {}

    /**
     * @param data the button's properties
     * @return a drawable resource, or 0 when the button should keep showing its name
     */
    public static int glyphFor(ControlData data) {
        return glyphForKeycode(soleKeycode(data));
    }

    /**
     * The icon for one key or action on its own.
     *
     * Split out from {@link #glyphFor} so the control editor can show the same icon beside a key
     * while it is being chosen as the button will wear once it is. One table, two readers.
     *
     * @param keycode a GLFW key, or one of {@link ControlData}'s negative action constants
     * @return a drawable resource, or 0 where there is no icon for it
     */
    public static int glyphForKeycode(int keycode) {
        switch (keycode) {
            case GLFW_KEY_W: return R.drawable.ic_ctrl_up;
            case GLFW_KEY_S: return R.drawable.ic_ctrl_down;
            case GLFW_KEY_A: return R.drawable.ic_ctrl_left;
            case GLFW_KEY_D: return R.drawable.ic_ctrl_right;
            case GLFW_KEY_SPACE: return R.drawable.ic_ctrl_jump;
            case GLFW_KEY_LEFT_SHIFT: return R.drawable.ic_ctrl_sneak;
            case GLFW_KEY_LEFT_CONTROL: return R.drawable.ic_ctrl_sprint;
            case GLFW_KEY_E: return R.drawable.ic_ctrl_inventory;
            case GLFW_KEY_T: return R.drawable.ic_ctrl_chat;
            case GLFW_KEY_Q: return R.drawable.ic_ctrl_drop;
            case GLFW_KEY_F: return R.drawable.ic_ctrl_offhand;
            case GLFW_KEY_TAB: return R.drawable.ic_ctrl_list;
            case GLFW_KEY_ESCAPE: return R.drawable.ic_ctrl_pause;
            case GLFW_KEY_F1: return R.drawable.ic_ctrl_eye;
            case GLFW_KEY_F3: return R.drawable.ic_ctrl_debug;
            case GLFW_KEY_F5: return R.drawable.ic_ctrl_perspective;

            case ControlData.SPECIALBTN_MOUSEPRI: return R.drawable.ic_ctrl_attack;
            case ControlData.SPECIALBTN_MOUSESEC: return R.drawable.ic_ctrl_use;
            case ControlData.SPECIALBTN_KEYBOARD: return R.drawable.ic_ctrl_keyboard;
            case ControlData.SPECIALBTN_VIRTUALMOUSE: return R.drawable.ic_ctrl_cursor;
            case ControlData.SPECIALBTN_TOGGLECTRL: return R.drawable.ic_ctrl_eye;
            case ControlData.SPECIALBTN_MENU: return R.drawable.ic_ctrl_menu;
            case ControlData.SPECIALBTN_SCROLLUP: return R.drawable.ic_ctrl_up;
            case ControlData.SPECIALBTN_SCROLLDOWN: return R.drawable.ic_ctrl_down;
            case ControlData.SPECIALBTN_GAMEKEYBOARD: return R.drawable.ic_ctrl_board;
            case ControlData.SPECIALBTN_VOICE: return R.drawable.ic_ctrl_mic;
            case ControlData.SPECIALBTN_SCREENSHOT: return R.drawable.ic_ctrl_camera;

            default: return 0;
        }
    }

    /**
     * The one key this button sends, or {@link net.kdt.pojavlaunch.LwjglGlfwKeycode#GLFW_KEY_UNKNOWN}
     * when it sends none or several. Keycode arrays are always padded out to four with the unknown
     * key, so the padding has to be skipped rather than counted.
     */
    private static int soleKeycode(ControlData data) {
        int found = GLFW_KEY_UNKNOWN;
        for (int keycode : data.keycodes) {
            if (keycode == GLFW_KEY_UNKNOWN) continue;
            if (found != GLFW_KEY_UNKNOWN) return GLFW_KEY_UNKNOWN;
            found = keycode;
        }
        return found;
    }
}
