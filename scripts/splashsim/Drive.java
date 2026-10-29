import net.kdt.pojavlaunch.splash.SplashPicker;

import java.util.HashSet;
import java.util.Set;

/**
 * Drives the shipped SplashPicker. The property worth checking is not "it returns an index":
 * it is that a player watching a loading screen never sees the same line twice running, which
 * is decided at the seam between two bags and is invisible in a reading of either half.
 */
public class Drive {
    private static int failures = 0;

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAIL " + what);
            failures++;
        }
    }

    /**
     * Different launches open on different lines.
     *
     * A bag that is handed out before it is first shuffled is still a permutation and still
     * never repeats, so every other check here passes while every cold start opens on the same
     * line. That is the whole of what a player would notice.
     */
    private static void theFirstLineVariesByLaunch() {
        Set<Integer> firsts = new HashSet<>();
        for (long seed = 0; seed < 60; seed++) firsts.add(new SplashPicker(20, seed).next());
        check(firsts.size() > 5, "every launch opened on the same few lines: " + firsts);
    }

    public static void main(String[] args) {
        everyLineBeforeAnyRepeat();
        neverTwiceInARow();
        emptyTableIsNotACrash();
        oneLineIsAlwaysThatLine();
        aSeedIsReproducible();
        twoLinesAlternate();
        theFirstLineVariesByLaunch();
        System.out.println(failures == 0 ? "ok: splashsim" : ("FAILED: " + failures));
        if (failures != 0) System.exit(1);
    }

    /** Every index is handed out once before any is handed out twice. */
    private static void everyLineBeforeAnyRepeat() {
        for (int count = 2; count <= 40; count++) {
            for (long seed = 0; seed < 40; seed++) {
                SplashPicker picker = new SplashPicker(count, seed);
                for (int bag = 0; bag < 5; bag++) {
                    Set<Integer> seen = new HashSet<>();
                    for (int i = 0; i < count; i++) seen.add(picker.next());
                    check(seen.size() == count,
                            "bag of " + count + " seed " + seed + " held " + seen.size());
                }
            }
        }
    }

    /**
     * The seam. A bag is internally fine by construction, so the only place a repeat can happen
     * is between the last of one order and the first of the next, and it happens about one time
     * in N, which is often enough to be seen and rare enough to survive a casual test.
     */
    private static void neverTwiceInARow() {
        for (int count = 2; count <= 40; count++) {
            for (long seed = 0; seed < 60; seed++) {
                SplashPicker picker = new SplashPicker(count, seed);
                int previous = -1;
                for (int i = 0; i < count * 8; i++) {
                    int next = picker.next();
                    check(next != previous,
                            "repeat " + next + " at " + i + " of " + count + " seed " + seed);
                    previous = next;
                }
            }
        }
    }

    private static void emptyTableIsNotACrash() {
        SplashPicker picker = new SplashPicker(0, 1L);
        for (int i = 0; i < 10; i++) check(picker.next() == -1, "empty table returned a line");
        SplashPicker negative = new SplashPicker(-3, 1L);
        check(negative.next() == -1, "negative count returned a line");
    }

    private static void oneLineIsAlwaysThatLine() {
        SplashPicker picker = new SplashPicker(1, 7L);
        for (int i = 0; i < 10; i++) check(picker.next() == 0, "single table wandered");
    }

    private static void aSeedIsReproducible() {
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        SplashPicker a = new SplashPicker(12, 99L);
        SplashPicker b = new SplashPicker(12, 99L);
        for (int i = 0; i < 50; i++) {
            first.append(a.next()).append(',');
            second.append(b.next()).append(',');
        }
        check(first.toString().equals(second.toString()), "one seed gave two sequences");
    }

    /**
     * Two lines is the smallest table where the seam exists, and it is the case the seam fix
     * has to turn into a strict alternation rather than merely improve.
     */
    private static void twoLinesAlternate() {
        for (long seed = 0; seed < 50; seed++) {
            SplashPicker picker = new SplashPicker(2, seed);
            int previous = picker.next();
            for (int i = 0; i < 30; i++) {
                int next = picker.next();
                check(next != previous, "two line table repeated at seed " + seed);
                previous = next;
            }
        }
    }
}
