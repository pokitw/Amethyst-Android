package android.hardware;
public class SensorManager {
    // Real values. Passed where microseconds are expected, 0..3 are read as delay constants
    // instead — which is what makes them a legal fallback below the 5000us permission threshold.
    public static final int SENSOR_DELAY_FASTEST = 0;
    public static final int SENSOR_DELAY_GAME = 1;
    public static final int SENSOR_DELAY_UI = 2;
    public static final int SENSOR_DELAY_NORMAL = 3;

    public Sensor getDefaultSensor(int type) {
        if (type == Sensor.TYPE_GYROSCOPE || type == Sensor.TYPE_GRAVITY) return new Sensor(type);
        return null;
    }
    /**
     * Below this, in microseconds, registration throws — which is what Android 12 does without
     * HIGH_SAMPLING_RATE_SENSORS. 5000 is the real threshold; the tests raise it to drive the
     * fallback, and set it absurdly high to check that a gyro which will not start at any rate
     * still cannot take the activity down with it.
     */
    public static int strictBelowUs = 5000;

    public boolean registerListener(SensorEventListener l, Sensor s, int us) {
        // 0..3 are the SENSOR_DELAY_* constants rather than a period, and are never restricted.
        if (us > SENSOR_DELAY_NORMAL && us < strictBelowUs) {
            throw new SecurityException("To use the sampling rate of " + us
                    + " microseconds, app needs to declare the normal permission"
                    + " HIGH_SAMPLING_RATE_SENSORS.");
        }
        return true;
    }
    public void unregisterListener(SensorEventListener l) {}
}
