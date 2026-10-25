package net.kdt.pojavlaunch.sodium;

import java.util.ArrayList;
import java.util.List;

/**
 * Drive the shipped {@link SodiumSupport}.
 *
 * A verdict here is wrong in two silent directions. Too strict and a player is told a working
 * setup is broken, which is the bug that was just fixed and must not come back. Too loose and
 * somebody is waved into a black screen with no explanation, which is worse than the dialog ever
 * was. Both are assertions, not "nothing threw".
 */
public class Harness {

    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) {
        theRealRendererSet();
        zinkIsSupportedBothWays();
        gl4esIsTheOneRefusal();
        unknownIsNeverARefusal();
        noSodiumIsNeverAWarning();
        theSuggestionIsOnlyEverZink();
        shadersAreNarrowerThanSodium();
        idsAreTakenAsWritten();
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

    /**
     * Every renderer id the launcher ships, with the verdict each must produce.
     *
     * The ids are the ones in res/values/headings_array.xml. If one is renamed there and not
     * here, this table is what notices: a renderer id that no longer matches falls through to
     * UNTESTED, which is exactly the silent degradation that would make the feature pointless.
     */
    private static void theRealRendererSet() {
        verdict(SodiumSupport.ZINK_KOPPER, SodiumSupport.Verdict.SUPPORTED);
        verdict(SodiumSupport.ZINK_OSMESA, SodiumSupport.Verdict.SUPPORTED);
        verdict(SodiumSupport.MOBILEGLUES, SodiumSupport.Verdict.UNTESTED);
        verdict(SodiumSupport.GL4ES, SodiumSupport.Verdict.UNSUPPORTED);
        verdict(SodiumSupport.LTW, SodiumSupport.Verdict.UNTESTED);

        // The literals, so a constant renamed in the Java does not quietly take the table with it.
        verdict("opengles3_desktopgl_zink_kopper", SodiumSupport.Verdict.SUPPORTED);
        verdict("vulkan_zink", SodiumSupport.Verdict.SUPPORTED);
        verdict("opengles_mobileglues", SodiumSupport.Verdict.UNTESTED);
        verdict("opengles2", SodiumSupport.Verdict.UNSUPPORTED);
    }

    /** Both Zinks are Mesa, so the presentation path underneath cannot change the answer. */
    private static void zinkIsSupportedBothWays() {
        for (String id : new String[]{SodiumSupport.ZINK_KOPPER, SodiumSupport.ZINK_OSMESA}) {
            check(!SodiumSupport.shouldWarn(SodiumSupport.verdict(id, true)),
                    "a Zink profile was warned about, which is the nagging the fix removes: " + id);
            check(SodiumSupport.betterRenderer(id, true) == null,
                    "Zink was offered a better renderer than itself: " + id);
            check(SodiumSupport.shadersViable(id), "shaders were called unviable on " + id);
        }
    }

    /**
     * The one case where refusing is a fact rather than a worry.
     *
     * gl4es tops out two whole OpenGL versions below Sodium's floor, and it is the launcher's
     * default renderer, so this is the verdict most players will actually meet.
     */
    private static void gl4esIsTheOneRefusal() {
        SodiumSupport.Verdict v = SodiumSupport.verdict(SodiumSupport.GL4ES, true);
        check(v == SodiumSupport.Verdict.UNSUPPORTED, "gl4es did not read as unsupported: " + v);
        check(SodiumSupport.shouldWarn(v), "the unsupported verdict did not warn");
        check(SodiumSupport.ZINK_KOPPER.equals(
                        SodiumSupport.betterRenderer(SodiumSupport.GL4ES, true)),
                "gl4es was not offered Zink");
        check(!SodiumSupport.shadersViable(SodiumSupport.GL4ES),
                "shaders were called viable on gl4es");
    }

    /**
     * A renderer this class has never heard of is one it has no business condemning.
     *
     * The launcher has added and dropped renderers repeatedly, and a new one defaulting to
     * UNSUPPORTED would reinstate the dead end for everybody on it.
     */
    private static void unknownIsNeverARefusal() {
        for (String id : new String[]{"something_new", "", "   ", "opengles4_future", null}) {
            SodiumSupport.Verdict v = SodiumSupport.verdict(id, true);
            check(v != SodiumSupport.Verdict.UNSUPPORTED,
                    "an unknown renderer was condemned: " + id + " -> " + v);
        }
        // Null means "follows the launcher default", which is not a renderer this can judge.
        check(SodiumSupport.verdict(null, true) == SodiumSupport.Verdict.UNTESTED,
                "a profile with no renderer set was judged rather than left alone");
    }

    /** Without the mod there is nothing to say, on any renderer at all. */
    private static void noSodiumIsNeverAWarning() {
        for (String id : new String[]{SodiumSupport.GL4ES, SodiumSupport.ZINK_KOPPER,
                SodiumSupport.MOBILEGLUES, SodiumSupport.LTW, "unknown", null}) {
            SodiumSupport.Verdict v = SodiumSupport.verdict(id, false);
            check(v == SodiumSupport.Verdict.ABSENT, "no mod still produced " + v + " on " + id);
            check(!SodiumSupport.shouldWarn(v), "a profile with no Sodium was warned about");
        }
    }

    /**
     * The offer has to be real.
     *
     * Kopper Zink needs Vulkan, and a device without it cannot be sent there. A dialog offering a
     * renderer the device cannot run would be worse than one offering nothing at all.
     */
    private static void theSuggestionIsOnlyEverZink() {
        for (String id : new String[]{SodiumSupport.GL4ES, SodiumSupport.MOBILEGLUES,
                SodiumSupport.LTW, "unknown", null}) {
            check(SodiumSupport.ZINK_KOPPER.equals(SodiumSupport.betterRenderer(id, true)),
                    "no renderer was suggested for " + id);
            check(SodiumSupport.betterRenderer(id, false) == null,
                    "a renderer was suggested on a device that cannot run it: " + id);
        }
    }

    /**
     * Shaders are a narrower claim than Sodium, and saying otherwise is the overclaim that would
     * send somebody to install Iris on a translator and watch it fail.
     */
    private static void shadersAreNarrowerThanSodium() {
        check(!SodiumSupport.shadersViable(SodiumSupport.MOBILEGLUES),
                "shaders were promised on a translation layer");
        check(!SodiumSupport.shadersViable(SodiumSupport.LTW), "shaders were promised on LTW");
        check(!SodiumSupport.shadersViable("unknown"), "shaders were promised on an unknown renderer");
        check(!SodiumSupport.shadersViable(null), "shaders were promised with no renderer set");

        // Anything shaders are viable on must at least be able to run Sodium without a warning.
        for (String id : new String[]{SodiumSupport.ZINK_KOPPER, SodiumSupport.ZINK_OSMESA,
                SodiumSupport.MOBILEGLUES, SodiumSupport.GL4ES, SodiumSupport.LTW, "x", null}) {
            if (SodiumSupport.shadersViable(id)) {
                check(SodiumSupport.verdict(id, true) == SodiumSupport.Verdict.SUPPORTED,
                        "shaders were called viable on a renderer Sodium is not supported on: " + id);
            }
        }
    }

    /** Case and surrounding space come from a JSON file somebody may have hand-edited. */
    private static void idsAreTakenAsWritten() {
        check(SodiumSupport.verdict("OPENGLES2", true) == SodiumSupport.Verdict.UNSUPPORTED,
                "an upper case renderer id was not recognised");
        check(SodiumSupport.verdict("  opengles2  ", true) == SodiumSupport.Verdict.UNSUPPORTED,
                "a padded renderer id was not recognised");
        check(SodiumSupport.verdict("Opengles3_DesktopGL_Zink_Kopper", true)
                        == SodiumSupport.Verdict.SUPPORTED,
                "a mixed case Zink id was not recognised");
        check(SodiumSupport.shadersViable("  OPENGLES3_DESKTOPGL_ZINK_KOPPER "),
                "a padded Zink id was not recognised for shaders");
    }


    /**
     * Jar names copied from what these mods actually ship as, not from what the matcher expects.
     *
     * 16.20: a fixture written from the same assumption as the code confirms the assumption. Every
     * name below is the real artifact name from the mod's own releases, capitalisation included.
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
     * actionable, so they act on it. Widening any entry towards a pattern (an "ium" suffix, say)
     * lights up a large part of the modding ecosystem, and these are the names it would hit.
     */
    private static void unrelatedModsAreNotMatched() {
        String[] innocent = {
                // Every one of these ends in -ium and none of them is Sodium.
                "Lithium-fabric-0.11.2+mc1.21.jar", "phosphor-fabric-mc1.17-0.8.1.jar",
                "ferritecore-6.0.1-fabric.jar", "Krypton-0.2.3.jar",
                "immersive_aircraft-0.6.3+1.20.1-fabric.jar", "Titanium-1.20.1-3.8.26.jar",
                "Mekanism-1.20.1-10.4.5.43.jar", "Botanium-1.19.2-1.0.jar",
                "Gazebo-1.0.jar", "modernfix-fabric-5.18.0+mc1.21.1.jar",
                // Iris and Indium depend on Sodium but are not renderer replacements: deletion
                // takes them, detection must not, or uninstalling Sodium alone would still warn.
                "iris-mc1.21.4-1.8.8.jar", "indium-1.0.34+mc1.21.jar",
                // Not a jar at all. A disabled mod is absent from the game (modmetasim's rule).
                "sodium-fabric-0.6.13+mc1.21.4.jar.disabled",
                // A resource pack or shader pack in the wrong folder is not a mod.
                "sodium-shaders.zip", "Sodium.txt",
                null, "", ".jar"
        };
        for (String name : innocent) {
            check(!SodiumSupport.matchesFamily(name),
                    "an unrelated file was taken for Sodium: " + name);
        }
    }

    /**
     * Properties of the list itself, which is the part a later edit is most likely to spoil.
     *
     * The entries have to be lower case, because the matcher lower-cases only the file name and
     * an upper-case entry would then match nothing at all: a silent loss of one whole fork.
     */
    private static void theFamilyListIsWellFormed() {
        check(SodiumSupport.FAMILY.length > 0, "the family list is empty");
        for (String entry : SodiumSupport.FAMILY) {
            check(entry != null && entry.length() > 0, "the family list has a blank entry");
            check(entry.equals(entry.toLowerCase(java.util.Locale.ROOT)),
                    "a family entry is not lower case and so can never match: " + entry);
            check(entry.trim().equals(entry), "a family entry carries space: " + entry);
            check(SodiumSupport.matchesFamily(entry + ".jar"),
                    "a family entry does not match its own name: " + entry);
        }
        // A redundant entry is harmless but it means one of the two is dead, and a dead entry
        // reads as coverage that is not there.
        for (String a : SodiumSupport.FAMILY) {
            for (String b : SodiumSupport.FAMILY) {
                if (a.equals(b)) continue;
                check(!a.contains(b), "family entry " + a + " is already covered by " + b);
            }
        }
    }

    /* ------------------------------------------------------------------ plumbing */

    private static void verdict(String id, SodiumSupport.Verdict expected) {
        SodiumSupport.Verdict actual = SodiumSupport.verdict(id, true);
        check(actual == expected, "renderer " + id + " gave " + actual + " not " + expected);
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
