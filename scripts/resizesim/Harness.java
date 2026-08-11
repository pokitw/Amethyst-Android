// In the package under test, like every other harness here.
package net.kdt.pojavlaunch.customcontrols.handleview;

/**
 * Drives the shipped resize arithmetic.
 *
 * There is no device in CI to drag a corner on, and every way this can be wrong is quiet: a
 * button that reaches zero is still in the layout, draws nothing and can never be selected again,
 * and a step applied after the floor rounds the result straight back under it. Both of those look
 * like the editor working right up until the button disappears.
 */
public class Harness {
    private static int failures = 0;
    private static int checks = 0;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    /** Two density buckets, so nothing here can be accidentally true only at 1x. */
    private static final float[] DENSITIES = {1f, 2.75f};

    public static void main(String[] args) {
        for (float density : DENSITIES) {
            float min = ControlHandleView.MIN_SIZE_DP * density;
            float step = ControlHandleView.STEP_DP * density;
            String at = " at density " + density;

            // The floor. A control dragged to nothing, or past it, is a control that is still in
            // the layout and can never be grabbed back.
            check(ControlHandleView.resolveSize(0f, min, step) >= min, "zero fell below the floor" + at);
            check(ControlHandleView.resolveSize(-500f, min, step) >= min,
                    "a drag past the corner produced a negative size" + at);
            check(ControlHandleView.resolveSize(1f, min, step) >= min, "one pixel beat the floor" + at);

            // The order that matters: snapping AFTER flooring can round back under it. Ask for a
            // size just above the floor and confirm the answer never lands below.
            for (float raw = 0f; raw < min + step * 3; raw += 0.37f) {
                check(ControlHandleView.resolveSize(raw, min, step) >= min,
                        "raw " + raw + " resolved under the floor" + at);
            }

            // The step. Anything above the floor lands on a boundary, which is what makes the
            // drag tick and the final number a round one.
            for (float raw = min + step; raw < min + step * 40; raw += 1.13f) {
                float resolved = ControlHandleView.resolveSize(raw, min, step);
                float offGrid = Math.abs(resolved / step - Math.round(resolved / step));
                check(offGrid < 0.001f,
                        "raw " + raw + " resolved to " + resolved + ", which is off the step" + at);
            }

            // Monotonic: dragging outwards must never shrink the control. A snap implemented with
            // floor() plus a floor() clamp can do exactly that at the boundary.
            float previous = 0f;
            for (float raw = 0f; raw < min + step * 40; raw += 0.5f) {
                float resolved = ControlHandleView.resolveSize(raw, min, step);
                check(resolved >= previous,
                        "growing the drag shrank the control at raw " + raw + at);
                previous = resolved;
            }

            // Stepping off is honoured, for anywhere that wants the raw value.
            check(ControlHandleView.resolveSize(123.4f, min, 0f) == 123.4f,
                    "a zero step still snapped" + at);
            check(ControlHandleView.resolveSize(1f, min, 0f) >= min,
                    "a zero step skipped the floor" + at);
        }

        // The ORDER, tested where it can actually be seen. With a floor that happens to sit on
        // the step grid, snapping first and flooring first give the same answer for every input,
        // so the shipped constants cannot tell the two apart: a mutation run swapping them
        // passed every check above. The property only bites when the floor is off the grid, and
        // it has to be pinned there, because the day either constant moves is the day the
        // ordering silently starts mattering.
        for (float offGridMin : new float[]{21f, 22f, 23f, 18.5f, 30.2f}) {
            for (float raw = -50f; raw < offGridMin + 12f; raw += 0.25f) {
                float resolved = ControlHandleView.resolveSize(raw, offGridMin, 4f);
                check(resolved >= offGridMin,
                        "raw " + raw + " with an off-grid floor of " + offGridMin
                                + " resolved to " + resolved + ", under the floor");
            }
        }

        // Nearest, not downwards. Snapping with floor() is still on the grid and still monotonic,
        // so nothing above catches it, but it means the control does not grow until the finger
        // has passed a whole step, which is the laggy feel this work exists to remove.
        check(ControlHandleView.resolveSize(6.1f, 0f, 4f) == 8f,
                "6.1 with a step of 4 did not round to the nearest step");
        check(ControlHandleView.resolveSize(9.9f, 0f, 4f) == 8f,
                "9.9 with a step of 4 did not round to the nearest step");
        check(ControlHandleView.resolveSize(102f, 0f, 4f) == 104f,
                "102 with a step of 4 did not round up to the nearest step");

        // The constants themselves have to stay sane, since they are the whole policy.
        check(ControlHandleView.MIN_SIZE_DP > 0, "the floor is not positive");
        check(ControlHandleView.MIN_SIZE_DP < 48, "the floor is large enough to overrule the player");
        check(ControlHandleView.STEP_DP > 0, "the step is not positive");
        check(ControlHandleView.STEP_DP < ControlHandleView.MIN_SIZE_DP,
                "the step is coarser than the floor, so the first step jumps past it");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
        System.out.println("control resize OK");
    }
}
