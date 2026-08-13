package net.kdt.pojavlaunch.testlaunch;

/**
 * A test launch asked for in the control editor, waiting for the launcher to come back to the top.
 *
 * <b>The editor cannot launch the game itself.</b> The launch listener is registered by
 * {@code LauncherActivity} and everything downstream of it wants an activity that is actually in
 * front: it shows dialogs, it starts activities, and it reads the account spinner, which is the
 * source of truth for who is logged in (handbook 12.3). The editor is a different activity sitting
 * on top of it, so raising the event from there would run all of that against a paused screen.
 *
 * <p>So the editor sets this and finishes. The launcher picks it up in {@code onResume}, which is
 * the first moment it is genuinely back, and raises the event exactly as the Play button does.
 * Nothing about the launch path itself changes, which is the point (12.2).
 *
 * <p>A static rather than an intent extra because both ends are in the launcher process and the
 * editor is started with a plain {@code startActivity} from two different places; threading a
 * result through both would be more moving parts for the same one bit. Main thread only.
 */
public final class TestLaunchRequest {

    private static boolean sPending;

    private TestLaunchRequest() {}

    public static void request() {
        sPending = true;
    }

    /** @return whether a launch was asked for, clearing it so it can never fire twice. */
    public static boolean consume() {
        boolean pending = sPending;
        sPending = false;
        return pending;
    }

    /** Drop a request that never got to run, so it cannot fire long after it was meant to. */
    public static void cancel() {
        sPending = false;
    }
}
