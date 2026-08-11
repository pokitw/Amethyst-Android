#!/bin/sh
# Compile the shipped Mojang skin parsers and drive them against fixtures.
#
# Mojang is not reachable from the build container, so the wire format is coded from the
# documented contract and this is the only check there is. It checks the part that fails silently:
# the skin URL lives inside a base64 blob in a properties array, which decodes to a second JSON
# document, and the slim flag is deeper still and absent rather than false for a classic skin.
#
# The Base64 stub is the platform's behaviour, not a shortcut: android.util.Base64 with DEFAULT
# decodes standard base64, and java.util.Base64 does the same thing, so the harness is exercising
# the real decode path rather than one that always succeeds (handbook lesson 16).
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

GSON_JAR=$(ls "$ROOT"/app_pojavlauncher/libs/gson-*.jar 2>/dev/null | head -1)
if [ -z "$GSON_JAR" ] || [ ! -f "$GSON_JAR" ]; then
    echo "skipping: no gson jar found"
    exit 0
fi

rm -rf "$OUT"
mkdir -p "$OUT/stub/android/util" "$OUT/stub/androidx/annotation" \
    "$OUT/stub/net/kdt/pojavlaunch" "$OUT/classes"

cat > "$OUT/stub/android/util/Log.java" <<'STUB'
package android.util;
public class Log {
    public static int w(String t, String m) { return 0; }
    public static int w(String t, String m, Throwable e) { return 0; }
    public static int i(String t, String m) { return 0; }
}
STUB
cat > "$OUT/stub/android/util/Base64.java" <<'STUB'
package android.util;
/**
 * The platform's behaviour, not a permissive stand-in: DEFAULT is standard base64 with padding,
 * and a body that is not base64 throws IllegalArgumentException exactly as Android's does. The
 * parser is supposed to survive that, so a stub that quietly returned empty bytes would be
 * testing the stub.
 */
public class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_WRAP = 2;
    public static byte[] decode(String input, int flags) {
        return java.util.Base64.getMimeDecoder().decode(input);
    }
    public static String encodeToString(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encodeToString(input);
    }
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
cat > "$OUT/stub/net/kdt/pojavlaunch/Tools.java" <<'STUB'
package net.kdt.pojavlaunch;
public class Tools {
    public static String read(java.io.InputStream in) throws java.io.IOException { return ""; }
}
STUB

javac -nowarn -source 8 -target 8 \
    -sourcepath "$OUT/stub:$SRC:$HERE" \
    -cp "$GSON_JAR" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/skin/MojangSkins.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes:$GSON_JAR" net.kdt.pojavlaunch.skin.Harness
