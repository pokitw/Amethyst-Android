package net.kdt.pojavlaunch.sodium;

import java.util.Locale;

/**
 * Whether Sodium can run on the renderer a profile is set to, <b>on this device</b>.
 *
 * <b>The launcher used to answer this with a flat no, and then lost the way past it.</b> A Sodium
 * family mod in the folder opened a dialog whose only button was "Delete Sodium and Launch": the
 * preference that skipped it survived the Compose settings rewrite and the switch that set it did
 * not, so the dialog became a dead end and Sodium became unrunnable. That is the whole of the bug
 * the player reported, and it was this fork's own doing rather than anything Sodium or upstream
 * decided.
 *
 * <p><b>The flat no was never the right answer either, because the question depends on the
 * renderer.</b> Sodium needs an OpenGL 3.3 context. Of the renderers this launcher ships, one is a
 * real OpenGL implementation rather than a translation layer, and on that one Sodium can be an
 * ordinary mod. Asking "do you have Sodium" and refusing is answering a question nobody asked.
 *
 * <p><b>But the renderer alone is not the answer either, and getting that wrong shipped.</b> The
 * first version of this class returned SUPPORTED for Zink on every device. Zink is Mesa, and Mesa
 * gates the OpenGL version it can advertise on specific Vulkan features of the driver underneath:
 * GL 2.1 needs {@code logicOp}, {@code fillModeNonSolid}, {@code alphaToOne} and
 * {@code shaderClipDistance}, GL 3.2 needs {@code VK_EXT_depth_clip_enable}, and GL 3.3 needs
 * {@code VK_EXT_vertex_attribute_divisor}. The bundled Mesa Turnip carries all of them, so Adreno
 * with Turnip clears Sodium's floor comfortably. A stock ARM Mali Vulkan driver fails several of
 * them outright, and Zink there lands around GL 3.1, which is <i>below</i> Sodium's floor. So the
 * class that existed was silent on precisely the devices that most needed telling, and a Mali
 * player got a crash with no warning where before they had at least had a dialog.
 *
 * <p><b>The forced GL version is why that could not be noticed from inside the game.</b>
 * {@code JREUtils} sets {@code MESA_GL_VERSION_OVERRIDE=4.6COMPAT} for any renderer whose id
 * contains "zink", so the context reports 4.6 whatever the driver can actually do, and Mesa's own
 * documentation says it "may not really implement all the features of the given version". Nothing
 * in this file may ever read a reported GL version for that reason: an overridden number is a
 * promise the launcher made to itself. Everything here is derived from what the <i>device</i> says,
 * through {@code GLInfoUtils} and {@code TurnipDrivers}.
 *
 * <p>Plain Java with no Android types, so {@code scripts/sodiumsim} compiles this exact file and
 * drives it. The Android side that reads the device lives in {@link GpuProfile}, which depends on
 * this class and never the other way round, so the harness never has to stub a framework type.
 */
public final class SodiumSupport {

    private SodiumSupport() {}

    /**
     * The OpenGL version Sodium will not start below.
     *
     * From Sodium's own README: official support is OpenGL 4.5, and "nearly all graphics cards that
     * are already compatible with Minecraft (which requires OpenGL 3.3) should also work".
     */
    public static final String REQUIRED_GL = "3.3";

    /**
     * What this launcher can say about running Sodium on a given renderer and device.
     *
     * Three levels of evidence, deliberately, because they are three different claims:
     * {@link #VERIFIED} has positive evidence for, {@link #INCOMPATIBLE} has positive evidence
     * against, and {@link #UNTESTED} has neither. The names say which, because the previous
     * spelling of VERIFIED was "SUPPORTED", which reads as a promise about the device when all it
     * ever meant was "this renderer is the real OpenGL one".
     */
    public enum Verdict {
        /** No Sodium family mod is installed. Nothing to say. */
        ABSENT,
        /**
         * Positive evidence this configuration clears Sodium's floor. Nothing is asked.
         *
         * Reached only by Zink on an Adreno GPU with a Turnip driver actually being loaded, which
         * is the one combination whose Vulkan feature set is known here: the Turnip shipped in
         * this APK carries every extension Mesa gates GL 3.3 on.
         */
        VERIFIED,
        /** Neither evidence for nor against. Worth a word, never a refusal. */
        UNTESTED,
        /** Positive evidence it cannot work, and there may be a renderer that can. */
        INCOMPATIBLE
    }

    /**
     * Why a verdict is not {@link Verdict#VERIFIED}, so the warning can say something useful.
     *
     * A generic "this might not work" teaches a player to dismiss the dialog. Each of these maps to
     * one sentence naming the actual obstacle, and each one is a different thing to do about it.
     */
    public enum Reason {
        /** Nothing to report: no Sodium, or the configuration is verified. */
        NONE,
        /** The device reports no Vulkan at all, so Zink has nothing to run on. */
        NO_VULKAN,
        /** A GL 2.1 class translator, or one backed by a GL ES 2 driver. Below the floor. */
        GL_TOO_OLD,
        /** Zink on a GPU whose Vulkan driver this build cannot vouch for. */
        DRIVER_UNPROVEN,
        /** Adreno, but the system Vulkan driver was chosen over Turnip. */
        TURNIP_OFF,
        /** A GL to GL ES translator that may or may not reach the floor. */
        TRANSLATOR,
        /** A renderer id this build has never heard of. */
        RENDERER_UNKNOWN
    }

    /* ------------------------------------------------------------------ renderer ids */

    /**
     * Mesa's Zink on top of Vulkan, by way of Kopper.
     *
     * The only renderer here that is a real OpenGL implementation: the game's GL calls are executed
     * by Mesa and handed to the device's Vulkan driver. What the resulting context can actually do
     * is therefore a fact about that Vulkan driver, which is why this id alone is not a verdict.
     */
    public static final String ZINK_KOPPER = "opengles3_desktopgl_zink_kopper";

    /**
     * Mesa's Zink over OSMesa. Shipped by upstream, and not vendored in this build.
     *
     * {@code MainActivity} remaps a profile holding this id to {@link #ZINK_KOPPER} before the
     * game starts, so both are treated as Zink here.
     */
    public static final String ZINK_OSMESA = "vulkan_zink";

    /** A GL to GL ES translator aimed at 1.17 and later. Higher than gl4es reaches. */
    public static final String MOBILEGLUES = "opengles_mobileglues";

    /** gl4es, shipped here as the Krypton Wrapper. GL 2.1 class, and the launcher's default. */
    public static final String GL4ES = "opengles2";

    /** The proprietary GL ES 3 wrapper. Shipped by upstream, and not vendored in this build. */
    public static final String LTW = "opengles3_ltw";

    /* ------------------------------------------------------------------ the device */

    /**
     * The GPU families this class tells apart.
     *
     * Only {@link #ADRENO} changes a verdict, and only because a Turnip driver is bundled for it.
     * The rest are separated so a warning can name the chip and so that a future build which
     * learns something about one of them has a place to put it; they are all treated alike today.
     */
    public enum GpuFamily { ADRENO, MALI, POWERVR, XCLIPSE, OTHER, UNKNOWN }

    /**
     * What the launcher managed to read about the device's graphics stack.
     *
     * Every field is something the device was asked, never something the launcher told it. In
     * particular there is deliberately no "reported GL version" field: on a Zink renderer that
     * number is {@code MESA_GL_VERSION_OVERRIDE} and says nothing about the driver.
     */
    public static final class Gpu {

        /** From the GL ES driver's own vendor and renderer strings. */
        public final GpuFamily family;

        /**
         * The GL ES major version the device will actually give a current context, or 0 if unknown.
         *
         * Verified rather than parsed: {@code GLInfoUtils} creates a context and makes it current
         * before believing the number, which exists precisely because some Mali drivers hand out a
         * GL ES 3 context that cannot be made current. It bounds the GL ES <i>translators</i> only;
         * Zink does not go through GL ES at all, so it must not gate Zink.
         */
        public final int glesMajor;

        /** Whether the device reports Vulkan. Zink cannot run without it. */
        public final boolean vulkan;

        /**
         * Whether a Turnip driver will actually be loaded for this launch.
         *
         * Must be computed the same way {@code JREUtils} decides it, which is
         * {@code GLInfo.isAdreno() && TurnipDrivers.resolve(...).loadTurnip}: a verdict that
         * assumed Turnip where the launch will not load it would be the original bug again with a
         * different cause.
         */
        public final boolean turnipActive;

        public Gpu(GpuFamily family, int glesMajor, boolean vulkan, boolean turnipActive) {
            this.family = family == null ? GpuFamily.UNKNOWN : family;
            this.glesMajor = Math.max(0, glesMajor);
            this.vulkan = vulkan;
            this.turnipActive = turnipActive;
        }

        /**
         * What to use when the device could not be read at all.
         *
         * Vulkan is assumed <b>present</b> and Turnip <b>absent</b>, which is the pairing that
         * condemns nothing and verifies nothing: an unreadable device lands on UNTESTED rather
         * than being told its renderer cannot work. That is the same asymmetry
         * {@code VersionPredicate} and {@code Tools.compareSHA1} encode, and the reason an
         * unreadable reading must not be read as a reading of zero.
         */
        public static Gpu unknown() {
            return new Gpu(GpuFamily.UNKNOWN, 0, true, false);
        }
    }

    /**
     * Classify a GPU from the two strings its GL ES driver reports.
     *
     * <b>The Adreno test is deliberately identical to {@code GLInfoUtils.GLInfo.isAdreno()}</b>,
     * which is the predicate {@code JREUtils} gates Turnip loading on. If this were looser, a
     * device could be called Adreno here, be promised Turnip, and not get it.
     */
    public static GpuFamily familyOf(String vendor, String renderer) {
        if (renderer == null) return GpuFamily.UNKNOWN;
        // Mirrors GLInfo.isAdreno(): renderer names the chip, vendor is matched exactly.
        if (renderer.contains("Adreno") && "Qualcomm".equals(vendor)) return GpuFamily.ADRENO;
        String lower = renderer.toLowerCase(Locale.ROOT);
        if (lower.contains("mali")) return GpuFamily.MALI;
        if (lower.contains("powervr")) return GpuFamily.POWERVR;
        if (lower.contains("xclipse")) return GpuFamily.XCLIPSE;
        // An Adreno string that failed the vendor match above is not treated as Adreno, because
        // Turnip will not load for it, but it is not nothing either.
        if (lower.contains("adreno")) return GpuFamily.OTHER;
        if (lower.contains("unknown") || lower.isEmpty()) return GpuFamily.UNKNOWN;
        return GpuFamily.OTHER;
    }

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

    /* ------------------------------------------------------------------ the verdict */

    /** Both Zinks are Mesa, whatever the presentation path underneath. */
    private static boolean isZink(String id) {
        return ZINK_KOPPER.equals(id) || ZINK_OSMESA.equals(id);
    }

    /** A translator whose ceiling is the device's own GL ES driver. */
    private static boolean isGlesTranslator(String id) {
        return MOBILEGLUES.equals(id) || LTW.equals(id);
    }

    /**
     * What to say about Sodium on this renderer and this device.
     *
     * @param rendererId   the profile's renderer, or null when it follows the launcher default
     * @param sodiumIsHere whether a Sodium family mod is in the profile's mods folder
     * @param gpu          what was read off the device; pass {@link Gpu#unknown()} when nothing
     *                     could be read, never a made up one
     */
    public static Verdict verdict(String rendererId, boolean sodiumIsHere, Gpu gpu) {
        if (!sodiumIsHere) return Verdict.ABSENT;
        String id = normalise(rendererId);
        Gpu device = gpu == null ? Gpu.unknown() : gpu;

        // gl4es tops out around GL 2.1 with extensions, two whole versions below the floor. True
        // of every device, so no GPU dimension enters here.
        if (GL4ES.equals(id)) return Verdict.INCOMPATIBLE;

        if (isZink(id)) {
            // Detected, not assumed: Zink is Mesa on Vulkan, so no Vulkan is a hard no. Note
            // Tools.getCompatibleRenderers only filters ids containing "vulkan", and the Kopper id
            // does not, so this renderer IS offered on a device without Vulkan and the dialog is
            // the only thing that will say otherwise.
            if (!device.vulkan) return Verdict.INCOMPATIBLE;
            // The one configuration with positive evidence. Turnip must actually be loading, not
            // merely be available, because the user can choose the system driver instead.
            if (device.family == GpuFamily.ADRENO && device.turnipActive) return Verdict.VERIFIED;
            // Everything else, Mali included, is UNTESTED and not INCOMPATIBLE. The evidence that
            // a stock Mali driver falls short is real but it is evidence about driver versions in
            // general, and the feature set cannot be queried from here, so condemning a specific
            // device would be claiming to know something this build cannot read.
            return Verdict.UNTESTED;
        }

        if (isGlesTranslator(id)) {
            // A GL to GL ES translator cannot expose more than its backend, so a device that will
            // not give a current GL ES 3 context cannot reach GL 3.3 through one. Detected, and
            // the only place glesMajor is decisive. Zero means unknown and decides nothing.
            if (device.glesMajor > 0 && device.glesMajor < 3) return Verdict.INCOMPATIBLE;
            return Verdict.UNTESTED;
        }

        // An id this class has never heard of is one it has no business condemning.
        return Verdict.UNTESTED;
    }

    /**
     * Why the player is being warned, so the dialog can name the obstacle.
     *
     * Computed beside {@link #verdict} rather than inside it so the verdict stays a single value
     * that callers can switch on, and so a new reason cannot change an existing verdict.
     */
    public static Reason reasonFor(String rendererId, boolean sodiumIsHere, Gpu gpu) {
        Verdict verdict = verdict(rendererId, sodiumIsHere, gpu);
        if (verdict == Verdict.ABSENT || verdict == Verdict.VERIFIED) return Reason.NONE;

        String id = normalise(rendererId);
        Gpu device = gpu == null ? Gpu.unknown() : gpu;

        if (GL4ES.equals(id)) return Reason.GL_TOO_OLD;

        if (isZink(id)) {
            if (!device.vulkan) return Reason.NO_VULKAN;
            // Adreno without Turnip is its own sentence, because the fix is a settings row rather
            // than a different renderer.
            if (device.family == GpuFamily.ADRENO) return Reason.TURNIP_OFF;
            return Reason.DRIVER_UNPROVEN;
        }

        if (isGlesTranslator(id)) {
            if (device.glesMajor > 0 && device.glesMajor < 3) return Reason.GL_TOO_OLD;
            return Reason.TRANSLATOR;
        }

        return Reason.RENDERER_UNKNOWN;
    }

    /**
     * The renderer to offer instead, or null when there is nothing honest to suggest.
     *
     * Only ever Kopper Zink, and only when the caller says it is actually in the compatible list
     * and the device reports Vulkan. Offering Zink to a device with no Vulkan would be moving the
     * player from one renderer that cannot work to another.
     *
     * <p>It is still offered on Mali, and that is deliberate: INCOMPATIBLE to UNTESTED is a real
     * improvement in expectation, and the dialog's own text says the driver is unproven rather
     * than promising it will work.
     */
    public static String betterRenderer(String rendererId, boolean kopperAvailable, Gpu gpu) {
        if (!kopperAvailable) return null;
        Gpu device = gpu == null ? Gpu.unknown() : gpu;
        if (!device.vulkan) return null;
        String id = normalise(rendererId);
        if (isZink(id)) return null;
        return ZINK_KOPPER;
    }

    /**
     * Whether this verdict is worth stopping the player for.
     *
     * VERIFIED is silent, which is the point of the whole class: somebody whose configuration has
     * positive evidence behind it must not be asked again on every launch.
     */
    public static boolean shouldWarn(Verdict verdict) {
        return verdict == Verdict.UNTESTED || verdict == Verdict.INCOMPATIBLE;
    }

    /**
     * Whether shader packs can be expected to work, which is the question actually being asked.
     *
     * Defined as "the Sodium verdict is VERIFIED", which makes it exactly as strong as the
     * evidence and no stronger. Iris requires Sodium, and a shader pack on top needs compute
     * shaders and storage buffers, so anywhere Sodium itself is only UNTESTED a shader pack is
     * certainly not something to promise.
     *
     * <p>This is the second place the old renderer-only answer was wrong: being true for Zink on
     * every GPU, it <i>removed</i> the shader pack warning on precisely the Mali devices that most
     * needed it.
     */
    public static boolean shadersViable(String rendererId, Gpu gpu) {
        return verdict(rendererId, true, gpu) == Verdict.VERIFIED;
    }

    private static String normalise(String rendererId) {
        if (rendererId == null) return "";
        return rendererId.trim().toLowerCase(Locale.ROOT);
    }
}
