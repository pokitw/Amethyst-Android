package net.kdt.pojavlaunch.customcontrols;

/**
 * The order a button's keys have to be sent in for a combination to mean anything.
 *
 * <b>A button holding Shift and F3 was sending them in the order the slots happened to be filled
 * in.</b> Bind F3 first and Shift second, which is the order anybody describing the button would
 * use, and F3 goes out while nothing is holding Shift: Minecraft opens the debug overlay and never
 * the profiler chart. The same button with the slots the other way round worked, which is what made
 * it look like an intermittent fault rather than an ordering one.
 *
 * <p>A real keyboard cannot express the broken order. Shift is held <i>first</i> and the other key
 * is struck underneath it, and that is not a convention, it is the only sequence a hand can perform:
 * the modifier is a state the second key is pressed inside of. So a combination has a canonical
 * order, and the launcher should produce it rather than replaying whatever order somebody clicked
 * the editor's slots in.
 *
 * <p><b>Two readers of the modifier need this, not one.</b> The key event carries a {@code mods}
 * bitfield, which is what {@code CallbackBridge.getCurrentMods} fills in; and Minecraft also
 * <i>polls</i> the key state through {@code glfwGetKey} for things like
 * {@code Screen.hasShiftDown()}. Sending the modifier first is what makes both true at the moment
 * the second key lands. Fixing only the bitfield would leave the polled half wrong, and vice versa.
 *
 * <p>Plain Java with no Android types, so {@code scripts/combosim} compiles this exact file and
 * drives it. The failure it guards against is silent in the worst way: every key is delivered, in
 * an order that is perfectly plausible, and the only symptom is that a combination quietly does the
 * wrong half of what it says.
 */
public final class KeyCombo {

    private KeyCombo() {}

    /**
     * The value an unbound slot holds.
     *
     * Shared with {@code LwjglGlfwKeycode.GLFW_KEY_UNKNOWN}, which in this codebase is 0 rather
     * than GLFW's own -1, and with the editor's {@code NO_KEY}. Restated here rather than imported
     * because this file deliberately depends on nothing.
     */
    public static final int NO_KEY = 0;

    /**
     * The keys the rest of the input path treats as held state rather than as a press.
     *
     * <b>Exactly the set {@code CallbackBridge.setModifiers} writes a flag for</b>, and that
     * identity is load-bearing rather than tidy. A key ordered first here that no flag follows
     * would be a claim nothing downstream can honour; a flag written for a key not ordered here
     * would be set after the key it was meant to modify had already gone. The two lists are
     * compared by {@code scripts/combosim} so they cannot drift apart in silence.
     *
     * Super is in neither list. There is no {@code holdingSuper} and {@code getCurrentMods} cannot
     * express {@code GLFW_MOD_SUPER}, so nothing could observe it either way, and inventing half
     * of a modifier is worse than leaving it as an ordinary key.
     */
    public static boolean isModifier(int keycode) {
        switch (keycode) {
            case 340: // GLFW_KEY_LEFT_SHIFT
            case 344: // GLFW_KEY_RIGHT_SHIFT
            case 341: // GLFW_KEY_LEFT_CONTROL
            case 345: // GLFW_KEY_RIGHT_CONTROL
            case 342: // GLFW_KEY_LEFT_ALT
            case 346: // GLFW_KEY_RIGHT_ALT
            case 280: // GLFW_KEY_CAPS_LOCK
            case 282: // GLFW_KEY_NUM_LOCK
                return true;
            default:
                return false;
        }
    }

    /**
     * The keys of one button, in the order they should be sent for this edge.
     *
     * Pressing puts every modifier first, so the keys struck underneath them are struck while they
     * are genuinely held. Releasing runs the whole thing backwards, so the struck keys come up
     * while the modifiers are still down: that is what a hand does, and it also means the release
     * events carry the same modifier bitfield the matching press events did, rather than a key
     * going up "unmodified" and a game concluding the combination ended earlier than it did.
     *
     * <p><b>Order within each group is preserved.</b> Two modifiers, or two ordinary keys, stay in
     * the order the author put them in: nothing here knows better than they do about which of Ctrl
     * and Shift should lead, and a stable partition is the smallest change that fixes the bug.
     *
     * <p>Unbound slots are dropped. They were previously sent as keycode 0, which the native send
     * happens to discard, but which still ran the modifier bookkeeping and the character path for a
     * key nobody pressed.
     *
     * @param keycodes the button's slots, in author order, empties included
     * @param isDown   true for the press edge, false for the release
     * @return a fresh array; never null, possibly empty
     */
    public static int[] order(int[] keycodes, boolean isDown) {
        if (keycodes == null) return new int[0];

        int bound = 0;
        for (int keycode : keycodes) {
            if (keycode != NO_KEY) bound++;
        }
        int[] ordered = new int[bound];

        int at = 0;
        for (int keycode : keycodes) {
            if (keycode != NO_KEY && isModifier(keycode)) ordered[at++] = keycode;
        }
        for (int keycode : keycodes) {
            if (keycode != NO_KEY && !isModifier(keycode)) ordered[at++] = keycode;
        }

        if (isDown) return ordered;

        // Releasing is the press run backwards rather than a second arrangement, which is what
        // guarantees the two can never disagree about grouping: whatever went down first comes up
        // last, for any input at all.
        int[] reversed = new int[ordered.length];
        for (int i = 0; i < ordered.length; i++) {
            reversed[i] = ordered[ordered.length - 1 - i];
        }
        return reversed;
    }

    /** Whether this button sends a modifier alongside something else, which is the case that broke. */
    public static boolean isCombination(int[] keycodes) {
        if (keycodes == null) return false;
        boolean modifier = false;
        boolean other = false;
        for (int keycode : keycodes) {
            if (keycode == NO_KEY) continue;
            if (isModifier(keycode)) modifier = true;
            else other = true;
        }
        return modifier && other;
    }
}
