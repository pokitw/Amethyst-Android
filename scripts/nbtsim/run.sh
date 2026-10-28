#!/usr/bin/env bash
# Write a level.dat the way Minecraft does, then read it back through the shipped NbtReader.
#
# Binary format parsing is wrong in ways reading cannot catch — a byte array skipped by one byte
# shifts everything after it — and there is no device in CI. So the reader is Java, and this
# compiles the real source against a fixture it generates.
#
# Usage: scripts/nbtsim/run.sh
# Known NOT to be caught, said here rather than left to be discovered:
#
#   Replacing the final rename with an in-place write. Both land the same verified bytes and
#   the only difference is the window in which level.dat is truncated, which exists solely
#   while the process is being killed. Nothing a harness can observe; it is in the file as a
#   comment on the line, and the line is one sentence long.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

mkdir -p "$work/src/net/kdt/pojavlaunch/ui/content"
cp -r "$here/stubs/." "$work/src/"
for f in NbtReader NbtEditor; do
  cp "$repo/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/ui/content/$f.java" \
     "$work/src/net/kdt/pojavlaunch/ui/content/"
done
cp "$here/NbtSim.java" "$work/src/"

javac -nowarn -d "$work/out" $(find "$work/src" -name '*.java')
java -cp "$work/out" NbtSim
