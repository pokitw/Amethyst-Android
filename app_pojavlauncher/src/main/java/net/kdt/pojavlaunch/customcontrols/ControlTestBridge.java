package net.kdt.pojavlaunch.customcontrols;

/**
 * The seam that lets a control layout be pressed for real with no game underneath it.
 *
 * <b>A control in the launcher process cannot simply be pressed.</b> Everything a button sends
 * goes through {@code CallbackBridge}, whose senders are {@code @CriticalNative} calls into a JVM
 * that is loaded in the {@code :game} process and nowhere else, so pressing one here would not
 * send a key, it would look for a native symbol that is not in this process. That is why the
 * editor has always intercepted touches before they reach a button at all: not because pressing
 * was undesirable, but because it was not possible.
 *
 * <p>So the test session taps the press one level above the bridge, at
 * {@link net.kdt.pojavlaunch.customcontrols.buttons.ControlButton#sendKeyPresses(boolean)}'s single
 * key path and at the joystick's. Once that seam exists, <b>reporting what came out of it is
 * strictly more than the game could tell you</b>: in a real world you find out a button is bound
 * to the wrong key by watching the wrong thing happen and working backwards, and here it says the
 * key by name the moment it is pressed.
 *
 * <p><b>Main thread only.</b> Every caller is a touch handler or an activity lifecycle method, and
 * a field that is only ever touched from one thread does not need to pretend otherwise. Nothing
 * here is called at all when no session is attached, which is every moment inside a real game.
 */
public final class ControlTestBridge {

    /** Told what a control would have sent, in place of the game that is not there. */
    public interface Listener {
        /**
         * @param keycode a GLFW keycode, or one of {@link ControlData}'s negative special actions
         * @param isDown  which edge of the press this is
         */
        void onControlKey(int keycode, boolean isDown);
    }

    private static Listener sListener;

    private ControlTestBridge() {}

    public static void attach(Listener listener) {
        sListener = listener;
    }

    /**
     * Ends the session, and takes the listener it is given so a host that has already been
     * replaced cannot end somebody else's.
     */
    public static void detach(Listener listener) {
        if (sListener == listener) sListener = null;
    }

    public static boolean isActive() {
        return sListener != null;
    }

    /**
     * Offer a press to the test session.
     *
     * @return true when a session took it, and it must go no further. False is the answer inside a
     *         real game, where this is one null check on the input path and nothing else.
     */
    public static boolean consume(int keycode, boolean isDown) {
        Listener listener = sListener;
        if (listener == null) return false;
        listener.onControlKey(keycode, isDown);
        return true;
    }
}
