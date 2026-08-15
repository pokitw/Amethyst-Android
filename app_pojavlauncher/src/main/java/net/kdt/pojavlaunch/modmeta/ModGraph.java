package net.kdt.pojavlaunch.modmeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a folder of mods says about itself, taken together.
 *
 * Two questions, one pass. <b>Will this mod run on this Minecraft version</b>, and <b>is anything
 * it needs missing</b>. They share a parser because in every modern format they share a field: the
 * Minecraft requirement is just another dependency entry.
 *
 * <p><b>Over-flagging is the failure mode, and it is not symmetrical with under-flagging.</b> A
 * warning nobody needed sends somebody to turn off a mod that works, breaks their game, and the
 * launcher gets the blame. A warning that never appeared leaves them exactly where they already
 * were. Everything here is built to fail towards silence, and four specific traps in this
 * ecosystem all produce a screen full of confident, wrong, actionable warnings:
 *
 * <ol>
 *   <li><b>Fabric API is forty modules.</b> They each declare {@code provides} and mods depend on
 *       those module ids rather than on {@code fabric-api}. A graph that ignores {@code provides}
 *       reports a missing dependency on nearly every Fabric mod installed.</li>
 *   <li><b>Platform ids are not mods.</b> Every mod depends on {@code minecraft} and on its
 *       loader, neither of which is a jar in the folder.</li>
 *   <li><b>A disabled jar is on disk and absent from the game.</b> Counting it means switching off
 *       Fabric API silently stops warning about the twenty mods that needed it, which is the exact
 *       case the feature exists for.</li>
 *   <li><b>Optional is not required.</b> Forge writes {@code mandatory}, NeoForge writes
 *       {@code type}, and reading one spelling makes every dependency in the other format either
 *       always required or never.</li>
 * </ol>
 *
 * <p>Plain Java with no Android types, driven directly by {@code scripts/modmetasim}. The check
 * that matters most there is the positive one: a folder of real, mutually consistent mods must
 * produce exactly zero warnings. A harness built only from broken fixtures passes a graph that
 * flags everything.
 */
public final class ModGraph {

    private ModGraph() {}

    /** How a mod stands against the profile it is installed in. */
    public enum Compat {
        /** It declares support for this Minecraft version. */
        MATCHES,
        /** It declares a version range this profile is not in. */
        WRONG_VERSION,
        /** It is built for a different loader than the profile runs. */
        WRONG_LOADER,
        /** It declares nothing that could be checked, or nothing is known about the profile. */
        UNKNOWN
    }

    /** One installed jar, as the graph needs to see it. */
    public static final class Entry {
        /** Whatever identifies this row to the caller. Never read here. */
        public final String key;
        /** Null when the jar's metadata could not be read at all. */
        public final ModRequirements requirements;
        /** A disabled jar is on disk and absent from the game, so it satisfies nothing. */
        public final boolean enabled;

        public Entry(String key, ModRequirements requirements, boolean enabled) {
            this.key = key;
            this.requirements = requirements;
            this.enabled = enabled;
        }
    }

    /** What the graph concluded about one jar. */
    public static final class Result {
        public final String key;
        public final Compat compat;
        /**
         * Required dependency ids that no enabled installed mod provides, lowercased. Empty when
         * nothing is missing, which is the ordinary case and the one the harness pins.
         */
        public final List<String> missing;
        /** Enabled installed mods that require this one, by key. Empty when nothing does. */
        public final List<String> requiredBy;

        Result(String key, Compat compat, List<String> missing, List<String> requiredBy) {
            this.key = key;
            this.compat = compat;
            this.missing = Collections.unmodifiableList(missing);
            this.requiredBy = Collections.unmodifiableList(requiredBy);
        }

        /** Whether there is anything here worth telling a player about. */
        public boolean hasProblem() {
            return compat == Compat.WRONG_VERSION || compat == Compat.WRONG_LOADER
                    || !missing.isEmpty();
        }
    }

    /**
     * Judge every jar against the profile and against each other.
     *
     * @param entries       every jar in the folder, enabled or not
     * @param mcVersion     the profile's Minecraft version, or null when it could not be worked
     *                      out. Null means <b>check nothing</b>: a snapshot profile, or one whose
     *                      version has never been downloaded, is not a profile that every mod
     *                      conflicts with.
     * @param profileLoader the profile's loader as {@link ModRequirements} spells it, or null for
     *                      vanilla or unknown, which likewise checks nothing
     */
    public static List<Result> resolve(List<Entry> entries, String mcVersion, String profileLoader) {
        // Who can satisfy a dependency: an id a mod declares, or one it says it provides. Disabled
        // jars are deliberately absent, since the game will not load them either.
        Map<String, String> providers = new HashMap<>();
        for (Entry entry : entries) {
            if (!entry.enabled || entry.requirements == null) continue;
            if (entry.requirements.modId != null) {
                providers.put(entry.requirements.modId, entry.key);
            }
            for (String provided : entry.requirements.provides) {
                // A real mod id wins over something merely provided, so a module and the mod that
                // claims it never fight over which row is named as the provider.
                if (!providers.containsKey(provided)) providers.put(provided, entry.key);
            }
        }

        Map<String, Set<String>> dependents = new HashMap<>();
        List<Result> results = new ArrayList<>(entries.size());

        for (Entry entry : entries) {
            List<String> missing = new ArrayList<>();
            if (entry.requirements != null && entry.enabled) {
                Set<String> seen = new HashSet<>();
                for (ModRequirements.Dep dep : entry.requirements.dependencies) {
                    if (!dep.required) continue;
                    if (ModRequirements.isPlatform(dep.id)) continue;
                    if (!seen.add(dep.id)) continue;
                    String provider = providers.get(dep.id);
                    if (provider == null) {
                        missing.add(dep.id);
                    } else if (!provider.equals(entry.key)) {
                        Set<String> set = dependents.get(provider);
                        if (set == null) {
                            set = new LinkedHashSet<>();
                            dependents.put(provider, set);
                        }
                        set.add(entry.key);
                    }
                }
            }
            results.add(new Result(entry.key, compatOf(entry, mcVersion, profileLoader),
                    missing, new ArrayList<String>()));
        }

        // Rebuilt with the dependents filled in, which cannot be done in the pass above because a
        // mod's dependents are only all known once every jar has been walked.
        List<Result> complete = new ArrayList<>(results.size());
        for (Result result : results) {
            Set<String> set = dependents.get(result.key);
            complete.add(new Result(result.key, result.compat, new ArrayList<>(result.missing),
                    set == null ? new ArrayList<String>() : new ArrayList<>(set)));
        }
        return complete;
    }

    /**
     * Whether a jar declares support for this profile.
     *
     * The loader is checked before the version, because a Forge jar in a Fabric profile is wrong
     * whatever version it names, and saying so names the actual problem. Legacy Forge jars are
     * exempt from the version check having only ever run on Forge, and their `mcversion` is read
     * as an ordinary dependency by the parser so it is checked the same way as everyone else's.
     */
    private static Compat compatOf(Entry entry, String mcVersion, String profileLoader) {
        if (entry.requirements == null) return Compat.UNKNOWN;

        if (profileLoader != null && entry.requirements.loader != null
                && !compatibleLoaders(entry.requirements.loader, profileLoader)) {
            return Compat.WRONG_LOADER;
        }

        if (mcVersion == null) return Compat.UNKNOWN;
        String range = entry.requirements.minecraftRange();
        if (range == null) return Compat.UNKNOWN;

        VersionPredicate.Verdict verdict = VersionPredicate.test(
                range, mcVersion, entry.requirements.usesMavenRanges());
        if (verdict == VersionPredicate.Verdict.CONFLICTS) return Compat.WRONG_VERSION;
        if (verdict == VersionPredicate.Verdict.MATCHES) return Compat.MATCHES;
        return Compat.UNKNOWN;
    }

    /**
     * Whether a jar built for one loader runs on another.
     *
     * <b>Quilt loads Fabric mods</b>, which is the whole of its compatibility promise and the
     * reason nearly every Quilt jar also ships a `fabric.mod.json`. Nothing else crosses: a Forge
     * jar does not run on NeoForge despite the name, because the coordinates and the entry points
     * changed, and a NeoForge jar certainly does not run on Forge.
     */
    private static boolean compatibleLoaders(String modLoader, String profileLoader) {
        if (modLoader.equals(profileLoader)) return true;
        return ModRequirements.LOADER_QUILT.equals(profileLoader)
                && ModRequirements.LOADER_FABRIC.equals(modLoader);
    }

    /** The loader id {@link ModRequirements} uses, from the one the mod browser's target carries. */
    public static String loaderOf(String modrinthLoaderId) {
        if (modrinthLoaderId == null) return null;
        String id = modrinthLoaderId.toLowerCase(java.util.Locale.ROOT);
        if (id.equals("fabric")) return ModRequirements.LOADER_FABRIC;
        if (id.equals("quilt")) return ModRequirements.LOADER_QUILT;
        if (id.equals("neoforge")) return ModRequirements.LOADER_NEOFORGE;
        if (id.equals("forge")) return ModRequirements.LOADER_FORGE;
        return null;
    }
}
