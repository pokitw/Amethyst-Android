#!/bin/sh
# Compile the shipped game viewport geometry and drive it.
#
# This is the arithmetic behind an accessibility setting that moves the whole game, its HUD and
# every control into a smaller rectangle. Being wrong here is silent: the game is drawn a few
# pixels off the panel, or the controls stop lining up with the picture they belong to, and
# neither shows up anywhere except on a device. GameViewport is plain Java with no Android types
# in it precisely so this can compile the real file rather than a copy of it.
#
# The check that matters most is the first one: 100% has to be byte-for-byte the layout every
# existing player already has, since everybody who never opens the setting depends on it.
#
# Mutations this is known to catch:
#
#   * the percent clamp is removed              (a hand-edited preference draws off the screen)
#   * rounding replaced by truncation           (every stop lands a pixel below where it should)
#   * row and column are swapped                (every anchor names the wrong corner)
#   * the halving of the leftover space is lost (the middle anchors sit against an edge)
#   * the aspect ratio is broken on one axis    (the world renders stretched)
#   * an out-of-range position is not guarded   (a bad preference throws on the launch path)
#   * a negative screen size is not guarded     (a pre-measure layout pass throws)
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

javac -nowarn -source 8 -target 8 \
    -sourcepath "$SRC:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/customcontrols/GameViewport.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.customcontrols.Harness
