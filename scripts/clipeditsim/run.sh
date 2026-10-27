#!/bin/sh
# Compile the shipped ClipEditor against a stubbed platform and drive it.
#
# ClipPlan decides and clipsim proves the deciding. This proves the CARRYING OUT, which is the
# other half and the one that cannot be reasoned about from the source: MediaMuxer fails in three
# ways that are all worse than an exception, and all three are reachable from this file.
#
# The compile is half the point on its own. ClipEditor is ~350 lines of new Java calling about
# twenty Android signatures, and CI is otherwise the only compiler in this project (16.6).
#
# The stubs model the platform's REFUSALS, not only its successes (16.16):
#
#   * MediaMuxer.writeSampleData throws WouldHaveHung where the real one BLOCKS FOREVER - on a
#     timestamp that goes backwards within a track, and on a negative one. minSdk here is 21 and
#     that is below the version where the platform started tolerating it, so this is the live
#     failure mode, not a historical one. The executor is supposed to refuse first, so anything
#     reaching the stub is a real defect.
#   * MediaMuxer.stop() throws when a track received no samples, as the real one does.
#   * MediaExtractor.readSampleData throws IllegalArgumentException with a NULL MESSAGE when the
#     buffer is too small, which is exactly what the JNI does with -ENOMEM and is the reason the
#     executor has to catch and name it.
#   * setDataSource, addTrack, stop and the sidecar write can each be made to refuse one at a
#     time, and the harness asserts no .part file survives any of them.
#
# The checks that matter most:
#
#   audioAndVideoShareOneOffsetPerSegment - the single most important invariant in the feature.
#       Deriving each track's shift from its own first kept sample is the obvious implementation
#       and it is wrong: audio's first kept sample is up to one AAC frame (21.3ms at 48kHz) later
#       than the video sync frame it belongs to, so the two drift apart at every cut, and the
#       drift accumulates. One offset per segment, shared.
#
#   flagsAreTranslatedAndNotCast - SAMPLE_FLAG_ENCRYPTED is 2 and so is BUFFER_FLAG_CODEC_CONFIG;
#       SAMPLE_FLAG_PARTIAL_FRAME is 4 and so is BUFFER_FLAG_END_OF_STREAM. `info.flags =
#       extractor.getSampleFlags()` looks obviously correct, compiles, and works on every file
#       this app records. The fixture decorates the source so a cast is observable.
#
#   everySegmentStartsOnAKeyFrame - MPEG4Writer DISCARDS leading non key video samples without
#       complaining, so a segment starting off a sync frame does not fail: the video just starts
#       up to one GOP after the audio and the clip is out of lip sync, logged at debug only.
#
# Mutations this is known to catch (12 of 12 tried):
#
#   * the sample flags are cast instead of translated   (an encrypted or partial-frame source is
#                                                        written as codec config / end of stream)
#   * the key frame guard is removed                    (video lands up to a GOP behind the sound)
#   * the monotonic / negative guard is removed         (the real muxer BLOCKS FOREVER)
#   * the B frame refusal is removed                    (ditto, via reordered timestamps)
#   * the video buffer fallback drops to 1 MB           (below the platform's own 1080p estimate,
#                                                        so a fat key frame throws a null-message
#                                                        IllegalArgumentException)
#   * the too-small-buffer IAE is no longer named       (that null message escapes to the user)
#   * the empty-track check is removed                  (stop() throws something unactionable)
#   * freeName returns the original                     (the edit overwrites the recording)
#   * the partial is not deleted on failure             (a .part file survives a failed export)
#   * the rotation is passed through unguarded          (setOrientationHint throws on 45 degrees)
#   * a failed sidecar fails the export                 (a cosmetic failure loses the whole clip)
#   * the decode-order detection is disabled            (B frames reach the muxer)
#
# Two findings came out of writing it, and both were real:
#
#   * mapToOutput answers -1 for "no segment holds this", and the executor was treating ANY
#     negative answer as that sentinel. A plan with a negative output time therefore had the
#     first part of each segment silently dropped and the rest written. The executor now takes
#     the offset from the segment it is already iterating, so the sentinel cannot be misread.
#   * the first draft of audioAndVideoShareOneOffsetPerSegment was a TAUTOLOGY: it recovered each
#     sample's source time by subtracting the very offset it then asserted. It now states the
#     output must be exactly what mapToOutput names, for both tracks, against the fixture's real
#     timestamps rather than a step re-derived in the harness (a video frame is at i*1000000/60,
#     which is not i*16666, and synthesising the series drifts).
#
# What this CANNOT check, and nothing in this container can: that a real MediaExtractor reports
# these sync frames, that a real MediaMuxer accepts these timestamps, and that the resulting file
# plays. Those need a device.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/tmp"

# The stubs come FIRST on the sourcepath so RecordingInfo resolves to the stub rather than to the
# real one, which drags in org.json and android.content. ClipEditor and ClipPlan are named
# explicitly, so those two are the real shipped files.
javac -nowarn -source 8 -target 8 \
    -sourcepath "$HERE/stubs:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/recorder/edit/ClipPlan.java" \
    "$SRC/net/kdt/pojavlaunch/recorder/edit/ClipEditor.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.recorder.edit.Harness "$OUT/tmp"
