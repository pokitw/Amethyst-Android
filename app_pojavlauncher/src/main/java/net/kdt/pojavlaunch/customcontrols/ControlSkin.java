package net.kdt.pojavlaunch.customcontrols;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * How an on-screen control is drawn.
 *
 * The layout format has carried a fill, a stroke and a corner radius per button since long before
 * this fork, and every layout anyone has ever shared has its own idea of them. That is why this is
 * a skin applied at draw time rather than a rewrite of the data: turning it on gives a decade of
 * existing layouts the same look without touching a byte of what the user saved, and turning it
 * off gives their colours straight back.
 *
 * The look itself is Minecraft on phones — a light translucent fill so the world stays readable
 * underneath, a dark keyline so the button still reads against snow or sky, and a corner radius
 * that lands between a square and a circle. Nothing here glows.
 */
public final class ControlSkin {
    /** White at 20%: present over dark terrain, not a hole punched in a bright one. */
    public static final int FILL = 0x33FFFFFF;
    /** The keyline. Dark rather than light, because bright backgrounds are the hard case. */
    public static final int STROKE = 0x59000000;
    public static final float STROKE_WIDTH_DP = 1.5f;
    /**
     * Per cent of half the shorter side, which is what {@code ControlData} stores: 100 is a
     * capsule, 0 is a square, and this is the rounded square the Pocket Edition buttons use.
     */
    public static final float CORNER_PERCENT = 34f;
    /** The lift while a finger is down. Brighter than the fill, never a different hue. */
    public static final int PRESS_ALPHA = 0x50;
    /** The joystick is a ring rather than a slab, so it gets its own weights. */
    public static final int JOYSTICK_FILL = 0x1FFFFFFF;
    public static final int JOYSTICK_RING = 0x66FFFFFF;
    public static final float JOYSTICK_RING_DP = 2f;

    private ControlSkin() {}

    public static boolean isPocket() {
        return LauncherPreferences.PREF_CONTROL_POCKET_SKIN;
    }

    public static boolean isGlyphs() {
        return LauncherPreferences.PREF_CONTROL_GLYPHS;
    }

    public static int fill(ControlData data) {
        return isPocket() ? FILL : data.bgColor;
    }

    public static int strokeColor(ControlData data) {
        return isPocket() ? STROKE : data.strokeColor;
    }

    public static float strokeWidthDp(ControlData data) {
        return isPocket() ? STROKE_WIDTH_DP : data.strokeWidth;
    }

    public static float cornerPercent(ControlData data) {
        return isPocket() ? CORNER_PERCENT : data.cornerRadius;
    }
}
