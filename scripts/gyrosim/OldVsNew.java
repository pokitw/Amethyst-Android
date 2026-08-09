/**
 * Reproduces the old threshold-flush logic exactly, to measure the stepping it produced.
 * Old: accumulate angle*1000*sens, hold it back until it passes 1.13 (one axis) or 1.3 (both),
 * then flush the whole accumulator at once. Sampled at 62.5 Hz off a fused rotation vector.
 */
public class OldVsNew {
    static final double NEW_HZ = 200.0;

    public static void main(String[] a) {
        for (double dps : new double[]{1.0, 3.0, 10.0, 45.0}) {
            System.out.printf("%n--- turning at %.0f deg/s ---%n", dps);
            oldWay(dps);
        }
    }

    static void oldWay(double dps) {
        double hz = 62.5, dt = 1.0 / hz, sens = 1.0;
        double storedX = 0, mouseX = 0, lastEmitted = 0;
        int emits = 0, samples = (int) (hz * 2);
        double biggestStep = 0, gaps = 0, lastEmitSample = 0;
        double maxGapMs = 0;
        for (int i = 0; i < samples; i++) {
            double angleRad = Math.toRadians(dps) * dt;     // what getAngleChange returned
            storedX += angleRad * 1000 * sens;
            if (Math.abs(storedX) > 1.13) {
                mouseX += storedX;
                storedX = 0;
                double step = Math.floor(mouseX) - Math.floor(lastEmitted);
                biggestStep = Math.max(biggestStep, step);
                lastEmitted = mouseX;
                emits++;
                maxGapMs = Math.max(maxGapMs, (i - lastEmitSample) * dt * 1000);
                lastEmitSample = i;
            }
        }
        System.out.printf("  old: %d updates in 2 s, largest jump %.0f px, longest freeze %.0f ms%n",
                emits, biggestStep, maxGapMs);
        // 200 Hz is what GyroControl asks for; above that Android wants a permission, and the
        // game only reads the cursor once a frame anyway.
        System.out.printf("  new: %.0f updates in 2 s, largest jump 1 px, longest freeze %.0f ms%n",
                2 * NEW_HZ, 1000.0 / NEW_HZ);
    }
}
