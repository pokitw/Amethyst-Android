package net.kdt.pojavlaunch.customcontrols.textures;

import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One pack's artwork, decoded and measured once.
 *
 * Immutable and shared: every button drawn in this style points at the same two bitmaps, because
 * a layout is nineteen buttons and decoding the same PNG nineteen times would be nineteen copies
 * of it on the heap. The per-button state — bounds, press, tint — lives in
 * {@link ControlTextureDrawable}, one per button, the same split the glyph's {@code mutate()}
 * already makes for the same reason.
 *
 * <b>The nine source rectangles are computed here, not at draw time.</b> They depend only on the
 * bitmap, so recomputing them per draw would be work with a constant answer; the destination
 * rectangles are the ones that move, and those live in the drawable's {@code onBoundsChange}.
 */
public final class ControlTexture {
    /** The face, and the face while a finger is down. The second may be absent. */
    @NonNull public final Bitmap base;
    @Nullable public final Bitmap pressed;

    /**
     * The nine-slice inset, in source pixels, already clamped to something the bitmap can honour.
     * Zero means the whole bitmap stretches, which is right for a texture with no border.
     */
    public final int slice;

    /**
     * Whether to interpolate when scaling.
     *
     * False by default and that is the point: this exists so a 16-pixel Minecraft-shaped face can
     * be blown up to a 60dp button and still have edges. Bilinear filtering turns exactly that
     * into mush.
     */
    public final boolean smooth;

    /** True when the pack's faces are pale, so the label and glyph above them must go dark. */
    public final boolean darkLabel;

    /** Source rectangles, in reading order: the nine cells of the grid, or one when slice is 0. */
    @NonNull public final Rect[] source;

    ControlTexture(@NonNull Bitmap base, @Nullable Bitmap pressed, int slice, boolean smooth,
                   boolean darkLabel) {
        this.base = base;
        this.pressed = pressed;
        this.slice = slice;
        this.smooth = smooth;
        this.darkLabel = darkLabel;
        this.source = buildSource(base.getWidth(), base.getHeight(), slice);
    }

    /**
     * Split a bitmap into the nine cells of a nine-slice grid.
     *
     * Reading order, so an index is {@code row * 3 + column} in both this and the destination
     * array and the two can never be walked differently.
     */
    @NonNull
    static Rect[] buildSource(int width, int height, int slice) {
        if (slice <= 0) return new Rect[]{new Rect(0, 0, width, height)};
        int[] x = {0, slice, width - slice, width};
        int[] y = {0, slice, height - slice, height};
        Rect[] rects = new Rect[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                rects[row * 3 + column] = new Rect(x[column], y[row], x[column + 1], y[row + 1]);
            }
        }
        return rects;
    }

    /**
     * How far in from each edge the corner cells reach, in destination pixels.
     *
     * <b>Scaled by the button, not left at source size.</b> A 6-pixel bevel drawn as 6 physical
     * pixels is a chunky border on a 720p phone and a hairline on a 1440p one, and the same
     * number on a 46px button and a 68px one gives a layout two different-looking bevels. Scaling
     * by the shorter side keeps the proportion the pack author saw, whatever the device and
     * whatever the button-size slider is set to.
     *
     * The scale is rounded to a whole number for unfiltered packs, because a bevel drawn at 2.4×
     * has some source pixels two destination pixels wide and others three, which is exactly the
     * unevenness nearest-neighbour is being asked to avoid.
     */
    public int cornerFor(int destinationWidth, int destinationHeight) {
        if (slice <= 0) return 0;
        int shortestSource = Math.min(base.getWidth(), base.getHeight());
        int shortestDestination = Math.min(destinationWidth, destinationHeight);
        if (shortestSource <= 0 || shortestDestination <= 0) return 0;
        float scale = (float) shortestDestination / shortestSource;
        if (!smooth) scale = Math.max(1f, Math.round(scale));
        else scale = Math.max(1f, scale);
        int corner = Math.round(slice * scale);
        // Two corners cannot be wider than the button they sit in. Without this the left and
        // right cells overlap on a small button and the middle band comes out negative.
        int limit = Math.min(destinationWidth, destinationHeight) / 2;
        return Math.max(0, Math.min(corner, limit));
    }
}
