import net.kdt.pojavlaunch.customcontrols.ControlPages;

/**
 * Drives the shipped ControlPages. What it is really checking is that no arrangement of pages
 * can take a control off the screen that the player did not take off the screen themselves.
 */
public class Drive {
    private static int failures = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.out.println("FAIL " + what); failures++; }
    }

    public static void main(String[] args) {
        anOldLayoutIsUntouched();
        theMenuSetIsFilteredByPageToo();
        tickingEveryPageIsTheSameAsAll();
        theLastPageCannotBeUnticked();
        aTickSurvivesARoundTrip();
        cyclingVisitsEveryPageAndReturns();
        aTargetNamesItsPage();
        aTargetPastTheEndStaysPut();
        deadEndsAreFound();
        aCycleButtonIsNeverADeadEnd();
        aSelfTargetIsADeadEnd();
        everyControlIsReachableOnSomePage();
        outOfRangeShowsRatherThanHides();
        removingAPageKeepsEveryoneSomewhere();
        removingAPageShiftsTheOnesAboveIt();
        anOrphanLandsOnOnePageAndNotAllOfThem();
        removingAPageRepointsTargets();
        System.out.println(failures == 0 ? "ok: pagesim" : ("FAILED: " + failures));
        if (failures != 0) System.exit(1);
    }

    /**
     * The one that matters most. Every layout anybody has ever saved has no page field at all,
     * so every control deserialises to mask 0, and every one of them has to behave exactly as
     * it did before pages existed, on every page, at every page count.
     */
    private static void anOldLayoutIsUntouched() {
        for (int pages = 1; pages <= ControlPages.MAX_PAGES; pages++) {
            for (int page = 0; page < pages; page++) {
                check(ControlPages.visible(0, true, true, true, page),
                        "an old in-game control vanished on page " + page);
                check(ControlPages.visible(0, true, true, false, page),
                        "an old menu control vanished on page " + page);
                check(!ControlPages.visible(0, false, false, true, page),
                        "a control marked for neither state appeared");
            }
        }
    }

    /**
     * Pages apply in both states.
     *
     * The first version filtered only the grabbed one, and the consequence was the bug that got
     * reported: four pages each carrying their own menu controls meant opening a chest showed
     * the union of all four. This asserts the correction in the direction that broke, which is
     * that an ungrabbed control off the current page is not drawn.
     */
    private static void theMenuSetIsFilteredByPageToo() {
        int onPageZeroOnly = onlyPage(0, 4);
        check(ControlPages.visible(onPageZeroOnly, true, true, false, 0),
                "a menu control on the current page was hidden");
        for (int page = 1; page < 4; page++) {
            check(!ControlPages.visible(onPageZeroOnly, true, true, false, page),
                    "a menu control from another page was drawn on page " + page);
            check(!ControlPages.visible(onPageZeroOnly, true, true, true, page),
                    "a page filter did not apply while grabbed on page " + page);
        }
        // And the two states still differ on the flags themselves, which is the thing pages
        // must not have quietly taken over.
        check(!ControlPages.visible(0, true, false, false, 0),
                "a control marked in-game only was drawn in a menu");
        check(!ControlPages.visible(0, false, true, true, 0),
                "a control marked menu only was drawn in game");
    }

    private static void tickingEveryPageIsTheSameAsAll() {
        for (int pages = 2; pages <= ControlPages.MAX_PAGES; pages++) {
            int mask = ControlPages.withPage(0, 0, false, pages);
            for (int page = 1; page < pages; page++) {
                mask = ControlPages.withPage(mask, page, true, pages);
            }
            mask = ControlPages.withPage(mask, 0, true, pages);
            check(mask == ControlPages.ALL_PAGES,
                    "every page ticked did not collapse to ALL at " + pages + ": " + mask);
        }
    }

    /**
     * A control on no page is drawn nowhere, which is a button that has silently stopped
     * existing. Unticking the last one has to be refused rather than accepted and canonicalised
     * back to "all", which is the trap in a scheme where zero means everything.
     */
    private static void theLastPageCannotBeUnticked() {
        for (int pages = 2; pages <= ControlPages.MAX_PAGES; pages++) {
            int mask = ControlPages.ALL_PAGES;
            for (int page = 1; page < pages; page++) mask = ControlPages.withPage(mask, page, false, pages);
            check(ControlPages.pagesOn(mask, pages) == 1, "clearing left " + ControlPages.pagesOn(mask, pages));
            int after = ControlPages.withPage(mask, 0, false, pages);
            check(after == mask, "the last page was unticked at " + pages);
            boolean anywhere = false;
            for (int page = 0; page < pages; page++) {
                if (ControlPages.visible(after, true, true, true, page)) anywhere = true;
            }
            check(anywhere, "a control ended up on no page at all");
        }
    }

    private static void aTickSurvivesARoundTrip() {
        for (int pages = 2; pages <= ControlPages.MAX_PAGES; pages++) {
            for (int page = 0; page < pages; page++) {
                int off = ControlPages.withPage(ControlPages.ALL_PAGES, page, false, pages);
                check(!ControlPages.onPage(off, page), "unticking page " + page + " did nothing");
                int on = ControlPages.withPage(off, page, true, pages);
                check(on == ControlPages.ALL_PAGES, "reticking did not restore ALL");
            }
        }
    }

    private static void cyclingVisitsEveryPageAndReturns() {
        for (int pages = 2; pages <= ControlPages.MAX_PAGES; pages++) {
            boolean[] seen = new boolean[pages];
            int at = 0;
            for (int step = 0; step < pages; step++) {
                seen[at] = true;
                at = ControlPages.resolveTarget(ControlPages.TARGET_CYCLE, at, pages);
            }
            check(at == 0, "cycling " + pages + " times did not come home, landed on " + at);
            for (int page = 0; page < pages; page++) check(seen[page], "cycling skipped page " + page);
        }
    }

    private static void aTargetNamesItsPage() {
        for (int pages = 2; pages <= ControlPages.MAX_PAGES; pages++) {
            for (int page = 0; page < pages; page++) {
                check(ControlPages.resolveTarget(page + 1, 0, pages) == page,
                        "target " + (page + 1) + " did not land on page " + page);
            }
        }
    }

    private static void aTargetPastTheEndStaysPut() {
        // Measured from a page that is NOT the last one, because clamping to the end and
        // staying put give the same answer there and a fixture that cannot tell them apart
        // agrees with the mutation it was written to catch (16.20).
        check(ControlPages.resolveTarget(6, 0, 3) == 0, "a target past the end moved");
        check(ControlPages.resolveTarget(6, 1, 3) == 1, "a target past the end moved from page 1");
        check(ControlPages.resolveTarget(1, 0, 1) == 0, "a single page layout moved");
        check(ControlPages.resolveTarget(ControlPages.TARGET_CYCLE, 0, 1) == 0,
                "a single page layout cycled somewhere");
    }

    private static void deadEndsAreFound() {
        // Three pages; the only page button sits on page 0 and goes to page 1. Pages 1 and 2
        // have nothing that leads anywhere.
        int[] masks = {ControlPages.withPage(0, 0, true, 3) & ~0, 0};
        masks[0] = onlyPage(0, 3);
        masks[1] = ControlPages.ALL_PAGES;
        int[] targets = {2, Integer.MIN_VALUE};
        boolean[] inGame = {true, true};
        boolean[] stuck = ControlPages.deadEndPages(masks, targets, inGame, 3);
        check(!stuck[0], "page 0 with a button was called a dead end");
        check(stuck[1] && stuck[2], "pages 1 and 2 were not called dead ends");
    }

    private static void aCycleButtonIsNeverADeadEnd() {
        int[] masks = {ControlPages.ALL_PAGES};
        int[] targets = {ControlPages.TARGET_CYCLE};
        boolean[] inGame = {true};
        for (int pages = 2; pages <= ControlPages.MAX_PAGES; pages++) {
            boolean[] stuck = ControlPages.deadEndPages(masks, targets, inGame, pages);
            for (int page = 0; page < pages; page++) {
                check(!stuck[page], "a cycle button left page " + page + " stuck at " + pages);
            }
        }
    }

    /** A button that goes to the page it is already on leads nowhere, and looks like it does. */
    private static void aSelfTargetIsADeadEnd() {
        int[] masks = {onlyPage(1, 3)};
        int[] targets = {2};
        boolean[] inGame = {true};
        boolean[] stuck = ControlPages.deadEndPages(masks, targets, inGame, 3);
        check(stuck[1], "a button pointing at its own page counted as a way out");
    }

    /** A page button that is hidden in game cannot be a way out of anything. */
    private static void everyControlIsReachableOnSomePage() {
        int[] masks = {ControlPages.ALL_PAGES};
        int[] targets = {ControlPages.TARGET_CYCLE};
        boolean[] hidden = {false};
        boolean[] stuck = ControlPages.deadEndPages(masks, targets, hidden, 3);
        check(stuck[0] && stuck[1] && stuck[2], "a control hidden in game counted as a way out");
    }

    private static void outOfRangeShowsRatherThanHides() {
        check(ControlPages.onPage(onlyPage(0, 3), 99), "an out of range page hid a control");
        check(ControlPages.onPage(onlyPage(0, 3), -1), "a negative page hid a control");
        check(ControlPages.pageCount(0) == 1, "a layout with no pages declared was not one page");
        check(ControlPages.pageCount(99) == ControlPages.MAX_PAGES, "the page cap did not hold");
    }

    /**
     * The invariant deleting a page has to keep: every control is still drawn on some page.
     * Swept over every mask of a four page layout, because the case that breaks it is the one
     * control that lived only on the page being deleted, and that is one mask out of fifteen.
     */
    private static void removingAPageKeepsEveryoneSomewhere() {
        for (int before = 2; before <= ControlPages.MAX_PAGES; before++) {
            int full = ControlPages.fullMask(before);
            for (int mask = 0; mask <= full; mask++) {
                for (int removed = 0; removed < before; removed++) {
                    int after = ControlPages.afterPageRemoved(mask, removed, before);
                    boolean anywhere = false;
                    for (int page = 0; page < before - 1; page++) {
                        if (ControlPages.onPage(after, page)) anywhere = true;
                    }
                    check(anywhere, "mask " + mask + " vanished when page " + removed
                            + " of " + before + " went");
                }
            }
        }
    }

    /**
     * A control that lived only on the deleted page lands on exactly one page.
     *
     * Without the guard the empty mask falls through the canonicalisation and comes out as
     * ALL_PAGES, so the control is still drawn somewhere and the sweep above is happy; what it
     * is actually doing is dropping that button onto every page somebody kept it off. Asserting
     * "still visible" could never tell those apart, so this asserts the count.
     */
    private static void anOrphanLandsOnOnePageAndNotAllOfThem() {
        for (int before = 3; before <= ControlPages.MAX_PAGES; before++) {
            for (int removed = 0; removed < before; removed++) {
                int only = onlyPage(removed, before);
                int after = ControlPages.afterPageRemoved(only, removed, before);
                int on = ControlPages.pagesOn(after, before - 1);
                check(on == 1, "an orphan from page " + removed + " of " + before
                        + " landed on " + on + " pages");
            }
        }
    }

    /** A control on page 2 is still on page 2, whichever page below it was deleted. */
    private static void removingAPageShiftsTheOnesAboveIt() {
        int onTwoAndThree = onlyPages(new int[]{2, 3}, 4);
        int after = ControlPages.afterPageRemoved(onTwoAndThree, 0, 4);
        check(ControlPages.onPage(after, 1) && ControlPages.onPage(after, 2),
                "pages above the hole did not shift down");
        check(!ControlPages.onPage(after, 0), "a control slid onto a page it was never on");
        int afterAbove = ControlPages.afterPageRemoved(onTwoAndThree, 3, 4);
        check(ControlPages.onPage(afterAbove, 2) && !ControlPages.onPage(afterAbove, 1),
                "removing a page above moved one below it");
        check(ControlPages.afterPageRemoved(ControlPages.ALL_PAGES, 1, 4) == ControlPages.ALL_PAGES,
                "ALL stopped meaning all");
    }

    private static void removingAPageRepointsTargets() {
        check(ControlPages.targetAfterPageRemoved(4, 0) == 3, "a target above the hole did not shift");
        check(ControlPages.targetAfterPageRemoved(2, 2) == 2, "a target below the hole moved");
        check(ControlPages.targetAfterPageRemoved(3, 2) == ControlPages.TARGET_CYCLE,
                "a target at the hole did not become a cycle");
        check(ControlPages.targetAfterPageRemoved(ControlPages.TARGET_CYCLE, 1)
                == ControlPages.TARGET_CYCLE, "a cycle button stopped cycling");
    }

    private static int onlyPages(int[] pages, int count) {
        int mask = ControlPages.ALL_PAGES;
        for (int page = 0; page < count; page++) {
            boolean wanted = false;
            for (int p : pages) if (p == page) wanted = true;
            if (!wanted) mask = ControlPages.withPage(mask, page, false, count);
        }
        return mask;
    }

    private static int onlyPage(int page, int pages) {
        int mask = ControlPages.ALL_PAGES;
        for (int other = 0; other < pages; other++) {
            if (other != page) mask = ControlPages.withPage(mask, other, false, pages);
        }
        return mask;
    }
}
