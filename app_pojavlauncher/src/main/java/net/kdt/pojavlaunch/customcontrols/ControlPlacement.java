package net.kdt.pojavlaunch.customcontrols;

/**
 * Where a control the player has just added should land.
 *
 * Every new control used to be placed at the centre of the screen, which is right for the first
 * one and wrong for the second: two added in a row sit exactly on top of each other, so the lower
 * one cannot be seen, cannot be dragged, and cannot be told from a single button at all. Adding
 * three and arranging them was therefore three trips through the sheet with a drag in between,
 * which is the opposite of what adding a control should cost.
 *
 * <p>No Android imports, deliberately, so {@code scripts/placesim} can drive the real thing
 * rather than a transcription of it (16.25). Everything here is in one pixel space and the caller
 * decides which.
 */
public final class ControlPlacement {
    private ControlPlacement() {}

    /**
     * How far out the search goes before it gives up and stacks.
     *
     * A ring is one button plus a gap wide, so 24 of them is further than any phone is across in
     * either direction. The cap exists because the loop has to terminate on a screen that is
     * genuinely full, not because the number means anything.
     */
    public static final int MAX_RINGS = 24;

    /**
     * The first free spot on a lattice centred on the screen, as {x, y}.
     *
     * <p><b>The first candidate is the exact centre</b>, so a player adding one control to an
     * empty area gets the obvious answer and nothing clever. Only when that is taken does the
     * search move, outwards a ring at a time, one button and one gap per step.
     *
     * <p><b>Candidates are clamped to the screen before they are tested</b>, which is what makes
     * the result safe rather than merely tidy: a control placed off the edge in the editor is one
     * that cannot be grabbed back, and that is the same loss the resize grip's floor exists to
     * prevent. Clamping two candidates onto the same spot is possible and harmless, since an
     * occupied spot is simply rejected again.
     *
     * <p><b>A full screen falls back to the centre</b> rather than to the last thing tried. Being
     * on top of something is recoverable with one drag; being somewhere the player would not
     * think to look is not.
     *
     * @param screenW the area to place within
     * @param screenH the area to place within
     * @param w       the new control's size
     * @param h       the new control's size
     * @param gap     how far apart the lattice sits the controls
     * @param taken   {x, y, w, h} for each control already placed; null entries are ignored
     * @return a fresh {x, y}, always inside the area where the area is big enough to hold it
     */
    public static float[] freeSpot(float screenW, float screenH, float w, float h, float gap,
                                   float[][] taken) {
        float maxX = Math.max(0f, screenW - w);
        float maxY = Math.max(0f, screenH - h);
        float baseX = clamp(centre(screenW, w), maxX);
        float baseY = clamp(centre(screenH, h), maxY);
        float stepX = Math.max(1f, w + gap);
        float stepY = Math.max(1f, h + gap);

        for (int ring = 0; ring <= MAX_RINGS; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    // Only the edge of this ring: the inside was covered by a smaller one.
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                    float x = clamp(baseX + dx * stepX, maxX);
                    float y = clamp(baseY + dy * stepY, maxY);
                    if (isFree(x, y, w, h, taken)) return new float[]{x, y};
                }
            }
        }
        return new float[]{baseX, baseY};
    }

    /** Whether a rectangle here would touch nothing already placed. */
    public static boolean isFree(float x, float y, float w, float h, float[][] taken) {
        if (taken == null) return true;
        for (float[] other : taken) {
            if (other == null || other.length < 4) continue;
            if (overlaps(x, y, w, h, other[0], other[1], other[2], other[3])) return false;
        }
        return true;
    }

    /**
     * Plain rectangle intersection, touching edges allowed.
     *
     * Touching has to be allowed or the lattice would reject its own spacing: a step of exactly
     * one button plus a zero gap puts the next candidate's edge on the previous one's, and a
     * strict test there would walk the whole ring looking for a spot that is already correct.
     */
    private static boolean overlaps(float ax, float ay, float aw, float ah,
                                    float bx, float by, float bw, float bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }

    private static float centre(float screen, float size) {
        return (screen - size) / 2f;
    }

    private static float clamp(float value, float max) {
        if (value < 0f) return 0f;
        return Math.min(value, max);
    }
}
