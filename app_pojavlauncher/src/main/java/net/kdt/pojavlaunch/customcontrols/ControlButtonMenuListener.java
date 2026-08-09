package net.kdt.pojavlaunch.customcontrols;

/**
 * How an on-screen button reaches the game activity.
 *
 * The control center used to be the only thing behind a button, hence the name. It now also
 * carries the two actions worth having without a trip through that sheet: the launcher's own
 * keyboard, and dictation.
 */
public interface ControlButtonMenuListener {
    void onClickedMenu();

    /** Open the launcher's on-screen keyboard directly, without the control center. */
    void onClickedGameKeyboard();

    /**
     * Both edges of a press on a voice button.
     *
     * Both, rather than just the press, because a voice button is two controls in one: a tap
     * starts listening and a second tap stops it, while holding the button listens only for as
     * long as it is held. Only the receiver knows how long the finger was down, so only the
     * receiver can tell those apart — which is why the raw edges are what get passed on.
     *
     * @param down true on press, false on release
     */
    void onClickedVoice(boolean down);

    /**
     * Push-to-talk from an ordinary control button that was held rather than tapped.
     *
     * Separate from {@link #onClickedVoice} because there is no tap to tell apart here: the
     * player is holding their chat key, so the release is the only thing that can mean "done".
     *
     * @param down true when the hold has been recognised, false when the finger comes off
     */
    void onVoiceShortcut(boolean down);

    /** Capture the frame being presented. Raised on the press only, so it is not a frame late. */
    void onClickedScreenshot();
}
