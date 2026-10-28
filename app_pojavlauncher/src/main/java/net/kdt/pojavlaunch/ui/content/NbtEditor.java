package net.kdt.pojavlaunch.ui.content;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Change one value inside a {@code level.dat}, without rewriting the rest of it.
 *
 * <h2>Why a splice and not a writer</h2>
 *
 * The obvious way to do this is to parse the file into a tree, change a field and serialise the
 * tree back. That would be wrong here, and dangerously so. {@link NbtReader} is a reader built
 * for a screen: it caps lists at a few thousand elements and nesting at a couple of dozen levels
 * so a malformed file cannot hang the launcher, and it collapses NBT's twelve tag types onto
 * Java's. Serialising its output back out would silently drop whatever those caps cut and would
 * have to guess the element type of every empty list. A world is somebody's hundreds of hours,
 * and the failure would be invisible until they opened it.
 *
 * So nothing is rewritten. The decompressed bytes are walked once, recording where the value in
 * question starts and ends, and the new bytes are spliced into that range. Every other byte of
 * the file is the byte that was there before, including the parts this code does not understand
 * and the parts a future Minecraft adds. <b>The blast radius is the one field.</b>
 *
 * <h2>What the caller still has to do</h2>
 *
 * {@link #edit} writes through a temporary file and reads the result back with {@link NbtReader}
 * before it replaces anything, so a splice that produced something unparseable fails without
 * touching the world. A backup of the original is left beside it. Those are the reason this is
 * safe to offer at all: being wrong has to be cheap, and here it costs a file that is still on
 * disk.
 *
 * Plain Java with no Android imports, so {@code scripts/nbtsim} can compile and drive the real
 * class rather than a transcription of it.
 */
public final class NbtEditor {

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    /** Matches NbtReader's. A file deeper than this is one neither of them will touch. */
    private static final int MAX_DEPTH = 24;

    /** A level.dat is tens of kilobytes. Anything past this is not one. */
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    /** NBT strings are length-prefixed with an unsigned short, in MODIFIED UTF-8 bytes. */
    private static final int MAX_STRING_BYTES = 0xFFFF;

    /** Minecraft's own name for the file it falls back to when level.dat will not read. */
    public static final String BACKUP_NAME = "level.dat_old";

    private NbtEditor() {}

    /** One field's payload, located in the decompressed bytes. */
    private static final class Span {
        final int type;
        final int start;
        final int end;

        Span(int type, int start, int end) {
            this.type = type;
            this.start = start;
            this.end = end;
        }
    }

    /* ------------------------------------------------------------------ the public edits */

    /**
     * Replace a string value, e.g. the world's name at {@code Data/LevelName}.
     *
     * @return the new file contents, or null if the path is absent, holds something that is not
     *         a string, or the replacement will not fit NBT's length prefix
     */
    @Nullable
    public static byte[] setString(@NonNull byte[] nbt, @NonNull String value,
                                   @NonNull String... path) {
        byte[] replacement = encodeString(value);
        if (replacement == null) return null;
        Span span = locate(nbt, path);
        if (span == null || span.type != TAG_STRING) return null;
        return splice(nbt, span, replacement);
    }

    /**
     * A length prefixed NBT string: {@code DataOutput.writeUTF}, not standard UTF-8.
     *
     * <b>NBT uses Java's modified UTF-8</b>, and the difference is not cosmetic. A character
     * outside the basic multilingual plane, which is to say every emoji, is four bytes in
     * standard UTF-8 and six in modified UTF-8, encoded as a surrogate pair. {@code readUTF},
     * which is what {@link NbtReader} and Minecraft both read these with, rejects any byte at
     * or above 0xF0 outright. So a world renamed with {@code String.getBytes("UTF-8")} produced
     * a file that neither could parse, the verify step correctly refused it, and renaming to
     * anything containing an emoji failed every time with no reason given.
     *
     * Written through {@code DataOutputStream} rather than by hand, so the encoder is literally
     * the one the reader is paired with. It also throws for a string too long for the two byte
     * prefix, which is the bound that matters and is measured in the right units: counting
     * standard UTF-8 bytes would have allowed a string the real encoder then refused.
     */
    @Nullable
    private static byte[] encodeString(@NonNull String value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length() + 2);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(value);
            out.flush();
            byte[] encoded = bytes.toByteArray();
            return encoded.length > MAX_STRING_BYTES + 2 ? null : encoded;
        } catch (IOException e) {
            // UTFDataFormatException for a string past the prefix's reach, and nothing else:
            // a ByteArrayOutputStream does not fail.
            return null;
        }
    }

    /**
     * Replace a byte value, e.g. {@code Data/allowCommands}.
     *
     * Minecraft writes every boolean as a TAG_Byte, so this is what "turn cheats on" is.
     */
    @Nullable
    public static byte[] setByte(@NonNull byte[] nbt, int value, @NonNull String... path) {
        Span span = locate(nbt, path);
        if (span == null || span.type != TAG_BYTE) return null;
        return splice(nbt, span, new byte[]{(byte) value});
    }

    /**
     * Replace an int value, e.g. {@code Data/GameType}.
     *
     * Deliberately strict about the tag type rather than widening to "any integer": a field
     * written as a TAG_Int in every version there has ever been is not one to start guessing
     * about, and a quiet type change is how a world stops loading.
     */
    @Nullable
    public static byte[] setInt(@NonNull byte[] nbt, int value, @NonNull String... path) {
        Span span = locate(nbt, path);
        if (span == null || span.type != TAG_INT) return null;
        return splice(nbt, span, new byte[]{
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        });
    }

    /* ------------------------------------------------------------------ files */

    /** What a caller is asked to do to the bytes. */
    public interface Change {
        /** @return the new contents, or null to abandon the edit without touching anything */
        @Nullable byte[] apply(@NonNull byte[] nbt);
    }

    /**
     * Apply a change to an NBT file on disk, safely.
     *
     * Five steps, and the order is the whole of the safety: read, change, write somewhere else,
     * <b>read the new file back with the shipped reader</b>, and only then put it in place. A
     * splice that corrupted the file fails the fourth step and the world is untouched.
     *
     * <b>Put in place by renaming the verified file</b>, which is the part worth being careful
     * about. Writing over {@code level.dat} truncates it to zero the instant the stream opens,
     * so a process killed during that write leaves a world with an empty data file and no way
     * back; {@code rename} replaces the path atomically and the file is never absent or
     * partial. It also means the bytes that land are the exact bytes that were verified, rather
     * than a second encoding of the same tree that is only identical because the compressor
     * happens to be deterministic.
     *
     * <b>The backup is {@code level.dat_old}</b>, which is Minecraft's own name for one: the
     * game falls back to it when {@code level.dat} will not read, so a recovery needs no file
     * manager, which matters because from Android 11 nothing can reach this folder anyway. The
     * cost is that it overwrites the game's own previous backup, which is the lesser loss of
     * the two and is a file the game rewrites on its next save regardless.
     *
     * @return true if the file now holds the change
     */
    public static boolean edit(@NonNull File file, @NonNull Change change) {
        byte[] original;
        boolean gzipped;
        try {
            original = readAll(file);
            gzipped = isGzip(original);
            byte[] plain = gzipped ? gunzip(original) : original;
            byte[] changed = change.apply(plain);
            if (changed == null) return false;

            byte[] encoded = gzipped ? gzip(changed) : changed;
            File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
            writeAll(temporary, encoded);
            // The shipped reader, not a check of our own: what has to be true is that the file
            // the launcher reads still parses, and the only honest way to assert that is to use
            // the thing that will read it.
            if (NbtReader.read(temporary) == null) {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
                return false;
            }
            File backup = new File(file.getParentFile(), BACKUP_NAME);
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
            writeAll(backup, original);
            if (!temporary.renameTo(file)) {
                // Within one directory this is rename(2) and does not fail, but a filesystem
                // that refuses it must not leave the edit unapplied with a backup sitting
                // beside it suggesting otherwise. The same verified bytes, written in place.
                writeAll(file, encoded);
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /* ------------------------------------------------------------------ walking */

    /**
     * Find a named value's payload by following a path of compound keys.
     *
     * Walks the real structure rather than searching for the key's bytes, because a name is not
     * unique in a file: "Data" appears inside several unrelated compounds in a modded level.dat,
     * and a byte search would find whichever came first.
     */
    @Nullable
    private static Span locate(@NonNull byte[] nbt, @NonNull String... path) {
        if (path.length == 0 || nbt.length > MAX_BYTES) return null;
        Cursor cursor = new Cursor(nbt);
        try {
            if (cursor.u8() != TAG_COMPOUND) return null;
            cursor.skipName();                        // the root compound's own name
            return locateIn(cursor, path, 0, 0);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Scan the compound the cursor is inside for {@code path[index]}, recursing as needed. */
    @Nullable
    private static Span locateIn(Cursor cursor, String[] path, int index, int depth)
            throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("too deep");
        while (true) {
            int type = cursor.u8();
            if (type == TAG_END) return null;
            String name = cursor.name();
            boolean wanted = name.equals(path[index]);
            boolean last = index == path.length - 1;
            if (wanted && last) {
                int start = cursor.at();
                cursor.skipPayload(type, depth);
                return new Span(type, start, cursor.at());
            }
            if (wanted && type == TAG_COMPOUND) {
                Span found = locateIn(cursor, path, index + 1, depth + 1);
                if (found != null) return found;
                // The key matched but the rest of the path did not. The cursor is now just past
                // this compound's TAG_End, so the scan simply carries on with its siblings.
                continue;
            }
            cursor.skipPayload(type, depth);
        }
    }

    private static byte[] splice(byte[] nbt, Span span, byte[] replacement) {
        byte[] out = new byte[nbt.length - (span.end - span.start) + replacement.length];
        System.arraycopy(nbt, 0, out, 0, span.start);
        System.arraycopy(replacement, 0, out, span.start, replacement.length);
        System.arraycopy(nbt, span.end, out, span.start + replacement.length,
                nbt.length - span.end);
        return out;
    }

    /**
     * A position in the decompressed bytes, which knows how long every tag type is.
     *
     * Reading nothing and skipping everything: the point of this class is that it never builds a
     * value, so there is nothing for a cap or a type collapse to lose.
     */
    private static final class Cursor {
        private final byte[] data;
        private int position;

        Cursor(byte[] data) {
            this.data = data;
        }

        int at() {
            return position;
        }

        int u8() throws IOException {
            require(1);
            return data[position++] & 0xFF;
        }

        int u16() throws IOException {
            require(2);
            int value = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
            position += 2;
            return value;
        }

        int s32() throws IOException {
            require(4);
            int value = ((data[position] & 0xFF) << 24) | ((data[position + 1] & 0xFF) << 16)
                    | ((data[position + 2] & 0xFF) << 8) | (data[position + 3] & 0xFF);
            position += 4;
            return value;
        }

        String name() throws IOException {
            int length = u16();
            require(length);
            String value = new String(data, position, length, "UTF-8");
            position += length;
            return value;
        }

        void skipName() throws IOException {
            int length = u16();
            skip(length);
        }

        void skip(long count) throws IOException {
            if (count < 0 || position + count > data.length) throw new IOException("overrun");
            position += (int) count;
        }

        void require(int count) throws IOException {
            if (count < 0 || position + count > data.length) throw new IOException("overrun");
        }

        /** Advance past one payload of the given type, leaving the cursor on what follows. */
        void skipPayload(int type, int depth) throws IOException {
            if (depth > MAX_DEPTH) throw new IOException("too deep");
            switch (type) {
                case TAG_BYTE: skip(1); return;
                case TAG_SHORT: skip(2); return;
                case TAG_INT: case TAG_FLOAT: skip(4); return;
                case TAG_LONG: case TAG_DOUBLE: skip(8); return;
                case TAG_BYTE_ARRAY: skip(lengthOf(1)); return;
                case TAG_INT_ARRAY: skip(lengthOf(4)); return;
                case TAG_LONG_ARRAY: skip(lengthOf(8)); return;
                case TAG_STRING: skipName(); return;
                case TAG_LIST: {
                    int element = u8();
                    int count = s32();
                    if (count < 0) return;            // an empty list may declare a count of 0 or less
                    for (int i = 0; i < count; i++) skipPayload(element, depth + 1);
                    return;
                }
                case TAG_COMPOUND: {
                    while (true) {
                        int child = u8();
                        if (child == TAG_END) return;
                        skipName();
                        skipPayload(child, depth + 1);
                    }
                }
                default:
                    throw new IOException("unknown tag " + type);
            }
        }

        /** An array's byte length, with the multiplication done in long so it cannot wrap. */
        private long lengthOf(int elementBytes) throws IOException {
            int count = s32();
            if (count < 0) throw new IOException("negative length");
            return (long) count * elementBytes;
        }
    }

    /* ------------------------------------------------------------------ bytes on disk */

    // Public so scripts/nbtsim can drive the real class rather than a copy of it (16.25).

    public static boolean isGzip(byte[] data) {
        return data.length >= 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B;
    }

    public static byte[] readAll(File file) throws IOException {
        long length = file.length();
        if (length > MAX_BYTES) throw new IOException("too big");
        InputStream in = new BufferedInputStream(new FileInputStream(file), 8192);
        try {
            return drain(in);
        } finally {
            in.close();
        }
    }

    public static byte[] gunzip(byte[] data) throws IOException {
        GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data), 8192);
        try {
            return drain(in);
        } finally {
            in.close();
        }
    }

    public static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(1024, data.length / 2));
        GZIPOutputStream out = new GZIPOutputStream(bytes, 8192);
        try {
            out.write(data);
        } finally {
            out.close();
        }
        return bytes.toByteArray();
    }

    private static byte[] drain(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int total = 0;
        while (true) {
            int read = in.read(buffer);
            if (read < 0) break;
            total += read;
            if (total > MAX_BYTES) throw new IOException("too big");
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    public static void writeAll(File file, byte[] data) throws IOException {
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }
}
