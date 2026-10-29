#!/bin/sh
# Compile the shipped placement search and drive it.
#
# ControlPlacement has no Android imports on purpose, so this compiles the real class rather than
# a copy of its arithmetic (16.25). The failure it guards against is not cosmetic: a control
# placed outside the layout in the editor draws nothing and can never be selected again, which is
# the same permanent loss the resize grip's floor exists to prevent.
#
# Mutations this is known to catch:
#
#   * the clamp is removed                     (controls land off the edge on a crowded layout)
#   * the ring loop runs once                   (the second control stacks on the first again)
#   * the fallback returns the last candidate   (a full screen puts the control in a corner
#                                                nobody would look in)
#   * the centre offset is dropped              (every new control appears below and right of
#                                                where it should, on every screen)
#   * the overlap test is made strict           (the lattice rejects its own spacing, which only
#                                                shows at a zero gap, so there is a check with one)
#
# And one it deliberately cannot catch, stated rather than hidden: dropping the ring edge test
# re-tests the inside of every ring, which is slower and chooses the same spot, because those
# cells were already rejected by a smaller ring. It is not observable in the output, so no
# assertion here can see it.
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
    "$SRC/net/kdt/pojavlaunch/customcontrols/ControlPlacement.java" \
    "$HERE/Drive.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.customcontrols.Drive
