package net.kdt.pojavlaunch.modmeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Does this version satisfy what a mod asked for.
 *
 * Two mutually incompatible grammars, because the ecosystem grew two:
 *
 * <pre>
 *   Fabric and Quilt   "&gt;=1.20.1 &lt;1.21"   "~1.20.1"   "1.20.x"   "*"
 *   Forge and NeoForge "[1.20.1,1.21)"     "[1.20,)"    "(,1.21]"
 * </pre>
 *
 * <b>Everything here answers {@link Verdict#UNKNOWN} rather than guessing.</b> A missed warning
 * costs a player nothing: they are where they already were. A false one tells them to turn off a
 * mod that works. So an operator this does not implement, a version it cannot compare, a predicate
 * with a syntax error in it, all land on UNKNOWN, and only a comparison this class actually
 * performed can produce CONFLICTS. Anyone tidying this file later has to keep that asymmetry.
 *
 * <p>Plain Java with no dependencies, so {@code scripts/modmetasim} drives this exact class. The
 * failure mode being guarded against is silent in both directions: a predicate read too loosely
 * checks nothing and looks calm, and one read too strictly condemns working mods.
 */
public final class VersionPredicate {

    private VersionPredicate() {}

    /** What a comparison concluded. */
    public enum Verdict {
        /** The version satisfies the predicate. */
        MATCHES,
        /** The version definitely does not, and this class is sure. */
        CONFLICTS,
        /** Not enough was understood to say. Nothing is ever reported to a player from this. */
        UNKNOWN
    }

    /**
     * How several alternative predicates are joined into one string before reaching here.
     *
     * Fabric's array form is an OR, and this keeps that fact rather than flattening it into a
     * conjunction which would be unsatisfiable. It is deliberately <b>not</b> a space, which was
     * the first draft and was wrong in the worst available way: a space already separates the
     * terms of a conjunction, so {@code ">=1.20.1 <1.21"} would have split into two alternatives,
     * the first of which matches 1.21, and the predicate would have passed the exact version it
     * was written to exclude. This is SemVer's own OR, so a predicate arriving with one already
     * in it is read correctly rather than merely not corrupted by the joining.
     */
    public static final String OR_SEPARATOR = " || ";

    /** The same, as the pattern that splits it back apart. */
    private static final String OR_PATTERN = "\\|\\|";

    /**
     * @param predicate the raw text the mod wrote
     * @param version   the version to test, which for this launcher is always a plain Minecraft
     *                  release like {@code 1.20.1}
     * @param maven     true for the Forge and NeoForge bracket grammar, false for Fabric and Quilt
     */
    public static Verdict test(String predicate, String version, boolean maven) {
        if (version == null || version.trim().length() == 0) return Verdict.UNKNOWN;
        if (predicate == null) return Verdict.MATCHES;
        String text = predicate.trim();
        if (text.length() == 0 || text.equals("*")) return Verdict.MATCHES;

        // Alternatives are an OR: one match is enough, and it takes every branch failing to
        // conclude a conflict. A branch nobody understood makes the whole thing unknown, because
        // that branch might have been the one that matched.
        boolean anyUnknown = false;
        for (String alternative : text.split(OR_PATTERN, -1)) {
            Verdict verdict = maven ? testMaven(alternative, version) : testSemver(alternative, version);
            if (verdict == Verdict.MATCHES) return Verdict.MATCHES;
            if (verdict == Verdict.UNKNOWN) anyUnknown = true;
        }
        return anyUnknown ? Verdict.UNKNOWN : Verdict.CONFLICTS;
    }

    /* ------------------------------------------------------------------ fabric and quilt */

    /**
     * A space separated conjunction of comparators, every one of which has to hold.
     *
     * A conjunction is the opposite of the alternatives above: one failure is a conflict, and one
     * term nobody understood makes it unknown, since that term might have been the one that ruled
     * the version out.
     */
    private static Verdict testSemver(String predicate, String version) {
        String text = predicate.trim();
        if (text.length() == 0 || text.equals("*")) return Verdict.MATCHES;
        boolean anyUnknown = false;
        // Commas appear as a conjunction separator in the wild alongside spaces, and mean the same.
        for (String term : text.split("[\\s,]+")) {
            if (term.length() == 0) continue;
            Verdict verdict = testSemverTerm(term, version);
            if (verdict == Verdict.CONFLICTS) return Verdict.CONFLICTS;
            if (verdict == Verdict.UNKNOWN) anyUnknown = true;
        }
        return anyUnknown ? Verdict.UNKNOWN : Verdict.MATCHES;
    }

    private static Verdict testSemverTerm(String term, String version) {
        if (term.equals("*")) return Verdict.MATCHES;

        if (term.startsWith(">=")) return compare(version, term.substring(2), GE);
        if (term.startsWith("<=")) return compare(version, term.substring(2), LE);
        if (term.startsWith("!=")) return negate(compare(version, term.substring(2), EQ));
        if (term.startsWith(">")) return compare(version, term.substring(1), GT);
        if (term.startsWith("<")) return compare(version, term.substring(1), LT);
        if (term.startsWith("=")) return matchesLoosely(term.substring(1), version);

        // Both are "this version or later, below the next bump", differing in which place bumps.
        // ~1.20.1 admits patch releases of 1.20. ^1.20.1 admits everything below 2.0, except that
        // SemVer bumps the leftmost NON-ZERO place, so ^0.2.3 stops at 0.3 and ^0.0.3 at 0.0.4.
        // That rule never shows on a Minecraft version, whose major is always 1, and does show on
        // the mod versions this same class is asked about, where getting it wrong is a condemned
        // dependency rather than a missed one.
        if (term.startsWith("~")) return between(version, term.substring(1), 1);
        if (term.startsWith("^")) return caret(version, term.substring(1));

        return matchesLoosely(term, version);
    }

    /**
     * A bare version, which may carry wildcards: {@code 1.20.x} and {@code 1.20.*} both mean any
     * patch of 1.20, and {@code 1.20} on its own means exactly 1.20 rather than a prefix.
     *
     * The exactness matters and is easy to get backwards. Mods write {@code "1.20.1"} meaning that
     * release, and reading it as a prefix would quietly accept 1.20.15 as well.
     */
    private static Verdict matchesLoosely(String pattern, String version) {
        String text = pattern.trim();
        if (text.length() == 0) return Verdict.MATCHES;
        int[] wanted = parse(text.replace(".x", ".*").replace(".X", ".*"));
        int[] actual = parse(version);
        if (wanted == null || actual == null) return Verdict.UNKNOWN;

        for (int i = 0; i < wanted.length; i++) {
            if (wanted[i] == WILDCARD) return Verdict.MATCHES;
            int have = i < actual.length ? actual[i] : 0;
            if (have != wanted[i]) return Verdict.CONFLICTS;
        }
        // Every stated place agrees. Places the pattern did not state must be zero, so that
        // "1.20" is 1.20 exactly and not the whole 1.20 line.
        for (int i = wanted.length; i < actual.length; i++) {
            if (actual[i] != 0) return Verdict.CONFLICTS;
        }
        return Verdict.MATCHES;
    }

    /**
     * SemVer's caret: at least this version, below the next bump of its leftmost non-zero place.
     *
     * A bound that is all zeroes has no place to bump and is left unknown rather than invented.
     */
    private static Verdict caret(String version, String bound) {
        int[] lower = parse(bound);
        if (lower == null || hasWildcard(lower)) return Verdict.UNKNOWN;
        for (int i = 0; i < lower.length; i++) {
            if (lower[i] != 0) return between(version, bound, i);
        }
        return Verdict.UNKNOWN;
    }

    /**
     * The half-open range a {@code ~} or {@code ^} term stands for.
     *
     * @param bumpIndex which place rolls over to give the exclusive upper bound: 1 for {@code ~},
     *                  which allows patch releases, 0 for {@code ^}, which allows minor ones
     */
    private static Verdict between(String version, String bound, int bumpIndex) {
        int[] lower = parse(bound);
        int[] actual = parse(version);
        if (lower == null || actual == null || hasWildcard(lower)) return Verdict.UNKNOWN;
        if (compareNumbers(actual, lower) < 0) return Verdict.CONFLICTS;

        int[] upper = new int[bumpIndex + 1];
        for (int i = 0; i <= bumpIndex; i++) upper[i] = i < lower.length ? lower[i] : 0;
        upper[bumpIndex] = upper[bumpIndex] + 1;
        return compareNumbers(actual, upper) < 0 ? Verdict.MATCHES : Verdict.CONFLICTS;
    }

    /* ------------------------------------------------------------------ forge and neoforge */

    /**
     * Maven's bracket ranges, and their unions.
     *
     * A bare version is <b>not</b> a constraint in Maven, it is a "soft" recommendation that any
     * version may override, which is why {@code versionRange="1.20.1"} must not be read as
     * equality. Reading it as one would condemn a large number of Forge mods that are perfectly
     * fine, which is exactly the failure this class exists to avoid.
     */
    private static Verdict testMaven(String predicate, String version) {
        String text = predicate.trim();
        if (text.length() == 0 || text.equals("*")) return Verdict.MATCHES;
        if (text.charAt(0) != '[' && text.charAt(0) != '(') return Verdict.MATCHES;

        List<String> ranges = splitMavenUnion(text);
        if (ranges.isEmpty()) return Verdict.UNKNOWN;
        boolean anyUnknown = false;
        // A union of ranges: satisfying any one of them is enough.
        for (String range : ranges) {
            Verdict verdict = testMavenRange(range, version);
            if (verdict == Verdict.MATCHES) return Verdict.MATCHES;
            if (verdict == Verdict.UNKNOWN) anyUnknown = true;
        }
        return anyUnknown ? Verdict.UNKNOWN : Verdict.CONFLICTS;
    }

    /**
     * Split on the commas that separate ranges, not the ones inside them.
     *
     * {@code [1.20.1,1.21)} has a comma in the middle and is one range; {@code [1.0,2.0),[3.0,)}
     * is two. Splitting on every comma turns the first into two broken halves, and a broken half
     * that still parses is how a range ends up meaning something nobody wrote.
     */
    private static List<String> splitMavenUnion(String text) {
        List<String> ranges = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[' || c == '(') depth++;
            else if (c == ']' || c == ')') depth--;
            else if (c == ',' && depth == 0) {
                ranges.add(text.substring(start, i));
                start = i + 1;
            }
        }
        ranges.add(text.substring(start));
        List<String> trimmed = new ArrayList<>();
        for (String range : ranges) {
            String value = range.trim();
            if (value.length() > 0) trimmed.add(value);
        }
        return trimmed;
    }

    private static Verdict testMavenRange(String range, String version) {
        String text = range.trim();
        if (text.length() < 3) return Verdict.UNKNOWN;
        char open = text.charAt(0);
        char close = text.charAt(text.length() - 1);
        if ((open != '[' && open != '(') || (close != ']' && close != ')')) return Verdict.UNKNOWN;

        String body = text.substring(1, text.length() - 1);
        int comma = body.indexOf(',');
        if (comma < 0) {
            // [1.20.1] is a single pinned version, the one bracket form that is a constraint.
            return matchesLoosely(body, version);
        }
        String lower = body.substring(0, comma).trim();
        String upper = body.substring(comma + 1).trim();

        if (lower.length() > 0) {
            Verdict verdict = compare(version, lower, open == '[' ? GE : GT);
            if (verdict != Verdict.MATCHES) return verdict;
        }
        if (upper.length() > 0) {
            Verdict verdict = compare(version, upper, close == ']' ? LE : LT);
            if (verdict != Verdict.MATCHES) return verdict;
        }
        return Verdict.MATCHES;
    }

    /* ------------------------------------------------------------------ comparison */

    private static final int GE = 0, GT = 1, LE = 2, LT = 3, EQ = 4;

    private static Verdict compare(String version, String bound, int operator) {
        int[] actual = parse(version);
        int[] wanted = parse(bound);
        if (actual == null || wanted == null || hasWildcard(wanted)) return Verdict.UNKNOWN;
        int order = compareNumbers(actual, wanted);
        boolean holds;
        switch (operator) {
            case GE: holds = order >= 0; break;
            case GT: holds = order > 0; break;
            case LE: holds = order <= 0; break;
            case LT: holds = order < 0; break;
            case EQ: holds = order == 0; break;
            default: return Verdict.UNKNOWN;
        }
        return holds ? Verdict.MATCHES : Verdict.CONFLICTS;
    }

    private static Verdict negate(Verdict verdict) {
        if (verdict == Verdict.MATCHES) return Verdict.CONFLICTS;
        if (verdict == Verdict.CONFLICTS) return Verdict.MATCHES;
        return Verdict.UNKNOWN;
    }

    private static final int WILDCARD = -1;

    /**
     * A version as its numeric places, or null when it is not one this class can order.
     *
     * <b>Anything with a suffix returns null</b>, which is UNKNOWN everywhere above. Snapshots
     * ({@code 24w14a}), pre-releases ({@code 1.21-pre1}) and candidates ({@code 1.20.1-rc1}) do
     * not order against releases by any rule this class could apply without inventing one, and
     * inventing one is how a mod gets condemned by arithmetic nobody checked. It costs little:
     * the version tested against here comes from the profile and is always a plain release.
     */
    private static int[] parse(String version) {
        if (version == null) return null;
        String text = version.trim();
        if (text.length() == 0) return null;
        // A leading v is common in hand-written predicates and means nothing.
        if (text.charAt(0) == 'v' || text.charAt(0) == 'V') text = text.substring(1);

        String[] parts = text.split("\\.");
        if (parts.length == 0) return null;
        int[] places = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.equals("*") || part.equals("x") || part.equals("X") || part.equals("+")) {
                places[i] = WILDCARD;
                continue;
            }
            if (part.length() == 0) return null;
            for (int c = 0; c < part.length(); c++) {
                if (part.charAt(c) < '0' || part.charAt(c) > '9') return null;
            }
            try {
                places[i] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return places;
    }

    private static boolean hasWildcard(int[] places) {
        for (int place : places) if (place == WILDCARD) return true;
        return false;
    }

    /** Missing places count as zero, so 1.20 and 1.20.0 are the same version. */
    private static int compareNumbers(int[] left, int[] right) {
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) return a < b ? -1 : 1;
        }
        return 0;
    }
}
