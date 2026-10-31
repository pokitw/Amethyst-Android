package net.kdt.pojavlaunch.modmigrate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.kdt.pojavlaunch.modmigrate.ModMigration.Candidate;
import net.kdt.pojavlaunch.modmigrate.ModMigration.Local;
import net.kdt.pojavlaunch.modmigrate.ModMigration.Plan;
import net.kdt.pojavlaunch.modmigrate.ModMigration.Step;
import net.kdt.pojavlaunch.modmigrate.ModMigration.Target;
import net.kdt.pojavlaunch.modmigrate.ModMigration.Verdict;

/**
 * Drives the shipped {@link ModMigration} planner.
 *
 * Every check here is a form of one question: can this plan put a mod in somebody's folder that
 * they did not choose, or take one out of the game that they did. Both are silent. A wrong
 * identity compiles perfectly, installs cleanly, and is only discovered when the player wonders
 * why they have a mod they have never heard of; a wrong disable takes a working mod out of the
 * game and looks exactly like the mod having been broken by the new version.
 */
public final class Drive {
    private static int sChecks;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) {
        identityIsProvedAndNeverGuessed();
        runningItTwiceChangesNothing();
        aReleaseIsPreferredOverANewerPrerelease();
        nothingIsDisabledWhenTheNewFileHasTheSameName();
        nothingIsDisabledAcrossFolders();
        anUnidentifiedJarIsNeverInstalled();
        noBuildSaysWhatItDidFind();
        searchTermsNeverUseTheFileName();
        nothingThrowsOnTheEmptyCases();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String failure : FAILURES) System.out.println("FAIL " + failure);
            System.out.println("FAILED: " + FAILURES.size());
            System.exit(1);
        }
        System.out.println("ok: migratesim");
    }

    /**
     * The one rule the whole feature rests on.
     *
     * A file name that looks right is not evidence, and the search that produced a candidate
     * project is not evidence either. Only the hash is. The four ways this can go wrong are all
     * checked, because each of them installs a mod nobody chose: a candidate with the right name
     * and the wrong bytes, a local file that could not be read, a version Modrinth did not hash,
     * and hex that came back in the other case.
     */
    private static void identityIsProvedAndNeverGuessed() {
        Local local = jar("sodium-fabric-0.5.8.jar", "AABBCC", "sodium", "Sodium");

        check(ModMigration.matches(local, one(version("AABBCC", "0.5.8", true))) != null,
                "an exact hash was not matched");
        // Hex case is the server's business, not a difference in the file.
        check(ModMigration.matches(local, one(version("aabbcc", "0.5.8", true))) != null,
                "a hash in the other case was not matched");

        check(ModMigration.matches(local, one(version("DDEEFF", "0.5.8", true))) == null,
                "a different hash was matched, which is how somebody else's mod gets installed");
        check(ModMigration.matches(local, one(version(null, "0.5.8", true))) == null,
                "a version with no hash was taken as a match");
        check(ModMigration.matches(jar("sodium.jar", null, "sodium", "Sodium"),
                one(version("AABBCC", "0.5.8", true))) == null,
                "an unreadable local jar was matched to something");
        check(ModMigration.matches(local, new ArrayList<Candidate>()) == null,
                "an empty candidate list produced a match");
    }

    /**
     * The keystone: a migration that has already been run must be a no-op.
     *
     * This is the case that would otherwise destroy a correct setup rather than merely fail to
     * improve it. After a move, the jar on disk <i>is</i> one of the target's builds, so a second
     * run has to see ALREADY and neither download it again nor disable what the first run
     * installed. Modelled by replanning with the installed file standing in for the local one,
     * which is exactly the state the folder is in afterwards.
     */
    private static void runningItTwiceChangesNothing() {
        Target target = new Target("1.21.1", "fabric", true);
        Local old = jar("sodium-fabric-0.5.8+mc1.20.1.jar", "OLD111", "sodium", "Sodium");
        Candidate wasOn1201 = version("OLD111", "0.5.8", true);
        List<Candidate> forTarget =
                one(named("sodium-fabric-0.6.0+mc1.21.1.jar", "NEW222", "0.6.0", true));

        Step first = ModMigration.step(old, wasOn1201, forTarget, "0.6.0");
        check(first.verdict == Verdict.MOVE, "the first run did not plan a move");
        check(first.disablesOld(target), "the first run left the old jar in the game");

        // The folder as it stands after that move: the new file is what is on disk now.
        Local now = jar("sodium-fabric-0.6.0+mc1.21.1.jar", "NEW222", "sodium", "Sodium");
        Step second = ModMigration.step(now, forTarget.get(0), forTarget, "0.6.0");
        check(second.verdict == Verdict.ALREADY,
                "a second run did not recognise its own work: " + second.verdict);
        check(second.install == null, "a second run would download the same file again");
        check(!second.disablesOld(target),
                "a second run would disable the jar the first run installed");

        Plan plan = ModMigration.plan(Arrays.asList(second));
        check(!plan.changesAnything(), "a plan with nothing to do claimed it would change things");
    }

    /**
     * Somebody moving a working set wants it to keep working, so a release beats a newer beta.
     *
     * Checked in both orders, because the list arrives newest-first and taking the first entry is
     * the obvious implementation and the wrong one exactly when the newest is a prerelease.
     */
    private static void aReleaseIsPreferredOverANewerPrerelease() {
        List<Candidate> newestIsBeta = Arrays.asList(
                named("mod-0.7.0-beta.jar", "B1", "0.7.0-beta", false),
                named("mod-0.6.0.jar", "R1", "0.6.0", true));
        Candidate chosen = ModMigration.chooseInstall(newestIsBeta);
        check(chosen != null && "0.6.0".equals(chosen.versionNumber),
                "a beta was installed over an available release: "
                        + (chosen == null ? "nothing" : chosen.versionNumber));

        // With nothing stable published for the target, the newest prerelease is the only answer
        // there is, and refusing would be refusing the whole migration over a channel label.
        List<Candidate> onlyBetas = Arrays.asList(
                named("mod-0.7.0-beta.jar", "B1", "0.7.0-beta", false),
                named("mod-0.6.0-alpha.jar", "A1", "0.6.0-alpha", false));
        Candidate fallback = ModMigration.chooseInstall(onlyBetas);
        check(fallback != null && "0.7.0-beta".equals(fallback.versionNumber),
                "a project with only prereleases for the target produced no plan");
    }

    /**
     * The disable that must not happen.
     *
     * When the target's file carries the same name as the jar on disk, the download has already
     * replaced it, so there is nothing left to take out of the game. Disabling by that name
     * afterwards renames the file that was just installed, which removes the mod and reads to the
     * player as the new version having broken it.
     */
    private static void nothingIsDisabledWhenTheNewFileHasTheSameName() {
        Target target = new Target("1.21.1", "fabric", true);
        Local local = jar("modmenu.jar", "OLD1", "modmenu", "Mod Menu");
        List<Candidate> forTarget = one(named("modmenu.jar", "NEW1", "11.0.0", true));

        Step step = ModMigration.step(local, version("OLD1", "7.2.2", true), forTarget, "11.0.0");
        check(step.verdict == Verdict.MOVE, "a same-named replacement was not planned as a move");
        check(!step.disablesOld(target),
                "the jar that had just been installed would have been disabled under its own name");

        // Case is not a difference in a file name on this platform's storage either.
        Step cased = ModMigration.step(jar("ModMenu.JAR", "OLD1", "modmenu", "Mod Menu"),
                version("OLD1", "7.2.2", true), forTarget, "11.0.0");
        check(!cased.disablesOld(target), "a name differing only in case was treated as a new file");
    }

    /** A jar in another profile's folder is not ours to take out of the game. */
    private static void nothingIsDisabledAcrossFolders() {
        Target separate = new Target("1.21.1", "fabric", false);
        Local local = jar("sodium-0.5.8.jar", "OLD1", "sodium", "Sodium");
        Step step = ModMigration.step(local, version("OLD1", "0.5.8", true),
                one(named("sodium-0.6.0.jar", "NEW1", "0.6.0", true)), "0.6.0");
        check(step.verdict == Verdict.MOVE, "a copy into another folder was not planned");
        check(!step.disablesOld(separate),
                "a jar belonging to another profile's folder would have been disabled");
    }

    /**
     * An unproved jar is reported and never acted on.
     *
     * This is the mod checker's "UNKNOWN never CONFLICTS" applied to writing files: a missed
     * migration leaves the player exactly where they already were, and a wrong one puts a mod
     * they never chose in their folder under a name they will later trust.
     */
    private static void anUnidentifiedJarIsNeverInstalled() {
        Local handBuilt = jar("my-own-mod.jar", "ZZZZ", "myown", "My Own Mod");
        Step step = ModMigration.step(handBuilt, null,
                one(named("something-else.jar", "NEW1", "1.0.0", true)), "1.0.0");
        check(step.verdict == Verdict.UNIDENTIFIED, "an unproved jar was given a verdict");
        check(step.install == null, "an unproved jar was scheduled for installation");
        check(step.projectId == null, "an unproved jar carried a project id");
        check(!step.disablesOld(new Target("1.21.1", "fabric", true)),
                "an unproved jar would have been disabled");
        check(step.label().equals("My Own Mod"), "an unproved jar lost its own name: " + step.label());
    }

    /** "No build" has to say what it did find, or the player cannot tell it from a failure. */
    private static void noBuildSaysWhatItDidFind() {
        Local local = jar("oldmod-1.0.0.jar", "OLD1", "oldmod", "Old Mod");
        Step step = ModMigration.step(local, version("OLD1", "1.0.0", true),
                new ArrayList<Candidate>(), "1.4.0");
        check(step.verdict == Verdict.NO_BUILD, "an abandoned mod was not reported as such");
        check(step.install == null, "a project with no target build was scheduled anyway");
        check("1.4.0".equals(step.newestElsewhere),
                "the newest version the project does have was not carried: " + step.newestElsewhere);
        check("1.0.0".equals(step.currentVersion),
                "the version on disk was not carried: " + step.currentVersion);
    }

    /**
     * The file name is not a search term, and an id's separators are.
     *
     * A jar is named for its version, its loader and its Minecraft version as well as its mod, so
     * searching for the file name finds whatever is popular. An id reads as one word to a search
     * engine, so "roughly_enough_items" finds nothing while "roughly enough items" finds the mod.
     */
    private static void searchTermsNeverUseTheFileName() {
        // The declared name here is deliberately NOT the spaced id: a mod whose name happens to
        // be the id with spaces in cannot show whether the separators were opened out, because
        // the dedupe correctly treats the two as one term. "REI" can.
        List<String> terms = ModMigration.searchTerms(
                jar("roughlyenoughitems-fabric-12.0.684.jar", "H", "roughly_enough_items", "REI"));
        for (String term : terms) {
            check(!term.contains(".jar"), "a file name reached the search: " + term);
            check(!term.contains("12.0.684"), "a version number reached the search: " + term);
        }
        check(terms.contains("REI"), "the declared name was not searched for");
        check(terms.contains("roughly enough items"),
                "an id's separators were not opened out: " + terms);
        check(terms.contains("roughly_enough_items"),
                "the id as written was dropped, and some projects are listed under it: " + terms);

        // And the dedupe itself: a name that already is the spaced id must not be searched twice.
        List<String> once = ModMigration.searchTerms(
                jar("rei.jar", "H", "roughly_enough_items", "Roughly Enough Items"));
        check(once.size() == 2, "a term was searched for twice over: " + once);

        // A jar whose metadata would not parse has only its own name to go on, and no search at
        // all is better than searching for a file name and accepting whatever comes back.
        check(ModMigration.searchTerms(jar("unknown.jar", "H", null, null)).isEmpty(),
                "a jar with no metadata produced a search term out of nothing");
    }

    /** Nothing here may throw: it runs while somebody is looking at a list of their own mods. */
    private static void nothingThrowsOnTheEmptyCases() {
        check(ModMigration.matches(null, null) == null, "a null local threw or matched");
        check(ModMigration.chooseInstall(null) == null, "a null version list threw or chose");
        check(ModMigration.step(null, null, null, null) == null, "a null jar produced a step");
        check(ModMigration.searchTerms(null).isEmpty(), "a null jar produced search terms");

        Plan empty = ModMigration.plan(null);
        check(empty.steps.isEmpty(), "a null plan was not empty");
        check(!empty.changesAnything(), "an empty plan claimed it would change things");
        check(empty.count(Verdict.MOVE) == 0, "an empty plan counted a move");

        Plan withNull = ModMigration.plan(Arrays.asList((Step) null));
        check(withNull.steps.isEmpty(), "a null step reached the plan");
    }

    /* ------------------------------------------------------------------ plumbing */

    private static Local jar(String fileName, String sha1, String modId, String name) {
        return new Local(fileName, sha1, modId, name);
    }

    private static Candidate version(String sha1, String number, boolean release) {
        return new Candidate("PROJ", "Sodium", "v-" + number, number,
                "file-" + number + ".jar", sha1, release);
    }

    private static Candidate named(String fileName, String sha1, String number, boolean release) {
        return new Candidate("PROJ", "Sodium", "v-" + number, number, fileName, sha1, release);
    }

    private static List<Candidate> one(Candidate candidate) {
        return new ArrayList<Candidate>(Arrays.asList(candidate));
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
