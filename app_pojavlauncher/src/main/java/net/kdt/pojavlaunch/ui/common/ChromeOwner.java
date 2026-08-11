package net.kdt.pojavlaunch.ui.common;

/**
 * A fragment that draws its own header, so the launcher's old chrome must get out of the way.
 *
 * <b>This replaces a hand-maintained list, and it replaces it because the list drifted.</b>
 * {@code LauncherActivity} decided by {@code instanceof MainMenuFragment || instanceof
 * SettingsFragment}, which was true when those were the only two Compose screens it hosted. Every
 * one added since drew an {@code AppScaffold} back bar and a large title <i>underneath</i> the
 * account spinner and the settings button, which is the old design sitting on top of the new one.
 * The profile editor, the profile type picker and the sign-in chooser were all like that, and
 * nothing said so, because a class name in an activity is not somewhere anybody looks when
 * writing a screen.
 *
 * Declared by the fragment instead, next to the thing that makes it true: a screen that calls
 * AppScaffold implements this, and the activity asks rather than remembers.
 *
 * <b>Hiding the account spinner does not switch it off.</b> It is the source of truth for who is
 * signed in and owns the Microsoft login listeners and the token refresh (handbook 10), and it
 * keeps all of that while GONE, which is exactly what the home screen has relied on since the
 * redesign started.
 */
public interface ChromeOwner {
    /** Whether this fragment draws its own header, including who is signed in if it shows that. */
    boolean drawsOwnHeader();

    /**
     * Whether it reports background work itself.
     *
     * Separate from the header on purpose: Settings hides the account bar but keeps the progress
     * bar, because a download started elsewhere has nowhere else to report from while it is open.
     * Only the home screen, which shows progress inside its own Play button, answers true.
     */
    boolean drawsOwnProgress();
}
