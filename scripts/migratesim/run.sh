#!/bin/sh
# Compile the shipped mod migration planner and drive it.
#
# ModMigration has no Android imports and does no I/O on purpose, so this compiles the real class
# rather than a copy of its rules (16.25). Neither failure this guards against is visible: a wrong
# identity installs a mod nobody chose, cleanly, and a wrong disable takes a working mod out of the
# game and reads as the new Minecraft version having broken it.
#
# Mutations this is known to catch:
#
#   * identity falls back to a file name or a search ranking  (somebody else's mod is installed
#                                                              under a name the player will trust)
#   * a null hash counts as a match                           (same, for every jar the launcher
#                                                              could not read)
#   * the ALREADY check is dropped                            (running it twice re-downloads every
#                                                              mod and disables what the first run
#                                                              installed: the setup is destroyed
#                                                              rather than merely not improved)
#   * the release preference is dropped                       (a working set is quietly moved onto
#                                                              prereleases)
#   * the same-name guard on disabling is dropped             (the jar just installed is renamed
#                                                              out of the game)
#   * the shared-folder guard on disabling is dropped         (another profile's mods are disabled)
#   * an unidentified jar carries an install                  (a guess becomes a download)
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
    "$SRC/net/kdt/pojavlaunch/modmigrate/ModMigration.java" \
    "$HERE/Drive.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.modmigrate.Drive
