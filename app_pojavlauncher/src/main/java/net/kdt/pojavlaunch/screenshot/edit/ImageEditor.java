package net.kdt.pojavlaunch.screenshot.edit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.screenshot.ScreenshotPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Carries out an {@link ImageEdit.Plan} on a screenshot file.
 *
 * <b>This class contains no geometry.</b> Which pixels survive, which way round they end up and
 * how big the result is are all answered by {@link ImageEdit}, which is plain Java and driven by
 * {@code scripts/imageeditsim} over every pixel of several images. What is left here is decoding,
 * one call to {@code Bitmap.createBitmap} and an encode, which is the part that needs a device.
 */
public final class ImageEditor {

    private static final String TAG = "ImageEditor";

    /**
     * How many pixels a preview may be.
     *
     * A screenshot is at most the size of the panel it was taken on, but the editor also opens
     * pictures a player imported, and a 6000 pixel wide photograph decoded whole just to be looked
     * at is tens of megabytes for no benefit. Roughly four megapixels is more than any phone can
     * show and decodes in well under a second.
     */
    private static final int PREVIEW_PIXEL_BUDGET = 4_000_000;

    private ImageEditor() {}

    /** The size and format of a picture, read without decoding it. */
    public static final class Info {
        public final int width;
        public final int height;
        public final boolean jpeg;

        Info(int width, int height, boolean jpeg) {
            this.width = width;
            this.height = height;
            this.jpeg = jpeg;
        }

        public boolean isUsable() {
            return width > 0 && height > 0;
        }
    }

    /**
     * Read a picture's dimensions without decoding its pixels.
     *
     * {@code inJustDecodeBounds} reads only the header, so this is cheap enough to call for a row
     * in a list. The format comes from the decoder's own reported MIME type rather than the file
     * extension, because the extension is whatever the file happened to be named.
     */
    public static Info read(@NonNull File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            Log.w(TAG, "could not read " + file.getName(), t);
            return new Info(0, 0, false);
        }
        boolean jpeg = options.outMimeType != null
                && options.outMimeType.toLowerCase(java.util.Locale.ROOT).contains("jpeg");
        return new Info(options.outWidth, options.outHeight, jpeg);
    }

    /**
     * Decode a picture small enough to look at.
     *
     * <b>The preview is downsampled and the plan is not.</b> Every coordinate
     * {@link ImageEdit#plan} works in is a full resolution source pixel, so the UI holds its crop
     * as fractions of the displayed image and multiplies up at the last moment. That keeps the
     * sample factor out of the geometry entirely, which matters because a plan computed against a
     * half size preview and applied to a full size bitmap crops the wrong half of the picture and
     * looks plausible while doing it.
     *
     * <p>Called off the main thread.
     */
    @Nullable
    public static Bitmap preview(@NonNull File file) {
        Info info = read(file);
        if (!info.isUsable()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSizeFor(info.width, info.height);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "out of memory previewing " + file.getName(), e);
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "could not preview " + file.getName(), t);
            return null;
        }
    }

    /**
     * The power of two that brings an image under the preview budget.
     *
     * {@code inSampleSize} is documented to be rounded down to a power of two by the decoder, so
     * computing one directly is what makes the result predictable.
     */
    static int sampleSizeFor(int width, int height) {
        int sample = 1;
        long pixels = (long) width * (long) height;
        while (pixels / ((long) sample * (long) sample) > PREVIEW_PIXEL_BUDGET) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * Apply a plan and write the result to a new file beside the original.
     *
     * <b>The original is never touched</b>, for the same reason the clip editor never overwrites a
     * recording: a screenshot of a thing that happened once cannot be taken again.
     *
     * <b>The format is the source's, not the capture preference's.</b> Saving an edited PNG as a
     * JPEG would quietly throw away both the quality and the alpha the player had, and the format
     * setting is about what a new capture is written as, which is a different question. Only the
     * quality of a JPEG comes from preferences, since there is no other number to use.
     *
     * @return the finished file
     */
    public static File export(@NonNull Context context, @NonNull File file,
                              @NonNull ImageEdit.Plan plan) throws IOException {
        if (!plan.isUsable()) throw new IOException("refusing to export an empty crop");
        Info info = read(file);
        if (!info.isUsable()) throw new IOException("could not read " + file.getName());

        Bitmap source = null;
        Bitmap result = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            // Full resolution on purpose: the plan's coordinates are source pixels.
            source = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (source == null) throw new IOException("could not decode " + file.getName());

            result = apply(source, plan);
            if (result == null) throw new IOException("could not apply the edit");

            File destination = freeName(file, info.jpeg);
            writeBitmap(context, result, destination, info.jpeg);
            return destination;
        } catch (OutOfMemoryError e) {
            throw new IOException("not enough memory to edit a picture this large", e);
        } finally {
            if (result != null && result != source) result.recycle();
            if (source != null) source.recycle();
        }
    }

    /**
     * Crop and orient a bitmap according to a plan.
     *
     * One call does both: {@code createBitmap} with a matrix crops in <b>source</b> coordinates
     * and then transforms, which is exactly the order {@link ImageEdit} documents, so the plan
     * drops straight in with no arithmetic here to get wrong.
     *
     * <p>Filtering is off. Every transform in this editor is a multiple of 90 degrees with an
     * optional flip, which is a permutation of the pixels: there is nothing to interpolate, and
     * asking for a filter would soften a picture that should come out identical.
     */
    @Nullable
    public static Bitmap apply(@NonNull Bitmap source, @NonNull ImageEdit.Plan plan) {
        ImageEdit.Rect rect = plan.sourceRect;
        int left = clamp(rect.left, 0, source.getWidth());
        int top = clamp(rect.top, 0, source.getHeight());
        int right = clamp(rect.right, left, source.getWidth());
        int bottom = clamp(rect.bottom, top, source.getHeight());
        if (right <= left || bottom <= top) return null;

        Matrix matrix = new Matrix();
        // Mirror before rotate, which is the convention ImageEdit states and checks. The scale is
        // about the crop's own centre, so it flips the selected pixels rather than moving them.
        if (plan.orientation.mirror) matrix.postScale(-1f, 1f);
        if (plan.orientation.rotation != 0) {
            matrix.postRotate(plan.orientation.rotation);
        }
        if (matrix.isIdentity()) {
            // createBitmap with an identity matrix returns the source itself, which the caller
            // would then recycle while holding it. A plain crop copy avoids that entirely.
            return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
        }
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top, matrix, false);
    }

    private static void writeBitmap(Context context, Bitmap bitmap, File destination, boolean jpeg)
            throws IOException {
        int quality = 100;
        if (jpeg) {
            try {
                quality = ScreenshotPreferences.load(context).compressQuality();
            } catch (Throwable t) {
                quality = ScreenshotPreferences.DEFAULT_QUALITY;
            }
        }
        Bitmap.CompressFormat format = jpeg
                ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
        OutputStream out = new FileOutputStream(destination);
        boolean written;
        try {
            written = bitmap.compress(format, quality, out);
            out.flush();
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
        if (!written) {
            if (!destination.delete()) Log.w(TAG, "left a failed write at " + destination.getName());
            throw new IOException("could not encode the picture");
        }
    }

    /** A name beside the original that is not taken. */
    static File freeName(@NonNull File source, boolean jpeg) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = jpeg ? ".jpg" : ".png";
        File parent = source.getParentFile();
        for (int n = 1; n < 1000; n++) {
            String candidate = n == 1 ? base + "_edit" + extension
                    : base + "_edit" + n + extension;
            File file = new File(parent, candidate);
            if (!file.exists()) return file;
        }
        return new File(parent, base + "_edit" + System.currentTimeMillis() + extension);
    }

    private static int clamp(int value, int lo, int hi) {
        if (value < lo) return lo;
        if (value > hi) return hi;
        return value;
    }
}
