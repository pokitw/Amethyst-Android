package net.kdt.pojavlaunch.customcontrols.textures;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One button's worth of texture, as a {@link Drawable} the view can wear as its background.
 *
 * <b>The background layer, not {@code onDraw}.</b> A {@code ControlButton} is a {@code TextView}:
 * its own drawing order is background, then the label, then the glyph, then the state wash. A
 * texture painted in {@code onDraw} would either cover the label or leave the old flat background
 * showing through its transparent corners. As the background it also gets the framework's state
 * plumbing for nothing — {@code setActivated} already runs on every press, which refreshes the
 * drawable state, which swaps the pressed face here with no invalidation code of our own.
 *
 * <b>Nothing is allocated while drawing.</b> The nine destination rectangles are recomputed only
 * when the bounds change, the source rectangles belong to the shared {@link ControlTexture}, and
 * the paint and its filters are fields. That is the contract {@code ControlButton.onDraw} already
 * keeps, and this sits in the same per-frame path.
 *
 * <b>Alpha is not ours.</b> A control's opacity is applied by {@code ControlLayout} as whole-view
 * alpha, so a paint alpha here would multiply with it and a half-transparent button would come
 * out at a quarter. The paint stays fully opaque and lets the view do it.
 */
public final class ControlTextureDrawable extends Drawable {
    /** Matches today's press lift: white over the face, at the alpha the flat skin uses. */
    private static final int PRESS_TINT = 0x50FFFFFF;

    @NonNull private final ControlTexture texture;
    @NonNull private final Paint paint = new Paint();
    @NonNull private final Rect[] destination;

    /** Used only when the pack has no pressed face of its own. */
    @Nullable private final ColorFilter pressFilter;
    /** Reused by the toggle overlay so a latched button allocates nothing either. */
    @Nullable private ColorFilter overlayFilter;
    private int overlayColor;

    private boolean pressed;

    public ControlTextureDrawable(@NonNull ControlTexture texture) {
        this.texture = texture;
        this.paint.setAntiAlias(false);
        this.paint.setFilterBitmap(texture.smooth);
        this.paint.setDither(false);
        this.destination = new Rect[texture.source.length];
        for (int i = 0; i < destination.length; i++) destination[i] = new Rect();
        this.pressFilter = texture.pressed != null
                ? null
                : new PorterDuffColorFilter(PRESS_TINT, PorterDuff.Mode.SRC_ATOP);
    }

    /** Pale faces need dark content above them; the pack says which it is. */
    public boolean isDarkLabel() {
        return texture.darkLabel;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(@NonNull int[] state) {
        boolean isPressed = false;
        for (int attribute : state) {
            // The press is published as "activated" rather than "pressed": ControlButton calls
            // setActivated from sendKeyPresses, which is the edge the game actually sees.
            if (attribute == android.R.attr.state_activated) isPressed = true;
        }
        if (isPressed == pressed) return false;
        pressed = isPressed;
        invalidateSelf();
        return true;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        layOut(bounds);
    }

    private void layOut(@NonNull Rect bounds) {
        int width = bounds.width();
        int height = bounds.height();
        if (destination.length == 1) {
            destination[0].set(bounds);
            return;
        }
        int corner = texture.cornerFor(width, height);
        int[] x = {bounds.left, bounds.left + corner, bounds.right - corner, bounds.right};
        int[] y = {bounds.top, bounds.top + corner, bounds.bottom - corner, bounds.bottom};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                destination[row * 3 + column].set(x[column], y[row], x[column + 1], y[row + 1]);
            }
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Bitmap bitmap = pressed && texture.pressed != null ? texture.pressed : texture.base;
        ColorFilter filter = pressed && texture.pressed == null ? pressFilter : null;
        paint.setColorFilter(filter);
        paint.setAlpha(255);
        blit(canvas, bitmap);
    }

    /**
     * Draw the face again in one colour, over itself.
     *
     * This is how a latched toggle is shown. The alternative — the rounded rectangle the flat
     * skin draws — takes its corner radius from the layout's own corner percentage, which is
     * exactly the number a texture has stopped drawing, so it would bleed outside a rounded face
     * or cut across a square one. Drawing the art again through a filter makes the highlight wear
     * whatever silhouette the art has, including its transparent corners.
     */
    public void drawOverlay(@NonNull Canvas canvas, int color, int alpha) {
        if (overlayFilter == null || overlayColor != color) {
            overlayColor = color;
            overlayFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        }
        Bitmap bitmap = pressed && texture.pressed != null ? texture.pressed : texture.base;
        paint.setColorFilter(overlayFilter);
        paint.setAlpha(alpha);
        blit(canvas, bitmap);
        paint.setColorFilter(null);
        paint.setAlpha(255);
    }

    private void blit(@NonNull Canvas canvas, @NonNull Bitmap bitmap) {
        for (int i = 0; i < destination.length; i++) {
            Rect to = destination[i];
            // A band with no width or height is skipped rather than drawn: on a button narrower
            // than two corners the middle column collapses, and Canvas is not obliged to be
            // graceful about an empty or inverted rectangle.
            if (to.isEmpty()) continue;
            Rect from = texture.source[i];
            if (from.isEmpty()) continue;
            // This overload of drawBitmap maps source rect to destination rect and consults no
            // density anywhere. Every other way of drawing a bitmap here would scale by the
            // difference between the bitmap's density and the canvas's, which is not a thing a
            // control's artwork should depend on.
            canvas.drawBitmap(bitmap, from, to, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        // Deliberately ignored. Opacity belongs to the view (see the class comment); honouring it
        // here as well is the double-application that makes a 50% button render at 25%.
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // Also ignored: the filter is this drawable's own way of showing state, and letting the
        // outside set one would fight the press.
    }

    @Override
    public int getOpacity() {
        // Honest rather than optimistic: a pack's corners are usually transparent, and claiming
        // otherwise would let the framework skip drawing what is behind the button.
        return PixelFormat.TRANSLUCENT;
    }
}
