package net.kdt.pojavlaunch.screenshot;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * What the screenshot settings currently say.
 *
 * Read where it is used rather than mirrored into statics, the same way {@link
 * net.kdt.pojavlaunch.recorder.RecorderPreferences} is: these values are only wanted at two
 * moments — when the game surface is built and when a picture is written — and both are rare
 * enough that a fresh read costs nothing and cannot go stale.
 *
 * The game runs in its own process, so these are the values as they stood when it was launched.
 * That is the same contract every other in-game setting has here.
 */
public final class ScreenshotPreferences {
    public static final String KEY_FORMAT = "screenshotFormat";
    public static final String KEY_QUALITY = "screenshotQuality";
    public static final String KEY_SHUTTER_AT_START = "screenshotShutterAtStart";
    public static final String KEY_SHUTTER_SIDE = "screenshotShutterSide";
    public static final String KEY_SHUTTER_SIZE = "screenshotShutterSize";

    public static final String FORMAT_PNG = "png";
    public static final String FORMAT_JPEG = "jpeg";

    public static final String SIDE_RIGHT = "right";
    public static final String SIDE_LEFT = "left";

    public static final String SIZE_SMALL = "small";
    public static final String SIZE_MEDIUM = "medium";
    public static final String SIZE_LARGE = "large";

    /**
     * Lossless by default, because a screenshot is usually kept rather than streamed and because
     * it is what Minecraft's own F2 writes — a folder that mixes the two should not also mix
     * quality unless someone asked for that.
     */
    public static final String DEFAULT_FORMAT = FORMAT_PNG;
    /** High enough that JPEG artefacts do not show on flat Minecraft colour, low enough to matter. */
    public static final int DEFAULT_QUALITY = 92;

    public final String format;
    public final int quality;
    public final boolean shutterAtStart;
    public final String shutterSide;
    public final String shutterSize;

    private ScreenshotPreferences(String format, int quality, boolean shutterAtStart,
                                  String shutterSide, String shutterSize) {
        this.format = format;
        this.quality = quality;
        this.shutterAtStart = shutterAtStart;
        this.shutterSide = shutterSide;
        this.shutterSize = shutterSize;
    }

    @NonNull
    public static ScreenshotPreferences load(@NonNull Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return new ScreenshotPreferences(
                readString(preferences, KEY_FORMAT, DEFAULT_FORMAT),
                clamp(readInt(preferences, KEY_QUALITY, DEFAULT_QUALITY), 40, 100),
                preferences.getBoolean(KEY_SHUTTER_AT_START, false),
                readString(preferences, KEY_SHUTTER_SIDE, SIDE_RIGHT),
                readString(preferences, KEY_SHUTTER_SIZE, SIZE_MEDIUM));
    }

    public boolean isJpeg() {
        return FORMAT_JPEG.equals(format);
    }

    public Bitmap.CompressFormat compressFormat() {
        return isJpeg() ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
    }

    /** PNG ignores the quality argument, so there is nothing to decide for it. */
    public int compressQuality() {
        return isJpeg() ? quality : 100;
    }

    public String extension() {
        return isJpeg() ? ".jpg" : ".png";
    }

    public boolean shutterOnLeft() {
        return SIDE_LEFT.equals(shutterSide);
    }

    /**
     * The shutter's diameter in dp.
     *
     * Three sizes rather than a slider: this is picked once, by whether it falls under your thumb,
     * and nobody wants to drag a control to 61.
     */
    public int shutterDiameterDp() {
        if (SIZE_SMALL.equals(shutterSize)) return 46;
        if (SIZE_LARGE.equals(shutterSize)) return 72;
        return 58;
    }

    private static String readString(SharedPreferences preferences, String key, String fallback) {
        try {
            String stored = preferences.getString(key, fallback);
            return stored == null ? fallback : stored;
        } catch (Throwable ignored) {
            return fallback; // wrong type stored; the default stands
        }
    }

    /**
     * Sliders and choice rows do not agree on how a number is stored, so read both.
     * The same reason {@code RecorderPreferences} does it.
     */
    private static int readInt(SharedPreferences preferences, String key, int fallback) {
        try {
            return preferences.getInt(key, fallback);
        } catch (Throwable ignored) {
            // Stored as a string by a choice row.
        }
        try {
            String stored = preferences.getString(key, null);
            if (stored != null) return Integer.parseInt(stored.trim());
        } catch (Throwable ignored) {
            // Not a number either; the default stands.
        }
        return fallback;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
