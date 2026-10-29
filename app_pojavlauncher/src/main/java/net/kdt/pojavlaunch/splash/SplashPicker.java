package net.kdt.pojavlaunch.splash;

import java.util.Random;

/**
 * Which splash line to show next, in an order that feels generous rather than random.
 *
 * A plain random pick is the obvious implementation and the wrong one: over a thirty second wait
 * with four lines shown, a player on a table of thirty will see a repeat often enough to notice,
 * and the one thing a rotating line must never do is look broken. So this is a shuffle bag, the
 * same device a music player shuffle uses. Every line is shown once before any is shown twice.
 *
 * <p><b>The seam between two bags is the whole reason this is a class.</b> The last line of one
 * order and the first of the next are independent draws, so one time in N the same line comes up
 * twice in a row, at exactly the moment the rotation restarts. That is corrected here, where it
 * can be checked, rather than by whoever is reading the table.
 *
 * <p>Plain Java with no Android imports, so {@code scripts/splashsim} can compile the shipped
 * class and drive it (§16.25). The seed is taken rather than read from the clock for the same
 * reason: a sequence nothing can reproduce is a sequence nothing can check.
 */
public final class SplashPicker {
    private final int[] mBag;
    private final Random mRandom;
    private int mCursor;
    private int mLast = -1;

    public SplashPicker(int count, long seed) {
        mBag = new int[Math.max(count, 0)];
        for (int i = 0; i < mBag.length; i++) mBag[i] = i;
        mRandom = new Random(seed);
        // Past the end, so the first next() shuffles rather than handing out the identity order.
        mCursor = mBag.length;
    }

    /** The next line to show, or -1 when there are none at all. */
    public int next() {
        if (mBag.length == 0) return -1;
        if (mCursor >= mBag.length) {
            shuffle();
            mCursor = 0;
        }
        mLast = mBag[mCursor++];
        return mLast;
    }

    private void shuffle() {
        for (int i = mBag.length - 1; i > 0; i--) {
            int j = mRandom.nextInt(i + 1);
            int swap = mBag[i];
            mBag[i] = mBag[j];
            mBag[j] = swap;
        }
        if (mLast >= 0 && mBag[0] == mLast) {
            int swap = mBag[0];
            mBag[0] = mBag[mBag.length - 1];
            mBag[mBag.length - 1] = swap;
        }
    }
}
