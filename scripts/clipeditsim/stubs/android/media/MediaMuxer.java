package android.media;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A MediaMuxer that records what it was given and refuses the way the real one refuses.
 *
 * Three platform behaviours are modelled on purpose, because each is a silent failure in the
 * field and a stub without them would let the executor's guards pass untested (§16.16):
 *
 * <ul>
 *   <li><b>A timestamp going backwards within a track hangs.</b> It cannot hang here or the
 *       harness would never finish, so it throws {@link WouldHaveHung} instead. The executor is
 *       supposed to refuse before reaching this, so a harness run that sees this exception has
 *       found a real bug rather than a modelling artefact.</li>
 *   <li><b>stop() throws when a track received no samples.</b></li>
 *   <li><b>The state machine is real:</b> addTrack after start, writeSampleData before start and
 *       anything after release all throw IllegalStateException.</li>
 * </ul>
 */
public final class MediaMuxer {

    /** Thrown where the real platform would block forever. */
    public static final class WouldHaveHung extends RuntimeException {
        public WouldHaveHung(String message) {
            super(message);
        }
    }

    public static final class OutputFormat {
        public static final int MUXER_OUTPUT_MPEG_4 = 0;
        public static final int MUXER_OUTPUT_WEBM = 1;
    }

    /** One sample as the muxer received it. */
    public static final class Written {
        public final int track;
        public final long presentationTimeUs;
        public final int flags;
        public final int size;

        Written(int track, long presentationTimeUs, int flags, int size) {
            this.track = track;
            this.presentationTimeUs = presentationTimeUs;
            this.flags = flags;
            this.size = size;
        }
    }

    /** The muxer the harness most recently constructed. */
    public static MediaMuxer LAST = null;
    /** Set to make addTrack refuse, as an unsupported format does. */
    public static boolean ADD_TRACK_THROWS = false;
    /** Set to make stop() refuse for a reason of its own. */
    public static boolean STOP_THROWS = false;

    public static void reset() {
        LAST = null;
        ADD_TRACK_THROWS = false;
        STOP_THROWS = false;
    }

    public final List<Written> written = new ArrayList<Written>();
    public final List<MediaFormat> tracks = new ArrayList<MediaFormat>();
    public int orientationHint = -1;
    public boolean started = false;
    public boolean stopped = false;
    public boolean released = false;

    private final Map<Integer, Long> mLastPts = new HashMap<Integer, Long>();
    private final File mPath;

    public MediaMuxer(String path, int format) throws IOException {
        mPath = new File(path);
        // The real one creates the file at construction, and the caller renames it afterwards,
        // so the harness has to see a real file appear or the rename it does cannot be checked.
        FileOutputStream out = new FileOutputStream(mPath);
        out.write(new byte[]{0});
        out.close();
        LAST = this;
    }

    public int addTrack(MediaFormat format) {
        checkAlive();
        if (started) throw new IllegalStateException("addTrack after start");
        if (ADD_TRACK_THROWS) throw new IllegalArgumentException("stub refuses this format");
        if (format == null) throw new IllegalArgumentException("null format");
        tracks.add(format);
        return tracks.size() - 1;
    }

    public void setOrientationHint(int degrees) {
        checkAlive();
        if (started) throw new IllegalStateException("setOrientationHint after start");
        if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270) {
            throw new IllegalArgumentException("illegal orientation " + degrees);
        }
        orientationHint = degrees;
    }

    public void start() {
        checkAlive();
        if (started) throw new IllegalStateException("already started");
        if (tracks.isEmpty()) throw new IllegalStateException("no tracks");
        started = true;
    }

    public void writeSampleData(int trackIndex, ByteBuffer buffer, MediaCodec.BufferInfo info) {
        checkAlive();
        if (!started) throw new IllegalStateException("writeSampleData before start");
        if (trackIndex < 0 || trackIndex >= tracks.size()) {
            throw new IllegalArgumentException("no track " + trackIndex);
        }
        if (buffer == null) throw new IllegalArgumentException("null buffer");
        if (info.offset + info.size > buffer.capacity()) {
            throw new IllegalArgumentException("offset + size past the buffer's capacity");
        }
        if (info.presentationTimeUs < 0L) {
            throw new WouldHaveHung("a negative timestamp marks the track malformed, after "
                    + "which the next write to it blocks forever");
        }
        Long last = mLastPts.get(Integer.valueOf(trackIndex));
        if (last != null && info.presentationTimeUs < last.longValue()) {
            throw new WouldHaveHung("timestamp " + info.presentationTimeUs + " after "
                    + last + " on track " + trackIndex + " blocks forever on API 21");
        }
        mLastPts.put(Integer.valueOf(trackIndex), Long.valueOf(info.presentationTimeUs));
        written.add(new Written(trackIndex, info.presentationTimeUs, info.flags, info.size));
    }

    public void stop() {
        checkAlive();
        if (!started) throw new IllegalStateException("stop before start");
        if (STOP_THROWS) throw new IllegalStateException("stub refuses to stop");
        for (int i = 0; i < tracks.size(); i++) {
            if (!mLastPts.containsKey(Integer.valueOf(i))) {
                throw new IllegalStateException("track " + i + " received no samples");
            }
        }
        started = false;
        stopped = true;
    }

    public void release() {
        released = true;
    }

    /** Everything written to one track, in order. */
    public List<Written> of(int track) {
        List<Written> out = new ArrayList<Written>();
        for (int i = 0; i < written.size(); i++) {
            if (written.get(i).track == track) out.add(written.get(i));
        }
        return out;
    }

    private void checkAlive() {
        if (released) throw new IllegalStateException("muxer is released");
    }
}
