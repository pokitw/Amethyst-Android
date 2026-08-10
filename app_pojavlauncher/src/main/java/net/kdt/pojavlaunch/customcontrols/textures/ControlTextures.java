package net.kdt.pojavlaunch.customcontrols.textures;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Where the packs that ship with the launcher live.
     *
     * <b>Read from assets, never copied out.</b> Unpacking them into the game folder on first run
     * is the obvious alternative and it is the {@code default.json} trap in §12.12 all over again:
     * a copy can be deleted, edited, or left behind at an old version, and then every one of those
     * is a state to reason about. From here a built-in pack is the same on every device, cannot be
     * half-written, and costs nothing to add to.
     */
    public static final String BUILT_IN_DIR = "controltextures";

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

    /**
     * The packs that ship with the launcher, in the order they should be offered.
     *
     * Not sorted: these are ordered deliberately in {@link #BUILT_IN_ORDER} so the list opens on
     * the one most people want rather than on whichever name happens to start with an A.
     */
    @NonNull
    public static List<String> builtIn(@Nullable Context context) {
        List<String> found = new ArrayList<>();
        if (context == null) return found;
        try {
            AssetManager assets = context.getAssets();
            String[] children = assets.list(BUILT_IN_DIR);
            if (children == null) return found;
            List<String> present = Arrays.asList(children);
            for (String name : BUILT_IN_ORDER) {
                if (present.contains(name)) found.add(name);
            }
            // Anything shipped but missing from the running order still gets offered, so adding a
            // pack to assets and forgetting this list loses its place rather than the pack.
            for (String name : children) {
                if (!found.contains(name)) found.add(name);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not list the built-in texture packs", t);
        }
        return found;
    }

    /**
     * The running order of the shipped packs.
     *
     * Stone first because it is the Bedrock button this whole feature was asked for, then the rest
     * of the blocks, then the launcher's own two surfaces. Alphabetical would open on Amethyst,
     * which is the brand rather than the answer.
     */
    private static final String[] BUILT_IN_ORDER = {
            "Stone", "Deepslate", "Oak", "Iron", "Gold",
            "Emerald", "Lapis", "Redstone", "Amethyst", "Slate", "Glass"
    };

    /** Every pack that could be selected: the shipped ones, then whatever has been imported. */
    @NonNull
    public static List<String> available(@Nullable Context context) {
        // A set, because an imported pack is allowed to shadow nothing: the importer already
        // renames around the built-ins, but a folder copied in by other means could still collide
        // and the list must not show the same name twice.
        Set<String> names = new LinkedHashSet<>(builtIn(context));
        List<String> installed = new ArrayList<>();
        try {
            File root = dir();
            File[] children = root == null ? null : root.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory() && new File(child, BASE_FILE).isFile()) {
                        installed.add(child.getName());
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not list the texture packs", t);
        }
        java.util.Collections.sort(installed, String.CASE_INSENSITIVE_ORDER);
        names.addAll(installed);
        return new ArrayList<>(names);
    }

    /** Whether this name belongs to a pack that ships with the launcher. */
    public static boolean isBuiltIn(@Nullable Context context, @Nullable String name) {
        return name != null && builtIn(context).contains(name);
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
     * @param context used to reach the packs that ship in assets; disk-only without it
     * @param name    the selected pack's folder name, or null for none
     */
    public static void ensureLoaded(@Nullable Context context, @Nullable String name) {
        if (loaded && equal(name, currentName)) return;
        loaded = true;
        currentName = name;
        current = null;
        if (name == null || name.isEmpty()) return;
        try {
            current = load(context, name);
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

    /**
     * Read one pack without disturbing the one in force, for showing it rather than wearing it.
     *
     * Settings needs every pack's face at once to draw the picker, which is a different question
     * from "what are the controls wearing" and must not answer it: going through
     * {@link #ensureLoaded} would leave the last pack listed loaded as the current one.
     *
     * @return the artwork, or null when there is no readable face under that name
     */
    @Nullable
    public static ControlTexture read(@Nullable Context context, @Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return load(context, name);
        } catch (Throwable t) {
            Log.w(TAG, "Could not read the texture pack \"" + name + "\"", t);
            return null;
        }
    }

    /**
     * One of a pack's files, exactly as it is stored.
     *
     * For copying a pack out rather than drawing it: re-encoding a decoded bitmap would hand back
     * a different PNG from the one the author made, which for a pack being exported to be edited
     * is the one thing it must not be.
     */
    @Nullable
    public static byte[] readFile(@Nullable Context context, @NonNull String name,
                                  @NonNull String fileName) {
        Source source = sourceFor(context, name);
        return source == null ? null : source.read(fileName);
    }

    /** Forget the artwork, so the next {@link #ensureLoaded} reads from disk again. */
    public static void invalidate() {
        loaded = false;
        current = null;
        currentName = null;
    }

    /**
     * Read one pack, from assets if the launcher ships it and from disk otherwise.
     *
     * Assets are tried first so a shipped pack always draws what it is supposed to: the name is
     * what gets written into the preference, and a folder that turned up on disk under the same
     * name would otherwise silently replace it.
     */
    @Nullable
    private static ControlTexture load(@Nullable Context context, @NonNull String name) {
        Source source = sourceFor(context, name);
        if (source == null) return null;
        Bitmap base = source.decode(BASE_FILE);
        if (base == null) return null;

        Bitmap pressed = source.decode(PRESSED_FILE);
        if (pressed != null
                && (pressed.getWidth() != base.getWidth() || pressed.getHeight() != base.getHeight())) {
            // Different dimensions would move the nine-slice grid on every press, which reads as
            // the button flickering rather than as it lighting up.
            Log.w(TAG, source.describe() + ": " + PRESSED_FILE + " is not the same size as "
                    + BASE_FILE + "; ignoring it");
            pressed = null;
        }

        int slice = 0;
        boolean smooth = false;
        boolean darkLabel = false;
        try {
            JSONObject pack = new JSONObject(source.text(PACK_FILE));
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
     * Where one pack's three files are coming from.
     *
     * The two places a pack can live differ only in how a named file inside it is opened, so that
     * is the whole of what this abstracts. Everything else about reading a pack — which files, the
     * size limit, what a missing one means — stays written once in {@link #load}.
     */
    private abstract static class Source {
        /** The bytes of one file, or null when it is not there. */
        @Nullable abstract byte[] read(@NonNull String fileName);

        /** Describes this pack in a log line, since a name alone would not say which one. */
        @NonNull abstract String describe();

        /**
         * Read one PNG, or nothing.
         *
         * The bounds pass first, the same shape the launcher's own picture reading uses, but
         * refusing rather than sampling — see {@link #MAX_DIMENSION}.
         */
        @Nullable
        final Bitmap decode(@NonNull String fileName) {
            byte[] bytes = read(fileName);
            if (bytes == null) return null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    Log.w(TAG, describe() + "/" + fileName + " is not a picture");
                    return null;
                }
                if (bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION) {
                    Log.w(TAG, describe() + "/" + fileName + " is " + bounds.outWidth + "x"
                            + bounds.outHeight + ", larger than " + MAX_DIMENSION + "; skipping it");
                    return null;
                }
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Throwable t) {
                Log.w(TAG, "Could not read " + describe() + "/" + fileName, t);
                return null;
            }
        }

        /** One file as text, or the empty string, which parses as no pack.json at all. */
        @NonNull
        final String text(@NonNull String fileName) {
            byte[] bytes = read(fileName);
            return bytes == null ? "" : new String(bytes, java.nio.charset.Charset.forName("UTF-8"));
        }
    }

    @Nullable
    private static Source sourceFor(@Nullable Context context, @NonNull String name) {
        if (context != null && isBuiltIn(context, name)) {
            return new AssetSource(context.getAssets(), BUILT_IN_DIR + "/" + name);
        }
        File root = dir();
        return root == null ? null : new FileSource(new File(root, name));
    }

    private static final class AssetSource extends Source {
        private final AssetManager assets;
        private final String folder;

        AssetSource(AssetManager assets, String folder) {
            this.assets = assets;
            this.folder = folder;
        }

        @Nullable
        @Override
        byte[] read(@NonNull String fileName) {
            InputStream input = null;
            try {
                input = assets.open(folder + "/" + fileName);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) > 0) out.write(buffer, 0, count);
                return out.toByteArray();
            } catch (Throwable ignored) {
                // Absent is the normal case for the two optional files, so it is not worth a line.
                return null;
            } finally {
                if (input != null) try { input.close(); } catch (Throwable ignored) {}
            }
        }

        @NonNull
        @Override
        String describe() {
            return folder;
        }
    }

    private static final class FileSource extends Source {
        private final File folder;

        FileSource(File folder) {
            this.folder = folder;
        }

        @Nullable
        @Override
        byte[] read(@NonNull String fileName) {
            File file = new File(folder, fileName);
            if (!file.isFile()) return null;
            try {
                long length = file.length();
                if (length <= 0 || length > MAX_FILE_BYTES) return null;
                byte[] bytes = new byte[(int) length];
                java.io.DataInputStream input =
                        new java.io.DataInputStream(new java.io.FileInputStream(file));
                try {
                    input.readFully(bytes);
                } finally {
                    input.close();
                }
                return bytes;
            } catch (Throwable t) {
                Log.w(TAG, "Could not read " + file, t);
                return null;
            }
        }

        @NonNull
        @Override
        String describe() {
            return folder.getName();
        }
    }

    /**
     * A ceiling on one file, so a pack is read into memory rather than a mistake being.
     *
     * The importer already caps a whole archive well below this; this is for a folder that got
     * here some other way, which on a rooted device or an older Android it still can.
     */
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

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
