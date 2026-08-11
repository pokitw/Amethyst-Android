#!/bin/sh
# Compile the shipped loader index and drive its parsers against the shapes those APIs send.
#
# None of meta.fabricmc.net, meta.quiltmc.org, maven.minecraftforge.net or maven.neoforged.net is
# reachable from the build container, so the parse is the only checkable part and it is where being
# wrong is silent. A Forge version split on the wrong hyphen files builds under a Minecraft version
# that has never existed; a NeoForge version read by the wrong rule hides every build it has.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

# Gson rather than org.json, for the reason PerformanceBackup already settled: Gson is a jar this
# app ships, so the harness drives the shipped class against the same library the phone uses.
# org.json lives inside the Android framework and cannot be compiled against here at all.
JSON_JAR=$(ls "$ROOT"/app_pojavlauncher/libs/gson-*.jar 2>/dev/null | head -1)
if [ -z "$JSON_JAR" ] || [ ! -f "$JSON_JAR" ]; then
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
    -cp "$JSON_JAR" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/modloaders/LoaderIndex.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes:$JSON_JAR" net.kdt.pojavlaunch.modloaders.Harness
