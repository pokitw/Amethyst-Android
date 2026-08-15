#!/bin/sh
# Compile the shipped heap advice and sweep it across the device population.
#
# Three numbers used to live in three files: the ceiling the memory slider enforces, the default a
# fresh install gets, and the bound the launch check tests against. Nothing made them agree, and a
# launch screen that refuses a value Settings offers is not a bug anybody would think to look for.
# They are one class now, and this drives that exact class: there is no device in CI, so a bound
# that nags every launch and one that never fires at all look identical from here otherwise.
#
# Mutations this is known to catch:
#
#   * the ceiling loses its 32-bit branch          (a 32-bit device is offered 6 GB it cannot map)
#   * the ceiling drops its system headroom        (Settings offers the whole device to one heap)
#   * the ceiling loses its floor                  (a device that will not report its memory gets
#                                                   a slider whose maximum is its minimum)
#   * the availability arm compares the wrong way  (it warns on healthy phones and stays quiet
#                                                   on starved ones)
#   * a zero free-memory reading is believed       (every device that will not answer warns
#                                                   on every single launch)
#   * the fix offers the ceiling, not the          (technically clears the warning, so only a
#     recommendation                                direct assertion on the number can see it)
#   * the fix ignores what is free                 (the fix for one warning raises the other and
#                                                   the dialog comes straight back)
#   * snapDown rounds up                           (the fix is a value the slider cannot show)
#   * the too-small-to-play floor is dropped       (a heap Minecraft cannot reach a title on,
#                                                   offered as the fix)
#   * either bound is turned into >=               (off by one megabyte at both boundaries)
#
# Two mutations are NOT caught, and both are the check reporting the truth rather than a hole:
#
#   * recommended() loses its clamp to the ceiling
#   * a tier is raised above its own ceiling
#
# With today's constants no tier comes near its ceiling, so the clamp never fires and removing it
# changes no number on any device; and with the clamp in place, raising a tier is silently
# corrected back down, which is the clamp doing its job. Making BOTH edits at once IS caught, by
# the every-megabyte invariant sweep. This is resizesim's situation exactly: the guard and the
# constants each hide the other's removal, so the sweep is written to cover the pair.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

# HeapAdvice has no Android imports, on purpose, so it compiles here with nothing stubbed out.
javac -nowarn -source 8 -target 8 \
    -sourcepath "$SRC:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/prefs/HeapAdvice.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.prefs.Harness
