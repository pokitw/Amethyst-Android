package net.kdt.pojavlaunch.sodium;

import java.util.Locale;

/**
 * Whether Sodium can run on the renderer a profile is set to.
 *
 * <b>The launcher used to answer this with a flat no, and then lost the way past it.</b> A Sodium
 * family mod in the folder opened a dialog whose only button was "Delete Sodium and Launch": the
 * preference that skipped it survived the Compose settings rewrite and the switch that set it did
 * not, so the dialog became a dead end and Sodium became unrunnable. That is the whole of the bug
 * the player reported, and it was this fork's own doing rather than anything Sodium or upstream
 * decided.
 *
 * <p><b>The flat no was never the right answer either, because the question depends on the
 * renderer.</b> Sodium needs an OpenGL 3.3 core context (its own README asks for 4.5 and says 3.3
 * is what Minecraft itself requires). Of the renderers this launcher actually ships, exactly one is
 * a real OpenGL implementation rather than a translation layer, and on that one Sodium is a mod
 * like any other. Asking "do you have Sodium" and refusing is answering a question nobody asked;
 * asking "can this renderer run it" is answerable, actionable, and usually yes once the renderer is
 * right.
 *
 * <p><b>Nothing here is about whether Sodium is a good idea.</b> It is how shader packs work at
 * all: Iris requires Sodium, so a launcher that cannot run one cannot run the other. The warning
 * that remains is about the renderer, which is a fact, rather than about the mod, which is a
 * preference.
 *
 * <p>Plain Java with no Android types, so {@code scripts/sodiumsim} compiles this exact file and
 * drives it. A wrong verdict here is silent in both directions: too strict and the player is told
 * a working setup is broken, too loose and they are sent into a black screen with no explanation.
 */
public final class SodiumSupport {

    private SodiumSupport() {}

    /**
     * The OpenGL version Sodium will not start below.
     *
     * From Sodium's own README: official support is OpenGL 4.5, and "nearly all graphics cards that
     * are already compatible with Minecraft (which requires OpenGL 3.3) should also work". Recorded
     * as a number because it is the one fact every verdict below is derived from.
     */
    public static final String REQUIRED_GL = "3.3";

    /** What this launcher can say about running Sodium on a given renderer. */
    public enum Verdict {
        /** No Sodium family mod is installed. Nothing to say. */
        ABSENT,
        /** A real OpenGL implementation. Sodium is an ordinary mod here. */
        SUPPORTED,
        /** A translation layer that may or may not reach what Sodium needs. Worth a word. */
        UNTESTED,
        /** Below Sodium's floor. It will not work, and there is a renderer that will. */
        UNSUPPORTED
    }

    /* ------------------------------------------------------------------ renderer ids */

    /**
     * Mesa's Zink on top of Vulkan, by way of Kopper.
     *
     * The only renderer here that is a real OpenGL implementation: the game's GL calls are
     * executed by Mesa and handed to the device's Vulkan driver, so the context reports whatever
     * Mesa can build on that driver, which on current Adreno and Mali is far above Sodium's floor.
     * Every other option translates GL to GL ES and is bounded by what GL ES exposes.
     */
    public static final String ZINK_KOPPER = "opengles3_desktopgl_zink_kopper";

    /** Mesa's Zink over OSMesa. Shipped by upstream, and not vendored in this build. */
    public static final String ZINK_OSMESA = "vulkan_zink";

    /** A GL to GL ES translator aimed at 1.17 and later. Higher than gl4es reaches, short of Mesa. */
    public static final String MOBILEGLUES = "opengles_mobileglues";

    /** gl4es, shipped here as the Krypton Wrapper. GL 2.1 class, and the launcher's default. */
    public static final String GL4ES = "opengles2";

    /** The proprietary GL ES 3 wrapper. Shipped by upstream, and not vendored in this build. */
    public static final String LTW = "opengles3_ltw";

    /* ------------------------------------------------------------------ detection */

    /**
     * The mods whose presence makes this question worth asking.
     *
     * <b>Deliberately narrower than what {@code Tools.deleteSodiumMods} removes.</b> Those are two
     * different questions: this one is "is there a Sodium family renderer here", so it lists only
     * the renderer replacements themselves, while deletion also takes the things that depend on
     * one (Iris, Indium, Monocle and the rest) because leaving a dependent behind without its
     * dependency is a crash on startup rather than a tidy removal.
     *
     * {@code "beddium"} rather than {@code "embeddium"} on purpose: the match is a substring, and
     * the mod ships as both {@code embeddium} and {@code emberdium} spellings across its forks.
     */
    public static final String[] FAMILY = {
            "sodium", "beddium", "rubidium", "xenon", "celeritas", "vintagium"
    };

    /**
     * Whether a jar file name looks like a Sodium family renderer mod.
     *
     * The predicate lives here rather than at the call site so {@code scripts/sodiumsim} drives
     * the real thing. Substring rather than equality because a jar is named for its build
     * ({@code sodium-fabric-0.6.13+mc1.21.4.jar}), and lower-cased on both sides because the
     * capitalisation differs between loaders and between forks.
     *
     * <p>The risk this carries is over-matching, and it is the direction that costs something: a
     * missed mod means the player is asked nothing and lands where they already were, while a
     * false match asks somebody with no Sodium at all to change their renderer. That is why the
     * list is six specific names and not a pattern.
     */
    public static boolean matchesFamily(String fileName) {
        if (fileName == null) return false;
        String name = fileName.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jar")) return false;
        for (String candidate : FAMILY) {
            if (name.contains(candidate)) return true;
        }
        return false;
    }

    /**
     * What to say about Sodium on this renderer.
     *
     * @param rendererId   the profile's renderer, or null when it follows the launcher default
     * @param sodiumIsHere whether a Sodium family mod is in the profile's mods folder
     */
    public static Verdict verdict(String rendererId, boolean sodiumIsHere) {
        if (!sodiumIsHere) return Verdict.ABSENT;
        String id = normalise(rendererId);

        // Both Zinks are Mesa, so both are real OpenGL whatever the presentation path underneath.
        if (ZINK_KOPPER.equals(id) || ZINK_OSMESA.equals(id)) return Verdict.SUPPORTED;

        // Reaches far enough for 1.17 and later, which means a core context and most of what
        // Sodium asks for, but it is still a translator and it is not the one people report
        // shaders working on. Said as uncertainty rather than as a refusal.
        if (MOBILEGLUES.equals(id)) return Verdict.UNTESTED;

        // gl4es tops out around GL 2.1 with extensions, which is below Sodium's floor by two whole
        // versions. This is the one case where "it will not work" is a fact rather than a worry.
        if (GL4ES.equals(id)) return Verdict.UNSUPPORTED;

        // LTW, and anything a future build adds. Unknown is not a refusal: a renderer this class
        // has never heard of is one it has no business condemning.
        return Verdict.UNTESTED;
    }

    /**
     * The renderer to offer instead, or null when there is nothing better to suggest.
     *
     * Only ever Kopper Zink, and only when the caller says it is actually available: the device
     * has to have Vulkan, and {@code Tools.getCompatibleRenderers} is what knows that. A dialog
     * offering a renderer the device cannot run would be worse than one offering nothing.
     */
    public static String betterRenderer(String rendererId, boolean kopperAvailable) {
        if (!kopperAvailable) return null;
        String id = normalise(rendererId);
        if (ZINK_KOPPER.equals(id) || ZINK_OSMESA.equals(id)) return null;
        return ZINK_KOPPER;
    }

    /**
     * Whether this verdict is worth stopping the player for.
     *
     * Supported is silent, which is the point of the whole class: somebody who has already put
     * their profile on Zink has answered the question and must not be asked again on every launch.
     */
    public static boolean shouldWarn(Verdict verdict) {
        return verdict == Verdict.UNTESTED || verdict == Verdict.UNSUPPORTED;
    }

    /**
     * Whether Iris can be expected to work, which is the question actually being asked.
     *
     * Iris requires Sodium and shader packs lean on compute shaders and storage buffers, so the
     * honest answer is narrower than Sodium's own: a real OpenGL implementation, or nothing. A
     * translator that scrapes past Sodium's floor will not carry a shader pack.
     */
    public static boolean shadersViable(String rendererId) {
        String id = normalise(rendererId);
        return ZINK_KOPPER.equals(id) || ZINK_OSMESA.equals(id);
    }

    private static String normalise(String rendererId) {
        if (rendererId == null) return "";
        return rendererId.trim().toLowerCase(Locale.ROOT);
    }
}
