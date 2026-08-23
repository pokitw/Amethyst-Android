package net.kdt.pojavlaunch.customcontrols;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Drive the shipped {@link KeyCombo}.
 *
 * The bug this exists for delivered every key, in a plausible order, and silently did the wrong
 * half of what the button said. Nothing crashed and nothing was dropped, so the only assertions
 * worth writing are about the order itself.
 */
public class Harness {

    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<>();

    private static final int SHIFT_L = 340, CTRL_L = 341, ALT_L = 342, SUPER_L = 343;
    private static final int SHIFT_R = 344, CTRL_R = 345, ALT_R = 346, SUPER_R = 347;
    private static final int F3 = 292, A = 65, B = 66, CAPS = 280, NUM = 282;
    private static final int NONE = KeyCombo.NO_KEY;

    public static void main(String[] args) {
        theReportedBug();
        modifiersLeadWhicheverSlotTheyAreIn();
        releaseIsThePressRunBackwards();
        bothHandsCount();
        locksAreNotModifiers();
        emptySlotsAreDropped();
        nothingIsLostOrInvented();
        orderWithinAGroupIsTheAuthors();
        edgeCases();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String failure : FAILURES) System.out.println("FAIL: " + failure);
            System.out.println(FAILURES.size() + " FAILURES");
            System.exit(1);
        }
        System.out.println("all passed");
    }

    /**
     * The exact button that was reported: Shift and F3, bound in both possible orders.
     *
     * Before the fix, one of these two worked and the other opened the debug overlay with no
     * modifier held. Which one you got depended on nothing but the order the editor's slots
     * happened to be filled in.
     */
    private static void theReportedBug() {
        press("F3 bound first, Shift second", new int[]{F3, SHIFT_L, NONE, NONE},
                new int[]{SHIFT_L, F3});
        press("Shift bound first, F3 second", new int[]{SHIFT_L, F3, NONE, NONE},
                new int[]{SHIFT_L, F3});

        // The property that actually matters, stated on its own: whatever the author did, the
        // modifier is not sent after the key it modifies.
        for (int[] slots : new int[][]{{F3, SHIFT_L, NONE, NONE}, {SHIFT_L, F3, NONE, NONE}}) {
            int[] down = KeyCombo.order(slots, true);
            check(KeyCombo.isModifier(down[0]),
                    "the press did not lead with the modifier: " + Arrays.toString(down));
            check(!KeyCombo.isModifier(down[down.length - 1]),
                    "the press ended on a modifier: " + Arrays.toString(down));
        }
    }

    /** Every arrangement of one modifier among four slots leads with it. */
    private static void modifiersLeadWhicheverSlotTheyAreIn() {
        for (int slot = 0; slot < 4; slot++) {
            int[] slots = {NONE, NONE, NONE, NONE};
            slots[slot] = CTRL_L;
            slots[(slot + 1) % 4] = A;
            int[] down = KeyCombo.order(slots, true);
            check(down.length == 2 && down[0] == CTRL_L && down[1] == A,
                    "a modifier in slot " + slot + " produced " + Arrays.toString(down));
        }
    }

    /**
     * Releasing runs the press backwards, so the struck key comes up while the modifier is still
     * held. A modifier released first would make the other key's release carry no modifier, which
     * is a combination that appears to end before it did.
     */
    private static void releaseIsThePressRunBackwards() {
        int[][] cases = {
            {SHIFT_L, F3, NONE, NONE},
            {F3, SHIFT_L, NONE, NONE},
            {CTRL_L, SHIFT_L, A, B},
            {A, B, NONE, NONE},
            {SHIFT_L, NONE, NONE, NONE},
        };
        for (int[] slots : cases) {
            int[] down = KeyCombo.order(slots, true);
            int[] up = KeyCombo.order(slots, false);
            check(down.length == up.length, "the two edges disagree on how many keys there are");
            for (int i = 0; i < down.length; i++) {
                check(down[i] == up[up.length - 1 - i],
                        "release is not the reverse of press for " + Arrays.toString(slots)
                                + ": down " + Arrays.toString(down) + " up " + Arrays.toString(up));
            }
        }

        // And said directly, because the reverse property above would also hold if both were wrong
        // in the same way.
        int[] up = KeyCombo.order(new int[]{F3, SHIFT_L, NONE, NONE}, false);
        check(up[0] == F3 && up[1] == SHIFT_L,
                "the struck key did not come up before the modifier: " + Arrays.toString(up));
    }

    /**
     * Right Shift is a modifier too. The editor calls both of them "Shift", so a layout using the
     * right hand variant looked identical and behaved differently.
     */
    private static void bothHandsCount() {
        int[][] pairs = {{SHIFT_L, SHIFT_R}, {CTRL_L, CTRL_R}, {ALT_L, ALT_R}};
        for (int[] pair : pairs) {
            for (int keycode : pair) {
                check(KeyCombo.isModifier(keycode), keycode + " was not treated as a modifier");
                int[] down = KeyCombo.order(new int[]{F3, keycode, NONE, NONE}, true);
                check(down[0] == keycode,
                        "the right hand modifier " + keycode + " did not lead: "
                                + Arrays.toString(down));
            }
        }
    }

    /**
     * Caps Lock and Num Lock are toggles rather than things held down, so reordering a button
     * around one would change what that button does rather than fix it.
     */
    private static void locksAreNotModifiers() {
        // They ARE, because setModifiers writes a flag for both and the two lists have to be the
        // same list. A button binding Caps Lock and a letter now sends the lock first, which is
        // also what a hand does.
        check(KeyCombo.isModifier(CAPS), "caps lock did not match setModifiers");
        check(KeyCombo.isModifier(NUM), "num lock did not match setModifiers");
        int[] down = KeyCombo.order(new int[]{A, CAPS, NONE, NONE}, true);
        check(down[0] == CAPS && down[1] == A,
                "a lock was not ordered ahead of the key it modifies: " + Arrays.toString(down));

        // Super is in neither list: there is no flag for it, so ordering it would promise
        // something nothing downstream can deliver.
        check(!KeyCombo.isModifier(SUPER_L), "left super claimed a flag that does not exist");
        check(!KeyCombo.isModifier(SUPER_R), "right super claimed a flag that does not exist");
    }

    /** An unbound slot is not a key and must not be sent as one. */
    private static void emptySlotsAreDropped() {
        check(KeyCombo.order(new int[]{NONE, NONE, NONE, NONE}, true).length == 0,
                "an empty button produced keys");
        check(KeyCombo.order(new int[]{A, NONE, NONE, NONE}, true).length == 1,
                "empty slots were sent alongside a real key");
        check(KeyCombo.order(new int[]{NONE, A, NONE, B}, true).length == 2,
                "holes between bound keys were not closed");
        for (boolean isDown : new boolean[]{true, false}) {
            for (int keycode : KeyCombo.order(new int[]{NONE, SHIFT_L, NONE, F3}, isDown)) {
                check(keycode != NONE, "an unbound slot reached the output");
            }
        }
    }

    /**
     * The ordering is a permutation. Anything else would be a fix that quietly changed which keys a
     * layout sends, which is worse than the bug.
     */
    private static void nothingIsLostOrInvented() {
        int[][] cases = {
            {SHIFT_L, F3, NONE, NONE}, {F3, SHIFT_L, CTRL_L, A}, {A, B, CAPS, SHIFT_R},
            {SUPER_R, ALT_L, NONE, B}, {A, NONE, NONE, NONE}, {NONE, NONE, NONE, NONE},
            {CAPS, A, NONE, NONE},
        };
        for (int[] slots : cases) {
            for (boolean isDown : new boolean[]{true, false}) {
                int[] out = KeyCombo.order(slots, isDown);
                int[] expected = bound(slots);
                int[] got = out.clone();
                Arrays.sort(expected);
                Arrays.sort(got);
                check(Arrays.equals(expected, got),
                        "the ordering changed the multiset of keys for "
                                + Arrays.toString(slots) + ": " + Arrays.toString(out));
            }
        }
        // A key bound twice stays bound twice, since that is what the author wrote.
        int[] twice = KeyCombo.order(new int[]{A, A, SHIFT_L, NONE}, true);
        check(twice.length == 3, "a duplicate key was silently dropped: " + Arrays.toString(twice));
    }

    /** Two modifiers, or two ordinary keys, keep the order the author chose. */
    private static void orderWithinAGroupIsTheAuthors() {
        int[] down = KeyCombo.order(new int[]{CTRL_L, SHIFT_L, B, A}, true);
        check(down[0] == CTRL_L && down[1] == SHIFT_L,
                "two modifiers were reordered against each other: " + Arrays.toString(down));
        check(down[2] == B && down[3] == A,
                "two ordinary keys were reordered against each other: " + Arrays.toString(down));
    }

    private static void edgeCases() {
        check(KeyCombo.order(null, true).length == 0, "a null array threw or produced keys");
        check(KeyCombo.order(new int[0], false).length == 0, "an empty array produced keys");

        // Specials are negative and are not modifiers, so they keep their place among the struck
        // keys rather than being hoisted or dropped.
        int[] down = KeyCombo.order(new int[]{-12, SHIFT_L, NONE, NONE}, true);
        check(down[0] == SHIFT_L && down[1] == -12,
                "a special keycode was mishandled: " + Arrays.toString(down));
        check(!KeyCombo.isModifier(-12), "a special keycode was treated as a modifier");

        check(KeyCombo.isCombination(new int[]{SHIFT_L, F3, NONE, NONE}),
                "shift plus a key was not recognised as a combination");
        check(!KeyCombo.isCombination(new int[]{SHIFT_L, NONE, NONE, NONE}),
                "a lone modifier was called a combination");
        check(!KeyCombo.isCombination(new int[]{A, B, NONE, NONE}),
                "two ordinary keys were called a combination");
        check(!KeyCombo.isCombination(null), "null was called a combination");
    }

    /* ------------------------------------------------------------------ plumbing */

    private static int[] bound(int[] slots) {
        int count = 0;
        for (int keycode : slots) if (keycode != NONE) count++;
        int[] out = new int[count];
        int at = 0;
        for (int keycode : slots) if (keycode != NONE) out[at++] = keycode;
        return out;
    }

    private static void press(String what, int[] slots, int[] expected) {
        int[] actual = KeyCombo.order(slots, true);
        check(Arrays.equals(expected, actual),
                what + ": sent " + Arrays.toString(actual) + " not " + Arrays.toString(expected));
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
