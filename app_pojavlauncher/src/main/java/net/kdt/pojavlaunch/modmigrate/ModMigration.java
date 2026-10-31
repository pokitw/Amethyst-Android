package net.kdt.pojavlaunch.modmigrate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Taking a working set of mods to another Minecraft version.
 *
 * The ask, in the words it arrived in: the same mods a profile runs on one version, on a different
 * or newer one, done automatically. What makes that hard is not the downloading, which this
 * launcher already does; it is knowing <b>which project a jar on disk came from</b>, because a
 * file name is not an identity and installing the wrong mod puts a stranger's code in somebody's
 * folder under a name they will later believe.
 *
 * <p><b>Identity is proved by hash and never inferred from a name.</b> Modrinth reports a sha1 for
 * every file it serves, and this launcher already parses it, so a local jar's sha1 found among a
 * candidate project's versions <i>is</i> the proof: that byte-for-byte file came from that project.
 * The mod browser's "already installed" tick is a guess for exactly the reason this is not (the
 * search response carries no file name), and the two must not be confused. A jar that cannot be
 * proved is reported as unidentified and offered to a search, never installed on a hunch, which is
 * the same asymmetry the mod checker's "UNKNOWN never CONFLICTS" encodes.
 *
 * <p>No Android imports and no I/O: every rule here is decided from values the caller has already
 * fetched, so {@code scripts/migratesim} drives the real class (§16.25). A wrong plan is somebody's
 * mod folder rearranged, which compiles perfectly and is not visible until the game will not start.
 */
public final class ModMigration {

    private ModMigration() {}

    /** Where the set is going. */
    public static final class Target {
        /** The Minecraft version to find builds for. Never null: with no version there is no plan. */
        public final String mcVersion;
        /** Modrinth's loader id, or null when the profile runs no loader. */
        public final String loaderId;
        /**
         * Whether the jars being read and the jars being written live in one folder.
         *
         * True for every profile the loader installer makes, because a null {@code gameDir}
         * resolves to the one shared game directory, so the old set and the new set would sit
         * together and the loader would try to load both.
         */
        public final boolean sameFolder;

        public Target(String mcVersion, String loaderId, boolean sameFolder) {
            this.mcVersion = mcVersion;
            this.loaderId = loaderId;
            this.sameFolder = sameFolder;
        }
    }

    /** A jar in the folder, as the planner needs it. */
    public static final class Local {
        public final String fileName;
        /** The file's own sha1, or null where it could not be read. */
        public final String sha1;
        /** The mod id it declares, or null where the metadata would not parse. */
        public final String modId;
        /** The name it declares, for searching and for the row. */
        public final String declaredName;

        public Local(String fileName, String sha1, String modId, String declaredName) {
            this.fileName = fileName;
            this.sha1 = sha1;
            this.modId = modId;
            this.declaredName = declaredName;
        }
    }

    /** One file of one version of a candidate project, reduced to what a decision needs. */
    public static final class Candidate {
        public final String projectId;
        public final String projectTitle;
        public final String versionId;
        public final String versionNumber;
        public final String fileName;
        /** Modrinth's sha1 for this file, or null where the response omitted it. */
        public final String sha1;
        public final boolean release;

        public Candidate(String projectId, String projectTitle, String versionId,
                         String versionNumber, String fileName, String sha1, boolean release) {
            this.projectId = projectId;
            this.projectTitle = projectTitle;
            this.versionId = versionId;
            this.versionNumber = versionNumber;
            this.fileName = fileName;
            this.sha1 = sha1;
            this.release = release;
        }
    }

    /** What is going to happen to one jar. */
    public enum Verdict {
        /** A build for the target exists and is not the jar already there. */
        MOVE,
        /** The jar on disk already is a build for the target. Nothing to do, and nothing to undo. */
        ALREADY,
        /** Proved which project it is, and that project has nothing for the target. */
        NO_BUILD,
        /** Could not prove which project it is. Offered to a search, never installed. */
        UNIDENTIFIED
    }

    /** One line of the plan. */
    public static final class Step {
        public final Local local;
        public final Verdict verdict;
        /** The project, once proved. Null for {@link Verdict#UNIDENTIFIED}. */
        public final String projectId;
        public final String projectTitle;
        /** The version the jar on disk is, once proved. Null when unidentified. */
        public final String currentVersion;
        /** The build to install. Null unless {@link Verdict#MOVE}. */
        public final Candidate install;
        /**
         * For {@link Verdict#NO_BUILD}, the newest version the project does have, so the row can
         * say what it found rather than only what it did not.
         */
        public final String newestElsewhere;

        Step(Local local, Verdict verdict, String projectId, String projectTitle,
             String currentVersion, Candidate install, String newestElsewhere) {
            this.local = local;
            this.verdict = verdict;
            this.projectId = projectId;
            this.projectTitle = projectTitle;
            this.currentVersion = currentVersion;
            this.install = install;
            this.newestElsewhere = newestElsewhere;
        }

        /** The name to show, which is the project's where there is one and the file's otherwise. */
        public String label() {
            if (isSet(projectTitle)) return projectTitle;
            if (isSet(local.declaredName)) return local.declaredName;
            return local.fileName;
        }

        /**
         * Whether the jar that is there now has to be taken out of the game once the new one lands.
         *
         * <b>Only when the new file has a different name, and only in a shared folder.</b> Where
         * the names match, the download has already replaced the file and there is nothing left to
         * disable; disabling by the old name then would rename the <i>new</i> jar and take the mod
         * out of the game, which is the one failure this feature must not be able to produce.
         * Where the folders differ the old jar belongs to another profile and is not ours to touch.
         */
        public boolean disablesOld(Target target) {
            return verdict == Verdict.MOVE
                    && target.sameFolder
                    && install != null
                    && !sameName(install.fileName, local.fileName);
        }
    }

    /** The whole plan, and the counts a screen reports before anything is written. */
    public static final class Plan {
        public final List<Step> steps;

        Plan(List<Step> steps) {
            this.steps = Collections.unmodifiableList(steps);
        }

        public int count(Verdict verdict) {
            int found = 0;
            for (Step step : steps) {
                if (step.verdict == verdict) found++;
            }
            return found;
        }

        /** The steps that will actually write something, in the order they will run. */
        public List<Step> installs() {
            List<Step> out = new ArrayList<>();
            for (Step step : steps) {
                if (step.verdict == Verdict.MOVE) out.add(step);
            }
            return out;
        }

        /**
         * Whether running this would change anything at all.
         *
         * The question a screen has to answer before offering a button, and the one that makes
         * running the migration twice safe: the second run finds every jar already a build for the
         * target, so there is nothing to move and nothing to disable.
         */
        public boolean changesAnything() {
            return !installs().isEmpty();
        }
    }

    /**
     * Whether this jar is the file one of these versions serves.
     *
     * The whole of identification. Hashes are compared case-insensitively because hex comes back
     * in whichever case a server felt like, and a null on either side is not a match: an unread
     * local file and a version Modrinth did not hash are both "do not know", and do-not-know must
     * never read as yes here, because the consequence is installing somebody else's mod.
     *
     * @return the version this jar is, or null when it is not one of them
     */
    public static Candidate matches(Local local, List<Candidate> versions) {
        if (local == null || versions == null || !isSet(local.sha1)) return null;
        String want = local.sha1.trim().toLowerCase(Locale.ROOT);
        for (Candidate candidate : versions) {
            if (candidate == null || !isSet(candidate.sha1)) continue;
            if (want.equals(candidate.sha1.trim().toLowerCase(Locale.ROOT))) return candidate;
        }
        return null;
    }

    /**
     * Which of the target's builds to install.
     *
     * <b>A release wherever there is one</b>, even when a beta is newer: somebody moving a working
     * set to a new version is asking for it to keep working, and quietly handing them a prerelease
     * is not that. Only when the project has published nothing stable for the target does the
     * newest prerelease get offered, and the plan says which it is.
     *
     * The list is taken newest-first, which is the order Modrinth sends and the order this relies
     * on rather than re-deriving: version numbers are free text and ordering them here would be
     * inventing a comparison the server has already made.
     */
    public static Candidate chooseInstall(List<Candidate> targetVersions) {
        if (targetVersions == null) return null;
        for (Candidate candidate : targetVersions) {
            if (candidate != null && candidate.release) return candidate;
        }
        for (Candidate candidate : targetVersions) {
            if (candidate != null) return candidate;
        }
        return null;
    }

    /**
     * One jar's line of the plan.
     *
     * @param local         the jar on disk
     * @param current       the version it was proved to be, or null when it could not be proved
     * @param targetVersions every build of that project for the target, newest first, or empty
     * @param newest        the project's newest build for anything, for the "no build" line
     */
    public static Step step(Local local, Candidate current, List<Candidate> targetVersions,
                            String newest) {
        if (local == null) return null;
        if (current == null) {
            return new Step(local, Verdict.UNIDENTIFIED, null, null, null, null, null);
        }

        // Before choosing anything to install: the jar on disk may already be one of the target's
        // own builds, which is the ordinary state on a second run and the state a migration must
        // leave exactly as it found it.
        Candidate already = matches(local, targetVersions);
        if (already != null) {
            return new Step(local, Verdict.ALREADY, current.projectId, title(current, already),
                    already.versionNumber, null, null);
        }

        Candidate install = chooseInstall(targetVersions);
        if (install == null) {
            return new Step(local, Verdict.NO_BUILD, current.projectId, title(current, null),
                    current.versionNumber, null, newest);
        }
        return new Step(local, Verdict.MOVE, current.projectId, title(current, install),
                current.versionNumber, install, null);
    }

    /** Assemble the steps, dropping the nulls a caller could hand in. */
    public static Plan plan(List<Step> steps) {
        List<Step> out = new ArrayList<>();
        if (steps != null) {
            for (Step step : steps) {
                if (step != null) out.add(step);
            }
        }
        return new Plan(out);
    }

    /**
     * What to search for to find a jar's project.
     *
     * The declared name first and the mod id second, because a name is what the project is called
     * on Modrinth and an id is a programmer's spelling of it: "sodium" matches both, but
     * "ferritecore" is listed as "FerriteCore" and `roughly_enough_items` as "Roughly Enough
     * Items". The file name is deliberately <b>not</b> used: it carries a version, a loader and a
     * Minecraft version, and searching for those finds whatever is popular rather than the mod.
     *
     * Whatever comes back is only ever a candidate, and a candidate is accepted on a hash and
     * never on the search's own ordering.
     */
    public static List<String> searchTerms(Local local) {
        List<String> terms = new ArrayList<>();
        if (local == null) return terms;
        if (isSet(local.declaredName)) terms.add(local.declaredName.trim());
        if (isSet(local.modId)) {
            String id = local.modId.trim();
            // An id is lower snake or kebab case by convention and reads as one word to a search
            // engine, so the separators become spaces: "roughly_enough_items" finds nothing and
            // "roughly enough items" finds the mod.
            String spaced = id.replace('_', ' ').replace('-', ' ').trim();
            addIfNew(terms, id);
            addIfNew(terms, spaced);
        }
        return terms;
    }

    private static void addIfNew(List<String> terms, String term) {
        if (!isSet(term)) return;
        for (String existing : terms) {
            if (existing.equalsIgnoreCase(term)) return;
        }
        terms.add(term);
    }

    private static String title(Candidate current, Candidate other) {
        if (current != null && isSet(current.projectTitle)) return current.projectTitle;
        if (other != null && isSet(other.projectTitle)) return other.projectTitle;
        return null;
    }

    private static boolean sameName(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private static boolean isSet(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
