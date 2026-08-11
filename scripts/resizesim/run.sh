#!/bin/sh
# Compile the shipped resize arithmetic and drive it.
#
# resolveSize is static and view-free precisely so this can exist: there is no device in CI to
# drag a control corner on, and the two ways it can be wrong (a control reaching zero, a step
# rounding back under the floor) both leave a button in the layout that cannot be selected again.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/src/net/kdt/pojavlaunch/customcontrols/handleview" "$OUT/classes"

# resolveSize and the two constants, lifted verbatim from the shipped file rather than retyped:
# the class itself drags in the whole Android view stack, and a copy typed by hand would be a
# harness checking its own arithmetic instead of the launcher's.
python3 - "$SRC/net/kdt/pojavlaunch/customcontrols/handleview/ControlHandleView.java" \
    "$OUT/src/net/kdt/pojavlaunch/customcontrols/handleview/ControlHandleView.java" <<'PY'
import re, sys
source = open(sys.argv[1]).read()

def grab(pattern, what):
    m = re.search(pattern, source)
    if not m:
        sys.exit("could not find " + what + " in the shipped file")
    return m.group(0)

minimum = grab(r'public static final float MIN_SIZE_DP = [^;]+;', 'MIN_SIZE_DP')
step = grab(r'public static final float STEP_DP = [^;]+;', 'STEP_DP')
resolve = grab(
    r'public static float resolveSize\(float rawPx, float minPx, float stepPx\) \{'
    r'(?:[^{}]|\{[^{}]*\})*\}', 'resolveSize')

open(sys.argv[2], 'w').write(
    "package net.kdt.pojavlaunch.customcontrols.handleview;\n"
    "public class ControlHandleView {\n    " + minimum + "\n    " + step + "\n    "
    + resolve + "\n}\n")
PY

javac -nowarn -source 8 -target 8 \
    -sourcepath "$OUT/src:$HERE" \
    -d "$OUT/classes" \
    "$OUT/src/net/kdt/pojavlaunch/customcontrols/handleview/ControlHandleView.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.customcontrols.handleview.Harness
