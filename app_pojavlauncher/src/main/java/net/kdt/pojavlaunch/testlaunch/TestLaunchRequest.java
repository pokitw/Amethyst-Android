package net.kdt.pojavlaunch.testlaunch;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

/**
 * The state of a test launch, from the editor asking for it to the player coming back out.
 *
 * <b>Preferences, not a static, and that is forced rather than chosen:</b>
 * {@code ContextAwareDoneListener} kills the launcher process the moment the game activity is up
 * ("You should kill yourself, NOW!", in its own words), so anything this flow needs on the far
 * side of the game session has to be on disk. A static survives to the first hop (editor to
 * launcher, same process) and no further.
 *
 * <p>What crosses the gap is two facts: where we are in the flow, and <b>whose profile was
 * selected before the test borrowed the selection</b>. The launch path reads the current profile
 * from preferences everywhere (handbook 10), so the test has to become the current profile to
 * launch at all; without the restore, the player's Play button would keep pointing at
 * "Control test" for ever after, which is the kind of quiet theft a test feature must never
 * commit.
 *
 * <p>The states:
 * <ul>
 *   <li>{@link #STATE_NONE}: nothing in flight.</li>
 *   <li>{@link #STATE_REQUESTED}: the editor prepared everything, switched the selection to the
 *       test profile, and finished. The launcher consumes this on resume: cover up, wait out any
 *       running tasks, raise the ordinary launch event (12.2).</li>
 *   <li>{@link #STATE_LAUNCHING}: the launch event has been raised. The next launcher resume
 *       after this is the player coming back out of the game (or a launch that failed), so the
 *       selection is restored and the editor reopened, which completes the loop the feature is
 *       for: test, play, quit, tweak.</li>
 * </ul>
 *
 * <p>Writes use {@code commit()}, not {@code apply()}: the very next thing after a write here is
 * another process reading the same file, and an async write racing a cross-process read is a
 * launch of the wrong profile once a year, which is the worst kind of bug to be asked about.
 */
public final class TestLaunchRequest {

    public static final int STATE_NONE = 0;
    public static final int STATE_REQUESTED = 1;
    public static final int STATE_LAUNCHING = 2;

    private static final String PREF_STATE = "testLaunchState";
    private static final String PREF_PREVIOUS_PROFILE = "testLaunchPreviousProfile";

    private TestLaunchRequest() {}

    public static int state() {
        return LauncherPreferences.DEFAULT_PREF.getInt(PREF_STATE, STATE_NONE);
    }

    /**
     * The editor is done preparing: remember whose profile is selected, select the test one,
     * and hand over to the launcher.
     */
    @SuppressWarnings("ApplySharedPref")
    public static void begin() {
        String current = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        SharedPreferences.Editor editor = LauncherPreferences.DEFAULT_PREF.edit();
        // Not overwritten when the test profile is somehow already selected, or a test started
        // twice would remember "the test profile" as the thing to restore.
        if (!TestLaunch.PROFILE_KEY.equals(current)) {
            editor.putString(PREF_PREVIOUS_PROFILE, current);
        }
        editor.putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, TestLaunch.PROFILE_KEY);
        editor.putInt(PREF_STATE, STATE_REQUESTED);
        editor.commit();
    }

    /** The launch event is about to be raised; the next resume is the way back out. */
    @SuppressWarnings("ApplySharedPref")
    public static void markLaunching() {
        LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF_STATE, STATE_LAUNCHING).commit();
    }

    /**
     * The session is over, however it went: give the selection back and clear the state.
     *
     * The restored key is checked against the profiles that actually exist, because the player
     * may have deleted theirs while the test ran and {@code getCurrentProfile} throws on a
     * selection that points at nothing.
     */
    @SuppressWarnings("ApplySharedPref")
    public static void end() {
        String previous = LauncherPreferences.DEFAULT_PREF
                .getString(PREF_PREVIOUS_PROFILE, null);
        SharedPreferences.Editor editor = LauncherPreferences.DEFAULT_PREF.edit();
        if (previous != null && !previous.isEmpty() && profileExists(previous)) {
            editor.putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, previous);
        }
        editor.remove(PREF_PREVIOUS_PROFILE);
        editor.putInt(PREF_STATE, STATE_NONE);
        editor.commit();
    }

    private static boolean profileExists(@Nullable String key) {
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        return LauncherProfiles.mainProfileJson.profiles.containsKey(key);
    }
}
