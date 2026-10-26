package net.kdt.pojavlaunch.sodium;

import android.content.Context;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.TurnipDrivers;

/**
 * Reads the device's graphics stack into a {@link SodiumSupport.Gpu}.
 *
 * <b>Separate from {@link SodiumSupport} so that class can stay free of Android types</b> and be
 * compiled and driven directly by {@code scripts/sodiumsim}. The dependency points one way: this
 * file knows about SodiumSupport, and SodiumSupport knows nothing about this one, so the harness
 * never has to stub {@code GLInfoUtils}, {@code TurnipDrivers} or a {@code Context}.
 *
 * <p><b>Every read is guarded and the whole thing degrades to {@link SodiumSupport.Gpu#unknown()}
 * rather than throwing.</b> This runs on the launch path, and §16.15 is the lesson that anything
 * there which touches a system service has to be unable to throw rather than merely unlikely to:
 * a device report is never worth a game that will not start. An unreadable device produces
 * UNTESTED, which warns and condemns nothing.
 */
public final class GpuProfile {

    private GpuProfile() {}

    /**
     * What the device says about itself, as far as it can be read.
     *
     * Cheap after the first call in a process: {@code GLInfoUtils.getGlInfo()} caches its answer
     * in a static, and {@code TurnipDrivers.resolve} is a preference read plus at most one
     * directory stat. Call it once per screen all the same (§16.11) rather than per frame.
     *
     * <p>Safe to call from the launcher process. {@code SettingsFragment} already reads
     * {@code getGlInfo().isAdreno} there to decide whether to draw the Turnip rows, so the EGL
     * probe is a path the launcher already takes.
     */
    public static SodiumSupport.Gpu read(Context context) {
        SodiumSupport.GpuFamily family = SodiumSupport.GpuFamily.UNKNOWN;
        int glesMajor = 0;
        boolean adreno = false;
        try {
            GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
            if (info != null) {
                family = SodiumSupport.familyOf(info.vendor, info.renderer);
                glesMajor = info.glesMajorVersion;
                // Taken from GLInfo itself rather than re-derived from the family, because this is
                // the exact predicate JREUtils gates Turnip loading on.
                adreno = info.isAdreno();
            }
        } catch (Throwable ignored) {
            // A device that will not answer is reported as unknown, not as a device with nothing.
        }

        // Assumed present when it cannot be read: believing a failed query would condemn Zink on
        // every device whose package manager will not answer.
        boolean vulkan = true;
        try {
            if (context != null) vulkan = Tools.checkVulkanSupport(context.getPackageManager());
        } catch (Throwable ignored) {
        }

        // Computed exactly as JREUtils does it, so a verdict cannot promise a driver the launch
        // will not load: Turnip is only ever loaded inside an isAdreno() branch there.
        boolean turnipActive = false;
        if (adreno && context != null) {
            try {
                turnipActive = TurnipDrivers
                        .resolve(context, LauncherPreferences.PREF_TURNIP_DRIVER).loadTurnip;
            } catch (Throwable ignored) {
            }
        }

        return new SodiumSupport.Gpu(family, glesMajor, vulkan, turnipActive);
    }

    /**
     * A one line description of the device for the launch log.
     *
     * The log header is what every bug report this project receives is built from, and "Sodium on
     * Zink" means two completely different things on Adreno with Turnip and on stock Mali. Reads
     * nothing itself: it is handed the profile that was already read.
     */
    public static String describe(SodiumSupport.Gpu gpu) {
        if (gpu == null) return "gpu unavailable";
        StringBuilder out = new StringBuilder();
        out.append("gpu ").append(gpu.family);
        out.append(", gles ").append(gpu.glesMajor > 0 ? String.valueOf(gpu.glesMajor) : "unknown");
        out.append(", vulkan ").append(gpu.vulkan ? "yes" : "no");
        out.append(", turnip ").append(gpu.turnipActive ? "active" : "off");
        return out.toString();
    }
}
