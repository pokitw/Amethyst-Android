package net.kdt.pojavlaunch.sodium;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kdt.pojavlaunch.sodium.SodiumSupport.Gpu;
import net.kdt.pojavlaunch.sodium.SodiumSupport.GpuFamily;
import net.kdt.pojavlaunch.sodium.SodiumSupport.Reason;
import net.kdt.pojavlaunch.sodium.SodiumSupport.Verdict;

/**
 * Drives the shipped SodiumSupport. See run.sh for what this is for and what it catches.
 */
public class Harness {

    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<>();

    private static final String[] ALL_RENDERERS = {
            SodiumSupport.ZINK_KOPPER, SodiumSupport.ZINK_OSMESA, SodiumSupport.MOBILEGLUES,
            SodiumSupport.GL4ES, SodiumSupport.LTW, "something_new", ""
    };
    private static final GpuFamily[] ALL_FAMILIES = GpuFamily.values();
    private static final int[] ALL_GLES = {0, 1, 2, 3};

    public static void main(String[] args) {
        String root = args.length > 0 ? args[0] : ".";

        theRendererIdsMatchTheResourceArray(root);
        adrenoWithTurnipIsTheVerifiedPath();
        maliOnZinkStillWarns();
        noVulkanIsAHardNo();
        adrenoWithoutTurnipIsItsOwnAnswer();
        aReportedGlVersionCannotDecideZink();
        glesVersionOnlyBoundsTranslators();
        unknownIsNeverCondemnedAndNeverVerified();
        theUnknownDeviceIsTheSafeDefault();
        shadersAreExactlyTheVerifiedSet();
        theSuggestionIsHonest();
        noSodiumIsNeverAWarning();
        everyWarningNamesAnObstacle();
        theWholeMatrixHoldsItsInvariants();
        idsAreTakenAsWritten();
        familyAgreesWithTheTurnipPredicate(root);
        realJarNamesAreMatched();
        unrelatedModsAreNotMatched();
        theFamilyListIsWellFormed();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String failure : FAILURES) System.out.println("FAIL: " + failure);
            System.out.println(FAILURES.size() + " FAILURES");
            System.exit(1);
        }
        System.out.println("all passed");
    }

    /* ------------------------------------------------------------------ device fixtures */

    /** A Snapdragon with the bundled Mesa Turnip actually loading. The one verified case. */
    private static Gpu adrenoTurnip() {
        return new Gpu(GpuFamily.ADRENO, 3, true, true);
    }

    /** The same chip with "system driver" chosen in Performance settings. */
    private static Gpu adrenoSystem() {
        return new Gpu(GpuFamily.ADRENO, 3, true, false);
    }

    /**
     * A real Mali, from the capability report this whole fix came from.
     *
     * Mali-G78 on the stock ARM Vulkan driver fails three of Zink's six GL 2.1 base requirements
     * and lacks the extensions Mesa gates GL 3.2 and 3.3 on, landing around GL 3.1. Turnip is
     * never loaded here, because JREUtils only loads it inside an isAdreno() branch.
     */
    private static Gpu mali() {
        return new Gpu(GpuFamily.MALI, 3, true, false);
    }

    private static Gpu noVulkan() {
        return new Gpu(GpuFamily.OTHER, 3, false, false);
    }

    /* ------------------------------------------------------------------ the core table */

    /**
     * The renderer ids are the ones the picker offers. A rename there that is not made here would
     * silently fall through to UNTESTED, which is the degradation that makes the feature pointless.
     */
    private static void theRendererIdsMatchTheResourceArray(String root) {
        File arrays = new File(root,
                "app_pojavlauncher/src/main/res/values/headings_array.xml");
        String xml = read(arrays);
        if (xml == null) {
            check(false, "could not read headings_array.xml at " + arrays.getPath());
            return;
        }
        int start = xml.indexOf("name=\"renderer_values\"");
        check(start >= 0, "renderer_values array not found in headings_array.xml");
        if (start < 0) return;
        int end = xml.indexOf("</string-array>", start);
        String block = xml.substring(start, end < 0 ? xml.length() : end);
        for (String id : new String[]{SodiumSupport.ZINK_KOPPER, SodiumSupport.ZINK_OSMESA,
                SodiumSupport.MOBILEGLUES, SodiumSupport.GL4ES, SodiumSupport.LTW}) {
            check(block.contains(id), "renderer id " + id + " is not in renderer_values any more");
        }
    }

    /** Adreno plus a Turnip that is actually loading is the one silent configuration. */
    private static void adrenoWithTurnipIsTheVerifiedPath() {
        for (String zink : new String[]{SodiumSupport.ZINK_KOPPER, SodiumSupport.ZINK_OSMESA}) {
            check(SodiumSupport.verdict(zink, true, adrenoTurnip()) == Verdict.VERIFIED,
                    zink + " on Adreno with Turnip is not VERIFIED");
            check(!SodiumSupport.shouldWarn(SodiumSupport.verdict(zink, true, adrenoTurnip())),
                    zink + " on Adreno with Turnip warns, which is the nagging this exists to stop");
            check(SodiumSupport.reasonFor(zink, true, adrenoTurnip()) == Reason.NONE,
                    zink + " on Adreno with Turnip has an obstacle to report");
            check(SodiumSupport.shadersViable(zink, adrenoTurnip()),
                    "shaders are not viable on the one configuration they are verified on");
        }
    }

    /**
     * THE REGRESSION GUARD. This exact case shipped silent.
     *
     * The first version of SodiumSupport returned SUPPORTED for Zink on every device, so a Mali
     * player got a crash with no dialog at all, which is worse than the dead-end dialog the
     * feature replaced. Reverting the GPU dimension makes this check fail, which is the property
     * chordsim's keyDownBuffer check has: the mutation IS the shipped bug.
     */
    private static void maliOnZinkStillWarns() {
        for (String zink : new String[]{SodiumSupport.ZINK_KOPPER, SodiumSupport.ZINK_OSMESA}) {
            Verdict v = SodiumSupport.verdict(zink, true, mali());
            check(v != Verdict.VERIFIED, zink + " on Mali is VERIFIED, which is the shipped bug");
            check(SodiumSupport.shouldWarn(v), zink + " on Mali does not warn");
            check(v == Verdict.UNTESTED,
                    zink + " on Mali should be UNTESTED, not " + v + ": the Vulkan feature set "
                            + "cannot be queried from here, so condemning it claims too much");
            check(SodiumSupport.reasonFor(zink, true, mali()) == Reason.DRIVER_UNPROVEN,
                    zink + " on Mali does not name the driver as the obstacle");
            check(!SodiumSupport.shadersViable(zink, mali()),
                    "shaders are called viable on Mali, which removes the shader pack warning "
                            + "on exactly the devices that need it");
        }
    }

    /** Zink is Mesa on Vulkan. No Vulkan is the one thing about Zink that can be known for sure. */
    private static void noVulkanIsAHardNo() {
        for (String zink : new String[]{SodiumSupport.ZINK_KOPPER, SodiumSupport.ZINK_OSMESA}) {
            check(SodiumSupport.verdict(zink, true, noVulkan()) == Verdict.INCOMPATIBLE,
                    zink + " without Vulkan is not INCOMPATIBLE");
            check(SodiumSupport.reasonFor(zink, true, noVulkan()) == Reason.NO_VULKAN,
                    zink + " without Vulkan does not say so");
        }
        // Even an Adreno that somehow reports no Vulkan must not be verified.
        Gpu adrenoNoVulkan = new Gpu(GpuFamily.ADRENO, 3, false, true);
        check(SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, adrenoNoVulkan)
                        == Verdict.INCOMPATIBLE,
                "an Adreno with no Vulkan was verified on the strength of being an Adreno");
        // And the suggestion must not send anybody there.
        check(SodiumSupport.betterRenderer(SodiumSupport.GL4ES, true, noVulkan()) == null,
                "Zink was offered to a device with no Vulkan");
    }

    /**
     * Turnip can be switched off, and then the verified premise is gone.
     *
     * Worth its own reason because the fix is a settings row rather than a different renderer,
     * and PojavLauncher's own docs say Adreno without Turnip faces crashes on Zink.
     */
    private static void adrenoWithoutTurnipIsItsOwnAnswer() {
        Verdict v = SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, adrenoSystem());
        check(v == Verdict.UNTESTED, "Adreno without Turnip gave " + v + ", expected UNTESTED");
        check(SodiumSupport.shouldWarn(v), "Adreno without Turnip does not warn");
        check(SodiumSupport.reasonFor(SodiumSupport.ZINK_KOPPER, true, adrenoSystem())
                        == Reason.TURNIP_OFF,
                "Adreno without Turnip does not point at the Turnip setting");
        check(!SodiumSupport.shadersViable(SodiumSupport.ZINK_KOPPER, adrenoSystem()),
                "shaders were promised on Adreno with Turnip switched off");
    }

    /**
     * THE OVERRIDE GUARD, written as a property rather than a case.
     *
     * JREUtils sets MESA_GL_VERSION_OVERRIDE=4.6COMPAT for every renderer id containing "zink", so
     * any GL version observed on a Zink context is a constant the launcher chose and says nothing
     * about the driver. The structural defence is that Gpu carries no reported-GL field at all;
     * this is the behavioural one. If somebody later wires a GL version into the Zink arm, the
     * verdict stops being constant across glesMajor and this fails.
     */
    private static void aReportedGlVersionCannotDecideZink() {
        for (String zink : new String[]{SodiumSupport.ZINK_KOPPER, SodiumSupport.ZINK_OSMESA}) {
            for (GpuFamily family : ALL_FAMILIES) {
                for (boolean vulkan : new boolean[]{true, false}) {
                    for (boolean turnip : new boolean[]{true, false}) {
                        Verdict first = null;
                        Reason firstReason = null;
                        for (int gles : ALL_GLES) {
                            Gpu gpu = new Gpu(family, gles, vulkan, turnip);
                            Verdict v = SodiumSupport.verdict(zink, true, gpu);
                            Reason r = SodiumSupport.reasonFor(zink, true, gpu);
                            if (first == null) { first = v; firstReason = r; continue; }
                            check(v == first,
                                    "a Zink verdict changed with the GL ES version (" + zink + ", "
                                            + family + ", vulkan=" + vulkan + ", turnip=" + turnip
                                            + ", gles=" + gles + "): " + first + " then " + v);
                            check(r == firstReason,
                                    "a Zink reason changed with the GL ES version (" + zink + ", "
                                            + family + ", gles=" + gles + ")");
                        }
                    }
                }
            }
        }
    }

    /** The GL ES version bounds the GL ES translators, and nothing else. */
    private static void glesVersionOnlyBoundsTranslators() {
        for (String id : new String[]{SodiumSupport.MOBILEGLUES, SodiumSupport.LTW}) {
            check(SodiumSupport.verdict(id, true, new Gpu(GpuFamily.OTHER, 2, true, false))
                            == Verdict.INCOMPATIBLE,
                    id + " on a GL ES 2 device is not INCOMPATIBLE, but a translator cannot "
                            + "expose more than its backend");
            check(SodiumSupport.reasonFor(id, true, new Gpu(GpuFamily.OTHER, 2, true, false))
                            == Reason.GL_TOO_OLD,
                    id + " on a GL ES 2 device does not say the GL is too old");
            check(SodiumSupport.verdict(id, true, new Gpu(GpuFamily.OTHER, 3, true, false))
                            == Verdict.UNTESTED,
                    id + " on a GL ES 3 device should be UNTESTED");
            // Unknown must decide nothing, the same rule an unreadable memory reading follows.
            check(SodiumSupport.verdict(id, true, new Gpu(GpuFamily.OTHER, 0, true, false))
                            == Verdict.UNTESTED,
                    id + " with an unknown GL ES version was condemned; 0 means unknown, not 0");
        }
        // gl4es is below the floor on every device, so the GL ES version must not rescue it.
        for (int gles : ALL_GLES) {
            check(SodiumSupport.verdict(SodiumSupport.GL4ES, true,
                            new Gpu(GpuFamily.ADRENO, gles, true, true)) == Verdict.INCOMPATIBLE,
                    "gl4es stopped being INCOMPATIBLE at GL ES " + gles);
        }
    }

    /** A family this build has never heard of gets neither a promise nor a condemnation. */
    private static void unknownIsNeverCondemnedAndNeverVerified() {
        for (GpuFamily family : new GpuFamily[]{GpuFamily.UNKNOWN, GpuFamily.OTHER,
                GpuFamily.POWERVR, GpuFamily.XCLIPSE}) {
            Gpu gpu = new Gpu(family, 3, true, false);
            Verdict v = SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, gpu);
            check(v == Verdict.UNTESTED,
                    "Zink on " + family + " gave " + v + ", expected UNTESTED");
            check(!SodiumSupport.shadersViable(SodiumSupport.ZINK_KOPPER, gpu),
                    "shaders were promised on " + family);
        }
        // An unrecognised renderer is likewise never condemned, whatever the device.
        for (GpuFamily family : ALL_FAMILIES) {
            check(SodiumSupport.verdict("renderer_from_the_future", true,
                            new Gpu(family, 3, true, true)) == Verdict.UNTESTED,
                    "an unknown renderer was judged on " + family);
        }
        check(SodiumSupport.reasonFor("renderer_from_the_future", true, adrenoTurnip())
                        == Reason.RENDERER_UNKNOWN,
                "an unknown renderer does not say that is what it is");
    }

    /**
     * Gpu.unknown() is what an unreadable device produces, so its defaults are load-bearing.
     *
     * Vulkan assumed present and Turnip assumed absent is the pairing that condemns nothing and
     * verifies nothing. Assuming no Vulkan would tell every device that would not answer that its
     * renderer cannot work; assuming Turnip would hand out the verified verdict for free.
     */
    private static void theUnknownDeviceIsTheSafeDefault() {
        Gpu unknown = Gpu.unknown();
        check(unknown.family == GpuFamily.UNKNOWN, "Gpu.unknown() is not of unknown family");
        check(unknown.glesMajor == 0, "Gpu.unknown() claims a GL ES version");
        check(unknown.vulkan, "Gpu.unknown() assumes no Vulkan, which condemns Zink everywhere");
        check(!unknown.turnipActive, "Gpu.unknown() assumes Turnip, which verifies for free");

        check(SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, unknown) == Verdict.UNTESTED,
                "Zink on an unreadable device is not UNTESTED");
        check(SodiumSupport.verdict(SodiumSupport.GL4ES, true, unknown) == Verdict.INCOMPATIBLE,
                "gl4es stopped being INCOMPATIBLE on an unreadable device");
        check(!SodiumSupport.shadersViable(SodiumSupport.ZINK_KOPPER, unknown),
                "shaders were promised on an unreadable device");

        // A null Gpu must behave exactly as the unknown one, since a caller can reach it.
        for (String id : ALL_RENDERERS) {
            check(SodiumSupport.verdict(id, true, null) == SodiumSupport.verdict(id, true, unknown),
                    "a null Gpu differs from Gpu.unknown() for " + id);
            check(SodiumSupport.reasonFor(id, true, null)
                            == SodiumSupport.reasonFor(id, true, unknown),
                    "a null Gpu gives a different reason for " + id);
        }
        // Negative and absurd values are normalised rather than trusted.
        check(new Gpu(null, -5, true, false).glesMajor == 0, "a negative GL ES version was kept");
        check(new Gpu(null, 3, true, false).family == GpuFamily.UNKNOWN,
                "a null family was not normalised");
    }

    /**
     * Shader packs are viable exactly where Sodium itself is verified, and nowhere else.
     *
     * Stated as an equivalence over the whole matrix rather than as examples, because the first
     * version got this wrong in the widening direction and a handful of cases would not have seen
     * it.
     */
    private static void shadersAreExactlyTheVerifiedSet() {
        for (String id : ALL_RENDERERS) {
            for (GpuFamily family : ALL_FAMILIES) {
                for (int gles : ALL_GLES) {
                    for (boolean vulkan : new boolean[]{true, false}) {
                        for (boolean turnip : new boolean[]{true, false}) {
                            Gpu gpu = new Gpu(family, gles, vulkan, turnip);
                            boolean viable = SodiumSupport.shadersViable(id, gpu);
                            boolean verified =
                                    SodiumSupport.verdict(id, true, gpu) == Verdict.VERIFIED;
                            check(viable == verified,
                                    "shadersViable and VERIFIED disagree for " + id + " on "
                                            + family + " (gles=" + gles + ", vulkan=" + vulkan
                                            + ", turnip=" + turnip + ")");
                        }
                    }
                }
            }
        }
    }

    /** The suggestion has to be reachable, an improvement, and not a loop. */
    private static void theSuggestionIsHonest() {
        check(SodiumSupport.ZINK_KOPPER.equals(
                        SodiumSupport.betterRenderer(SodiumSupport.GL4ES, true, mali())),
                "Zink was not offered to a Mali on gl4es, where it is still the better bet");
        check(SodiumSupport.betterRenderer(SodiumSupport.GL4ES, false, adrenoTurnip()) == null,
                "a renderer the device cannot run was offered");
        for (String zink : new String[]{SodiumSupport.ZINK_KOPPER, SodiumSupport.ZINK_OSMESA}) {
            check(SodiumSupport.betterRenderer(zink, true, mali()) == null,
                    "Zink was offered to a profile already on " + zink);
        }
        check(SodiumSupport.betterRenderer(null, true, adrenoTurnip()) != null,
                "nothing was offered to a profile with no renderer set");
        // Never a suggestion that cannot possibly help.
        for (GpuFamily family : ALL_FAMILIES) {
            String better = SodiumSupport.betterRenderer(
                    SodiumSupport.GL4ES, true, new Gpu(family, 3, false, false));
            check(better == null, "Zink was offered to a " + family + " with no Vulkan");
        }
    }

    /** No Sodium family mod means there is nothing to say, on any device. */
    private static void noSodiumIsNeverAWarning() {
        for (String id : ALL_RENDERERS) {
            for (GpuFamily family : ALL_FAMILIES) {
                Gpu gpu = new Gpu(family, 3, true, true);
                check(SodiumSupport.verdict(id, false, gpu) == Verdict.ABSENT,
                        "a verdict was given with no Sodium installed (" + id + ", " + family + ")");
                check(!SodiumSupport.shouldWarn(SodiumSupport.verdict(id, false, gpu)),
                        "a warning was raised with no Sodium installed");
                check(SodiumSupport.reasonFor(id, false, gpu) == Reason.NONE,
                        "an obstacle was named with no Sodium installed");
            }
        }
    }

    /** A warning with no reason is the generic sentence this design exists to avoid. */
    private static void everyWarningNamesAnObstacle() {
        for (String id : ALL_RENDERERS) {
            for (GpuFamily family : ALL_FAMILIES) {
                for (int gles : ALL_GLES) {
                    for (boolean vulkan : new boolean[]{true, false}) {
                        for (boolean turnip : new boolean[]{true, false}) {
                            Gpu gpu = new Gpu(family, gles, vulkan, turnip);
                            Verdict v = SodiumSupport.verdict(id, true, gpu);
                            Reason r = SodiumSupport.reasonFor(id, true, gpu);
                            if (SodiumSupport.shouldWarn(v)) {
                                check(r != Reason.NONE,
                                        "a warning carried no reason (" + id + ", " + family + ")");
                            } else {
                                check(r == Reason.NONE,
                                        "a silent verdict carried a reason (" + id + ", "
                                                + family + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    /** Invariants that must hold for every point in the matrix. */
    private static void theWholeMatrixHoldsItsInvariants() {
        int points = 0;
        for (String id : ALL_RENDERERS) {
            String norm = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            boolean zink = SodiumSupport.ZINK_KOPPER.equals(norm)
                    || SodiumSupport.ZINK_OSMESA.equals(norm);
            boolean translator = SodiumSupport.MOBILEGLUES.equals(norm)
                    || SodiumSupport.LTW.equals(norm);
            boolean gl4es = SodiumSupport.GL4ES.equals(norm);
            for (GpuFamily family : ALL_FAMILIES) {
                for (int gles : ALL_GLES) {
                    for (boolean vulkan : new boolean[]{true, false}) {
                        for (boolean turnip : new boolean[]{true, false}) {
                            points++;
                            Gpu gpu = new Gpu(family, gles, vulkan, turnip);
                            Verdict v = SodiumSupport.verdict(id, true, gpu);
                            check(v != null, "a null verdict for " + id);
                            if (v == Verdict.VERIFIED) {
                                check(zink && family == GpuFamily.ADRENO && turnip && vulkan,
                                        "VERIFIED reached without Zink on Adreno with Turnip and "
                                                + "Vulkan (" + id + ", " + family + ", turnip="
                                                + turnip + ", vulkan=" + vulkan + ")");
                            }
                            if (v == Verdict.INCOMPATIBLE) {
                                boolean allowed = gl4es
                                        || (zink && !vulkan)
                                        || (translator && gles > 0 && gles < 3);
                                check(allowed,
                                        "INCOMPATIBLE claimed without positive evidence (" + id
                                                + ", " + family + ", gles=" + gles + ", vulkan="
                                                + vulkan + ")");
                            }
                            check(SodiumSupport.shouldWarn(v)
                                            == (v == Verdict.UNTESTED || v == Verdict.INCOMPATIBLE),
                                    "shouldWarn disagrees with the verdict for " + id);
                        }
                    }
                }
            }
        }
        check(points == ALL_RENDERERS.length * ALL_FAMILIES.length * ALL_GLES.length * 4,
                "the matrix sweep did not visit every point");
    }

    /** Case and surrounding space come from a JSON file somebody may have hand-edited. */
    private static void idsAreTakenAsWritten() {
        check(SodiumSupport.verdict("OPENGLES2", true, adrenoTurnip()) == Verdict.INCOMPATIBLE,
                "an upper case renderer id was not recognised");
        check(SodiumSupport.verdict("  opengles2  ", true, adrenoTurnip()) == Verdict.INCOMPATIBLE,
                "a padded renderer id was not recognised");
        check(SodiumSupport.verdict("Opengles3_DesktopGL_Zink_Kopper", true, adrenoTurnip())
                        == Verdict.VERIFIED,
                "a mixed case Zink id was not recognised");
        check(SodiumSupport.shadersViable("  OPENGLES3_DESKTOPGL_ZINK_KOPPER ", adrenoTurnip()),
                "a padded Zink id was not recognised for shaders");
    }

    /**
     * familyOf's Adreno test must be the same predicate JREUtils gates Turnip loading on.
     *
     * Lifted from the shipped GLInfoUtils rather than restated, which is chordsim's trick: if this
     * harness held its own copy of the rule, the copy and the code could drift and the harness
     * would agree with the drift. A looser test here would call a device Adreno, promise it
     * Turnip, and not get it.
     */
    private static void familyAgreesWithTheTurnipPredicate(String root) {
        File glInfo = new File(root,
                "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/GLInfoUtils.java");
        String source = read(glInfo);
        if (source == null) {
            check(false, "could not read GLInfoUtils.java at " + glInfo.getPath());
            return;
        }
        int at = source.indexOf("public boolean isAdreno()");
        check(at >= 0, "isAdreno() is gone from GLInfoUtils; the Turnip predicate moved");
        if (at < 0) return;
        String body = source.substring(at, Math.min(source.length(), at + 240));
        // The shipped rule, as of writing: renderer.contains("Adreno") && vendor.equals("Qualcomm")
        check(body.contains("\"Adreno\"") && body.contains("\"Qualcomm\""),
                "isAdreno() no longer tests Adreno against Qualcomm; familyOf must be updated to "
                        + "match whatever it tests now, or a verdict will promise an unloaded driver");

        check(SodiumSupport.familyOf("Qualcomm", "Adreno (TM) 740") == GpuFamily.ADRENO,
                "a real Adreno was not classified as Adreno");
        // The vendor is matched exactly, exactly as isAdreno() does, so a renderer naming Adreno
        // under another vendor is deliberately NOT Adreno: Turnip would not load for it.
        check(SodiumSupport.familyOf("NotQualcomm", "Adreno (TM) 740") != GpuFamily.ADRENO,
                "an Adreno string under the wrong vendor was treated as Adreno, which would "
                        + "promise a Turnip driver that JREUtils will not load");
        check(SodiumSupport.familyOf("ARM", "Mali-G78") == GpuFamily.MALI, "Mali-G78 misread");
        check(SodiumSupport.familyOf("ARM", "Mali-G715-Immortalis") == GpuFamily.MALI,
                "an Immortalis Mali was misread");
        check(SodiumSupport.familyOf("Imagination Technologies", "PowerVR Rogue GE8320")
                        == GpuFamily.POWERVR, "PowerVR misread");
        check(SodiumSupport.familyOf("Samsung Electronics", "Samsung Xclipse 920")
                        == GpuFamily.XCLIPSE, "Xclipse misread");
        // GLInfoUtils.initDummyInfo() uses exactly this string when the query fails.
        check(SodiumSupport.familyOf("<Unknown>", "<Unknown>") == GpuFamily.UNKNOWN,
                "the dummy GLInfo was not read as an unknown device");
        check(SodiumSupport.familyOf(null, null) == GpuFamily.UNKNOWN, "null strings misread");
        check(SodiumSupport.familyOf("ARM", "") == GpuFamily.UNKNOWN, "an empty renderer misread");
    }

    /* ------------------------------------------------------------------ detection */

    /**
     * Jar names copied from what these mods actually ship as, not from what the matcher expects.
     *
     * 16.20: a fixture written from the same assumption as the code confirms the assumption.
     */
    private static void realJarNamesAreMatched() {
        String[] real = {
                "sodium-fabric-0.6.13+mc1.21.4.jar",
                "sodium-fabric-mc1.20.1-0.5.8.jar",
                "sodium-neoforge-0.6.13+mc1.21.4.jar",
                "Embeddium-1.21.1-1.0.11+mc1.21.1.jar",
                "embeddium-0.3.31+mc1.20.1.jar",
                "rubidium-0.7.1.jar",
                "Xenon-1.21.1-0.1.0.jar",
                "celeritas-0.4.0+mc1.21.jar",
                "vintagium-0.3.0+mc1.12.2.jar"
        };
        for (String name : real) {
            check(SodiumSupport.matchesFamily(name), "a real Sodium jar was missed: " + name);
        }
    }

    /**
     * The check with teeth, because over-matching is the direction that costs something.
     *
     * A missed mod asks the player nothing and leaves them where they already were. A false match
     * tells somebody with no Sodium at all to change their renderer, which is both wrong and
     * actionable, so they act on it.
     */
    private static void unrelatedModsAreNotMatched() {
        String[] innocent = {
                "Lithium-fabric-0.11.2+mc1.21.jar", "phosphor-fabric-mc1.17-0.8.1.jar",
                "ferritecore-6.0.1-fabric.jar", "Krypton-0.2.3.jar",
                "immersive_aircraft-0.6.3+1.20.1-fabric.jar", "Titanium-1.20.1-3.8.26.jar",
                "Mekanism-1.20.1-10.4.5.43.jar", "Botanium-1.19.2-1.0.jar",
                "Gazebo-1.0.jar", "modernfix-fabric-5.18.0+mc1.21.1.jar",
                // Iris and Indium depend on Sodium but are not renderer replacements: deletion
                // takes them, detection must not, or uninstalling Sodium alone would still warn.
                "iris-mc1.21.4-1.8.8.jar", "indium-1.0.34+mc1.21.jar",
                // Not a jar at all. A disabled mod is absent from the game.
                "sodium-fabric-0.6.13+mc1.21.4.jar.disabled",
                "sodium-shaders.zip", "Sodium.txt",
                null, "", ".jar"
        };
        for (String name : innocent) {
            check(!SodiumSupport.matchesFamily(name),
                    "an unrelated file was taken for Sodium: " + name);
        }
    }

    /** Properties of the list itself, which is the part a later edit is most likely to spoil. */
    private static void theFamilyListIsWellFormed() {
        check(SodiumSupport.FAMILY.length > 0, "the family list is empty");
        for (String entry : SodiumSupport.FAMILY) {
            check(entry != null && entry.length() > 0, "the family list has a blank entry");
            check(entry.equals(entry.toLowerCase(Locale.ROOT)),
                    "a family entry is not lower case and so can never match: " + entry);
            check(entry.trim().equals(entry), "a family entry carries space: " + entry);
            check(SodiumSupport.matchesFamily(entry + ".jar"),
                    "a family entry does not match its own name: " + entry);
        }
        for (String a : SodiumSupport.FAMILY) {
            for (String b : SodiumSupport.FAMILY) {
                if (a.equals(b)) continue;
                check(!a.contains(b), "family entry " + a + " is already covered by " + b);
            }
        }
    }

    /* ------------------------------------------------------------------ plumbing */

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
