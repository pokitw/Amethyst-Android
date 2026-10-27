package net.kdt.pojavlaunch.recorder.edit;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import net.kdt.pojavlaunch.recorder.edit.ClipPlan.Plan;
import net.kdt.pojavlaunch.recorder.edit.ClipPlan.Problem;
import net.kdt.pojavlaunch.recorder.edit.ClipPlan.Range;
import net.kdt.pojavlaunch.recorder.edit.ClipPlan.Segment;

/**
 * Drives the shipped ClipPlan. See run.sh for what this is for and what it catches.
 */
public class Harness {

    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<String>();

    /** The recorder asks for an I frame every two seconds (GameRecorder.java:57). */
    private static final long SYNC_SPACING_US = 2_000_000L;
    private static final long SECOND = 1_000_000L;

    public static void main(String[] args) {
        syncLookupHandlesEveryEdge();
        aPlainTrimKeepsOneSegment();
        onlyResumePointsSnap();
        aCutNeverRemovesLessThanAsked();
        aCutClosesTheGapItLeaves();
        mapToOutputIsHalfOpen();
        overlappingAndTouchingCutsMerge();
        nestedCutsAreFullyRemoved();
        subtractNeverEmitsAnEmptySpan();
        malformedInputIsNormalisedNotTrusted();
        refusalsCarryTheirReason();
        theInvariantsHoldAcrossASweep();
        aRealisticEditComesOutRight();
        endsSnapToAFrameAndNeverAdmitOne();
        theTimelineDividesIntoSelectableSpans();
        deletionsFollowTheirSpanWhenTheTimelineChanges();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String failure : FAILURES) System.out.println("FAIL: " + failure);
            System.out.println(FAILURES.size() + " FAILURES");
            System.exit(1);
        }
        System.out.println("all passed");
    }

    /* ------------------------------------------------------------------ fixtures */

    /** A sync frame every two seconds across a clip of the given length. */
    private static long[] grid(long durationUs) {
        int count = (int) (durationUs / SYNC_SPACING_US) + 1;
        long[] syncs = new long[count];
        for (int i = 0; i < count; i++) syncs[i] = i * SYNC_SPACING_US;
        return syncs;
    }

    /** Every frame of a clip at the given rate, the way a container stores them. */
    private static long[] framesAt(long durationUs, int fps) {
        int count = (int) (durationUs * fps / 1_000_000L);
        long[] frames = new long[count];
        for (int i = 0; i < count; i++) frames[i] = i * 1_000_000L / fps;
        return frames;
    }

    /** Sync frames drawn FROM a frame table, which is the relationship a real file has. */
    private static long[] syncsFrom(long[] frames, int every) {
        int count = (frames.length + every - 1) / every;
        long[] syncs = new long[count];
        for (int i = 0; i < count; i++) syncs[i] = frames[i * every];
        return syncs;
    }

    /** Which frames a plan keeps. The only thing an end snap must never change. */
    private static List<Long> keptFrames(Plan plan, long[] frames) {
        List<Long> kept = new ArrayList<Long>();
        for (int i = 0; i < frames.length; i++) {
            if (plan.mapToOutput(frames[i]) >= 0L) kept.add(frames[i]);
        }
        return kept;
    }

    private static List<Range> cuts(long... bounds) {
        List<Range> list = new ArrayList<Range>();
        for (int i = 0; i + 1 < bounds.length; i += 2) {
            list.add(new Range(bounds[i], bounds[i + 1]));
        }
        return list;
    }

    /* ------------------------------------------------------------------ checks */

    private static void syncLookupHandlesEveryEdge() {
        long[] syncs = {0L, 2 * SECOND, 4 * SECOND, 6 * SECOND};

        check(ClipPlan.syncAtOrAfter(syncs, 0L) == 0L, "syncAtOrAfter missed an exact first hit");
        check(ClipPlan.syncAtOrAfter(syncs, 1L) == 2 * SECOND, "syncAtOrAfter went backwards");
        check(ClipPlan.syncAtOrAfter(syncs, 2 * SECOND) == 2 * SECOND,
                "syncAtOrAfter skipped an exact hit");
        check(ClipPlan.syncAtOrAfter(syncs, 6 * SECOND) == 6 * SECOND,
                "syncAtOrAfter missed the last frame exactly");
        check(ClipPlan.syncAtOrAfter(syncs, 6 * SECOND + 1) == -1L,
                "syncAtOrAfter invented a frame past the end");

        // The UI snaps a handle with this, so a tie must be decided or the handle jitters.

        check(ClipPlan.syncAtOrAfter(new long[0], 0L) == -1L, "an empty sync table answered");
    }

    private static void aPlainTrimKeepsOneSegment() {
        long duration = 60 * SECOND;
        Plan plan = ClipPlan.trim(duration, grid(duration), null, 10 * SECOND, 20 * SECOND);
        check(plan.isUsable(), "a plain trim was refused");
        check(plan.segments.size() == 1, "a plain trim produced " + plan.segments.size()
                + " segments, expected 1");
        if (plan.segments.isEmpty()) return;
        Segment only = plan.segments.get(0);
        check(only.sourceStartUs == 10 * SECOND, "a trim starting on a sync frame moved");
        check(only.sourceEndUs == 20 * SECOND, "a trim end moved; ends do not need to snap");
        check(only.outputStartUs == 0L, "the first segment does not start the output at zero");
        check(plan.outputDurationUs == 10 * SECOND,
                "the output duration is " + plan.outputDurationUs + ", expected " + 10 * SECOND);
    }

    /**
     * The asymmetry that makes this feature better than it looks, and the thing most likely to be
     * "tidied" away by somebody who assumes both ends snap.
     */
    private static void onlyResumePointsSnap() {
        long duration = 60 * SECOND;
        long[] syncs = grid(duration);

        // An in point between sync frames snaps forward to the next one.
        Plan plan = ClipPlan.trim(duration, syncs, null, 10_500_000L, 20_300_000L);
        check(plan.isUsable(), "a trim between sync frames was refused");
        if (plan.segments.isEmpty()) return;
        Segment only = plan.segments.get(0);
        check(only.sourceStartUs == 12 * SECOND,
                "the in point snapped to " + only.sourceStartUs + ", expected the next sync frame "
                        + 12 * SECOND + ". A decoder cannot start anywhere else.");
        check(only.sourceEndUs == 20_300_000L,
                "the out point moved to " + only.sourceEndUs + ". It must not: nothing has to "
                        + "decode past a segment end, so the trim end is frame accurate.");

        // And the near side of a cut is frame accurate for the same reason.
        Plan withCut = ClipPlan.build(duration, syncs, null, 0L, 30 * SECOND,
                cuts(10_300_000L, 15_700_000L));
        check(withCut.segments.size() == 2, "a single interior cut did not leave two segments");
        if (withCut.segments.size() == 2) {
            check(withCut.segments.get(0).sourceEndUs == 10_300_000L,
                    "the near side of a cut snapped, and it should not have: it is a segment end");
            check(withCut.segments.get(1).sourceStartUs == 16 * SECOND,
                    "the far side of a cut did not snap forward to a sync frame");
        }
    }

    /**
     * THE SAFETY PROPERTY. Somebody cutting out a death, a mistake or a face on a shared screen
     * is asking for that material to be gone. A cut that left part of it in would be the one
     * failure this feature must not have, so it is asserted as a property over a sweep rather
     * than at a few points, and flipping the snap direction has to fail it.
     */
    private static void aCutNeverRemovesLessThanAsked() {
        long duration = 60 * SECOND;
        long[] syncs = grid(duration);

        for (long cutStart = 0; cutStart < 40 * SECOND; cutStart += 137_000L) {
            for (long cutLength = 500_000L; cutLength <= 7 * SECOND; cutLength += 311_000L) {
                long cutEnd = cutStart + cutLength;
                Plan plan = ClipPlan.build(duration, syncs, null, 0L, duration,
                        cuts(cutStart, cutEnd));
                for (int i = 0; i < plan.segments.size(); i++) {
                    Segment segment = plan.segments.get(i);
                    // No kept span may overlap the requested cut, at all.
                    boolean overlaps = segment.sourceStartUs < cutEnd
                            && segment.sourceEndUs > cutStart;
                    check(!overlaps,
                            "a kept segment " + segment + " overlaps the requested cut ["
                                    + cutStart + "," + cutEnd + "). A cut must never remove less "
                                    + "than asked.");
                    if (overlaps) return;
                }
            }
        }

        // And the plan says how much extra it took, so the UI can be honest about it.
        Plan plan = ClipPlan.build(duration, syncs, null, 0L, duration, cuts(10 * SECOND, 11 * SECOND));
        check(plan.extraRemovedUs == SECOND,
                "a one second cut on a two second grid removed " + plan.extraRemovedUs
                        + " extra, expected " + SECOND + " (it must reach the next sync frame)");
        Plan aligned = ClipPlan.build(duration, syncs, null, 0L, duration,
                cuts(10 * SECOND, 12 * SECOND));
        check(aligned.extraRemovedUs == 0L,
                "a cut already on the grid reported " + aligned.extraRemovedUs + " extra removed");
    }

    /** The point of the whole thing: the output has no hole where the cut was. */
    private static void aCutClosesTheGapItLeaves() {
        long duration = 60 * SECOND;
        Plan plan = ClipPlan.build(duration, grid(duration), null, 0L, 30 * SECOND,
                cuts(10 * SECOND, 20 * SECOND));
        check(plan.segments.size() == 2, "expected two segments around one cut");
        if (plan.segments.size() != 2) return;
        Segment first = plan.segments.get(0);
        Segment second = plan.segments.get(1);

        check(first.outputStartUs == 0L, "the first segment does not start at zero");
        check(second.outputStartUs == first.durationUs(),
                "the second segment starts at " + second.outputStartUs + " in the output but the "
                        + "first is " + first.durationUs() + " long, so the cut left a gap");
        check(plan.outputDurationUs == first.durationUs() + second.durationUs(),
                "the output duration does not equal the sum of the segments");
        check(plan.outputDurationUs == 20 * SECOND,
                "30s selection minus a 10s cut came out as " + plan.outputDurationUs);

        // The shift applied to the second segment is exactly the removed duration.
        check(second.offsetUs() == -(10 * SECOND),
                "the second segment is shifted by " + second.offsetUs() + ", expected "
                        + -(10 * SECOND));
    }

    /**
     * mapToOutput is the executor's inner loop, so its boundary behaviour is the file's.
     *
     * Half open: a sample exactly at a segment end belongs to the next segment or to nothing, and
     * never to both, or a frame is written twice and the muxer sees a non monotonic timestamp.
     */
    private static void mapToOutputIsHalfOpen() {
        long duration = 60 * SECOND;
        Plan plan = ClipPlan.build(duration, grid(duration), null, 0L, 30 * SECOND,
                cuts(10 * SECOND, 20 * SECOND));
        if (plan.segments.size() != 2) {
            check(false, "fixture did not produce two segments");
            return;
        }

        check(plan.mapToOutput(0L) == 0L, "the first sample did not land at zero");
        check(plan.mapToOutput(5 * SECOND) == 5 * SECOND, "a sample before the cut moved");
        // The sample exactly at the cut start is the segment's exclusive end: dropped.
        check(plan.mapToOutput(10 * SECOND) == -1L,
                "the sample exactly at a segment end was kept; the range is half open");
        check(plan.mapToOutput(15 * SECOND) == -1L, "a sample inside the cut was kept");
        check(plan.mapToOutput(20 * SECOND) == 10 * SECOND,
                "the first sample after the cut did not land where the cut closed to");
        check(plan.mapToOutput(29_999_999L) == 19_999_999L, "the last kept sample moved wrongly");
        check(plan.mapToOutput(30 * SECOND) == -1L, "a sample at the exclusive out point was kept");
        check(plan.mapToOutput(45 * SECOND) == -1L, "a sample past the out point was kept");

        // No two distinct kept source times may map to the same output time, and order must hold.
        long previous = -1L;
        for (long t = 0; t < duration; t += 16_683L) {   // ~60fps, deliberately not a round number
            long mapped = plan.mapToOutput(t);
            if (mapped < 0L) continue;
            check(mapped > previous,
                    "output timestamps are not strictly increasing at source " + t + ": " + mapped
                            + " after " + previous);
            if (mapped <= previous) return;
            previous = mapped;
        }
    }

    private static void overlappingAndTouchingCutsMerge() {
        long duration = 60 * SECOND;
        long[] syncs = grid(duration);

        // Overlapping.
        Plan overlap = ClipPlan.build(duration, syncs, null, 0L, 30 * SECOND,
                cuts(10 * SECOND, 20 * SECOND, 15 * SECOND, 24 * SECOND));
        check(overlap.segments.size() == 2,
                "two overlapping cuts produced " + overlap.segments.size()
                        + " segments, expected 2");
        if (overlap.segments.size() == 2) {
            check(overlap.segments.get(1).sourceStartUs == 24 * SECOND,
                    "overlapping cuts did not merge into one span");
        }

        // Exactly touching. Treating these as separate leaves a zero length keep between them.
        Plan touching = ClipPlan.build(duration, syncs, null, 0L, 30 * SECOND,
                cuts(10 * SECOND, 16 * SECOND, 16 * SECOND, 22 * SECOND));
        check(touching.segments.size() == 2,
                "two touching cuts produced " + touching.segments.size()
                        + " segments, expected 2 (they are one cut)");
        for (int i = 0; i < touching.segments.size(); i++) {
            check(touching.segments.get(i).durationUs() > 0L,
                    "a zero length segment survived: " + touching.segments.get(i));
        }

        // Out of order input is the same cut set.
        Plan ordered = ClipPlan.build(duration, syncs, null, 0L, 30 * SECOND,
                cuts(4 * SECOND, 6 * SECOND, 20 * SECOND, 22 * SECOND));
        Plan reversed = ClipPlan.build(duration, syncs, null, 0L, 30 * SECOND,
                cuts(20 * SECOND, 22 * SECOND, 4 * SECOND, 6 * SECOND));
        check(ordered.segments.size() == reversed.segments.size()
                        && ordered.outputDurationUs == reversed.outputDurationUs,
                "the cut order changed the plan; cuts must be sorted before use");

        // Several cuts in a row.
        Plan many = ClipPlan.build(duration, syncs, null, 0L, duration,
                cuts(4 * SECOND, 6 * SECOND, 12 * SECOND, 14 * SECOND, 30 * SECOND, 34 * SECOND));
        check(many.segments.size() == 4, "three interior cuts did not leave four segments, got "
                + many.segments.size());
        check(many.outputDurationUs == duration - 8 * SECOND,
                "three cuts totalling 8s left " + many.outputDurationUs);
    }

    private static void malformedInputIsNormalisedNotTrusted() {
        long duration = 60 * SECOND;

        // An unsorted sync table, which a malformed container can produce.
        long[] shuffled = {4 * SECOND, 0L, 2 * SECOND, 2 * SECOND, 6 * SECOND};
        Plan plan = ClipPlan.trim(duration, shuffled, null, 3 * SECOND, 10 * SECOND);
        check(plan.isUsable(), "an unsorted sync table was refused rather than sorted");
        if (plan.isUsable()) {
            check(plan.segments.get(0).sourceStartUs == 4 * SECOND,
                    "an unsorted sync table gave the wrong start: "
                            + plan.segments.get(0).sourceStartUs);
        }

        // A reversed cut is a bug upstream, not an instruction to subtract nothing.
        Plan reversedCut = ClipPlan.build(duration, grid(duration), null, 0L, 30 * SECOND,
                cuts(20 * SECOND, 10 * SECOND));
        check(reversedCut.segments.size() == 2,
                "a reversed cut range was not normalised; got " + reversedCut.segments.size()
                        + " segments");

        // Cuts wholly outside the selection change nothing.
        Plan outside = ClipPlan.build(duration, grid(duration), null, 10 * SECOND, 20 * SECOND,
                cuts(0L, 5 * SECOND, 40 * SECOND, 50 * SECOND));
        check(outside.segments.size() == 1 && outside.outputDurationUs == 10 * SECOND,
                "a cut outside the selection altered the plan");

        // In and out beyond the clip are clamped rather than trusted.
        Plan clamped = ClipPlan.trim(duration, grid(duration), null, -5 * SECOND, 999 * SECOND);
        check(clamped.isUsable(), "an over wide selection was refused rather than clamped");
        if (clamped.isUsable()) {
            check(clamped.segments.get(0).sourceStartUs == 0L, "a negative in point was not clamped");
            check(clamped.segments.get(0).sourceEndUs == duration,
                    "an out point past the end was not clamped to the duration");
        }

        // A null cut entry must not throw: the UI builds this list.
        List<Range> withNull = new ArrayList<Range>();
        withNull.add(null);
        withNull.add(new Range(10 * SECOND, 12 * SECOND));
        try {
            Plan nullCut = ClipPlan.build(duration, grid(duration), null, 0L, 30 * SECOND, withNull);
            check(nullCut.segments.size() == 2, "a null cut entry changed the plan");
        } catch (Throwable t) {
            check(false, "a null cut entry threw " + t.getClass().getSimpleName());
        }
    }

    /** A refusal has to say why: a silently empty plan writes a zero length file. */
    private static void refusalsCarryTheirReason() {
        long duration = 60 * SECOND;
        long[] syncs = grid(duration);

        Plan inverted = ClipPlan.trim(duration, syncs, null, 20 * SECOND, 10 * SECOND);
        check(inverted.problem == Problem.EMPTY_SELECTION,
                "an inverted selection gave " + inverted.problem);
        check(!inverted.isUsable(), "an inverted selection was called usable");

        Plan empty = ClipPlan.trim(duration, syncs, null, 10 * SECOND, 10 * SECOND);
        check(empty.problem == Problem.EMPTY_SELECTION,
                "a zero length selection gave " + empty.problem);

        Plan noSyncs = ClipPlan.trim(duration, new long[0], null, 0L, 10 * SECOND);
        check(noSyncs.problem == Problem.NO_SYNC_FRAMES,
                "a track with no sync frames gave " + noSyncs.problem);
        check(!noSyncs.isUsable(), "a track with no sync frames was called usable");

        Plan allCut = ClipPlan.build(duration, syncs, null, 10 * SECOND, 20 * SECOND,
                cuts(0L, duration));
        check(allCut.problem == Problem.NOTHING_LEFT,
                "cutting everything gave " + allCut.problem);
        check(!allCut.isUsable(), "a plan with nothing left was called usable");

        // A selection with no sync frame inside it cannot be started at all.
        long[] sparse = {0L, 50 * SECOND};
        Plan noStart = ClipPlan.trim(duration, sparse, null, 10 * SECOND, 20 * SECOND);
        check(!noStart.isUsable(),
                "a selection containing no sync frame was called usable: there is nothing a "
                        + "decoder could start from");
        check(noStart.problem == Problem.NOTHING_LEFT,
                "a selection with no sync frame inside gave " + noStart.problem);

        check(ClipPlan.trim(duration, syncs, null, 0L, duration).problem == Problem.NONE,
                "the whole clip was refused");
    }

    /**
     * The invariants every plan must hold, over a sweep rather than at chosen points.
     *
     * memsim's and viewportsim's shape: a handful of examples can agree with an off by one that a
     * sweep cannot.
     */
    private static void theInvariantsHoldAcrossASweep() {
        long duration = 120 * SECOND;
        long[] syncs = grid(duration);
        int sweeps = 0;

        for (long in = 0; in < 40 * SECOND; in += 713_000L) {
            for (long out = in + 1; out <= duration; out += 9_871_000L) {
                for (int variant = 0; variant < 3; variant++) {
                    List<Range> cutList;
                    if (variant == 0) {
                        cutList = cuts();
                    } else if (variant == 1) {
                        cutList = cuts(in + 3 * SECOND, in + 5 * SECOND);
                    } else {
                        cutList = cuts(in + SECOND, in + 2 * SECOND,
                                in + 8 * SECOND, in + 13 * SECOND);
                    }
                    sweeps++;
                    Plan plan = ClipPlan.build(duration, syncs, null, in, out, cutList);
                    if (!plan.isUsable()) continue;

                    long expectedOutput = 0L;
                    long previousSourceEnd = Long.MIN_VALUE;
                    for (int i = 0; i < plan.segments.size(); i++) {
                        Segment s = plan.segments.get(i);

                        if (!okay(s.durationUs() > 0L, "a zero or negative length segment " + s)) return;
                        if (!okay(s.sourceStartUs >= previousSourceEnd,
                                "segments are not ascending and disjoint in the source: " + s)) return;
                        previousSourceEnd = s.sourceEndUs;

                        if (!okay(isSync(syncs, s.sourceStartUs),
                                "a segment starts at " + s.sourceStartUs
                                        + ", which is not a sync frame. A decoder cannot start "
                                        + "there and the output will not play.")) return;

                        if (!okay(s.outputStartUs == expectedOutput,
                                "segment " + i + " starts at " + s.outputStartUs
                                        + " in the output, expected " + expectedOutput
                                        + ": the output has a gap or an overlap")) return;
                        expectedOutput += s.durationUs();

                        if (!okay(s.sourceStartUs >= in && s.sourceEndUs <= out,
                                "a segment " + s + " escapes the selection [" + in + "," + out
                                        + ")")) return;
                    }
                    if (!okay(plan.outputDurationUs == expectedOutput,
                            "the output duration " + plan.outputDurationUs
                                    + " does not equal the summed segments " + expectedOutput)) return;

                    // Nothing kept may lie inside any requested cut.
                    for (int c = 0; c < cutList.size(); c++) {
                        Range cut = cutList.get(c);
                        long lo = Math.min(cut.startUs, cut.endUs);
                        long hi = Math.max(cut.startUs, cut.endUs);
                        for (int i = 0; i < plan.segments.size(); i++) {
                            Segment s = plan.segments.get(i);
                            if (!okay(!(s.sourceStartUs < hi && s.sourceEndUs > lo),
                                    "kept " + s + " overlaps requested cut [" + lo + "," + hi
                                            + ")")) return;
                        }
                    }
                }
            }
        }
        check(sweeps > 500, "the sweep only ran " + sweeps + " cases, which is too few to trust");
    }

    /** One end to end case worked out by hand, so the table can be read against the arithmetic. */
    private static void aRealisticEditComesOutRight() {
        // A ten minute clip. Trim off the first 7.4s and the last minute, and cut a 12s death out
        // of the middle. Sync frames every two seconds.
        long duration = 600 * SECOND;
        long[] syncs = grid(duration);
        Plan plan = ClipPlan.build(duration, syncs, null, 7_400_000L, 540 * SECOND,
                cuts(300 * SECOND, 312 * SECOND));

        check(plan.isUsable(), "the realistic edit was refused");
        if (!plan.isUsable()) return;
        check(plan.segments.size() == 2, "expected two segments, got " + plan.segments.size());
        if (plan.segments.size() != 2) return;

        Segment a = plan.segments.get(0);
        Segment b = plan.segments.get(1);
        // In snaps forward from 7.4s to 8s. The cut's near side is exact. Its far side is already
        // on the grid.
        check(a.sourceStartUs == 8 * SECOND, "in point: " + a.sourceStartUs);
        check(a.sourceEndUs == 300 * SECOND, "cut near side moved: " + a.sourceEndUs);
        check(b.sourceStartUs == 312 * SECOND, "cut far side: " + b.sourceStartUs);
        check(b.sourceEndUs == 540 * SECOND, "out point moved: " + b.sourceEndUs);

        long expected = (300 - 8) * SECOND + (540 - 312) * SECOND;
        check(plan.outputDurationUs == expected,
                "output duration " + plan.outputDurationUs + ", expected " + expected);
        check(a.outputStartUs == 0L, "first segment output start");
        check(b.outputStartUs == (300 - 8) * SECOND, "second segment output start");
        check(plan.extraRemovedUs == 600_000L,
                "extra removed " + plan.extraRemovedUs + ", expected 600000 (7.4s to 8s)");

        // Spot check the mapping at the join.
        check(plan.mapToOutput(299_999_999L) == 291_999_999L, "the sample before the cut");
        check(plan.mapToOutput(312 * SECOND) == 292 * SECOND, "the sample after the cut");
    }

    /**
     * A cut wholly inside another one, which is what distinguishes a running maximum from a plain
     * assignment when the cursor advances.
     *
     * Partially overlapping cuts do not: the later one always ends after the cursor, so taking its
     * end directly happens to be right. A NESTED cut ends before the cursor, and assigning would
     * wind the cursor backwards and hand back material the outer cut had already removed. The
     * first version of this harness only had the partial case and could not see it.
     */
    private static void nestedCutsAreFullyRemoved() {
        long duration = 60 * SECOND;
        long[] syncs = grid(duration);

        // [12,15) lies wholly inside [10,20).
        Plan plan = ClipPlan.build(duration, syncs, null, 0L, 30 * SECOND,
                cuts(10 * SECOND, 20 * SECOND, 12 * SECOND, 15 * SECOND));
        check(plan.segments.size() == 2,
                "a nested cut produced " + plan.segments.size() + " segments, expected 2");
        check(plan.outputDurationUs == 20 * SECOND,
                "30s minus a 10s cut containing a 3s cut came out as " + plan.outputDurationUs
                        + ", expected " + 20 * SECOND);
        for (int i = 0; i < plan.segments.size(); i++) {
            Segment s = plan.segments.get(i);
            check(!(s.sourceStartUs < 20 * SECOND && s.sourceEndUs > 10 * SECOND),
                    "a nested cut let " + s + " survive inside the outer cut [10s,20s): the "
                            + "cursor wound backwards");
        }
        check(plan.mapToOutput(17 * SECOND) == -1L,
                "material between the inner and outer cut ends was kept");

        // Three levels of nesting, and a nested cut sharing the outer cut's end.
        Plan deep = ClipPlan.build(duration, syncs, null, 0L, 40 * SECOND,
                cuts(10 * SECOND, 30 * SECOND, 14 * SECOND, 26 * SECOND,
                        18 * SECOND, 22 * SECOND, 25 * SECOND, 30 * SECOND));
        check(deep.outputDurationUs == 20 * SECOND,
                "nested cuts totalling one 20s span came out as " + deep.outputDurationUs);
        for (long t = 10 * SECOND; t < 30 * SECOND; t += 250_000L) {
            if (deep.mapToOutput(t) != -1L) {
                check(false, "nested cuts kept source " + t + ", which is inside [10s,30s)");
                break;
            }
        }
    }

    /**
     * subtract's own contract, tested directly because the harness shares its package.
     *
     * A zero length span is absorbed harmlessly further down (the segment builder refuses a span
     * with no sync frame inside it, and a zero length span has none), so going through build()
     * cannot see the guard that prevents one. That is the memsim situation, where two guards hide
     * each other's removal. Testing the function's own contract separates them.
     */
    private static void subtractNeverEmitsAnEmptySpan() {
        long[][] cases = {
                {0, 30 * SECOND, 0, 10 * SECOND},                      // cut at the very start
                {0, 30 * SECOND, 20 * SECOND, 30 * SECOND},            // cut to the very end
                {0, 30 * SECOND, 0, 30 * SECOND},                      // cut everything
                {10 * SECOND, 20 * SECOND, 10 * SECOND, 12 * SECOND},  // cut flush with the in
                {10 * SECOND, 20 * SECOND, 18 * SECOND, 20 * SECOND},  // cut flush with the out
                {0, 30 * SECOND, 10 * SECOND, 16 * SECOND},            // ordinary interior cut
        };
        for (int c = 0; c < cases.length; c++) {
            long[] k = cases[c];
            List<Range> keeps = ClipPlan.subtract(new Range(k[0], k[1]), cuts(k[2], k[3]));
            long covered = 0L;
            long previousEnd = Long.MIN_VALUE;
            for (int i = 0; i < keeps.size(); i++) {
                Range keep = keeps.get(i);
                check(keep.endUs > keep.startUs,
                        "subtract emitted the empty span " + keep + " for selection ["
                                + k[0] + "," + k[1] + ") minus [" + k[2] + "," + k[3] + ")");
                check(keep.startUs >= previousEnd,
                        "subtract emitted spans out of order or overlapping: " + keep);
                previousEnd = keep.endUs;
                check(keep.startUs >= k[0] && keep.endUs <= k[1],
                        "subtract emitted " + keep + " outside the selection");
                // Nothing kept may be inside the cut.
                check(!(keep.startUs < k[3] && keep.endUs > k[2]),
                        "subtract kept " + keep + " inside the cut");
                covered += keep.durationUs();
            }
            long expected = (k[1] - k[0]) - Math.min(k[1], k[3]) + Math.max(k[0], k[2]);
            if (k[3] <= k[0] || k[2] >= k[1]) expected = k[1] - k[0];
            check(covered == expected,
                    "subtract kept " + covered + " of selection [" + k[0] + "," + k[1]
                            + ") minus [" + k[2] + "," + k[3] + "), expected " + expected);
        }

        // Two touching cuts must not leave a sliver between them.
        List<Range> touching = ClipPlan.subtract(new Range(0L, 30 * SECOND),
                cuts(10 * SECOND, 16 * SECOND, 16 * SECOND, 22 * SECOND));
        for (int i = 0; i < touching.size(); i++) {
            check(touching.get(i).durationUs() > 0L,
                    "touching cuts left the empty span " + touching.get(i));
        }
        check(touching.size() == 2, "touching cuts left " + touching.size() + " spans, expected 2");
    }

    /* ------------------------------------------------------------------ plumbing */

    private static boolean isSync(long[] syncs, long us) {
        for (int i = 0; i < syncs.length; i++) {
            if (syncs[i] == us) return true;
        }
        return false;
    }

    /**
     * The end snap states the duration truthfully and changes nothing else.
     *
     * Four properties, and the second is the one that makes the first safe to do at all:
     *
     * <ol>
     *   <li>every segment ends on a frame, or on the clip's end;</li>
     *   <li>the set of frames kept is <b>identical</b> to the set kept with no frame table, so
     *       snapping the end forward cannot hand back a frame a cut was removing;</li>
     *   <li>the last frame of every segment gets its natural display duration, which is the whole
     *       reason for doing this;</li>
     *   <li>segments still never overlap, including when the sync table names a timestamp that is
     *       absent from the frame table.</li>
     * </ol>
     */
    private static void endsSnapToAFrameAndNeverAdmitOne() {
        long duration = 120 * SECOND;
        for (int fps : new int[]{24, 30, 50, 60}) {
            long[] frames = framesAt(duration, fps);
            long[] syncs = syncsFrom(frames, fps * 2);

            // A grid of selections and cuts, deliberately landing between frames.
            for (long in = 0; in <= 20 * SECOND; in += 1_300_000L) {
                for (long out = in + 3_100_000L; out <= 60 * SECOND; out += 7_700_000L) {
                    for (int c = 0; c < 3; c++) {
                        List<Range> cutList = c == 0 ? cuts()
                                : c == 1 ? cuts(in + 1_050_000L, in + 2_900_000L)
                                : cuts(in + 900_000L, in + 1_700_000L,
                                       in + 4_300_000L, in + 6_100_000L);

                        Plan snapped = ClipPlan.build(duration, syncs, frames, in, out, cutList);
                        Plan plain = ClipPlan.build(duration, syncs, null, in, out, cutList);
                        if (!snapped.isUsable()) {
                            check(!plain.isUsable(),
                                    "a frame table must not make a usable plan unusable at "
                                            + fps + "fps " + in + ".." + out);
                            continue;
                        }

                        // (2) The kept frames are the same. This is the safety property.
                        check(keptFrames(snapped, frames).equals(keptFrames(plain, frames)),
                                "the end snap changed which frames survive at " + fps + "fps "
                                        + in + ".." + out + " cuts=" + c);

                        for (int i = 0; i < snapped.segments.size(); i++) {
                            Segment segment = snapped.segments.get(i);

                            // (1) Ends on a frame or on the clip's end.
                            boolean onFrame = Arrays.binarySearch(frames, segment.sourceEndUs) >= 0;
                            check(onFrame || segment.sourceEndUs == duration,
                                    "segment " + i + " ends at " + segment.sourceEndUs
                                            + ", which is neither a frame nor the clip's end");

                            // (3) The last frame kept gets its whole natural duration: there is
                            // no frame strictly between it and the segment's end.
                            long last = -1L;
                            for (int f = 0; f < frames.length; f++) {
                                if (frames[f] >= segment.sourceStartUs
                                        && frames[f] < segment.sourceEndUs) {
                                    last = frames[f];
                                }
                            }
                            check(last >= 0L, "segment " + i + " keeps no frame at all");
                            for (int f = 0; f < frames.length; f++) {
                                check(!(frames[f] > last && frames[f] < segment.sourceEndUs),
                                        "a frame at " + frames[f] + " sits between the last kept "
                                                + "frame " + last + " and the end "
                                                + segment.sourceEndUs);
                            }

                            // (4) No overlap with the next segment.
                            if (i + 1 < snapped.segments.size()) {
                                check(segment.sourceEndUs
                                                <= snapped.segments.get(i + 1).sourceStartUs,
                                        "segment " + i + " ends at " + segment.sourceEndUs
                                                + " past the next start "
                                                + snapped.segments.get(i + 1).sourceStartUs);
                            }
                        }

                        // The declared duration is the sum of the segments, always.
                        long total = 0L;
                        for (int i = 0; i < snapped.segments.size(); i++) {
                            total += snapped.segments.get(i).durationUs();
                        }
                        check(total == snapped.outputDurationUs,
                                "the declared duration " + snapped.outputDurationUs
                                        + " is not the sum of the segments " + total);

                        // Snapping forward can only ever lengthen the declared output, never
                        // shorten it, and never by a whole frame per segment.
                        check(snapped.outputDurationUs >= plain.outputDurationUs,
                                "the end snap shortened the output");
                        check(snapped.outputDurationUs - plain.outputDurationUs
                                        <= (long) snapped.segments.size() * (1_000_000L / fps + 1),
                                "the end snap added more than one frame per segment");
                    }
                }
            }
        }

        // (4) again, adversarially: a sync table naming a timestamp the frame table does not
        // have. Without the union inside build(), a snapped end overruns the next start.
        //
        // The arrangement has to be built deliberately, which is the point. The first attempt
        // put the liar sync just after a cut whose near side already sat exactly on a frame, so
        // the end never moved and dropping the union was invisible: the fixture had been written
        // from the same assumption as the code (§16.20). The hazard needs the liar sync to fall
        // strictly BETWEEN the cut's near side and the next real frame, so that snapping the end
        // forward jumps clean over the next segment's start.
        long shortClip = 60 * SECOND;
        long[] frames = framesAt(shortClip, 60);
        for (int f = 300; f < 320; f++) {
            long frameBefore = frames[f];
            long frameAfter = frames[f + 1];
            long cutStart = frameBefore + 1_000L;              // just past a frame
            long cutEnd = cutStart + 1_000L;
            long liar = (cutEnd + frameAfter) / 2;             // between the cut and the next frame
            check(liar > cutEnd && liar < frameAfter, "the fixture does not stage the hazard");

            long[] liarSyncs = new long[]{0L, liar, 30 * SECOND};
            Plan plan = ClipPlan.build(shortClip, liarSyncs, frames, 0L, 40 * SECOND,
                    cuts(cutStart, cutEnd));
            check(plan.isUsable(), "a sync frame off the frame grid should still plan");
            check(plan.segments.size() >= 2, "the cut should have split the clip in two");
            for (int i = 0; i + 1 < plan.segments.size(); i++) {
                check(plan.segments.get(i).sourceEndUs <= plan.segments.get(i + 1).sourceStartUs,
                        "a sync frame absent from the frame table let segment " + i
                                + " end at " + plan.segments.get(i).sourceEndUs
                                + " past the next start "
                                + plan.segments.get(i + 1).sourceStartUs);
            }
        }

        // A frame table that is shuffled and repeated is normalised, not trusted.
        long[] shuffled = new long[frames.length];
        for (int i = 0; i < frames.length; i++) {
            shuffled[i] = frames[(i * 7 + 3) % frames.length];
        }
        Plan fromShuffled = ClipPlan.build(shortClip, syncsFrom(frames, 120), shuffled,
                0L, 30 * SECOND, cuts(5 * SECOND, 7_300_000L));
        Plan fromSorted = ClipPlan.build(shortClip, syncsFrom(frames, 120), frames,
                0L, 30 * SECOND, cuts(5 * SECOND, 7_300_000L));
        check(fromShuffled.outputDurationUs == fromSorted.outputDurationUs,
                "an unsorted frame table changed the answer");
        check(fromShuffled.segments.size() == fromSorted.segments.size(),
                "an unsorted frame table changed the segment count");
    }

    /**
     * Splitting divides the trim into spans, and deleting one is exactly a cut.
     *
     * This is the editor's whole cutting model, so the properties worth asserting are the ones
     * that make it coherent: the spans tile the trim with no gap and no overlap, a split outside
     * the trim is ignored rather than clamped, the span a time falls in is the one containing it,
     * and a deleted span round trips through the plan as exactly the material removed.
     */
    private static void theTimelineDividesIntoSelectableSpans() {
        long duration = 60 * SECOND;
        long[] frames = framesAt(duration, 60);
        long[] syncs = syncsFrom(frames, 120);

        for (long in = 0; in <= 10 * SECOND; in += 2_300_000L) {
            for (long out = in + 9 * SECOND; out <= 50 * SECOND; out += 9_700_000L) {
                long[] splits = new long[]{
                    in - SECOND,                 // before the trim, must be ignored
                    in + 2_100_000L,
                    in + 5_400_000L,
                    out + SECOND,                // after it, must be ignored
                };
                List<Range> spans = ClipPlan.timelineSegments(in, out, splits);

                // They tile the trim exactly.
                check(!spans.isEmpty(), "a non empty trim produced no spans");
                check(spans.get(0).startUs == in, "the first span does not start at the trim");
                check(spans.get(spans.size() - 1).endUs == out,
                        "the last span does not end at the trim");
                for (int i = 0; i + 1 < spans.size(); i++) {
                    check(spans.get(i).endUs == spans.get(i + 1).startUs,
                            "a gap or an overlap between spans " + i + " and " + (i + 1));
                    check(spans.get(i).durationUs() > 0L, "span " + i + " is empty");
                }

                // A split outside the trim changed nothing.
                check(spans.size() == 3, "expected three spans, got " + spans.size());

                // Every point in the trim lands in exactly one span, and the end lands in the
                // last one rather than nowhere.
                for (long at = in; at <= out; at += 311_000L) {
                    int index = ClipPlan.segmentAt(spans, at);
                    check(index >= 0, "no span holds " + at);
                    Range span = spans.get(index);
                    check(at >= span.startUs && at <= span.endUs,
                            "segmentAt returned a span that does not hold " + at);
                }
                check(ClipPlan.segmentAt(spans, out) == spans.size() - 1,
                        "the trim's end does not select the last span");
                check(ClipPlan.segmentAt(spans, in - 1) < 0, "a time before the trim selected one");

                // Deleting the middle span removes exactly that material and nothing else.
                Range middle = spans.get(1);
                List<Range> cuts = new ArrayList<Range>();
                cuts.add(middle);
                check(ClipPlan.isRemoved(middle, cuts), "a deleted span does not read as removed");
                check(!ClipPlan.isRemoved(spans.get(0), cuts),
                        "an untouched span reads as removed");

                Plan plan = ClipPlan.build(duration, syncs, frames, in, out, cuts);
                if (!plan.isUsable()) continue;
                for (int f = 0; f < frames.length; f++) {
                    long frame = frames[f];
                    boolean inside = frame >= middle.startUs && frame < middle.endUs;
                    boolean survived = plan.mapToOutput(frame) >= 0L;
                    if (inside) {
                        check(!survived, "a frame at " + frame + " inside the deleted span "
                                + "survived");
                    }
                }
            }
        }

        // A span that was never deleted is not removed just because a cut overlaps it, which is
        // what makes undeleting give back exactly what delete took.
        List<Range> spans = ClipPlan.timelineSegments(0L, 10 * SECOND,
                new long[]{3 * SECOND, 6 * SECOND});
        List<Range> overlapping = new ArrayList<Range>();
        overlapping.add(new Range(2 * SECOND, 7 * SECOND));
        for (int i = 0; i < spans.size(); i++) {
            check(!ClipPlan.isRemoved(spans.get(i), overlapping),
                    "an overlapping cut marked span " + i + " as deleted");
        }

        // Degenerate trims produce nothing rather than a span of negative length.
        check(ClipPlan.timelineSegments(5 * SECOND, 5 * SECOND, new long[0]).isEmpty(),
                "an empty trim produced a span");
        check(ClipPlan.timelineSegments(9 * SECOND, 2 * SECOND, new long[0]).isEmpty(),
                "an inverted trim produced a span");
        check(ClipPlan.timelineSegments(0L, 10 * SECOND, null).size() == 1,
                "a null split table should leave one span");
    }

    /**
     * A deletion survives a change to the timeline's shape, and stays nameable.
     *
     * `isRemoved` is exact equality, so the editor's shading only works while every cut is one
     * of the current spans. `retime` is what keeps that true after a split or a trim drag, and
     * the property that matters is the INVARIANT rather than any particular mapping: the result
     * must always be a subset of the spans it was given, or the plan would go on removing
     * material the screen says is being kept.
     *
     * The two concrete movements are checked by name, because they are the ones somebody would
     * "simplify": splitting inside a deleted span must leave both halves deleted, and pulling a
     * trim handle through a deleted span must leave the remaining part deleted. Both follow
     * from overlap being the test, and both break if it is tightened to a complete cover.
     */
    private static void deletionsFollowTheirSpanWhenTheTimelineChanges() {
        // The invariant, swept. Every result is one of the spans handed in, identically.
        for (long in = 0; in <= 6 * SECOND; in += 1_300_000L) {
            for (long out = in + 4 * SECOND; out <= 30 * SECOND; out += 3_100_000L) {
                long[] splits = new long[]{
                    in + 1_700_000L, in + 3_300_000L, (in + out) / 2, out - 900_000L,
                };
                List<Range> spans = ClipPlan.timelineSegments(in, out, splits);
                for (int pick = 0; pick < spans.size(); pick++) {
                    List<Range> cuts = new ArrayList<Range>();
                    cuts.add(spans.get(pick));

                    // Nothing moved: retime is the identity on a cut that is already a span.
                    List<Range> same = ClipPlan.retime(cuts, spans);
                    check(same.size() == 1 && same.get(0).startUs == spans.get(pick).startUs
                            && same.get(0).endUs == spans.get(pick).endUs,
                            "retime changed a cut that was already a span");

                    // The trim moved. Whatever comes back must be spans of the NEW timeline.
                    for (long shift = -2 * SECOND; shift <= 2 * SECOND; shift += 700_000L) {
                        long nextIn = Math.max(0L, in + shift);
                        if (nextIn >= out) continue;
                        List<Range> moved = ClipPlan.timelineSegments(nextIn, out, splits);
                        List<Range> after = ClipPlan.retime(cuts, moved);
                        for (int i = 0; i < after.size(); i++) {
                            boolean found = false;
                            for (int j = 0; j < moved.size(); j++) {
                                if (after.get(i).startUs == moved.get(j).startUs
                                        && after.get(i).endUs == moved.get(j).endUs) {
                                    found = true;
                                    break;
                                }
                            }
                            check(found, "retime produced a range that is not one of the spans");
                            check(ClipPlan.isRemoved(after.get(i), after),
                                    "a retimed cut does not read as removed");
                        }
                        // And the material is still going: a cut cannot evaporate while any of
                        // it is still inside the trim.
                        Range cut = cuts.get(0);
                        boolean stillInside = cut.endUs > nextIn && cut.startUs < out;
                        check(!stillInside || !after.isEmpty(),
                                "retime dropped a deletion that is still inside the trim");
                    }
                }
            }
        }

        // Splitting inside a deleted span leaves both halves deleted.
        List<Range> before = ClipPlan.timelineSegments(0L, 12 * SECOND, new long[]{4 * SECOND});
        List<Range> deleted = new ArrayList<Range>();
        deleted.add(before.get(1));                       // 4s to 12s
        List<Range> split = ClipPlan.timelineSegments(0L, 12 * SECOND,
                new long[]{4 * SECOND, 8 * SECOND});
        List<Range> after = ClipPlan.retime(deleted, split);
        check(after.size() == 2, "splitting a deleted span left " + after.size()
                + " deleted halves rather than two");
        check(ClipPlan.isRemoved(split.get(1), after) && ClipPlan.isRemoved(split.get(2), after),
                "a half of a split deleted span came back to life");
        check(!ClipPlan.isRemoved(split.get(0), after), "an untouched span was deleted by a split");

        // Pulling the trim through a deleted span keeps what is left of it deleted.
        List<Range> wide = ClipPlan.timelineSegments(0L, 12 * SECOND, new long[]{4 * SECOND});
        List<Range> gone = new ArrayList<Range>();
        gone.add(wide.get(0));                            // 0s to 4s
        List<Range> pulled = ClipPlan.timelineSegments(2 * SECOND, 12 * SECOND,
                new long[]{4 * SECOND});
        List<Range> kept = ClipPlan.retime(gone, pulled);
        check(kept.size() == 1 && kept.get(0).startUs == 2 * SECOND
                && kept.get(0).endUs == 4 * SECOND,
                "pulling the trim through a deleted span did not keep the rest of it deleted");

        // Pulling the trim PAST it entirely drops it, because there is nothing left to delete.
        List<Range> past = ClipPlan.timelineSegments(4 * SECOND, 12 * SECOND, new long[0]);
        check(ClipPlan.retime(gone, past).isEmpty(),
                "a deletion the trim moved past is still being applied");

        // Touching is not overlapping, which is what keeps the half open convention intact.
        List<Range> touching = new ArrayList<Range>();
        touching.add(new Range(0L, 4 * SECOND));
        List<Range> neighbour = ClipPlan.timelineSegments(4 * SECOND, 12 * SECOND, new long[0]);
        check(ClipPlan.retime(touching, neighbour).isEmpty(),
                "a cut that merely touches a span deleted it");

        // Each span appears at most once, however many old cuts it swallowed. Removing a split
        // between two deleted spans is the case: the merged span overlaps both cuts, and a list
        // naming it twice would still shade and still export correctly, which is exactly why it
        // would survive unnoticed until something downstream counted the deletions.
        List<Range> twoDeleted = ClipPlan.timelineSegments(0L, 12 * SECOND,
                new long[]{4 * SECOND, 8 * SECOND});
        List<Range> both = new ArrayList<Range>();
        both.add(twoDeleted.get(0));
        both.add(twoDeleted.get(1));
        List<Range> merged = ClipPlan.timelineSegments(0L, 12 * SECOND, new long[]{8 * SECOND});
        List<Range> once = ClipPlan.retime(both, merged);
        check(once.size() == 1, "a merged span was named " + once.size() + " times, not once");

        // A span of no length is never deleted, however a cut straddles it. `timelineSegments`
        // cannot produce one, so this is the contract for a hand built list rather than a case
        // the editor reaches, and it is checked because a guard nothing exercises is a guard
        // somebody removes.
        List<Range> degenerate = new ArrayList<Range>();
        degenerate.add(new Range(5 * SECOND, 5 * SECOND));
        degenerate.add(null);
        List<Range> straddling = new ArrayList<Range>();
        straddling.add(new Range(SECOND, 9 * SECOND));
        check(ClipPlan.retime(straddling, degenerate).isEmpty(),
                "an empty span was reported as deleted");

        // Nulls and emptiness come back empty rather than throwing.
        check(ClipPlan.retime(null, before).isEmpty(), "a null cut list did not come back empty");
        check(ClipPlan.retime(deleted, null).isEmpty(), "a null span list did not come back empty");
        check(ClipPlan.retime(new ArrayList<Range>(), before).isEmpty(),
                "an empty cut list did not come back empty");
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }

    /** check() that also reports whether it held, so a sweep can stop at the first failure. */
    private static boolean okay(boolean condition, String message) {
        check(condition, message);
        return condition;
    }
}
