package net.kdt.pojavlaunch;

import android.content.pm.PackageManager;

/** Stub carrying only what GpuProfile touches, with the real signature. */
public final class Tools {
    public static boolean checkVulkanSupport(PackageManager packageManager) {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
                && packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
    }
}
