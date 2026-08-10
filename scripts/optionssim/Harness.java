package net.kdt.pojavlaunch.optimiser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Drives the shipped options.txt merge against real files.
 *
 * This edits a file the player owns and the game also writes, so what has to be proved is not
 * that it can set a key: it is that everything it does NOT own survives the round trip. There is
 * no device in CI, so this is the only thing that can catch a merge that quietly eats someone's
 * keybinds before they do.
 */
public class Harness {
    private static int failures = 0;

    private static void check(boolean condition, String message) {
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    private static void write(File file, String content) throws Exception {
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), Charset.forName("UTF-8"))) {
            writer.write(content);
        }
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), "UTF-8");
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File options = GameOptions.fileIn(dir);

        // A file shaped like a real one: settings, a blank line, a line with no colon, a key
        // whose value contains colons (a keybind), and a duplicated key.
        String original = String.join("\n",
                "version:3465",
                "renderDistance:12",
                "key_key.forward:key.keyboard.w",
                "",
                "not a setting line",
                "resourcePacks:[\"vanilla\",\"file/pack.zip\"]",
                "soundCategory_master:1.0",
                "renderDistance:8",
                "lang:en_us") + "\n";
        write(options, original);

        GameOptions.Options parsed = GameOptions.read(dir);
        check(parsed.existed, "the file should be seen as existing");
        // The duplicate is indexed on its LAST occurrence, which is the one the game reads.
        check("8".equals(parsed.get("renderDistance")),
                "duplicated key should read as the last value, got " + parsed.get("renderDistance"));
        check("key.keyboard.w".equals(parsed.get("key_key.forward")),
                "a keybind value should survive, got " + parsed.get("key_key.forward"));

        parsed.set("renderDistance", "8");
        parsed.set("graphics", "fast");
        parsed.set("entityShadows", "false");
        GameOptions.write(dir, parsed);

        String after = read(options);
        // Everything not owned survives, in place.
        check(after.contains("version:3465"), "an unknown key was lost");
        check(after.contains("key_key.forward:key.keyboard.w"), "a keybind was lost");
        check(after.contains("resourcePacks:[\"vanilla\",\"file/pack.zip\"]"),
                "a value containing colons and quotes was mangled");
        check(after.contains("soundCategory_master:1.0"), "a sound setting was lost");
        check(after.contains("not a setting line"), "a non-setting line was dropped");
        check(after.contains("\n\n"), "a blank line was dropped");
        check(after.contains("lang:en_us"), "the last line was lost");
        // The keys we own are set, and the new ones appended.
        check(after.contains("graphics:fast"), "an appended key is missing");
        check(after.contains("entityShadows:false"), "an appended key is missing");

        // The backup is the file as it was BEFORE the first write.
        check(GameOptions.hasBackup(dir), "a backup should have been taken");
        check(read(GameOptions.backupIn(dir)).equals(original),
                "the backup should be the original file, byte for byte");

        // A second apply must not overwrite the backup, or the original is lost forever.
        GameOptions.Options again = GameOptions.read(dir);
        again.set("renderDistance", "6");
        GameOptions.write(dir, again);
        check(read(GameOptions.backupIn(dir)).equals(original),
                "a second write overwrote the backup");

        // Restore puts the player's file back exactly and clears the backup.
        check(GameOptions.restore(dir), "restore should report success");
        check(read(options).equals(original), "restore did not reproduce the original");
        check(!GameOptions.hasBackup(dir), "the backup should be gone after a restore");
        check(!GameOptions.restore(dir), "a second restore should report that there was nothing");

        // A profile that has never been launched has no file, and that is not an error: writing
        // only the keys we own is legal because Minecraft defaults everything it does not find.
        File fresh = new File(dir, "fresh");
        //noinspection ResultOfMethodCallIgnored
        fresh.mkdirs();
        GameOptions.Options empty = GameOptions.read(fresh);
        check(!empty.existed, "a missing file should report existed=false");
        empty.set("renderDistance", "8");
        GameOptions.write(fresh, empty);
        check(read(GameOptions.fileIn(fresh)).equals("renderDistance:8\n"),
                "a fresh file should contain exactly what was set");
        check(!GameOptions.hasBackup(fresh),
                "there is nothing to back up when there was no file");

        System.out.println("checked merge, duplicates, odd lines, backup, restore and fresh write");
        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("options merge OK");
    }
}
