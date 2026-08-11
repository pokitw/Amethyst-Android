// In the package under test, like every other harness here.
package net.kdt.pojavlaunch.customcontrols;

/**
 * Drives the shipped slide-to-repeat arithmetic.
 *
 * Nothing here simulates a touch stream: the lifecycle around this (arm on the slide, cancel on
 * the release, give the key back on a detach) lives in a View and cannot be lifted out of one.
 * What can be lifted out, and what breaks silently, is the arithmetic underneath, so that is what
 * this checks and it says so rather than implying more.
 *
 * The two failures worth catching:
 *
 * <ul>
 *   <li><b>An axis-wise threshold.</b> The obvious spelling compares dx and dy separately, which
 *       arms at the configured distance along an axis and at 1.41 times it along a diagonal. The
 *       player experiences that as a gesture that works when they slide down and not when they
 *       slide diagonally, which is close to impossible to report and close to impossible to
 *       guess at from the description. The sweep below binary-searches the arming radius around a
 *       whole circle and insists it is the same in every direction.</li>
 *   <li><b>A gap under one game tick.</b> Minecraft samples input once a tick, so presses closer
 *       together than 50ms are presses it never sees. A repeat that looked twice as fast and
 *       registered no more clicks would read as the setting doing nothing.</li>
 * </ul>
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

    /** One game tick. Stated here rather than read from the shipped constant, so that a change to
     *  the constant has to be argued with this number rather than agreeing with itself. */
    private static final int TICK_MS = 50;

    /** The two sliders the editor offers, which is the whole range a player can ask for. */
    private static final int GAP_MIN = 50, GAP_MAX = 500;
    private static final float SLIDE_MIN = 8f, SLIDE_MAX = 64f;

    public static void main(String[] args) {
        checkSlideDistance();
        checkGap();
        checkThresholdDirections();
        checkThresholdEdges();

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) {
            System.out.println("slide to repeat BROKEN");
            System.exit(1);
        }
        System.out.println("slide to repeat OK");
    }

    private static void checkSlideDistance() {
        // Nothing configured falls back to a distance that is actually usable, rather than to
        // zero, which would arm the repeat on the first move event of every press.
        float fallback = ControlData.slideDistanceDp(0f);
        check(fallback >= SLIDE_MIN && fallback <= SLIDE_MAX,
                "the default slide of " + fallback + " dp is outside what the editor can set");
        check(ControlData.slideDistanceDp(-12f) == fallback, "a negative slide did not fall back");
        check(ControlData.slideDistanceDp(Float.NaN) == fallback, "NaN did not fall back");

        // Every value the slider can produce survives unchanged. A clamp added here later would
        // silently disagree with the number the panel is showing.
        for (float dp = SLIDE_MIN; dp <= SLIDE_MAX; dp += 0.5f) {
            check(ControlData.slideDistanceDp(dp) == dp,
                    "a configured slide of " + dp + " dp came back as "
                            + ControlData.slideDistanceDp(dp));
        }
    }

    private static void checkGap() {
        int fallback = ControlData.repeatGapMs(0);
        check(fallback >= TICK_MS, "the default gap of " + fallback + " ms is under a game tick");
        check(ControlData.repeatGapMs(-1000) >= TICK_MS, "a negative gap beat the floor");
        check(ControlData.repeatGapMs(1) >= TICK_MS, "one millisecond beat the floor");

        for (int gap = -200; gap <= 2000; gap++) {
            int resolved = ControlData.repeatGapMs(gap);
            check(resolved >= TICK_MS, "a gap of " + gap + " resolved to " + resolved
                    + ", which is under a game tick");
            check(gap < TICK_MS || resolved == gap,
                    "a gap of " + gap + " was changed to " + resolved);

            // Press-to-press spacing is twice the half gap, because the cycle sends one edge per
            // half. That is the number the player is actually setting.
            int half = ControlData.repeatHalfGapMs(gap);
            int pressToPress = half * 2;
            check(pressToPress >= TICK_MS, "a gap of " + gap + " gives presses " + pressToPress
                    + " ms apart, which is faster than the game can see");
            check(pressToPress <= resolved, "a gap of " + gap + " gives presses " + pressToPress
                    + " ms apart, faster than the " + resolved + " asked for");
            check(resolved - pressToPress <= 1, "a gap of " + gap + " gives presses "
                    + pressToPress + " ms apart, slower than the " + resolved + " asked for");
            check(half > 0, "a gap of " + gap + " gives a half gap of " + half
                    + ", which would post a repeat with no delay at all");
        }

        // The slider's ends, stated as the rates a player would read off them.
        check(1000 / (ControlData.repeatHalfGapMs(GAP_MIN) * 2) == 20,
                "the fastest the slider offers is not twenty presses a second");
        check(1000 / (ControlData.repeatHalfGapMs(GAP_MAX) * 2) == 2,
                "the slowest the slider offers is not two presses a second");
    }

    /**
     * The threshold is a real distance, the same in every direction.
     *
     * Binary-searched rather than sampled at a few points, because an axis-wise implementation is
     * right along both axes and only wrong between them, which is exactly where a handful of test
     * points would not be.
     */
    private static void checkThresholdDirections() {
        for (float threshold : new float[]{8f, 20f, 64f, 176f}) {
            for (int degrees = 0; degrees < 360; degrees += 7) {
                double radians = Math.toRadians(degrees);
                float ux = (float) Math.cos(radians), uy = (float) Math.sin(radians);

                float low = 0f, high = threshold * 4f;
                for (int i = 0; i < 60; i++) {
                    float mid = (low + high) / 2f;
                    if (ControlData.pastSlideThreshold(ux * mid, uy * mid, threshold)) high = mid;
                    else low = mid;
                }
                check(Math.abs(high - threshold) < threshold * 0.002f,
                        "at " + degrees + " degrees the repeat armed after " + high
                                + " px, not the " + threshold + " asked for");
            }
        }
    }

    private static void checkThresholdEdges() {
        // A move event that has not moved never arms, whatever the threshold says. Zero is
        // reachable: slideDistanceDp guards the stored value, but nothing guards a caller.
        check(!ControlData.pastSlideThreshold(0f, 0f, 0f), "a still finger armed a zero threshold");
        check(!ControlData.pastSlideThreshold(0f, 0f, 20f), "a still finger armed the repeat");

        // Both signs on both axes. Sliding left or up is the same gesture as right or down.
        check(ControlData.pastSlideThreshold(-30f, 0f, 20f), "sliding left did not arm");
        check(ControlData.pastSlideThreshold(0f, -30f, 20f), "sliding up did not arm");
        check(ControlData.pastSlideThreshold(30f, 0f, 20f), "sliding right did not arm");
        check(ControlData.pastSlideThreshold(0f, 30f, 20f), "sliding down did not arm");
        check(!ControlData.pastSlideThreshold(-10f, 0f, 20f), "a short slide left armed");
        check(!ControlData.pastSlideThreshold(0f, -10f, 20f), "a short slide up armed");

        // The diagonal that tells a radial threshold from an axis-wise one in one line: 15 and 15
        // is 21.2 px of travel, past a threshold of 20, while neither axis has reached it.
        check(ControlData.pastSlideThreshold(15f, 15f, 20f),
                "a diagonal slide past the threshold did not arm, so the threshold is per axis");
        check(ControlData.pastSlideThreshold(-15f, 15f, 20f), "the other diagonal did not arm");
        check(!ControlData.pastSlideThreshold(14f, 14f, 20f),
                "a diagonal slide short of the threshold armed anyway");
    }
}
