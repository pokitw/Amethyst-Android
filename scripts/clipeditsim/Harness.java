package net.kdt.pojavlaunch.recorder.edit;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaCodec;
import android.util.Log;

import net.kdt.pojavlaunch.recorder.RecordingInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Drives the shipped {@link ClipEditor} against a synthetic MP4.
 *
 * {@code ClipPlan} decides, {@code ClipEditor} carries out, and {@code clipsim} already proves
 * the deciding. This proves the carrying out: that the executor walks the right samples, rebases
 * them with one shared offset per segment, translates the flags rather than casting them, and
 * refuses every way the platform fails silently instead of walking into it.
 */
public final class Harness {

    private static final long SECOND = 1_000_000L;
    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<String>();

    private static File sTempDir;

    public static void main(String[] args) throws Exception {
        sTempDir = new File(args.length > 0 ? args[0] : "/tmp/clipeditsim");
        if (!sTempDir.exists() && !sTempDir.mkdirs()) {
            throw new IOException("could not make " + sTempDir);
        }

        readFindsEveryFrameAndEverySyncFrame();
        aTrimCopiesExactlyTheSelectedSamples();
        aCutLeavesNoneOfItsMaterialBehind();
        audioAndVideoShareOneOffsetPerSegment();
        flagsAreTranslatedAndNotCast();
        everySegmentStartsOnAKeyFrame();
        theMuxerIsNeverAskedToDoSomethingThatHangs();
        aTooSmallBufferIsExplained();
        platformRefusalsLeaveNothingBehind();
        theOriginalIsNeverTouched();
        bFramesAreRefusedRatherThanMangled();
        rotationIsCarriedAcrossAndGuarded();
        theExecutorsOwnGuardsFire();
        anUndeclaredBufferIsStillBigEnough();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String failure : FAILURES) System.out.println("FAIL: " + failure);
            System.out.println(FAILURES.size() + " FAILURES");
            System.exit(1);
        }
        System.out.println("all passed");
    }

    /* ------------------------------------------------------------------ fixtures */

    private static MediaFormat videoFormat(long durationUs, int maxInput) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "video/avc");
        format.setLong(MediaFormat.KEY_DURATION, durationUs);
        format.setInteger(MediaFormat.KEY_WIDTH, 1920);
        format.setInteger(MediaFormat.KEY_HEIGHT, 1080);
        if (maxInput > 0) format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInput);
        return format;
    }

    private static MediaFormat audioFormat(long durationUs) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setLong(MediaFormat.KEY_DURATION, durationUs);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);
        return format;
    }

    /** A video track at the given rate with a sync frame every two seconds. */
    private static MediaExtractor.Track videoTrack(long durationUs, int fps, int sampleBytes) {
        int count = (int) (durationUs * fps / SECOND);
        long[] times = new long[count];
        boolean[] sync = new boolean[count];
        int[] sizes = new int[count];
        int[] extra = new int[count];
        for (int i = 0; i < count; i++) {
            times[i] = i * SECOND / fps;
            sync[i] = i % (fps * 2) == 0;
            sizes[i] = sync[i] ? sampleBytes : sampleBytes / 8;
        }
        return new MediaExtractor.Track("video/avc", times, sync, sizes, extra,
                videoFormat(durationUs, sampleBytes));
    }

    /** An AAC track: 1024 samples at 48 kHz, every frame independently decodable. */
    private static MediaExtractor.Track audioTrack(long durationUs) {
        long step = 1024L * SECOND / 48000L;        // 21333us
        int count = (int) (durationUs / step);
        long[] times = new long[count];
        boolean[] sync = new boolean[count];
        int[] sizes = new int[count];
        int[] extra = new int[count];
        for (int i = 0; i < count; i++) {
            times[i] = i * step;
            sync[i] = true;
            sizes[i] = 384;
        }
        return new MediaExtractor.Track("audio/mp4a-latm", times, sync, sizes, extra,
                audioFormat(durationUs));
    }

    private static void install(MediaExtractor.Track... tracks) {
        MediaExtractor.reset();
        MediaMuxer.reset();
        RecordingInfo.PRESENT = true;
        RecordingInfo.WRITE_THROWS = false;
        MediaExtractor.NEXT_FILE = new ArrayList<MediaExtractor.Track>(Arrays.asList(tracks));
    }

    /** A real, empty file for the executor to open a descriptor on. */
    private static File freshFile(String name) throws IOException {
        File file = new File(sTempDir, name);
        if (file.exists() && !file.delete()) throw new IOException("could not clear " + file);
        FileOutputStream out = new FileOutputStream(file);
        out.write(new byte[]{1, 2, 3});
        out.close();
        // Clear anything a previous run left beside it.
        File[] siblings = sTempDir.listFiles();
        if (siblings != null) {
            for (File sibling : siblings) {
                if (!sibling.equals(file)) sibling.delete();
            }
        }
        return file;
    }

    /* ------------------------------------------------------------------ checks */

    /** read() reports every frame, every sync frame, and notices decode order. */
    private static void readFindsEveryFrameAndEverySyncFrame() throws Exception {
        long duration = 30 * SECOND;
        install(videoTrack(duration, 60, 200_000), audioTrack(duration));
        ClipEditor.Source source = ClipEditor.read(freshFile("read.mp4"));

        check(source.framesUs.length == 1800, "expected 1800 frames, got " + source.framesUs.length);
        check(source.syncUs.length == 15, "expected 15 sync frames, got " + source.syncUs.length);
        check(source.hasAudio, "the audio track was not noticed");
        check(source.width == 1920 && source.height == 1080, "the size was not read");
        check(source.framesInOrder, "an in order track was reported out of order");
        check(source.isUsable(), "a good clip was reported unusable");
        for (int i = 0; i < source.syncUs.length; i++) {
            check(source.syncUs[i] == i * 2 * SECOND,
                    "sync frame " + i + " is at " + source.syncUs[i]);
            check(Arrays.binarySearch(source.framesUs, source.syncUs[i]) >= 0,
                    "a sync frame is missing from the frame table, which is the one relationship "
                            + "ClipPlan's end snapping depends on");
        }

        // A container that declares no duration falls back to the last timestamp rather than
        // reporting zero, which would make every selection empty.
        MediaFormat noDuration = videoFormat(0L, 200_000);
        noDuration.getMap().remove(MediaFormat.KEY_DURATION);
        MediaExtractor.Track bare = videoTrack(duration, 60, 200_000);
        install(new MediaExtractor.Track(bare.mime, bare.timesUs, bare.sync, bare.sizes,
                bare.extraFlags, noDuration));
        ClipEditor.Source undeclared = ClipEditor.read(freshFile("nodur.mp4"));
        check(undeclared.durationUs > 29 * SECOND,
                "a clip with no declared duration came back as " + undeclared.durationUs);

        // No video track at all is an IOException, not a null.
        install(audioTrack(duration));
        try {
            ClipEditor.read(freshFile("audioonly.mp4"));
            check(false, "a file with no video track should not read");
        } catch (IOException expected) {
            check(true, "");
        }
    }

    /** The samples written are exactly the frames inside the selection, rebased to zero. */
    private static void aTrimCopiesExactlyTheSelectedSamples() throws Exception {
        long duration = 30 * SECOND;
        install(videoTrack(duration, 60, 200_000));
        File file = freshFile("trim.mp4");
        ClipEditor.Source source = ClipEditor.read(file);

        ClipPlan.Plan plan = ClipPlan.trim(source.durationUs, source.syncUs, source.framesUs,
                4 * SECOND, 10 * SECOND);
        check(plan.isUsable(), "a plain trim should plan");

        File out = ClipEditor.export(file, source, plan, null);
        MediaMuxer muxer = MediaMuxer.LAST;
        check(muxer.stopped, "the muxer was not stopped, so the file has no moov box");
        check(out.exists(), "the output does not exist");
        check(!new File(out.getAbsolutePath() + ".part").exists(), "a .part file was left behind");

        List<MediaMuxer.Written> written = muxer.of(0);
        // Every frame in [4s, 10s) and no other.
        int expected = 0;
        for (int i = 0; i < source.framesUs.length; i++) {
            if (source.framesUs[i] >= 4 * SECOND && source.framesUs[i] < 10 * SECOND) expected++;
        }
        check(written.size() == expected,
                "wrote " + written.size() + " samples, expected " + expected);
        check(written.get(0).presentationTimeUs == 0L,
                "the first sample is at " + written.get(0).presentationTimeUs + ", not zero");
        check((written.get(0).flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0,
                "the first sample is not a key frame");
        for (int i = 1; i < written.size(); i++) {
            check(written.get(i).presentationTimeUs > written.get(i - 1).presentationTimeUs,
                    "timestamps are not strictly increasing at " + i);
        }
        long last = written.get(written.size() - 1).presentationTimeUs;
        check(last < plan.outputDurationUs,
                "the last sample at " + last + " is past the declared duration "
                        + plan.outputDurationUs);
    }

    /** Nothing from inside a cut reaches the output. */
    private static void aCutLeavesNoneOfItsMaterialBehind() throws Exception {
        long duration = 60 * SECOND;
        install(videoTrack(duration, 60, 200_000), audioTrack(duration));
        File file = freshFile("cut.mp4");
        ClipEditor.Source source = ClipEditor.read(file);

        List<ClipPlan.Range> cuts = new ArrayList<ClipPlan.Range>();
        cuts.add(new ClipPlan.Range(10 * SECOND, 20 * SECOND));
        cuts.add(new ClipPlan.Range(30 * SECOND, 34 * SECOND));
        ClipPlan.Plan plan = ClipPlan.build(source.durationUs, source.syncUs, source.framesUs,
                0L, 50 * SECOND, cuts);
        check(plan.isUsable(), "a cut plan should be usable");

        ClipEditor.export(file, source, plan, null);
        MediaMuxer muxer = MediaMuxer.LAST;

        // Invert the mapping: for each written sample, find the source time it came from and
        // assert it was never inside a cut.
        for (int t = 0; t < 2; t++) {
            List<MediaMuxer.Written> written = muxer.of(t);
            check(!written.isEmpty(), "track " + t + " received nothing");
            for (int i = 0; i < written.size(); i++) {
                long outUs = written.get(i).presentationTimeUs;
                long sourceUs = sourceTimeOf(plan, outUs);
                check(sourceUs >= 0L, "output time " + outUs + " maps back to no segment");
                for (int c = 0; c < cuts.size(); c++) {
                    ClipPlan.Range cut = cuts.get(c);
                    check(!(sourceUs >= cut.startUs && sourceUs < cut.endUs),
                            "a sample from " + sourceUs + " survived the cut ["
                                    + cut.startUs + "," + cut.endUs + ")");
                }
            }
        }
    }

    /**
     * Audio and video are shifted by the same amount in every segment.
     *
     * The research called this the single most important invariant and it is easy to get wrong in
     * the obvious way: deriving each track's offset from its own first kept sample. Audio's first
     * kept sample is up to one AAC frame later than the video sync frame it belongs to, so a per
     * track offset drifts the two by up to 21ms at every cut, cumulatively.
     */
    private static void audioAndVideoShareOneOffsetPerSegment() throws Exception {
        long duration = 90 * SECOND;
        long audioStep = 1024L * SECOND / 48000L;
        install(videoTrack(duration, 60, 200_000), audioTrack(duration));
        File file = freshFile("sync.mp4");
        ClipEditor.Source source = ClipEditor.read(file);

        int trials = 0;
        for (int trial = 0; trial < 6; trial++) {
            List<ClipPlan.Range> cuts = new ArrayList<ClipPlan.Range>();
            cuts.add(new ClipPlan.Range(7 * SECOND + trial * 130_000L, 13 * SECOND));
            cuts.add(new ClipPlan.Range(25 * SECOND, 31 * SECOND + trial * 70_000L));
            ClipPlan.Plan plan = ClipPlan.build(source.durationUs, source.syncUs,
                    source.framesUs, 2 * SECOND, 60 * SECOND, cuts);
            if (!plan.isUsable()) continue;
            trials++;

            MediaMuxer.reset();
            MediaExtractor.NEXT_FILE = new ArrayList<MediaExtractor.Track>(
                    Arrays.asList(videoTrack(duration, 60, 200_000), audioTrack(duration)));
            ClipEditor.export(file, source, plan, null);
            MediaMuxer muxer = MediaMuxer.LAST;

            // The output must be EXACTLY what mapToOutput says, for both tracks. Stated this way
            // rather than by re-deriving each sample's source time from the offset, which is the
            // shape the first draft had and was a tautology: it recovered the source time using
            // the very offset it then asserted.
            //
            // A per-track offset - deriving the shift from each track's own first kept sample -
            // is the obvious wrong implementation, and it fails here on the audio track at the
            // first cut, because audio's first kept sample is up to one AAC frame later than the
            // video sync frame it belongs to.
            for (int t = 0; t < 2; t++) {
                // The fixture's ACTUAL timestamps, never a step re-derived here: a video frame
                // sits at i*1000000/60, which is not i*16666, and synthesising the series drifts
                // a microsecond at a time until the check is comparing nothing in particular.
                long[] sourceTimes = t == 0 ? source.framesUs : audioTimes(duration, audioStep);
                List<Long> expected = new ArrayList<Long>();
                for (int i = 0; i < sourceTimes.length; i++) {
                    long mapped = plan.mapToOutput(sourceTimes[i]);
                    if (mapped >= 0L) expected.add(Long.valueOf(mapped));
                }
                List<MediaMuxer.Written> got = muxer.of(t);
                check(got.size() == expected.size(),
                        "track " + t + " wrote " + got.size() + " samples where mapToOutput "
                                + "names " + expected.size());
                int n = Math.min(got.size(), expected.size());
                for (int i = 0; i < n; i++) {
                    check(got.get(i).presentationTimeUs == expected.get(i).longValue(),
                            "track " + t + " sample " + i + " landed at "
                                    + got.get(i).presentationTimeUs + ", mapToOutput says "
                                    + expected.get(i));
                }
            }

            // And the thing that actually matters to a viewer: a sound and the picture that goes
            // with it stay the same distance apart after the edit as before it.
            for (long sourceUs = 2 * SECOND; sourceUs < 60 * SECOND; sourceUs += 311_000L) {
                long audioAt = (sourceUs / audioStep) * audioStep;
                long videoAt = frameAtOrBefore(source.framesUs, sourceUs);
                long a = plan.mapToOutput(audioAt);
                long v = plan.mapToOutput(videoAt);
                if (a < 0L || v < 0L) continue;
                check((a - v) == (audioAt - videoAt),
                        "sound and picture drifted apart by "
                                + ((a - v) - (audioAt - videoAt)) + "us at " + sourceUs);
            }
        }
        check(trials >= 5, "only " + trials + " arrangements were exercised");
    }

    /**
     * The sample flags are translated, not cast.
     *
     * SAMPLE_FLAG_ENCRYPTED is 2 and so is BUFFER_FLAG_CODEC_CONFIG; SAMPLE_FLAG_PARTIAL_FRAME is
     * 4 and so is BUFFER_FLAG_END_OF_STREAM. Casting therefore tells the muxer "this sample is
     * codec configuration" or "this is the end of the stream" on any source that sets either.
     */
    private static void flagsAreTranslatedAndNotCast() throws Exception {
        long duration = 20 * SECOND;
        MediaExtractor.Track video = videoTrack(duration, 60, 200_000);
        // Decorate every non sync frame with the two colliding flags.
        int[] extra = new int[video.timesUs.length];
        for (int i = 0; i < extra.length; i++) {
            if (!video.sync[i]) {
                extra[i] = MediaExtractor.SAMPLE_FLAG_ENCRYPTED
                        | MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME;
            }
        }
        install(new MediaExtractor.Track(video.mime, video.timesUs, video.sync, video.sizes,
                extra, video.format));
        File file = freshFile("flags.mp4");
        ClipEditor.Source source = ClipEditor.read(file);
        ClipPlan.Plan plan = ClipPlan.trim(source.durationUs, source.syncUs, source.framesUs,
                0L, 10 * SECOND);
        ClipEditor.export(file, source, plan, null);

        boolean sawANonKeyFrame = false;
        for (MediaMuxer.Written w : MediaMuxer.LAST.of(0)) {
            check((w.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0,
                    "an encrypted source sample was written as codec configuration");
            check((w.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0,
                    "a partial frame was written as the end of the stream");
            check(w.flags == 0 || w.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME,
                    "an unexpected flag " + w.flags + " reached the muxer");
            if (w.flags == 0) sawANonKeyFrame = true;
        }
        check(sawANonKeyFrame, "the fixture never produced a decorated sample, so this "
                + "check proved nothing");
        // And the sync bit still got through, or the output would not decode.
        check((MediaMuxer.LAST.of(0).get(0).flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0,
                "the key frame flag was dropped in translation");
    }

    /** Every segment's first video sample carries the key frame flag. */
    private static void everySegmentStartsOnAKeyFrame() throws Exception {
        long duration = 60 * SECOND;
        install(videoTrack(duration, 60, 200_000));
        File file = freshFile("keyframes.mp4");
        ClipEditor.Source source = ClipEditor.read(file);

        List<ClipPlan.Range> cuts = new ArrayList<ClipPlan.Range>();
        cuts.add(new ClipPlan.Range(9_300_000L, 15_700_000L));
        cuts.add(new ClipPlan.Range(26_100_000L, 29_900_000L));
        ClipPlan.Plan plan = ClipPlan.build(source.durationUs, source.syncUs, source.framesUs,
                1_400_000L, 45 * SECOND, cuts);
        ClipEditor.export(file, source, plan, null);

        for (int s = 0; s < plan.segments.size(); s++) {
            long startUs = plan.segments.get(s).outputStartUs;
            boolean found = false;
            for (MediaMuxer.Written w : MediaMuxer.LAST.of(0)) {
                if (w.presentationTimeUs == startUs) {
                    check((w.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0,
                            "segment " + s + " begins on a sample that is not a key frame, "
                                    + "which MPEG4Writer would silently discard");
                    found = true;
                    break;
                }
            }
            check(found, "segment " + s + " has no sample at its own output start " + startUs);
        }
    }

    /**
     * Across a sweep, the muxer is never handed anything it would block on.
     *
     * The stub throws WouldHaveHung where the platform would wait forever. Anything reaching it
     * is a real defect, because the executor is supposed to refuse first.
     */
    private static void theMuxerIsNeverAskedToDoSomethingThatHangs() throws Exception {
        long duration = 120 * SECOND;
        File file = freshFile("sweep.mp4");
        install(videoTrack(duration, 60, 200_000), audioTrack(duration));
        ClipEditor.Source source = ClipEditor.read(file);

        int exported = 0;
        for (long in = 0; in <= 12 * SECOND; in += 1_700_000L) {
            for (long out = in + 8 * SECOND; out <= 70 * SECOND; out += 11_300_000L) {
                for (int c = 0; c < 3; c++) {
                    List<ClipPlan.Range> cuts = new ArrayList<ClipPlan.Range>();
                    if (c >= 1) cuts.add(new ClipPlan.Range(in + 2_100_000L, in + 5_400_000L));
                    if (c >= 2) cuts.add(new ClipPlan.Range(in + 6_050_000L, in + 7_010_000L));
                    ClipPlan.Plan plan = ClipPlan.build(source.durationUs, source.syncUs,
                            source.framesUs, in, out, cuts);
                    if (!plan.isUsable()) continue;

                    MediaMuxer.reset();
                    MediaExtractor.NEXT_FILE = new ArrayList<MediaExtractor.Track>(
                            Arrays.asList(videoTrack(duration, 60, 200_000),
                                    audioTrack(duration)));
                    try {
                        ClipEditor.export(file, source, plan, null);
                        exported++;
                    } catch (MediaMuxer.WouldHaveHung hang) {
                        check(false, "the executor handed the muxer something that blocks: "
                                + hang.getMessage());
                    } catch (IOException e) {
                        check(false, "a usable plan was refused: " + e.getMessage());
                    }
                    check(MediaMuxer.LAST.stopped, "the muxer was not stopped");
                }
            }
        }
        check(exported > 40, "the sweep only exported " + exported + " clips, so it is not "
                + "covering much");
    }

    /** A sample larger than the buffer is explained, not re-thrown with a null message. */
    private static void aTooSmallBufferIsExplained() throws Exception {
        long duration = 10 * SECOND;
        // Declare a tiny max input size AND make a sample bigger than the generous fallback, so
        // the executor's own sizing cannot save it. This is the platform's null-message
        // IllegalArgumentException, which reads as a caller bug unless it is caught and named.
        MediaExtractor.Track video = videoTrack(duration, 60, 64);
        video.sizes[30] = 4_000_000;
        install(video);
        File file = freshFile("bigsample.mp4");
        ClipEditor.Source source = ClipEditor.read(file);
        ClipPlan.Plan plan = ClipPlan.trim(source.durationUs, source.syncUs, source.framesUs,
                0L, 8 * SECOND);
        try {
            ClipEditor.export(file, source, plan, null);
            check(false, "a sample too large for the buffer should not export silently");
        } catch (IOException e) {
            check(e.getMessage() != null && e.getMessage().contains("buffer"),
                    "the too-small-buffer failure was not explained: " + e.getMessage());
        } catch (IllegalArgumentException raw) {
            check(false, "the platform's null-message IllegalArgumentException escaped, which "
                    + "reads as a bug in the caller rather than a sample that will not fit");
        }

        // The ordinary case still sizes itself from the declared maximum.
        install(videoTrack(duration, 60, 900_000));
        File ok = freshFile("normal.mp4");
        ClipEditor.Source fine = ClipEditor.read(ok);
        ClipEditor.export(ok, fine, ClipPlan.trim(fine.durationUs, fine.syncUs, fine.framesUs,
                0L, 8 * SECOND), null);
        check(MediaMuxer.LAST.stopped, "a normal clip failed to export");
    }

    /**
     * Each platform refusal, one at a time, leaves no half file behind.
     *
     * A stub that can only succeed tests nothing about the guard around it (§16.16).
     */
    private static void platformRefusalsLeaveNothingBehind() throws Exception {
        long duration = 20 * SECOND;

        // setDataSource refuses.
        install(videoTrack(duration, 60, 200_000));
        File file = freshFile("refuse.mp4");
        ClipEditor.Source source = ClipEditor.read(file);
        ClipPlan.Plan plan = ClipPlan.trim(source.durationUs, source.syncUs, source.framesUs,
                0L, 10 * SECOND);

        install(videoTrack(duration, 60, 200_000));
        MediaExtractor.DATA_SOURCE_THROWS = true;
        expectFailure(file, source, plan, "setDataSource refusing");
        MediaExtractor.DATA_SOURCE_THROWS = false;

        // addTrack refuses.
        install(videoTrack(duration, 60, 200_000));
        MediaMuxer.ADD_TRACK_THROWS = true;
        expectFailure(file, source, plan, "addTrack refusing");
        MediaMuxer.ADD_TRACK_THROWS = false;

        // stop refuses.
        install(videoTrack(duration, 60, 200_000));
        MediaMuxer.STOP_THROWS = true;
        expectFailure(file, source, plan, "stop refusing");
        MediaMuxer.STOP_THROWS = false;

        // A sidecar that will not be written must NOT fail the export: the clip plays either way.
        install(videoTrack(duration, 60, 200_000));
        RecordingInfo.WRITE_THROWS = true;
        File out = ClipEditor.export(file, source, plan, null);
        check(out.exists(), "a failed sidecar should not have failed the export");
        RecordingInfo.WRITE_THROWS = false;

        // And a clip with no sidecar at all is fine.
        install(videoTrack(duration, 60, 200_000));
        RecordingInfo.PRESENT = false;
        File out2 = ClipEditor.export(file, source, plan, null);
        check(out2.exists(), "a clip with no sidecar failed to export");
        RecordingInfo.PRESENT = true;
    }

    private static void expectFailure(File file, ClipEditor.Source source, ClipPlan.Plan plan,
                                      String what) {
        int before = countFiles();
        try {
            ClipEditor.export(file, source, plan, null);
            check(false, what + " should have failed the export");
        } catch (Throwable expected) {
            check(true, "");
        }
        int after = countFiles();
        // The muxer stub creates its output file at construction, exactly as the real one does,
        // so "nothing left behind" means the count is back where it started.
        check(after <= before, what + " left " + (after - before) + " file(s) behind");
        for (File f : sTempDir.listFiles()) {
            check(!f.getName().endsWith(".part"),
                    what + " left a .part file: " + f.getName());
        }
    }

    private static int countFiles() {
        File[] files = sTempDir.listFiles();
        return files == null ? 0 : files.length;
    }

    /** The source file is never written to, renamed or removed. */
    private static void theOriginalIsNeverTouched() throws Exception {
        long duration = 20 * SECOND;
        install(videoTrack(duration, 60, 200_000));
        File file = freshFile("original.mp4");
        long length = file.length();
        long modified = file.lastModified();

        ClipEditor.Source source = ClipEditor.read(file);
        ClipPlan.Plan plan = ClipPlan.trim(source.durationUs, source.syncUs, source.framesUs,
                2 * SECOND, 12 * SECOND);
        File out = ClipEditor.export(file, source, plan, null);

        check(file.exists(), "the original was removed");
        check(file.length() == length, "the original's length changed");
        check(file.lastModified() == modified, "the original was written to");
        check(!out.equals(file), "the edit was written over the original");
        check(out.getName().contains("_edit"), "the edit is not named as one: " + out.getName());

        // A second edit does not overwrite the first.
        install(videoTrack(duration, 60, 200_000));
        File again = ClipEditor.export(file, source, plan, null);
        check(!again.equals(out), "a second edit overwrote the first");
        check(out.exists(), "the first edit was removed by the second");
    }

    /** A clip whose frames are stored out of order is refused with a reason. */
    private static void bFramesAreRefusedRatherThanMangled() throws Exception {
        long duration = 20 * SECOND;
        MediaExtractor.Track video = videoTrack(duration, 60, 200_000);
        long[] shuffled = new long[video.timesUs.length];
        System.arraycopy(video.timesUs, 0, shuffled, 0, shuffled.length);
        // A classic IPBB pattern: the P frame is stored before the B frames it precedes.
        for (int i = 3; i + 2 < shuffled.length; i += 4) {
            long swap = shuffled[i];
            shuffled[i] = shuffled[i + 2];
            shuffled[i + 2] = swap;
        }
        install(new MediaExtractor.Track(video.mime, shuffled, video.sync, video.sizes,
                video.extraFlags, video.format));
        File file = freshFile("bframes.mp4");
        ClipEditor.Source source = ClipEditor.read(file);
        check(!source.framesInOrder, "reordered timestamps were not noticed");
        check(!source.isUsable(), "a reordered clip reported itself usable");

        ClipPlan.Plan plan = ClipPlan.trim(30 * SECOND, new long[]{0L, 2 * SECOND},
                new long[]{0L, 2 * SECOND}, 0L, 2 * SECOND);
        try {
            ClipEditor.export(file, source, plan, null);
            check(false, "a reordered clip should be refused rather than mangled");
        } catch (MediaMuxer.WouldHaveHung hang) {
            check(false, "the reordering reached the muxer and would have hung the app");
        } catch (IOException e) {
            check(e.getMessage() != null && e.getMessage().contains("order"),
                    "the refusal did not say why: " + e.getMessage());
        }
    }

    /** A rotated source keeps its rotation, and an illegal one is dropped rather than thrown. */
    private static void rotationIsCarriedAcrossAndGuarded() throws Exception {
        long duration = 20 * SECOND;
        for (int degrees : new int[]{0, 90, 180, 270, 45, -90, 360}) {
            MediaExtractor.Track video = videoTrack(duration, 60, 200_000);
            video.format.setInteger("rotation-degrees", degrees);
            install(video);
            File file = freshFile("rot" + (degrees < 0 ? "neg" : "") + Math.abs(degrees) + ".mp4");
            ClipEditor.Source source = ClipEditor.read(file);
            ClipPlan.Plan plan = ClipPlan.trim(source.durationUs, source.syncUs, source.framesUs,
                    0L, 10 * SECOND);
            try {
                ClipEditor.export(file, source, plan, null);
            } catch (IllegalArgumentException e) {
                check(false, "an odd rotation of " + degrees + " reached setOrientationHint, "
                        + "which throws");
            }
            boolean legal = degrees == 90 || degrees == 180 || degrees == 270;
            check(MediaMuxer.LAST.orientationHint == (legal ? degrees : -1),
                    "rotation " + degrees + " produced a hint of "
                            + MediaMuxer.LAST.orientationHint);
        }
    }

    /**
     * The executor's own guards fire, on plans {@link ClipPlan} would never produce.
     *
     * This is the half {@code clipsim} cannot reach. Those guards exist because the platform
     * BLOCKS rather than throwing, so the only way to know they work is to hand the executor the
     * bad plan directly. The constructors are package private and this harness shares the
     * package, which is what makes that possible.
     */
    private static void theExecutorsOwnGuardsFire() throws Exception {
        long duration = 60 * SECOND;

        // (1) A segment that does not begin on a sync frame. MPEG4Writer would silently eat the
        // leading frames and put the video a whole GOP behind the sound.
        install(videoTrack(duration, 60, 200_000));
        File file = freshFile("guards.mp4");
        ClipEditor.Source source = ClipEditor.read(file);

        List<ClipPlan.Segment> offKey = new ArrayList<ClipPlan.Segment>();
        offKey.add(new ClipPlan.Segment(5 * SECOND + SECOND / 60, 10 * SECOND, 0L));
        ClipPlan.Plan offKeyPlan = new ClipPlan.Plan(offKey, 5 * SECOND - SECOND / 60,
                ClipPlan.Problem.NONE, 0L);
        install(videoTrack(duration, 60, 200_000));
        try {
            ClipEditor.export(file, source, offKeyPlan, null);
            check(false, "a segment starting off a sync frame was accepted, which puts the "
                    + "video up to a GOP behind the sound with nothing logged");
        } catch (MediaMuxer.WouldHaveHung hang) {
            check(false, "the off-key segment reached the muxer");
        } catch (IOException e) {
            check(e.getMessage() != null && e.getMessage().contains("key frame"),
                    "the refusal did not name the cause: " + e.getMessage());
        }

        // (2) Two segments whose output times overlap. The real muxer BLOCKS FOREVER on this.
        List<ClipPlan.Segment> backwards = new ArrayList<ClipPlan.Segment>();
        backwards.add(new ClipPlan.Segment(0L, 6 * SECOND, 0L));
        backwards.add(new ClipPlan.Segment(10 * SECOND, 16 * SECOND, 2 * SECOND));
        ClipPlan.Plan backwardsPlan = new ClipPlan.Plan(backwards, 8 * SECOND,
                ClipPlan.Problem.NONE, 0L);
        install(videoTrack(duration, 60, 200_000));
        try {
            ClipEditor.export(file, source, backwardsPlan, null);
            check(false, "overlapping output times were accepted");
        } catch (MediaMuxer.WouldHaveHung hang) {
            check(false, "a backwards timestamp reached the muxer, which on API 21 blocks "
                    + "forever with no exception and no log");
        } catch (IOException e) {
            check(e.getMessage() != null && e.getMessage().contains("backwards"),
                    "the refusal did not name the cause: " + e.getMessage());
        }

        // (3) A negative output time. MPEG4Writer marks the track malformed and stops it, after
        // which the next write to that track blocks.
        List<ClipPlan.Segment> negative = new ArrayList<ClipPlan.Segment>();
        negative.add(new ClipPlan.Segment(10 * SECOND, 16 * SECOND, -4 * SECOND));
        ClipPlan.Plan negativePlan = new ClipPlan.Plan(negative, 6 * SECOND,
                ClipPlan.Problem.NONE, 0L);
        install(videoTrack(duration, 60, 200_000));
        try {
            ClipEditor.export(file, source, negativePlan, null);
            check(false, "a negative output time was accepted");
        } catch (MediaMuxer.WouldHaveHung hang) {
            check(false, "a negative timestamp reached the muxer");
        } catch (IOException e) {
            check(true, "");
        }

        // (4) A window with video but no sound in it. stop() throws for this, with a message
        // nobody could act on, so the executor says what is actually wrong.
        long step = 10 * SECOND;
        int audioCount = (int) (duration / step);
        long[] times = new long[audioCount];
        boolean[] sync = new boolean[audioCount];
        int[] sizes = new int[audioCount];
        int[] extra = new int[audioCount];
        for (int i = 0; i < audioCount; i++) {
            times[i] = i * step;
            sync[i] = true;
            sizes[i] = 384;
        }
        MediaExtractor.Track sparseAudio = new MediaExtractor.Track("audio/mp4a-latm", times,
                sync, sizes, extra, audioFormat(duration));
        install(videoTrack(duration, 60, 200_000), sparseAudio);
        File quiet = freshFile("quiet.mp4");
        ClipEditor.Source quietSource = ClipEditor.read(quiet);
        ClipPlan.Plan silent = ClipPlan.trim(quietSource.durationUs, quietSource.syncUs,
                quietSource.framesUs, 2 * SECOND, 4 * SECOND);
        check(silent.isUsable(), "the fixture's plan should be usable");
        install(videoTrack(duration, 60, 200_000), sparseAudio);
        try {
            ClipEditor.export(quiet, quietSource, silent, null);
            check(false, "a window with no sound in it should be reported");
        } catch (IOException e) {
            check(e.getMessage() != null && e.getMessage().contains("too short"),
                    "a soundless window was not explained: " + e.getMessage());
        }
    }

    /**
     * A track that declares no maximum sample size still gets a buffer big enough.
     *
     * The platform's own fallback estimate for 1080p AVC is about 1.70 MB, so anything under
     * that is a guess below the thing it is guessing at, and the symptom is an
     * IllegalArgumentException with a null message on whichever clip first has a fat key frame.
     */
    private static void anUndeclaredBufferIsStillBigEnough() throws Exception {
        long duration = 20 * SECOND;
        MediaExtractor.Track video = videoTrack(duration, 60, 200_000);
        video.format.getMap().remove(MediaFormat.KEY_MAX_INPUT_SIZE);
        // Between a 1 MB guess and the platform's own 1080p estimate.
        video.sizes[120] = 1_500_000;
        install(new MediaExtractor.Track(video.mime, video.timesUs, video.sync, video.sizes,
                video.extraFlags, video.format));
        File file = freshFile("undeclared.mp4");
        ClipEditor.Source source = ClipEditor.read(file);
        ClipPlan.Plan plan = ClipPlan.trim(source.durationUs, source.syncUs, source.framesUs,
                0L, 10 * SECOND);
        try {
            ClipEditor.export(file, source, plan, null);
        } catch (IOException e) {
            check(false, "a 1.5 MB key frame on a track that declares no maximum did not fit: "
                    + e.getMessage());
        }
        check(MediaMuxer.LAST.stopped, "the export did not finish");
    }

    /* ------------------------------------------------------------------ plumbing */

    /** The audio fixture's actual timestamps. */
    private static long[] audioTimes(long durationUs, long step) {
        int count = (int) (durationUs / step);
        long[] times = new long[count];
        for (int i = 0; i < count; i++) times[i] = i * step;
        return times;
    }

    private static long frameAtOrBefore(long[] framesUs, long us) {
        long best = framesUs[0];
        for (int i = 0; i < framesUs.length; i++) {
            if (framesUs[i] <= us) best = framesUs[i]; else break;
        }
        return best;
    }

    /** The source time an output time came from, or -1. */
    private static long sourceTimeOf(ClipPlan.Plan plan, long outputUs) {
        for (int i = 0; i < plan.segments.size(); i++) {
            ClipPlan.Segment segment = plan.segments.get(i);
            long candidate = outputUs - segment.offsetUs();
            if (candidate >= segment.sourceStartUs && candidate < segment.sourceEndUs) {
                return candidate;
            }
        }
        return -1L;
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
