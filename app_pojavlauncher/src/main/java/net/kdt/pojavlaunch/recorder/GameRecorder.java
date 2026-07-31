package net.kdt.pojavlaunch.recorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
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
import java.nio.ByteOrder;
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

    private static final String MIME_TYPE = "video/avc";
    private static final int I_FRAME_INTERVAL_SECONDS = 2;
    /** Most hardware H.264 encoders want their dimensions aligned this way. */
    private static final int SIZE_ALIGNMENT = 16;

    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int AUDIO_CHANNEL_COUNT = 2;
    private static final int AUDIO_BYTES_PER_FRAME = AUDIO_CHANNEL_COUNT * 2; // 16 bit PCM

    private static final long MAX_OUTPUT_BYTES = RecorderPreferences.MAX_OUTPUT_BYTES;
    private static final long MIN_FREE_BYTES = RecorderPreferences.MIN_FREE_BYTES;
    /** How often the output is measured against those limits. */
    private static final long SIZE_CHECK_INTERVAL_MS = 2_000;

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
        /** A running recording is being wrapped up early; what has been captured is kept. */
        void onRecordingTruncated(@NonNull String reason);
    }

    private final Context mContext;
    private final File mOutputDirectory;
    @Nullable private final String mMinecraftVersion;
    private final Listener mListener;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    /** Settings for the running session, re-read every time a recording starts. */
    @Nullable private RecorderPreferences mPreferences;

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
    @Nullable private AudioRecord mMicRecord;
    @Nullable private AcousticEchoCanceler mEchoCanceler;
    @Nullable private NoiseSuppressor mNoiseSuppressor;
    /** Channels the microphone actually gave us; mono captures are widened when read. */
    private int mMicChannels;
    /** Scratch space for the microphone's share of a mixed buffer. */
    @Nullable private ByteBuffer mMixBuffer;
    @Nullable private short[] mMonoBuffer;
    @Nullable private MediaCodec mAudioEncoder;
    @Nullable private Thread mAudioThread;
    private volatile boolean mAudioStopRequested;
    /** Frames of PCM handed to the encoder so far, the audio track's clock. */
    private long mAudioFramesWritten;
    /** Offset of the first captured sample from the recording's origin, in nanoseconds. */
    private long mAudioBaseNs = -1;
    /** The rate the capture actually runs at, which need not be the one requested. */
    private int mAudioSampleRate = 48000;

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
    /**
     * The recording's origin on the monotonic clock, subtracted from every sample so the file
     * starts at zero. Guarded by mMuxerLock along with the rest of the muxer state.
     */
    private long mPtsOffsetUs;
    private boolean mTimestampsClamped;
    /**
     * When the running recording started, on the elapsed-realtime clock. Kept apart from
     * mPtsOffsetUs, which belongs to the muxer and is read under its lock, so that the on-screen
     * timer can be read from the UI thread without touching any of that.
     */
    private volatile long mStartedAtMs;
    /** Rough size of the media written so far, used to stop before the container overflows. */
    private volatile long mBytesWritten;
    private long mLastSizeCheckMs;
    private boolean mLimitReached;

    public GameRecorder(@NonNull Context context, @NonNull File outputDirectory,
                        @Nullable String minecraftVersion, @NonNull Listener listener) {
        mContext = context.getApplicationContext();
        mOutputDirectory = outputDirectory;
        mMinecraftVersion = minecraftVersion;
        mListener = listener;
    }

    /** @return whether the user asked for the game's audio to be recorded. */
    public boolean wantsAudio() {
        return RecorderPreferences.load(mContext).captureAudio;
    }

    /** @return whether a recording session is currently running. */
    public boolean isRecording() {
        return mSessionActive;
    }

    /** @return how long the running recording has lasted, or zero when nothing is recording. */
    public long getElapsedMs() {
        long startedAt = mStartedAtMs;
        if (!mSessionActive || startedAt == 0L) return 0L;
        return SystemClock.elapsedRealtime() - startedAt;
    }

    /** @return roughly how much has been written to the current file so far, in bytes. */
    public long getBytesWritten() {
        return mBytesWritten;
    }

    /** @return the size a recording is allowed to reach before it is closed off. */
    public static long getMaxOutputBytes() {
        return MAX_OUTPUT_BYTES;
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

            RecorderPreferences preferences = RecorderPreferences.load(mContext);
            mPreferences = preferences;

            int[] size = computeRecordingSize(preferences.longEdge);
            if (!mOutputDirectory.exists() && !mOutputDirectory.mkdirs())
                throw new IOException("Could not create " + mOutputDirectory);
            if (mOutputDirectory.getUsableSpace() < MIN_FREE_BYTES)
                throw new IOException("There is not enough free storage to record");

            String name = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT).format(new Date());
            mOutputFile = new File(mOutputDirectory, name + ".mp4");

            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, size[0], size[1]);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, preferences.videoBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, preferences.frameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);

            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mEncoder.createInputSurface();
            mEncoder.start();

            // Audio is a bonus: if it cannot be set up we still record the picture.
            boolean withAudio = projection != null && preferences.captureAudio
                    && setupAudio(projection, preferences);

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
            // Everything is stamped on the raw monotonic clock, so this instant becomes the file's
            // zero. Taken before capture starts, so no sample can ever land before it.
            mPtsOffsetUs = System.nanoTime() / 1000L;
            mStartedAtMs = SystemClock.elapsedRealtime();
            mTimestampsClamped = false;
            mBytesWritten = 0;
            mLastSizeCheckMs = System.currentTimeMillis();
            mLimitReached = false;

            if (!nativeStartRecording(mInputSurface, size[0], size[1], preferences.frameRate))
                throw new IOException("The " + Tools.LOCAL_RENDERER
                        + " renderer cannot be recorded, see the log for details");

            mDrainThread = new Thread(this::drainLoop, "GameRecorder-drain");
            mDrainThread.start();
            if (withAudio) {
                mAudioThread = new Thread(this::audioLoop, "GameRecorder-audio");
                mAudioThread.start();
            }

            // Details the MP4 itself cannot carry, kept next to it for the gallery to show.
            new RecordingInfo(mMinecraftVersion, size[0], size[1], preferences.frameRate,
                    withAudio ? preferences.describeAudio() : null).write(mOutputFile);

            mSessionActive = true;
            Log.i(TAG, "Recording to " + mOutputFile + " at " + size[0] + "x" + size[1]
                    + "@" + preferences.frameRate + " " + preferences.videoBitRate / 1000 + "kbps"
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
     * Wire up audio capture for whichever sources the user asked for.
     * <p>
     * The game's own output can only be captured through playback capture, which needs a media
     * projection. The microphone is an ordinary capture. When both are wanted they are recorded
     * separately and mixed, because Android has no single source that carries the two.
     *
     * @return whether audio capture is running; false means carry on with video only.
     */
    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the caller before we get here
    private boolean setupAudio(@Nullable MediaProjection projection,
                               @NonNull RecorderPreferences preferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && preferences.captureInternalAudio()) {
            Log.i(TAG, "Playback capture needs Android 10, recording without the game's audio");
            if (!preferences.captureMicrophone()) return false;
        }
        try {
            return setupAudioSources(projection, preferences);
        } catch (Throwable t) {
            Log.e(TAG, "Could not set up audio capture, recording without audio", t);
            releaseAudio();
            return false;
        }
    }

    private boolean setupAudioSources(@Nullable MediaProjection projection,
                                      @NonNull RecorderPreferences preferences) throws IOException {
        // Capture at the rate the device already outputs at. Asking for anything else makes the
        // framework resample the game's audio on the way in and it audibly costs quality.
        int sampleRate = preferences.audioSampleRate;
        int minBuffer = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) minBuffer = sampleRate * AUDIO_BYTES_PER_FRAME / 4;

        boolean wantsInternal = preferences.captureInternalAudio()
                && projection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        if (wantsInternal) {
            mAudioRecord = createPlaybackCapture(projection, sampleRate, minBuffer);
            if (mAudioRecord == null)
                Log.w(TAG, "The game's audio could not be captured");
        }
        if (preferences.captureMicrophone()) {
            // Echo cancellation matters when the two are mixed: without it a device playing
            // through its speaker records the game twice, once cleanly and once through the room.
            mMicRecord = createMicrophoneCapture(sampleRate, minBuffer, mAudioRecord != null);
            if (mMicRecord == null)
                Log.w(TAG, "The microphone could not be captured");
        }
        if (mAudioRecord == null && mMicRecord == null)
            throw new IOException("No audio source could be opened");

        // Whatever the device actually gave us, which need not be what was asked for.
        AudioRecord clock = mAudioRecord != null ? mAudioRecord : mMicRecord;
        mAudioSampleRate = clock.getSampleRate();
        mMicChannels = mMicRecord != null ? mMicRecord.getChannelCount() : 0;
        // Worth having in the log: if the internal source reports a microphone one, the ROM
        // ignored the playback capture config and we would be recording the room, not the game.
        Log.i(TAG, "Audio rate=" + mAudioSampleRate
                + " internal=" + (mAudioRecord != null
                        ? "yes(source=" + mAudioRecord.getAudioSource()
                          + ",ch=" + mAudioRecord.getChannelCount() + ")" : "no")
                + " microphone=" + (mMicRecord != null ? "yes(ch=" + mMicChannels + ")" : "no"));

        MediaFormat format = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, mAudioSampleRate,
                AUDIO_CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, preferences.audioBitRate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuffer * 2);

        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();
        if (mAudioRecord != null) mAudioRecord.startRecording();
        if (mMicRecord != null) mMicRecord.startRecording();
        return true;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    @Nullable
    private AudioRecord createPlaybackCapture(@NonNull MediaProjection projection, int sampleRate,
                                              int minBuffer) {
        try {
            AudioPlaybackCaptureConfiguration captureConfig =
                    new AudioPlaybackCaptureConfiguration.Builder(projection)
                            // Our own playback only: the game shares the launcher's UID.
                            .addMatchingUid(Process.myUid())
                            .build();
            AudioRecord record = new AudioRecord.Builder()
                    .setAudioFormat(stereoFormat(sampleRate))
                    .setBufferSizeInBytes(minBuffer * 2)
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .build();
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record.release();
                return null;
            }
            return record;
        } catch (Throwable t) {
            Log.w(TAG, "Playback capture could not be opened", t);
            return null;
        }
    }

    @SuppressLint("MissingPermission")
    @Nullable
    private AudioRecord createMicrophoneCapture(int sampleRate, int minBuffer, boolean cancelEcho) {
        // Stereo microphones are common but far from universal, so fall back to mono and widen
        // it when mixing rather than giving up on the microphone entirely.
        AudioRecord record = openMicrophone(sampleRate, minBuffer, AudioFormat.CHANNEL_IN_STEREO);
        if (record == null)
            record = openMicrophone(sampleRate, minBuffer, AudioFormat.CHANNEL_IN_MONO);
        if (record == null) return null;

        if (cancelEcho) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler canceler = AcousticEchoCanceler.create(record.getAudioSessionId());
                    if (canceler != null) {
                        canceler.setEnabled(true);
                        mEchoCanceler = canceler;
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor suppressor = NoiseSuppressor.create(record.getAudioSessionId());
                    if (suppressor != null) {
                        suppressor.setEnabled(true);
                        mNoiseSuppressor = suppressor;
                    }
                }
            } catch (Throwable t) {
                // Purely an improvement; the microphone still works without it.
                Log.w(TAG, "Could not attach echo cancellation to the microphone", t);
            }
        }
        return record;
    }

    @SuppressLint("MissingPermission")
    @Nullable
    private AudioRecord openMicrophone(int sampleRate, int minBuffer, int channelMask) {
        try {
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build();
            AudioRecord record = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(minBuffer, 4096) * 2)
                    .build();
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record.release();
                return null;
            }
            return record;
        } catch (Throwable t) {
            return null;
        }
    }

    private static AudioFormat stereoFormat(int sampleRate) {
        return new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();
    }

    /**
     * Moves PCM from the capture into the AAC encoder and its output into the muxer.
     * Timestamps come from the number of samples handed over rather than from the wall clock, so
     * the track stays evenly paced even when a read is late.
     */
    // Only ever started when setupAudio() succeeded, which requires Android 10.
    @SuppressLint("NewApi")
    private void audioLoop() {
        AudioRecord internal = mAudioRecord;
        AudioRecord microphone = mMicRecord;
        MediaCodec encoder = mAudioEncoder;
        if ((internal == null && microphone == null) || encoder == null) return;

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        try {
            while (!mAudioStopRequested) {
                int inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                if (inputIndex >= 0) {
                    ByteBuffer input = encoder.getInputBuffer(inputIndex);
                    int read = 0;
                    if (input != null) {
                        input.clear();
                        read = readAudio(input, internal, microphone);
                    }
                    if (read < 0) read = 0;
                    long framesRead = read / AUDIO_BYTES_PER_FRAME;
                    if (mAudioBaseNs < 0 && read > 0) {
                        // Anchor at the start of the first buffer we actually got, on the same raw
                        // CLOCK_MONOTONIC clock the video frames carry. System.nanoTime() reads
                        // that clock on Android. The muxer rebases both tracks together, so the
                        // two only have to agree with each other, not with any absolute origin.
                        mAudioBaseNs = System.nanoTime()
                                - framesRead * 1_000_000_000L / mAudioSampleRate;
                    }
                    encoder.queueInputBuffer(inputIndex, 0, read, currentAudioTimestampUs(), 0);
                    mAudioFramesWritten += framesRead;
                }
                // Non-blocking here: getting back to reading the capture matters more.
                drainAudio(encoder, bufferInfo, 0);
            }

            // Flush the tail: signal end of stream and drain what is left.
            int inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US * 10);
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(inputIndex, 0, 0, currentAudioTimestampUs(),
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            long deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS;
            // Blocking dequeue now, so waiting for the tail does not spin a core.
            while (!drainAudio(encoder, bufferInfo, DEQUEUE_TIMEOUT_US)
                    && System.currentTimeMillis() < deadline) {
                // keep draining until end of stream shows up
            }
        } catch (Throwable t) {
            Log.e(TAG, "Audio capture failed, the recording keeps its video", t);
        }
    }

    /**
     * Fills the encoder's buffer with stereo PCM from whichever sources are running.
     * <p>
     * With both, the two are summed. They are separate captures rather than one stream, so the
     * only thing keeping them aligned is that they run at the same rate and are read in step;
     * a short read on either side is padded with silence so neither can slip against the other.
     *
     * @return the number of bytes written into the buffer.
     */
    private int readAudio(@NonNull ByteBuffer input, @Nullable AudioRecord internal,
                          @Nullable AudioRecord microphone) {
        int capacity = input.capacity();
        if (internal != null && microphone == null) return Math.max(0, internal.read(input, capacity));
        if (internal == null && microphone != null) return readMicrophone(input, microphone, capacity);

        if (internal == null) return 0;
        // PCM is little endian, while a ByteBuffer reads and writes shorts big endian unless it
        // is told otherwise. Reading samples through the wrong order swaps each one's bytes,
        // which is heard as continuous static rather than as the two sources together.
        input.order(ByteOrder.LITTLE_ENDIAN);
        int read = Math.max(0, internal.read(input, capacity));
        if (read <= 0) return 0;

        // Pull the same span from the microphone into scratch space, then sum the two.
        if (mMixBuffer == null || mMixBuffer.capacity() < read) {
            mMixBuffer = ByteBuffer.allocateDirect(read);
            mMixBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        mMixBuffer.clear();
        int micRead = readMicrophone(mMixBuffer, microphone, read);

        int mixableBytes = Math.min(micRead, read);
        for (int offset = 0; offset + 1 < mixableBytes; offset += 2) {
            int mixed = input.getShort(offset) + mMixBuffer.getShort(offset);
            // Summing two full scale signals overflows, so clip rather than wrap.
            input.putShort(offset, (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed)));
        }
        return read;
    }

    /** Reads microphone PCM as stereo, widening a mono capture so the layouts match. */
    private int readMicrophone(@NonNull ByteBuffer destination, @NonNull AudioRecord microphone,
                               int wantedBytes) {
        destination.order(ByteOrder.LITTLE_ENDIAN);
        if (mMicChannels >= AUDIO_CHANNEL_COUNT)
            return Math.max(0, microphone.read(destination, wantedBytes));

        // One mono frame becomes one stereo frame, so half as many samples are needed.
        int monoSamples = wantedBytes / AUDIO_BYTES_PER_FRAME;
        if (mMonoBuffer == null || mMonoBuffer.length < monoSamples)
            mMonoBuffer = new short[monoSamples];
        int read = Math.max(0, microphone.read(mMonoBuffer, 0, monoSamples));
        for (int frame = 0; frame < read; frame++) {
            short sample = mMonoBuffer[frame];
            destination.putShort(frame * AUDIO_BYTES_PER_FRAME, sample);
            destination.putShort(frame * AUDIO_BYTES_PER_FRAME + 2, sample);
        }
        return read * AUDIO_BYTES_PER_FRAME;
    }

    private long currentAudioTimestampUs() {
        long base = mAudioBaseNs < 0 ? System.nanoTime() : mAudioBaseNs;
        return (base + mAudioFramesWritten * 1_000_000_000L / mAudioSampleRate) / 1000L;
    }

    /** @return whether end of stream was reached. */
    private boolean drainAudio(MediaCodec encoder, MediaCodec.BufferInfo bufferInfo, long timeoutUs) {
        while (true) {
            int status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
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

            // Long sessions are wrapped up before the container or the storage gives out.
            mBytesWritten += bufferInfo.size;
            checkOutputLimits();

            /*
             * Both tracks arrive stamped on the raw CLOCK_MONOTONIC clock, so their timestamps run
             * from the device's uptime rather than from zero. Written out as-is, the file would
             * claim to be as long as the device has been switched on, with all the content bunched
             * at the very end. Shifting both by the same origin puts the recording at zero while
             * leaving the two tracks in step with each other.
             */
            long rebased = bufferInfo.presentationTimeUs - mPtsOffsetUs;
            if (rebased < 0) {
                // Only reachable if the clock behind these timestamps is not the one nanoTime()
                // reads. The muxer rejects negative timestamps, so clamp and say so once.
                if (!mTimestampsClamped) {
                    mTimestampsClamped = true;
                    Log.w(TAG, "Sample timestamps precede the recording start by "
                            + (-rebased / 1000) + " ms, clamping; audio and video may be offset");
                }
                rebased = 0;
            }
            bufferInfo.presentationTimeUs = rebased;

            data.position(bufferInfo.offset);
            data.limit(bufferInfo.offset + bufferInfo.size);
            mMuxer.writeSampleData(track, data, bufferInfo);
        }
    }

    private void releaseAudio() {
        mAudioRecord = releaseRecord(mAudioRecord, "the game's audio capture");
        mMicRecord = releaseRecord(mMicRecord, "the microphone capture");
        if (mEchoCanceler != null) {
            try {
                mEchoCanceler.release();
            } catch (Throwable t) {
                Log.w(TAG, "Could not release the echo canceller", t);
            }
            mEchoCanceler = null;
        }
        if (mNoiseSuppressor != null) {
            try {
                mNoiseSuppressor.release();
            } catch (Throwable t) {
                Log.w(TAG, "Could not release the noise suppressor", t);
            }
            mNoiseSuppressor = null;
        }
        mMixBuffer = null;
        mMonoBuffer = null;
        mMicChannels = 0;
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

    /**
     * Ends a recording that is about to outgrow its container or the storage it sits on.
     * Called while holding mMuxerLock, from whichever encoder happens to be writing.
     */
    private void checkOutputLimits() {
        if (mLimitReached) return;
        long now = System.currentTimeMillis();
        boolean overSize = mBytesWritten >= MAX_OUTPUT_BYTES;
        boolean lowSpace = false;
        if (!overSize) {
            // Cheap counter first; the filesystem is only consulted every so often.
            if (now - mLastSizeCheckMs < SIZE_CHECK_INTERVAL_MS) return;
            mLastSizeCheckMs = now;
            try {
                lowSpace = mOutputDirectory.getUsableSpace() < MIN_FREE_BYTES;
            } catch (Throwable ignored) {
                // Unreadable free space is not a reason to stop.
            }
        }
        if (!overSize && !lowSpace) return;

        mLimitReached = true;
        String reason = overSize
                ? "the recording reached the maximum size a MP4 file can hold"
                : "the device is running out of storage";
        Log.i(TAG, "Finishing the recording early: " + reason);
        // Off the muxer lock: stopping takes the same lock from the control thread.
        mMainHandler.post(() -> {
            mListener.onRecordingTruncated(reason);
            stopIfRecording();
        });
    }

    @Nullable
    private AudioRecord releaseRecord(@Nullable AudioRecord record, String what) {
        if (record == null) return null;
        try {
            if (record.getState() == AudioRecord.STATE_INITIALIZED) record.stop();
        } catch (Throwable t) {
            Log.w(TAG, "Could not stop " + what, t);
        }
        try {
            record.release();
        } catch (Throwable t) {
            Log.w(TAG, "Could not release " + what, t);
        }
        return null;
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
        if (output != null) {
            if (output.exists() && !output.delete())
                Log.w(TAG, "Could not delete the unusable recording " + output);
            RecordingInfo.delete(output);
        }
        mOutputFile = null;
    }

    /**
     * Pick the encoder resolution from the size the game currently renders at, keeping its aspect
     * ratio and never upscaling. The native side letterboxes into whatever we return here, so a
     * mismatch (a rotation mid-recording, say) stays correct, just with bars.
     */
    private static int[] computeRecordingSize(int targetLongEdge) {
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
        // A target of zero means "whatever the game renders at", so only ever scale down.
        if (targetLongEdge > 0 && longEdge > targetLongEdge) {
            double scale = (double) targetLongEdge / longEdge;
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

    /** Hands over the pointer artwork as straight-alpha RGBA, row major. */
    private static native void nativeSetPointerBitmap(byte[] rgba, int width, int height);

    /** Whether the pointer is showing, and its size in game framebuffer pixels. */
    private static native void nativeSetPointerState(boolean visible, float width, float height);

    /**
     * Tells the recorder where the virtual mouse stands.
     * <p>
     * The pointer is an Android view above the game's surface, so it is absent from the frames the
     * recorder copies and has to be drawn back in. Called whenever the pointer appears or
     * disappears, which keeps recordings matching what is on screen.
     *
     * @param widthPx  pointer width in game framebuffer pixels
     * @param heightPx pointer height in game framebuffer pixels
     */
    public static void setPointerState(boolean visible, float widthPx, float heightPx) {
        try {
            nativeSetPointerState(visible, widthPx, heightPx);
        } catch (Throwable t) {
            Log.w(TAG, "Could not update the pointer state", t);
        }
    }

    /**
     * Uploads the pointer artwork once per session.
     * <p>
     * Bitmap.getPixels() packs each pixel as ARGB in a single int, which lands in memory as
     * B, G, R, A on a little endian machine. GL wants R, G, B, A, so the channels are reordered
     * here rather than asking the GPU to sample a layout it has no core support for.
     */
    public static void setPointerBitmap(@Nullable Bitmap bitmap) {
        if (bitmap == null) return;
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width <= 0 || height <= 0) return;

            int[] packed = new int[width * height];
            bitmap.getPixels(packed, 0, width, 0, 0, width, height);
            byte[] rgba = new byte[packed.length * 4];
            for (int i = 0; i < packed.length; i++) {
                int pixel = packed[i];
                rgba[i * 4] = (byte) ((pixel >> 16) & 0xFF);
                rgba[i * 4 + 1] = (byte) ((pixel >> 8) & 0xFF);
                rgba[i * 4 + 2] = (byte) (pixel & 0xFF);
                rgba[i * 4 + 3] = (byte) ((pixel >> 24) & 0xFF);
            }
            nativeSetPointerBitmap(rgba, width, height);
        } catch (Throwable t) {
            Log.w(TAG, "Could not hand over the pointer artwork", t);
        }
    }
}
