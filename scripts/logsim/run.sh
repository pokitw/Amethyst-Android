#!/bin/sh
# Compile the shipped log parser and drive it.
#
# The level filter is the one part of the log viewer that can be wrong without looking wrong: a
# line mis-read as INFO simply does not appear when the filter is set to errors, and a stack trace
# that fails to inherit its exception's level leaves a one-line error with its cause hidden. There
# is no device in CI and no Kotlin compiler in this container, which is why the parsing lives in
# Java: this compiles LogParser.java itself at source 8 and runs the real thing.
#
# The first draft of this check was Python that re-implemented the algorithm and read only the
# constants out of the source. It passed while six of eight deliberate mutations to the shipped
# code went unnoticed, because the thing being driven was the copy. Mutations this now catches:
#
#   * a line stops inheriting the level above it            (stack traces leave the error filter)
#   * the unbracketed error marks are skipped               (a JVM crash files itself as INFO)
#   * the prefix bound is removed                           (chat quoting ERROR files as an error)
#   * SEVERE is demoted                                     (java.util.logging errors disappear)
#   * the filter comparison is flipped                      (Errors shows everything but errors)
#   * blank lines are always dropped                        (the log reads unlike it was written)
#   * the search is made case sensitive                     (half the matches vanish)
#   * the level pattern loses its slash                     (every Minecraft line goes level-less)
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
SECURITY="$ROOT/app_pojavlauncher/src/main/assets/components/security"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

javac -nowarn -source 8 -target 8 \
    -sourcepath "$SRC:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/logs/LogParser.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.logs.Harness "$SECURITY"
