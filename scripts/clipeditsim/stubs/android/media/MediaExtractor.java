package android.media;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A MediaExtractor over a synthetic file.
 *
 * The harness builds the tracks and parks them in {@link #NEXT_FILE}; {@code setDataSource}
 * picks them up and ignores the descriptor, so the class under test can be given a real empty
 * temp file and still read a forty minute clip.
 *
 * <b>It models the platform's refusals, not only its successes</b> (§16.16). In particular
 * {@code readSampleData} throws an {@code IllegalArgumentException} with a <b>null message</b>
 * when the buffer is too small, exactly as the real JNI does when the native layer returns
 * -ENOMEM, because that is the failure a wrong buffer size actually produces and a stub that
 * short-read instead would prove nothing about the code that sizes the buffer.
 */
public final class MediaExtractor {

    public static final int SAMPLE_FLAG_SYNC = 1;
    public static final int SAMPLE_FLAG_ENCRYPTED = 2;
    public static final int SAMPLE_FLAG_PARTIAL_FRAME = 4;

    public static final int SEEK_TO_PREVIOUS_SYNC = 0;
    public static final int SEEK_TO_NEXT_SYNC = 1;
    public static final int SEEK_TO_CLOSEST_SYNC = 2;

    /** One synthetic track. */
    public static final class Track {
        public final String mime;
        public final long[] timesUs;
        public final boolean[] sync;
        public final int[] sizes;
        /** Extra sample flags OR-ed in, to exercise the flag translation. */
        public final int[] extraFlags;
        public final MediaFormat format;

        public Track(String mime, long[] timesUs, boolean[] sync, int[] sizes, int[] extraFlags,
                     MediaFormat format) {
            this.mime = mime;
            this.timesUs = timesUs;
            this.sync = sync;
            this.sizes = sizes;
            this.extraFlags = extraFlags;
            this.format = format;
        }
    }

    /** The file the next constructed extractor will read. */
    public static List<Track> NEXT_FILE = new ArrayList<Track>();
    /** Set to make setDataSource throw, as an unreadable file does. */
    public static boolean DATA_SOURCE_THROWS = false;
    /** Set to make getTrackFormat throw, as a corrupt header can. */
    public static int TRACK_FORMAT_THROWS_AT = -1;
    /** Every seek this extractor was asked for, so a harness can assert it did not walk. */
    public static final List<Long> SEEKS = new ArrayList<Long>();
    /** How many times advance() was called, so a harness can assert the walk is not quadratic. */
    public static long ADVANCES = 0L;

    public static void reset() {
        NEXT_FILE = new ArrayList<Track>();
        DATA_SOURCE_THROWS = false;
        TRACK_FORMAT_THROWS_AT = -1;
        SEEKS.clear();
        ADVANCES = 0L;
    }

    private List<Track> mTracks = new ArrayList<Track>();
    private int mSelected = -1;
    private int mCursor = 0;
    private boolean mReleased = false;

    public void setDataSource(FileDescriptor fd) throws IOException {
        if (DATA_SOURCE_THROWS) throw new IOException("stub refuses this data source");
        mTracks = NEXT_FILE;
    }

    public void setDataSource(String path) throws IOException {
        setDataSource((FileDescriptor) null);
    }

    public int getTrackCount() {
        checkAlive();
        return mTracks.size();
    }

    public MediaFormat getTrackFormat(int index) {
        checkAlive();
        if (index == TRACK_FORMAT_THROWS_AT) {
            throw new IllegalArgumentException("stub refuses track " + index);
        }
        return mTracks.get(index).format;
    }

    public void selectTrack(int index) {
        checkAlive();
        if (index < 0 || index >= mTracks.size()) {
            throw new IllegalArgumentException("no track " + index);
        }
        if (mSelected >= 0 && mSelected != index) {
            // The real one allows several selected tracks at once; this harness only ever uses
            // one, and saying so loudly beats silently modelling something the caller does not do.
            throw new IllegalStateException("stub supports one selected track, "
                    + mSelected + " is still selected");
        }
        mSelected = index;
        mCursor = 0;
    }

    public void unselectTrack(int index) {
        checkAlive();
        if (mSelected == index) mSelected = -1;
    }

    public void seekTo(long timeUs, int mode) {
        checkAlive();
        SEEKS.add(Long.valueOf(timeUs));
        if (mSelected < 0) return;
        Track track = mTracks.get(mSelected);
        int landing = 0;
        for (int i = 0; i < track.timesUs.length; i++) {
            boolean usable = mode == SEEK_TO_PREVIOUS_SYNC || mode == SEEK_TO_CLOSEST_SYNC
                    ? track.sync[i] : track.sync[i];
            if (!usable) continue;
            if (track.timesUs[i] <= timeUs) landing = i;
        }
        if (mode == SEEK_TO_NEXT_SYNC) {
            for (int i = 0; i < track.timesUs.length; i++) {
                if (track.sync[i] && track.timesUs[i] >= timeUs) {
                    landing = i;
                    break;
                }
            }
        }
        mCursor = landing;
    }

    public long getSampleTime() {
        checkAlive();
        if (mSelected < 0) return -1L;
        Track track = mTracks.get(mSelected);
        if (mCursor >= track.timesUs.length) return -1L;
        return track.timesUs[mCursor];
    }

    public int getSampleFlags() {
        checkAlive();
        if (mSelected < 0) return -1;
        Track track = mTracks.get(mSelected);
        if (mCursor >= track.timesUs.length) return -1;
        int flags = track.sync[mCursor] ? SAMPLE_FLAG_SYNC : 0;
        flags |= track.extraFlags[mCursor];
        return flags;
    }

    public int getSampleTrackIndex() {
        checkAlive();
        if (mSelected < 0) return -1;
        Track track = mTracks.get(mSelected);
        return mCursor >= track.timesUs.length ? -1 : mSelected;
    }

    public int readSampleData(ByteBuffer buffer, int offset) {
        checkAlive();
        if (mSelected < 0) return -1;
        Track track = mTracks.get(mSelected);
        if (mCursor >= track.timesUs.length) return -1;
        int size = track.sizes[mCursor];
        if (offset + size > buffer.capacity()) {
            // What the platform really does: ENOMEM becomes an IllegalArgumentException whose
            // message is null, which is indistinguishable from a caller bug unless the caller
            // says so itself.
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < size; i++) buffer.put(offset + i, (byte) (mCursor + i));
        return size;
    }

    public boolean advance() {
        checkAlive();
        ADVANCES++;
        if (mSelected < 0) return false;
        Track track = mTracks.get(mSelected);
        if (mCursor >= track.timesUs.length) return false;
        mCursor++;
        return mCursor < track.timesUs.length;
    }

    public void release() {
        mReleased = true;
    }

    private void checkAlive() {
        if (mReleased) throw new IllegalStateException("extractor is released");
    }
}
