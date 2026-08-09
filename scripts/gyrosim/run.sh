#!/usr/bin/env bash
# Run the real GyroControl against synthetic motion and check what comes out.
#
# There is no device in CI and no Android SDK in the dev container, but GyroControl is ordinary
# arithmetic behind a handful of framework types — so those types are stubbed and the actual
# shipped source is compiled and driven. This catches the things that matter and cannot be seen by
# reading: whether a slow turn arrives one pixel at a time, whether a bias drifts the view, whether
# two devices sampling at different rates turn by the same amount.
#
# Usage: scripts/gyrosim/run.sh
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
src="$repo/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/customcontrols/mouse"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

mkdir -p "$work/src/net/kdt/pojavlaunch/customcontrols/mouse"
cp -r "$here/stubs/." "$work/src/"
cp "$src/GyroControl.java" "$src/GyroSmoother.java" \
   "$work/src/net/kdt/pojavlaunch/customcontrols/mouse/"
cp "$here/GyroSim.java" "$here/OldVsNew.java" "$work/src/"

javac -nowarn -d "$work/out" $(find "$work/src" -name '*.java')
java -cp "$work/out" GyroSim
echo
echo "--- what the old threshold-flush implementation did, for comparison ---"
java -cp "$work/out" OldVsNew
