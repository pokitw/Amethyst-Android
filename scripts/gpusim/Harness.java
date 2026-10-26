package net.kdt.pojavlaunch.sodium;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.TurnipDrivers;

import net.kdt.pojavlaunch.sodium.SodiumSupport.Gpu;
import net.kdt.pojavlaunch.sodium.SodiumSupport.GpuFamily;

/**
 * Drives the shipped GpuProfile against stubs that can refuse. See run.sh.
 */
public class Harness {

    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) {
        itReadsARealAdreno();
        itReadsAMali();
        turnipIsOnlyEverClaimedForAdreno();
        theSystemDriverChoiceIsHonoured();
        nothingItTouchesCanMakeItThrow();
        theDummyInfoReadsAsUnknown();
        itDescribesTheDeviceForTheLog();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String f : FAILURES) System.out.println("FAIL: " + f);
            System.out.println(FAILURES.size() + " FAILURES");
            System.exit(1);
        }
        System.out.println("all passed");
    }

    /* ------------------------------------------------------------------ fixtures */

    private static void reset() {
        GLInfoUtils.THROW = false;
        GLInfoUtils.NEXT = new GLInfoUtils.GLInfo("Qualcomm", "Adreno (TM) 740", 3);
        PackageManager.VULKAN = true;
        PackageManager.THROW = false;
        Context.THROW_ON_PACKAGE_MANAGER = false;
        TurnipDrivers.LOAD = true;
        TurnipDrivers.THROW = false;
        LauncherPreferences.PREF_TURNIP_DRIVER = "bundled";
    }

    private static Gpu read() {
        return GpuProfile.read(new Context());
    }

    /* ------------------------------------------------------------------ checks */

    /** The configuration the whole verified verdict rests on. */
    private static void itReadsARealAdreno() {
        reset();
        Gpu gpu = read();
        check(gpu.family == GpuFamily.ADRENO, "a Snapdragon was not read as Adreno");
        check(gpu.glesMajor == 3, "the GL ES version was lost");
        check(gpu.vulkan, "Vulkan was not detected on a device reporting it");
        check(gpu.turnipActive, "Turnip was not reported active with the bundled driver chosen");
        check(SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, gpu)
                        == SodiumSupport.Verdict.VERIFIED,
                "Adreno with Turnip did not reach VERIFIED through the real reader");
    }

    /** And the configuration the fix exists for. */
    private static void itReadsAMali() {
        reset();
        GLInfoUtils.NEXT = new GLInfoUtils.GLInfo("ARM", "Mali-G78", 3);
        Gpu gpu = read();
        check(gpu.family == GpuFamily.MALI, "a Mali was not read as Mali");
        check(!gpu.turnipActive,
                "Turnip was reported active on a Mali, which JREUtils will never load");
        check(SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, gpu)
                        == SodiumSupport.Verdict.UNTESTED,
                "Mali on Zink did not warn through the real reader, which is the shipped bug");
    }

    /**
     * turnipActive must be false for every non-Adreno device, whatever the preference says.
     *
     * JREUtils only loads Turnip inside an {@code if(info.isAdreno())} branch, so claiming it
     * anywhere else would promise a driver the launch will not load: the original bug with a
     * different cause.
     */
    private static void turnipIsOnlyEverClaimedForAdreno() {
        String[][] devices = {
                {"ARM", "Mali-G715-Immortalis"},
                {"Imagination Technologies", "PowerVR Rogue GE8320"},
                {"Samsung Electronics", "Samsung Xclipse 920"},
                {"<Unknown>", "<Unknown>"},
                // An Adreno string under the wrong vendor fails isAdreno(), so no Turnip.
                {"Mesa", "Adreno (TM) 640"},
        };
        for (String[] device : devices) {
            reset();
            TurnipDrivers.LOAD = true;
            GLInfoUtils.NEXT = new GLInfoUtils.GLInfo(device[0], device[1], 3);
            Gpu gpu = read();
            check(!gpu.turnipActive,
                    "Turnip claimed for " + device[1] + " under vendor " + device[0]);
            check(SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, gpu)
                            != SodiumSupport.Verdict.VERIFIED,
                    device[1] + " reached VERIFIED without Turnip");
        }
    }

    /** Choosing the system Vulkan driver takes the verified premise away. */
    private static void theSystemDriverChoiceIsHonoured() {
        reset();
        LauncherPreferences.PREF_TURNIP_DRIVER = TurnipDrivers.CHOICE_SYSTEM;
        Gpu gpu = read();
        check(gpu.family == GpuFamily.ADRENO, "the family changed with the driver choice");
        check(!gpu.turnipActive, "the system driver choice still reported Turnip active");
        check(SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, gpu)
                        == SodiumSupport.Verdict.UNTESTED,
                "Adreno on the system driver was still verified");
        check(SodiumSupport.reasonFor(SodiumSupport.ZINK_KOPPER, true, gpu)
                        == SodiumSupport.Reason.TURNIP_OFF,
                "the system driver choice did not point at the Turnip setting");
    }

    /**
     * The one thing this class must never do.
     *
     * It runs on the launch path, and §16.15 is the lesson that anything there which touches a
     * system service has to be unable to throw rather than merely unlikely to: a device report is
     * never worth a game that will not start. Every stub is made to refuse in turn, and in every
     * case the result has to be a usable Gpu rather than an exception.
     */
    private static void nothingItTouchesCanMakeItThrow() {
        // The EGL probe fails, which on a real device is a driver that will not initialise.
        reset();
        GLInfoUtils.THROW = true;
        Gpu gpu = guard("GLInfoUtils throwing");
        if (gpu != null) {
            check(gpu.family == GpuFamily.UNKNOWN,
                    "a failed GL probe did not read as an unknown family");
            check(!gpu.turnipActive, "Turnip was claimed after the GL probe failed");
            check(gpu.vulkan,
                    "a failed GL probe was taken to mean no Vulkan, which condemns Zink");
        }

        // The package manager refuses.
        reset();
        PackageManager.THROW = true;
        gpu = guard("PackageManager throwing");
        if (gpu != null) {
            check(gpu.vulkan,
                    "an unreadable Vulkan query was believed as 'no Vulkan', which would tell "
                            + "every device that will not answer that Zink cannot work");
        }

        // The Context itself is dead.
        reset();
        Context.THROW_ON_PACKAGE_MANAGER = true;
        gpu = guard("Context.getPackageManager throwing");
        if (gpu != null) check(gpu.vulkan, "a dead Context was read as no Vulkan");

        // Resolving the driver folder fails.
        reset();
        TurnipDrivers.THROW = true;
        gpu = guard("TurnipDrivers.resolve throwing");
        if (gpu != null) {
            check(!gpu.turnipActive, "Turnip was claimed after resolve() failed");
            check(gpu.family == GpuFamily.ADRENO, "the family was lost when resolve() failed");
        }

        // Everything fails at once.
        reset();
        GLInfoUtils.THROW = true;
        PackageManager.THROW = true;
        TurnipDrivers.THROW = true;
        gpu = guard("everything throwing");
        if (gpu != null) {
            check(gpu.family == GpuFamily.UNKNOWN, "a total failure was not unknown");
            check(SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, gpu)
                            == SodiumSupport.Verdict.UNTESTED,
                    "a total failure did not land on UNTESTED");
        }

        // A null Context, which a caller can reach.
        reset();
        try {
            Gpu nullContext = GpuProfile.read(null);
            check(nullContext != null, "read(null) returned null");
            if (nullContext != null) {
                check(!nullContext.turnipActive, "read(null) claimed Turnip");
            }
        } catch (Throwable t) {
            check(false, "read(null) threw " + t.getClass().getSimpleName());
        }

        // And describe() must survive a null too, since it goes into the log.
        try {
            check(GpuProfile.describe(null) != null, "describe(null) returned null");
        } catch (Throwable t) {
            check(false, "describe(null) threw " + t.getClass().getSimpleName());
        }
    }

    /** GLInfoUtils.initDummyInfo() uses exactly these strings when its query fails. */
    private static void theDummyInfoReadsAsUnknown() {
        reset();
        GLInfoUtils.NEXT = new GLInfoUtils.GLInfo("<Unknown>", "<Unknown>", 2);
        Gpu gpu = read();
        check(gpu.family == GpuFamily.UNKNOWN, "the dummy GLInfo was not read as unknown");
        check(SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, gpu)
                        == SodiumSupport.Verdict.UNTESTED,
                "the dummy GLInfo produced something other than UNTESTED on Zink");
        // glesMajor 2 must not condemn Zink, which does not go through GL ES at all.
        check(SodiumSupport.verdict(SodiumSupport.ZINK_KOPPER, true, gpu)
                        != SodiumSupport.Verdict.INCOMPATIBLE,
                "a GL ES 2 reading condemned Zink, which does not use GL ES");
    }

    /** The log line is what every bug report is built from, so it has to carry the device. */
    private static void itDescribesTheDeviceForTheLog() {
        reset();
        String adreno = GpuProfile.describe(read());
        check(adreno.contains("ADRENO"), "the log description does not name the family");
        check(adreno.contains("turnip active"), "the log description does not state Turnip");
        check(adreno.contains("vulkan yes"), "the log description does not state Vulkan");

        reset();
        GLInfoUtils.NEXT = new GLInfoUtils.GLInfo("ARM", "Mali-G78", 3);
        String mali = GpuProfile.describe(read());
        check(mali.contains("MALI") && mali.contains("turnip off"),
                "an Adreno and a Mali describe the same way, so a bug report cannot tell them "
                        + "apart: " + mali);
        check(!adreno.equals(mali), "two different devices produced the same description");

        reset();
        GLInfoUtils.THROW = true;
        String unknown = GpuProfile.describe(read());
        check(unknown.contains("gles unknown"),
                "an unreadable GL ES version was printed as a number: " + unknown);
    }

    /* ------------------------------------------------------------------ plumbing */

    private static Gpu guard(String what) {
        try {
            Gpu gpu = read();
            check(gpu != null, "read() returned null with " + what);
            return gpu;
        } catch (Throwable t) {
            check(false, "read() threw " + t.getClass().getSimpleName() + " with " + what
                    + ", and this runs on the launch path");
            return null;
        }
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
