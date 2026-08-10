package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.Tools;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imported Turnip drivers: the folder they live in, the zips they arrive as, and which one the
 * game should load.
 *
 * <b>Why this exists.</b> The launcher already ships a Turnip build and loads it through a linker
 * namespace when the GPU is Adreno; that machinery is upstream's. What players actually do, on
 * the evidence of the community server, is chase newer Turnip builds from GitHub releases the way
 * emulator players do, by unzipping an APK and swapping the library inside. This gives that
 * practice a front door: import the zip, pick it in Settings, and the same loader loads it.
 *
 * <b>Where drivers live.</b> Under {@link Context#getFilesDir()}, in internal storage, and that
 * is not negotiable: the shared storage under {@code Android/data} is mounted noexec, so a
 * library there can never be dlopened. Internal storage is also why the import is a copy rather
 * than a reference to the picked file.
 *
 * <b>The format.</b> An adrenotools driver zip: a {@code meta.json} naming the library
 * ({@code libraryName}) plus the .so files beside it. That is the format every driver release in
 * the wild ships, because it is what Winlator and the emulators consume. Nothing else in the zip
 * is trusted: entry paths are canonicalised (this is the launcher's fourth archive from a
 * stranger), sizes are capped, and the named library must parse as an arm64 ELF before the folder
 * is accepted.
 *
 * <b>Failure is always the system driver.</b> The native loader already falls back when a driver
 * fails to load; resolve() falls back to the bundled Turnip when a chosen import has been
 * deleted. Neither path rewrites the preference, for the same reason the texture packs do not:
 * both processes cache the whole preference file, and the launcher would lose the write.
 */
public final class TurnipDrivers {
    private static final String TAG = "TurnipDrivers";

    /** Preference values. Anything else is "imported:" plus the folder name. */
    public static final String CHOICE_SYSTEM = "system";
    public static final String CHOICE_BUNDLED = "bundled";
    private static final String CHOICE_IMPORTED_PREFIX = "imported:";

    /** A driver zip is a few MB of compiled Mesa; a hundred is something else wearing one. */
    private static final long MAX_ZIP_BYTES = 100L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 100L * 1024 * 1024;

    private TurnipDrivers() {}

    /** One imported driver, as the picker lists it. */
    public static final class Driver {
        /** The folder under {@link #root}, which is also the stable identity in the preference. */
        public final String folder;
        /** The display name out of meta.json, falling back to the folder. */
        public final String name;
        /** The library file name the loader must dlopen. */
        public final String libraryName;

        Driver(String folder, String name, String libraryName) {
            this.folder = folder;
            this.name = name;
            this.libraryName = libraryName;
        }
    }

    /** What the game process should actually do, resolved from the preference. */
    public static final class Resolved {
        /** False for the system driver: no Turnip is loaded at all. */
        public final boolean loadTurnip;
        /** Absolute directory of an imported driver, or null for the bundled one. */
        @Nullable public final String directory;
        /** Library to dlopen, or null for the bundled default. */
        @Nullable public final String libraryName;

        Resolved(boolean loadTurnip, @Nullable String directory, @Nullable String libraryName) {
            this.loadTurnip = loadTurnip;
            this.directory = directory;
            this.libraryName = libraryName;
        }
    }

    public static String importedChoice(String folder) {
        return CHOICE_IMPORTED_PREFIX + folder;
    }

    @NonNull
    public static File root(@NonNull Context context) {
        return new File(context.getFilesDir(), "turnip_drivers");
    }

    /** The imported drivers on disk, in name order. A folder without a valid meta is skipped. */
    @NonNull
    public static List<Driver> list(@NonNull Context context) {
        List<Driver> drivers = new ArrayList<>();
        File[] folders = root(context).listFiles();
        if (folders == null) return drivers;
        for (File folder : folders) {
            Driver driver = read(folder);
            if (driver != null) drivers.add(driver);
        }
        // Name order, so the picker is stable across imports.
        //noinspection ComparatorCombinators - source 8
        java.util.Collections.sort(drivers, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return drivers;
    }

    @Nullable
    private static Driver read(@NonNull File folder) {
        if (!folder.isDirectory()) return null;
        File meta = new File(folder, "meta.json");
        if (!meta.isFile()) return null;
        try {
            JsonObject json = JsonParser.parseString(
                    Tools.read(meta.getAbsolutePath())).getAsJsonObject();
            String library = stringOf(json, "libraryName");
            if (library == null || !new File(folder, library).isFile()) return null;
            String name = stringOf(json, "name");
            return new Driver(folder.getName(),
                    name == null || name.isEmpty() ? folder.getName() : name, library);
        } catch (Throwable t) {
            Log.w(TAG, "Unreadable driver meta in " + folder.getName(), t);
            return null;
        }
    }

    @Nullable
    private static String stringOf(JsonObject json, String member) {
        return json.has(member) && json.get(member).isJsonPrimitive()
                ? json.get(member).getAsString() : null;
    }

    /**
     * Take a driver zip and make it a folder under {@link #root}.
     *
     * @return the imported driver
     * @throws IOException with a loggable reason when the zip is not a driver. The caller shows
     *                     its own translated message; nothing from in here reaches the screen.
     */
    @NonNull
    public static Driver importZip(@NonNull Context context, @NonNull InputStream source)
            throws IOException {
        File staging = new File(root(context), ".importing");
        FileUtils.deleteQuietly(staging);
        if (!staging.mkdirs()) throw new IOException("Could not create " + staging);
        boolean keep = false;
        try {
            long total = 0;
            byte[] buffer = new byte[65536];
            try (ZipInputStream zip = new ZipInputStream(source)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    // Canonicalised against the staging root: an entry that climbs out of the
                    // folder is an attack, not a driver, whatever else the zip contains.
                    File out = new File(staging, entry.getName());
                    String canonical = out.getCanonicalPath();
                    if (!canonical.startsWith(staging.getCanonicalPath() + File.separator)) {
                        throw new IOException("Entry escapes the archive: " + entry.getName());
                    }
                    File parent = out.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Could not create " + parent);
                    }
                    long written = 0;
                    try (FileOutputStream stream = new FileOutputStream(out)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            written += read;
                            total += read;
                            if (written > MAX_ENTRY_BYTES || total > MAX_ZIP_BYTES) {
                                throw new IOException("Driver zip too large");
                            }
                            stream.write(buffer, 0, read);
                        }
                    }
                }
            }

            // Some releases nest the driver one folder down; accept exactly that shape too.
            File base = new File(staging, "meta.json").isFile() ? staging : sole(staging);
            Driver driver = base == null ? null : read(base);
            if (driver == null) {
                throw new IOException("Not an adrenotools driver zip: no usable meta.json");
            }
            File library = new File(base, driver.libraryName);
            if (!isArm64Elf(library)) {
                throw new IOException(driver.libraryName + " is not an arm64 library");
            }

            // Named after the driver, so importing the same zip twice replaces it instead of
            // multiplying it. The name came out of a stranger's JSON, so it is reduced to a
            // file-safe form first.
            String folderName = safeFolder(driver.name);
            File target = new File(root(context), folderName);
            FileUtils.deleteQuietly(target);
            if (!base.renameTo(target)) throw new IOException("Could not place " + folderName);
            keep = true;
            Log.i(TAG, "Imported Turnip driver " + driver.name + " -> " + folderName);
            return new Driver(folderName, driver.name, driver.libraryName);
        } finally {
            if (!keep) FileUtils.deleteQuietly(staging);
            // When base == staging the rename moved it; a leftover shell is still cleaned here.
            if (staging.exists() && keep) FileUtils.deleteQuietly(staging);
        }
    }

    /** The single directory inside a folder, or null when it is not that shape. */
    @Nullable
    private static File sole(@NonNull File folder) {
        File[] children = folder.listFiles();
        if (children == null || children.length != 1 || !children[0].isDirectory()) return null;
        return new File(children[0], "meta.json").isFile() ? children[0] : null;
    }

    /** ELF magic and EM_AARCH64, read straight off the file. An x86 driver fails here, not in game. */
    private static boolean isArm64Elf(@NonNull File library) {
        if (!library.isFile()) return false;
        try (FileInputStream stream = new FileInputStream(library)) {
            byte[] header = new byte[20];
            int read = stream.read(header);
            if (read < 20) return false;
            boolean elf = header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
            // e_machine is a little-endian half at offset 18; EM_AARCH64 is 183.
            int machine = (header[18] & 0xFF) | ((header[19] & 0xFF) << 8);
            return elf && machine == 183;
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    private static String safeFolder(@NonNull String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._ +-]", "_").trim();
        if (safe.isEmpty() || ".".equals(safe) || "..".equals(safe)) safe = "driver";
        if (safe.length() > 64) safe = safe.substring(0, 64);
        return safe;
    }

    public static void delete(@NonNull Context context, @NonNull String folder) {
        // The folder name went through safeFolder on the way in, but the preference file is
        // writable by anything with the phone in hand, so it is not trusted on the way out either.
        if (folder.contains("/") || folder.contains("\\") || folder.startsWith(".")) return;
        FileUtils.deleteQuietly(new File(root(context), folder));
    }

    /**
     * Turn the stored choice into what the game process should do.
     *
     * A choice pointing at a deleted import falls back to the bundled Turnip with a log line and
     * nothing else. The preference is deliberately not corrected from here: this runs in the game
     * process, both processes cache the whole preference file, and the launcher would be the one
     * to lose the change (the texture packs learned this first).
     */
    @NonNull
    public static Resolved resolve(@NonNull Context context, @Nullable String choice) {
        if (CHOICE_SYSTEM.equals(choice)) return new Resolved(false, null, null);
        if (choice != null && choice.startsWith(CHOICE_IMPORTED_PREFIX)) {
            String folder = choice.substring(CHOICE_IMPORTED_PREFIX.length());
            Driver driver = read(new File(root(context), folder));
            if (driver != null) {
                return new Resolved(true,
                        new File(root(context), folder).getAbsolutePath(), driver.libraryName);
            }
            Log.w(TAG, "Chosen driver " + folder.toLowerCase(Locale.ROOT)
                    + " is gone, using the bundled Turnip");
        }
        return new Resolved(true, null, null);
    }
}
