package android.content.pm;

/** Stub modelling the platform's refusals as well as its answers (16.16). */
public class PackageManager {
    public static final String FEATURE_VULKAN_HARDWARE_LEVEL =
            "android.hardware.vulkan.level";
    public static final String FEATURE_VULKAN_HARDWARE_VERSION =
            "android.hardware.vulkan.version";

    public static boolean VULKAN = true;
    public static boolean THROW = false;

    public boolean hasSystemFeature(String name) {
        if (THROW) throw new RuntimeException("package manager died");
        return VULKAN;
    }
}
