package net.kdt.pojavlaunch.recorder;

import java.io.File;

/** Stub. The real one parses JSON and is not what this harness is about. */
public class RecordingInfo {
    /** Set to make read() behave as the real one does when there is no sidecar. */
    public static boolean PRESENT = true;
    /** Set to make write() throw, so the caller's guard can be exercised. */
    public static boolean WRITE_THROWS = false;
    public static File LAST_WRITTEN = null;

    public static RecordingInfo read(File recording) {
        if (!PRESENT) return null;
        return new RecordingInfo();
    }

    public void write(File recording) {
        if (WRITE_THROWS) throw new RuntimeException("stub refuses to write a sidecar");
        LAST_WRITTEN = recording;
    }
}
