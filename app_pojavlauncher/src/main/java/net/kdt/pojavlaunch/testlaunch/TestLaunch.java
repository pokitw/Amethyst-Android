package net.kdt.pojavlaunch.testlaunch;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.optimiser.GameOptions;
import net.kdt.pojavlaunch.optimiser.PerformancePlan;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * A real Minecraft, launched only to try a control layout in, and stripped down to suit that.
 *
 * <b>This is a launch, not a simulation.</b> The layout is pressed against the actual game, in a
 * world, with the real hotbar and the real inventory and real blocks to place, because that is
 * what was asked for and because a mock can never tell you how a button feels while you are
 * actually playing.
 *
 * <p>Everything about it is arranged to cost as little as possible.
 * <ul>
 *   <li><b>A superflat world, pre-made.</b> Shipped as a level.dat in assets and copied in, so
 *       there is no world creation screen to click through and almost nothing to generate: three
 *       layers, no structures, no lakes, no caves. The rules in it turn off mob spawning, weather,
 *       the daylight cycle and random ticks, which between them are most of what a server thread
 *       does when nothing is happening.</li>
 *   <li><b>Its own game directory.</b> Nothing here touches the player's worlds, mods or settings,
 *       so the optimised options.txt below cannot be mistaken for their own tuning.</li>
 *   <li><b>Minecraft's own settings turned down</b> to the floor of every one that matters.</li>
 *   <li><b>Opened straight into the world</b>, where the version allows it. See
 *       {@link #VERSION}.</li>
 * </ul>
 *
 * <p>The profile is written under a reserved key so it is found again rather than duplicated, and
 * it is an ordinary profile in every other way: it shows up in the profile list, it can be edited,
 * and deleting it costs nothing because this rebuilds it.
 */
public final class TestLaunch {

    private static final String TAG = "TestLaunch";

    /**
     * The Minecraft the test world runs, and the one real compromise in here.
     *
     * <b>1.20.1 is the oldest release that can open a world for you.</b> Minecraft gained
     * {@code --quickPlaySingleplayer} in 23w14a, and nothing before it has any supported way to
     * skip the title screen, so a lighter version would mean landing on the main menu and
     * clicking through Singleplayer with the virtual mouse every single time. Being dropped into
     * the world is most of what makes this quick enough to use as a loop, so it wins.
     *
     * <p>The cost is that 1.17 and up ask for OpenGL 3.2 core, which GL4ES cannot serve, so this
     * runs on MobileGlues rather than on the lightest translator available. The superflat world
     * and the settings below are what buy that back.
     */
    public static final String VERSION = "1.20.1";

    /** The key the profile is stored under, reserved so it is never confused with a real one. */
    public static final String PROFILE_KEY = "amethystx-control-test";

    /** Relative to the storage root, which is what {@code Tools.getGameDirPath} expects. */
    public static final String GAME_DIR = "controltest";

    /** The folder inside {@code saves}, which is also what quick play is given. */
    public static final String WORLD_FOLDER = "controltest";

    /**
     * Written into the game directory so the launch path can recognise it later.
     *
     * A file rather than a static flag, because the decision is needed in the <b>game</b> process,
     * where a static set by the launcher does not exist. Everything else that has to cross that
     * boundary here crosses it as a file or a preference for the same reason.
     */
    public static final String MARKER = ".amethystx-controltest";

    private TestLaunch() {}

    public static File gameDir() {
        return new File(Tools.DIR_GAME_HOME, GAME_DIR);
    }

    /** Whether a game directory is the control test's own, and may therefore be opened straight. */
    public static boolean isTestGameDir(@Nullable File dir) {
        return dir != null && new File(dir, MARKER).isFile();
    }

    /** Whether everything the launch needs is already on disk, so it will not have to download. */
    public static boolean isPrepared() {
        return new File(gameDir(), "saves/" + WORLD_FOLDER + "/level.dat").isFile()
                && new File(Tools.DIR_HOME_VERSION, VERSION + "/" + VERSION + ".json").isFile();
    }

    /**
     * Make the profile, the directory, the world and the settings, and select the profile.
     *
     * Safe to call again: everything is created only if it is missing, so a world that has been
     * built in survives, and so does anything the player changed in its settings afterwards.
     *
     * @return the profile that will launch
     */
    public static MinecraftProfile prepare(@NonNull Context context) throws IOException {
        File dir = gameDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create the test game directory at " + dir);
        }
        File marker = new File(dir, MARKER);
        if (!marker.isFile() && !marker.createNewFile()) {
            Log.w(TAG, "Could not write the marker; the world will not open by itself");
        }
        installWorld(context, dir);
        writeOptions(dir);
        MinecraftProfile profile = writeProfile(context);
        select();
        return profile;
    }

    /**
     * Put the superflat world in place, once.
     *
     * Never overwritten. The whole point of a test world is that you go into it repeatedly, and
     * somebody who has built something to test their buttons against would not thank us for
     * flattening it on the next launch.
     */
    private static void installWorld(@NonNull Context context, @NonNull File dir)
            throws IOException {
        File world = new File(dir, "saves/" + WORLD_FOLDER);
        File level = new File(world, "level.dat");
        if (level.isFile()) return;
        if (!world.isDirectory() && !world.mkdirs()) {
            throw new IOException("Could not create the test world folder at " + world);
        }
        Tools.copyAssetFile(context, "testworld/level.dat", world.getAbsolutePath(),
                "level.dat", false);
        // Checked rather than assumed, because the two ways this feature can fail look identical
        // from the outside: a world that was never written and a world Minecraft would not load
        // both end at the title screen. Making the first one loud is what leaves the second one
        // as the only remaining explanation.
        if (!level.isFile()) {
            throw new IOException("The test world was not written to " + level);
        }
    }

    /**
     * Minecraft's own settings, at the floor of everything that costs frames.
     *
     * These are set outright rather than bounded the way performance mode's are, and that is the
     * difference between the two features: performance mode is tuning somebody's real game and
     * must never undo their own choices, while this is a scratch directory whose only purpose is
     * to run as cheaply as possible. Nothing here can reach a world anybody cares about.
     *
     * <p>Written only when the file is absent, so a setting changed inside the test world stays
     * changed. Somebody who turns the render distance up in here meant to.
     */
    private static void writeOptions(@NonNull File dir) {
        try {
            GameOptions.Options options = GameOptions.read(dir);
            if (options.existed) return;
            for (Map.Entry<String, String> entry : TestOptions.values().entrySet()) {
                options.set(entry.getKey(), entry.getValue());
            }
            GameOptions.write(dir, options);
        } catch (IOException e) {
            // Not fatal: the game writes its own options.txt on first run, and the world is
            // superflat either way. A launch that works and is slower beats no launch.
            Log.w(TAG, "Could not write the test world's options.txt", e);
        }
    }

    /** Create or refresh the reserved profile, without disturbing anything the player edited. */
    private static MinecraftProfile writeProfile(@NonNull Context context) {
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(PROFILE_KEY);
        if (profile == null) {
            profile = new MinecraftProfile();
            profile.icon = null;
            LauncherProfiles.mainProfileJson.profiles.put(PROFILE_KEY, profile);
        }
        profile.name = "Control test";
        profile.lastVersionId = VERSION;
        profile.gameDir = GAME_DIR;
        // Only if it has none. A renderer chosen here once is the player's to change afterwards,
        // and this profile is visible in the editor like any other.
        if (profile.pojavRendererName == null || profile.pojavRendererName.isEmpty()) {
            profile.pojavRendererName = renderer(context);
        }
        LauncherProfiles.write();
        return profile;
    }

    /**
     * The translator this Minecraft needs, from what the device actually has.
     *
     * The same rule performance mode uses, and for the same reason: from 1.17 the game asks for
     * OpenGL 3.2 core, which GL4ES cannot serve at all. Zink is the fallback rather than the
     * choice, because it renders through OSMesa and cannot be recorded.
     */
    @Nullable
    private static String renderer(@NonNull Context context) {
        java.util.List<String> available = Tools.getCompatibleRenderers(context).rendererIds;
        if (available.contains(PerformancePlan.RENDERER_MOBILEGLUES)) {
            return PerformancePlan.RENDERER_MOBILEGLUES;
        }
        if (available.contains(PerformancePlan.RENDERER_ZINK_KOPPER)) {
            return PerformancePlan.RENDERER_ZINK_KOPPER;
        }
        return null;
    }

    /** Point the launcher at the test profile, which is what the launch path reads. */
    public static void select() {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, PROFILE_KEY)
                .apply();
    }
}
