package net.kdt.pojavlaunch.customcontrols.mouse;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import net.kdt.pojavlaunch.GrabListener;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Aiming by moving the device, built to feel like a mouse rather than like a phone.
 *
 * <h3>Why the old one stepped</h3>
 * The previous implementation held movement back until it had accumulated more than a fixed
 * amount — 1.13 units on one axis, 1.3 across two — and then released the whole accumulator at
 * once. That is a quantiser: turn slowly and nothing happens, nothing happens, then the view jumps
 * by a pixel and a bit. It was there to hide gyroscope drift, and it worked, at the cost of making
 * every slow movement arrive in visible steps. It also sampled a <i>fused</i> rotation vector at
 * 62Hz and diffed consecutive orientations, which adds the fusion's own filtering and its yaw
 * correction on top.
 *
 * <p>This reads the gyroscope directly instead — angular velocity, in radians per second, at the
 * fastest rate the device offers — integrates it against the real time between samples, and adds
 * the result to the cursor every single time. Drift is dealt with where it belongs, by calibrating
 * it away, so nothing has to be held back to hide it.
 *
 * <p>Sub-pixel movement is not lost by doing that: {@link CallbackBridge#mouseX} is a float and
 * the native bridge floors it only at the moment it hands a position to GLFW, so a tenth of a
 * pixel per sample accumulates and becomes a step every tenth sample. That is exactly what a mouse
 * at a finite DPI does.
 *
 * <h3>Player space</h3>
 * The hard part of gyro aiming is that "turn right" is not a fixed axis of the device — it depends
 * on how the device is being held. Reading the screen's up axis alone (<i>local space</i>, which
 * is what most mobile shooters do) is correct only while the phone is upright, and degrades as it
 * tilts back, which is how almost everyone actually holds one. Lying down with the phone near flat
 * it stops working altogether.
 *
 * <p>So yaw is taken around <i>gravity</i> instead, blended out of the two device axes that can
 * contribute to it, following Jibb Smart's player-space formulation from
 * <a href="http://gyrowiki.jibbsmart.com">GyroWiki</a> — the same approach behind Fortnite's gyro
 * controls. Held upright it is identical to local space; held flat, twisting the phone turns the
 * view, which is what the hands are trying to do. The relax factor of 2 gives roughly 60° of tilt
 * at full one-to-one response before it begins to taper.
 *
 * <h3>Threading</h3>
 * Sensor callbacks arrive on the main thread, and the cursor is pushed from there, exactly as
 * before. The input bridge is not safe to drive from anywhere else.
 */
public class GyroControl implements SensorEventListener, GrabListener {

    /**
     * How many pixels of mouse travel one degree of rotation is worth at 100% sensitivity.
     *
     * Minecraft turns the view by 0.15° per pixel at its default mouse sensitivity, so 1/0.15
     * makes 100% mean <b>one-to-one</b>: turn the phone ten degrees, the view turns ten degrees.
     * That is the anchor every gyro-aiming reference recommends, and it is a real change from the
     * old scale, which was nearer 260% of it — anyone who had tuned the slider will want to raise
     * it once.
     */
    private static final float PIXELS_PER_DEGREE = 1f / 0.15f;

    private static final float RAD_TO_DEG = 57.29578f;

    private static final String TAG = "GyroControl";

    /**
     * Asked-for time between gyroscope samples, in microseconds — 200Hz, and not by accident.
     *
     * Android 12 throws {@link SecurityException} out of
     * {@link SensorManager#registerListener(SensorEventListener, Sensor, int)} for any period
     * under 5000µs unless the app declares {@code HIGH_SAMPLING_RATE_SENSORS}. This asked for
     * 2500µs and crashed the game on resume, because that call happens in {@code onResume} and a
     * throw there is not a gyro that failed, it is an activity that cannot start.
     *
     * <p>The permission is deliberately <b>not</b> declared, because the rate above 200Hz buys
     * nothing here. The game reads the cursor once per {@code pojavPumpEvents}, which is once a
     * frame, so a sensor running at three times the frame rate is already being coalesced before
     * the game sees it — and the whole point of integrating against the measured interval is that
     * the rate does not change the result. {@code scripts/gyrosim/run.sh} checks exactly that.
     */
    private static final int GYRO_PERIOD_US = 5000;

    /** Gravity moves slowly and is only used to decide which way is up. 50Hz is ample. */
    private static final int GRAVITY_PERIOD_US = 20000;

    /**
     * A gap longer than this means the sensor stopped and started — the app was backgrounded, or
     * the device slept. Integrating across it would fling the view.
     */
    private static final float MAX_SAMPLE_SECONDS = 0.1f;

    /**
     * Roughly 60° of tilt before the response starts to taper. Jibb Smart's constant, and the
     * same value JoyShockMapper ships.
     */
    private static final float YAW_RELAX = 2f;

    /**
     * What is left of drift after calibration, in degrees per second, removed softly.
     *
     * Subtracted from the magnitude rather than compared against it: a hard cut-off would make the
     * control dead until it suddenly was not, which is the stepping this rewrite exists to remove.
     */
    private static final float DEADZONE_DPS = 0.15f;

    /* --- calibration --- */

    /** Below this the device is treated as still. Deliberately tighter than a slow, aimed turn. */
    private static final float STILL_DPS = 0.6f;
    /** How long it has to stay that still before its output is believed to be pure bias. */
    private static final float STILL_DELAY_SECONDS = 0.6f;
    /** Time constant once calibrated: slow, so a steady hand is never mistaken for a still one. */
    private static final float BIAS_TAU_SECONDS = 4f;
    /** Time constant before the first calibration, so a fresh start settles in under a second. */
    private static final float BIAS_TAU_FIRST_SECONDS = 0.25f;
    /** No real MEMS gyroscope is out by more than this, so nothing beyond it is bias. */
    private static final float MAX_BIAS_DPS = 2f;

    private final WindowManager mWindowManager;
    private final SensorManager mSensorManager;
    private final Sensor mGyroscope;
    private final Sensor mGravitySensor;
    /** True when gravity is being stood in for by the raw accelerometer, which needs filtering. */
    private final boolean mGravityIsAccelerometer;

    private final GyroSmoother mSmoother = new GyroSmoother();

    private int mSurfaceRotation = Surface.ROTATION_0;
    private boolean mShouldHandleEvents;

    private long mLastTimestamp;
    /** A running estimate of the sensor's interval, which is what sizes the smoothing window. */
    private float mSampleSeconds;
    private float mConfiguredSampleSeconds;
    private int mConfiguredLevel = -1;

    /* Gyroscope bias, in radians per second, device axes. Kept across enable/disable: it belongs
     * to the hardware, not to the session, and throwing it away means drifting until it is
     * relearned. */
    private float mBiasX, mBiasY, mBiasZ;
    private float mStillSeconds;
    private boolean mCalibrated;

    /* Gravity in the screen's frame, normalised. Defaults to "upright", so player space behaves
     * like local space for the fraction of a second before the first gravity sample lands. */
    private float mGravityUp = 1f;
    private float mGravityNormal = 0f;
    private boolean mUpsideDown;

    public GyroControl(Activity activity) {
        mWindowManager = activity.getWindowManager();
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor gravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        // Fused gravity exists on every device that has a gyroscope worth using, but falling back
        // costs one line and the alternative is player space quietly doing nothing.
        if (gravity == null) gravity = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGravitySensor = gravity;
        mGravityIsAccelerometer =
                gravity != null && gravity.getType() == Sensor.TYPE_ACCELEROMETER;
        updateOrientation();
    }

    /**
     * Start listening.
     *
     * Called from {@code MainActivity.onResume}, which is why nothing in here is allowed to throw:
     * an exception on this path does not disable the gyroscope, it stops the game from starting.
     * Sensor registration is the part that can, so it is tried at descending rates and the gyro is
     * simply given up on if none of them are permitted.
     */
    public void enable() {
        if (mGyroscope == null) return;
        mLastTimestamp = 0;
        mStillSeconds = 0;
        mSmoother.reset();

        if (!register(mGyroscope, GYRO_PERIOD_US)
                && !register(mGyroscope, SensorManager.SENSOR_DELAY_GAME)
                && !register(mGyroscope, SensorManager.SENSOR_DELAY_NORMAL)) {
            Log.w(TAG, "The gyroscope would not start at any rate; aiming by motion is off");
            return;
        }
        // Only which way is up. Losing it costs the tilt-aware turning, not the gyro.
        if (mGravitySensor != null) register(mGravitySensor, GRAVITY_PERIOD_US);

        mShouldHandleEvents = CallbackBridge.isGrabbing();
        // Removed first because enable() is reached both from onResume and from the in-game
        // toggle, and the listener list would otherwise gain a duplicate every time.
        CallbackBridge.removeGrabListener(this);
        CallbackBridge.addGrabListener(this);
    }

    /** @return whether the sensor is now being listened to at that rate */
    private boolean register(Sensor sensor, int periodUs) {
        try {
            return mSensorManager.registerListener(this, sensor, periodUs);
        } catch (Throwable t) {
            Log.w(TAG, "Sensor " + sensor.getType() + " refused a " + periodUs + "us period", t);
            return false;
        }
    }

    public void disable() {
        if (mGyroscope == null) return;
        try {
            mSensorManager.unregisterListener(this);
        } catch (Throwable t) {
            Log.w(TAG, "Could not unregister the motion sensors", t);
        }
        mLastTimestamp = 0;
        mSmoother.reset();
        CallbackBridge.removeGrabListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_GRAVITY || type == Sensor.TYPE_ACCELEROMETER) {
            onGravity(event.values[0], event.values[1], event.values[2]);
            return;
        }
        if (type != Sensor.TYPE_GYROSCOPE) return;
        onGyroscope(event);
    }

    /**
     * Which way is up, in the screen's own frame.
     *
     * Android reports gravity pointing away from the Earth, so this vector <i>is</i> world-up
     * expressed in device axes, which is exactly what the player-space blend needs. When the
     * accelerometer is standing in for it the reading also carries the device's own acceleration;
     * a shallow low-pass is enough, because all that is wanted is an orientation, not a
     * measurement.
     */
    private void onGravity(float x, float y, float z) {
        float up;
        switch (mSurfaceRotation) {
            case Surface.ROTATION_90:  up = -x; break;
            case Surface.ROTATION_180: up = -y; break;
            case Surface.ROTATION_270: up = x;  break;
            default:                   up = y;  break;
        }
        float normal = z;
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-4f) return;
        up /= length;
        normal /= length;

        if (mGravityIsAccelerometer) {
            mGravityUp += (up - mGravityUp) * 0.15f;
            mGravityNormal += (normal - mGravityNormal) * 0.15f;
        } else {
            mGravityUp = up;
            mGravityNormal = normal;
        }

        // Held the other way up with the display locked, the screen's axes are inverted relative
        // to the hands holding it. Player space works this out for yaw by itself, since it is
        // defined against gravity, but pitch has no such luxury. The band between the two
        // thresholds is deliberately wide: near the edge, a phone held on its side would otherwise
        // flicker between the two answers.
        if (mGravityUp > 0.25f) mUpsideDown = false;
        else if (mGravityUp < -0.25f) mUpsideDown = true;
    }

    private void onGyroscope(SensorEvent event) {
        long timestamp = event.timestamp;
        if (mLastTimestamp == 0L) {
            mLastTimestamp = timestamp;
            return;
        }
        float dt = (timestamp - mLastTimestamp) * 1e-9f;
        mLastTimestamp = timestamp;
        if (dt <= 0f) return;
        if (dt > MAX_SAMPLE_SECONDS) {
            // Time we cannot account for. Drop it rather than turn it into a flick.
            mSmoother.reset();
            return;
        }

        float rawX = event.values[0];
        float rawY = event.values[1];
        float rawZ = event.values[2];
        updateBias(rawX, rawY, rawZ, dt);

        float x = rawX - mBiasX;
        float y = rawY - mBiasY;
        float z = rawZ - mBiasZ;

        // Always compute against the screen, never the device: the same physical twist has to mean
        // the same thing whichever way round the activity has been laid out.
        float right, up;
        switch (mSurfaceRotation) {
            case Surface.ROTATION_90:  right = y;  up = -x; break;
            case Surface.ROTATION_180: right = -x; up = -y; break;
            case Surface.ROTATION_270: right = -y; up = x;  break;
            default:                   right = x;  up = y;  break;
        }
        float normal = z;

        float yawRate;
        if (LauncherPreferences.PREF_GYRO_PLAYER_SPACE) {
            // The component of the turn that is actually around the world's vertical axis, taken
            // from the two device axes that can contribute to it, then allowed to reach the full
            // size of that pair within the relax factor. Without the clamp, a phone held nearly
            // flat would multiply a small twist into a huge turn.
            float worldYaw = up * mGravityUp + normal * mGravityNormal;
            float combined = (float) Math.sqrt(up * up + normal * normal);
            float scaled = Math.abs(worldYaw) * YAW_RELAX;
            yawRate = Math.signum(worldYaw) * Math.min(scaled, combined);
        } else {
            yawRate = mUpsideDown ? -up : up;
        }
        float pitchRate = mUpsideDown ? -right : right;

        yawRate *= RAD_TO_DEG;
        pitchRate *= RAD_TO_DEG;

        // Applied to the pair rather than to each axis, so a diagonal movement is not bent towards
        // whichever axis happened to clear the threshold first.
        float magnitude = (float) Math.sqrt(yawRate * yawRate + pitchRate * pitchRate);
        if (magnitude <= DEADZONE_DPS) {
            yawRate = 0f;
            pitchRate = 0f;
        } else {
            float scale = (magnitude - DEADZONE_DPS) / magnitude;
            yawRate *= scale;
            pitchRate *= scale;
        }

        updateSmoothing(dt);
        mSmoother.filter(yawRate, pitchRate);
        if (!mShouldHandleEvents) return;

        float pixels = dt * PIXELS_PER_DEGREE * LauncherPreferences.PREF_GYRO_SENSITIVITY;
        float dx = mSmoother.outX * pixels;
        float dy = mSmoother.outY * pixels;
        if (LauncherPreferences.PREF_GYRO_INVERT_X) dx = -dx;
        if (LauncherPreferences.PREF_GYRO_INVERT_Y) dy = -dy;
        if (dx == 0f && dy == 0f) return;

        // Both subtracted: turning the phone anticlockwise about the screen's up axis swings what
        // it faces to the left, and tipping it about the screen's right axis raises what it faces.
        // Minecraft's cursor grows to the right and downwards, so both are the other way round.
        CallbackBridge.mouseX -= dx;
        CallbackBridge.mouseY -= dy;
        CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
    }

    /**
     * Learn what the gyroscope reports when nothing is moving.
     *
     * Every MEMS gyroscope has an offset, it changes with temperature, and integrating it is what
     * makes an uncorrected gyro slowly rotate the view on its own. Measuring it removes the reason
     * the old implementation needed a threshold to sit behind.
     *
     * <p>Conservative on purpose. It only learns while the device is stiller than any deliberate
     * aim, only after it has been that still for a moment, and only towards a value small enough
     * to be real hardware bias — because the failure mode of an eager calibrator is that it
     * quietly learns to cancel out slow aiming.
     */
    private void updateBias(float rawX, float rawY, float rawZ, float dt) {
        float x = rawX - mBiasX;
        float y = rawY - mBiasY;
        float z = rawZ - mBiasZ;
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z) * RAD_TO_DEG;
        if (magnitude > STILL_DPS) {
            mStillSeconds = 0f;
            return;
        }
        mStillSeconds += dt;
        if (mStillSeconds < STILL_DELAY_SECONDS) return;

        float tau = mCalibrated ? BIAS_TAU_SECONDS : BIAS_TAU_FIRST_SECONDS;
        float alpha = Math.min(1f, dt / tau);
        mBiasX = clampBias(mBiasX + (rawX - mBiasX) * alpha);
        mBiasY = clampBias(mBiasY + (rawY - mBiasY) * alpha);
        mBiasZ = clampBias(mBiasZ + (rawZ - mBiasZ) * alpha);
        if (mStillSeconds > STILL_DELAY_SECONDS + 1f) mCalibrated = true;
    }

    private static float clampBias(float bias) {
        float limit = MAX_BIAS_DPS / RAD_TO_DEG;
        return Math.max(-limit, Math.min(limit, bias));
    }

    /**
     * Keep the smoothing window the right length in time.
     *
     * The window is specified in seconds but implemented in samples, so it has to be told how fast
     * this device's gyroscope is actually reporting — which is only knowable by measuring it.
     */
    private void updateSmoothing(float dt) {
        mSampleSeconds = mSampleSeconds == 0f ? dt : mSampleSeconds + (dt - mSampleSeconds) * 0.05f;
        int level = LauncherPreferences.PREF_GYRO_SMOOTHING;
        // Deliberately not once per sample. Resizing the window empties it, and the rate estimate
        // wobbles by a microsecond either way forever, so reconfiguring on every wobble would mean
        // a buffer that was always empty and smoothing that never happened. It is re-checked while
        // the estimate is still settling, and then left alone.
        boolean rateMoved = mConfiguredSampleSeconds <= 0f
                || Math.abs(mSampleSeconds - mConfiguredSampleSeconds)
                        > mConfiguredSampleSeconds * 0.1f;
        if (level == mConfiguredLevel && !rateMoved) return;
        mConfiguredLevel = level;
        mConfiguredSampleSeconds = mSampleSeconds;
        mSmoother.configure(level, mSampleSeconds);
    }

    /** Follow the activity's layout, which is what the screen frame above is relative to. */
    public void updateOrientation() {
        mSurfaceRotation = mWindowManager.getDefaultDisplay().getRotation();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onGrabState(boolean isGrabbing) {
        mShouldHandleEvents = isGrabbing;
        // Not a pause: time keeps passing while the cursor is loose, and resuming with a stale
        // timestamp would integrate all of it at once.
        mLastTimestamp = 0;
        mSmoother.reset();
    }
}
