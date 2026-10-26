package net.kdt.pojavlaunch.utils;

/**
 * Stub mirroring the real class's shape, including that getGlInfo() can be made to throw.
 *
 * The real one catches Throwable internally and falls back to a dummy GLInfo, but a stub that can
 * only succeed tests nothing about the guard around it (16.16).
 */
public class GLInfoUtils {
    public static GLInfo NEXT = new GLInfo("Qualcomm", "Adreno (TM) 740", 3);
    public static boolean THROW = false;

    public static GLInfo getGlInfo() {
        if (THROW) throw new RuntimeException("EGL would not initialise");
        return NEXT;
    }

    public static class GLInfo {
        public final String vendor;
        public final String renderer;
        public final int glesMajorVersion;

        public GLInfo(String vendor, String renderer, int glesMajorVersion) {
            this.vendor = vendor;
            this.renderer = renderer;
            this.glesMajorVersion = glesMajorVersion;
        }

        /** Verbatim from the shipped class: this is the predicate Turnip loading is gated on. */
        public boolean isAdreno() {
            return renderer.contains("Adreno") && vendor.equals("Qualcomm");
        }
    }
}
