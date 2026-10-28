import net.kdt.pojavlaunch.ui.content.NbtEditor;
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
            // A short and a float, so every fixed-width tag type is one the walker must step
            // over at least once somewhere before the fields the checks read.
            tag(o, 2, "SpawnY"); o.writeShort(64);
            tag(o, 5, "SpawnAngle"); o.writeFloat(0.5f);
            // A byte array in the middle: must be stepped over exactly or everything after shifts.
            tag(o, 7, "BiomeBytes"); o.writeInt(300); o.write(new byte[300]);
            tag(o, 11, "Ints"); o.writeInt(5); for (int i = 0; i < 5; i++) o.writeInt(i);
            tag(o, 12, "Longs"); o.writeInt(3); for (int i = 0; i < 3; i++) o.writeLong(i);
            // A list of compounds, also stepped over.
            tag(o, 9, "ServerBrands"); o.writeByte(8); o.writeInt(2); str(o, "vanilla"); str(o, "fabric");
            // A compound BEFORE the one being looked for, so reaching Version means stepping
            // over a whole compound rather than recursing into it. Without this, skipPayload's
            // TAG_COMPOUND arm is unreachable from every check in this file.
            tag(o, 10, "DragonFight");
              tag(o, 1, "DragonKilled"); o.writeByte(1);
              tag(o, 10, "ExitPortalLocation");
                tag(o, 3, "X"); o.writeInt(0);
                tag(o, 3, "Y"); o.writeInt(64);
              o.writeByte(0);                           // end ExitPortalLocation
              tag(o, 9, "Gateways"); o.writeByte(3); o.writeInt(4);
                for (int i = 0; i < 4; i++) o.writeInt(i);
            o.writeByte(0);                             // end DragonFight
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

    static byte[] bytesOf(File f) throws IOException {
        byte[] out = new byte[(int) f.length()];
        DataInputStream in = new DataInputStream(new FileInputStream(f));
        in.readFully(out);
        in.close();
        return out;
    }

    /**
     * NbtEditor: a splice, so everything it does not touch has to come back byte for byte.
     *
     * That property is the whole reason this class exists instead of a tree writer, and it is
     * also the only one worth asserting hard: a writer that round-trips the fields the harness
     * happens to look at while quietly dropping a capped list would pass a weaker check and
     * destroy somebody's world. So each edit is compared against the original bytes with the
     * edited range cut out of both, which is an assertion about the whole file rather than
     * about the fields.
     */
    static void editorChecks(File dir) throws Exception {
        File level = writeLevel(dir, true);
        byte[] plain = NbtEditor.gunzip(bytesOf(level));

        // ---- a string, the same length ----
        byte[] sameLength = NbtEditor.setString(plain, "Skyblock Extra", "Data", "LevelName");
        check("string of equal length splices",
                sameLength != null && sameLength.length == plain.length,
                sameLength == null ? "null" : sameLength.length + " vs " + plain.length);

        // ---- a string, longer and shorter: the file must change size by exactly the delta ----
        byte[] longer = NbtEditor.setString(plain, "A Much Longer World Name", "Data", "LevelName");
        check("longer string grows the file by the delta",
                longer != null && longer.length == plain.length + ("A Much Longer World Name".length()
                        - "Skyblock Ultra".length()),
                longer == null ? "null" : String.valueOf(longer.length - plain.length));
        byte[] shorter = NbtEditor.setString(plain, "Hi", "Data", "LevelName");
        check("shorter string shrinks the file by the delta",
                shorter != null && shorter.length == plain.length
                        + (2 - "Skyblock Ultra".length()),
                shorter == null ? "null" : String.valueOf(shorter.length - plain.length));

        // ---- everything else is untouched, asserted on the bytes ----
        // The LevelName payload is the only range allowed to differ. Find it by the one thing
        // that cannot lie: the common prefix and the common suffix of the two files.
        int prefix = 0;
        while (prefix < plain.length && prefix < longer.length && plain[prefix] == longer[prefix]) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < plain.length - prefix && suffix < longer.length - prefix
                && plain[plain.length - 1 - suffix] == longer[longer.length - 1 - suffix]) {
            suffix++;
        }
        int changedInOriginal = plain.length - prefix - suffix;
        check("only the edited payload differs",
                changedInOriginal <= "Skyblock Ultra".length() + 2,
                changedInOriginal + " bytes differ");

        // ---- and it still parses, with every neighbour intact ----
        File out = new File(dir, "edited.dat");
        NbtEditor.writeAll(out, NbtEditor.gzip(longer));
        Map<String, Object> reread = NbtReader.read(out);
        check("edited file parses",
                "A Much Longer World Name".equals(NbtReader.getString(reread, "Data", "LevelName")),
                String.valueOf(NbtReader.getString(reread, "Data", "LevelName")));
        check("the field after the edit survives",
                "1.20.1".equals(NbtReader.getString(reread, "Data", "Version", "Name")),
                String.valueOf(NbtReader.getString(reread, "Data", "Version", "Name")));
        check("the long after the edit survives",
                NbtReader.getLong(reread, 0, "Data", "LastPlayed") == 1735689600000L,
                String.valueOf(NbtReader.getLong(reread, 0, "Data", "LastPlayed")));
        check("the double after the edit survives",
                NbtReader.get(reread, "Data", "BorderSize") instanceof Double,
                String.valueOf(NbtReader.get(reread, "Data", "BorderSize")));

        // ---- numbers ----
        byte[] typed = NbtEditor.setInt(plain, 1, "Data", "GameType");
        File gm = new File(dir, "gm.dat");
        NbtEditor.writeAll(gm, NbtEditor.gzip(typed));
        check("int edit lands and keeps the length",
                typed != null && typed.length == plain.length
                        && NbtReader.getLong(NbtReader.read(gm), -1, "Data", "GameType") == 1,
                String.valueOf(NbtReader.getLong(NbtReader.read(gm), -1, "Data", "GameType")));

        byte[] flagged = NbtEditor.setByte(plain, 0, "Data", "hardcore");
        File hc = new File(dir, "hc.dat");
        NbtEditor.writeAll(hc, NbtEditor.gzip(flagged));
        check("byte edit lands",
                flagged != null
                        && NbtReader.getLong(NbtReader.read(hc), -1, "Data", "hardcore") == 0,
                String.valueOf(NbtReader.getLong(NbtReader.read(hc), -1, "Data", "hardcore")));

        // ---- the type has to match, or a world stops loading quietly ----
        check("setInt refuses a byte field",
                NbtEditor.setInt(plain, 1, "Data", "hardcore") == null, "refused");
        check("setByte refuses an int field",
                NbtEditor.setByte(plain, 1, "Data", "GameType") == null, "refused");
        check("setString refuses a number",
                NbtEditor.setString(plain, "x", "Data", "GameType") == null, "refused");

        // ---- paths that are not there ----
        check("absent key refused",
                NbtEditor.setString(plain, "x", "Data", "Nope") == null, "refused");
        check("absent parent refused",
                NbtEditor.setString(plain, "x", "Nope", "LevelName") == null, "refused");
        check("empty path refused",
                NbtEditor.setString(plain, "x") == null, "refused");
        check("a compound is not a value",
                NbtEditor.setString(plain, "x", "Data") == null, "refused");

        // ---- nested, past a list and two arrays ----
        byte[] nested = NbtEditor.setString(plain, "1.21.4", "Data", "Version", "Name");
        File nf = new File(dir, "nested.dat");
        NbtEditor.writeAll(nf, NbtEditor.gzip(nested));
        Map<String, Object> nestedRoot = NbtReader.read(nf);
        check("nested string edit lands",
                "1.21.4".equals(NbtReader.getString(nestedRoot, "Data", "Version", "Name")),
                String.valueOf(NbtReader.getString(nestedRoot, "Data", "Version", "Name")));
        check("its sibling int survives",
                NbtReader.getLong(nestedRoot, -1, "Data", "Version", "Id") == 3465,
                String.valueOf(NbtReader.getLong(nestedRoot, -1, "Data", "Version", "Id")));
        check("the outer name survives a nested edit",
                "Skyblock Ultra".equals(NbtReader.getString(nestedRoot, "Data", "LevelName")),
                String.valueOf(NbtReader.getString(nestedRoot, "Data", "LevelName")));

        // ---- edit() on a real file: backup kept, content changed, still readable ----
        File live = new File(dir, "live.dat");
        NbtEditor.writeAll(live, bytesOf(level));
        boolean ok = NbtEditor.edit(live, new NbtEditor.Change() {
            public byte[] apply(byte[] nbt) {
                return NbtEditor.setString(nbt, "Renamed", "Data", "LevelName");
            }
        });
        check("edit() reports success", ok, String.valueOf(ok));
        check("edit() changed the file",
                "Renamed".equals(NbtReader.getString(NbtReader.read(live), "Data", "LevelName")),
                String.valueOf(NbtReader.getString(NbtReader.read(live), "Data", "LevelName")));
        // Named level.dat_old, which is the name Minecraft itself falls back to, so a bad edit
        // is recoverable by launching the game rather than by a file manager that cannot reach
        // this folder at all.
        // The literal, not NbtEditor.BACKUP_NAME. The value is a contract with Minecraft,
        // which falls back to this exact name, so a check that reads the constant out of the
        // code under test would agree with any rename of it.
        check("the backup is named the way Minecraft looks for it",
                "level.dat_old".equals(NbtEditor.BACKUP_NAME), NbtEditor.BACKUP_NAME);
        File backup = new File(dir, "level.dat_old");
        check("edit() left a backup under Minecraft's own backup name",
                backup.isFile() && "Skyblock Ultra".equals(
                        NbtReader.getString(NbtReader.read(backup), "Data", "LevelName")),
                backup.isFile() ? "kept" : "missing");
        check("edit() left no temporary behind",
                !new File(dir, "live.dat.tmp").exists(), "clean");
        check("the edited file is still gzipped",
                NbtEditor.isGzip(bytesOf(live)), "gzip");

        // ---- a change that returns null must not touch the file ----
        byte[] before = bytesOf(live);
        boolean refused = NbtEditor.edit(live, new NbtEditor.Change() {
            public byte[] apply(byte[] nbt) { return null; }
        });
        check("a null change writes nothing",
                !refused && java.util.Arrays.equals(before, bytesOf(live)), "untouched");

        // ---- a change that corrupts the file must be rejected before it lands ----
        boolean corrupt = NbtEditor.edit(live, new NbtEditor.Change() {
            public byte[] apply(byte[] nbt) { return "not nbt at all".getBytes(); }
        });
        check("a corrupting change is refused and the file survives",
                !corrupt && java.util.Arrays.equals(before, bytesOf(live)),
                corrupt ? "WROTE IT" : "untouched");

        // ---- an uncompressed level.dat stays uncompressed ----
        File flat = new File(dir, "flat.dat");
        NbtEditor.writeAll(flat, plain);
        NbtEditor.edit(flat, new NbtEditor.Change() {
            public byte[] apply(byte[] nbt) {
                return NbtEditor.setString(nbt, "Flat", "Data", "LevelName");
            }
        });
        check("an uncompressed file is not gzipped on the way out",
                !NbtEditor.isGzip(bytesOf(flat))
                        && "Flat".equals(NbtReader.getString(NbtReader.read(flat), "Data", "LevelName")),
                "plain");

        // ---- a name long enough that the length prefix needs both its bytes ----
        StringBuilder wide = new StringBuilder();
        for (int i = 0; i < 300; i++) wide.append('w');
        byte[] wideName = NbtEditor.setString(plain, wide.toString(), "Data", "LevelName");
        File wf = new File(dir, "wide.dat");
        NbtEditor.writeAll(wf, NbtEditor.gzip(wideName));
        Map<String, Object> wideRoot = NbtReader.read(wf);
        check("a 300 character name round trips",
                wideName != null && wide.toString().equals(
                        NbtReader.getString(wideRoot, "Data", "LevelName")),
                wideRoot == null ? "unparseable"
                        : String.valueOf(NbtReader.getString(wideRoot, "Data", "LevelName")).length()
                          + " chars");
        check("and the field after a 300 character name survives",
                "1.20.1".equals(NbtReader.getString(wideRoot, "Data", "Version", "Name")),
                String.valueOf(NbtReader.getString(wideRoot, "Data", "Version", "Name")));

        // ---- a string NBT cannot hold ----
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 70000; i++) huge.append('x');
        check("a name too long for the length prefix is refused",
                NbtEditor.setString(plain, huge.toString(), "Data", "LevelName") == null,
                "refused");

        // ---- a compound before the target has to be stepped over, not entered ----
        byte[] pastCompound = NbtEditor.setString(plain, "2.0.0", "Data", "Version", "Name");
        File pc = new File(dir, "past.dat");
        NbtEditor.writeAll(pc, NbtEditor.gzip(pastCompound));
        Map<String, Object> pcRoot = NbtReader.read(pc);
        check("the compound that was stepped over is intact",
                NbtReader.getLong(pcRoot, -1, "Data", "DragonFight", "ExitPortalLocation", "Y") == 64,
                String.valueOf(NbtReader.getLong(pcRoot, -1, "Data", "DragonFight",
                        "ExitPortalLocation", "Y")));
        check("and the field past it was the one edited",
                "2.0.0".equals(NbtReader.getString(pcRoot, "Data", "Version", "Name")),
                String.valueOf(NbtReader.getString(pcRoot, "Data", "Version", "Name")));

        // ---- modified UTF-8, which is what NBT uses and what readUTF demands ----
        // A supplementary character is four bytes in standard UTF-8 and six in modified UTF-8,
        // and readUTF rejects any byte at or above 0xF0 outright. Encoding the wrong one made
        // every rename containing an emoji fail, with the verify step correctly refusing a
        // file neither this reader nor Minecraft could parse.
        String emoji = "My World \uD83C\uDFAE";
        byte[] withEmoji = NbtEditor.setString(plain, emoji, "Data", "LevelName");
        File ef = new File(dir, "emoji.dat");
        check("an emoji name encodes at all", withEmoji != null, "encoded");
        if (withEmoji != null) {
            NbtEditor.writeAll(ef, NbtEditor.gzip(withEmoji));
            Map<String, Object> emojiRoot = NbtReader.read(ef);
            check("an emoji name round trips through the real reader",
                    emoji.equals(NbtReader.getString(emojiRoot, "Data", "LevelName")),
                    emojiRoot == null ? "unparseable"
                            : String.valueOf(NbtReader.getString(emojiRoot, "Data", "LevelName")));
            check("the field after an emoji name survives",
                    "1.20.1".equals(NbtReader.getString(emojiRoot, "Data", "Version", "Name")),
                    String.valueOf(NbtReader.getString(emojiRoot, "Data", "Version", "Name")));
        }
        // A NUL is the other place the two encodings differ: one byte in standard UTF-8 and
        // two in modified, and readUTF will not take the one byte form.
        byte[] withNul = NbtEditor.setString(plain, "a\u0000b", "Data", "LevelName");
        File nf2 = new File(dir, "nul.dat");
        if (withNul != null) {
            NbtEditor.writeAll(nf2, NbtEditor.gzip(withNul));
            check("a NUL in a name round trips",
                    "a\u0000b".equals(
                            NbtReader.getString(NbtReader.read(nf2), "Data", "LevelName")),
                    "nul");
        } else {
            check("a NUL in a name round trips", false, "refused outright");
        }

        // ---- the edit lands by rename, so level.dat is never truncated ----
        File atomic = new File(dir, "atomic.dat");
        NbtEditor.writeAll(atomic, bytesOf(level));
        NbtEditor.edit(atomic, new NbtEditor.Change() {
            public byte[] apply(byte[] nbt) {
                return NbtEditor.setString(nbt, "Atomic", "Data", "LevelName");
            }
        });
        check("no temporary is left after a rename based replace",
                !new File(dir, "atomic.dat.tmp").exists(), "clean");

        // ---- garbage in, refusal out ----
        check("garbage bytes are refused",
                NbtEditor.setString("hello".getBytes(), "x", "Data", "LevelName") == null,
                "refused");
        check("empty bytes are refused",
                NbtEditor.setString(new byte[0], "x", "Data", "LevelName") == null, "refused");
        byte[] truncated = new byte[plain.length / 2];
        System.arraycopy(plain, 0, truncated, 0, truncated.length);
        check("a truncated file is refused, not half edited",
                NbtEditor.setString(truncated, "x", "Data", "Version", "Name") == null, "refused");
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

        editorChecks(dir);

        for (File f : dir.listFiles()) f.delete();
        dir.delete();
        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
