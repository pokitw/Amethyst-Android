package net.kdt.pojavlaunch.recorder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Records the gameplay straight out of the OpenGL pipeline.
 * <p>
 * This class owns the encoding half of the recorder: it configures a hardware H.264 encoder,
 * hands its input {@link Surface} to the native EGL bridge and muxes the encoded samples into an
 * MP4 file. The frames themselves never travel through Java: the render thread blits the game's
 * back buffer into that surface on the GPU (see {@code jni/ctxbridges/gl_recorder.c}).
 * <p>
 * Recording parameters are hardcoded for now, see the constants below.
 */
public class GameRecorder {
    private static final String TAG = "GameRecorder";

    /* ---- Hardcoded recording parameters ---- */
    private static final String MIME_TYPE = "video/avc";
    /** The captured frame is scaled down so that its longest side is this many pixels. */
    private static final int TARGET_LONG_EDGE = 1280;
    private static final int FRAME_RATE = 30;
    private static final int BIT_RATE = 8_000_000;
    private static final int I_FRAME_INTERVAL_SECONDS = 2;
    /** Most hardware H.264 encoders want their dimensions aligned this way. */
    private static final int SIZE_ALIGNMENT = 16;

    private static final long DEQUEUE_TIMEOUT_US = 10_000;
    /** How long we keep draining after end of stream was signalled before giving up. */
    private static final long DRAIN_TIMEOUT_MS = 5_000;

    /** Reports state changes on the main thread. */
    public interface Listener {
        void onRecordingStarted();
        void onRecordingStopped(@NonNull File output);
        void onRecordingFailed(@NonNull String reason);
    }

    private final File mOutputDirectory;
    private final Listener mListener;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    /**
     * Serialises start and stop, both of which block and must stay off the UI thread. Being a
     * single thread, it also gives every field below a happens-before edge between the two.
     */
    private final ExecutorService mControlExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GameRecorder-control");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Whether a session is up as far as Java is concerned. Kept apart from the native state on
     * purpose: the renderer may drop a session on its own (an unsupported driver, a lost
     * context), and the encoder still has to be torn down properly when that happens.
     */
    private volatile boolean mSessionActive;

    /* Owned by the control thread, read by the drain thread. */
    @Nullable private MediaCodec mEncoder;
    @Nullable private MediaMuxer mMuxer;
    @Nullable private Surface mInputSurface;
    @Nullable private File mOutputFile;
    @Nullable private Thread mDrainThread;
    private int mTrackIndex = -1;
    private volatile boolean mMuxerStarted;
    private volatile boolean mDrainFailed;

    public GameRecorder(@NonNull File outputDirectory, @NonNull Listener listener) {
        mOutputDirectory = outputDirectory;
        mListener = listener;
    }

    /** @return whether a recording session is currently running. */
    public boolean isRecording() {
        return mSessionActive;
    }

    /**
     * Start recording, or stop the running one. Returns immediately, the outcome is reported
     * through the {@link Listener}. Repeated taps queue up behind each other rather than racing.
     */
    public void toggle() {
        mControlExecutor.execute(() -> {
            if (mSessionActive) stopInternal();
            else startInternal();
        });
    }

    /** Stop a running recording, if any. Safe to call from lifecycle callbacks. */
    public void stopIfRecording() {
        mControlExecutor.execute(() -> {
            if (mSessionActive) stopInternal();
        });
    }

    /**
     * Only the EGL bridge can be recorded. The "vulkan_zink" renderer draws through OSMesa into a
     * CPU buffer instead, which never reaches the GL pipeline we hook into. This mirrors the
     * bridge selection done by pojavInitOpenGL() in egl_bridge.c.
     */
    private static boolean isRendererSupported() {
        String renderer = Tools.LOCAL_RENDERER;
        return renderer != null && renderer.startsWith("opengles");
    }

    private void startInternal() {
        try {
            if (!isRendererSupported())
                throw new IOException("The " + Tools.LOCAL_RENDERER + " renderer cannot be recorded");

            int[] size = computeRecordingSize();
            if (!mOutputDirectory.exists() && !mOutputDirectory.mkdirs())
                throw new IOException("Could not create " + mOutputDirectory);

            String name = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT).format(new Date());
            mOutputFile = new File(mOutputDirectory, name + ".mp4");

            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, size[0], size[1]);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);

            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mEncoder.createInputSurface();
            mEncoder.start();

            mMuxer = new MediaMuxer(mOutputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mTrackIndex = -1;
            mMuxerStarted = false;
            mDrainFailed = false;

            if (!nativeStartRecording(mInputSurface, size[0], size[1], FRAME_RATE))
                throw new IOException("The renderer refused to start recording");

            mDrainThread = new Thread(this::drainLoop, "GameRecorder-drain");
            mDrainThread.start();

            mSessionActive = true;
            Log.i(TAG, "Recording to " + mOutputFile + " at " + size[0] + "x" + size[1]);
            mMainHandler.post(mListener::onRecordingStarted);
        } catch (Throwable t) {
            Log.e(TAG, "Could not start recording", t);
            mSessionActive = false;
            nativeStopRecording();
            releaseEncoder();
            deleteOutputFile();
            String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            mMainHandler.post(() -> mListener.onRecordingFailed(reason));
        }
    }

    private void stopInternal() {
        mSessionActive = false;
        // Blocks until the render thread has let go of the encoder surface, so that no frame can
        // land after the end of stream marker. Returns straight away if the renderer already
        // dropped the session by itself.
        nativeStopRecording();

        File output = mOutputFile;
        boolean failed = false;
        try {
            if (mEncoder != null) mEncoder.signalEndOfInputStream();
            if (mDrainThread != null) {
                mDrainThread.join(DRAIN_TIMEOUT_MS);
                if (mDrainThread.isAlive()) {
                    Log.w(TAG, "The encoder never reported end of stream");
                    failed = true;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not finish the recording cleanly", t);
            failed = true;
        }
        if (!mMuxerStarted) failed = true; // not a single sample made it through

        releaseEncoder();

        if (failed || mDrainFailed || output == null) {
            deleteOutputFile();
            mMainHandler.post(() -> mListener.onRecordingFailed("The recording could not be saved"));
        } else {
            Log.i(TAG, "Recording saved to " + output);
            mMainHandler.post(() -> mListener.onRecordingStopped(output));
        }
        mOutputFile = null;
    }

    /** Pulls encoded samples out of the codec and writes them to the muxer until end of stream. */
    private void drainLoop() {
        MediaCodec encoder = mEncoder;
        MediaMuxer muxer = mMuxer;
        if (encoder == null || muxer == null) return;

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        try {
            while (true) {
                int status = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US);
                if (status == MediaCodec.INFO_TRY_AGAIN_LATER) continue;

                if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Comes exactly once, before any sample, and carries the codec-specific data.
                    mTrackIndex = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    mMuxerStarted = true;
                    continue;
                }
                if (status < 0) continue; // unknown status, the docs say to ignore those

                ByteBuffer data = encoder.getOutputBuffer(status);
                // The config blob is already carried by the track format given to the muxer.
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bufferInfo.size = 0;
                if (data != null && bufferInfo.size > 0 && mMuxerStarted) {
                    data.position(bufferInfo.offset);
                    data.limit(bufferInfo.offset + bufferInfo.size);
                    muxer.writeSampleData(mTrackIndex, data, bufferInfo);
                }
                encoder.releaseOutputBuffer(status, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Encoding failed", t);
            mDrainFailed = true;
        }
    }

    /**
     * Tears the pipeline down encoder first: MediaMuxer is not thread safe, so the drain thread
     * has to be gone before it is touched. Stopping the codec makes a drain thread that is still
     * blocked in dequeueOutputBuffer() throw, which is exactly how it gets unstuck.
     */
    private void releaseEncoder() {
        if (mEncoder != null) {
            try {
                mEncoder.stop();
            } catch (Throwable t) {
                Log.w(TAG, "Could not stop the encoder", t);
            }
        }
        if (mDrainThread != null) {
            try {
                mDrainThread.join(DRAIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (mDrainThread.isAlive()) {
                // Leaving the muxer alone is the only safe move while something may still write
                // to it, so the file is abandoned rather than finalised.
                Log.e(TAG, "The drain thread is stuck, leaking the muxer to avoid a race");
                mDrainFailed = true;
                mMuxer = null;
            }
            mDrainThread = null;
        }
        if (mEncoder != null) {
            try {
                mEncoder.release();
            } catch (Throwable t) {
                Log.w(TAG, "Could not release the encoder", t);
            }
            mEncoder = null;
        }
        if (mMuxer != null) {
            try {
                if (mMuxerStarted) mMuxer.stop();
            } catch (Throwable t) {
                Log.w(TAG, "Could not stop the muxer", t);
                mDrainFailed = true;
            }
            try {
                mMuxer.release();
            } catch (Throwable t) {
                Log.w(TAG, "Could not release the muxer", t);
            }
            mMuxer = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        mMuxerStarted = false;
        mTrackIndex = -1;
    }

    private void deleteOutputFile() {
        File output = mOutputFile;
        if (output != null && output.exists() && !output.delete())
            Log.w(TAG, "Could not delete the unusable recording " + output);
        mOutputFile = null;
    }

    /**
     * Pick the encoder resolution from the size the game currently renders at, keeping its aspect
     * ratio and never upscaling. The native side letterboxes into whatever we return here, so a
     * mismatch (a rotation mid-recording, say) stays correct, just with bars.
     */
    private static int[] computeRecordingSize() {
        int width = CallbackBridge.windowWidth;
        int height = CallbackBridge.windowHeight;
        if (width <= 0 || height <= 0) {
            width = CallbackBridge.physicalWidth;
            height = CallbackBridge.physicalHeight;
        }
        if (width <= 0 || height <= 0) { // no surface yet, fall back to plain 720p
            width = 1280;
            height = 720;
        }

        int longEdge = Math.max(width, height);
        if (longEdge > TARGET_LONG_EDGE) {
            double scale = (double) TARGET_LONG_EDGE / longEdge;
            width = (int) Math.round(width * scale);
            height = (int) Math.round(height * scale);
        }
        return new int[]{align(width), align(height)};
    }

    private static int align(int value) {
        return Math.max(SIZE_ALIGNMENT,
                (value + SIZE_ALIGNMENT / 2) / SIZE_ALIGNMENT * SIZE_ALIGNMENT);
    }

    static {
        // The recorder lives in libpojavexec, alongside the EGL bridge it hooks into.
        // JREUtils has usually loaded it already by the time the game runs, and loading a
        // library twice is a no-op, but this keeps the class usable on its own.
        System.loadLibrary("pojavexec");
    }

    /** Hands the encoder surface to the render thread, which starts capturing on its next frame. */
    private static native boolean nativeStartRecording(Surface surface, int width, int height,
                                                       int frameRate);

    /** Blocks until the render thread has released the encoder surface. */
    private static native void nativeStopRecording();
}
