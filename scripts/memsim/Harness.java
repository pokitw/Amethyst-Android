package net.kdt.pojavlaunch.prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Sweep the shipped {@link HeapAdvice} across the whole device population.
 *
 * There is no device in CI, so this is the only thing that can tell a bound that nags every launch
 * from one that never fires at all. Both are silent: the first teaches people to tap through a real
 * warning, the second ships a feature that does nothing and looks fine forever.
 */
public class Harness {

    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) {
        sweep();
        defaultsNeverWarn();
        ceilingNeverWarns();
        fixesClearTheirOwnWarning();
        fixesOfferTheRightNumber();
        monotonic();
        itFiresSomewhere();
        grid();
        unknownReadings();
        edges();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String failure : FAILURES) System.out.println("FAIL: " + failure);
            System.out.println(FAILURES.size() + " FAILURES");
            System.exit(1);
        }
        System.out.println("all passed");
    }

    /** Real device memory sizes, in megabytes as ActivityManager reports them (never a round GB). */
    private static final int[] DEVICES = {
        // A reading that failed, then the phones people actually run this on.
        0, 512, 900, 1400, 1850, 2800, 3600, 3900, 5700, 7400, 11500, 15400, 23000
    };

    private static final boolean[] BITS = {false, true};

    /** How much of the device is free at the moment, as fractions, plus "could not be read". */
    private static final double[] FREE_FRACTIONS = {0.03, 0.10, 0.25, 0.40, 0.60, 0.85, 1.00};

    /**
     * The three invariants, at every device size rather than at the realistic ones.
     *
     * Every megabyte from nothing to 24 GB, because the relationships being checked are between
     * two staircases and a slope, and the only interesting points are the ones where they cross.
     * With today's constants they never do, so this passes trivially and removing either clamp in
     * {@code recommended} changes nothing at all. That is the honest state of it, and it is the
     * same shape as resizesim sweeping off-grid floors: the sweep is here so the day the tiering
     * or the headroom moves, a default above the ceiling fails a check instead of shipping.
     */
    private static void sweep() {
        for (int total = 0; total <= 24000; total++) {
            for (boolean is32Bit : BITS) {
                int ceiling = HeapAdvice.ceiling(total, is32Bit);
                int recommended = HeapAdvice.recommended(total, is32Bit);
                check(ceiling >= HeapAdvice.MINIMUM_MB,
                        "ceiling below the slider's own minimum at " + total + "/" + is32Bit);
                check(ceiling <= Math.max(512, total),
                        "ceiling above the device's total memory at " + total + "/" + is32Bit);
                check(recommended >= HeapAdvice.MINIMUM_MB,
                        "recommended below the minimum at " + total + "/" + is32Bit);
                check(recommended <= ceiling,
                        "the default (" + recommended + ") is above the ceiling (" + ceiling
                                + ") at " + total + "/" + is32Bit);
                check(recommended % HeapAdvice.STEP_MB == 0,
                        "the default is off the slider's grid at " + total + "/" + is32Bit);
            }
        }
    }

    /**
     * The assertion every fresh install depends on.
     *
     * If the launcher's own default trips the launch check, every new user is warned on their
     * first launch about a value the launcher chose for them, and the launcher is arguing with
     * itself. This is memsim's counterpart to viewportsim's "100% is byte-for-byte the full
     * screen": the case nobody configures and therefore everybody hits.
     */
    private static void defaultsNeverWarn() {
        for (int total : DEVICES) {
            for (boolean is32Bit : BITS) {
                int recommended = HeapAdvice.recommended(total, is32Bit);
                // Availability unknown, so only the device arm can speak. The availability arm is
                // legitimately allowed to fire on a default: it is about this moment, not this
                // device, and a phone with three other games open really has no memory free.
                HeapAdvice.Advice advice = HeapAdvice.advise(total, 0, recommended, is32Bit);
                check(advice.level == HeapAdvice.Level.OK,
                        "the launcher's own default warns on a " + total + " MB device"
                                + (is32Bit ? " (32-bit)" : "") + ": " + recommended + " gave "
                                + advice.level);
            }
        }
    }

    /** Settings must never offer a value the launch screen then refuses. */
    private static void ceilingNeverWarns() {
        for (int total : DEVICES) {
            for (boolean is32Bit : BITS) {
                int ceiling = HeapAdvice.ceiling(total, is32Bit);
                check(HeapAdvice.advise(total, 0, ceiling, is32Bit).level == HeapAdvice.Level.OK,
                        "the slider's own maximum warns at " + total + "/" + is32Bit);
                check(HeapAdvice.advise(total, 0, HeapAdvice.MINIMUM_MB, is32Bit).level
                                == HeapAdvice.Level.OK,
                        "the slider's own minimum warns at " + total + "/" + is32Bit);
            }
        }
    }

    /**
     * A one-tap fix that leaves the warning up is the most embarrassing bug available here, and
     * the easiest to write: the suggestion has to satisfy every arm, not the one it was raised by.
     */
    private static void fixesClearTheirOwnWarning() {
        forEveryCase(new Case() {
            @Override public void run(int total, int avail, int alloc, boolean is32Bit) {
                HeapAdvice.Advice advice = HeapAdvice.advise(total, avail, alloc, is32Bit);
                if (advice.level == HeapAdvice.Level.OK || advice.suggestedMb == 0) return;
                String where = total + "/" + avail + "/" + alloc + "/" + is32Bit;
                check(advice.suggestedMb < alloc, "the fix is not a reduction at " + where);
                check(advice.suggestedMb >= HeapAdvice.MINIMUM_MB,
                        "the fix is below the minimum at " + where);
                check(advice.suggestedMb % HeapAdvice.STEP_MB == 0,
                        "the fix is off the slider's grid at " + where);
                check(advice.suggestedMb <= HeapAdvice.ceiling(total, is32Bit),
                        "the fix is above the ceiling at " + where);
                HeapAdvice.Advice after =
                        HeapAdvice.advise(total, avail, advice.suggestedMb, is32Bit);
                check(after.level == HeapAdvice.Level.OK,
                        "the fix leaves " + after.level + " standing at " + where);
            }
        });
    }

    /** Asking for more can never turn a warning off. */
    private static void monotonic() {
        forEveryCase(new Case() {
            @Override public void run(int total, int avail, int alloc, boolean is32Bit) {
                if (HeapAdvice.advise(total, avail, alloc, is32Bit).level == HeapAdvice.Level.OK) {
                    return;
                }
                int higher = alloc + HeapAdvice.STEP_MB;
                check(HeapAdvice.advise(total, avail, higher, is32Bit).level != HeapAdvice.Level.OK,
                        "raising " + alloc + " to " + higher + " cleared a warning at "
                                + total + "/" + avail + "/" + is32Bit);
            }
        });
    }

    /**
     * A check that cannot fire is a feature that does nothing.
     *
     * Both arms are asserted separately, because a bug in either one alone would still leave the
     * other firing and a single "it warns sometimes" assertion would pass.
     */
    private static void itFiresSomewhere() {
        // Over the ceiling: an allocation kept from an older, looser bound, or a device that has
        // reported less memory than it did when the value was chosen.
        HeapAdvice.Advice over = HeapAdvice.advise(3600, 3000, 3000, false);
        check(over.level == HeapAdvice.Level.OVER_CEILING,
                "3000 MB on a 3.6 GB device did not read as over the ceiling");
        check(over.suggestedMb > 0, "no fix offered for an over-ceiling allocation");

        // Not enough free right now: the common one, and the only one most people will ever see.
        HeapAdvice.Advice low = HeapAdvice.advise(7400, 900, 2048, false);
        check(low.level == HeapAdvice.Level.LOW_MEMORY,
                "2 GB with 900 MB free did not read as low memory");
        check(low.suggestedMb > 0 && low.suggestedMb <= 900,
                "the low-memory fix does not fit in what is free: " + low.suggestedMb);

        // And the ordinary case stays quiet, which is the other half of the same claim.
        check(HeapAdvice.advise(7400, 4000, 2048, false).level == HeapAdvice.Level.OK,
                "2 GB with 4 GB free warned");
    }

    /**
     * Not merely that the fix works, but that it is the right number.
     *
     * Every value at or below the bound clears the warning, so "the fix clears its own warning"
     * passes for the ceiling, for the minimum, and for anything between. What somebody being told
     * their setting is too high actually wants is this device's own recommended value, and only a
     * direct assertion can tell that apart from the largest legal one.
     */
    private static void fixesOfferTheRightNumber() {
        for (int total : DEVICES) {
            for (boolean is32Bit : BITS) {
                int ceiling = HeapAdvice.ceiling(total, is32Bit);
                // Availability unknown, so nothing but the device itself constrains the answer.
                HeapAdvice.Advice advice = HeapAdvice.advise(total, 0, ceiling + 64, is32Bit);
                check(advice.level == HeapAdvice.Level.OVER_CEILING,
                        "past the ceiling did not read as over it at " + total + "/" + is32Bit);
                check(advice.suggestedMb == HeapAdvice.recommended(total, is32Bit),
                        "an over-ceiling warning offered " + advice.suggestedMb + " rather than "
                                + "this device's recommendation "
                                + HeapAdvice.recommended(total, is32Bit)
                                + " at " + total + "/" + is32Bit);

                // And with memory scarce, the recommendation is capped by what is actually free
                // rather than offered regardless.
                int scarce = HeapAdvice.MINIMUM_MB + 40;
                HeapAdvice.Advice tight =
                        HeapAdvice.advise(total, scarce, ceiling + 64, is32Bit);
                check(tight.suggestedMb <= scarce,
                        "an over-ceiling fix ignored what was free at " + total + "/" + is32Bit
                                + ": offered " + tight.suggestedMb + " with " + scarce + " free");
            }
        }
    }

    /** Every value this class produces has to be one the slider can actually represent. */
    private static void grid() {
        for (int mb = -32; mb <= 4096; mb++) {
            int snapped = HeapAdvice.snapDown(mb);
            check(snapped % HeapAdvice.STEP_MB == 0, "snapDown(" + mb + ") is off the grid");
            check(snapped <= Math.max(0, mb), "snapDown(" + mb + ") rounded up");
            check(mb < 0 || mb - snapped < HeapAdvice.STEP_MB,
                    "snapDown(" + mb + ") dropped a whole step");
        }
        for (int total : DEVICES) {
            for (boolean is32Bit : BITS) {
                check(HeapAdvice.recommended(total, is32Bit) % HeapAdvice.STEP_MB == 0,
                        "the default is off the slider's grid at " + total + "/" + is32Bit);
            }
        }
    }

    /**
     * A reading of zero is a failure, not a device with no memory free. Guessing from one would
     * warn on every launch on any device whose ActivityManager will not answer.
     */
    private static void unknownReadings() {
        for (int total : DEVICES) {
            for (boolean is32Bit : BITS) {
                int ceiling = HeapAdvice.ceiling(total, is32Bit);
                for (int avail : new int[]{0, -1, -4096}) {
                    check(HeapAdvice.advise(total, avail, ceiling, is32Bit).level
                                    == HeapAdvice.Level.OK,
                            "an unreadable free-memory value (" + avail + ") warned at " + total);
                    check(HeapAdvice.advise(total, avail, ceiling + 1, is32Bit).level
                                    == HeapAdvice.Level.OVER_CEILING,
                            "the device arm went quiet when free memory was unreadable at " + total);
                }
            }
        }
    }

    /**
     * The pair either side of each bound, in both directions.
     *
     * A one-sided sweep passes a comparison written the wrong way round, which is the lesson
     * gyrosim learned the hard way (16.22); every boundary here is asserted from both sides.
     */
    private static void edges() {
        int total = 7400;
        int ceiling = HeapAdvice.ceiling(total, false);
        check(HeapAdvice.advise(total, 0, ceiling, false).level == HeapAdvice.Level.OK,
                "exactly the ceiling warned");
        check(HeapAdvice.advise(total, 0, ceiling + 1, false).level == HeapAdvice.Level.OVER_CEILING,
                "one megabyte over the ceiling did not warn");

        check(HeapAdvice.advise(total, 2048, 2048, false).level == HeapAdvice.Level.OK,
                "an allocation exactly equal to free memory warned");
        check(HeapAdvice.advise(total, 2047, 2048, false).level == HeapAdvice.Level.LOW_MEMORY,
                "one megabyte short of the allocation did not warn");

        // 32-bit is held to 1 GB whatever the device claims, because address space runs out first.
        check(HeapAdvice.ceiling(23000, true) == 1024,
                "a 32-bit device was allowed past 1 GB: " + HeapAdvice.ceiling(23000, true));
        check(HeapAdvice.ceiling(23000, false) > 1024,
                "a 64-bit device was held to the 32-bit bound");

        // The floor exists so a device that will not report its memory still gets a usable slider
        // rather than one whose maximum is its minimum.
        check(HeapAdvice.ceiling(0, false) == 512,
                "an unreadable device size did not fall back to the floor");
        check(HeapAdvice.ceiling(0, false) > HeapAdvice.MINIMUM_MB,
                "the ceiling floor leaves the slider no range at all");
    }

    /* ------------------------------------------------------------------ plumbing */

    private interface Case {
        void run(int total, int avail, int alloc, boolean is32Bit);
    }

    /**
     * Every device, every free-memory reading, every allocation the slider can produce.
     *
     * The allocation range deliberately runs past the ceiling: values written by an older build,
     * or by a device that reported more memory at the time, are exactly the ones the device arm
     * exists to catch, and a sweep that stopped at today's ceiling would never reach it.
     */
    private static void forEveryCase(Case body) {
        for (int total : DEVICES) {
            for (boolean is32Bit : BITS) {
                int ceiling = HeapAdvice.ceiling(total, is32Bit);
                for (double fraction : FREE_FRACTIONS) {
                    int avail = (int) Math.round(Math.max(total, 512) * fraction);
                    for (int alloc = HeapAdvice.MINIMUM_MB;
                         alloc <= ceiling + 512;
                         alloc += HeapAdvice.STEP_MB) {
                        body.run(total, avail, alloc, is32Bit);
                    }
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
