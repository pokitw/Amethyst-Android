package net.kdt.pojavlaunch.optimiser;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.GLInfoUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What this phone actually is, worked out once, in the terms that decide frame rate.
 *
 * <b>The GPU is the answer, not the chip name.</b> A launcher can read {@code Build.SOC_MODEL} on
 * API 31 and up and get "SM8650", which is a fact about a part number and not about rendering. The
 * string that matters is GL_RENDERER, which the launcher already queries for its renderer list:
 * "Adreno (TM) 750" names the exact graphics hardware Minecraft will be translated onto, on every
 * Android version, with no permission and no allowlist of phone models to maintain. The model
 * number in it is monotonic within a family, so it tiers itself.
 *
 * <b>Pixels are the other answer.</b> The single biggest lever on a modern phone is not the chip,
 * it is that flagship panels are far denser than the game needs: a 1440 by 3168 screen is four and
 * a half million pixels, more than twice a 1080p one, and Minecraft pays for every one of them
 * every frame. So the panel is measured rather than assumed, and the plan works back from a pixel
 * budget instead of picking a percentage out of the air.
 *
 * Everything here is read once and cached in the caller: none of it is snapshot state, and
 * reading it inside a composition would re-run it every frame (handbook 16.11).
 */
public final class DeviceProfile {

    /** The graphics families that need different advice. Anything else is {@link #UNKNOWN}. */
    public enum Gpu { ADRENO, MALI, XCLIPSE, POWERVR, UNKNOWN }

    /**
     * How much headroom the device has, which decides how hard the plan pushes.
     *
     * Four steps rather than a score, because every recommendation downstream is a discrete
     * choice and a continuous number would only be re-bucketed. An unrecognised device lands on
     * {@link #MID}: the conservative end would make a strong unknown phone look broken, and the
     * optimistic end would make a weak one unplayable, and of the two mistakes the middle is the
     * one that is merely wrong rather than harmful.
     */
    public enum Tier { LOW, MID, HIGH, FLAGSHIP }

    public final Gpu gpu;
    /** The model number inside the GL renderer string ("Adreno (TM) 750" gives 750), or 0. */
    public final int gpuModel;
    /** GL_RENDERER verbatim, for the report the player can read. */
    public final String gpuName;
    public final int glesMajorVersion;
    /** The system on a chip, where Android will say ("SM8650"), else an empty string. */
    public final String soc;
    public final String deviceName;
    public final int totalMemoryMb;
    public final int cpuCores;
    /** The panel, in real pixels, largest side first. */
    public final int screenWidth;
    public final int screenHeight;
    public final float refreshRate;
    public final Tier tier;

    private DeviceProfile(Gpu gpu, int gpuModel, String gpuName, int glesMajorVersion, String soc,
                          String deviceName, int totalMemoryMb, int cpuCores,
                          int screenWidth, int screenHeight, float refreshRate, Tier tier) {
        this.gpu = gpu;
        this.gpuModel = gpuModel;
        this.gpuName = gpuName;
        this.glesMajorVersion = glesMajorVersion;
        this.soc = soc;
        this.deviceName = deviceName;
        this.totalMemoryMb = totalMemoryMb;
        this.cpuCores = cpuCores;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.refreshRate = refreshRate;
        this.tier = tier;
    }

    /** Total pixels the panel asks for at full scale. */
    public long pixels() {
        return (long) screenWidth * (long) screenHeight;
    }

    public boolean isAdreno() {
        return gpu == Gpu.ADRENO;
    }

    /**
     * Read the device.
     *
     * Every step is individually wrapped: a launcher that cannot work out its own refresh rate
     * should offer a slightly worse plan, never fail to offer one. An [Activity] gives the real
     * panel size; any other context still gives everything else.
     */
    @NonNull
    public static DeviceProfile detect(@NonNull Context context) {
        String gpuName = "";
        int gles = 0;
        try {
            GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
            if (info != null) {
                gpuName = info.renderer == null ? "" : info.renderer;
                gles = info.glesMajorVersion;
            }
        } catch (Throwable ignored) {
            // GL info is queried by creating a context; on a device where that fails the rest of
            // the profile is still worth having.
        }

        Gpu gpu = familyOf(gpuName);
        int model = modelOf(gpu, gpuName);

        String soc = "";
        try {
            if (Build.VERSION.SDK_INT >= 31) soc = orEmpty(Build.SOC_MODEL);
            // Below API 31 there is no SOC_MODEL. BOARD is the closest thing the platform offers
            // and is often the same string; it is only ever shown, never branched on.
            if (soc.isEmpty()) soc = orEmpty(Build.BOARD);
        } catch (Throwable ignored) {
        }

        String name = (orEmpty(Build.MANUFACTURER) + " " + orEmpty(Build.MODEL)).trim();

        int memory = 4096;
        try {
            memory = Tools.getTotalDeviceMemory(context);
        } catch (Throwable ignored) {
        }

        int cores = 4;
        try {
            cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        } catch (Throwable ignored) {
        }

        int width = 1920;
        int height = 1080;
        float refresh = 60f;
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            Display display = null;
            if (context instanceof Activity && Build.VERSION.SDK_INT >= 30) {
                display = ((Activity) context).getDisplay();
            }
            if (display == null) {
                WindowManager manager =
                        (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (manager != null) display = manager.getDefaultDisplay();
            }
            if (display != null) {
                display.getRealMetrics(metrics);
                // Largest side first, so the numbers mean the same thing whichever way the phone
                // was being held when this ran.
                width = Math.max(metrics.widthPixels, metrics.heightPixels);
                height = Math.min(metrics.widthPixels, metrics.heightPixels);
                float reported = display.getRefreshRate();
                if (reported > 20f && reported < 500f) refresh = reported;
            }
        } catch (Throwable ignored) {
        }

        return new DeviceProfile(gpu, model, gpuName, gles, soc, name, memory, cores,
                width, height, refresh, tierOf(gpu, model, memory, cores));
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static Gpu familyOf(@NonNull String renderer) {
        String lower = renderer.toLowerCase(Locale.ROOT);
        if (lower.contains("adreno")) return Gpu.ADRENO;
        // Checked before Mali: Samsung's Xclipse reports as "Samsung Xclipse 920" and is an RDNA
        // part, not a Mali one, and it wants different advice from either.
        if (lower.contains("xclipse")) return Gpu.XCLIPSE;
        if (lower.contains("mali")) return Gpu.MALI;
        if (lower.contains("powervr") || lower.contains("img")) return Gpu.POWERVR;
        return Gpu.UNKNOWN;
    }

    private static final Pattern ADRENO_MODEL = Pattern.compile("adreno[^0-9]*([0-9]{3})");
    private static final Pattern MALI_MODEL = Pattern.compile("mali[^0-9]*g?([0-9]{2,3})");
    private static final Pattern XCLIPSE_MODEL = Pattern.compile("xclipse[^0-9]*([0-9]{3})");

    /** The number inside the renderer string, which is what makes the tiering self-maintaining. */
    static int modelOf(@NonNull Gpu gpu, @NonNull String renderer) {
        String lower = renderer.toLowerCase(Locale.ROOT);
        Pattern pattern;
        switch (gpu) {
            case ADRENO: pattern = ADRENO_MODEL; break;
            case MALI: pattern = MALI_MODEL; break;
            case XCLIPSE: pattern = XCLIPSE_MODEL; break;
            default: return 0;
        }
        Matcher matcher = pattern.matcher(lower);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Which tier a device lands in.
     *
     * <b>The GPU decides, and the rest can only pull it down.</b> A phone with a strong GPU and
     * 4GB of RAM will still stutter, so memory and core count veto; but a weak GPU with 16GB of
     * RAM is not a flagship, so they never promote. That asymmetry is the whole rule.
     *
     * The Adreno numbers are read as families rather than as a scale: 7xx is the current
     * flagship line, the 6xx line spans four years and splits inside itself, and anything 5xx or
     * below predates the hardware Minecraft on Android is worth tuning for. Mali is bucketed the
     * same way but one step lower for the same number, which is not snobbery: the OpenGL
     * translation layers this launcher ships are all developed and tested against Adreno first,
     * and the Turnip driver path does not exist at all anywhere else.
     */
    static Tier tierOf(@NonNull Gpu gpu, int model, int memoryMb, int cores) {
        Tier base;
        switch (gpu) {
            case ADRENO:
                if (model >= 730) base = Tier.FLAGSHIP;
                else if (model >= 640) base = Tier.HIGH;
                else if (model >= 615) base = Tier.MID;
                else if (model > 0) base = Tier.LOW;
                else base = Tier.MID;
                break;
            case XCLIPSE:
                base = model >= 940 ? Tier.FLAGSHIP : Tier.HIGH;
                break;
            case MALI:
                // Immortalis and the G7xx line report three digits; the older G5x/G7x parts two.
                if (model >= 715) base = Tier.HIGH;
                else if (model >= 610 || (model >= 76 && model <= 78)) base = Tier.MID;
                else if (model > 0) base = Tier.LOW;
                else base = Tier.MID;
                break;
            case POWERVR:
                base = Tier.LOW;
                break;
            default:
                base = Tier.MID;
                break;
        }

        // The vetoes. Minecraft's heap plus the JVM plus Android leaves very little on a 4GB
        // phone whatever the GPU can do, and a four-core device cannot hide world generation
        // behind rendering.
        if (memoryMb < 4096 && base.ordinal() > Tier.LOW.ordinal()) base = Tier.LOW;
        else if (memoryMb < 6144 && base.ordinal() > Tier.MID.ordinal()) base = Tier.MID;
        if (cores <= 4 && base.ordinal() > Tier.MID.ordinal()) base = Tier.MID;
        return base;
    }
}
