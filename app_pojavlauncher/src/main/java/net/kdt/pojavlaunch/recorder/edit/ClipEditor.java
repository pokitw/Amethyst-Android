package net.kdt.pojavlaunch.recorder.edit;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.recorder.RecordingInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Carries out a {@link ClipPlan.Plan} by copying samples from one MP4 into another.
 *
 * <b>This class contains no decisions.</b> Every question about which samples survive and what
 * their new timestamps are is answered by {@link ClipPlan}, which is plain Java and driven by
 * {@code scripts/clipsim}. What is left here is the part that can only be exercised on a device:
 * walking a {@code MediaExtractor} and feeding a {@code MediaMuxer}. Keeping the split that sharp
 * is the whole reason the risky half is verifiable at all (§16.25).
 *
 * <p>Nothing is decoded or re-encoded. The compressed samples are copied as they are, so an edit
 * is as fast as the storage and cannot lose a single bit of quality. The cost is a rule about
 * where a segment may begin, which {@link ClipPlan} documents and the UI shows.
 *
 * <h2>Why this file is so defensive</h2>
 *
 * {@code MediaMuxer} fails quietly in three different ways, all of them worse than an exception,
 * and all three are reachable from an arithmetic slip in a plan:
 *
 * <ul>
 *   <li><b>A timestamp that goes backwards within a track hangs the thread.</b> It does not throw.
 *       {@code MediaAdapter::pushBuffer} waits on a condition the writer thread will never signal
 *       once that track has stopped, so the export simply never returns. Media3 guards the same
 *       call for the same reason below API 25, and this app's {@code minSdk} is 21.</li>
 *   <li><b>A negative timestamp truncates the track silently.</b> MPEG4Writer marks the track
 *       malformed, stops its source and logs nothing above a warning, after which the next write
 *       to it hangs as above. "The export froze" and "the clip came out short" are one bug.</li>
 *   <li><b>A segment starting off a sync frame is not refused.</b> MPEG4Writer discards leading
 *       non key video samples until the first real one, so the video simply starts later than the
 *       audio and the whole clip is out of lip sync by up to one GOP, logged at debug only.</li>
 * </ul>
 *
 * Each of those is checked here before the call that would trigger it. They should all be
 * impossible given a plan {@code clipsim} has passed, which is exactly why they are cheap: they
 * never fire, and if one ever does it reports a bug instead of hanging someone's phone.
 */
public final class ClipEditor {

    private static final String TAG = "ClipEditor";

    /**
     * Buffer floors when a track will not declare its own maximum sample size.
     *
     * A buffer that is too small does <b>not</b> short read: the native layer returns -ENOMEM and
     * the JNI turns it into an {@code IllegalArgumentException} with a null message, which reads
     * like a caller bug. The platform's own fallback estimate for 1080p AVC is about 1.70 MB, so
     * a 1 MB guess is below the thing it is guessing at.
     */
    private static final int VIDEO_FALLBACK_BYTES = 1835008;   // 1.75 MB
    private static final int AUDIO_FALLBACK_BYTES = 262144;    // 256 KB

    /**
     * How much the fraction must advance before the caller is told again.
     *
     * The copy loop reaches this once per sample, and a forty minute clip holds a quarter of a
     * million of them across its two tracks. A caller that hops to the main thread to move a
     * progress bar would therefore post a quarter of a million runnables during one export,
     * which is not a slow progress bar, it is an unusable app. Throttling here rather than in
     * each caller is the right place: the spam originates here, and a caller cannot throttle
     * what it is not told about cheaply.
     *
     * 0.5% is about two hundred updates over a whole export, which is smoother than any bar
     * needs and nothing next to the work being done.
     */
    private static final float PROGRESS_STEP = 0.005f;

    private ClipEditor() {}

    /** Called on the worker thread as the copy proceeds. */
    public interface Progress {
        /** @param fraction 0 to 1, best effort */
        void onProgress(float fraction);
    }

    /** What a clip is, as far as the editor needs to know. */
    public static final class Source {
        public final long durationUs;
        /** The video track's sync frame timestamps, ascending. Where a segment may begin. */
        public final long[] syncUs;
        /**
         * Every video frame's timestamp, ascending.
         *
         * Collected because a segment's end should land on a frame even though it is not required
         * to: see {@link ClipPlan}'s class comment for the sub frame error that removes. The walk
         * that finds the sync frames is reading every timestamp anyway, so this is free.
         */
        public final long[] framesUs;
        public final int width;
        public final int height;
        public final boolean hasAudio;
        /**
         * True when the video track's timestamps ascend in storage order.
         *
         * <b>A stream copy can reorder spans of samples, not samples.</b> With B frames, storage
         * order is decode order and presentation timestamps jump about inside it, so selecting a
         * span by timestamp and writing it out in storage order feeds the muxer a timestamp that
         * goes backwards, which hangs it. Even where it did not, MediaMuxer discards the original
         * decode timeline and rebuilds it from the presentation one, so the copy would not be
         * lossless in the timing either. This recorder's encoder is a realtime surface encoder
         * and does not emit them, so the honest thing is to notice rather than to assume.
         */
        public final boolean framesInOrder;

        Source(long durationUs, long[] syncUs, long[] framesUs, int width, int height,
               boolean hasAudio, boolean framesInOrder) {
            this.durationUs = durationUs;
            this.syncUs = syncUs;
            this.framesUs = framesUs;
            this.width = width;
            this.height = height;
            this.hasAudio = hasAudio;
            this.framesInOrder = framesInOrder;
        }

        public boolean isUsable() {
            return durationUs > 0L && syncUs.length > 0 && framesInOrder;
        }
    }

    /**
     * Read the duration, the size, every frame and every sync frame of a clip.
     *
     * <b>Found by walking the video track</b>, reading only sample metadata and never the sample
     * data itself. The alternative, repeated {@code seekTo(..., SEEK_TO_NEXT_SYNC)}, needs a probe
     * per frame to find the gaps and gives no way to know when to stop; walking is one pass and is
     * exact. On a forty minute clip that is a few hundred thousand calls to {@code advance()} with
     * none of the payload read, which is seconds rather than minutes, and it happens once when the
     * editor opens.
     *
     * <p>Called off the main thread.
     */
    public static Source read(@NonNull File file) throws IOException {
        // The stream first: if opening the file throws, an extractor constructed before it has
        // already allocated its native object and nothing will ever release it.
        FileInputStream stream = new FileInputStream(file);
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(stream.getFD());
            int videoTrack = findTrack(extractor, "video/");
            if (videoTrack < 0) throw new IOException("no video track in " + file.getName());

            MediaFormat format = extractor.getTrackFormat(videoTrack);
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) : 0L;
            int width = format.containsKey(MediaFormat.KEY_WIDTH)
                    ? format.getInteger(MediaFormat.KEY_WIDTH) : 0;
            int height = format.containsKey(MediaFormat.KEY_HEIGHT)
                    ? format.getInteger(MediaFormat.KEY_HEIGHT) : 0;
            boolean hasAudio = findTrack(extractor, "audio/") >= 0;

            extractor.selectTrack(videoTrack);
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            List<Long> syncs = new ArrayList<Long>();
            List<Long> frames = new ArrayList<Long>();
            long lastTime = -1L;
            boolean inOrder = true;
            while (true) {
                long time = extractor.getSampleTime();
                if (time < 0L) break;
                frames.add(time);
                if ((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    syncs.add(time);
                }
                if (time < lastTime) inOrder = false;
                if (time > lastTime) lastTime = time;
                if (!extractor.advance()) break;
            }
            // A container can declare no duration. The last timestamp is a better answer than
            // zero, which would make every selection empty.
            if (durationUs <= 0L && lastTime > 0L) durationUs = lastTime;

            return new Source(durationUs, toArray(syncs), toArray(frames), width, height,
                    hasAudio, inOrder);
        } finally {
            extractor.release();
            closeQuietly(stream);
        }
    }

    private static long[] toArray(List<Long> list) {
        long[] out = new long[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    /**
     * Copy the planned segments of a clip into a new file beside it.
     *
     * <b>The original is never touched.</b> The output is written to a {@code .part} file and
     * renamed only once every sample is in it, so an interrupted export leaves something obviously
     * incomplete rather than a short clip that looks finished. That is the rule
     * {@code GalleryExport} already follows for a video it publishes.
     *
     * <p>Called off the main thread, and it must be: see the class comment on hanging.
     *
     * @return the finished file
     */
    public static File export(@NonNull File file, @NonNull Source source,
                              @NonNull ClipPlan.Plan plan,
                              @Nullable Progress progress) throws IOException {
        if (!plan.isUsable()) {
            throw new IOException("refusing to export an unusable plan: " + plan.problem);
        }
        if (!source.framesInOrder) {
            throw new IOException("this clip stores its frames out of order and cannot be "
                    + "edited without re-encoding");
        }
        File destination = freeName(file);
        File partial = new File(destination.getAbsolutePath() + ".part");
        if (partial.exists() && !partial.delete()) {
            throw new IOException("could not clear " + partial.getName());
        }

        FileInputStream input = new FileInputStream(file);
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean finished = false;
        try {
            extractor.setDataSource(input.getFD());
            muxer = new MediaMuxer(partial.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // Every track has to be added before start(). The extractor's own format is handed
            // over whole, because it carries the codec specific data (the AVC SPS and PPS, the
            // AAC config) and a file without that in its avcC box decodes nowhere. Rebuilding
            // the format with createVideoFormat is the way to lose it.
            int trackCount = extractor.getTrackCount();
            int[] outputIndex = new int[trackCount];
            int[] bufferSize = new int[trackCount];
            boolean[] isVideo = new boolean[trackCount];
            for (int i = 0; i < trackCount; i++) outputIndex[i] = -1;
            int rotation = 0;
            int added = 0;
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.containsKey(MediaFormat.KEY_MIME)
                        ? format.getString(MediaFormat.KEY_MIME) : null;
                if (mime == null) continue;
                boolean video = mime.startsWith("video/");
                if (!video && !mime.startsWith("audio/")) continue;
                isVideo[i] = video;
                outputIndex[i] = muxer.addTrack(format);
                added++;
                int declared = format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)
                        ? format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 0;
                bufferSize[i] = Math.max(declared,
                        video ? VIDEO_FALLBACK_BYTES : AUDIO_FALLBACK_BYTES);
                // Spelled out rather than MediaFormat.KEY_ROTATION, which is an API 23 symbol.
                // It is a compile time String constant so it would inline harmlessly, but a
                // literal cannot be mistaken for a call this app is too old to make.
                if (video && format.containsKey("rotation-degrees")) {
                    rotation = format.getInteger("rotation-degrees");
                }
            }
            if (added == 0) throw new IOException("nothing in " + file.getName() + " to copy");
            // addTrack ignores KEY_ROTATION, so a rotated source comes out sideways unless the
            // hint is carried over by hand. Guarded to the four legal values because anything
            // else throws, and this one came out of a file rather than out of our own recorder.
            if (rotation == 90 || rotation == 180 || rotation == 270) {
                muxer.setOrientationHint(rotation);
            }
            muxer.start();

            // Progress is output time summed over every track, so a clip with sound reports half
            // way when the video is done rather than reaching 100% and then sitting there.
            long totalUs = Math.max(1L, plan.outputDurationUs) * added;
            long doneUs = 0L;
            Progress sink = throttled(progress);
            // One track at a time. MediaMuxer only requires timestamps to increase within a
            // track, not across them, so this is simpler than interleaving and produces the same
            // file.
            for (int track = 0; track < trackCount; track++) {
                if (outputIndex[track] < 0) continue;
                long written = copyTrack(extractor, muxer, track, outputIndex[track],
                        bufferSize[track], isVideo[track], plan, sink, doneUs, totalUs);
                if (written == 0L) {
                    // stop() throws when a track received nothing, and the message it throws is
                    // not one anybody could act on. This can only happen for a selection shorter
                    // than one audio frame, which the UI's minimum selection already prevents.
                    throw new IOException("the selection is too short to hold any "
                            + (isVideo[track] ? "video" : "sound"));
                }
                doneUs += Math.max(1L, plan.outputDurationUs);
            }
            // Said explicitly, because the running figure can never reach it: a track's last
            // sample sits at a timestamp BEFORE the segment's end (that is what makes it the
            // last one), so the fraction tops out a frame short and a bar would stop at 99%
            // and sit there through the mux. The work really is done here.
            if (sink != null) sink.onProgress(1f);
            muxer.stop();
            finished = true;
        } finally {
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Throwable ignored) {
                    // A muxer that will not release has already written what it is going to.
                }
            }
            extractor.release();
            closeQuietly(input);
            if (!finished && partial.exists() && !partial.delete()) {
                Log.w(TAG, "left a partial export at " + partial.getName());
            }
        }

        if (!partial.renameTo(destination)) {
            // A rename can fail across a storage boundary even when both paths are writable.
            // Guarded, because a copy that dies half way leaves a short file under the final
            // name, and a short .mp4 is one both galleries list as a finished clip. The whole
            // point of the .part file is that an interrupted export leaves nothing that looks
            // complete, and an unguarded fallback here gives that away at the last step.
            try {
                copyFile(partial, destination);
            } catch (IOException e) {
                if (destination.exists() && !destination.delete()) {
                    Log.w(TAG, "left a truncated export at " + destination.getName());
                }
                throw e;
            }
            if (!partial.delete()) Log.w(TAG, "could not remove " + partial.getName());
        }
        carrySidecar(file, destination);
        return destination;
    }

    /**
     * Wrap a caller's progress so it hears about meaningful movement and not about every sample.
     *
     * Completion is always passed on whatever the step says, so a caller can rely on seeing 1.
     */
    @Nullable
    private static Progress throttled(@Nullable final Progress sink) {
        if (sink == null) return null;
        return new Progress() {
            private float mLast = -1f;

            @Override
            public void onProgress(float fraction) {
                if (fraction < 1f && fraction - mLast < PROGRESS_STEP) return;
                mLast = fraction;
                sink.onProgress(fraction);
            }
        };
    }

    /** Copy one track's planned samples. Returns how many samples it wrote. */
    private static long copyTrack(MediaExtractor extractor, MediaMuxer muxer, int track,
                                  int outputTrack, int bufferBytes, boolean video,
                                  ClipPlan.Plan plan, @Nullable Progress progress,
                                  long baseUs, long totalUs) throws IOException {
        extractor.selectTrack(track);
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferBytes);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long written = 0L;
        long lastWrittenUs = Long.MIN_VALUE;
        try {
            for (int s = 0; s < plan.segments.size(); s++) {
                ClipPlan.Segment segment = plan.segments.get(s);
                // Seek to the sync frame at or before the segment start, then walk forward. For
                // video the start IS a sync frame so this lands exactly; for audio it lands
                // nearby and the skip below trims the difference.
                extractor.seekTo(segment.sourceStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                boolean firstOfSegment = true;
                while (true) {
                    long time = extractor.getSampleTime();
                    if (time < 0L) break;                      // end of track
                    if (time >= segment.sourceEndUs) break;
                    if (time < segment.sourceStartUs) {
                        if (!extractor.advance()) break;
                        continue;
                    }
                    // The segment's own offset, not plan.mapToOutput. They are the same
                    // arithmetic and we are already inside the segment, but mapToOutput answers
                    // -1 for "no segment holds this", and a plan with a negative output time
                    // produces negative answers that are indistinguishable from that sentinel.
                    // Reading one as the other silently drops the first part of a segment and
                    // writes the rest, which is the quietest possible way to corrupt an edit.
                    long mapped = time + segment.offsetUs();

                    int flags = sampleFlags(extractor.getSampleFlags());
                    if (video && firstOfSegment
                            && (flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) == 0) {
                        // MPEG4Writer would silently eat every frame up to the next real one,
                        // putting the video up to a whole GOP behind the sound with nothing said.
                        throw new IOException("segment " + s + " does not begin at a key frame");
                    }
                    if (mapped < 0L || mapped < lastWrittenUs) {
                        // Both of these hang writeSampleData rather than throwing, and a plan
                        // out of clipsim can produce neither: output time accumulates from zero
                        // and segments are ordered and disjoint. The guard is here because the
                        // cost of being wrong is a frozen phone and the cost of asking is one
                        // comparison per sample.
                        throw new IOException("refusing to write a timestamp that goes backwards "
                                + "(" + mapped + " after " + lastWrittenUs + ")");
                    }

                    buffer.clear();
                    int size;
                    try {
                        size = extractor.readSampleData(buffer, 0);
                    } catch (IllegalArgumentException e) {
                        // The platform throws this with a null message when the buffer is too
                        // small, which is indistinguishable from a caller bug without saying so.
                        throw new IOException("a sample is larger than the "
                                + bufferBytes + " byte buffer this track declared", e);
                    }
                    if (size < 0) break;
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = mapped;
                    info.flags = flags;
                    muxer.writeSampleData(outputTrack, buffer, info);
                    lastWrittenUs = mapped;
                    written++;
                    firstOfSegment = false;

                    if (progress != null) {
                        progress.onProgress(Math.min(1f,
                                (float) (baseUs + mapped) / (float) totalUs));
                    }
                    if (!extractor.advance()) break;
                }
            }
        } finally {
            extractor.unselectTrack(track);
        }
        return written;
    }

    /**
     * Translate extractor sample flags into muxer buffer flags.
     *
     * <b>Not a cast.</b> Only the sync bit means the same thing in both sets:
     * {@code SAMPLE_FLAG_ENCRYPTED} is 2 and so is {@code BUFFER_FLAG_CODEC_CONFIG},
     * {@code SAMPLE_FLAG_PARTIAL_FRAME} is 4 and so is {@code BUFFER_FLAG_END_OF_STREAM}. Passing
     * the extractor's flags straight through therefore tells the muxer "this sample is codec
     * configuration" or "this is the end of the stream" on any source that sets either, which is
     * a corrupt file rather than an error.
     */
    private static int sampleFlags(int extractorFlags) {
        int flags = 0;
        if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
        }
        return flags;
    }

    private static int findTrack(MediaExtractor extractor, String mimePrefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            if (!format.containsKey(MediaFormat.KEY_MIME)) continue;
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(mimePrefix)) return i;
        }
        return -1;
    }

    /**
     * A name next to the original that is not taken.
     *
     * Never the original: an editor that overwrote its source would make a mistake unrecoverable,
     * and a recording is the one thing in this app a player cannot make again.
     */
    static File freeName(@NonNull File source) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : ".mp4";
        File parent = source.getParentFile();
        for (int n = 1; n < 1000; n++) {
            String candidate = n == 1 ? base + "_edit" + extension
                    : base + "_edit" + n + extension;
            File file = new File(parent, candidate);
            if (!file.exists() && !new File(file.getAbsolutePath() + ".part").exists()) {
                return file;
            }
        }
        return new File(parent, base + "_edit" + System.currentTimeMillis() + extension);
    }

    /**
     * Give the edit the original's sidecar, since the resolution and frame rate are unchanged.
     *
     * A failure here is not worth failing the export for: the clip plays either way, and both
     * galleries read the file rather than the sidecar.
     */
    private static void carrySidecar(File source, File destination) {
        try {
            RecordingInfo info = RecordingInfo.read(source);
            if (info != null) info.write(destination);
        } catch (Throwable t) {
            Log.w(TAG, "could not carry the sidecar across", t);
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        try {
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = in.read(chunk)) > 0) out.write(chunk, 0, read);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static void closeQuietly(@Nullable java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
