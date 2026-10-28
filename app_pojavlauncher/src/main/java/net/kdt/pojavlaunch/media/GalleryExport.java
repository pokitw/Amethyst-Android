package net.kdt.pojavlaunch.media;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
        return publish(context, source, mime, true, false);
    }

    /**
     * Copy a picture into the gallery because the player asked for this one, setting or not.
     *
     * The preference answers "copy everything I capture", which is a standing instruction and
     * the right thing to gate the automatic path on. A button is the opposite: somebody looking
     * at one file and asking for it. Reading the preference here would make that button refuse
     * in exactly the case it exists for, which is what it did.
     */
    public static boolean publishImageOnRequest(@NonNull Context context, @NonNull File source) {
        String mime = source.getName().toLowerCase(java.util.Locale.US).endsWith(".png")
                ? "image/png" : "image/jpeg";
        return publish(context, source, mime, true, true);
    }

    /**
     * Copy a finished recording into the gallery. Call from a background thread — a clip can be
     * gigabytes, and this is a byte-for-byte copy of it.
     */
    public static boolean publishVideo(@NonNull Context context, @NonNull File source) {
        return publish(context, source, "video/mp4", false, false);
    }

    /** As {@link #publishImageOnRequest}, for a clip. Call from a background thread. */
    public static boolean publishVideoOnRequest(@NonNull Context context, @NonNull File source) {
        return publish(context, source, "video/mp4", false, true);
    }

    /** {@link #publishVideoOnRequest} on a thread of its own, since a clip can be gigabytes. */
    public static void publishVideoOnRequestLater(@NonNull final Context context,
                                                  @Nullable final File file) {
        if (file == null) return;
        final Context application = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                publishVideoOnRequest(application, file);
            }
        }, "GalleryExport").start();
    }

    private static boolean publish(@NonNull Context context, @NonNull File source,
                                   @NonNull String mime, boolean image, boolean onRequest) {
        if (!source.isFile() || source.length() <= 0L) return false;
        if (!onRequest && !isEnabled(context)) return false;
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

    /**
     * What a bulk copy did, so a settings row can report it rather than merely finishing.
     *
     * {@code unavailable} and {@code busy} are told apart from "nothing new" deliberately: all
     * three finish instantly having copied nothing, and only one of them means the gallery is
     * already up to date.
     */
    public static final class BulkResult {
        public final int copied;
        public final int skipped;
        public final int failed;
        /** The folder could not be listed at all, which is not the same as it being empty. */
        public final boolean unavailable;
        /** Another copy of this kind is already running. */
        public final boolean busy;

        BulkResult(int copied, int skipped, int failed, boolean unavailable, boolean busy) {
            this.copied = copied;
            this.skipped = skipped;
            this.failed = failed;
            this.unavailable = unavailable;
            this.busy = busy;
        }

        public int total() {
            return copied + skipped + failed;
        }
    }

    /**
     * One bulk copy at a time, per process rather than per screen.
     *
     * The settings row that starts this lives in a composition, and leaving the screen and
     * coming back destroys whatever state it was holding while the copy carries on: there is no
     * suspension point inside it to cancel at. A second press would then run a second pass over
     * the same folder, and the two would race the first one's media store inserts, so
     * {@link #alreadyInGallery} would answer false for a file the other pass was halfway
     * through writing. The guard belongs here, where the copy is, and not on the row.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean BULK_RUNNING =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Copy a whole folder into the gallery, on request, skipping what is already there.
     *
     * The automatic path only ever sees a file at the moment it is created, so anything captured
     * before the setting was turned on, or while it was off, is invisible to every gallery on
     * the phone and has no route out: the launcher's own folders are under {@code Android/data},
     * which from Android 11 cannot be browsed to at all. Editing one file and pressing the
     * editor's button is a route for one file, and that is not the same as having the ones you
     * already took.
     *
     * <b>Skipping matters more than copying here.</b> A bulk action is one people press twice,
     * and without a check the second press fills their gallery with a duplicate of everything.
     * So each name is looked for first, in the media store above Android 10 and in the public
     * album below it, and already-there is counted separately from copied so the row can say
     * "nothing new" rather than silently doing nothing.
     *
     * Call from a background thread: this is a byte-for-byte copy of every file in the folder.
     */
    public static BulkResult publishFolderOnRequest(@NonNull Context context, @NonNull File folder,
                                                    final boolean image) {
        if (!BULK_RUNNING.compareAndSet(false, true)) {
            return new BulkResult(0, 0, 0, false, true);
        }
        try {
            return copyFolder(context, folder, image);
        } finally {
            BULK_RUNNING.set(false);
        }
    }

    private static BulkResult copyFolder(@NonNull Context context, @NonNull File folder,
                                         final boolean image) {
        File[] files = folder.listFiles();
        if (files == null) return new BulkResult(0, 0, 0, true, false);
        int copied = 0;
        int skipped = 0;
        int failed = 0;
        for (File file : files) {
            if (file == null || !file.isFile() || file.length() <= 0L) continue;
            String name = file.getName().toLowerCase(java.util.Locale.US);
            boolean wanted = image
                    ? name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    : name.endsWith(".mp4");
            if (!wanted) continue;
            if (alreadyInGallery(context, file.getName(), image)) {
                skipped++;
                continue;
            }
            boolean ok = image
                    ? publishImageOnRequest(context, file)
                    : publishVideoOnRequest(context, file);
            if (ok) copied++; else failed++;
        }
        return new BulkResult(copied, skipped, failed, false, false);
    }

    /** Whether the album already holds a file of this name. Errs towards copying. */
    private static boolean alreadyInGallery(@NonNull Context context, @NonNull String name,
                                            boolean image) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                File album = new File(Environment.getExternalStoragePublicDirectory(
                        image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES),
                        ALBUM);
                return new File(album, name).exists();
            }
            Uri collection = image
                    ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            String relative = (image ? Environment.DIRECTORY_PICTURES
                    : Environment.DIRECTORY_MOVIES) + File.separator + ALBUM + File.separator;
            Cursor cursor = context.getContentResolver().query(
                    collection,
                    new String[]{MediaStore.MediaColumns._ID},
                    MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                            + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                    new String[]{name, relative},
                    null);
            if (cursor == null) return false;
            try {
                return cursor.getCount() > 0;
            } finally {
                cursor.close();
            }
        } catch (Throwable t) {
            // A failed lookup must not stop the copy. The cost of being wrong here is a
            // duplicate, which is visible and deletable; refusing would be neither.
            return false;
        }
    }

    // Back on the method that needs it: every column this touches is API 29. It had drifted
    // onto an unrelated data holder when that holder was inserted between the two, which
    // compiles, suppresses nothing where it matters, and marks the holder as Q-only for every
    // caller.
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
