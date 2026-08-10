package net.kdt.pojavlaunch.optimiser;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft's own {@code options.txt}, edited without taking anything away.
 *
 * <b>This file is not ours.</b> The game writes it on every exit, players edit it by hand, and it
 * carries hundreds of keys the launcher has never heard of: keybinds, resource pack order, chat
 * settings, the sound category volumes. So the rule here is the same one the control skin follows
 * (handbook 12.11): read everything, change only the handful of keys we own, write everything
 * back. A key this launcher does not understand must come out the other side byte for byte, in
 * the same place in the file.
 *
 * <b>Line-oriented, because the file is.</b> {@code key:value}, one per line, no escaping, no
 * sections. A line that is blank, or that has no colon, is not a setting; it is kept as-is at its
 * original position rather than dropped, because being wrong about that costs someone a setting
 * and the only upside would be a tidier file.
 *
 * <b>The backup is taken once.</b> The first time performance mode writes, the untouched file is
 * copied beside it. Turning the mode off restores that copy, so "off" means the settings the
 * player actually had, not a launcher's idea of defaults. A second apply does not overwrite the
 * backup, or the original would be lost after the first one.
 *
 * <b>What this cannot do</b> is edit a file the game currently has open. Minecraft reads
 * options.txt once at startup and writes its whole in-memory copy back on exit, so an edit made
 * while it runs is overwritten a moment later with no error anywhere. The caller is responsible
 * for only applying this while the game is not running; that is a launcher-side condition, and it
 * is why performance mode lives in Settings rather than in the in-game control center.
 */
public final class GameOptions {
    private static final String TAG = "GameOptions";
    private static final String FILE_NAME = "options.txt";
    private static final String BACKUP_NAME = "options.txt.amethyst-backup";
    /** A game options file is a few KB. Anything past this is not one, and is left alone. */
    private static final long MAX_BYTES = 4L * 1024 * 1024;

    private GameOptions() {}

    @NonNull
    public static File fileIn(@NonNull File gameDir) {
        return new File(gameDir, FILE_NAME);
    }

    @NonNull
    public static File backupIn(@NonNull File gameDir) {
        return new File(gameDir, BACKUP_NAME);
    }

    /** Whether there is a backup to go back to, which is what makes "turn it off" honest. */
    public static boolean hasBackup(@NonNull File gameDir) {
        return backupIn(gameDir).isFile();
    }

    /**
     * One line of the file, kept whole.
     *
     * A setting has a key; anything else (a blank line, a comment, a line with no colon) has a
     * null key and is written back exactly as it was read.
     */
    private static final class Line {
        @Nullable final String key;
        String raw;

        Line(@Nullable String key, String raw) {
            this.key = key;
            this.raw = raw;
        }
    }

    /** The parsed file: every line in order, plus an index of the ones that are settings. */
    public static final class Options {
        private final List<Line> lines;
        private final Map<String, Line> byKey;
        /** False when there was no file to read, so the caller can say "not launched yet". */
        public final boolean existed;

        private Options(List<Line> lines, Map<String, Line> byKey, boolean existed) {
            this.lines = lines;
            this.byKey = byKey;
            this.existed = existed;
        }

        @Nullable
        public String get(@NonNull String key) {
            Line line = byKey.get(key);
            if (line == null) return null;
            int colon = line.raw.indexOf(':');
            return colon < 0 ? null : line.raw.substring(colon + 1);
        }

        /** Set a key, in place if it is already there and appended if it is not. */
        public void set(@NonNull String key, @NonNull String value) {
            Line line = byKey.get(key);
            if (line != null) {
                line.raw = key + ":" + value;
                return;
            }
            // Appended rather than inserted: the game does not care about order, and appending
            // keeps every existing line at the index the player would find it at.
            Line added = new Line(key, key + ":" + value);
            lines.add(added);
            byKey.put(key, added);
        }

        public boolean has(@NonNull String key) {
            return byKey.containsKey(key);
        }

        public int size() {
            return byKey.size();
        }
    }

    /**
     * Read the file, or an empty set of options when there is not one yet.
     *
     * A profile that has never been launched has no options.txt, and that is not an error: writing
     * only the keys performance mode owns is legal, because Minecraft fills in every key it does
     * not find with its own default.
     */
    @NonNull
    public static Options read(@NonNull File gameDir) throws IOException {
        File file = fileIn(gameDir);
        List<Line> lines = new ArrayList<>();
        Map<String, Line> byKey = new LinkedHashMap<>();
        if (!file.isFile()) return new Options(lines, byKey, false);
        if (file.length() > MAX_BYTES) {
            throw new IOException("options.txt is " + file.length() + " bytes, which is not one");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8")))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                int colon = raw.indexOf(':');
                String key = colon > 0 ? raw.substring(0, colon) : null;
                Line line = new Line(key, raw);
                lines.add(line);
                // The LAST occurrence wins the index, not the first. A hand-edited file can carry
                // the same key twice, and Minecraft's own parser takes the last one it reads, so
                // rewriting the first would change the file and change nothing in the game.
                if (key != null) byKey.put(key, line);
            }
        }
        return new Options(lines, byKey, true);
    }

    /**
     * Write the options back, taking a backup first if there is not one already.
     *
     * The write goes to a temporary file in the same directory and is renamed into place, so a
     * process killed mid-write cannot leave someone with half an options file: either the old one
     * or the new one is there, never a truncated mixture.
     */
    public static void write(@NonNull File gameDir, @NonNull Options options) throws IOException {
        File file = fileIn(gameDir);
        File backup = backupIn(gameDir);
        if (options.existed && !backup.exists()) {
            copy(file, backup);
        }
        File temporary = new File(gameDir, FILE_NAME + ".tmp");
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(temporary), Charset.forName("UTF-8"))) {
            for (Line line : options.lines) {
                writer.write(line.raw);
                writer.write("\n");
            }
        }
        // delete() first because renameTo does not replace on every Android version.
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        if (!temporary.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw new IOException("Could not put options.txt in place");
        }
    }

    /**
     * Put the player's original file back and drop the backup.
     *
     * @return true when there was a backup to restore
     */
    public static boolean restore(@NonNull File gameDir) {
        File backup = backupIn(gameDir);
        if (!backup.isFile()) return false;
        try {
            copy(backup, fileIn(gameDir));
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Could not restore options.txt", e);
            return false;
        }
    }

    private static void copy(@NonNull File from, @NonNull File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }
}
