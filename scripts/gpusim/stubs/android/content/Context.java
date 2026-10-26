package android.content;

import android.content.pm.PackageManager;

/** Stub. Returns whatever the test set, and throws when the test says the platform would. */
public class Context {
    public static boolean THROW_ON_PACKAGE_MANAGER = false;
    public PackageManager packageManager = new PackageManager();

    public PackageManager getPackageManager() {
        if (THROW_ON_PACKAGE_MANAGER) {
            // A dead Context really does throw here, and GpuProfile runs on the launch path.
            throw new IllegalStateException("no package manager");
        }
        return packageManager;
    }
}
