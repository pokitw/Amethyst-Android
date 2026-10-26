#!/bin/sh
# Compile the shipped Sodium support decision and drive it.
#
# The launcher used to refuse Sodium outright, and then lost the way past the refusal: the
# preference that skipped the dialog survived the Compose settings rewrite and the switch that set
# it did not, so "Delete Sodium and Launch" became the only button. That made shader packs
# impossible, since Iris requires Sodium.
#
# The replacement is a verdict about the RENDERER AND THE DEVICE, and the device half is the part
# this harness exists for. The first version asked only about the renderer and returned SUPPORTED
# for Zink everywhere, which SHIPPED: Zink is Mesa, Mesa gates the GL version it can advertise on
# specific Vulkan features, the bundled Turnip has them and a stock ARM Mali driver does not, so a
# Mali player got a crash with NO dialog at all. That is worse than the dead end it replaced.
#
# It is wrong in two silent directions and this drives both. Too strict and somebody is told a
# working setup is broken. Too loose and somebody is waved into a black screen with no explanation.
#
# Two checks matter more than the rest:
#
#   maliOnZinkStillWarns             - reverting the GPU dimension makes this fail. The mutation
#                                      IS the shipped bug, which is the property chordsim's
#                                      keyDownBuffer check has and the reason to trust either.
#   aReportedGlVersionCannotDecideZink - JREUtils sets MESA_GL_VERSION_OVERRIDE=4.6COMPAT for every
#                                      renderer id containing "zink", so a GL version observed on
#                                      a Zink context is a constant the launcher chose. Written as
#                                      a property (the verdict is invariant across glesMajor for
#                                      every Zink row) rather than as a case, so wiring a reported
#                                      version into that arm later cannot pass.
#
# Mutations this is known to catch:
#
#   * the GPU dimension is dropped from Zink   (the shipped bug: silent on Mali, crash, no dialog)
#   * Mali is condemned outright               (claims a Vulkan feature set this build cannot
#                                               query, and the handbook's rule is that anything
#                                               not understood answers UNTESTED, never a refusal)
#   * VERIFIED no longer requires turnipActive (promises a driver JREUtils will not load, because
#                                               it only loads Turnip inside an isAdreno() branch)
#   * VERIFIED no longer requires vulkan       (an Adreno reporting no Vulkan is waved through)
#   * glesMajor is allowed to decide Zink      (reinstates trusting the overridden GL version)
#   * glesMajor == 0 is treated as 0 rather    (every device that will not report its GL ES
#     than as unknown                           version has its translators condemned)
#   * Gpu.unknown() assumes no Vulkan          (an unreadable device is told Zink cannot work)
#   * Gpu.unknown() assumes Turnip             (an unreadable device gets VERIFIED for free)
#   * familyOf's Adreno test is loosened       (a device is called Adreno, promised Turnip, and
#                                               does not get it; checked against the real
#                                               GLInfoUtils.isAdreno source, not a copy of it)
#   * shadersViable widens past VERIFIED       (removes the shader pack warning on exactly the
#                                               devices that need it, which also shipped)
#   * gl4es stops reading as INCOMPATIBLE      (the one factual refusal becomes a shrug)
#   * an unknown renderer defaults to          (every future renderer reinstates the dead end)
#     INCOMPATIBLE
#   * ABSENT stops being returned when no      (a profile with no Sodium is warned about)
#     Sodium is installed
#   * a warning carries Reason.NONE            (the generic sentence that teaches people to
#                                               dismiss dialogs unread)
#   * betterRenderer ignores availability      (a device with no Vulkan is offered Zink)
#   * betterRenderer suggests Zink to Zink     (a dialog offering the renderer already in use)
#   * the id is no longer lowercased           (a hand-edited profile silently misreads)
#   * a renderer id is renamed in              (checked against the real resource array, because a
#     headings_array.xml and not here           rename there falls through to UNTESTED in silence)
#
# What this CANNOT check, and nothing in this container can: whether a given Vulkan driver on a
# given phone actually clears GL 3.3 through Mesa. There is no device in CI and no Vulkan loader
# here. VERIFIED means "this build has positive evidence for this configuration", not "tested on
# your phone", and the only honest confirmation is a real device.
#
# SodiumSupport has no Android imports, on purpose, so nothing has to be stubbed out. The Android
# side that reads the device is GpuProfile, which depends on SodiumSupport and never the reverse,
# so it stays out of this compile entirely.
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

# The repo root is passed in because two checks read the shipped source and resources rather than
# holding their own copy of what those say.
java -cp "$OUT/classes" net.kdt.pojavlaunch.sodium.Harness "$ROOT"
