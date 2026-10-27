package net.kdt.pojavlaunch.recorder.edit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Which parts of a recording survive an edit, and where each lands in the output.
 *
 * This is the whole of the decision making for trimming and cutting a clip. The Android side
 * ({@code ClipEditor}) does nothing but walk a {@code MediaExtractor}, add each surviving
 * sample's segment offset to its timestamp, and hand it to a {@code MediaMuxer}. It takes that
 * offset from the {@link Segment} it is already iterating rather than calling
 * {@link Plan#mapToOutput}, because the latter answers -1 for "no segment holds this" and a
 * negative output time is indistinguishable from that sentinel. All of the arithmetic
 * is here, and none of it is there, because <b>a wrong plan is a corrupt or wrong length video
 * that compiles perfectly</b> and there is no device in this project's CI to notice. Plain Java
 * with no Android imports so {@code scripts/clipsim} compiles this exact file and drives it
 * (§16.25).
 *
 * <h2>Why the edit is lossless, and what that costs</h2>
 *
 * The samples are copied, never decoded and re-encoded. That is instant rather than minutes, it
 * cannot lose quality, and it needs no transcode pipeline. The price is a rule about where a cut
 * may land, and the rule is narrower than it first appears:
 *
 * <ul>
 *   <li><b>A segment's first video sample must be a sync frame</b>, because a decoder cannot start
 *       anywhere else. {@code GameRecorder} asks for an I frame every two seconds, so the points
 *       where playback <i>resumes</i> sit on a roughly two second grid: the trim start, and the
 *       far side of every cut.</li>
 *   <li><b>A segment's end is unconstrained.</b> Nothing needs to decode past it. So the trim end
 *       and the near side of every cut are frame accurate.</li>
 * </ul>
 *
 * <h2>Why the end still snaps, to a frame rather than to a sync frame</h2>
 *
 * An end that is merely <i>allowed</i> to land anywhere should still land on a frame boundary, and
 * this is the one piece of arithmetic here that is not obvious. A player's output duration is the
 * sum of the segment durations, and each frame is shown until the next one appears. Take a segment
 * ending at 90000us of a 60fps clip: the last frame inside it is at 83333, the next is at 100000,
 * and the segment is declared 90000 long. The following segment's first frame therefore lands
 * 6667us after that last frame instead of 16667us after it, so the frame at the join is shown for
 * four tenths of its time. It is a sub frame error and it happens once per join, which is why it
 * would never be found by looking: but it is free to remove, because the end is snapped
 * <b>forward to the next frame's timestamp</b>, which keeps <i>exactly the same set of frames</i>
 * (the span is half open, so a frame sitting on the new end is still excluded) and simply states
 * the duration truthfully.
 *
 * <p>That it cannot admit a frame is what makes it safe to do this on the near side of a cut, and
 * it rests on one fact: <b>every sync frame is a frame</b>. The two tables are unioned here rather
 * than assumed to nest, so a malformed file cannot make the end of one segment overrun the start
 * of the next.
 *
 * <p>Passing no frame table is allowed and simply turns this off.
 *
 * That asymmetry is worth stating because the obvious assumption is that both ends of every edit
 * snap, and half of them do not. It is also why the UI snaps only the handles that genuinely must
 * snap, and leaves the others free.
 *
 * <h2>The direction a snap errs in</h2>
 *
 * A resume point is snapped <b>forward</b>, to the next sync frame at or after the requested time.
 * So <b>a cut always removes at least what was asked for</b>, and may remove up to two seconds
 * more. The other direction was considered and rejected: somebody cutting out a death, a mistake
 * or a face on a shared screen is asking for that material to be gone, and a cut that quietly
 * left 1.9 seconds of it in would be the one failure this feature must not have. Losing a little
 * extra footage is a disappointment; failing to remove what you were told to remove is a breach.
 *
 * <p>The trim start is the one resume point where that reasoning does not apply, since there is no
 * material being removed on purpose, only dead air at the front. It snaps forward too, for
 * consistency and because the alternative is a plan whose first segment can start before the
 * requested in point, which reads as a bug every time somebody checks.
 */
public final class ClipPlan {

    private ClipPlan() {}

    /** A half open span of source time, in microseconds. */
    public static final class Range {
        public final long startUs;
        /** Exclusive. */
        public final long endUs;

        public Range(long startUs, long endUs) {
            this.startUs = startUs;
            this.endUs = endUs;
        }

        public long durationUs() {
            return Math.max(0L, endUs - startUs);
        }

        @Override
        public String toString() {
            return "[" + startUs + "," + endUs + ")";
        }
    }

    /**
     * One run of samples copied from the source, and where it lands in the output.
     *
     * {@code sourceStartUs} is always a sync frame. {@code outputStartUs} is the accumulated
     * duration of the segments before it, which is what closes the gap a cut leaves behind.
     */
    public static final class Segment {
        public final long sourceStartUs;
        /** Exclusive. */
        public final long sourceEndUs;
        public final long outputStartUs;

        Segment(long sourceStartUs, long sourceEndUs, long outputStartUs) {
            this.sourceStartUs = sourceStartUs;
            this.sourceEndUs = sourceEndUs;
            this.outputStartUs = outputStartUs;
        }

        public long durationUs() {
            return sourceEndUs - sourceStartUs;
        }

        /** The shift applied to every sample in this segment. */
        public long offsetUs() {
            return outputStartUs - sourceStartUs;
        }

        @Override
        public String toString() {
            return "[" + sourceStartUs + "," + sourceEndUs + ")->" + outputStartUs;
        }
    }

    /**
     * Why a plan cannot be carried out, when it cannot.
     *
     * A refusal with a reason, rather than an empty plan: an editor that silently wrote a
     * zero length file would be the same class of failure as a wrong plan.
     */
    public enum Problem {
        /** The plan is usable. */
        NONE,
        /** The in point is not before the out point, so nothing was selected. */
        EMPTY_SELECTION,
        /** The track declares no sync frames at all, so no segment can be started. */
        NO_SYNC_FRAMES,
        /** The cuts consume the whole selection, or every segment snapped away to nothing. */
        NOTHING_LEFT
    }

    /** A plan, or a refusal carrying the reason. */
    public static final class Plan {
        public final List<Segment> segments;
        public final long outputDurationUs;
        public final Problem problem;
        /** How much longer the cuts came out than asked, from snapping resume points forward. */
        public final long extraRemovedUs;

        Plan(List<Segment> segments, long outputDurationUs, Problem problem, long extraRemovedUs) {
            this.segments = Collections.unmodifiableList(segments);
            this.outputDurationUs = outputDurationUs;
            this.problem = problem;
            this.extraRemovedUs = extraRemovedUs;
        }

        public boolean isUsable() {
            return problem == Problem.NONE && !segments.isEmpty() && outputDurationUs > 0L;
        }

        /**
         * The output timestamp for a source sample, or -1 if the sample is dropped.
         *
         * This is the executor's whole inner loop. Half open on purpose: a sample exactly at a
         * segment's end belongs to no segment, so a boundary can never duplicate a frame.
         */
        public long mapToOutput(long sourceUs) {
            for (int i = 0; i < segments.size(); i++) {
                Segment segment = segments.get(i);
                if (sourceUs >= segment.sourceStartUs && sourceUs < segment.sourceEndUs) {
                    return sourceUs + segment.offsetUs();
                }
            }
            return -1L;
        }

        // No seekTargetOf either: the executor walks this list, so it has the segment in hand
        // and an accessor that re-finds it by index is a second way to say the same thing.
    }

    /* ------------------------------------------------------------------ sync frames */

    /**
     * The first sync frame at or after {@code us}, or -1 if there is none.
     *
     * Binary search rather than a walk: a forty minute clip at a sync frame every two seconds has
     * on the order of a thousand of them, and the UI calls this on every drag frame.
     */
    public static long syncAtOrAfter(long[] syncUs, long us) {
        if (syncUs == null || syncUs.length == 0) return -1L;
        int at = Arrays.binarySearch(syncUs, us);
        if (at >= 0) return syncUs[at];
        int insertion = -(at + 1);
        if (insertion >= syncUs.length) return -1L;
        return syncUs[insertion];
    }

    // There is deliberately no syncAtOrBefore and no nearestSync. Both existed, both were
    // tested, and neither had a caller: snapping here is forward-only, by design, so a "nearest"
    // helper would hand the UI a point the planner will not use, and a handle that clicked onto
    // it would disagree with the clip that got saved. A tested helper whose documented purpose
    // is wrong is worse than no helper.

    /**
     * The first frame at or after {@code us}, or {@code fallbackUs} when there is none.
     *
     * Separate from {@link #syncAtOrAfter} despite the identical shape, because the fallback is
     * the whole difference: a resume point past the last sync frame is a refusal, while an end
     * past the last frame is simply the end of the clip.
     */
    public static long frameEndAtOrAfter(long[] framesUs, long us, long fallbackUs) {
        if (framesUs == null || framesUs.length == 0) return fallbackUs;
        int at = Arrays.binarySearch(framesUs, us);
        if (at >= 0) return framesUs[at];
        int insertion = -(at + 1);
        if (insertion >= framesUs.length) return fallbackUs;
        return framesUs[insertion];
    }

    /* ------------------------------------------------------------------ the plan */

    /**
     * Work out which spans of the source survive, and where each lands.
     *
     * @param durationUs the clip's duration
     * @param syncUs     the video track's sync frame timestamps, ascending. Duplicates and
     *                   unsorted input are tolerated rather than trusted, because this comes from
     *                   walking a file somebody else wrote.
     * @param framesUs   every video frame's timestamp, ascending, or null to leave segment ends
     *                   unsnapped. See the class comment for what this buys.
     * @param inUs       the trim start
     * @param outUs      the trim end, exclusive
     * @param cuts       spans to remove from inside the selection. May overlap and be in any
     *                   order; they are normalised here.
     */
    public static Plan build(long durationUs, long[] syncUs, long[] framesUs, long inUs,
                             long outUs, List<Range> cuts) {
        List<Segment> empty = new ArrayList<Segment>();

        if (syncUs == null || syncUs.length == 0) {
            return new Plan(empty, 0L, Problem.NO_SYNC_FRAMES, 0L);
        }
        // Defensive copy, sorted and deduplicated: the caller got these by walking a container
        // and a malformed file can repeat or reorder them. Sorting a thousand longs is free next
        // to the file read that produced them.
        long[] syncs = normaliseSyncs(syncUs);
        // Unioned, not assumed to nest. Every sync frame IS a frame, and that is exactly what
        // stops a snapped end from overrunning the next segment's start, so it is established
        // here rather than trusted to the file.
        //
        // Empty stays empty rather than becoming the sync table: unioning a missing frame table
        // with the syncs would snap every end to the next SYNC frame, which is the coarse
        // behaviour this whole asymmetry exists to avoid, and it would do it silently.
        long[] frames = (framesUs == null || framesUs.length == 0)
                ? new long[0] : unionSorted(normaliseSyncs(framesUs), syncs);

        long clipDuration = Math.max(0L, durationUs);
        long in = clamp(inUs, 0L, clipDuration);
        long out = clamp(outUs, 0L, clipDuration);
        if (in >= out) {
            return new Plan(empty, 0L, Problem.EMPTY_SELECTION, 0L);
        }

        List<Range> keeps = subtract(new Range(in, out), cuts);
        if (keeps.isEmpty()) {
            return new Plan(empty, 0L, Problem.NOTHING_LEFT, 0L);
        }

        List<Segment> segments = new ArrayList<Segment>();
        long outputCursor = 0L;
        long extraRemoved = 0L;
        for (int i = 0; i < keeps.size(); i++) {
            Range keep = keeps.get(i);
            // Only the START snaps, and only forward. See the class comment: the end does not
            // need to decode, and forward is what makes a cut never under-remove.
            long start = syncAtOrAfter(syncs, keep.startUs);
            if (start < 0L || start >= keep.endUs) {
                // No sync frame inside this span at all, so it cannot be started. That is the
                // whole span lost, which counts as removed on top of what was asked for.
                extraRemoved += keep.durationUs();
                continue;
            }
            extraRemoved += start - keep.startUs;
            // Forward to the next frame. Cannot admit a frame (half open) and cannot reach the
            // next segment's start (which is a sync frame, and therefore a frame, at or after
            // this end), so it changes the declared duration and nothing else.
            long end = frames.length == 0
                    ? keep.endUs : frameEndAtOrAfter(frames, keep.endUs, clipDuration);
            if (end < keep.endUs) end = keep.endUs;
            segments.add(new Segment(start, end, outputCursor));
            outputCursor += end - start;
        }

        if (segments.isEmpty()) {
            return new Plan(empty, 0L, Problem.NOTHING_LEFT, extraRemoved);
        }
        return new Plan(segments, outputCursor, Problem.NONE, extraRemoved);
    }

    /**
     * The simple case, kept separate because it is the one almost everybody uses.
     *
     * A trim with no cuts. Exactly equivalent to {@link #build} with an empty cut list, and worth
     * its own name so a caller doing the common thing does not have to build an empty list.
     */
    public static Plan trim(long durationUs, long[] syncUs, long[] framesUs, long inUs,
                            long outUs) {
        return build(durationUs, syncUs, framesUs, inUs, outUs, new ArrayList<Range>());
    }

    /* ------------------------------------------------------------------ range algebra */

    /**
     * {@code whole} minus every range in {@code cuts}, as an ascending list of disjoint spans.
     *
     * <b>No merging step, and that is deliberate rather than an omission.</b> The first version
     * merged overlapping and touching cuts first, and mutation testing showed both halves of that
     * were dead: {@code cursor = max(cursor, cut.endUs)} already absorbs an overlap, and the
     * {@code cut.startUs > cursor} guard already refuses to emit the zero length sliver two
     * abutting cuts would otherwise leave. Dead code that looks load bearing is worse than no
     * code, so it went. The sort upstream is <i>not</i> dead: this walk is only correct on
     * ascending input.
     */
    static List<Range> subtract(Range whole, List<Range> cuts) {
        List<Range> result = new ArrayList<Range>();
        if (whole.endUs <= whole.startUs) return result;

        List<Range> sorted = sortedClipped(cuts, whole);
        long cursor = whole.startUs;
        for (int i = 0; i < sorted.size(); i++) {
            Range cut = sorted.get(i);
            if (cut.startUs > cursor) {
                result.add(new Range(cursor, cut.startUs));
            }
            cursor = Math.max(cursor, cut.endUs);
        }
        if (cursor < whole.endUs) {
            result.add(new Range(cursor, whole.endUs));
        }
        return result;
    }

    /** Clip every cut to the selection, drop the empties, and sort. Ascending order is required. */
    private static List<Range> sortedClipped(List<Range> cuts, Range whole) {
        List<Range> clipped = new ArrayList<Range>();
        if (cuts != null) {
            for (int i = 0; i < cuts.size(); i++) {
                Range cut = cuts.get(i);
                if (cut == null) continue;
                // A reversed range is a bug upstream, not an instruction: normalise rather than
                // letting it subtract nothing or everything depending on the comparison order.
                long lo = Math.min(cut.startUs, cut.endUs);
                long hi = Math.max(cut.startUs, cut.endUs);
                long start = Math.max(lo, whole.startUs);
                long end = Math.min(hi, whole.endUs);
                if (end > start) clipped.add(new Range(start, end));
            }
        }
        Collections.sort(clipped, new Comparator<Range>() {
            @Override
            public int compare(Range a, Range b) {
                if (a.startUs != b.startUs) return a.startUs < b.startUs ? -1 : 1;
                if (a.endUs != b.endUs) return a.endUs < b.endUs ? -1 : 1;
                return 0;
            }
        });

        return clipped;
    }

    private static long[] normaliseSyncs(long[] syncUs) {
        if (syncUs == null) return new long[0];
        // The common case by a long way: a table read by walking a container in order is already
        // ascending and unique, and for a forty minute clip the frame table is well over a
        // hundred thousand entries. The UI rebuilds a plan on every frame of a drag, so copying
        // and sorting that unconditionally is megabytes of garbage per second for no answer that
        // differs. The array is never written to, so handing the caller's own back is safe.
        boolean ascending = true;
        for (int i = 1; i < syncUs.length; i++) {
            if (syncUs[i] <= syncUs[i - 1]) {
                ascending = false;
                break;
            }
        }
        if (ascending) return syncUs;
        long[] copy = new long[syncUs.length];
        System.arraycopy(syncUs, 0, copy, 0, syncUs.length);
        Arrays.sort(copy);
        int unique = 0;
        for (int i = 0; i < copy.length; i++) {
            if (i == 0 || copy[i] != copy[i - 1]) {
                copy[unique++] = copy[i];
            }
        }
        if (unique == copy.length) return copy;
        long[] trimmed = new long[unique];
        System.arraycopy(copy, 0, trimmed, 0, unique);
        return trimmed;
    }

    /**
     * Both tables as one ascending, deduplicated table.
     *
     * Both inputs are already sorted and unique, so this is a merge rather than a second sort.
     * It exists to establish "every sync frame is a frame" rather than to assume it: a file whose
     * sync table names a timestamp absent from its frame table would otherwise let a snapped
     * segment end run past the next segment's start, which is the one way this snapping could
     * corrupt a plan.
     */
    private static long[] unionSorted(long[] a, long[] b) {
        if (a.length == 0) return b;
        if (b.length == 0) return a;
        // Same reasoning as above, and the same common case: in any well formed file every sync
        // frame IS a frame, so the union is just the frame table. Proving that is one merge walk
        // with no allocation, against allocating and filling an array twice the size of the
        // frame table on every call.
        boolean contained = true;
        int ai = 0;
        for (int bi = 0; bi < b.length; bi++) {
            while (ai < a.length && a[ai] < b[bi]) ai++;
            if (ai >= a.length || a[ai] != b[bi]) {
                contained = false;
                break;
            }
        }
        if (contained) return a;
        long[] merged = new long[a.length + b.length];
        int i = 0, j = 0, n = 0;
        while (i < a.length || j < b.length) {
            long next;
            if (i == a.length) {
                next = b[j++];
            } else if (j == b.length) {
                next = a[i++];
            } else if (a[i] < b[j]) {
                next = a[i++];
            } else if (b[j] < a[i]) {
                next = b[j++];
            } else {
                next = a[i++];
                j++;
            }
            if (n == 0 || merged[n - 1] != next) merged[n++] = next;
        }
        if (n == merged.length) return merged;
        long[] trimmed = new long[n];
        System.arraycopy(merged, 0, trimmed, 0, n);
        return trimmed;
    }

    private static long clamp(long value, long lo, long hi) {
        if (value < lo) return lo;
        if (value > hi) return hi;
        return value;
    }
}
