package net.kdt.pojavlaunch.customcontrols.mouse;

/**
 * Takes the shake out of gyro aiming without taking the speed out.
 *
 * A plain moving average is the obvious way to smooth a noisy signal and the wrong one for aiming:
 * it delays <i>everything</i>, so a flick arrives late and the whole control feels like it is
 * being dragged through syrup. What actually needs smoothing is only the smallest movements — the
 * hand tremor that makes a still phone drift a pixel at a time — and those are exactly the ones
 * nobody would notice arriving a frame late.
 *
 * So each sample is split in two by how fast it is. Slow samples go through the averaging buffer;
 * fast ones bypass it entirely; in between they are mixed, so there is no seam where the
 * behaviour switches. Large movements are therefore delivered with <b>zero</b> added latency, and
 * tiny ones are averaged over a window long enough to erase tremor.
 *
 * <p>The window is measured in <b>time, not samples</b>. Gyroscopes report anywhere from 100Hz to
 * 500Hz depending on the device, and a fixed sample count would mean five times as much smoothing
 * on one phone as another.
 *
 * <p>The idea is Jibb Smart's, from the gyro-aiming work behind Fortnite's and JoyShockMapper's
 * controls. See <a href="http://gyrowiki.jibbsmart.com">GyroWiki</a>.
 */
public class GyroSmoother {

    /**
     * Below this, in degrees per second, a movement is treated as pure tremor and fully smoothed.
     * A hand holding a phone still produces roughly half a degree per second.
     */
    private static final float FULL_SMOOTH_DPS = 1.5f;

    /** Above this, a movement is deliberate and passes through untouched. */
    private static final float NO_SMOOTH_DPS = 7f;

    /** The longest window offered, at 100% strength. Past this, aiming starts to feel detached. */
    private static final float MAX_WINDOW_SECONDS = 0.06f;

    /** Enough for the longest window at the fastest sensor rate anything reports. */
    private static final int MAX_SAMPLES = 48;

    private final float[] mBufferX = new float[MAX_SAMPLES];
    private final float[] mBufferY = new float[MAX_SAMPLES];
    private int mIndex;
    private int mLength = 1;
    private float mSumX;
    private float mSumY;

    /** The smoothed result of the last {@link #filter}, in the same units as its input. */
    public float outX;
    public float outY;

    /**
     * Resize the averaging window.
     *
     * @param strengthPercent 0 for none, 100 for the longest window
     * @param sampleSeconds   how long the gyro actually takes between samples on this device
     */
    public void configure(int strengthPercent, float sampleSeconds) {
        float window = MAX_WINDOW_SECONDS * Math.max(0, Math.min(100, strengthPercent)) / 100f;
        int wanted = 1;
        if (sampleSeconds > 0f) wanted = Math.round(window / sampleSeconds);
        wanted = Math.max(1, Math.min(MAX_SAMPLES, wanted));
        if (wanted == mLength) return;
        mLength = wanted;
        reset();
    }

    /**
     * @param x a rate in degrees per second
     * @param y a rate in degrees per second
     */
    public void filter(float x, float y) {
        if (mLength <= 1) {
            outX = x;
            outY = y;
            return;
        }

        // How much of this sample is fast enough to skip the buffer. Ramped rather than switched:
        // a hard cutoff would make the control change character mid-movement, which reads as a
        // stutter exactly when someone is trying to track a target.
        float magnitude = (float) Math.sqrt(x * x + y * y);
        float direct;
        if (magnitude <= FULL_SMOOTH_DPS) direct = 0f;
        else if (magnitude >= NO_SMOOTH_DPS) direct = 1f;
        else direct = (magnitude - FULL_SMOOTH_DPS) / (NO_SMOOTH_DPS - FULL_SMOOTH_DPS);

        float smoothX = x * (1f - direct);
        float smoothY = y * (1f - direct);

        mSumX -= mBufferX[mIndex];
        mSumY -= mBufferY[mIndex];
        mBufferX[mIndex] = smoothX;
        mBufferY[mIndex] = smoothY;
        mSumX += smoothX;
        mSumY += smoothY;
        mIndex++;
        if (mIndex >= mLength) mIndex = 0;

        outX = x * direct + mSumX / mLength;
        outY = y * direct + mSumY / mLength;
    }

    /** Forget the window, for a gyro that has just been switched on or handed a gap in time. */
    public void reset() {
        mIndex = 0;
        mSumX = 0f;
        mSumY = 0f;
        outX = 0f;
        outY = 0f;
        java.util.Arrays.fill(mBufferX, 0f);
        java.util.Arrays.fill(mBufferY, 0f);
    }
}
