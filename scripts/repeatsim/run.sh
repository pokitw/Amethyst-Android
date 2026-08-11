#!/bin/sh
# Compile the shipped slide-to-repeat arithmetic and drive it.
#
# The four functions are static and free of any view precisely so this can exist. There is no
# device in CI to slide a thumb across a button on, and the two ways this feature can be wrong are
# both quiet: a threshold measured per axis instead of radially arms at a different distance
# depending on which way you happened to slide, and a gap that can come out under one game tick
# sends presses Minecraft cannot see, which looks exactly like a slow phone.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/src/net/kdt/pojavlaunch/customcontrols" "$OUT/classes"

# Lifted verbatim from the shipped file rather than retyped: ControlData drags in exp4j, the
# Android view stack and CallbackBridge, and a copy typed by hand would be a harness checking its
# own arithmetic instead of the launcher's.
python3 - "$SRC/net/kdt/pojavlaunch/customcontrols/ControlData.java" \
    "$OUT/src/net/kdt/pojavlaunch/customcontrols/ControlData.java" <<'PY'
import re, sys
source = open(sys.argv[1]).read()

def grab(pattern, what):
    m = re.search(pattern, source)
    if not m:
        sys.exit("could not find " + what + " in the shipped file")
    return m.group(0)

def method(signature, what):
    return grab(re.escape(signature) + r' \{(?:[^{}]|\{[^{}]*\})*\}', what)

parts = [
    grab(r'public static final float DEFAULT_SLIDE_DP = [^;]+;', 'DEFAULT_SLIDE_DP'),
    grab(r'public static final int REPEAT_GAP_FLOOR_MS = [^;]+;', 'REPEAT_GAP_FLOOR_MS'),
    method('public static float slideDistanceDp(float configured)', 'slideDistanceDp'),
    method('public static int repeatGapMs(int configured)', 'repeatGapMs'),
    method('public static int repeatHalfGapMs(int configured)', 'repeatHalfGapMs'),
    method('public static boolean pastSlideThreshold(float dx, float dy, float thresholdPx)',
           'pastSlideThreshold'),
]

open(sys.argv[2], 'w').write(
    "package net.kdt.pojavlaunch.customcontrols;\n"
    "public class ControlData {\n    " + "\n    ".join(parts) + "\n}\n")
PY

javac -nowarn -source 8 -target 8 \
    -sourcepath "$OUT/src:$HERE" \
    -d "$OUT/classes" \
    "$OUT/src/net/kdt/pojavlaunch/customcontrols/ControlData.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.customcontrols.Harness
