package net.kdt.pojavlaunch.customcontrols;

import net.kdt.pojavlaunch.customcontrols.textures.ControlTextures;
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
 *
 * <b>One choice, not two.</b> A texture pack is a third answer to the same question the Pocket
 * skin answers — what do my buttons look like — so it is the same setting, with the packs listed
 * after the two built-in styles. Two controls would have needed a documented rule about which
 * wins, and would have left the Pocket switch sitting there doing nothing whenever a pack was on.
 */
public final class ControlSkin {
    /** The style the layout's own saved colours give, which is what upstream has always drawn. */
    public static final String STYLE_LAYOUT = "layout";
    /** The built-in Pocket Edition look. */
    public static final String STYLE_POCKET = "pocket";
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

    /**
     * The texture pack the style names, or null when it names one of the two built-in styles.
     *
     * A name that no longer has a folder is not an error here — it simply loads nothing, and the
     * buttons come out flat. Settings keeps showing the missing name so it is obvious what
     * happened; the game process never rewrites the preference, because both processes cache the
     * whole preference file and the launcher would be the one to lose the change.
     */
    public static String texturePack() {
        String style = LauncherPreferences.PREF_CONTROL_STYLE;
        if (style == null || STYLE_LAYOUT.equals(style) || STYLE_POCKET.equals(style)) return null;
        return style;
    }

    /**
     * Whether a texture pack is actually drawing.
     *
     * Not the same as one being selected: a pack whose folder has been deleted, or whose face
     * will not decode, leaves this false and the buttons fall back to the flat skin rather than
     * to nothing.
     */
    public static boolean isTextured() {
        return ControlTextures.isActive();
    }

    /**
     * Whether the built-in Pocket look applies.
     *
     * False while a texture is drawing, because the fill and the keyline are exactly what the
     * texture has replaced. The corner radius still comes through {@link #cornerPercent} for the
     * things a texture does not cover.
     */
    public static boolean isPocket() {
        return !isTextured() && STYLE_POCKET.equals(LauncherPreferences.PREF_CONTROL_STYLE);
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
