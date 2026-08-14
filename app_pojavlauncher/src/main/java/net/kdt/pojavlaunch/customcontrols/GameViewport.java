package net.kdt.pojavlaunch.customcontrols;

/**
 * Where the game actually sits on the screen.
 *
 * <b>A phone can be too big to play on.</b> On a large device held close, held one-handed, or
 * held while lying down, the far edges of the panel are outside what the player can comfortably
 * see or reach, and Minecraft puts the two things read most often — the hotbar and the health and
 * hunger bars — hard against the bottom edge of its own framebuffer. Nothing in the launcher can
 * move that HUD: it belongs to the game. What the launcher can move is the whole frame the game
 * draws into, and shrinking that brings the HUD in with it, because the HUD is part of the
 * picture rather than something drawn over it.
 *
 * <p>The inset is applied to {@link ControlLayout}, not to the surface alone, and that is the
 * decision the whole feature rests on. The layout holds a {@code dimension_tracker} child that
 * {@code Tools.updateWindowSize} reads to set {@code CallbackBridge.physicalWidth/Height}, which
 * is in turn what control positions, the hotbar-tap strip and the virtual cursor are all measured
 * against. So shrinking the layout moves every one of those in step, for free, and the coordinate
 * space stays internally consistent rather than needing each consumer taught about an offset.
 * {@code MinecraftGLSurface} sizes its framebuffer from its own view bounds for the same reason,
 * so the game renders at the box's aspect ratio rather than being letterboxed or stretched.
 *
 * <p>Uniform on both axes on purpose. The game adapts to any aspect ratio it is given, so an
 * uneven inset would not distort anything, but it would change the field of view as a side effect
 * of a setting about reach, and "the game, smaller" is a promise a player can predict.
 *
 * <p>Plain geometry with no Android types in it, so {@code scripts/viewportsim} can compile this
 * exact file and drive it. Getting an inset wrong is not a crash, it is a game drawn slightly off
 * the screen or a control layout that no longer lines up with it, which is the kind of wrong that
 * is only visible on a device this project has none of.
 */
public final class GameViewport {

    private GameViewport() {}

    /**
     * The smallest the game may be made.
     *
     * Below about half the screen the controls, whose sizes are set in dp and do not shrink with
     * the box, start to crowd each other badly enough that the layout needs rebuilding rather
     * than resizing. A floor is not a recommendation: it is the point past which this stops being
     * the same layout.
     */
    public static final int MIN_PERCENT = 50;
    public static final int MAX_PERCENT = 100;

    /** Nine anchors, read left to right and top to bottom, so index 4 is the middle. */
    public static final int POSITION_COUNT = 9;
    public static final int POSITION_DEFAULT = 4;

    /**
     * Where the game's frame should sit inside the screen.
     *
     * @param screenWidth  the full width available, in pixels
     * @param screenHeight the full height available, in pixels
     * @param percent      how much of each axis the game should take, clamped to
     *                     [{@link #MIN_PERCENT}, {@link #MAX_PERCENT}]
     * @param position     one of the nine anchors, out-of-range values falling back to the middle
     * @return {@code {width, height, left, top}} in pixels
     */
    public static int[] bounds(int screenWidth, int screenHeight, int percent, int position) {
        int safeWidth = Math.max(0, screenWidth);
        int safeHeight = Math.max(0, screenHeight);
        int scale = clamp(percent, MIN_PERCENT, MAX_PERCENT);
        int anchor = (position < 0 || position >= POSITION_COUNT) ? POSITION_DEFAULT : position;

        // Rounded rather than truncated, which is worth a line because it is not what makes 100%
        // come out exact: both do, since the multiply and divide cancel. What rounding buys is
        // the half pixel at every other stop, so the box is the nearest whole pixel to the
        // fraction asked for rather than always the one below it. Neither can exceed the panel,
        // because `scale` is clamped at 100 just above, so there is nothing to clamp afterwards.
        int width = Math.round(safeWidth * scale / 100f);
        int height = Math.round(safeHeight * scale / 100f);

        // Column and row each run 0, 1, 2 for start, middle and end. Multiplying the leftover
        // space by the index and halving lands on 0, half and all of it, which is the whole of
        // the anchoring: no branch per corner, and no corner that can be forgotten.
        int column = anchor % 3;
        int row = anchor / 3;
        int left = (safeWidth - width) * column / 2;
        int top = (safeHeight - height) * row / 2;

        return new int[]{width, height, left, top};
    }

    /** Whether these settings would leave the game exactly where it has always been. */
    public static boolean isFullScreen(int percent, int position) {
        return clamp(percent, MIN_PERCENT, MAX_PERCENT) >= MAX_PERCENT;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
