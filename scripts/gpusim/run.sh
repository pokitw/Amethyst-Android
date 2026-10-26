#!/bin/sh
# Compile the shipped GpuProfile against stubs and drive it.
#
# GpuProfile is the Android half of the Sodium verdict: it reads GLInfoUtils, the package manager
# and TurnipDrivers and turns them into a SodiumSupport.Gpu. sodiumsim drives the decision; this
# drives the reading, which sodiumsim deliberately cannot see because SodiumSupport has no Android
# imports and takes the device as an argument.
#
# Two things it checks that nothing else can:
#
#   1. THE SIGNATURES. GpuProfile is new Java calling four project classes and two framework ones.
#      CI is otherwise the only compiler (16.6), and a signature that drifted would be found there
#      rather than here. The stubs copy the real signatures, and GLInfo.isAdreno() is copied
#      verbatim because that is the predicate Turnip loading is gated on.
#
#   2. THAT IT CANNOT THROW. It runs on the launch path, where §16.15 applies: anything touching a
#      system service has to be unable to throw rather than merely unlikely to, because a device
#      report is never worth a game that will not start. Every stub can be made to refuse, one at
#      a time and all at once, and the result still has to be a usable Gpu.
#
# A stub that can only succeed tests nothing about the guard around it, which is lesson 16.16 and
# was learned from the gyro harness shipping a crash. So these refuse: GLInfoUtils throws as a
# driver that will not initialise, the package manager throws, the Context itself is dead, and
# TurnipDrivers throws as an unreadable driver folder.
#
# Mutations this is known to catch:
#
#   * any try/catch in GpuProfile.read is removed   (the launch path gains a throw)
#   * turnipActive stops being gated on isAdreno()  (promises a driver JREUtils will not load)
#   * turnipActive is read from the preference       (the system-driver choice stops being
#     rather than from resolve().loadTurnip           honoured, so VERIFIED is handed out wrongly)
#   * the Vulkan guard defaults to false on failure  (every device that will not answer is told
#                                                     Zink cannot work)
#   * the family falls back to OTHER rather than     (an unreadable device stops being unknown,
#     UNKNOWN on a failed probe                       which is the input the safe default rests on)
#   * describe() stops naming the family or Turnip   (a bug report cannot tell an Adreno with
#                                                     Turnip from a stock Mali, which are the two
#                                                     configurations this whole feature separates)
#
# One mutation is NOT caught, and it is the check reporting the truth rather than a hole:
#
#   * adreno is read from familyOf(...) == ADRENO instead of from info.isAdreno()
#
# No input distinguishes those two, because familyOf's Adreno arm is deliberately the same rule as
# GLInfo.isAdreno(): renderer contains "Adreno" and vendor equals "Qualcomm". So the mutation
# changes no answer today. It is still worth keeping the code as it is, for a reason this harness
# cannot see: reading isAdreno() means GpuProfile follows the shipped predicate automatically if
# upstream changes it, and upstream is actively working on exactly this area. The other half of the
# pair is guarded where it can be: sodiumsim's familyAgreesWithTheTurnipPredicate reads the real
# GLInfoUtils source and fails if that rule stops matching familyOf, which is the point at which
# this mutation would start to matter. This is memsim's situation, where the guard and the
# constants hide each other's removal, and it is stated rather than hidden.
#
# What it CANNOT check: whether GLInfoUtils' real EGL probe works on a real device, or whether a
# real Vulkan driver clears GL 3.3 through Mesa. There is no device in CI and no GL here.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

# The stubs come first on the sourcepath so the real Tools, LauncherPreferences, GLInfoUtils and
# TurnipDrivers (which pull in the whole app) are never reached. The two files under test are the
# shipped ones, named explicitly.
javac -nowarn -source 8 -target 8 \
    -sourcepath "$HERE/stubs:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/sodium/SodiumSupport.java" \
    "$SRC/net/kdt/pojavlaunch/sodium/GpuProfile.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.sodium.Harness
