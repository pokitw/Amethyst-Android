package net.kdt.pojavlaunch.recorder;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

/**
 * The details of a recording that the file itself cannot carry.
 * <p>
 * MediaMuxer has no way to attach arbitrary metadata to an MP4, so this is written next to the
 * clip as a small JSON sidecar. Recordings made before this existed, or ones whose sidecar was
 * lost, simply come back with everything unset rather than being treated as broken.
 */
public class RecordingInfo {
    private static final String TAG = "RecordingInfo";
    private static final String EXTENSION = ".json";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final String KEY_VERSION = "minecraftVersion";
    private static final String KEY_WIDTH = "width";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_FRAME_RATE = "frameRate";
    private static final String KEY_AUDIO = "audio";

    @Nullable public final String minecraftVersion;
    public final int width;
    public final int height;
    public final int frameRate;
    /** How the audio track was captured, or null when the clip is silent. */
    @Nullable public final String audio;

    public RecordingInfo(@Nullable String minecraftVersion, int width, int height, int frameRate,
                         @Nullable String audio) {
        this.minecraftVersion = minecraftVersion;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.audio = audio;
    }

    /** The sidecar belonging to a recording, whether or not it exists. */
    public static File sidecarOf(@NonNull File recording) {
        return new File(recording.getAbsolutePath() + EXTENSION);
    }

    public void write(@NonNull File recording) {
        try {
            JSONObject json = new JSONObject();
            if (minecraftVersion != null) json.put(KEY_VERSION, minecraftVersion);
            json.put(KEY_WIDTH, width);
            json.put(KEY_HEIGHT, height);
            json.put(KEY_FRAME_RATE, frameRate);
            if (audio != null) json.put(KEY_AUDIO, audio);

            try (OutputStreamWriter writer =
                         new OutputStreamWriter(new FileOutputStream(sidecarOf(recording)), UTF_8)) {
                writer.write(json.toString());
            }
        } catch (Throwable t) {
            // The recording itself is fine without this, so never let it fail the capture.
            Log.w(TAG, "Could not write the details for " + recording.getName(), t);
        }
    }

    /** @return the stored details, or an empty set of them when there is nothing to read. */
    @NonNull
    public static RecordingInfo read(@NonNull File recording) {
        File sidecar = sidecarOf(recording);
        if (sidecar.isFile()) {
            try {
                byte[] buffer = new byte[(int) Math.min(sidecar.length(), 64 * 1024)];
                try (java.io.FileInputStream input = new java.io.FileInputStream(sidecar)) {
                    int read = input.read(buffer);
                    if (read > 0) {
                        JSONObject json = new JSONObject(new String(buffer, 0, read, UTF_8));
                        return new RecordingInfo(
                                json.optString(KEY_VERSION, null),
                                json.optInt(KEY_WIDTH, 0),
                                json.optInt(KEY_HEIGHT, 0),
                                json.optInt(KEY_FRAME_RATE, 0),
                                json.optString(KEY_AUDIO, null));
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Could not read the details for " + recording.getName(), t);
            }
        }
        return new RecordingInfo(null, 0, 0, 0, null);
    }

    /** Removes the sidecar, so deleting a recording does not leave it orphaned. */
    public static void delete(@NonNull File recording) {
        File sidecar = sidecarOf(recording);
        if (sidecar.exists() && !sidecar.delete())
            Log.w(TAG, "Could not delete the details for " + recording.getName());
    }
}
