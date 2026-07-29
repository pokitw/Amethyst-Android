package net.kdt.pojavlaunch.recorder;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

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

    /* ---- Hardcoded audio parameters ---- */
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_CHANNEL_COUNT = 2;
    private static final int AUDIO_BIT_RATE = 128_000;
    private static final int AUDIO_BYTES_PER_FRAME = AUDIO_CHANNEL_COUNT * 2; // 16 bit PCM

    private static final long DEQUEUE_TIMEOUT_US = 10_000;
    /** How long we keep draining after end of stream was signalled before giving up. */
    private static final long DRAIN_TIMEOUT_MS = 5_000;
    /** How long a track waits for its sibling before giving up and dropping early samples. */
    private static final long MUXER_START_TIMEOUT_MS = 2_000;

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

    /* Owned by the control thread, read by the drain threads. */
    @Nullable private MediaCodec mEncoder;
    @Nullable private MediaMuxer mMuxer;
    @Nullable private Surface mInputSurface;
    @Nullable private File mOutputFile;
    @Nullable private Thread mDrainThread;
    private volatile boolean mDrainFailed;

    /* Audio half, all null when recording without sound. */
    @Nullable private AudioRecord mAudioRecord;
    @Nullable private MediaCodec mAudioEncoder;
    @Nullable private Thread mAudioThread;
    private volatile boolean mAudioStopRequested;
    /** Frames of PCM handed to the encoder so far, the audio track's clock. */
    private long mAudioFramesWritten;
    /** Offset of the first captured sample from the recording's origin, in nanoseconds. */
    private long mAudioBaseNs = -1;

    /**
     * Muxer bookkeeping. MediaMuxer may only start once every track has been added and refuses
     * writes before that, so both encoders rendezvous here.
     */
    private final Object mMuxerLock = new Object();
    private int mExpectedTracks;
    private int mAddedTracks;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private volatile boolean mMuxerStarted;

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
     *
     * @param projection a media projection to capture the game's audio through, or null to
     *                   record video only. Ignored when stopping.
     */
    public void toggle(@Nullable MediaProjection projection) {
        mControlExecutor.execute(() -> {
            if (mSessionActive) stopInternal();
            else startInternal(projection);
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

    private void startInternal(@Nullable MediaProjection projection) {
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

            // Audio is a bonus: if it cannot be set up we still record the picture.
            boolean withAudio = projection != null && setupAudio(projection);

            mMuxer = new MediaMuxer(mOutputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            synchronized (mMuxerLock) {
                mExpectedTracks = withAudio ? 2 : 1;
                mAddedTracks = 0;
                mVideoTrackIndex = -1;
                mAudioTrackIndex = -1;
                mMuxerStarted = false;
            }
            mDrainFailed = false;
            mAudioStopRequested = false;
            mAudioFramesWritten = 0;
            mAudioBaseNs = -1;

            // Both tracks are timestamped against this instant, so they line up in the file.
            long startNanos = System.nanoTime();
            if (!nativeStartRecording(mInputSurface, size[0], size[1], FRAME_RATE, startNanos))
                throw new IOException("The renderer refused to start recording");

            mDrainThread = new Thread(this::drainLoop, "GameRecorder-drain");
            mDrainThread.start();
            if (withAudio) {
                mAudioThread = new Thread(() -> audioLoop(startNanos), "GameRecorder-audio");
                mAudioThread.start();
            }

            mSessionActive = true;
            Log.i(TAG, "Recording to " + mOutputFile + " at " + size[0] + "x" + size[1]
                    + (withAudio ? " with audio" : " without audio"));
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

        // Let the audio track flush its tail before the muxer is finalised.
        mAudioStopRequested = true;
        if (mAudioThread != null) {
            try {
                mAudioThread.join(DRAIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mAudioThread = null;
        }

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

    /**
     * Wire up capture of the game's own audio output. Playback capture is the only sanctioned way
     * for an app to record what it is playing, and it needs a media projection to do it.
     *
     * @return whether audio capture is running; false means carry on with video only.
     */
    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the caller before we get here
    private boolean setupAudio(@NonNull MediaProjection projection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.i(TAG, "Playback capture needs Android 10, recording without audio");
            return false;
        }
        try {
            return setupAudioQ(projection);
        } catch (Throwable t) {
            Log.e(TAG, "Could not set up audio capture, recording without audio", t);
            releaseAudio();
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private boolean setupAudioQ(@NonNull MediaProjection projection) throws IOException {
        AudioPlaybackCaptureConfiguration captureConfig =
                new AudioPlaybackCaptureConfiguration.Builder(projection)
                        // Our own playback only: the game shares the launcher's UID.
                        .addMatchingUid(Process.myUid())
                        .build();
        AudioFormat captureFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();
        int minBuffer = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) minBuffer = AUDIO_SAMPLE_RATE * AUDIO_BYTES_PER_FRAME / 4;

        mAudioRecord = new AudioRecord.Builder()
                .setAudioFormat(captureFormat)
                .setBufferSizeInBytes(minBuffer * 2)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build();
        if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED)
            throw new IOException("The audio capture could not be initialised");

        MediaFormat format = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuffer * 2);

        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();
        mAudioRecord.startRecording();
        return true;
    }

    /**
     * Moves PCM from the capture into the AAC encoder and its output into the muxer.
     * Timestamps come from the number of samples handed over rather than from the wall clock, so
     * the track stays evenly paced even when a read is late.
     */
    // Only ever started when setupAudio() succeeded, which requires Android 10.
    @SuppressLint("NewApi")
    private void audioLoop(long startNanos) {
        AudioRecord record = mAudioRecord;
        MediaCodec encoder = mAudioEncoder;
        if (record == null || encoder == null) return;

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        try {
            while (!mAudioStopRequested) {
                int inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                if (inputIndex >= 0) {
                    ByteBuffer input = encoder.getInputBuffer(inputIndex);
                    int read = 0;
                    if (input != null) {
                        input.clear();
                        read = record.read(input, input.capacity());
                    }
                    if (read < 0) read = 0;
                    long framesRead = read / AUDIO_BYTES_PER_FRAME;
                    if (mAudioBaseNs < 0 && read > 0) {
                        // Anchor the track at the start of the first buffer we actually got.
                        mAudioBaseNs = Math.max(0, System.nanoTime() - startNanos
                                - framesRead * 1_000_000_000L / AUDIO_SAMPLE_RATE);
                    }
                    encoder.queueInputBuffer(inputIndex, 0, read, currentAudioTimestampUs(), 0);
                    mAudioFramesWritten += framesRead;
                }
                drainAudio(encoder, bufferInfo);
            }

            // Flush the tail: signal end of stream and drain what is left.
            int inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US * 10);
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(inputIndex, 0, 0, currentAudioTimestampUs(),
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            long deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS;
            while (!drainAudio(encoder, bufferInfo) && System.currentTimeMillis() < deadline) {
                // keep draining until end of stream shows up
            }
        } catch (Throwable t) {
            Log.e(TAG, "Audio capture failed, the recording keeps its video", t);
        }
    }

    private long currentAudioTimestampUs() {
        long base = Math.max(0, mAudioBaseNs);
        return (base + mAudioFramesWritten * 1_000_000_000L / AUDIO_SAMPLE_RATE) / 1000L;
    }

    /** @return whether end of stream was reached. */
    private boolean drainAudio(MediaCodec encoder, MediaCodec.BufferInfo bufferInfo) {
        while (true) {
            int status = encoder.dequeueOutputBuffer(bufferInfo, 0);
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
            if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                addTrack(false, encoder.getOutputFormat());
                continue;
            }
            if (status < 0) continue;

            ByteBuffer data = encoder.getOutputBuffer(status);
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bufferInfo.size = 0;
            if (data != null && bufferInfo.size > 0) writeSample(mAudioTrackIndex, data, bufferInfo);
            encoder.releaseOutputBuffer(status, false);
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true;
        }
    }

    /** Registers a track and starts the muxer once every expected track has shown up. */
    private void addTrack(boolean video, MediaFormat format) {
        synchronized (mMuxerLock) {
            if (mMuxer == null || mMuxerStarted) return;
            int index = mMuxer.addTrack(format);
            if (video) mVideoTrackIndex = index;
            else mAudioTrackIndex = index;
            if (++mAddedTracks >= mExpectedTracks) {
                mMuxer.start();
                mMuxerStarted = true;
                mMuxerLock.notifyAll();
            }
        }
    }

    /**
     * Writes one sample, waiting briefly if the other track has not registered yet. Without the
     * wait the first video keyframe could be dropped while audio is still starting up, which
     * leaves the file unplayable until the next one.
     */
    private void writeSample(int track, ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
        synchronized (mMuxerLock) {
            if (!mMuxerStarted) {
                try {
                    mMuxerLock.wait(MUXER_START_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (!mMuxerStarted || mMuxer == null || track < 0) return; // nothing to write into yet
            data.position(bufferInfo.offset);
            data.limit(bufferInfo.offset + bufferInfo.size);
            mMuxer.writeSampleData(track, data, bufferInfo);
        }
    }

    private void releaseAudio() {
        if (mAudioRecord != null) {
            try {
                if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) mAudioRecord.stop();
            } catch (Throwable t) {
                Log.w(TAG, "Could not stop the audio capture", t);
            }
            try {
                mAudioRecord.release();
            } catch (Throwable t) {
                Log.w(TAG, "Could not release the audio capture", t);
            }
            mAudioRecord = null;
        }
        if (mAudioEncoder != null) {
            try {
                mAudioEncoder.stop();
            } catch (Throwable t) {
                Log.w(TAG, "Could not stop the audio encoder", t);
            }
            try {
                mAudioEncoder.release();
            } catch (Throwable t) {
                Log.w(TAG, "Could not release the audio encoder", t);
            }
            mAudioEncoder = null;
        }
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
                    addTrack(true, encoder.getOutputFormat());
                    continue;
                }
                if (status < 0) continue; // unknown status, the docs say to ignore those

                ByteBuffer data = encoder.getOutputBuffer(status);
                // The config blob is already carried by the track format given to the muxer.
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bufferInfo.size = 0;
                if (data != null && bufferInfo.size > 0) writeSample(mVideoTrackIndex, data, bufferInfo);
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
        // Wake anything still parked waiting for a track that will never register.
        synchronized (mMuxerLock) {
            mMuxerLock.notifyAll();
        }
        releaseAudio();
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
        synchronized (mMuxerLock) {
            mMuxerStarted = false;
            mAddedTracks = 0;
            mVideoTrackIndex = -1;
            mAudioTrackIndex = -1;
        }
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
                                                       int frameRate, long startTimeNanos);

    /** Blocks until the render thread has released the encoder surface. */
    private static native void nativeStopRecording();
}
