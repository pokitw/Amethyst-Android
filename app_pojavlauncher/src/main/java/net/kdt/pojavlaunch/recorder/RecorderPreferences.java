package net.kdt.pojavlaunch.recorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

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

    /** Longest side of the encoded video in pixels, or 0 to keep whatever the game renders at. */
    public final int longEdge;
    public final int frameRate;
    /** Video bitrate in bits per second. */
    public final int videoBitRate;
    public final boolean captureAudio;
    /** Audio bitrate in bits per second. */
    public final int audioBitRate;
    /**
     * Sample rate to capture at. Matching what the device actually outputs keeps the framework
     * from resampling the game's audio, which is the single biggest thing you can hear.
     */
    public final int audioSampleRate;

    private RecorderPreferences(int longEdge, int frameRate, int videoBitRate,
                                boolean captureAudio, int audioBitRate, int audioSampleRate) {
        this.longEdge = longEdge;
        this.frameRate = frameRate;
        this.videoBitRate = videoBitRate;
        this.captureAudio = captureAudio;
        this.audioBitRate = audioBitRate;
        this.audioSampleRate = audioSampleRate;
    }

    public static RecorderPreferences load(@NonNull Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return new RecorderPreferences(
                readInt(preferences, KEY_RESOLUTION, 1280),
                readInt(preferences, KEY_FRAME_RATE, 30),
                preferences.getInt(KEY_VIDEO_BITRATE, 12) * 1_000_000,
                preferences.getBoolean(KEY_CAPTURE_AUDIO, true),
                readInt(preferences, KEY_AUDIO_BITRATE, 192) * 1000,
                outputSampleRate(context));
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
}
