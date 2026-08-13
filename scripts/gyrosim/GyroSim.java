import android.hardware.*;
import net.kdt.pojavlaunch.customcontrols.mouse.GyroControl;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import org.lwjgl.glfw.CallbackBridge;

/** Drives the real GyroControl with synthetic motion and checks what comes out. */
public class GyroSim {
    static final Sensor GYRO = new Sensor(Sensor.TYPE_GYROSCOPE);
    static final Sensor GRAV = new Sensor(Sensor.TYPE_GRAVITY);
    /** The rate GyroControl actually asks for: 200Hz, the fastest Android allows unpermitted. */
    static final double HZ = 200.0;
    static final long STEP_NS = (long) (1e9 / HZ);
    static long clock = 1_000_000_000L;
    static int failures = 0;

    static SensorEvent ev(Sensor s, float a, float b, float c) {
        SensorEvent e = new SensorEvent();
        e.sensor = s; e.values[0] = a; e.values[1] = b; e.values[2] = c; e.timestamp = clock;
        return e;
    }

    /** Feed gravity so player space knows which way is up. gUp/gNormal in device axes, ROTATION_90. */
    static void gravity(GyroControl g, float upFrac, float normalFrac) {
        gravity(g, upFrac, 0f, normalFrac);
    }

    /**
     * Gravity with all three screen axes, which is what a player who is not upright needs.
     *
     * <b>The two-argument form above could not express the pose that broke this.</b> It pins the
     * screen's right axis to zero, so every fixture written with it has gravity lying exactly in
     * the plane player space reads yaw from, and player space is perfectly conditioned in all of
     * them. Somebody lying on their side puts gravity almost entirely along that pinned axis. A
     * harness that cannot say so agrees with the bug (16.20).
     *
     * <p>ROTATION_90: screenUp = -deviceX, screenRight = deviceY, screenNormal = deviceZ.
     */
    static void gravity(GyroControl g, float upFrac, float rightFrac, float normalFrac) {
        g.onSensorChanged(
                ev(GRAV, -upFrac * 9.81f, rightFrac * 9.81f, normalFrac * 9.81f));
    }

    /** Run `seconds` of constant angular velocity, in deg/s about the screen axes. */
    static void turn(GyroControl g, double yawDps, double pitchDps, double seconds) {
        int n = (int) (seconds * HZ);
        for (int i = 0; i < n; i++) {
            clock += STEP_NS;
            // ROTATION_90: screenRight = deviceY, screenUp = -deviceX -> deviceX = -screenUp
            float up = (float) Math.toRadians(yawDps), right = (float) Math.toRadians(pitchDps);
            g.onSensorChanged(ev(GYRO, -up, right, 0f));
        }
    }

    static void idle(GyroControl g, double seconds, float biasDps) {
        int n = (int) (seconds * HZ);
        for (int i = 0; i < n; i++) {
            clock += STEP_NS;
            float b = (float) Math.toRadians(biasDps);
            g.onSensorChanged(ev(GYRO, b, b * 0.5f, 0f));
        }
    }

    static GyroControl fresh() {
        CallbackBridge.mouseX = 0; CallbackBridge.mouseY = 0;
        android.view.Display.ROTATION = android.view.Surface.ROTATION_90;
        GyroControl g = new GyroControl(new android.app.Activity());
        g.enable();
        g.onGrabState(true);
        gravity(g, 1f, 0f);              // upright
        clock += STEP_NS; g.onSensorChanged(ev(GYRO, 0, 0, 0));  // seed the timestamp
        return g;
    }

    static void check(String name, boolean ok, String detail) {
        System.out.printf("%-46s %s   %s%n", name, ok ? "PASS" : "FAIL", detail);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        // 1:1 scaling. 90 degrees of yaw at 100% should be 90/0.15 = 600 px.
        GyroControl g = fresh();
        turn(g, 90.0, 0.0, 1.0);
        double dx = -CallbackBridge.mouseX;     // turning left = mouseX decreasing
        check("90 deg yaw -> 600 px (1:1)", Math.abs(dx - 600) < 12,
              String.format("got %.1f px", dx));

        // Pitch, same scale.
        g = fresh();
        turn(g, 0.0, 45.0, 1.0);
        double dy = -CallbackBridge.mouseY;
        check("45 deg pitch -> 300 px", Math.abs(dy - 300) < 8, String.format("got %.1f px", dy));

        // No drift when still, with a 0.4 deg/s hardware bias present.
        g = fresh();
        idle(g, 20.0, 0.4f);
        double drift = Math.hypot(CallbackBridge.mouseX, CallbackBridge.mouseY);
        check("20 s still with 0.4 dps bias -> no drift", drift < 2.0,
              String.format("%.2f px over 20 s", drift));

        // Smoothness: no step is much bigger than the average during a slow turn.
        g = fresh();
        double prev = 0, biggest = 0, total;
        int n = (int) (2.0 * HZ);
        for (int i = 0; i < n; i++) {
            clock += STEP_NS;
            float up = (float) Math.toRadians(3.0);
            g.onSensorChanged(ev(GYRO, -up, 0, 0));
            double now = -CallbackBridge.mouseX;
            biggest = Math.max(biggest, Math.floor(now) - Math.floor(prev));
            prev = now;
        }
        total = prev;
        check("slow 3 dps turn steps by 1 px at a time", biggest <= 1.0,
              String.format("largest single step %.0f px, %.1f px total", biggest, total));

        // A fast flick is not delayed by smoothing: the full angle still arrives.
        g = fresh();
        turn(g, 400.0, 0.0, 0.25);
        double flick = -CallbackBridge.mouseX;
        check("400 dps flick for 0.25 s -> full 100 deg", Math.abs(flick - 666.7) < 20,
              String.format("got %.1f px, want 666.7", flick));

        // Player space: phone lying flat, twisting about the screen normal should turn the view.
        g = fresh();
        gravity(g, 0f, 1f);              // world-up is out of the screen: phone flat, face up
        for (int i = 0; i < (int) HZ; i++) {
            clock += STEP_NS;
            g.onSensorChanged(ev(GYRO, 0, 0, (float) Math.toRadians(90.0)));  // roll, device Z
        }
        double flat = Math.abs(CallbackBridge.mouseX);
        check("flat phone: twist turns the view", flat > 500,
              String.format("got %.1f px, want ~600", flat));

        // Local space in the same pose must NOT turn - that is the bug player space fixes.
        LauncherPreferences.PREF_GYRO_PLAYER_SPACE = false;
        g = fresh();
        gravity(g, 0f, 1f);
        for (int i = 0; i < (int) HZ; i++) {
            clock += STEP_NS;
            g.onSensorChanged(ev(GYRO, 0, 0, (float) Math.toRadians(90.0)));
        }
        check("local space ignores it (the old behaviour)", Math.abs(CallbackBridge.mouseX) < 1,
              String.format("got %.1f px", CallbackBridge.mouseX));
        LauncherPreferences.PREF_GYRO_PLAYER_SPACE = true;

        // Lying on your side, which is how this was reported: horizontal nearly dead, vertical
        // fine. Gravity is then along the screen's RIGHT axis, so it contributes nothing to the
        // pair player space builds yaw from, and the unblended formula returns almost zero.
        g = fresh();
        gravity(g, 0f, 1f, 0f);          // rolled a full 90 degrees onto one side
        turn(g, 90.0, 0.0, 1.0);
        double side = -CallbackBridge.mouseX;
        check("on your side: 90 deg yaw still turns 600 px", Math.abs(side - 600) < 30,
              String.format("got %.1f px", side));

        // The other side, because a fix that only worked one way round would pass the line above.
        g = fresh();
        gravity(g, 0f, -1f, 0f);
        turn(g, 90.0, 0.0, 1.0);
        double otherSide = -CallbackBridge.mouseX;
        check("on the other side: same turn, same direction", Math.abs(otherSide - 600) < 30,
              String.format("got %.1f px", otherSide));

        // Turning the other way has to go the other way, by the same amount. Every check above
        // turns left, so a fallback that dropped the sign and returned a magnitude passed all of
        // them: right and left were both "600 px left". Caught by mutating the fix, not by
        // reading it.
        g = fresh();
        gravity(g, 0f, 1f, 0f);
        turn(g, -90.0, 0.0, 1.0);
        double sideBack = -CallbackBridge.mouseX;
        check("on your side: turning back goes back", Math.abs(sideBack + 600) < 30,
              String.format("got %.1f px, want -600", sideBack));

        // Pitch was the half that always worked. It must still work, and not have been traded.
        g = fresh();
        gravity(g, 0f, 1f, 0f);
        turn(g, 0.0, 45.0, 1.0);
        double sidePitch = -CallbackBridge.mouseY;
        check("on your side: pitch is unchanged", Math.abs(sidePitch - 300) < 12,
              String.format("got %.1f px", sidePitch));

        // The whole roll, in one sweep. The complaint was "uneven" as much as "slow", so what
        // matters is that no posture between upright and flat on your side is left weak: the
        // response has to stay near 1:1 the whole way round rather than sagging in the middle.
        double worst = 1e9, worstAt = 0;
        for (int deg = 0; deg <= 90; deg += 5) {
            double r = Math.toRadians(deg);
            g = fresh();
            gravity(g, (float) Math.cos(r), (float) Math.sin(r), 0f);
            turn(g, 90.0, 0.0, 1.0);
            double got = -CallbackBridge.mouseX;
            if (got < worst) { worst = got; worstAt = deg; }
        }
        check("every roll from upright to on-your-side aims", worst > 540,
              String.format("weakest %.1f px at %.0f deg of roll, want ~600", worst, worstAt));

        // Upright, player space and local space must agree.
        g = fresh(); turn(g, 60.0, 0.0, 1.0);
        double player = -CallbackBridge.mouseX;
        LauncherPreferences.PREF_GYRO_PLAYER_SPACE = false;
        g = fresh(); turn(g, 60.0, 0.0, 1.0);
        double local = -CallbackBridge.mouseX;
        LauncherPreferences.PREF_GYRO_PLAYER_SPACE = true;
        check("upright: player space == local space", Math.abs(player - local) < 5,
              String.format("%.1f vs %.1f px", player, local));

        // Rate independence: 100 Hz and 400 Hz must produce the same total.
        double[] totals = new double[2];
        for (int k = 0; k < 2; k++) {
            long step = (long) (1e9 / (k == 0 ? 100.0 : 400.0));
            CallbackBridge.mouseX = 0; CallbackBridge.mouseY = 0;
            GyroControl h = new GyroControl(new android.app.Activity());
            h.enable(); h.onGrabState(true);
            gravity(h, 1f, 0f);
            clock += step; h.onSensorChanged(ev(GYRO, 0, 0, 0));
            for (int i = 0; i < (k == 0 ? 100 : 400); i++) {
                clock += step;
                h.onSensorChanged(ev(GYRO, (float) -Math.toRadians(90.0), 0, 0));
            }
            totals[k] = -CallbackBridge.mouseX;
        }
        check("100 Hz and 400 Hz give the same turn",
              Math.abs(totals[0] - totals[1]) < 15,
              String.format("%.1f vs %.1f px", totals[0], totals[1]));

        // enable() is called from MainActivity.onResume, so a throw there is not a gyro that
        // failed -- it is an activity that cannot resume. This shipped once: 2500us asked for,
        // SecurityException raised, game dead on launch.
        android.hardware.SensorManager.strictBelowUs = 5000;
        boolean threw = false;
        try { fresh(); } catch (Throwable t) { threw = true; }
        check("enable() at the real Android 12 limit", !threw, "no exception");

        // A stricter device than any that exists, so the fallback to SENSOR_DELAY_GAME is taken.
        android.hardware.SensorManager.strictBelowUs = 100000;
        threw = false;
        try {
            GyroControl fb = fresh();
            turn(fb, 90.0, 0.0, 1.0);
            check("falls back to a slower rate and still aims",
                    Math.abs(-CallbackBridge.mouseX - 600) < 12,
                    String.format("got %.1f px", -CallbackBridge.mouseX));
        } catch (Throwable t) { threw = true; }
        check("fallback path does not throw", !threw, "no exception");

        // Nothing will start at all. The game must still run, without a gyro.
        android.hardware.SensorManager.strictBelowUs = Integer.MAX_VALUE;
        threw = false;
        try {
            CallbackBridge.mouseX = 0;
            GyroControl dead = new GyroControl(new android.app.Activity());
            dead.enable();
            dead.onGrabState(true);
            dead.disable();
        } catch (Throwable t) { threw = true; }
        check("a gyro that will not start is not a crash", !threw, "no exception");
        android.hardware.SensorManager.strictBelowUs = 5000;

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
