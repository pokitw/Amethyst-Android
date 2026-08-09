package net.kdt.pojavlaunch.ui.content;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Just enough NBT to read a world's {@code level.dat}.
 *
 * A world folder's name is not the world's name. Minecraft names the folder after the world when
 * it is created and then never renames it, so a world called "Skyblock" that was renamed from
 * "New World" still lives in {@code saves/New World}, and two worlds created with the same name
 * live in {@code New World} and {@code New World (1)}. Reading the file is the only way to show
 * people the name they actually gave it — along with the version it was last opened in, which is
 * the thing worth knowing before launching a different one at it.
 *
 * <p>This is not a general NBT library and should not become one. It walks the tree, keeps the
 * scalars and strings, and steps over the bulk types entirely — a level.dat is a few kilobytes of
 * settings, and nothing here needs the arrays.
 *
 * <p>Written in Java rather than Kotlin for one reason: binary format parsing is the kind of code
 * that is wrong in ways reading cannot catch, and Java is the half of this project that can be
 * compiled and run against a fixture without a device. See {@code scripts/gyrosim} for the same
 * trick applied to the gyroscope.
 */
public final class NbtReader {

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

    /**
     * How deep the walk may go. A level.dat is three or four levels deep; anything claiming to be
     * deeper is either corrupt or hostile, and either way is not worth a stack overflow.
     */
    private static final int MAX_DEPTH = 24;

    /** Elements in one list. Real ones hold a handful; this only has to not be unbounded. */
    private static final int MAX_ELEMENTS = 4096;

    private NbtReader() {}

    /**
     * Read a compressed or uncompressed NBT file into nested maps.
     *
     * @return the root compound's contents, or null if the file cannot be read as NBT
     */
    @Nullable
    public static Map<String, Object> read(File file) {
        InputStream raw = null;
        try {
            raw = new BufferedInputStream(new FileInputStream(file), 8192);
            // Saved gzipped since Beta, but a hand-edited or third-party world may not be, and the
            // magic costs two bytes to check.
            raw.mark(2);
            int first = raw.read();
            int second = raw.read();
            raw.reset();
            InputStream body = (first == 0x1F && second == 0x8B)
                    ? new GZIPInputStream(raw, 8192) : raw;

            DataInputStream in = new DataInputStream(body);
            int type = in.readUnsignedByte();
            if (type != TAG_COMPOUND) return null;
            skipString(in);                       // the root compound's own name, always empty
            Object root = readPayload(in, TAG_COMPOUND, 0);
            //noinspection unchecked
            return root instanceof Map ? (Map<String, Object>) root : null;
        } catch (Throwable t) {
            // A world whose level.dat will not parse is still a world, and the caller falls back
            // to naming it after its folder.
            return null;
        } finally {
            close(raw);
        }
    }

    /** Follow a path of compound keys, e.g. {@code get(root, "Data", "Version", "Name")}. */
    @Nullable
    public static Object get(@Nullable Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map)) return null;
            current = ((Map<?, ?>) current).get(key);
        }
        return current;
    }

    @Nullable
    public static String getString(@Nullable Map<String, Object> root, String... path) {
        Object value = get(root, path);
        if (!(value instanceof String)) return null;
        String text = ((String) value).trim();
        return text.isEmpty() ? null : text;
    }

    /** @return the value as a long, or {@code fallback} when it is absent or not a number */
    public static long getLong(@Nullable Map<String, Object> root, long fallback, String... path) {
        Object value = get(root, path);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("nbt too deep");
        switch (type) {
            case TAG_BYTE:   return in.readByte();
            case TAG_SHORT:  return in.readShort();
            case TAG_INT:    return in.readInt();
            case TAG_LONG:   return in.readLong();
            case TAG_FLOAT:  return in.readFloat();
            case TAG_DOUBLE: return in.readDouble();
            case TAG_STRING: return in.readUTF();

            case TAG_BYTE_ARRAY: skipBytes(in, checkedLength(in.readInt()));       return null;
            case TAG_INT_ARRAY:  skipBytes(in, checkedLength(in.readInt()) * 4L);  return null;
            case TAG_LONG_ARRAY: skipBytes(in, checkedLength(in.readInt()) * 8L);  return null;

            case TAG_LIST: {
                int elementType = in.readUnsignedByte();
                int count = in.readInt();
                if (count < 0) count = 0;
                if (count > MAX_ELEMENTS) throw new IOException("nbt list too long");
                // Read and discard. Nothing this file is consulted for lives inside a list, and
                // keeping them would mean a data model for values nobody reads — but they still
                // have to be walked, because the tags after them start wherever they end.
                for (int i = 0; i < count; i++) readPayload(in, elementType, depth + 1);
                return null;
            }

            case TAG_COMPOUND: {
                Map<String, Object> map = new HashMap<>();
                while (true) {
                    int childType = in.readUnsignedByte();
                    if (childType == TAG_END) return map;
                    String name = in.readUTF();
                    Object value = readPayload(in, childType, depth + 1);
                    if (value != null) map.put(name, value);
                }
            }

            default:
                throw new IOException("unknown nbt tag " + type);
        }
    }

    private static int checkedLength(int length) throws IOException {
        if (length < 0) throw new IOException("negative nbt array length");
        return length;
    }

    private static void skipString(DataInputStream in) throws IOException {
        skipBytes(in, in.readUnsignedShort());
    }

    /**
     * {@link DataInputStream#skipBytes} is allowed to skip fewer bytes than asked and routinely
     * does on a compressed stream, so it has to be driven to completion by hand.
     */
    private static void skipBytes(DataInputStream in, long count) throws IOException {
        long left = count;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) throw new EOFException();
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static void close(@Nullable InputStream stream) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }
}
