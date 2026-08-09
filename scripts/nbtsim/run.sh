#!/usr/bin/env bash
# Write a level.dat the way Minecraft does, then read it back through the shipped NbtReader.
#
# Binary format parsing is wrong in ways reading cannot catch — a byte array skipped by one byte
# shifts everything after it — and there is no device in CI. So the reader is Java, and this
# compiles the real source against a fixture it generates.
#
# Usage: scripts/nbtsim/run.sh
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

mkdir -p "$work/src/net/kdt/pojavlaunch/ui/content"
cp -r "$here/stubs/." "$work/src/"
cp "$repo/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/ui/content/NbtReader.java" \
   "$work/src/net/kdt/pojavlaunch/ui/content/"
cp "$here/NbtSim.java" "$work/src/"

javac -nowarn -d "$work/out" $(find "$work/src" -name '*.java')
java -cp "$work/out" NbtSim
