#!/bin/sh
# Compile the shipped options.txt merge and drive it against real files in a temp directory.
#
# The launcher edits a file the player owns and the game also writes. What this proves is that
# everything the launcher does not own survives the round trip: keybinds, resource pack lists,
# sound volumes, blank lines and all. There is no device in CI, so this is the only check.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/stub/android/util" "$OUT/stub/androidx/annotation" "$OUT/classes" "$OUT/work" \
    "$OUT/stub/android/app" "$OUT/stub/android/content" "$OUT/stub/android/os" \
    "$OUT/stub/android/view" "$OUT/stub/net/kdt/pojavlaunch" \
    "$OUT/stub/net/kdt/pojavlaunch/prefs" "$OUT/stub/net/kdt/pojavlaunch/utils"

cat > "$OUT/stub/android/util/Log.java" <<'STUB'
package android.util;
public class Log {
    public static int d(String t, String m) { return 0; }
    public static int i(String t, String m) { return 0; }
    public static int w(String t, String m) { return 0; }
    public static int w(String t, String m, Throwable e) { return 0; }
    public static int e(String t, String m) { return 0; }
    public static int e(String t, String m, Throwable e) { return 0; }
}
STUB
for A in NonNull Nullable; do
cat > "$OUT/stub/androidx/annotation/$A.java" <<STUB
package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE,
         ElementType.TYPE_USE})
public @interface $A {}
STUB
done

# The layer above the merge is performance mode itself, and compiling it drags in the real
# Modrinth client, so the surface it touches is stubbed here rather than the class being reduced
# to something a harness can hold. What is driven is still the shipped applyOptions.
cat > "$OUT/stub/android/content/SharedPreferences.java" <<'STUB'
package android.content;
import java.util.Map;
public interface SharedPreferences {
    interface Editor {
        Editor putBoolean(String k, boolean v);
        Editor putInt(String k, int v);
        Editor putString(String k, String v);
        Editor remove(String k);
        void apply();
    }
    Editor edit();
    boolean contains(String k);
    boolean getBoolean(String k, boolean d);
    int getInt(String k, int d);
    String getString(String k, String d);
    Map<String, ?> getAll();
}
STUB
cat > "$OUT/stub/android/content/Context.java" <<'STUB'
package android.content;
public class Context {
    public static final String WINDOW_SERVICE = "window";
    public Object getSystemService(String name) { return null; }
}
STUB
cat > "$OUT/stub/android/app/Activity.java" <<'STUB'
package android.app;
import android.view.Display;
public class Activity extends android.content.Context { public Display getDisplay() { return null; } }
STUB
cat > "$OUT/stub/android/os/Build.java" <<'STUB'
package android.os;
public class Build {
    public static String MANUFACTURER = "", MODEL = "", BOARD = "", SOC_MODEL = "";
    public static class VERSION { public static int SDK_INT = 34; }
}
STUB
cat > "$OUT/stub/android/util/DisplayMetrics.java" <<'STUB'
package android.util;
public class DisplayMetrics { public int widthPixels = 1080, heightPixels = 2400; }
STUB
cat > "$OUT/stub/android/view/Display.java" <<'STUB'
package android.view;
import android.util.DisplayMetrics;
public class Display {
    public void getRealMetrics(DisplayMetrics out) {}
    public float getRefreshRate() { return 60f; }
}
STUB
cat > "$OUT/stub/android/view/WindowManager.java" <<'STUB'
package android.view;
public class WindowManager { public Display getDefaultDisplay() { return null; } }
STUB
cat > "$OUT/stub/net/kdt/pojavlaunch/Tools.java" <<'STUB'
package net.kdt.pojavlaunch;
public class Tools {
    public static int getTotalDeviceMemory(android.content.Context c) { return 12288; }
    public static String read(java.io.InputStream in) throws java.io.IOException { return ""; }
}
STUB
cat > "$OUT/stub/net/kdt/pojavlaunch/utils/GLInfoUtils.java" <<'STUB'
package net.kdt.pojavlaunch.utils;
public class GLInfoUtils {
    public static class GLInfo {
        public String vendor = "", renderer = "Adreno (TM) 750";
        public int glesMajorVersion = 3;
        public boolean isAdreno() { return true; }
    }
    public static GLInfo getGlInfo() { return new GLInfo(); }
}
STUB
cat > "$OUT/stub/net/kdt/pojavlaunch/utils/DownloadUtils.java" <<'STUB'
package net.kdt.pojavlaunch.utils;
public class DownloadUtils {
    public static class ParseException extends Exception { public ParseException(Throwable t) { super(t); } }
    public static String downloadString(String url) throws java.io.IOException { return ""; }
    public static void downloadFile(String url, java.io.File to) throws java.io.IOException {}
}
STUB
cat > "$OUT/stub/net/kdt/pojavlaunch/prefs/LauncherPreferences.java" <<'STUB'
package net.kdt.pojavlaunch.prefs;
import android.content.SharedPreferences;
public class LauncherPreferences {
    public static SharedPreferences DEFAULT_PREF;
    public static void loadPreferences(android.content.Context c) {}
}
STUB
# R, generated from what the classes actually reference, so it cannot drift from them.
{
    echo 'package net.kdt.pojavlaunch;'
    echo 'public final class R {'
    echo '    public static final class string {'
    grep -oh 'R\.string\.[A-Za-z0-9_]*' "$SRC"/net/kdt/pojavlaunch/optimiser/*.java | \
        sed 's/R\.string\.//' | sort -u | \
        awk '{ print "        public static final int " $0 " = " (NR + 1000) ";" }'
    echo '    }'
    echo '}'
} > "$OUT/stub/net/kdt/pojavlaunch/R.java"

GSON_JAR=$(ls "$ROOT"/app_pojavlauncher/libs/gson-*.jar 2>/dev/null | head -1)

javac -nowarn -source 8 -target 8 \
    -sourcepath "$OUT/stub:$SRC:$HERE" \
    -cp "$GSON_JAR" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/optimiser/GameOptions.java" \
    "$SRC/net/kdt/pojavlaunch/optimiser/PerformancePlan.java" \
    "$SRC/net/kdt/pojavlaunch/optimiser/PerformanceMode.java" \
    "$HERE/Harness.java" \
    "$HERE/ApplyHarness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes:$GSON_JAR" net.kdt.pojavlaunch.optimiser.Harness "$OUT/work"
java -cp "$OUT/classes:$GSON_JAR" net.kdt.pojavlaunch.optimiser.ApplyHarness "$OUT/work"
