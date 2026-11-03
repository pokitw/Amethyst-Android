#!/bin/sh
# Compile the shipped LunarApi and ClientPlan at source 8 and drive them with fixtures.
#
# The fixtures copy the SHAPE of what Lunar's launcher API sends, captured from real responses,
# with placeholder values; nothing of Lunar's is committed. What breaks silently here is a
# refusal read as a launch, an x86 native bundle downloaded, a name from the server that climbs
# out of its folder, a JVM flag that stops the runtime before a line of Java runs, and an LWJGL
# jar left in place that cannot work on Android. Each is a check below.
#
# Mutations it is known to catch (each was made and seen to fail before this was trusted):
#   downloads() keeps NATIVES; safeChild() lets ".." through; jvmArguments() keeps a module the
#   runtime lacks; a dropped flag leaks its value; a null token is passed as null; seeds include
#   classifier jars; the folder rule compares minor versions as strings; assets are not
#   de-duplicated.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

if [ -z "$GSON_JAR" ]; then
    GSON_JAR=$(ls "$ROOT"/app_pojavlauncher/libs/gson-*.jar 2>/dev/null | head -1)
fi
if [ -z "$GSON_JAR" ]; then
    GSON_JAR=$(find "$HOME/.gradle" "$HOME/.m2" "$HOME/.minecraft" -name 'gson-2.[89]*.jar' -o -name 'gson-2.1*.jar' 2>/dev/null \
        | grep -v sources | head -1)
fi
if [ -z "$GSON_JAR" ] || [ ! -f "$GSON_JAR" ]; then
    echo "skipping: no gson jar found (set GSON_JAR to one)"
    exit 0
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes"
javac -nowarn -source 8 -target 8 -Xlint:-options -encoding UTF-8 -cp "$GSON_JAR" -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/clients/LunarApi.java" \
    "$SRC/net/kdt/pojavlaunch/clients/ClientPlan.java" \
    "$HERE/Drive.java"
java -cp "$OUT/classes:$GSON_JAR" Drive "$HERE/fixtures"
