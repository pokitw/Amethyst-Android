#!/bin/sh
# Compile the shipped performance plan and drive it through real device and profile combinations.
#
# The plan is a pure function on purpose, which is what makes this possible: there is no device in
# CI, but the decision itself is the thing that can be wrong, and every way it can be wrong is
# silent. A resolution solved against the wrong panel looks like the feature doing nothing; a
# graphicsMode written into a 1.12 profile is a key that version has never heard of and is dropped
# on the next save; a bound the wrong way round quietly RAISES a setting the player lowered, in a
# mode called performance.
#
# The R stub is generated from the source's own R.string references, so the harness cannot drift
# from the class. That the names exist as real strings is checked separately by checkres.py.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"
PLAN="$SRC/net/kdt/pojavlaunch/optimiser/PerformancePlan.java"

rm -rf "$OUT"
mkdir -p "$OUT/stub/androidx/annotation" "$OUT/stub/net/kdt/pojavlaunch" "$OUT/classes"

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

# DeviceProfile brings its own Android surface with it; only the enums are wanted here, but the
# class has to compile for them to exist.
mkdir -p "$OUT/stub/android/app" "$OUT/stub/android/content" "$OUT/stub/android/os" \
    "$OUT/stub/android/util" "$OUT/stub/android/view" "$OUT/stub/net/kdt/pojavlaunch/utils"
cat > "$OUT/stub/android/os/Build.java" <<'STUB'
package android.os;
public class Build {
    public static String MANUFACTURER = "OnePlus", MODEL = "CPH2581", BOARD = "pineapple";
    public static String SOC_MODEL = "SM8650";
    public static class VERSION { public static int SDK_INT = 34; }
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
    public static int getTotalDeviceMemory(android.content.Context ctx) { return 12288; }
}
STUB
cat > "$OUT/stub/net/kdt/pojavlaunch/utils/GLInfoUtils.java" <<'STUB'
package net.kdt.pojavlaunch.utils;
public class GLInfoUtils {
    public static class GLInfo {
        public String vendor = "Qualcomm", renderer = "Adreno (TM) 750";
        public int glesMajorVersion = 3;
        public boolean isAdreno() { return true; }
    }
    public static GLInfo getGlInfo() { return new GLInfo(); }
}
STUB

# R, generated from what the class actually references.
{
    echo 'package net.kdt.pojavlaunch;'
    echo 'public final class R {'
    echo '    public static final class string {'
    grep -o 'R\.string\.[A-Za-z0-9_]*' "$PLAN" | sed 's/R\.string\.//' | sort -u | \
        awk '{ print "        public static final int " $0 " = " (NR + 1000) ";" }'
    echo '    }'
    echo '}'
} > "$OUT/stub/net/kdt/pojavlaunch/R.java"

javac -nowarn -source 8 -target 8 \
    -sourcepath "$OUT/stub:$SRC:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/optimiser/DeviceProfile.java" \
    "$PLAN" \
    "$HERE/PlanHarness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.optimiser.PlanHarness
