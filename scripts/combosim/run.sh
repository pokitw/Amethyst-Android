#!/bin/sh
# Compile the shipped key combination ordering and drive it.
#
# The bug: a control button holding Shift and F3 sent them in the order the editor's slots happened
# to be filled in. Bind F3 first, which is how anybody would describe the button, and F3 went out
# while nothing held Shift, so Minecraft opened the debug overlay and never the profiler chart. The
# same button with the slots the other way round worked, which is what made it look intermittent.
#
# Nothing crashed and no key was dropped, so there is nothing to assert here except the order.
# KeyCombo has no Android imports, on purpose, so nothing has to be stubbed out.
#
# Mutations this is known to catch:
#
#   * the ordering is removed and slots are sent as they come  (the original bug, both directions)
#   * the release is not reversed                              (the modifier comes up first, so the
#                                                               struck key's release is unmodified)
#   * the release reverses only within a group                 (same, less obviously)
#   * the right hand modifiers are dropped from isModifier     (a layout using Right Shift behaves
#                                                               differently from an identical one
#                                                               using Left Shift)
#   * Caps Lock is treated as a held modifier                  (a button gets reordered around a
#                                                               toggle, changing what it does)
#   * empty slots stop being dropped                           (keycode 0 is sent as a key)
#   * the partition stops being stable                         (two modifiers, or two ordinary
#                                                               keys, swap against the author)
#   * a key bound twice is deduplicated                        (the layout stops doing what it says)
#   * specials are hoisted like modifiers                      (a special fires before the key it
#                                                               was meant to follow)
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
    "$SRC/net/kdt/pojavlaunch/customcontrols/KeyCombo.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.customcontrols.Harness
