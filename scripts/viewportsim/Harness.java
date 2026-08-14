package net.kdt.pojavlaunch.customcontrols;

/**
 * Drive the shipped {@link GameViewport}.
 *
 * The class under test is the real one, compiled from the app's own source. Getting an inset
 * wrong is not a crash: it is a game drawn a few pixels off the panel, or a control layout that
 * no longer lines up with the picture it belongs to, and there is no device in CI to see either.
 */
public class Harness {

    private static int failures = 0;
    private static int checks = 0;

    /** Real panels, in the landscape orientation the game runs in. */
    private static final int[][] SCREENS = {
            {2400, 1080, 0},   // a common 1080p phone
            {3168, 1440, 0},   // OnePlus 12
            {2340, 1080, 0},
            {1920, 1200, 0},   // a tablet
            {1280, 720, 0},
            {2401, 1081, 0},   // deliberately odd, so rounding has somewhere to go wrong
    };

    public static void main(String[] args) {
        fullScreenIsUntouched();
        staysOnThePanel();
        anchorsLandWhereTheySay();
        aspectRatioIsKept();
        clampsAndFallbacks();
        theWholeRangeIsUsable();

        if (failures > 0) {
            System.out.println(failures + " of " + checks + " checks failed");
            System.exit(1);
        }
        System.out.println("ok: " + checks + " checks against the shipped GameViewport");
    }

    /**
     * The setting off must be byte-for-byte the layout every existing player already has.
     *
     * This is the check that matters most: everybody who never opens this setting is relying on
     * it, and "100% is subtly not full screen" would be a bug shipped to every single user.
     */
    private static void fullScreenIsUntouched() {
        for (int[] screen : SCREENS) {
            for (int position = 0; position < GameViewport.POSITION_COUNT; position++) {
                int[] box = GameViewport.bounds(screen[0], screen[1], 100, position);
                check("100% width on " + screen[0] + "x" + screen[1], box[0], screen[0]);
                check("100% height on " + screen[0] + "x" + screen[1], box[1], screen[1]);
                check("100% left on " + screen[0] + "x" + screen[1], box[2], 0);
                check("100% top on " + screen[0] + "x" + screen[1], box[3], 0);
            }
            check("isFullScreen agrees", GameViewport.isFullScreen(100, 4), true);
            check("isFullScreen agrees when smaller", GameViewport.isFullScreen(80, 4), false);
        }
    }

    /** Nothing may ever hang off the edge of the panel, at any size, in any corner. */
    private static void staysOnThePanel() {
        for (int[] screen : SCREENS) {
            for (int percent = GameViewport.MIN_PERCENT; percent <= GameViewport.MAX_PERCENT; percent++) {
                for (int position = 0; position < GameViewport.POSITION_COUNT; position++) {
                    int[] box = GameViewport.bounds(screen[0], screen[1], percent, position);
                    String where = screen[0] + "x" + screen[1] + " at " + percent + "% pos " + position;
                    if (box[2] < 0 || box[3] < 0) {
                        fail("negative offset on " + where);
                    }
                    if (box[2] + box[0] > screen[0]) {
                        fail("overhangs the right on " + where
                                + " (" + box[2] + "+" + box[0] + " > " + screen[0] + ")");
                    }
                    if (box[3] + box[1] > screen[1]) {
                        fail("overhangs the bottom on " + where
                                + " (" + box[3] + "+" + box[1] + " > " + screen[1] + ")");
                    }
                    if (box[0] <= 0 || box[1] <= 0) {
                        fail("empty box on " + where);
                    }
                }
            }
        }
    }

    /**
     * Each anchor has to actually pull the game that way.
     *
     * The nine positions are computed arithmetically rather than by a branch per corner, which is
     * what makes it worth checking that the arithmetic really does put the top row at the top and
     * the right column against the right edge.
     */
    private static void anchorsLandWhereTheySay() {
        int width = 2400, height = 1080, percent = 80;
        int[] expectedWidth = GameViewport.bounds(width, height, percent, 4);
        int slackX = width - expectedWidth[0];
        int slackY = height - expectedWidth[1];

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int[] box = GameViewport.bounds(width, height, percent, row * 3 + column);
                int wantLeft = slackX * column / 2;
                int wantTop = slackY * row / 2;
                check("left of row " + row + " column " + column, box[2], wantLeft);
                check("top of row " + row + " column " + column, box[3], wantTop);
            }
        }
        // Spelt out for the three that matter to a player: the top row is flush with the top,
        // the bottom row with the bottom, and the middle is genuinely in the middle.
        check("top row is flush", GameViewport.bounds(width, height, percent, 1)[3], 0);
        int[] bottom = GameViewport.bounds(width, height, percent, 7);
        check("bottom row is flush", bottom[3] + bottom[1], height);
        int[] centre = GameViewport.bounds(width, height, percent, 4);
        check("centre is even on x", centre[2] * 2 + centre[0] <= width + 1, true);
        check("centre is even on y", centre[3] * 2 + centre[1] <= height + 1, true);
    }

    /**
     * The box keeps the panel's shape, so the game is never stretched.
     *
     * The surface takes its framebuffer from these bounds, so a box whose aspect ratio drifted
     * from the panel's would not letterbox, it would render the world at the wrong shape.
     */
    private static void aspectRatioIsKept() {
        for (int[] screen : SCREENS) {
            double panel = (double) screen[0] / screen[1];
            for (int percent = GameViewport.MIN_PERCENT; percent <= GameViewport.MAX_PERCENT; percent += 5) {
                int[] box = GameViewport.bounds(screen[0], screen[1], percent, 4);
                double actual = (double) box[0] / box[1];
                // Within a percent: both sides are rounded to whole pixels, so exact equality is
                // not on offer, but a real distortion would be far larger than this.
                if (Math.abs(actual - panel) / panel > 0.01) {
                    fail("aspect drifted on " + screen[0] + "x" + screen[1] + " at " + percent
                            + "%: " + actual + " vs " + panel);
                }
            }
        }
    }

    /** Values a preference file could hold after an edit, a downgrade, or a bad write. */
    private static void clampsAndFallbacks() {
        int[] tooSmall = GameViewport.bounds(2400, 1080, 5, 4);
        int[] floor = GameViewport.bounds(2400, 1080, GameViewport.MIN_PERCENT, 4);
        check("below the floor clamps up", tooSmall[0], floor[0]);

        int[] tooBig = GameViewport.bounds(2400, 1080, 500, 4);
        check("above the ceiling clamps down", tooBig[0], 2400);

        int[] negative = GameViewport.bounds(2400, 1080, -20, 4);
        check("a negative percent clamps up", negative[0], floor[0]);

        int[] centre = GameViewport.bounds(2400, 1080, 80, 4);
        check("an unknown position falls back to the centre",
                GameViewport.bounds(2400, 1080, 80, 99)[2], centre[2]);
        check("a negative position falls back to the centre",
                GameViewport.bounds(2400, 1080, 80, -3)[3], centre[3]);

        // A zero-sized screen is what a layout pass before measurement looks like. It must
        // answer rather than divide by zero or hand back something negative.
        int[] empty = GameViewport.bounds(0, 0, 80, 0);
        check("a zero screen gives a zero box", empty[0], 0);
        check("a zero screen gives no offset", empty[2], 0);
        int[] negativeScreen = GameViewport.bounds(-100, -100, 80, 0);
        check("a negative screen gives no width", negativeScreen[0], 0);
        check("a negative screen gives no offset", negativeScreen[2], 0);
    }

    /**
     * Every step the slider offers has to be a step the player can see.
     *
     * A range whose ends produce the same box would be a control that looks adjustable and is
     * not, which is the same rule the settings screens follow about showing a slider that does
     * nothing.
     */
    private static void theWholeRangeIsUsable() {
        int previous = -1;
        for (int percent = GameViewport.MIN_PERCENT; percent <= GameViewport.MAX_PERCENT; percent += 5) {
            int[] box = GameViewport.bounds(2400, 1080, percent, 1);
            if (box[0] <= previous) {
                fail("width did not grow from the step below at " + percent + "%");
            }
            previous = box[0];
        }
        // The floor has to be a real reduction rather than a rounding away from full screen.
        int[] smallest = GameViewport.bounds(2400, 1080, GameViewport.MIN_PERCENT, 4);
        check("the floor is about half the panel", smallest[0], 1200);
        check("the floor halves the height too", smallest[1], 540);

        // Pins rounding rather than truncation on a size where the two disagree: 2401 at 85% is
        // 2040.85, so rounding gives 2041 and truncating gives 2040. Both produce exactly the
        // panel at 100%, so this is the only shape of check that can tell them apart.
        check("a fraction rounds to the nearest pixel",
                GameViewport.bounds(2401, 1081, 85, 4)[0], 2041);
        check("and rounds down when that is nearer",
                GameViewport.bounds(2401, 1081, 65, 4)[0], 1561);
    }

    private static void check(String name, Object got, Object want) {
        checks++;
        if (got == null ? want != null : !got.equals(want)) {
            failures++;
            System.out.println("FAIL: " + name + ": got " + got + ", wanted " + want);
        }
    }

    private static void fail(String message) {
        checks++;
        failures++;
        System.out.println("FAIL: " + message);
    }
}
