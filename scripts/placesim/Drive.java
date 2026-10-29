package net.kdt.pojavlaunch.customcontrols;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the shipped {@link ControlPlacement} against the screens a phone actually is.
 *
 * The failure this exists for is not a wrong pixel. It is a control placed somewhere the player
 * cannot grab it back from, which in the editor is permanent: a control off the edge draws
 * nothing and can never be selected again, the same loss the resize grip's floor guards against.
 * So the assertions are about containment and freedom rather than about particular coordinates.
 */
public final class Drive {
    private static int sChecks;
    private static final List<String> FAILURES = new ArrayList<>();

    /** Landscape, because the editor and the game both are. */
    private static final float[][] SCREENS = {
            {640f, 360f}, {720f, 360f}, {800f, 360f}, {891f, 411f}, {1280f, 800f}, {320f, 240f}
    };

    public static void main(String[] args) {
        anEmptyScreenGetsTheExactCentre();
        aSecondControlDoesNotLandOnTheFirst();
        everySpotIsOnTheScreen();
        aFullScreenFallsBackToTheCentreRatherThanOffTheEdge();
        aControlBiggerThanTheScreenStillTerminates();
        theSearchFindsAHoleInACrowd();
        touchingEdgesAreNotAnOverlap();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String failure : FAILURES) System.out.println("FAIL " + failure);
            System.out.println("FAILED: " + FAILURES.size());
            System.exit(1);
        }
        System.out.println("ok: placesim");
    }

    /**
     * The one everybody sees.
     *
     * Almost every use of this is one control added to a space with nothing in it, and there the
     * answer has to be the plain centre and nothing cleverer. This is placesim's counterpart to
     * viewportsim asserting that 100% is byte for byte the full screen.
     */
    private static void anEmptyScreenGetsTheExactCentre() {
        for (float[] screen : SCREENS) {
            float[] spot = ControlPlacement.freeSpot(screen[0], screen[1], 50f, 50f, 8f, null);
            check(spot[0] == (screen[0] - 50f) / 2f && spot[1] == (screen[1] - 50f) / 2f,
                    "an empty " + screen[0] + "x" + screen[1] + " screen did not centre: "
                            + spot[0] + "," + spot[1]);
        }
        check(ControlPlacement.freeSpot(640f, 360f, 50f, 50f, 8f, new float[0][])[0] == 295f,
                "an empty array behaved differently from null");
    }

    /** Adding two in a row is the reported friction, so it gets its own check. */
    private static void aSecondControlDoesNotLandOnTheFirst() {
        float[][] taken = new float[1][];
        taken[0] = new float[]{295f, 155f, 50f, 50f};
        float[] spot = ControlPlacement.freeSpot(640f, 360f, 50f, 50f, 8f, taken);
        check(ControlPlacement.isFree(spot[0], spot[1], 50f, 50f, taken),
                "the second control landed on the first at " + spot[0] + "," + spot[1]);
    }

    /**
     * Containment, swept, which is the assertion that actually protects a layout.
     *
     * Every count of existing controls from none to a screenful, at several sizes, on every
     * screen: the answer is always a rectangle wholly inside the area.
     */
    private static void everySpotIsOnTheScreen() {
        float[] sizes = {20f, 46f, 50f, 80f, 120f};
        for (float[] screen : SCREENS) {
            for (float size : sizes) {
                for (int count = 0; count < 40; count++) {
                    float[][] taken = grid(screen[0], screen[1], size, count);
                    float[] spot = ControlPlacement.freeSpot(
                            screen[0], screen[1], size, size, 8f, taken);
                    check(spot[0] >= 0f && spot[1] >= 0f
                                    && spot[0] + size <= screen[0] + 0.001f
                                    && spot[1] + size <= screen[1] + 0.001f,
                            "a " + size + "dp control on " + screen[0] + "x" + screen[1]
                                    + " with " + count + " placed landed at "
                                    + spot[0] + "," + spot[1]);
                }
            }
        }
    }

    /**
     * A screen with no room left stacks rather than wandering.
     *
     * Being on top of something costs one drag. Being off the edge, or at the last position the
     * search happened to try, costs the control.
     */
    private static void aFullScreenFallsBackToTheCentreRatherThanOffTheEdge() {
        // One rectangle covering everything, so nothing can ever be free.
        float[][] taken = {{0f, 0f, 640f, 360f}};
        float[] spot = ControlPlacement.freeSpot(640f, 360f, 50f, 50f, 8f, taken);
        check(spot[0] == 295f && spot[1] == 155f,
                "a full screen did not fall back to the centre: " + spot[0] + "," + spot[1]);
    }

    /** A control wider than the screen has no valid position; it must still answer, at the origin. */
    private static void aControlBiggerThanTheScreenStillTerminates() {
        float[] spot = ControlPlacement.freeSpot(320f, 240f, 400f, 400f, 8f, null);
        check(spot[0] == 0f && spot[1] == 0f,
                "an oversized control was not pinned to the origin: " + spot[0] + "," + spot[1]);
    }

    /**
     * The search has to actually search, not merely step once.
     *
     * A crowd with one gap in it: the gap is on the lattice, so it is reachable, and a placement
     * that gave up after the first ring would stack instead of finding it.
     */
    private static void theSearchFindsAHoleInACrowd() {
        float size = 50f, gap = 8f, step = size + gap;
        float baseX = (640f - size) / 2f, baseY = (360f - size) / 2f;
        List<float[]> taken = new ArrayList<>();
        int holeDx = 2, holeDy = -1;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                if (dx == holeDx && dy == holeDy) continue;
                float x = baseX + dx * step, y = baseY + dy * step;
                if (x < 0f || y < 0f || x + size > 640f || y + size > 360f) continue;
                taken.add(new float[]{x, y, size, size});
            }
        }
        float[][] array = taken.toArray(new float[0][]);
        float[] spot = ControlPlacement.freeSpot(640f, 360f, size, size, gap, array);
        check(ControlPlacement.isFree(spot[0], spot[1], size, size, array),
                "the search stacked instead of finding the hole: " + spot[0] + "," + spot[1]);
        check(Math.abs(spot[0] - (baseX + holeDx * step)) < 0.001f
                        && Math.abs(spot[1] - (baseY + holeDy * step)) < 0.001f,
                "the hole was not the spot chosen: " + spot[0] + "," + spot[1]);
    }

    /**
     * A zero gap has to be allowed to work, which is what makes the overlap test non-strict.
     *
     * With no gap the lattice puts each candidate's edge exactly on the previous one's, so a
     * strict test would call the lattice's own spacing an overlap and walk past a spot that is
     * already correct. Checked with the only arrangement that can see it: the eight neighbours
     * of the centre taken except one, so the free cell is the one that touches.
     */
    private static void touchingEdgesAreNotAnOverlap() {
        float size = 50f, gap = 0f, step = size + gap;
        float baseX = (640f - size) / 2f, baseY = (360f - size) / 2f;
        List<float[]> taken = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 1 && dy == 0) continue; // the hole, sharing an edge with the centre
                taken.add(new float[]{baseX + dx * step, baseY + dy * step, size, size});
            }
        }
        float[][] array = taken.toArray(new float[0][]);
        float[] spot = ControlPlacement.freeSpot(640f, 360f, size, size, gap, array);
        check(Math.abs(spot[0] - (baseX + step)) < 0.001f && Math.abs(spot[1] - baseY) < 0.001f,
                "a spot sharing an edge was rejected as an overlap: " + spot[0] + "," + spot[1]);
    }

    /* ------------------------------------------------------------------ plumbing */

    /** A naive left-to-right packing, to stand in for a layout somebody has built up. */
    private static float[][] grid(float screenW, float screenH, float size, int count) {
        List<float[]> out = new ArrayList<>();
        float x = 0f, y = 0f;
        for (int i = 0; i < count; i++) {
            if (x + size > screenW) {
                x = 0f;
                y += size;
            }
            if (y + size > screenH) break;
            out.add(new float[]{x, y, size, size});
            x += size;
        }
        return out.toArray(new float[0][]);
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
