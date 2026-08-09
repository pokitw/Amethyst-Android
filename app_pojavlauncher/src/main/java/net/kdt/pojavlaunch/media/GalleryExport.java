package net.kdt.pojavlaunch.media;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Puts a picture or a clip where the phone's gallery will find it.
 *
 * <b>Why a copy is needed at all.</b> Everything the launcher writes lives under
 * {@code Android/data/org.angelauramc.amethyst/files}, which is app-private external storage. The
 * system's media scanner does not index it and cannot be made to: on Android 10 and up that
 * directory is outside the media store's scope entirely. So a file that is only there is a file
 * no gallery, no chat app and no share sheet will ever see, however long you wait.
 *
 * <b>Why a copy and not a move.</b> The game's own screenshots folder is where Minecraft's F2
 * writes too, and both the Game files screen and the recordings gallery read those folders
 * directly. Moving the file out would empty two screens the player already uses to get at it.
 * The cost is honest and is stated where the setting is: a second copy takes a second lot of room.
 *
 * <b>Two ways in, by version.</b> From Android 10 the media store takes the file itself, with no
 * permission and no scanner: insert a row, write into it, clear {@code IS_PENDING}. Below that
 * there is no media store to write into, so the file goes to the public pictures or movies folder
 * and the scanner is asked to look at it — which needs {@code WRITE_EXTERNAL_STORAGE}, declared in
 * the manifest with {@code maxSdkVersion="28"} for exactly this. If it has not been granted, this
 * gives up quietly rather than throwing: a gallery copy is a convenience, and no convenience is
 * worth taking a game down for.
 */
public final class GalleryExport {
    private static final String TAG = "GalleryExport";

    /** One switch for both, since "in my gallery" is one expectation rather than two. */
    public static final String KEY_ENABLED = "galleryExport";
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The album both kinds land in.
     * Named for the launcher rather than for Minecraft, because the gallery is full of other
     * people's folders and "Amethyst X" says whose these are.
     */
    private static final String ALBUM = "Amethyst X";

    /** Copying stops here rather than filling the volume up on the way to a gallery copy. */
    private static final long FREE_SPACE_MARGIN = 200L * 1024 * 1024;

    private GalleryExport() {}

    public static boolean isEnabled(@NonNull Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
        } catch (Throwable t) {
            return DEFAULT_ENABLED;
        }
    }

    /**
     * Copy a finished picture into the gallery. Call from a background thread.
     *
     * @return true when it got there
     */
    public static boolean publishImage(@NonNull Context context, @NonNull File source) {
        String mime = source.getName().toLowerCase(java.util.Locale.US).endsWith(".png")
                ? "image/png" : "image/jpeg";
        return publish(context, source, mime, true);
    }

    /**
     * Copy a finished recording into the gallery. Call from a background thread — a clip can be
     * gigabytes, and this is a byte-for-byte copy of it.
     */
    public static boolean publishVideo(@NonNull Context context, @NonNull File source) {
        return publish(context, source, "video/mp4", false);
    }

    private static boolean publish(@NonNull Context context, @NonNull File source,
                                   @NonNull String mime, boolean image) {
        if (!source.isFile() || source.length() <= 0L) return false;
        if (!isEnabled(context)) return false;
        if (!hasRoomFor(source)) {
            Log.w(TAG, "Not enough free space to copy " + source.getName() + " to the gallery");
            return false;
        }
        try {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? publishViaMediaStore(context, source, mime, image)
                    : publishViaPublicDirectory(context, source, image);
        } catch (Throwable t) {
            Log.w(TAG, "Could not copy " + source.getName() + " to the gallery", t);
            return false;
        }
    }

    /**
     * The volume the gallery lives on is not necessarily the one the game folder is on, so this
     * is a sanity check rather than a guarantee. It is here because the alternative — filling a
     * phone up to the last byte while copying a three gigabyte clip — is much worse than not
     * copying it.
     */
    private static boolean hasRoomFor(@NonNull File source) {
        try {
            File root = Environment.getExternalStorageDirectory();
            if (root == null || !root.exists()) return true;
            return root.getUsableSpace() > source.length() + FREE_SPACE_MARGIN;
        } catch (Throwable t) {
            return true; // unable to tell; let the copy try and fail honestly
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static boolean publishViaMediaStore(@NonNull Context context, @NonNull File source,
                                                @NonNull String mime, boolean image)
            throws Exception {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = image
                ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, source.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                (image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES)
                        + File.separator + ALBUM);
        // Hidden from the gallery until the bytes are all there, so a half-copied clip is never
        // offered up for playing.
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri item = resolver.insert(collection, values);
        if (item == null) {
            Log.w(TAG, "The media store would not take " + source.getName());
            return false;
        }
        try {
            OutputStream out = resolver.openOutputStream(item);
            if (out == null) throw new IllegalStateException("no stream for " + item);
            try {
                copy(source, out);
            } finally {
                out.close();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(item, done, null, null);
            return true;
        } catch (Throwable t) {
            // A pending row with nothing in it would sit there invisible forever.
            try {
                resolver.delete(item, null, null);
            } catch (Throwable ignored) {
                // Nothing further to try.
            }
            throw t instanceof Exception ? (Exception) t : new RuntimeException(t);
        }
    }

    private static boolean publishViaPublicDirectory(@NonNull Context context,
                                                     @NonNull File source, boolean image)
            throws Exception {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "No storage permission, so nothing was copied to the gallery");
            return false;
        }
        File album = new File(Environment.getExternalStoragePublicDirectory(
                image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES), ALBUM);
        if (!album.exists() && !album.mkdirs()) {
            Log.w(TAG, "Could not create " + album);
            return false;
        }
        File target = new File(album, source.getName());
        OutputStream out = new java.io.FileOutputStream(target);
        try {
            copy(source, out);
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw t instanceof Exception ? (Exception) t : new RuntimeException(t);
        } finally {
            out.close();
        }
        // Written straight to disk, so the gallery has no idea it exists until it is told.
        MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()},
                null, null);
        return true;
    }

    private static void copy(@NonNull File source, @NonNull OutputStream out) throws Exception {
        InputStream in = new FileInputStream(source);
        try {
            byte[] buffer = new byte[64 * 1024];
            while (true) {
                int read = in.read(buffer);
                if (read < 0) break;
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            in.close();
        }
    }

    /**
     * The same, on a thread of its own.
     *
     * For the recorder, which finishes on a thread that the player is waiting on: a multi-gigabyte
     * copy must not be what stands between them and the game coming back.
     */
    public static void publishVideoLater(@NonNull final Context context, @Nullable final File file) {
        if (file == null) return;
        final Context application = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                publishVideo(application, file);
            }
        }, "GalleryExport").start();
    }
}
