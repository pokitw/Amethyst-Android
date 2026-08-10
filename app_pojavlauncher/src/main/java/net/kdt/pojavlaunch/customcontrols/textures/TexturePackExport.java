package net.kdt.pojavlaunch.customcontrols.textures;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Handing a pack back out, so it can be edited and brought in again.
 *
 * <b>Why a starting point rather than a blank one.</b> The format is three files and a rule about
 * which parts of the picture get stretched, and none of that is discoverable by staring at an empty
 * canvas. Someone who wants Bedrock buttons in a different colour wants Stone with the greys
 * changed, not a specification. So this exports a pack that already works, and the first edit
 * anyone makes is to a face they have already seen on their own buttons.
 *
 * <b>Why it goes to Downloads.</b> The same reason {@link TexturePackImport} has to exist at all:
 * packs live under {@code Android/data}, which no file manager can browse from Android 11, so a
 * copy written beside them would be a copy nobody could open. This is the other half of that
 * problem and it gets the same answer the gallery copy does, through the media store above
 * Android 10 and the public directory below it.
 *
 * The README is the only documentation this format has, and it is deliberately here rather than in
 * the pack folder for exactly the reason above: a file nobody can reach explains nothing.
 */
public final class TexturePackExport {
    private static final String TAG = "TexturePackExport";

    /** What a player starts from when they have not chosen a pack yet. */
    public static final String DEFAULT_TEMPLATE = "Stone";

    private TexturePackExport() {}

    /** What happened, so the caller can say it in the player's words. */
    public enum Status { SAVED, NO_PACK, FAILED }

    public static final class Result {
        @NonNull public final Status status;
        /** On {@link Status#SAVED}: the file name it was written as. */
        @Nullable public final String fileName;

        Result(@NonNull Status status, @Nullable String fileName) {
            this.status = status;
            this.fileName = fileName;
        }
    }

    /**
     * Write one pack out as a zip the importer will take straight back.
     *
     * @param name the pack to copy, which is normally whatever is selected
     */
    @NonNull
    public static Result export(@NonNull Context context, @Nullable String name) {
        String pack = (name == null || name.isEmpty()) ? DEFAULT_TEMPLATE : name;
        byte[] base = ControlTextures.readFile(context, pack, ControlTextures.BASE_FILE);
        if (base == null && !DEFAULT_TEMPLATE.equals(pack)) {
            // The selected style was one of the flat two, or a pack that has since gone missing.
            pack = DEFAULT_TEMPLATE;
            base = ControlTextures.readFile(context, pack, ControlTextures.BASE_FILE);
        }
        if (base == null) return new Result(Status.NO_PACK, null);

        byte[] pressed = ControlTextures.readFile(context, pack, ControlTextures.PRESSED_FILE);
        byte[] meta = ControlTextures.readFile(context, pack, ControlTextures.PACK_FILE);
        String fileName = pack + " pack.zip";
        try {
            OutputStream out = open(context, fileName);
            if (out == null) return new Result(Status.FAILED, null);
            try {
                ZipOutputStream zip = new ZipOutputStream(out);
                write(zip, ControlTextures.BASE_FILE, base);
                if (pressed != null) write(zip, ControlTextures.PRESSED_FILE, pressed);
                if (meta != null) write(zip, ControlTextures.PACK_FILE, meta);
                write(zip, "README.txt", readme(pack).getBytes(Charset.forName("UTF-8")));
                zip.finish();
            } finally {
                out.close();
            }
            finish(context, fileName);
            return new Result(Status.SAVED, fileName);
        } catch (Throwable t) {
            Log.w(TAG, "Could not write the pack out", t);
            return new Result(Status.FAILED, null);
        }
    }

    private static void write(@NonNull ZipOutputStream zip, @NonNull String name,
                              @NonNull byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    /* ------------------------------------------------------------------ where it lands */

    @Nullable private static Uri pendingUri;

    @Nullable
    private static OutputStream open(@NonNull Context context, @NonNull String fileName)
            throws Exception {
        pendingUri = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            // Hidden until every byte is there, the same promise the gallery copy makes about a
            // half-written video: a truncated zip offered to a file manager is a broken import
            // waiting to happen.
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return null;
            pendingUri = uri;
            return context.getContentResolver().openOutputStream(uri);
        }
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!folder.exists() && !folder.mkdirs()) return null;
        return new FileOutputStream(new File(folder, fileName));
    }

    private static void finish(@NonNull Context context, @NonNull String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (pendingUri == null) return;
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(pendingUri, done, null, null);
            pendingUri = null;
            return;
        }
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        MediaScannerConnection.scanFile(context,
                new String[]{new File(folder, fileName).getAbsolutePath()}, null, null);
    }

    /**
     * The whole of the format, in the one place someone will actually find it.
     *
     * Written as instructions rather than as a specification: the reader has a zip open in a file
     * manager and wants to know which file to paint on, not a description of a nine-slice.
     */
    @NonNull
    private static String readme(@NonNull String pack) {
        return "Amethyst X control texture pack\n"
                + "===============================\n"
                + "\n"
                + "This is a copy of the \"" + pack + "\" button style. Edit it, zip the files back\n"
                + "up, and add it in Settings, Controls, Add texture pack.\n"
                + "\n"
                + "FILES\n"
                + "  button.png          The button face. The only file that has to be here.\n"
                + "  button_pressed.png  Optional. Shown while a finger is down. Must be exactly\n"
                + "                      the same size as button.png, or it is ignored.\n"
                + "  pack.json           Optional. Every value below has a working default.\n"
                + "\n"
                + "PACK.JSON\n"
                + "  slice   How many pixels in from each edge the border reaches. 0 stretches the\n"
                + "          whole picture. This is measured in the picture's own pixels.\n"
                + "  smooth  false keeps hard pixel edges when the button is bigger than the\n"
                + "          picture, which is what you want for Minecraft-style art. true is for\n"
                + "          faces with curves in them.\n"
                + "  label   \"light\" or \"dark\". The colour of the text and icon drawn on top.\n"
                + "          Use \"dark\" if your face is pale, or the label will not be readable.\n"
                + "\n"
                + "HOW THE PICTURE IS STRETCHED\n"
                + "  A button can be any shape, so the face is cut into nine pieces using slice.\n"
                + "\n"
                + "      corner | top edge | corner       corners  never stretch\n"
                + "      -------+----------+-------       top and bottom stretch sideways\n"
                + "      left   |  middle  | right        left and right stretch up and down\n"
                + "      -------+----------+-------       the middle stretches both ways\n"
                + "      corner | bottom   | corner\n"
                + "\n"
                + "  So: keep detail in the corners, keep the middle flat, and only vary an edge\n"
                + "  along the direction it is not being stretched in. A bevelled frame around a\n"
                + "  plain interior is the shape this format draws well, which is why every pack\n"
                + "  that ships with the launcher is one.\n"
                + "\n"
                + "LIMITS\n"
                + "  Pictures larger than 1024 pixels a side are refused rather than shrunk, since\n"
                + "  slice is written in source pixels and a resized face would be cut in the wrong\n"
                + "  place. Small is fine: most of the packs here are 16 pixels square.\n"
                + "\n"
                + "  There is no per-button artwork. Every button in a layout wears the same face;\n"
                + "  what tells them apart is the label and the icon on top of it.\n";
    }
}
