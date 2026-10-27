package net.kdt.pojavlaunch.screenshot.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.kdt.pojavlaunch.screenshot.edit.ImageEdit.Orientation;
import net.kdt.pojavlaunch.screenshot.edit.ImageEdit.Plan;
import net.kdt.pojavlaunch.screenshot.edit.ImageEdit.Rect;

/** Drives the shipped ImageEdit. See run.sh. */
public class Harness {

    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<String>();

    /** Deliberately non square and not a multiple of each other, so a swapped axis shows. */
    private static final int[][] SIZES = {{5, 3}, {3, 5}, {1, 7}, {7, 1}, {4, 4}, {1, 1}, {9, 2}};

    private static Orientation[] all() {
        Orientation[] out = new Orientation[8];
        int i = 0;
        for (int r = 0; r < 360; r += 90) {
            out[i++] = new Orientation(r, false);
            out[i++] = new Orientation(r, true);
        }
        return out;
    }

    public static void main(String[] args) {
        mappingIsABijectionOverEveryPixel();
        unmapIsTheExactInverse();
        theConventionIsWhatTheCommentSays();
        displayedSizeSwapsOnlyOnQuarterTurns();
        rotatingFourTimesIsIdentity();
        mirroringTwiceIsIdentity();
        mirrorOnScreenIsHorizontalAtEveryRotation();
        aCropComesBackToTheRightPixels();
        theCropIsClampedNotRefused();
        outputSizeIsWhatThePlayerFramed();
        degenerateInputIsRefusedNotGuessed();
        noOpIsRecognised();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String f : FAILURES) System.out.println("FAIL: " + f);
            System.out.println(FAILURES.size() + " FAILURES");
            System.exit(1);
        }
        System.out.println("all passed");
    }

    /**
     * The strongest check available: every pixel of every size at every orientation lands on a
     * distinct, in bounds displayed pixel. A transform that drops, doubles or overflows shows up
     * here and is invisible in source.
     */
    private static void mappingIsABijectionOverEveryPixel() {
        for (int s = 0; s < SIZES.length; s++) {
            int w = SIZES[s][0];
            int h = SIZES[s][1];
            Orientation[] orientations = all();
            for (int o = 0; o < orientations.length; o++) {
                Orientation orientation = orientations[o];
                int dw = ImageEdit.displayedWidth(w, h, orientation);
                int dh = ImageEdit.displayedHeight(w, h, orientation);
                Set<Integer> seen = new HashSet<Integer>();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int[] p = ImageEdit.mapPoint(x, y, w, h, orientation);
                        boolean inBounds = p[0] >= 0 && p[0] < dw && p[1] >= 0 && p[1] < dh;
                        if (!okay(inBounds, w + "x" + h + " " + orientation + ": source ("
                                + x + "," + y + ") maps outside the " + dw + "x" + dh
                                + " displayed image, to (" + p[0] + "," + p[1] + ")")) return;
                        if (!okay(seen.add(p[1] * dw + p[0]), w + "x" + h + " " + orientation
                                + ": two source pixels map to (" + p[0] + "," + p[1] + ")")) return;
                    }
                }
                check(seen.size() == w * h, w + "x" + h + " " + orientation
                        + ": only " + seen.size() + " of " + (w * h) + " pixels were covered");
            }
        }
    }

    private static void unmapIsTheExactInverse() {
        for (int s = 0; s < SIZES.length; s++) {
            int w = SIZES[s][0];
            int h = SIZES[s][1];
            Orientation[] orientations = all();
            for (int o = 0; o < orientations.length; o++) {
                Orientation orientation = orientations[o];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int[] d = ImageEdit.mapPoint(x, y, w, h, orientation);
                        int[] back = ImageEdit.unmapPoint(d[0], d[1], w, h, orientation);
                        if (!okay(back[0] == x && back[1] == y,
                                w + "x" + h + " " + orientation + ": (" + x + "," + y
                                        + ") round tripped to (" + back[0] + "," + back[1]
                                        + "). unmapPoint is not the inverse of mapPoint.")) return;
                    }
                }
            }
        }
    }

    /**
     * Ground truth written independently of the implementation, which is what the skin UV table
     * does (CLAUDE.md section 14). A formula that is self consistent can still be the wrong
     * formula, and only an outside statement of where a corner goes can say so.
     */
    private static void theConventionIsWhatTheCommentSays() {
        int w = 5;
        int h = 3;

        // No rotation: the top left pixel stays put.
        expect(w, h, new Orientation(0, false), 0, 0, 0, 0, "identity moved the origin");

        // 90 clockwise: the source top left becomes the displayed TOP RIGHT, and the displayed
        // image is h wide, so that is (h-1, 0).
        expect(w, h, new Orientation(90, false), 0, 0, h - 1, 0, "90 cw origin");
        // and the source's top right (w-1,0) becomes the displayed bottom right (h-1, w-1).
        expect(w, h, new Orientation(90, false), w - 1, 0, h - 1, w - 1, "90 cw top right");

        // 180: the origin goes to the opposite corner.
        expect(w, h, new Orientation(180, false), 0, 0, w - 1, h - 1, "180 origin");

        // 270 clockwise (90 anticlockwise): the origin becomes the displayed bottom left.
        expect(w, h, new Orientation(270, false), 0, 0, 0, w - 1, "270 cw origin");

        // Mirror with no rotation: left to right flip, so the origin goes to the top right.
        expect(w, h, new Orientation(0, true), 0, 0, w - 1, 0, "mirror origin");
        expect(w, h, new Orientation(0, true), w - 1, 0, 0, 0, "mirror top right");
    }

    private static void displayedSizeSwapsOnlyOnQuarterTurns() {
        int w = 9;
        int h = 2;
        Orientation[] orientations = all();
        for (int i = 0; i < orientations.length; i++) {
            Orientation o = orientations[i];
            int dw = ImageEdit.displayedWidth(w, h, o);
            int dh = ImageEdit.displayedHeight(w, h, o);
            boolean swapped = o.rotation == 90 || o.rotation == 270;
            check(swapped ? (dw == h && dh == w) : (dw == w && dh == h),
                    o + ": displayed size is " + dw + "x" + dh + ", expected "
                            + (swapped ? h + "x" + w : w + "x" + h));
            check(o.swapsAxes() == swapped, o + ": swapsAxes disagrees with the rotation");
        }
    }

    private static void rotatingFourTimesIsIdentity() {
        Orientation[] orientations = all();
        for (int i = 0; i < orientations.length; i++) {
            Orientation start = orientations[i];
            Orientation o = start;
            for (int turn = 0; turn < 4; turn++) o = o.rotatedRight();
            check(o.equals(start), start + ": four right turns gave " + o);

            o = start;
            for (int turn = 0; turn < 4; turn++) o = o.rotatedLeft();
            check(o.equals(start), start + ": four left turns gave " + o);

            check(start.rotatedRight().rotatedLeft().equals(start),
                    start + ": right then left is not identity");
        }
    }

    private static void mirroringTwiceIsIdentity() {
        Orientation[] orientations = all();
        for (int i = 0; i < orientations.length; i++) {
            Orientation start = orientations[i];
            check(start.mirroredOnScreen().mirroredOnScreen().equals(start),
                    start + ": mirroring twice gave " + start.mirroredOnScreen()
                            .mirroredOnScreen());
        }
    }

    /**
     * The one piece of this file somebody would "simplify" into !mirror and break.
     *
     * The stored mirror is applied BEFORE the rotation, so flipping what the player can see has to
     * turn the rotation around too at 90 and 270, or the flip comes out vertical. Checked by its
     * effect on pixels rather than by its representation: after a screen mirror, the pixel that
     * was displayed at (x,y) must be displayed at (dw-1-x, y).
     */
    private static void mirrorOnScreenIsHorizontalAtEveryRotation() {
        int w = 5;
        int h = 3;
        Orientation[] orientations = all();
        for (int i = 0; i < orientations.length; i++) {
            Orientation before = orientations[i];
            Orientation after = before.mirroredOnScreen();
            int dw = ImageEdit.displayedWidth(w, h, before);
            int dh = ImageEdit.displayedHeight(w, h, before);
            check(ImageEdit.displayedWidth(w, h, after) == dw
                            && ImageEdit.displayedHeight(w, h, after) == dh,
                    before + ": mirroring on screen changed the displayed size, so it was not a "
                            + "horizontal flip");
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int[] was = ImageEdit.mapPoint(x, y, w, h, before);
                    int[] now = ImageEdit.mapPoint(x, y, w, h, after);
                    boolean flipped = now[0] == dw - 1 - was[0] && now[1] == was[1];
                    if (!okay(flipped, before + " -> " + after + ": source (" + x + "," + y
                            + ") was displayed at (" + was[0] + "," + was[1] + ") and is now at ("
                            + now[0] + "," + now[1] + "), which is not a horizontal flip of it. "
                            + "The stored mirror is applied before the rotation, so a screen flip "
                            + "must also turn the rotation around at 90 and 270.")) return;
                }
            }
        }
    }

    /**
     * A crop drawn on screen must select exactly the pixels under it.
     *
     * Checked by taking the set of source pixels whose displayed position falls inside the crop,
     * and asserting the plan's source rectangle is exactly that set's bounding box and has the
     * same area. That is independent of how the plan computes it.
     */
    private static void aCropComesBackToTheRightPixels() {
        for (int s = 0; s < SIZES.length; s++) {
            int w = SIZES[s][0];
            int h = SIZES[s][1];
            Orientation[] orientations = all();
            for (int o = 0; o < orientations.length; o++) {
                Orientation orientation = orientations[o];
                int dw = ImageEdit.displayedWidth(w, h, orientation);
                int dh = ImageEdit.displayedHeight(w, h, orientation);
                for (int left = 0; left < dw; left++) {
                    for (int right = left + 1; right <= dw; right++) {
                        for (int top = 0; top < dh; top++) {
                            for (int bottom = top + 1; bottom <= dh; bottom++) {
                                Rect crop = new Rect(left, top, right, bottom);
                                Plan plan = ImageEdit.plan(w, h, orientation, crop);

                                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
                                int count = 0;
                                for (int y = 0; y < h; y++) {
                                    for (int x = 0; x < w; x++) {
                                        int[] d = ImageEdit.mapPoint(x, y, w, h, orientation);
                                        if (d[0] < left || d[0] >= right) continue;
                                        if (d[1] < top || d[1] >= bottom) continue;
                                        count++;
                                        if (x < minX) minX = x;
                                        if (y < minY) minY = y;
                                        if (x > maxX) maxX = x;
                                        if (y > maxY) maxY = y;
                                    }
                                }
                                Rect expected = new Rect(minX, minY, maxX + 1, maxY + 1);
                                if (!okay(plan.sourceRect.equals(expected),
                                        w + "x" + h + " " + orientation + " crop " + crop
                                                + ": plan selects " + plan.sourceRect
                                                + ", the pixels under it are " + expected)) return;
                                if (!okay(count == plan.sourceRect.width()
                                                * plan.sourceRect.height(),
                                        w + "x" + h + " " + orientation + " crop " + crop
                                                + ": " + count + " pixels under the crop but the "
                                                + "source rect covers "
                                                + (plan.sourceRect.width()
                                                * plan.sourceRect.height()))) return;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void theCropIsClampedNotRefused() {
        int w = 5;
        int h = 3;
        Orientation o = new Orientation(0, false);

        Plan over = ImageEdit.plan(w, h, o, new Rect(-10, -10, 100, 100));
        check(over.isUsable(), "a crop larger than the image was refused rather than clamped");
        check(over.sourceRect.equals(new Rect(0, 0, w, h)),
                "an oversized crop clamped to " + over.sourceRect);

        // A finger dragged up and to the left produces a reversed rectangle.
        Plan reversed = ImageEdit.plan(w, h, o, new Rect(4, 2, 1, 1));
        check(reversed.isUsable(), "a reversed crop was refused; a finger drags both ways");
        check(reversed.sourceRect.equals(new Rect(1, 1, 4, 2)),
                "a reversed crop normalised to " + reversed.sourceRect);

        Plan wholly = ImageEdit.plan(w, h, o, new Rect(50, 50, 60, 60));
        check(!wholly.isUsable(), "a crop entirely off the image was called usable");

        Plan nullCrop = ImageEdit.plan(w, h, o, null);
        check(nullCrop.sourceRect.equals(new Rect(0, 0, w, h)),
                "a null crop did not mean the whole image");
    }

    private static void outputSizeIsWhatThePlayerFramed() {
        int w = 9;
        int h = 2;
        Orientation[] orientations = all();
        for (int i = 0; i < orientations.length; i++) {
            Orientation o = orientations[i];
            int dw = ImageEdit.displayedWidth(w, h, o);
            int dh = ImageEdit.displayedHeight(w, h, o);
            Plan whole = ImageEdit.plan(w, h, o);
            check(whole.outputWidth == dw && whole.outputHeight == dh,
                    o + ": the whole image came out " + whole.outputWidth + "x"
                            + whole.outputHeight + ", expected the displayed size "
                            + dw + "x" + dh);
            if (dw >= 2 && dh >= 1) {
                Rect crop = new Rect(1, 0, dw, dh);
                Plan part = ImageEdit.plan(w, h, o, crop);
                check(part.outputWidth == crop.width() && part.outputHeight == crop.height(),
                        o + ": a " + crop.width() + "x" + crop.height() + " crop came out "
                                + part.outputWidth + "x" + part.outputHeight);
                // The source rectangle has the axes swapped back, which is the whole trick.
                int expectW = o.swapsAxes() ? crop.height() : crop.width();
                check(part.sourceRect.width() == expectW,
                        o + ": the source rect is " + part.sourceRect.width()
                                + " wide, expected " + expectW);
            }
        }
    }

    private static void degenerateInputIsRefusedNotGuessed() {
        check(!ImageEdit.plan(0, 10, new Orientation(0, false)).isUsable(),
                "a zero width image was called usable");
        check(!ImageEdit.plan(10, 0, new Orientation(0, false)).isUsable(),
                "a zero height image was called usable");
        check(!ImageEdit.plan(-5, -5, new Orientation(0, false)).isUsable(),
                "a negative size was called usable");
        try {
            Plan p = ImageEdit.plan(5, 3, null, null);
            check(p.isUsable() && p.orientation.isIdentity(),
                    "a null orientation did not default to the identity");
        } catch (Throwable t) {
            check(false, "a null orientation threw " + t.getClass().getSimpleName());
        }
        // An arbitrary angle is snapped rather than accepted: only right angles are permutations.
        check(new Orientation(45, false).rotation == 0, "45 degrees was not snapped down");
        check(new Orientation(-90, false).rotation == 270, "-90 did not normalise to 270");
        check(new Orientation(450, false).rotation == 90, "450 did not normalise to 90");
    }

    private static void noOpIsRecognised() {
        check(ImageEdit.plan(5, 3, new Orientation(0, false)).isNoOp(5, 3),
                "the identity plan was not recognised as a no op");
        check(!ImageEdit.plan(5, 3, new Orientation(90, false)).isNoOp(5, 3),
                "a rotation was called a no op");
        check(!ImageEdit.plan(5, 3, new Orientation(0, true)).isNoOp(5, 3),
                "a mirror was called a no op");
        check(!ImageEdit.plan(5, 3, new Orientation(0, false), new Rect(1, 0, 5, 3)).isNoOp(5, 3),
                "a crop was called a no op");
    }

    /* ------------------------------------------------------------------ plumbing */

    private static void expect(int w, int h, Orientation o, int sx, int sy,
                               int dx, int dy, String what) {
        int[] got = ImageEdit.mapPoint(sx, sy, w, h, o);
        check(got[0] == dx && got[1] == dy, what + ": (" + sx + "," + sy + ") -> ("
                + got[0] + "," + got[1] + "), expected (" + dx + "," + dy + ")");
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }

    private static boolean okay(boolean condition, String message) {
        check(condition, message);
        return condition;
    }
}
