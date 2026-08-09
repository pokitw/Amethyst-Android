import net.kdt.pojavlaunch.ui.content.NbtReader;
import java.io.*;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes a level.dat the way Minecraft does, then reads it back through the shipped NbtReader.
 * Binary format parsing is the kind of code reading cannot check, and there is no device in CI.
 */
public class NbtSim {
    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.printf("%-46s %s   %s%n", name, ok ? "PASS" : "FAIL", detail);
        if (!ok) failures++;
    }

    static void str(DataOutputStream o, String s) throws IOException { o.writeUTF(s); }
    static void tag(DataOutputStream o, int t, String n) throws IOException { o.writeByte(t); str(o, n); }

    /** A level.dat shaped like a real one: nested compounds, a list, and arrays to step over. */
    static File writeLevel(File dir, boolean gzip) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(raw);
        o.writeByte(10); str(o, "");                    // root compound, unnamed
          tag(o, 10, "Data");
            tag(o, 8, "LevelName"); str(o, "Skyblock Ultra");
            tag(o, 3, "GameType"); o.writeInt(0);
            tag(o, 1, "hardcore"); o.writeByte(1);
            tag(o, 4, "LastPlayed"); o.writeLong(1735689600000L);
            // A byte array in the middle: must be stepped over exactly or everything after shifts.
            tag(o, 7, "BiomeBytes"); o.writeInt(300); o.write(new byte[300]);
            tag(o, 11, "Ints"); o.writeInt(5); for (int i = 0; i < 5; i++) o.writeInt(i);
            tag(o, 12, "Longs"); o.writeInt(3); for (int i = 0; i < 3; i++) o.writeLong(i);
            // A list of compounds, also stepped over.
            tag(o, 9, "ServerBrands"); o.writeByte(8); o.writeInt(2); str(o, "vanilla"); str(o, "fabric");
            tag(o, 10, "Version");
              tag(o, 8, "Name"); str(o, "1.20.1");
              tag(o, 3, "Id"); o.writeInt(3465);
            o.writeByte(0);                             // end Version
            tag(o, 6, "BorderSize"); o.writeDouble(60000000.0);
          o.writeByte(0);                               // end Data
        o.writeByte(0);                                 // end root
        o.flush();

        File f = new File(dir, gzip ? "level.dat" : "level_plain.dat");
        OutputStream out = new FileOutputStream(f);
        if (gzip) out = new GZIPOutputStream(out);
        out.write(raw.toByteArray());
        out.close();
        return f;
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "nbtsim" + System.nanoTime());
        if (!dir.mkdirs()) throw new IOException("mkdir");

        Map<String, Object> root = NbtReader.read(writeLevel(dir, true));
        check("gzipped level.dat parses", root != null, root == null ? "null" : "ok");
        check("world name after a byte array",
                "Skyblock Ultra".equals(NbtReader.getString(root, "Data", "LevelName")),
                String.valueOf(NbtReader.getString(root, "Data", "LevelName")));
        check("version after a list of strings",
                "1.20.1".equals(NbtReader.getString(root, "Data", "Version", "Name")),
                String.valueOf(NbtReader.getString(root, "Data", "Version", "Name")));
        check("game type reads as a number",
                NbtReader.getLong(root, -1, "Data", "GameType") == 0,
                String.valueOf(NbtReader.getLong(root, -1, "Data", "GameType")));
        check("hardcore byte reads as a number",
                NbtReader.getLong(root, -1, "Data", "hardcore") == 1,
                String.valueOf(NbtReader.getLong(root, -1, "Data", "hardcore")));
        check("last played survives as a long",
                NbtReader.getLong(root, 0, "Data", "LastPlayed") == 1735689600000L,
                String.valueOf(NbtReader.getLong(root, 0, "Data", "LastPlayed")));
        check("missing key falls back",
                NbtReader.getString(root, "Data", "Nope") == null
                        && NbtReader.getLong(root, 42, "Nope") == 42, "ok");

        Map<String, Object> plain = NbtReader.read(writeLevel(dir, false));
        check("uncompressed level.dat also parses",
                "Skyblock Ultra".equals(NbtReader.getString(plain, "Data", "LevelName")), "ok");

        // Anything that is not NBT must come back null rather than throw.
        File junk = new File(dir, "junk.dat");
        FileOutputStream j = new FileOutputStream(junk);
        j.write("this is not nbt at all, not even close".getBytes("UTF-8"));
        j.close();
        check("garbage returns null, does not throw", NbtReader.read(junk) == null, "ok");
        check("missing file returns null",
                NbtReader.read(new File(dir, "absent.dat")) == null, "ok");

        // Truncated mid-array: the skip must hit EOF and be caught, not loop.
        byte[] full = new byte[(int) writeLevel(dir, true).length()];
        FileInputStream fi = new FileInputStream(new File(dir, "level.dat"));
        int read = fi.read(full); fi.close();
        File cut = new File(dir, "cut.dat");
        FileOutputStream c = new FileOutputStream(cut);
        c.write(full, 0, Math.max(1, read / 2)); c.close();
        long start = System.currentTimeMillis();
        NbtReader.read(cut);
        check("truncated file returns quickly",
                System.currentTimeMillis() - start < 1000,
                (System.currentTimeMillis() - start) + " ms");

        for (File f : dir.listFiles()) f.delete();
        dir.delete();
        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
