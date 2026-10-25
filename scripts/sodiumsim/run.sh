#!/bin/sh
# Compile the shipped Sodium support decision and drive it.
#
# The launcher used to refuse Sodium outright, and then lost the way past the refusal: the
# preference that skipped the dialog survived the Compose settings rewrite and the switch that set
# it did not, so "Delete Sodium and Launch" became the only button. That made shader packs
# impossible, since Iris requires Sodium.
#
# The replacement is a verdict about the RENDERER rather than about the mod, and it is wrong in two
# silent directions. Too strict and somebody is told a working setup is broken, which is the bug
# being fixed. Too loose and somebody is waved into a black screen with no explanation.
#
# SodiumSupport has no Android imports, on purpose, so nothing has to be stubbed out.
#
# Mutations this is known to catch:
#
#   * Zink stops reading as supported          (the nagging comes back for the people who already
#                                               did the right thing, on every single launch)
#   * gl4es stops reading as unsupported       (the one factual refusal becomes a shrug, and the
#                                               launcher stops naming the actual problem)
#   * an unknown renderer defaults to          (every future renderer reinstates the dead end)
#     UNSUPPORTED
#   * ABSENT stops being returned when no      (a profile with no Sodium is warned about)
#     Sodium is installed
#   * shadersViable widens to the translators  (somebody is sent to install Iris on a renderer
#                                               that cannot carry a shader pack)
#   * betterRenderer ignores availability      (a device with no Vulkan is offered Zink)
#   * betterRenderer suggests Zink to Zink     (a dialog offering the renderer already in use)
#   * the id is no longer lowercased           (a hand-edited profile silently misreads)
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
    "$SRC/net/kdt/pojavlaunch/sodium/SodiumSupport.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.sodium.Harness
