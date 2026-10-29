package net.kdt.pojavlaunch;

import androidx.annotation.Keep;

/**
 * How many frames the game has actually presented.
 *
 * The one honest answer to "is the screen still black because it is working, or because it is
 * stuck". Counted in {@code pojavSwapBuffers}, which is the single function both the EGL bridge
 * and OSMesa are reached through, so the EGL path and the zink path answer the same way without
 * having to agree about anything.
 *
 * <p><b>Nothing here is load bearing.</b> A reader that cannot reach the symbol gets -1 forever
 * and must carry on without it: this is consulted on the launch path, where the rule is that
 * nothing may throw (§16.15), and the thing it is used for has its own way out regardless.
 */
@Keep
public final class GameFrames {
    private GameFrames() {}

    private static boolean sAvailable = true;

    private static native int nativePresented();

    /** Frames presented since the process started, or -1 where that cannot be read. */
    public static int presented() {
        if (!sAvailable) return -1;
        try {
            return nativePresented();
        } catch (Throwable t) {
            // Latched, because the answer cannot change: the symbol is either in the library
            // that is loaded or it is not, and a reader that kept asking would throw once per
            // poll for the rest of the session.
            sAvailable = false;
            return -1;
        }
    }
}
