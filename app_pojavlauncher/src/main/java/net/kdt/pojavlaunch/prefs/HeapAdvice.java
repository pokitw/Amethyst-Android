package net.kdt.pojavlaunch.prefs;

/**
 * How much memory Minecraft should be given on this device, and whether the current setting will
 * survive being asked for.
 *
 * <b>The launcher already had this check, in the one place it could not act on it.</b>
 * {@code Tools.launchMinecraft} compares the allocation against free memory at the top of the
 * launch, which on a desktop is a mild annoyance and on a phone is three separate problems: it runs
 * after the whole version download has finished, which on mobile data costs real money; it runs in
 * the game process, by which point the launcher has already been killed, so the settings it points
 * at are not reachable; and it is an OK-only dialog that then launches anyway, so being told
 * changes nothing. Moving the same question in front of the Play button fixes all three, and lets
 * the answer carry a fix rather than a number.
 *
 * <p><b>Three numbers, one definition each.</b> The ceiling the memory slider enforces, the default
 * a fresh install gets, and the bound the launch check tests against were three separate pieces of
 * arithmetic in three files, and nothing made them agree. They are one file now, which is the point
 * of it: a launch screen that refuses a value Settings offers is a launcher arguing with itself,
 * and that is not a bug anybody would think to look for.
 *
 * <p><b>The availability arm is deliberately the shipped threshold and not a better one.</b> The
 * heap is committed up front ({@code -Xms} equals {@code -Xmx} in {@code JREUtils}), so the honest
 * statement is "you are asking for more than the device says is free", which needs no invented
 * constant for the game's native overhead. Moving the question earlier and attaching a fix is the
 * improvement; changing the threshold in the same breath would be changing two things at once with
 * nothing to check the new one against.
 *
 * <p><b>The address space bound stays where it is.</b> {@code getMaxContinuousAddressSpaceSize}
 * parses {@code /proc/self/maps}, so it describes the process that asks. The launcher's map is not
 * the game's, and an answer from the wrong process is worse than no answer, so that arm stays in
 * {@code Tools.launchMinecraft} where the process being measured is the one about to start a JVM.
 *
 * <p>Plain Java with no Android types in it, so {@code scripts/memsim} can compile this exact file
 * and sweep it across the whole device population. Getting this wrong is silent in both directions:
 * a bound that is too tight nags every launch until people tap through it without reading, and one
 * that is too loose is a feature that never fires and looks fine forever.
 */
public final class HeapAdvice {

    private HeapAdvice() {}

    /** The smallest heap the slider offers. Below this Minecraft will not reach a title screen. */
    public static final int MINIMUM_MB = 256;

    /** The slider's granularity. Any value this class suggests lands on it. */
    public static final int STEP_MB = 8;

    /**
     * The smallest ceiling worth reporting.
     *
     * A device claiming less than this has not reported its memory, it has failed to; the reading
     * comes from {@code ActivityManager.MemoryInfo} and zero is what a failure looks like. A
     * slider whose maximum equals its minimum is worse than a bound that is slightly wrong, so the
     * floor keeps a usable control in front of anybody whose device will not answer.
     */
    private static final int CEILING_FLOOR_MB = 512;

    /** What is wrong, if anything, with asking for this much. */
    public enum Level {
        /** Nothing. Launch. */
        OK,
        /** More than this device should ever be asked for, whatever else is running. */
        OVER_CEILING,
        /** Fine in principle, but more than is free at this moment. */
        LOW_MEMORY
    }

    /** A verdict and, where one exists, the value that would clear it. */
    public static final class Advice {
        public final Level level;
        /** A lower allocation that clears {@link #level}, or zero when there is nothing to offer. */
        public final int suggestedMb;
        /** The ceiling used to reach the verdict, so a message can quote it. */
        public final int ceilingMb;

        Advice(Level level, int suggestedMb, int ceilingMb) {
            this.level = level;
            this.suggestedMb = suggestedMb;
            this.ceilingMb = ceilingMb;
        }
    }

    /**
     * The most memory this device should ever hand Minecraft.
     *
     * The headroom left behind is the system's: Android is still running, and a phone that gives
     * everything to one process does not get a faster game, it gets a game the low memory killer
     * takes away. 32-bit devices are held much harder because their address space, not their RAM,
     * is what runs out first.
     *
     * @param totalMb the device's total memory in megabytes, or zero if it could not be read
     * @param is32Bit whether this is a 32-bit device
     */
    public static int ceiling(int totalMb, boolean is32Bit) {
        int total = Math.max(0, totalMb);
        int ceiling;
        if (is32Bit || total < 2048) {
            ceiling = Math.min(1024, total);
        } else {
            ceiling = total - (total < 3064 ? 800 : 1024);
        }
        return Math.max(CEILING_FLOOR_MB, ceiling);
    }

    /**
     * What a fresh install should be given on this device.
     *
     * The tiering is the launcher's own and is kept to the megabyte: too little and Minecraft
     * stutters and dies, too much and the garbage collector spends its time in a heap Android
     * cannot afford to leave resident. Clamped to {@link #ceiling} so the default can never be a
     * value the launch check would then refuse, which is the one way this class could end up
     * arguing with itself.
     */
    public static int recommended(int totalMb, boolean is32Bit) {
        int total = Math.max(0, totalMb);
        int tier;
        if (total < 1024) tier = 296;
        else if (total < 1536) tier = 448;
        else if (total < 2048) tier = 656;
        else if (is32Bit) tier = 696;
        else if (total < 3064) tier = 936;
        else if (total < 4096) tier = 1144;
        else if (total < 6144) tier = 1536;
        else tier = 2048;
        // Clamped to the ceiling so the default can never be a value the launch check would then
        // refuse, which is the one way this class could end up arguing with itself, and snapped
        // afterwards because the ceiling is not itself on the slider's grid. Today no tier reaches
        // its ceiling, so neither step changes a number; they are here so that stays true the day
        // the tiering or the headroom moves, and scripts/memsim sweeps every device size for it.
        return snapDown(Math.min(tier, ceiling(total, is32Bit)));
    }

    /**
     * Whether this allocation is worth stopping the player for, and what to offer instead.
     *
     * @param totalMb      total device memory, zero if unknown
     * @param availableMb  memory free right now, zero or less if unknown, in which case the
     *                     availability arm is skipped entirely rather than guessed at
     * @param allocationMb what the game is currently set to take
     * @param is32Bit      whether this is a 32-bit device
     */
    public static Advice advise(int totalMb, int availableMb, int allocationMb, boolean is32Bit) {
        int ceiling = ceiling(totalMb, is32Bit);

        Level level;
        if (allocationMb > ceiling) level = Level.OVER_CEILING;
        else if (availableMb > 0 && allocationMb > availableMb) level = Level.LOW_MEMORY;
        else return new Advice(Level.OK, 0, ceiling);

        // The offer has to satisfy every arm at once, or the fix for one warning raises the other
        // and the dialog comes straight back. An over-ceiling allocation is answered with this
        // device's own recommended value rather than merely with the ceiling, because somebody
        // being told their setting is too high wants the right number and not the largest legal
        // one. Then the availability bound applies to both, and snapping down onto the slider's
        // grid cannot break either: every bound here is an upper bound and snapping only lowers.
        int fit = level == Level.OVER_CEILING ? recommended(totalMb, is32Bit) : ceiling;
        if (availableMb > 0) fit = Math.min(fit, availableMb);

        // Always a genuine reduction, and never by construction rather than by a guard: an
        // over-ceiling allocation is above the ceiling and the offer is at or below it, and a
        // low-memory allocation is above what is free and the offer is at or below that.
        int suggested = snapDown(fit);
        // Nothing worth offering when every value that would fit is too small to reach a title
        // screen on. The dialog then says what is wrong and offers only to launch anyway.
        if (suggested < MINIMUM_MB) suggested = 0;

        return new Advice(level, suggested, ceiling);
    }

    /** The largest multiple of {@link #STEP_MB} that is no greater than {@code megabytes}. */
    public static int snapDown(int megabytes) {
        if (megabytes <= 0) return 0;
        return (megabytes / STEP_MB) * STEP_MB;
    }
}
