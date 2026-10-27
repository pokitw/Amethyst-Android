package net.kdt.pojavlaunch.screenshot.edit;

/**
 * The geometry of cropping, rotating and mirroring a screenshot.
 *
 * Android's {@code Bitmap.createBitmap(src, x, y, w, h, matrix, filter)} crops in <b>source</b>
 * coordinates and then applies the matrix. A player, meanwhile, draws a crop box on the image <b>as
 * they can see it</b>, which is already rotated. So something has to carry a rectangle from
 * displayed space back to source space, and that something is this file rather than the view layer,
 * because getting it wrong produces a picture that is mirrored, or rotated the wrong way, or
 * cropped somewhere else entirely, and <b>none of those are visible in source</b>. The skin atlas
 * taught this project the same lesson: a leg wearing a sleeve is invisible in the code and obvious
 * in a render (§14), so the table is stated once and checked against independently written ground
 * truth.
 *
 * <p>Plain Java with no Android imports, so {@code scripts/imageeditsim} compiles this exact file
 * and drives it. The check it carries is a brute force one: every pixel of several small images is
 * mapped and unmapped, which is the only kind of verification a transform like this deserves.
 *
 * <h2>The convention, stated once</h2>
 *
 * There are eight ways to lay a rectangle on the plane, and the only thing that matters is picking
 * one description and keeping it. Here:
 *
 * <ul>
 *   <li>Rotation is <b>clockwise degrees</b>, one of 0, 90, 180, 270.</li>
 *   <li>Mirroring is a <b>horizontal flip applied before the rotation</b>. Before, not after:
 *       flip-then-rotate and rotate-then-flip are different operations for 90 and 270, so the
 *       order is part of the definition and not an implementation detail.</li>
 *   <li>{@link #mapPoint} takes a source pixel to where it appears on screen.
 *       {@link #unmapPoint} is its exact inverse.</li>
 * </ul>
 */
public final class ImageEdit {

    private ImageEdit() {}

    /** A half open rectangle in pixels. */
    public static final class Rect {
        public final int left;
        public final int top;
        /** Exclusive. */
        public final int right;
        /** Exclusive. */
        public final int bottom;

        public Rect(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int width() {
            return Math.max(0, right - left);
        }

        public int height() {
            return Math.max(0, bottom - top);
        }

        public boolean isEmpty() {
            return width() <= 0 || height() <= 0;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Rect)) return false;
            Rect o = (Rect) other;
            return left == o.left && top == o.top && right == o.right && bottom == o.bottom;
        }

        @Override
        public int hashCode() {
            return ((left * 31 + top) * 31 + right) * 31 + bottom;
        }

        @Override
        public String toString() {
            return "(" + left + "," + top + ")-(" + right + "," + bottom + ")";
        }
    }

    /**
     * How the image is laid out relative to its pixels.
     *
     * Immutable, and composed through {@link #rotatedRight()} and {@link #mirrored()} so the UI's
     * two buttons cannot produce a combination this class has not defined.
     */
    public static final class Orientation {
        /** Clockwise, one of 0, 90, 180, 270. */
        public final int rotation;
        /** A horizontal flip, applied before the rotation. */
        public final boolean mirror;

        public Orientation(int rotation, boolean mirror) {
            int normalised = rotation % 360;
            if (normalised < 0) normalised += 360;
            // Snap to the four right angles rather than accepting an arbitrary degree: every
            // operation here is a pixel permutation, and that is only true at multiples of 90.
            this.rotation = (normalised / 90) * 90;
            this.mirror = mirror;
        }

        public static Orientation identity() {
            return new Orientation(0, false);
        }

        public boolean swapsAxes() {
            return rotation == 90 || rotation == 270;
        }

        public boolean isIdentity() {
            return rotation == 0 && !mirror;
        }

        /** Turn the picture a quarter turn clockwise, as the button does. */
        public Orientation rotatedRight() {
            return new Orientation(rotation + 90, mirror);
        }

        public Orientation rotatedLeft() {
            return new Orientation(rotation + 270, mirror);
        }

        /**
         * Flip the picture as the player sees it, left to right.
         *
         * <b>Not simply {@code !mirror}.</b> The mirror in this representation is applied before
         * the rotation, so flipping the <i>displayed</i> image of a picture that is already turned
         * 90 or 270 degrees has to turn the rotation around as well, or the flip comes out
         * vertical. That is the one piece of this file somebody would simplify and break.
         */
        public Orientation mirroredOnScreen() {
            if (swapsAxes()) {
                return new Orientation(rotation + 180, !mirror);
            }
            return new Orientation(rotation, !mirror);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Orientation)) return false;
            Orientation o = (Orientation) other;
            return rotation == o.rotation && mirror == o.mirror;
        }

        @Override
        public int hashCode() {
            return rotation * 2 + (mirror ? 1 : 0);
        }

        @Override
        public String toString() {
            return rotation + (mirror ? "m" : "");
        }
    }

    /** Everything the Android side needs: crop these pixels, apply this, get this size. */
    public static final class Plan {
        /** In source coordinates, ready for {@code Bitmap.createBitmap}. */
        public final Rect sourceRect;
        public final Orientation orientation;
        public final int outputWidth;
        public final int outputHeight;

        Plan(Rect sourceRect, Orientation orientation, int outputWidth, int outputHeight) {
            this.sourceRect = sourceRect;
            this.orientation = orientation;
            this.outputWidth = outputWidth;
            this.outputHeight = outputHeight;
        }

        public boolean isUsable() {
            return !sourceRect.isEmpty() && outputWidth > 0 && outputHeight > 0;
        }

        /** True when the edit would produce a byte for byte copy, so there is nothing to do. */
        public boolean isNoOp(int sourceWidth, int sourceHeight) {
            return orientation.isIdentity()
                    && sourceRect.left == 0 && sourceRect.top == 0
                    && sourceRect.right == sourceWidth && sourceRect.bottom == sourceHeight;
        }

        @Override
        public String toString() {
            return "crop " + sourceRect + " then " + orientation
                    + " -> " + outputWidth + "x" + outputHeight;
        }
    }

    /* ------------------------------------------------------------------ point mapping */

    /** The displayed width of a source image under an orientation. */
    public static int displayedWidth(int sourceWidth, int sourceHeight, Orientation o) {
        return o.swapsAxes() ? sourceHeight : sourceWidth;
    }

    /** The displayed height of a source image under an orientation. */
    public static int displayedHeight(int sourceWidth, int sourceHeight, Orientation o) {
        return o.swapsAxes() ? sourceWidth : sourceHeight;
    }

    /**
     * Where a source pixel appears on screen. Returns {x, y}.
     *
     * The mirror is applied first, then the clockwise rotation, which is the convention the class
     * comment states. Spot check for 90 degrees: the source's top left corner is the displayed
     * image's top RIGHT corner, and the displayed image is {@code sourceHeight} wide.
     */
    public static int[] mapPoint(int x, int y, int sourceWidth, int sourceHeight, Orientation o) {
        int mx = o.mirror ? sourceWidth - 1 - x : x;
        int my = y;
        switch (o.rotation) {
            case 90:
                return new int[]{sourceHeight - 1 - my, mx};
            case 180:
                return new int[]{sourceWidth - 1 - mx, sourceHeight - 1 - my};
            case 270:
                return new int[]{my, sourceWidth - 1 - mx};
            default:
                return new int[]{mx, my};
        }
    }

    /** The exact inverse of {@link #mapPoint}. Returns {x, y} in source coordinates. */
    public static int[] unmapPoint(int dx, int dy, int sourceWidth, int sourceHeight,
                                   Orientation o) {
        int mx;
        int my;
        switch (o.rotation) {
            case 90:
                my = sourceHeight - 1 - dx;
                mx = dy;
                break;
            case 180:
                mx = sourceWidth - 1 - dx;
                my = sourceHeight - 1 - dy;
                break;
            case 270:
                my = dx;
                mx = sourceWidth - 1 - dy;
                break;
            default:
                mx = dx;
                my = dy;
                break;
        }
        int x = o.mirror ? sourceWidth - 1 - mx : mx;
        return new int[]{x, my};
    }

    /* ------------------------------------------------------------------ the plan */

    /**
     * Carry a crop the player drew on screen back to the pixels it selects.
     *
     * The transform is a multiple of 90 degrees, so it takes axis aligned rectangles to axis
     * aligned rectangles and the bounding box of the two mapped corners is exact rather than an
     * approximation. The crop is clamped to the displayed bounds first: a drag that leaves the
     * image is an ordinary thing for a finger to do, not an error to refuse.
     *
     * @param displayedCrop the selection in the coordinates the player sees, or null for all of it
     */
    public static Plan plan(int sourceWidth, int sourceHeight, Orientation orientation,
                            Rect displayedCrop) {
        Orientation o = orientation == null ? Orientation.identity() : orientation;
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return new Plan(new Rect(0, 0, 0, 0), o, 0, 0);
        }

        int dw = displayedWidth(sourceWidth, sourceHeight, o);
        int dh = displayedHeight(sourceWidth, sourceHeight, o);

        Rect crop = displayedCrop == null ? new Rect(0, 0, dw, dh) : displayedCrop;
        int left = clamp(Math.min(crop.left, crop.right), 0, dw);
        int top = clamp(Math.min(crop.top, crop.bottom), 0, dh);
        int right = clamp(Math.max(crop.left, crop.right), 0, dw);
        int bottom = clamp(Math.max(crop.top, crop.bottom), 0, dh);
        if (right <= left || bottom <= top) {
            return new Plan(new Rect(0, 0, 0, 0), o, 0, 0);
        }

        // Map the two extreme pixels of the half open box, then take the bounding box. Using
        // right-1 and bottom-1 is what keeps this in pixel space: mapping the exclusive edge
        // would reach a pixel that is not in the selection.
        int[] a = unmapPoint(left, top, sourceWidth, sourceHeight, o);
        int[] b = unmapPoint(right - 1, bottom - 1, sourceWidth, sourceHeight, o);
        Rect sourceRect = new Rect(
                Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[0], b[0]) + 1, Math.max(a[1], b[1]) + 1);

        // The output is the crop as the player framed it, which is the whole point of letting
        // them crop in displayed space.
        return new Plan(sourceRect, o, right - left, bottom - top);
    }

    /**
     * The whole image at an orientation, with no crop. The rotate buttons' plan.
     */
    public static Plan plan(int sourceWidth, int sourceHeight, Orientation orientation) {
        return plan(sourceWidth, sourceHeight, orientation, null);
    }

    private static int clamp(int value, int lo, int hi) {
        if (value < lo) return lo;
        if (value > hi) return hi;
        return value;
    }
}
