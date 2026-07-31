package net.kdt.pojavlaunch.recorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import java.io.File;

/**
 * Everything the in-game recorder can be configured with.
 * <p>
 * Read once when a recording starts. The settings screen lives in the launcher process while the
 * recorder runs in the game process, so a change only takes effect on the next launch, same as
 * every other launcher preference.
 */
public class RecorderPreferences {
    private static final String TAG = "RecorderPreferences";

    public static final String KEY_RESOLUTION = "recorderResolution";
    public static final String KEY_FRAME_RATE = "recorderFrameRate";
    public static final String KEY_VIDEO_BITRATE = "recorderVideoBitrate";
    public static final String KEY_CAPTURE_AUDIO = "recorderCaptureAudio";
    public static final String KEY_AUDIO_BITRATE = "recorderAudioBitrate";
    public static final String KEY_AUDIO_SOURCE = "recorderAudioSource";

    /** Values stored by the audio source list preference. */
    public static final String SOURCE_INTERNAL = "internal";
    public static final String SOURCE_MICROPHONE = "microphone";
    public static final String SOURCE_BOTH = "both";

    /**
     * Where a recording is cut short. MP4 addresses its data with 32 bit offsets, so a file that
     * reaches 4 GB stops being valid; stopping well before that leaves room for the index the
     * muxer writes at the end.
     */
    public static final long MAX_OUTPUT_BYTES = 3_500L * 1024 * 1024;
    /** Free space below which a recording will not start, and running ones are wrapped up. */
    public static final long MIN_FREE_BYTES = 250L * 1024 * 1024;

    /** Longest side of the encoded video in pixels, or 0 to keep whatever the game renders at. */
    public final int longEdge;
    public final int frameRate;
    /** Video bitrate in bits per second. */
    public final int videoBitRate;
    public final boolean captureAudio;
    /** Which sources the audio track is built from, one of the SOURCE_ constants. */
    @NonNull public final String audioSource;
    /** Audio bitrate in bits per second. */
    public final int audioBitRate;
    /**
     * Sample rate to capture at. Matching what the device actually outputs keeps the framework
     * from resampling the game's audio, which is the single biggest thing you can hear.
     */
    public final int audioSampleRate;

    private RecorderPreferences(int longEdge, int frameRate, int videoBitRate,
                                boolean captureAudio, @NonNull String audioSource,
                                int audioBitRate, int audioSampleRate) {
        this.longEdge = longEdge;
        this.frameRate = frameRate;
        this.videoBitRate = videoBitRate;
        this.captureAudio = captureAudio;
        this.audioSource = audioSource;
        this.audioBitRate = audioBitRate;
        this.audioSampleRate = audioSampleRate;
    }

    public static RecorderPreferences load(@NonNull Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String source = SOURCE_INTERNAL;
        try {
            String stored = preferences.getString(KEY_AUDIO_SOURCE, SOURCE_INTERNAL);
            if (stored != null) source = stored;
        } catch (Throwable ignored) {
            // Wrong type stored; the default stands.
        }
        return new RecorderPreferences(
                readInt(preferences, KEY_RESOLUTION, 1280),
                readInt(preferences, KEY_FRAME_RATE, 30),
                preferences.getInt(KEY_VIDEO_BITRATE, 12) * 1_000_000,
                preferences.getBoolean(KEY_CAPTURE_AUDIO, true),
                source,
                readInt(preferences, KEY_AUDIO_BITRATE, 192) * 1000,
                outputSampleRate(context));
    }

    /** @return whether the game's own output is part of the audio track. */
    public boolean captureInternalAudio() {
        return captureAudio && !SOURCE_MICROPHONE.equals(audioSource);
    }

    /** @return whether the microphone is part of the audio track. */
    public boolean captureMicrophone() {
        return captureAudio && !SOURCE_INTERNAL.equals(audioSource);
    }

    /**
     * The resolution under the name the settings screen gives it.
     * Looked up through the same arrays the setting itself uses, so the two can never describe the
     * same value differently, and so it stays translated.
     *
     * @param context any context, for the resources
     * @return a label such as "1080p", or the raw long edge if the value is not one of the presets
     */
    @NonNull
    public String describeResolution(@NonNull Context context) {
        String[] values = context.getResources().getStringArray(R.array.recorder_resolution_values);
        String[] names = context.getResources().getStringArray(R.array.recorder_resolution_names);
        String current = Integer.toString(longEdge);
        for (int i = 0; i < values.length && i < names.length; i++) {
            if (current.equals(values[i])) return names[i];
        }
        return longEdge + "px";
    }

    /** A short description of the audio track, stored alongside the recording. */
    @Nullable
    public String describeAudio() {
        if (!captureAudio) return null;
        if (SOURCE_MICROPHONE.equals(audioSource)) return "microphone";
        if (SOURCE_BOTH.equals(audioSource)) return "game and microphone";
        return "game";
    }

    /** ListPreference stores its values as strings even when they are numbers. */
    private static int readInt(SharedPreferences preferences, String key, int fallback) {
        try {
            String value = preferences.getString(key, null);
            if (value != null) return Integer.parseInt(value);
        } catch (Throwable t) {
            Log.w(TAG, "Unreadable value for " + key + ", using " + fallback, t);
        }
        return fallback;
    }

    /**
     * The rate the device's audio output actually runs at. Capturing at anything else makes the
     * framework resample on the way in, which costs quality for nothing.
     */
    private static int outputSampleRate(@NonNull Context context) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                String property = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                if (property != null) {
                    int rate = Integer.parseInt(property);
                    if (rate >= 8000 && rate <= 192000) return rate;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not read the device output sample rate", t);
        }
        return 48000; // what almost every Android device runs at
    }

    /** Where recordings are written for the given game directory. */
    public static File recordingsDirectory(@NonNull File gameDirectory) {
        return new File(gameDirectory, "recordings");
    }

    /** Where the current profile's recordings live, or null if that cannot be worked out. */
    @Nullable
    public static File currentRecordingsDirectory() {
        try {
            LauncherProfiles.load();
            return recordingsDirectory(Tools.getGameDirPath(LauncherProfiles.getCurrentProfile()));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Free space on the volume a directory lives on, walking up to the nearest ancestor that
     * exists, since the recordings folder is only created when the first clip is made.
     */
    public static long usableSpaceFor(@Nullable File directory) {
        for (File candidate = directory; candidate != null; candidate = candidate.getParentFile()) {
            if (candidate.exists()) return candidate.getUsableSpace();
        }
        return 0;
    }

    /** Bytes a second of recording takes at these settings, video and audio together. */
    public long bytesPerSecond() {
        long bits = videoBitRate + (captureAudio ? audioBitRate : 0);
        return Math.max(1, bits / 8);
    }

    /** How long can be recorded before the file limit or the free space runs out, in seconds. */
    public long recordableSeconds(long usableSpace) {
        long spaceBudget = Math.max(0, usableSpace - MIN_FREE_BYTES);
        long budget = Math.min(MAX_OUTPUT_BYTES, spaceBudget);
        return budget / bytesPerSecond();
    }
}
