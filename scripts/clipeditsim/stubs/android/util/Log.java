package android.util;

/** Collects what the class under test logged, so a harness can assert on it. */
public final class Log {
    public static final StringBuilder OUTPUT = new StringBuilder();
    public static int w(String tag, String msg) { OUTPUT.append("W/").append(tag).append(' ').append(msg).append('\n'); return 0; }
    public static int w(String tag, String msg, Throwable t) { return w(tag, msg); }
    public static int d(String tag, String msg) { OUTPUT.append("D/").append(tag).append(' ').append(msg).append('\n'); return 0; }
    public static int e(String tag, String msg) { OUTPUT.append("E/").append(tag).append(' ').append(msg).append('\n'); return 0; }
    public static int e(String tag, String msg, Throwable t) { return e(tag, msg); }
    public static int i(String tag, String msg) { OUTPUT.append("I/").append(tag).append(' ').append(msg).append('\n'); return 0; }
}
