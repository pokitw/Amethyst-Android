package net.kdt.pojavlaunch.customcontrols.textures;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The texture pack the controls are currently wearing, if any.
 *
 * A pack is a folder holding a {@code button.png}, optionally a {@code button_pressed.png}, and
 * optionally a {@code pack.json}. That is the whole format. It is deliberately the whole format:
 * a control layout is buttons of nineteen different sizes and shapes, so what a pack can usefully
 * say is what a button face looks like and where its border stops — everything past that is the
 * layout's job, and the layout already does it.
 *
 * <b>Why there is no per-button artwork.</b> The obvious next step is {@code jump.png}, matched on
 * the button's name. The name is the wrong key and the handbook already says so: icons are picked
 * from <i>the key a button sends</i>, "so old layouts gain icons with no migration". A name is an
 * unvalidated free-text field that can be empty, duplicated, translated, or literally "..", and
 * baking it into a shareable file format would freeze the weakest identity in the system forever.
 * If per-button art earns its place later it goes on the keycode, like the glyphs.
 *
 * <b>Failure is per file.</b> A pack whose {@code button_pressed.png} is corrupt loses its pressed
 * face and keeps its buttons; only a missing or unreadable {@code button.png} disables the pack,
 * and then the controls fall back to the flat skin rather than to nothing. This sits inside
 * {@code loadControls} on the launch path, where a thrown exception is not a missing texture, it
 * is a game that will not start.
 */
public final class ControlTextures {
    private static final String TAG = "ControlTextures";

    public static final String BASE_FILE = "button.png";
    public static final String PRESSED_FILE = "button_pressed.png";
    public static final String PACK_FILE = "pack.json";

    /**
     * Bigger than this and the file is refused rather than sampled down.
     *
     * Sampling would be the usual answer, but the nine-slice inset is written in source pixels: a
     * bitmap quietly halved would have every pack's border cut in the wrong place. A button is a
     * couple of hundred pixels across at most, so anything past this is a mistake worth reporting
     * rather than a picture worth rescuing.
     */
    private static final int MAX_DIMENSION = 1024;

    private ControlTextures() {}

    /** The folder packs live in. Beside the layouts rather than inside them — see {@link #dir}. */
    @Nullable
    public static File dir() {
        // Read lazily. Tools' storage constants are assigned in initStorageConstants, so a static
        // initialiser here would capture null on any run where storage was not ready yet.
        String home = Tools.DIR_GAME_HOME;
        if (home == null) return null;
        // Not under controlmap/: the two surviving layout dialogs list that directory and show
        // every folder in it, so a "textures" folder there would appear in the layout picker
        // forever, holding nothing anyone could load.
        return new File(home, "controltextures");
    }

    /** Every pack that could be selected, by folder name, sorted for a stable picker. */
    @NonNull
    public static List<String> available() {
        List<String> names = new ArrayList<>();
        try {
            File root = dir();
            File[] children = root == null ? null : root.listFiles();
            if (children == null) return names;
            for (File child : children) {
                if (child.isDirectory() && new File(child, BASE_FILE).isFile()) {
                    names.add(child.getName());
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not list the texture packs", t);
        }
        java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /* --------------------------------------------------------------- the loaded pack */

    @Nullable private static ControlTexture current;
    @Nullable private static String currentName;
    private static boolean loaded;

    /**
     * Make sure the pack named by the current style is the one in memory.
     *
     * Called wherever the controls are about to be built — the game's {@code loadControls}, the
     * editor activity, and the return from Settings that already reloads preferences and the
     * layout. Reloading is keyed on the name, so the common case is a string comparison.
     *
     * @param name the selected pack's folder name, or null for none
     */
    public static void ensureLoaded(@Nullable String name) {
        if (loaded && equal(name, currentName)) return;
        loaded = true;
        currentName = name;
        current = null;
        if (name == null || name.isEmpty()) return;
        try {
            current = load(name);
        } catch (Throwable t) {
            // Never fatal: the controls simply come out in the flat skin.
            Log.w(TAG, "The texture pack \"" + name + "\" could not be loaded", t);
        }
        if (current == null && name != null && !name.isEmpty()) {
            Log.w(TAG, "No usable " + BASE_FILE + " in \"" + name + "\"; drawing without textures");
        }
    }

    /** The artwork in force, or null when the controls are drawn flat. */
    @Nullable
    public static ControlTexture current() {
        return current;
    }

    /** Whether a pack is actually drawing, which is not the same as one being selected. */
    public static boolean isActive() {
        return current != null;
    }

    /** Forget the artwork, so the next {@link #ensureLoaded} reads from disk again. */
    public static void invalidate() {
        loaded = false;
        current = null;
        currentName = null;
    }

    @Nullable
    private static ControlTexture load(@NonNull String name) {
        File root = dir();
        if (root == null) return null;
        File folder = new File(root, name);
        Bitmap base = decode(new File(folder, BASE_FILE));
        if (base == null) return null;

        Bitmap pressed = decode(new File(folder, PRESSED_FILE));
        if (pressed != null
                && (pressed.getWidth() != base.getWidth() || pressed.getHeight() != base.getHeight())) {
            // Different dimensions would move the nine-slice grid on every press, which reads as
            // the button flickering rather than as it lighting up.
            Log.w(TAG, PRESSED_FILE + " is not the same size as " + BASE_FILE + "; ignoring it");
            pressed = null;
        }

        int slice = 0;
        boolean smooth = false;
        boolean darkLabel = false;
        try {
            String json = Tools.read(new File(folder, PACK_FILE).getAbsolutePath());
            JSONObject pack = new JSONObject(json);
            slice = pack.optInt("slice", 0);
            smooth = pack.optBoolean("smooth", false);
            darkLabel = "dark".equalsIgnoreCase(pack.optString("label", "light"));
        } catch (Throwable ignored) {
            // No pack.json, or one that cannot be read: every value has a working default.
        }

        int shortest = Math.min(base.getWidth(), base.getHeight());
        if (slice > 0 && slice * 2 >= shortest) {
            int clamped = Math.max(0, (shortest - 1) / 2);
            Log.w(TAG, "slice " + slice + " does not fit a " + base.getWidth() + "x"
                    + base.getHeight() + " face; using " + clamped);
            slice = clamped;
        }
        return new ControlTexture(base, pressed, Math.max(0, slice), smooth, darkLabel);
    }

    /**
     * Read one PNG, or nothing.
     *
     * The bounds pass first, the same shape the launcher's own picture reading uses, but refusing
     * rather than sampling — see {@link #MAX_DIMENSION}.
     */
    @Nullable
    private static Bitmap decode(@NonNull File file) {
        if (!file.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(TAG, file.getName() + " is not a picture");
                return null;
            }
            if (bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION) {
                Log.w(TAG, file.getName() + " is " + bounds.outWidth + "x" + bounds.outHeight
                        + ", larger than " + MAX_DIMENSION + "; skipping it");
                return null;
            }
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable t) {
            Log.w(TAG, "Could not read " + file, t);
            return null;
        }
    }

    private static boolean equal(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Only the folder name is ever used as a path segment, so it is the only thing to check. */
    public static boolean isSafeName(@Nullable String name) {
        if (name == null || name.isEmpty() || name.length() > 64) return false;
        if (name.startsWith(".")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == ' ' || c == '-' || c == '_';
            if (!ok) return false;
        }
        return !Arrays.asList(".", "..").contains(name);
    }
}
