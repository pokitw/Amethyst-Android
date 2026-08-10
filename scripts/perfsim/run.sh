#!/bin/sh
# Compile the shipped performance-mode backup and drive it through capture and restore.
#
# Performance mode writes a dozen settings at once, and the only thing that makes that acceptable
# is being able to put them all back. The case this checks is the one an obvious implementation
# loses: a key that was never written is not a key holding its default, and restoring the first
# has to remove it rather than write a value.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

# The launcher ships a Gson in libs/, which is the copy the app itself compiles against, so the
# harness serialises with exactly the library the phone will.
if [ -z "$GSON_JAR" ]; then
    GSON_JAR=$(ls "$ROOT"/app_pojavlauncher/libs/gson-*.jar 2>/dev/null | head -1)
fi
if [ -z "$GSON_JAR" ] || [ ! -f "$GSON_JAR" ]; then
    echo "skipping: no gson jar found (set GSON_JAR to one)"
    exit 0
fi

rm -rf "$OUT"
mkdir -p "$OUT/stub/androidx/annotation" "$OUT/classes"
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

javac -nowarn -source 8 -target 8 \
    -cp "$GSON_JAR" \
    -sourcepath "$OUT/stub:$SRC:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/optimiser/PerformanceBackup.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes:$GSON_JAR" net.kdt.pojavlaunch.optimiser.Harness
