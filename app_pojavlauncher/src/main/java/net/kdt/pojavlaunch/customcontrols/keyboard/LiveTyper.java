package net.kdt.pojavlaunch.customcontrols.keyboard;

/**
 * Keeps a text field showing the transcript as it is still being recognised.
 *
 * A speech recogniser does not hand over one sentence at the end. It hands over a guess after
 * roughly every word, and each guess may revise the ones before it — "wait for me" becomes "weight
 * for me" becomes "wait for me at spawn". Typing every guess in turn would leave all of them in
 * the chat box; typing only the last one would leave the player watching an empty box while they
 * talk, wondering whether anything is being heard at all.
 *
 * So this remembers what it has already typed, and for each new guess types only the difference:
 * backspace over the tail that changed, then send the new tail. The common prefix — almost always
 * everything but the last word or two — is left alone, which is what stops the field flickering.
 *
 * <p><b>It is a model of a text field it cannot see.</b> Nothing on the launcher side can read the
 * game's chat box, so {@link #typed()} is what this believes is in there, not what is. That belief
 * is only correct while the player leaves the field alone, which is the whole reason
 * {@link #forget()} exists: anything that could have desynchronised the two — the field closing,
 * the player typing, a dictation ending — has to abandon the model rather than keep correcting
 * against it and eat characters that were never ours.
 *
 * <p>Every method must be called from one thread. The characters go into the native input queue,
 * which is not safe for several writers.
 */
public class LiveTyper {

    /**
     * Roughly a chat line's worth. Long enough for anything a person says in one breath, short
     * enough that a rambling dictation cannot flood the input queue or overrun the chat box.
     */
    public static final int MAX_CHARS = 256;

    /** U+00A7, the prefix Minecraft reads as a colour code. */
    private static final char SECTION_SIGN = '\u00A7';

    private final CharacterSenderStrategy mSender;
    private String mTyped = "";

    public LiveTyper(CharacterSenderStrategy sender) {
        mSender = sender;
    }

    /**
     * Reduce what was heard to exactly the characters that can be sent.
     *
     * A speech recogniser returns arbitrary Unicode, and the bridge takes a single Java char
     * straight through as a code point, so three kinds of thing are dropped rather than sent
     * broken:
     *
     * <ul>
     *   <li>Anything above U+FFFF — emoji, rarer CJK — would arrive as two lone surrogates.</li>
     *   <li>ISO control characters. A recogniser that returns a line break would otherwise push a
     *       raw newline at a chat box.</li>
     *   <li>The section sign, because it opens Minecraft's colour escapes and dictated text has no
     *       business writing them.</li>
     * </ul>
     *
     * Filtering happens here, before anything is compared, so that {@link #set} is always diffing
     * two strings that went through the same rules.
     *
     * @param text what was heard, or null
     * @return the sendable characters, never null
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(Math.min(text.length(), MAX_CHARS));
        for (int i = 0; i < text.length() && out.length() < MAX_CHARS; ) {
            int codePoint = text.codePointAt(i);
            int width = Character.charCount(codePoint);
            i += width;
            // Two chars wide means it needs a surrogate pair, and the bridge has room for one.
            if (width != 1) continue;
            char c = (char) codePoint;
            if (Character.isISOControl(c) || c == SECTION_SIGN) continue;
            out.append(c);
        }
        return out.toString();
    }

    /** What this believes it has put into the game's text field. */
    public String typed() {
        return mTyped;
    }

    /**
     * Make the field read {@code text}, sending only what changed.
     *
     * @return the sanitised text now believed to be in the field
     */
    public String set(String text) {
        String wanted = sanitize(text);
        if (wanted.equals(mTyped)) return mTyped;

        int shared = 0;
        int limit = Math.min(wanted.length(), mTyped.length());
        while (shared < limit && wanted.charAt(shared) == mTyped.charAt(shared)) shared++;

        for (int i = mTyped.length(); i > shared; i--) mSender.sendBackspace();
        for (int i = shared; i < wanted.length(); i++) mSender.sendChar(wanted.charAt(i));

        mTyped = wanted;
        return mTyped;
    }

    /** Take back everything typed so far, leaving the field as it was found. */
    public void clear() {
        for (int i = mTyped.length(); i > 0; i--) mSender.sendBackspace();
        mTyped = "";
    }

    /**
     * Stop tracking, leaving the text where it is.
     *
     * Used once a dictation is over: the words belong to the player now, and a later dictation
     * must not backspace over something they have since edited themselves.
     */
    public void forget() {
        mTyped = "";
    }
}
