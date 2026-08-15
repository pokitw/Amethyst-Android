#!/bin/sh
# Compile the shipped Discord presence card and check what it would publish.
#
# The card is a public profile, so the two failures that matter are both silent and both permanent
# once seen: a field naming the wrong Minecraft version, and a field naming the player. Whether the
# presence actually lands is the Discord app's business and needs a device; what it SAYS is string
# work, and that is checkable here.
#
# PresenceCard has no Android imports, on purpose, so nothing has to be stubbed out.
#
# Mutations this is known to catch:
#
#   * the loader list stops being most-specific-first  (every NeoForge profile reports as Forge)
#   * the version is searched for rather than parsed   ("1.66" out of neoforge-21.1.66, a version
#                                                       that has never existed, in public)
#   * a snapshot is accepted as a release              (the card names a version that is not the
#                                                       one being played)
#   * inheritsFrom is trusted without checking it      (any string on the card)
#   * the NeoForge early return is removed             (the build number becomes the version)
#   * the field truncation is removed                  (Discord drops an over-long field, so the
#                                                       card silently never appears)
#   * truncation is made off by one                    (the same, at exactly the limit)
#   * an account name is added to either field         (an identity published to strangers)
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
    "$SRC/net/kdt/pojavlaunch/discord/PresenceCard.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.discord.Harness
