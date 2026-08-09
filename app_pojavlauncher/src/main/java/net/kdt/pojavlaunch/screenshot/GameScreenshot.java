package net.kdt.pojavlaunch.screenshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.media.GalleryExport;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Takes a picture of the frame the game is about to present.
 *
 * <b>Why this exists when the game has F2.</b> Minecraft's own screenshot key already writes into
 * the same folder and already misses the on-screen controls, because those are Android views above
 * the surface rather than anything the game draws. What this adds is that it does not go through
 * the game at all: it needs no keybind, so it cannot be taken away by a modpack that rebinds F2 or
 * by a version whose key is something else, it is one tap on a control button or one tap in the
 * control center, and it says so on screen rather than in the chat log. It also works on
 * vulkan_zink, which the recorder cannot touch.
 *
 * <b>The shape of the handover.</b> The render thread cannot be made to wait on Java and Java must
 * not be made to call into GL, so the two never meet: {@link #nativeRequest()} arms a flag, the
 * next presented frame is copied into a native buffer, and this class collects it on a background
 * thread. The buffer arrives tightly packed, top-down and opaque, which is exactly what
 * {@link Bitmap#copyPixelsFromBuffer} wants — every question of stride, row order and alpha is
 * settled on the native side so that there is one shape of buffer and no room for a mistake here.
 */
public final class GameScreenshot {
    private static final String TAG = "GameScreenshot";

    /**
     * How long to wait for the render thread to produce a frame.
     *
     * A game that has stopped presenting — mid-load, or paused with rendering wound down — would
     * otherwise leave the caller waiting forever. Two seconds is far longer than any frame and
     * short enough that the failure is reported while the player still remembers asking.
     */
    private static final long TIMEOUT_MS = 2000L;

    /**
     * Minecraft's own naming, so the launcher's screenshots and the game's sit together in one
     * ordered list rather than in two interleaved schemes.
     */
    private static final String NAME_PATTERN = "yyyy-MM-dd_HH.mm.ss";

    /** Where it ended up, or why it did not. */
    public interface Callback {
        void onScreenshotSaved(@NonNull File file);

        /** @param messageRes a string resource naming what went wrong */
        void onScreenshotFailed(int messageRes);
    }

    private GameScreenshot() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Guards against a second capture being started while one is still in flight. */
    private static volatile boolean busy;

    /**
     * Take one, off the calling thread.
     *
     * @param callback called on the main thread, exactly once
     */
    public static void take(@NonNull Context context, @NonNull final Callback callback) {
        if (busy) {
            callback.onScreenshotFailed(R.string.screenshot_failed_busy);
            return;
        }
        final ScreenshotPreferences settings = ScreenshotPreferences.load(context);
        // Resolved here, on the caller's thread, because working it out goes through
        // LauncherProfiles — an unsynchronised static the handbook says to read on the main
        // thread (§12.7). Everything after this point is pure file and pixel work.
        final File directory = screenshotsDirectory();
        if (directory == null) {
            callback.onScreenshotFailed(R.string.screenshot_failed);
            return;
        }
        final Context application = context.getApplicationContext();
        busy = true;
        // Its own thread rather than a pool: this happens a few times an hour at most, and it
        // blocks on the render thread and then on a PNG encode, neither of which belongs anywhere
        // shared.
        new Thread(new Runnable() {
            @Override
            public void run() {
                File saved = null;
                try {
                    saved = capture(application, directory, settings);
                } catch (Throwable t) {
                    Log.e(TAG, "The screenshot could not be taken", t);
                }
                final File result = saved;
                busy = false;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null) callback.onScreenshotSaved(result);
                        else callback.onScreenshotFailed(R.string.screenshot_failed);
                    }
                });
            }
        }, "GameScreenshot").start();
    }

    @Nullable
    private static File capture(@NonNull Context context, @NonNull File directory,
                                @NonNull ScreenshotPreferences settings) {
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "Could not create " + directory);
            return null;
        }

        if (!nativeRequest()) return null;
        try {
            int[] info = new int[2];
            ByteBuffer pixels = nativeAwait(info, TIMEOUT_MS);
            if (pixels == null) {
                Log.e(TAG, "The renderer did not hand over a frame");
                return null;
            }
            return write(context, directory, settings, pixels, info[0], info[1]);
        } finally {
            // Always, including after a timeout: this is the only thing that frees the native
            // buffer and lets the next screenshot be asked for.
            nativeRelease();
        }
    }

    @Nullable
    private static File write(@NonNull Context context, @NonNull File directory,
                              @NonNull ScreenshotPreferences settings,
                              @NonNull ByteBuffer pixels, int width, int height) {
        if (width <= 0 || height <= 0) return null;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            // ARGB_8888 is R,G,B,A in memory, which is what glReadPixels(GL_RGBA) produced and
            // what the native side kept. Nothing to swizzle.
            pixels.rewind();
            bitmap.copyPixelsFromBuffer(pixels);
            File target = uniqueFile(directory, settings.extension());
            OutputStream out = new FileOutputStream(target);
            boolean encoded;
            try {
                encoded = bitmap.compress(
                        settings.compressFormat(), settings.compressQuality(), out);
            } finally {
                out.close();
            }
            if (!encoded) {
                Log.e(TAG, "The frame could not be encoded");
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                return null;
            }
            // Already off the main thread, and a screenshot is a couple of megabytes, so the copy
            // happens here rather than on a thread of its own. Failing to reach the gallery does
            // not fail the screenshot: the file the player asked for is written either way.
            GalleryExport.publishImage(context, target);
            return target;
        } catch (Throwable t) {
            Log.e(TAG, "Could not write the screenshot", t);
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * The game folder's screenshots directory for the profile being played, or null.
     *
     * The same folder Minecraft writes its own F2 pictures into, which is the point: one folder,
     * one list, whichever took the picture. Main thread only — see {@link #take}.
     */
    @Nullable
    public static File screenshotsDirectory() {
        try {
            LauncherProfiles.load();
            return new File(Tools.getGameDirPath(LauncherProfiles.getCurrentProfile()),
                    "screenshots");
        } catch (Throwable t) {
            Log.w(TAG, "Could not work out where the screenshots go", t);
            return null;
        }
    }

    /**
     * The game's own scheme, including how it disambiguates.
     *
     * Minecraft appends _1, _2 … when a second picture lands inside the same second, and matching
     * that means a folder holding both sources still reads as one sequence.
     */
    @NonNull
    private static File uniqueFile(@NonNull File directory, @NonNull String extension) {
        String stamp = new SimpleDateFormat(NAME_PATTERN, Locale.US).format(new Date());
        File candidate = new File(directory, stamp + extension);
        for (int index = 1; candidate.exists() && index < 1000; index++) {
            candidate = new File(directory, stamp + "_" + index + extension);
        }
        return candidate;
    }

    static {
        // The capture lives in libpojavexec, alongside the bridges it hooks into. JREUtils has
        // loaded it long before the game is on screen; this only keeps the class usable alone.
        System.loadLibrary("pojavexec");
    }

    /** Arms the next presented frame. False when one is already being taken. */
    private static native boolean nativeRequest();

    /**
     * Blocks until the render thread has a frame, or gives up.
     *
     * @param info  filled with {width, height} on success
     * @return a direct buffer of tightly packed, top-down, opaque RGBA rows, valid until
     *         {@link #nativeRelease()}, or null
     */
    private static native ByteBuffer nativeAwait(int[] info, long timeoutMs);

    /** Frees the frame and clears the request. Safe in any state, which is why it goes in a finally. */
    private static native void nativeRelease();
}
