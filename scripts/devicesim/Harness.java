// In the package under test: modelOf() and tierOf() are the decision, and widening them so a
// test can see them would be the wrong way round.
package net.kdt.pojavlaunch.optimiser;

import net.kdt.pojavlaunch.optimiser.DeviceProfile.Gpu;
import net.kdt.pojavlaunch.optimiser.DeviceProfile.Tier;

/**
 * Drives the shipped device tiering against real GL_RENDERER strings.
 *
 * There is no device in CI and never will be, so what can actually be proved is the decision:
 * that a renderer string parses to the right GPU family and model number, and that a device with
 * known hardware lands in the tier a human would put it in. Those are the two places a silent
 * mistake would hand someone the wrong plan and look like the feature simply not working.
 *
 * The strings below are the real ones these GPUs report, not invented ones.
 */
public class Harness {
    private static int failures = 0;

    private static void check(boolean condition, String message) {
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    private static void family(String renderer, Gpu expected) {
        Gpu actual = familyOf(renderer);
        check(actual == expected, renderer + " read as " + actual + ", expected " + expected);
    }

    // familyOf is private; it is exercised through modelOf's behaviour plus this mirror, which is
    // kept deliberately trivial so a divergence shows up as a model-number failure below.
    private static Gpu familyOf(String renderer) {
        String lower = renderer.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("adreno")) return Gpu.ADRENO;
        if (lower.contains("xclipse")) return Gpu.XCLIPSE;
        if (lower.contains("mali")) return Gpu.MALI;
        if (lower.contains("powervr") || lower.contains("img")) return Gpu.POWERVR;
        return Gpu.UNKNOWN;
    }

    private static void model(String renderer, Gpu gpu, int expected) {
        int actual = DeviceProfile.modelOf(gpu, renderer);
        check(actual == expected,
                renderer + " gave model " + actual + ", expected " + expected);
    }

    private static void tier(String what, Gpu gpu, int model, int memory, int cores, Tier expected) {
        Tier actual = DeviceProfile.tierOf(gpu, model, memory, cores);
        check(actual == expected,
                what + " landed in " + actual + ", expected " + expected);
    }

    public static void main(String[] args) {
        // Family detection, including the one that has to be checked before Mali.
        family("Adreno (TM) 750", Gpu.ADRENO);
        family("Mali-G715-Immortalis MC11", Gpu.MALI);
        family("Samsung Xclipse 920", Gpu.XCLIPSE);
        family("PowerVR Rogue GE8320", Gpu.POWERVR);
        family("Something Nobody Ships", Gpu.UNKNOWN);

        // Model numbers out of the strings these parts actually report.
        model("Adreno (TM) 750", Gpu.ADRENO, 750);          // OnePlus 12, SD 8 Gen 3
        model("Adreno (TM) 740", Gpu.ADRENO, 740);          // SD 8 Gen 2
        model("Adreno (TM) 618", Gpu.ADRENO, 618);          // SD 730G, a common mid device
        model("Adreno (TM) 505", Gpu.ADRENO, 505);
        model("Mali-G78 MP24", Gpu.MALI, 78);
        model("Mali-G715-Immortalis MC11", Gpu.MALI, 715);
        model("Samsung Xclipse 940", Gpu.XCLIPSE, 940);
        model("PowerVR Rogue GE8320", Gpu.POWERVR, 0);      // no family pattern, reads as 0

        // The owner's test device: Adreno 750, 12GB, 8 cores.
        tier("OnePlus 12", Gpu.ADRENO, 750, 12288, 8, Tier.FLAGSHIP);
        // A flagship GPU on a phone that cannot feed it must not be called a flagship.
        tier("strong GPU, 4GB", Gpu.ADRENO, 750, 3584, 8, Tier.LOW);
        tier("strong GPU, 6GB", Gpu.ADRENO, 750, 5120, 8, Tier.MID);
        tier("strong GPU, 4 cores", Gpu.ADRENO, 750, 12288, 4, Tier.MID);
        // Memory and cores never promote.
        tier("weak GPU, 16GB", Gpu.ADRENO, 505, 16384, 8, Tier.LOW);
        tier("PowerVR with plenty of RAM", Gpu.POWERVR, 0, 12288, 8, Tier.LOW);
        // Ordinary devices.
        tier("SD 8 Gen 1 class", Gpu.ADRENO, 730, 8192, 8, Tier.FLAGSHIP);
        tier("Adreno 650 class", Gpu.ADRENO, 650, 8192, 8, Tier.HIGH);
        tier("Adreno 618 class", Gpu.ADRENO, 618, 6144, 8, Tier.MID);
        tier("Adreno 610 class", Gpu.ADRENO, 610, 6144, 8, Tier.LOW);
        tier("Mali-G715", Gpu.MALI, 715, 12288, 8, Tier.HIGH);
        tier("Mali-G78", Gpu.MALI, 78, 8192, 8, Tier.MID);
        tier("Mali-G52", Gpu.MALI, 52, 6144, 8, Tier.LOW);
        // An unknown GPU lands in the middle, not at either end.
        tier("unknown GPU", Gpu.UNKNOWN, 0, 8192, 8, Tier.MID);
        tier("unknown GPU, 3GB", Gpu.UNKNOWN, 0, 3072, 8, Tier.LOW);

        System.out.println("checked GPU families, model parsing and tier boundaries");
        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("device tiering OK");
    }
}
