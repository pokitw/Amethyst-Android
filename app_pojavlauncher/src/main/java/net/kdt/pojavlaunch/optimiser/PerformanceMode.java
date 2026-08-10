package net.kdt.pojavlaunch.optimiser;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.modloaders.modpacks.api.ModInstall;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthMods;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turning a {@link PerformancePlan} into writes, and taking them back.
 *
 * <b>Everything reversible is captured before anything is written.</b> The plan touches a dozen
 * settings across three different stores: the launcher's preferences, the profile's renderer, and
 * Minecraft's own options.txt. A switch that changes that much and cannot be switched off is not a
 * switch, so the first thing this does is record what was there, and the last thing it can do is
 * put it back.
 *
 * <b>The mods are the one thing that does not come back, on purpose.</b> They are jars in a folder
 * the player owns, listed on the Game files screen, and some of them keep configuration next to
 * them. Deleting somebody's files because a switch was turned off is a different and much larger
 * promise than restoring a setting, and getting it wrong is not recoverable. The screen says so
 * rather than the code guessing.
 *
 * <b>Nothing here may run while the game is running.</b> Minecraft reads options.txt once at
 * startup and writes its whole in-memory copy back on exit, so an edit made in between is
 * overwritten a moment later with no error anywhere. That is a launcher-side condition, and it is
 * the reason performance mode lives in Settings and not in the in-game control center.
 */
public final class PerformanceMode {
    private static final String TAG = "PerformanceMode";

    /** Whether the mode is on. Read by Settings; nothing in the game process asks. */
    public static final String PREF_ENABLED = "performanceMode";
    /** The capture, as JSON. Empty means there is nothing to go back to. */
    public static final String PREF_BACKUP = "performanceBackup";
    /**
     * The render distance the plan asked for, kept so the switch can say what it amounts to.
     *
     * Bookkeeping rather than a setting: the other two numbers on that line are preferences and
     * can be read live, but this one lives in Minecraft's file, and reading it back would mean an
     * options.txt parse on the main thread every time the screen resumed.
     */
    public static final String PREF_CHUNKS = "performanceChunks";

    /**
     * The reserved key the profile's renderer is captured under.
     *
     * Not a preference: the renderer lives in launcher_profiles.json, per profile, and must stay
     * there (handbook 14). It rides in the same capture because it is part of the same undo, and
     * the "@" makes it a name no SharedPreferences key of ours will ever collide with.
     */
    public static final String RENDERER_KEY = "@profileRenderer";

    private PerformanceMode() {}

    public static boolean isEnabled() {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        return preferences != null && preferences.getBoolean(PREF_ENABLED, false);
    }

    /** Whether there is a capture worth restoring, which is what makes "off" honest. */
    public static boolean canRestore() {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        if (preferences == null) return false;
        return PerformanceBackup.isUsable(preferences.getString(PREF_BACKUP, ""));
    }

    /** What an apply or a restore actually managed, in the terms the screen has to report. */
    public static final class Report {
        public int settingsChanged;
        public int optionsChanged;
        /** Names of the mods that arrived, for the sentence afterwards. */
        public final List<String> modsInstalled = new ArrayList<>();
        /** Names of the mods that did not, which is not the same as a failure to apply. */
        public final List<String> modsMissing = new ArrayList<>();
        /** Set when options.txt could not be written at all. */
        public boolean optionsFailed;
        /** Set when the profile had never been launched, so there was no options.txt to edit. */
        public boolean optionsAbsent;
    }

    // ------------------------------------------------------------------ applying

    /**
     * Write the launcher preferences, capturing what they were first.
     *
     * Runs on the main thread: these are a handful of SharedPreferences writes, and the reload
     * afterwards is the same one every settings row does. The renderer is passed in rather than
     * read here because {@link net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles} is
     * unsynchronised state shared with the launch path (handbook 12.7), and the caller is already
     * on the thread that may touch it.
     *
     * @param currentRenderer the profile's renderer as it is now, or null when it follows the
     *                        global default. Captured so the undo can put that back exactly,
     *                        including putting back "unset".
     */
    public static Report applyPreferences(@NonNull Context context, @NonNull PerformancePlan.Plan plan,
                                          @Nullable String currentRenderer) {
        Report report = new Report();
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        if (preferences == null) return report;

        Map<String, Object> before = new LinkedHashMap<>();
        for (PerformancePlan.Item item : plan.preferences) {
            // A key that was never written must come back as never written: several of these are
            // computed from the device on first run, and freezing a number into them would pin
            // the answer to whatever phone happened to be in hand (see PerformanceBackup).
            if (!preferences.contains(item.key)) {
                before.put(item.key, null);
                continue;
            }
            switch (item.type) {
                case BOOL: before.put(item.key, preferences.getBoolean(item.key, false)); break;
                case INT: before.put(item.key, preferences.getInt(item.key, 0)); break;
                default: before.put(item.key, preferences.getString(item.key, "")); break;
            }
        }
        before.put(RENDERER_KEY, currentRenderer);

        String[] keys = new String[plan.preferences.size() + 1];
        for (int i = 0; i < plan.preferences.size(); i++) keys[i] = plan.preferences.get(i).key;
        keys[keys.length - 1] = RENDERER_KEY;

        SharedPreferences.Editor editor = preferences.edit();
        for (PerformancePlan.Item item : plan.preferences) {
            switch (item.type) {
                case BOOL: editor.putBoolean(item.key, item.boolValue()); break;
                case INT: editor.putInt(item.key, item.intValue()); break;
                default: editor.putString(item.key, item.value); break;
            }
            report.settingsChanged++;
        }
        editor.putString(PREF_BACKUP, PerformanceBackup.capture(before, keys));
        editor.putInt(PREF_CHUNKS, plan.renderDistance);
        editor.putBoolean(PREF_ENABLED, true);
        editor.apply();
        LauncherPreferences.loadPreferences(context);
        return report;
    }

    /**
     * Write the plan's changes into Minecraft's own options.txt.
     *
     * Runs off the main thread. Every option is checked against what is there before it is
     * written: the plan states which direction is faster for each key, so a player who had
     * already turned something down does not get it turned back up by a mode that is supposed to
     * be doing the opposite.
     */
    public static void applyOptions(@NonNull File gameDir, @NonNull PerformancePlan.Plan plan,
                                    @NonNull Report report) {
        if (plan.options.isEmpty()) return;
        try {
            GameOptions.Options options = GameOptions.read(gameDir);
            if (!options.existed) {
                // No options.txt means the profile has never been launched. Writing the handful of
                // keys the plan owns is still correct: Minecraft fills in every key it does not
                // find, so the file it writes on first exit will carry these and its own defaults.
                report.optionsAbsent = true;
            }
            for (PerformancePlan.Item item : plan.options) {
                if (!PerformancePlan.allows(item, options.get(item.key))) continue;
                options.set(item.key, item.value);
                report.optionsChanged++;
            }
            if (report.optionsChanged > 0) GameOptions.write(gameDir, options);
        } catch (IOException e) {
            Log.w(TAG, "Could not write options.txt in " + gameDir, e);
            report.optionsFailed = true;
        }
    }

    /**
     * Fetch and install the plan's mods.
     *
     * Runs off the main thread and blocks. Each mod is independent: one that has no build for this
     * Minecraft version is reported and the rest go in, because five out of six is a real
     * improvement and refusing the lot over one missing jar would be worse for everybody. Required
     * dependencies come along through {@link ModInstall}, which is the same path the mod browser
     * uses, so Fabric API arrives with Sodium without being named here.
     *
     * @param folder    the profile's mods directory
     * @param mcVersion the version every build has to match
     * @param loaderId  the loader every build has to match
     */
    public static void installMods(@NonNull File folder, @NonNull PerformancePlan.Plan plan,
                                   @NonNull String mcVersion, @NonNull String loaderId,
                                   @NonNull Report report, @Nullable Progress progress) {
        List<String> existing = fileNamesIn(folder);
        int index = 0;
        for (PerformancePlan.Mod mod : plan.mods) {
            index++;
            if (progress != null) progress.onMod(mod.name, index, plan.mods.size());
            if (alreadyThere(existing, mod.slug)) {
                // Counted as installed, because from where the player is standing it is: the jar
                // is in the folder and the game will load it. Installing over the top would leave
                // two versions of one mod, which is a crash rather than a duplicate.
                report.modsInstalled.add(mod.name);
                continue;
            }
            ModrinthMods.File file = bestBuild(mod.slug, mcVersion, loaderId);
            if (file == null) {
                report.modsMissing.add(mod.name);
                continue;
            }
            ModInstall.Result result = ModInstall.install(folder, file, mcVersion, loaderId);
            if (result.ok()) report.modsInstalled.add(mod.name);
            else report.modsMissing.add(mod.name);
        }
    }

    /** Told how far through the mod list the install is, so the sheet can say so. */
    public interface Progress {
        void onMod(String name, int index, int total);
    }

    /**
     * The build to install, asking for the loader that was requested and then for Fabric.
     *
     * The fallback is Quilt's own advice: Quilt runs Fabric mods, and most of these publish a
     * Fabric build and no Quilt one. Asked only for "quilt", the plan would report five of six
     * mods missing on a Quilt profile that can run all six.
     */
    @Nullable
    private static ModrinthMods.File bestBuild(@NonNull String slug, @NonNull String mcVersion,
                                               @NonNull String loaderId) {
        List<ModrinthMods.File> files = ModrinthMods.versions(slug, mcVersion, loaderId);
        if (files.isEmpty() && loaderId.toLowerCase(Locale.ROOT).contains("quilt")) {
            files = ModrinthMods.versions(slug, mcVersion, "fabric");
        }
        return files.isEmpty() ? null : ModrinthMods.best(files);
    }

    /**
     * Whether a mod looks like it is already installed.
     *
     * A guess, and known to be one: the index does not say what a project's jar is called, so the
     * only evidence available without a request per mod is the slug appearing in a file name. A
     * false match means a mod that is not installed is skipped, which is visible and fixable from
     * the mod browser; a missed one means two jars, which is a crash. The guess is deliberately
     * the cautious way round.
     */
    private static boolean alreadyThere(@NonNull List<String> fileNames, @NonNull String slug) {
        String needle = slug.replace('-', ' ');
        for (String name : fileNames) {
            String flattened = name.replace('-', ' ').replace('_', ' ');
            if (flattened.contains(needle)) return true;
        }
        return false;
    }

    @NonNull
    private static List<String> fileNamesIn(@NonNull File folder) {
        List<String> names = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return names;
        for (File file : files) {
            if (file.isFile()) names.add(file.getName().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    // ------------------------------------------------------------------ undoing

    /**
     * Put the launcher preferences back.
     *
     * @return the renderer the profile had before, wrapped so that "there was no renderer" and
     *         "there is nothing to restore" are different answers. The caller applies it, because
     *         writing a profile is main thread work it is already doing.
     */
    @NonNull
    public static Restored restorePreferences(@NonNull Context context) {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        Restored restored = new Restored();
        if (preferences == null) return restored;
        Map<String, Object> values = PerformanceBackup.restore(preferences.getString(PREF_BACKUP, ""));
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (RENDERER_KEY.equals(key)) {
                restored.hasRenderer = true;
                restored.renderer = value == null ? null : String.valueOf(value);
                continue;
            }
            if (value == null) {
                // Never set before, so it goes back to never set rather than to a value that
                // merely looks like the default today.
                editor.remove(key);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else {
                editor.putString(key, String.valueOf(value));
            }
            restored.settings++;
        }
        editor.remove(PREF_BACKUP);
        editor.remove(PREF_CHUNKS);
        editor.putBoolean(PREF_ENABLED, false);
        editor.apply();
        LauncherPreferences.loadPreferences(context);
        return restored;
    }

    /** What a restore recovered. */
    public static final class Restored {
        public int settings;
        /** True when the capture carried a renderer, whether or not one was set. */
        public boolean hasRenderer;
        @Nullable public String renderer;
    }

    /** Put Minecraft's own options back. Runs off the main thread. */
    public static boolean restoreOptions(@NonNull File gameDir) {
        return GameOptions.restore(gameDir);
    }
}
