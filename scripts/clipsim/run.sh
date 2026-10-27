#!/bin/sh
# Compile the shipped clip edit planner and drive it.
#
# ClipPlan decides which spans of a recording survive a trim or a cut, and what every surviving
# sample's new timestamp is. The Android side walks a MediaExtractor, asks mapToOutput for each
# sample and hands it to a MediaMuxer, and contains no decisions at all. That split exists for one
# reason: there is no device in this project's CI and no way to play an MP4 in this container, so
# a wrong plan is a corrupt or wrong length video that compiles perfectly and ships. Plain Java
# with no Android imports is the only form of this logic a check can execute (16.25).
#
# The one that matters most:
#
#   aCutNeverRemovesLessThanAsked - somebody cutting out a death, a mistake, or a face on a shared
#       screen is asking for that material to be gone. A cut that left 1.9 seconds of it in would
#       be the single failure this feature must not have. Asserted as a property over a sweep of
#       every cut start and length rather than at a few points, so flipping the snap direction
#       cannot pass. That flip is a one word edit and it is the most likely edit anybody makes
#       here, because snapping backwards looks friendlier until you ask what a cut is for.
#
# The asymmetry it exists to protect, which is easy to "tidy" away:
#
#   onlyResumePointsSnap - only a segment's FIRST sample has to be a sync frame, because nothing
#       needs to decode past a segment's end. So the trim end and the near side of every cut are
#       frame accurate, and only the trim start and the far side of a cut sit on the two second
#       grid. Anybody assuming both ends snap will make the editor coarser than it needs to be
#       and this check is what says so.
#
# The editor's cutting model, added with the CapCut shaped redesign:
#
#   deletionsFollowTheirSpanWhenTheTimelineChanges - the screen shades a span as deleted by exact
#       equality against the cut list, so that list has to be re-stated in terms of the current
#       spans every time a split or a trim drag changes what the spans are. The property asserted
#       is the INVARIANT rather than any particular mapping: retime's result is always a subset of
#       the spans handed in. Break that and the plan goes on removing material the screen says is
#       being kept, which is the one disagreement this feature cannot afford. Overlap rather than
#       a complete cover is the test, and both movements it exists for are checked by name.
#
# The second one that matters, added after the first draft shipped:
#
#   endsSnapToAFrameAndNeverAdmitOne - a segment END is unconstrained, but it should still land on
#       a frame. Declaring a segment that ends between two frames makes the frame at the join
#       display for a fraction of its time, once per cut, which is a sub frame error nobody would
#       ever find by looking. Snapping the end FORWARD to the next frame fixes it and is provably
#       safe, because the span is half open so the frame sitting on the new end is still excluded:
#       the set of surviving frames is asserted identical with and without the frame table, which
#       is what stops this from quietly handing back material a cut was removing.
#
#       That safety rests on every sync frame also being a frame, so build() UNIONS the two tables
#       rather than assuming they nest. The check for it had to be built deliberately: the first
#       fixture put its liar sync where the next real frame was already earlier, so dropping the
#       union was invisible and the fixture had been written from the same assumption as the code
#       (§16.20). It now sweeps twenty positions that each stage the overrun.
#
# Mutations this is known to catch:
#
#   * the end snap is removed                             (segments end between frames again)
#   * the end snap goes backwards                         (a frame is lost from the output)
#   * the end snap overshoots by one frame                (a frame is shown for twice its time)
#   * the frames/syncs union is dropped                   (a sync frame absent from the frame
#                                                          table lets one segment's end overrun
#                                                          the next segment's start)
#   * an absent frame table is unioned with the syncs     (ends snap to SYNC frames, silently
#                                                          making every edit two seconds coarse:
#                                                          this one was a real bug, written and
#                                                          caught by this check within the hour)
#
#   * a resume point snaps backwards instead of forwards  (a cut silently under-removes: THE bug)
#   * a segment end is snapped too                        (the editor becomes needlessly coarse
#                                                          and loses frame accurate trimming)
#   * outputStartUs stops accumulating                    (every segment lands at zero, so the
#                                                          output has overlapping timestamps and
#                                                          the muxer rejects or the video stutters)
#   * outputStartUs accumulates the pre-snap duration     (a gap where the cut was, which is the
#                                                          whole thing a cut is supposed to close)
#   * mapToOutput becomes inclusive at the segment end    (the boundary frame is written twice and
#                                                          the timestamp stops increasing)
#   * mapToOutput stops returning -1 for a dropped sample (the cut material is written back in)
#   * the running cursor becomes a plain assignment       (a cut NESTED inside another winds the
#                                                          cursor backwards and hands back material
#                                                          the outer cut had already removed)
#   * the zero length guard in subtract is loosened       (an empty span escapes the function)
#   * the cut list is not sorted before use               (the plan depends on the order the user
#                                                          happened to add cuts in)
#   * a reversed cut range subtracts nothing              (a cut the user made does nothing)
#   * the sync table is trusted unsorted                  (binary search on unsorted input answers
#                                                          wrongly and the segment will not decode)
#   * in/out are not clamped to the clip                  (a segment past the end of the file)
#   * a selection with no sync frame inside is accepted   (a segment nothing can start from)
#   * an empty or inverted selection is accepted          (a zero length file written silently)
#   * nearestSync stops breaking ties                     (a UI handle jitters between two frames)
#   * syncAtOrAfter invents a frame past the last one     (a seek past the end of the track)
#
# Two mutations were NOT caught by the first draft, and both were the check telling the truth:
#
#   * merging overlapping cuts, and merging touching ones, were both DEAD. subtract's running
#     cursor already absorbs an overlap and its `startUs > cursor` guard already refuses the zero
#     length sliver two abutting cuts would leave. The merge loop was deleted rather than
#     documented, because dead code that looks load bearing is worse than no code.
#   * two guards then hid each other: the running maximum and the zero length guard. The first was
#     only observable through a NESTED cut, which the fixtures lacked; the second only through
#     subtract's own contract, since a zero length span is absorbed downstream by the segment
#     builder refusing a span with no sync frame in it. Both are now tested directly, and that is
#     memsim's situation handled rather than hidden.
#
# What this CANNOT check, and nothing in this container can: that MediaExtractor reports the sync
# frames this planner is handed, that MediaMuxer accepts the timestamps it produces, and that the
# resulting file plays. Those need a device. The plan is the half that can be made certain, which
# is exactly why all the judgement lives in it.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

# ClipPlan has no Android imports, on purpose, so nothing has to be stubbed out.
javac -nowarn -source 8 -target 8 \
    -sourcepath "$SRC:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/recorder/edit/ClipPlan.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.recorder.edit.Harness
