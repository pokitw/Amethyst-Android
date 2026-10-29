package net.kdt.pojavlaunch.customcontrols;

/**
 * Pages: several sets of on-screen controls in one layout, one of them showing at a time.
 *
 * A layout that can do everything covers the screen, and a layout that leaves the screen clear
 * cannot do everything. Pages are the way out of that: building on one, bridging on another,
 * PvP on a third, and a button that moves between them.
 *
 * <p><b>A page is a filter on the controls, not a second layout.</b> The alternative, a list of
 * whole layouts, duplicates the furniture that belongs on every page (the stick, the menu, the
 * page buttons themselves) and then lets the copies drift apart the first time somebody drags
 * one. So membership is a bitmask on the control, in the same spirit as {@code displayInMenu},
 * and the default of <b>0 meaning every page</b> is what makes every layout ever saved come
 * back behaving exactly as it did.
 *
 * <p>Plain Java with no Android imports: the rules here decide what is on the screen, and a
 * control that is wrongly hidden is a button nobody can press, so {@code scripts/pagesim}
 * compiles this class and drives it (§16.25).
 */
public final class ControlPages {

    /**
     * Eight, which is a limit on the editor rather than on the int.
     *
     * A mask has room for thirty two, but a row of chips somebody picks from with a thumb does
     * not, and nobody has eight distinct sets of controls in mind, let alone thirty two.
     */
    public static final int MAX_PAGES = 8;

    /** Every page, and the value an older layout deserialises to. */
    public static final int ALL_PAGES = 0;

    /** {@link ControlData#targetPage} for a button that moves to the next page in order. */
    public static final int TARGET_CYCLE = 0;

    private ControlPages() {}

    /** The pages a layout actually has, bounded, with a single unnamed page as the floor. */
    public static int pageCount(int declared) {
        if (declared < 1) return 1;
        return Math.min(declared, MAX_PAGES);
    }

    /** Every page of a layout this big, as a mask. */
    public static int fullMask(int pageCount) {
        int pages = pageCount(pageCount);
        return (1 << pages) - 1;
    }

    /**
     * Is a control with this mask drawn on this page?
     *
     * <p>Out of range answers <b>yes</b>, deliberately. Everything here fails towards showing a
     * control rather than hiding one, because the two failures are not equally bad: a control
     * that appears where it was not wanted is clutter somebody can see and fix, and one that is
     * wrongly hidden is a button that has silently stopped existing in the middle of a game.
     */
    public static boolean onPage(int mask, int page) {
        if (mask == ALL_PAGES) return true;
        if (page < 0 || page >= MAX_PAGES) return true;
        return (mask & (1 << page)) != 0;
    }

    /**
     * The whole of when a control is drawn, in one expression.
     *
     * <p><b>Pages apply while the cursor is grabbed and never while a GUI is open.</b> The
     * ungrabbed state already shows a deliberately minimal set, and {@code check_layouts.py}
     * enforces a floor of menu, keyboard and mouse there precisely so that a layout cannot
     * strand the player in a chat box with no way out (§17). Filtering that set by page as well
     * would be a second, invisible way to reach exactly the state that floor exists to prevent,
     * and it would buy nothing: a page is about what your thumbs need while you are playing.
     */
    public static boolean visible(int mask, boolean displayInGame, boolean displayInMenu,
                                  boolean grabbing, int page) {
        if (!grabbing) return displayInMenu;
        return displayInGame && onPage(mask, page);
    }

    /**
     * Where a page button lands.
     *
     * {@link #TARGET_CYCLE} moves to the next page in order and wraps, which is what a button
     * does before anybody has configured it; 1 and up name a page, one based, so that the zero
     * an absent field deserialises to is the useful default rather than "always page one".
     *
     * <p>A button pointing at a page that has since been deleted <b>stays where it is</b>. The
     * alternative is to clamp it to the last page, which sends somebody somewhere they did not
     * ask for; a button that does nothing is the lesser failure, and the layout report names it
     * rather than leaving it to be discovered.
     */
    public static int resolveTarget(int targetPage, int current, int pageCount) {
        int pages = pageCount(pageCount);
        if (pages <= 1) return 0;
        int from = (current < 0 || current >= pages) ? 0 : current;
        if (targetPage <= TARGET_CYCLE) return (from + 1) % pages;
        int wanted = targetPage - 1;
        if (wanted >= pages) return from;
        return wanted;
    }

    /**
     * Tick or untick one page on a control, keeping the mask canonical.
     *
     * Two rules hold this together. A mask covering every page collapses back to
     * {@link #ALL_PAGES}, so a layout never carries two spellings of the same thing and a
     * control the player ticked everywhere still appears on a page created later, which is what
     * "every page" meant when they said it. And the <b>last page cannot be unticked</b>: a
     * control on no page at all is drawn nowhere, which is indistinguishable from a deleted
     * button and is not something anybody means to ask for.
     */
    public static int withPage(int mask, int page, boolean on, int pageCount) {
        int pages = pageCount(pageCount);
        if (page < 0 || page >= pages) return mask;
        int full = fullMask(pages);
        int resolved = (mask == ALL_PAGES) ? full : (mask & full);
        if (on) resolved |= (1 << page);
        else resolved &= ~(1 << page);
        if (resolved == 0) return mask;
        return (resolved == full) ? ALL_PAGES : resolved;
    }

    /**
     * A control's mask after one page has been taken out of the layout.
     *
     * Deleting page two must not quietly move every control that was on page three onto page
     * two's buttons, which is the "appended, never inserted" hazard (§14) wearing different
     * clothes: the bits above the hole shift down so that each one keeps naming the page it
     * always named.
     *
     * <p>A control that lived <b>only</b> on the deleted page lands on whichever page took its
     * place, or on the last one if it was the last. It is not left on no page and it is not
     * promoted to all of them: the first makes a button disappear with no way to find it again,
     * and the second drops it onto every page somebody had carefully kept it off.
     */
    public static int afterPageRemoved(int mask, int removed, int pageCountBefore) {
        int before = pageCount(pageCountBefore);
        if (before <= 1 || removed < 0 || removed >= before) return mask;
        if (mask == ALL_PAGES) return ALL_PAGES;
        int after = before - 1;
        int moved = 0;
        for (int page = 0; page < before; page++) {
            if (page == removed) continue;
            if ((mask & (1 << page)) == 0) continue;
            moved |= 1 << (page > removed ? page - 1 : page);
        }
        if (moved == 0) moved = 1 << Math.min(removed, after - 1);
        return (moved == fullMask(after)) ? ALL_PAGES : moved;
    }

    /**
     * A page button's target after one page has been taken out.
     *
     * Targets above the hole shift down so they keep meaning the same page. A button pointing
     * at the page that was deleted becomes a cycle button rather than silently pointing at
     * whatever slid into the slot, because a control that quietly starts doing something else
     * is worse than one that does the obvious thing.
     */
    public static int targetAfterPageRemoved(int targetPage, int removed) {
        if (targetPage <= TARGET_CYCLE) return TARGET_CYCLE;
        int page = targetPage - 1;
        if (page == removed) return TARGET_CYCLE;
        return page > removed ? targetPage - 1 : targetPage;
    }

    /** How many of a layout's pages this control is on, for a one line summary. */
    public static int pagesOn(int mask, int pageCount) {
        int pages = pageCount(pageCount);
        if (mask == ALL_PAGES) return pages;
        return Integer.bitCount(mask & fullMask(pages));
    }

    /**
     * Which pages have no button that leads anywhere else.
     *
     * Advice rather than a refusal: the control center carries a page picker whenever a layout
     * has pages, so no page is ever truly a dead end and nobody can be locked out of their game
     * by one. What it costs is a trip through a sheet, which is exactly the thing a page button
     * exists to avoid, so it is worth saying.
     *
     * @param masks      one per control
     * @param targets    one per control: its {@code targetPage} if it is a page switch, or
     *                   {@link Integer#MIN_VALUE} if it is anything else
     * @param inGame     one per control, its {@code displayInGame}
     */
    public static boolean[] deadEndPages(int[] masks, int[] targets, boolean[] inGame, int pageCount) {
        int pages = pageCount(pageCount);
        boolean[] stuck = new boolean[pages];
        if (pages <= 1) return stuck;
        for (int page = 0; page < pages; page++) {
            boolean exit = false;
            for (int i = 0; i < masks.length && !exit; i++) {
                if (targets[i] == Integer.MIN_VALUE) continue;
                if (!inGame[i]) continue;
                if (!onPage(masks[i], page)) continue;
                // A button that resolves to the page it is already on is not a way off it, and
                // nor is one pointing at a page that no longer exists.
                if (resolveTarget(targets[i], page, pages) != page) exit = true;
            }
            stuck[page] = !exit;
        }
        return stuck;
    }
}
