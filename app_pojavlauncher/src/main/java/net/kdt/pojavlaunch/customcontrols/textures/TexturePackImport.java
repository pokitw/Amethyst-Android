package net.kdt.pojavlaunch.customcontrols.textures;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Getting a pack onto the device.
 *
 * There has to be an importer, and it is not a convenience. Packs live under
 * {@code Android/data}, which from Android 11 no file manager can browse, so without this there
 * is no way for a player to put one there at all — the folder would be a place only the launcher
 * could reach and only the launcher could fill.
 *
 * It follows Game files' rule rather than asking what kind of file this is: the picker takes
 * anything, and what is inside decides. A zip holding a {@code button.png} is a texture pack, the
 * same way a zip holding a {@code pack.mcmeta} is a resource pack.
 *
 * <b>Every entry path is resolved and checked before anything is written.</b> This is the second
 * place in the launcher that unpacks an archive a stranger sent, and it gets the same treatment
 * as the first: an entry that escapes the destination fails the whole import rather than being
 * skipped, because an archive that tried once is not one to trust the rest of.
 */
public final class TexturePackImport {
    private static final String TAG = "TexturePackImport";

    /** Enough for a face, a pressed face and a small json, with room for a careless extra. */
    private static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024;
    private static final int MAX_ENTRIES = 64;

    /** What happened, so the caller can say it in the player's words. */
    public enum Result { ADDED, NOT_A_PACK, FAILED }

    /** Set on {@link Result#ADDED}: the folder name the pack landed under. */
    @Nullable public String name;

    public TexturePackImport() {}

    @NonNull
    public Result importFrom(@NonNull Context context, @NonNull Uri uri) {
        File root = ControlTextures.dir();
        if (root == null) return Result.FAILED;
        if (!root.exists() && !root.mkdirs()) {
            Log.w(TAG, "Could not create " + root);
            return Result.FAILED;
        }

        String folderName = uniqueName(context, root, baseName(context, uri));
        File target = new File(root, folderName);
        if (!target.mkdirs()) {
            Log.w(TAG, "Could not create " + target);
            return Result.FAILED;
        }

        boolean sawBase = false;
        try {
            String canonicalRoot = target.getCanonicalPath() + File.separator;
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new IllegalStateException("no stream for " + uri);
            ZipInputStream zip = new ZipInputStream(input);
            try {
                long written = 0L;
                int entries = 0;
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) break;
                    if (entry.isDirectory()) continue;
                    String name = entry.getName().replace('\\', '/');
                    // Flattened deliberately: packs are shared both zipped from inside the folder
                    // and from outside it, and a pack is three files with fixed names — there is
                    // no structure worth preserving, and flattening removes the whole question of
                    // which directory level the real pack was at.
                    String leaf = name.substring(name.lastIndexOf('/') + 1)
                            .toLowerCase(Locale.US);
                    if (!isWanted(leaf)) continue;

                    File out = new File(target, leaf);
                    if (!out.getCanonicalPath().startsWith(canonicalRoot)) {
                        Log.w(TAG, "Refusing an archive with an entry outside its folder: " + name);
                        deleteTree(target);
                        return Result.FAILED;
                    }
                    written += copy(zip, out, MAX_TOTAL_BYTES - written);
                    if (written >= MAX_TOTAL_BYTES) {
                        Log.w(TAG, "That archive is larger than a texture pack should be");
                        deleteTree(target);
                        return Result.FAILED;
                    }
                    if (ControlTextures.BASE_FILE.equals(leaf)) sawBase = true;
                }
            } finally {
                zip.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not read that archive", t);
            deleteTree(target);
            return Result.FAILED;
        }

        if (!sawBase) {
            // Not a failure, a mismatch: the file was readable and simply is not a pack.
            deleteTree(target);
            return Result.NOT_A_PACK;
        }
        this.name = folderName;
        return Result.ADDED;
    }

    private static boolean isWanted(@NonNull String leaf) {
        return ControlTextures.BASE_FILE.equals(leaf)
                || ControlTextures.PRESSED_FILE.equals(leaf)
                || ControlTextures.PACK_FILE.equals(leaf);
    }

    private static long copy(@NonNull InputStream in, @NonNull File out, long limit)
            throws Exception {
        OutputStream stream = new FileOutputStream(out);
        long total = 0L;
        try {
            byte[] buffer = new byte[16 * 1024];
            while (total < limit) {
                int read = in.read(buffer);
                if (read <= 0) break;
                stream.write(buffer, 0, read);
                total += read;
            }
        } finally {
            stream.close();
        }
        return total;
    }

    /** The zip's own name, reduced to something that is safe as a single path segment. */
    @NonNull
    private static String baseName(@NonNull Context context, @NonNull Uri uri) {
        String display = null;
        try {
            Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) display = cursor.getString(0);
                } finally {
                    cursor.close();
                }
            }
        } catch (Throwable ignored) {
            // No name available; the fallback below covers it.
        }
        if (display == null) return "Texture pack";
        display = display.substring(display.lastIndexOf('/') + 1);
        int dot = display.lastIndexOf('.');
        if (dot > 0) display = display.substring(0, dot);
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < display.length() && safe.length() < 40; i++) {
            char c = display.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == ' ' || c == '-' || c == '_';
            safe.append(ok ? c : ' ');
        }
        String result = safe.toString().trim();
        return ControlTextures.isSafeName(result) ? result : "Texture pack";
    }

    @NonNull
    /**
     * A folder name nothing else answers to.
     *
     * Checked against the shipped packs as well as the folder, and that is not tidiness: a pack is
     * loaded by name and the built-in ones are looked for first, so an import that landed on
     * "Stone" would sit on disk unreachable, with the shipped Stone drawing in its place and
     * nothing on screen to explain why.
     */
    @NonNull
    private static String uniqueName(@NonNull Context context, @NonNull File root,
                                     @NonNull String name) {
        java.util.List<String> builtIn = ControlTextures.builtIn(context);
        String candidate = name;
        for (int index = 2; index < 1000; index++) {
            if (!new File(root, candidate).exists() && !builtIn.contains(candidate)) break;
            candidate = name + " (" + index + ")";
        }
        return candidate;
    }

    private static void deleteTree(@NonNull File root) {
        File[] children = root.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        root.delete();
    }
}
