#!/bin/sh
# Compile the shipped native key path and ask the one question Minecraft asks.
#
# Minecraft decides the profiler pie chart on F3's RELEASE, with
# `renderDebugCharts = renderDebug && Screen.hasShiftDown()`, and hasShiftDown polls glfwGetKey,
# whose entire backing store is keyDownBuffer. So the bug reduces to one measurable thing: when
# F3's release callback is dispatched, does a poll of Shift still read PRESS?
#
# It used to read RELEASE, always. critical_send_key stamped keyDownBuffer at SEND time while
# deferring the callback to pojavPumpEvents, so every key one touch event produced reached its
# final state before the first callback was delivered. The order the keys were sent in could not
# be observed at all, which is why every "the array is in the wrong order" theory was wrong and
# why the on-screen keyboard worked: it holds Shift across frames, so its state survives the pump.
#
# The three functions are lifted VERBATIM out of input_bridge_v3.c by extract.py rather than
# retyped, because a hand-copied version would be a harness checking its own arithmetic.
#
# Mutations this is known to catch:
#
#   * the eager keyDownBuffer write is restored in the queued branch   (the shipped bug, exactly;
#                                                                       a one-line revert of the
#                                                                       whole fix)
#   * the buffer is written after the callback in the pump             (a key never reads as held
#                                                                       at its own press)
#   * the pump writes event.i2 or event.i4 instead of event.i3         (the action is not the
#                                                                       scancode or the mods)
#   * the max(0, ...) clamp is dropped                                 (a key below 31 writes
#                                                                       behind the buffer)
#   * the release order is not reversed by the caller                  (Shift comes up first and
#                                                                       the chart is lost again)
#   * the pump stops advancing its index                               (events replayed or lost)
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT"

python3 "$HERE/extract.py" "$ROOT/app_pojavlauncher/src/main/jni/input_bridge_v3.c" "$OUT/lifted.c"

cc -std=gnu11 -O0 -Wall -I"$HERE" -o "$OUT/chordsim" "$OUT/lifted.c" "$HERE/harness.c" -lm
"$OUT/chordsim"
