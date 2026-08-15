package net.kdt.pojavlaunch.modmeta;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Build real jars on disk and read them back through the shipped classes.
 *
 * The metadata in the fixtures is copied from the shape mods actually ship, not from what the
 * parser expects: writing both halves from the same assumption in the same hour is how a harness
 * ends up agreeing with the bug (16.20). Everything driven here is the real class.
 */
public class Harness {

    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<>();
    private static File sDir;

    public static void main(String[] args) throws Exception {
        sDir = new File(args.length > 0 ? args[0] : "build/jars");
        sDir.mkdirs();

        semverGrammar();
        mavenGrammar();
        neverConfidentlyWrong();
        formatOrder();
        fabricParsing();
        quiltParsing();
        tomlParsing();
        legacyParsing();
        theGoodFolder();
        theBrokenFolder();
        fabricApiModules();
        disabledJars();
        loaderMismatch();
        nothingKnown();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String failure : FAILURES) System.out.println("FAIL: " + failure);
            System.out.println(FAILURES.size() + " FAILURES");
            System.exit(1);
        }
        System.out.println("all passed");
    }

    /* ------------------------------------------------------------------ the two grammars */

    private static void semverGrammar() {
        // Both directions of every operator, because a one-sided sweep passes a comparison
        // written the wrong way round (16.22).
        semver(">=1.20.1", "1.20.1", "MATCHES");
        semver(">=1.20.1", "1.21", "MATCHES");
        semver(">=1.20.1", "1.20", "CONFLICTS");
        semver(">1.20.1", "1.20.1", "CONFLICTS");
        semver(">1.20.1", "1.20.2", "MATCHES");
        semver("<=1.20.1", "1.20.1", "MATCHES");
        semver("<=1.20.1", "1.20.2", "CONFLICTS");
        semver("<1.21", "1.20.6", "MATCHES");
        semver("<1.21", "1.21", "CONFLICTS");

        // A conjunction, which is the single most common shape a mod writes and the one the OR
        // separator was originally about to break: read as alternatives, 1.21 would have passed.
        semver(">=1.20.1 <1.21", "1.20.4", "MATCHES");
        semver(">=1.20.1 <1.21", "1.21", "CONFLICTS");
        semver(">=1.20.1 <1.21", "1.20", "CONFLICTS");
        semver(">=1.20.1, <1.21", "1.21", "CONFLICTS");

        // An OR, which is what an array of predicates becomes.
        semver("1.20.1 || 1.21", "1.20.1", "MATCHES");
        semver("1.20.1 || 1.21", "1.21", "MATCHES");
        semver("1.20.1 || 1.21", "1.20.2", "CONFLICTS");

        // Wildcards and bare versions. A bare version is exact: a mod saying 1.20 does not mean
        // the whole 1.20 line, and reading it as a prefix quietly accepts 1.20.6.
        semver("1.20.x", "1.20.4", "MATCHES");
        semver("1.20.x", "1.21", "CONFLICTS");
        semver("1.20.*", "1.20", "MATCHES");
        semver("*", "1.21", "MATCHES");
        semver("1.20", "1.20", "MATCHES");
        semver("1.20", "1.20.1", "CONFLICTS");
        semver("1.20.0", "1.20", "MATCHES");
        semver("=1.20.1", "1.20.1", "MATCHES");
        semver("=1.20.1", "1.20.2", "CONFLICTS");
        semver("!=1.20.1", "1.20.2", "MATCHES");
        semver("!=1.20.1", "1.20.1", "CONFLICTS");

        // Tilde bumps the patch place, caret the leftmost non-zero one.
        semver("~1.20.1", "1.20.4", "MATCHES");
        semver("~1.20.1", "1.20.0", "CONFLICTS");
        semver("~1.20.1", "1.21", "CONFLICTS");
        semver("^1.20.1", "1.21", "MATCHES");
        semver("^1.20.1", "2.0", "CONFLICTS");
        semver("^1.20.1", "1.20.0", "CONFLICTS");
        semver("^0.2.3", "0.2.9", "MATCHES");
        semver("^0.2.3", "0.3", "CONFLICTS");
        semver("^0.0.3", "0.0.3", "MATCHES");
        semver("^0.0.3", "0.0.4", "CONFLICTS");
        semver("^0.0.0", "0.0.0", "UNKNOWN");
    }

    private static void mavenGrammar() {
        maven("[1.20.1,1.21)", "1.20.1", "MATCHES");
        maven("[1.20.1,1.21)", "1.20.6", "MATCHES");
        maven("[1.20.1,1.21)", "1.21", "CONFLICTS");
        maven("[1.20.1,1.21)", "1.20", "CONFLICTS");
        maven("[1.20.1,1.21]", "1.21", "MATCHES");
        maven("(1.20.1,1.21)", "1.20.1", "CONFLICTS");
        maven("[1.20,)", "1.21", "MATCHES");
        maven("[1.20,)", "1.19", "CONFLICTS");
        maven("(,1.21]", "1.20", "MATCHES");
        maven("(,1.21]", "1.21.1", "CONFLICTS");
        maven("[1.20.1]", "1.20.1", "MATCHES");
        maven("[1.20.1]", "1.20.2", "CONFLICTS");

        // A union: the comma inside a range is not the comma between two of them, and splitting
        // on every comma turns one range into two broken halves that still parse.
        maven("[1.19,1.20),[1.21,1.22)", "1.19.4", "MATCHES");
        maven("[1.19,1.20),[1.21,1.22)", "1.21.1", "MATCHES");
        maven("[1.19,1.20),[1.21,1.22)", "1.20.1", "CONFLICTS");

        // Maven's bare version is a soft recommendation and constrains nothing. Reading it as
        // equality would condemn a large number of perfectly fine Forge mods.
        maven("1.20.1", "1.21", "MATCHES");
        maven("", "1.21", "MATCHES");
    }

    /**
     * The rule the whole feature hangs on: what is not understood is never reported.
     *
     * Each of these is a real shape from the wild that this parser deliberately does not model,
     * and every one of them must be UNKNOWN rather than CONFLICTS.
     */
    private static void neverConfidentlyWrong() {
        String[][] unknowns = {
            {">=1.21-pre1", "1.21"},        // a pre-release bound
            {">=24w14a", "1.21"},           // a snapshot bound
            {">=1.20.1-rc1", "1.20.1"},     // a release candidate bound
            {">=1.20.1+build.4", "1.21"},   // build metadata
            {">=", "1.21"},                 // an operator with nothing after it
            {"[1.20.1", "1.21"},            // an unclosed maven range
            {"nonsense", "1.21"},           // not a version at all
            {"1.20.1 || garbage", "1.19"},  // an alternative nobody understood, which might have
                                            // been the one that matched
        };
        for (String[] pair : unknowns) {
            boolean isMaven = pair[0].startsWith("[") || pair[0].startsWith("(");
            VersionPredicate.Verdict verdict =
                    VersionPredicate.test(pair[0], pair[1], isMaven);
            check(verdict != VersionPredicate.Verdict.CONFLICTS,
                    "\"" + pair[0] + "\" against " + pair[1] + " confidently said CONFLICTS");
        }
        // A conjunction with one unreadable term cannot be a match either, since that term might
        // have been the one that ruled the version out.
        check(VersionPredicate.test(">=1.20 <24w14a", "1.21", false)
                        == VersionPredicate.Verdict.UNKNOWN,
                "a conjunction with an unreadable term did not come out unknown");
        // But a conjunction where a READABLE term fails is still a definite conflict, or the
        // whole feature does nothing.
        check(VersionPredicate.test(">=1.21 <24w14a", "1.20", false)
                        == VersionPredicate.Verdict.CONFLICTS,
                "a conjunction with a definite failure went quiet");
        check(VersionPredicate.test("anything", null, false)
                        == VersionPredicate.Verdict.UNKNOWN,
                "a null version was compared against rather than skipped");
    }

    /* ------------------------------------------------------------------ the four formats */

    /**
     * A Quilt jar almost always also ships fabric.mod.json and a NeoForge jar almost always also
     * ships the older mods.toml. Read in the obvious order both report the older loader, and are
     * then judged against the wrong loader's version grammar.
     */
    private static void formatOrder() {
        File quilt = jar("order-quilt.jar",
                "quilt.mod.json", QUILT_JSON,
                "fabric.mod.json", FABRIC_JSON);
        check(ModRequirements.LOADER_QUILT.equals(read(quilt).loader),
                "a Quilt jar carrying a Fabric file read as Fabric");

        File neo = jar("order-neo.jar",
                "META-INF/neoforge.mods.toml", NEOFORGE_TOML,
                "META-INF/mods.toml", FORGE_TOML);
        ModRequirements requirements = read(neo);
        check(ModRequirements.LOADER_NEOFORGE.equals(requirements.loader),
                "a NeoForge jar carrying a mods.toml read as Forge");
        check("[1.21,1.22)".equals(requirements.minecraftRange()),
                "the wrong file's version range was read: " + requirements.minecraftRange());
    }

    private static void fabricParsing() {
        ModRequirements r = read(jar("fabric.jar", "fabric.mod.json", FABRIC_JSON));
        check("sodium".equals(r.modId), "fabric modId: " + r.modId);
        check(">=1.20.1 <1.21".equals(r.minecraftRange()),
                "fabric minecraft range: " + r.minecraftRange());
        check(ids(r, true).contains("fabric-api"), "fabric required deps: " + ids(r, true));
        // Recommended is not required, and acting on a suggestion would put jars in a folder
        // nobody chose.
        check(!ids(r, true).contains("cloth-config"), "a recommendation was read as required");

        // The array form of a predicate is an OR. Read as a conjunction it is unsatisfiable and
        // every mod written this way reports a conflict.
        ModRequirements array = read(jar("fabric-array.jar", "fabric.mod.json",
                "{\"schemaVersion\":1,\"id\":\"arraymod\",\"version\":\"1.0\","
                        + "\"depends\":{\"minecraft\":[\"1.20.1\",\"1.21\"]}}"));
        check(VersionPredicate.test(array.minecraftRange(), "1.21", false)
                        == VersionPredicate.Verdict.MATCHES,
                "an array of versions was not read as alternatives: " + array.minecraftRange());
        check(VersionPredicate.test(array.minecraftRange(), "1.19", false)
                        == VersionPredicate.Verdict.CONFLICTS,
                "an array of versions matched something outside it");
    }

    private static void quiltParsing() {
        ModRequirements r = read(jar("quilt.jar", "quilt.mod.json", QUILT_JSON));
        check("quiltmod".equals(r.modId), "quilt modId: " + r.modId);
        check("1.21.x".equals(r.minecraftRange()), "quilt minecraft range: " + r.minecraftRange());
        check(ids(r, true).contains("quilted_fabric_api"), "quilt deps: " + ids(r, true));
        check(!ids(r, true).contains("optionalmod"), "a Quilt optional dep was read as required");
        check(r.provides.contains("quiltmod-compat"), "quilt provides: " + r.provides);
    }

    private static void tomlParsing() {
        ModRequirements forge = read(jar("forge.jar", "META-INF/mods.toml", FORGE_TOML));
        check("jei".equals(forge.modId), "forge modId: " + forge.modId);
        check("[1.20.1,1.21)".equals(forge.minecraftRange()),
                "forge minecraft range: " + forge.minecraftRange());
        check(ids(forge, true).contains("forge"), "forge deps: " + ids(forge, true));
        check(!ids(forge, true).contains("jade"), "mandatory=false was read as required");

        // NeoForge renamed mandatory to type. Reading only one spelling makes every dependency in
        // the other format either always required or never, and both are silent.
        ModRequirements neo = read(jar("neo.jar", "META-INF/neoforge.mods.toml", NEOFORGE_TOML));
        check(ids(neo, true).contains("neoforge"), "neoforge required deps: " + ids(neo, true));
        check(!ids(neo, true).contains("someoptional"),
                "type=\"optional\" was read as required");
        // A mod declaring what it will NOT run beside must never be reported as needing it.
        check(!ids(neo, true).contains("oldmod"),
                "type=\"incompatible\" was read as a requirement");

        // A block description is skipped whole. Read as prose, the brackets and keys inside one
        // become a table and a dependency that the mod never declared.
        check("[1.21,1.22)".equals(neo.minecraftRange()),
                "a block description ate the keys after it: " + neo.minecraftRange());
        check(!ids(neo, true).contains("decoy") && !ids(neo, false).contains("decoy"),
                "a dependency was invented out of a mod's own description: " + ids(neo, true));
        check("neomod".equals(neo.modId),
                "a block description overwrote the mod's id: " + neo.modId);
    }

    private static void legacyParsing() {
        ModRequirements r = read(jar("legacy.jar", "mcmod.info", LEGACY_INFO));
        check("oldmod".equals(r.modId), "legacy modId: " + r.modId);
        // The only format with a standalone version field, turned into a dependency so everything
        // downstream sees one shape.
        check("1.7.10".equals(r.minecraftRange()), "legacy mcversion: " + r.minecraftRange());
        check(ids(r, true).contains("basemod"), "legacy required mods: " + ids(r, true));
    }

    /* ------------------------------------------------------------------ the folder */

    /**
     * The assertion that matters most, and the one a harness built only from broken fixtures
     * cannot make: a folder of real, mutually consistent mods produces exactly nothing.
     */
    private static void theGoodFolder() {
        List<ModGraph.Entry> entries = Arrays.asList(
                entry("sodium", jar("good-sodium.jar", "fabric.mod.json", FABRIC_JSON), true),
                entry("fabric-api", jar("good-api.jar", "fabric.mod.json", FABRIC_API_JSON), true),
                entry("cloth", jar("good-cloth.jar", "fabric.mod.json",
                        "{\"schemaVersion\":1,\"id\":\"cloth-config\",\"version\":\"11.1\","
                                + "\"depends\":{\"minecraft\":\">=1.20\",\"fabricloader\":\">=0.15\"}}"),
                        true));
        List<ModGraph.Result> results =
                ModGraph.resolve(entries, "1.20.4", ModRequirements.LOADER_FABRIC);
        for (ModGraph.Result result : results) {
            check(!result.hasProblem(),
                    "a healthy folder warned about " + result.key + ": " + result.compat
                            + " missing " + result.missing);
        }
        check(results.size() == 3, "the graph lost a row");
    }

    /** And the other half of the same claim: it does fire when something really is wrong. */
    private static void theBrokenFolder() {
        List<ModGraph.Entry> entries = Arrays.asList(
                entry("sodium", jar("bad-sodium.jar", "fabric.mod.json", FABRIC_JSON), true),
                entry("jei", jar("bad-jei.jar", "META-INF/mods.toml", FORGE_TOML), true));
        List<ModGraph.Result> results =
                ModGraph.resolve(entries, "1.21", ModRequirements.LOADER_FABRIC);

        ModGraph.Result sodium = find(results, "sodium");
        check(sodium.compat == ModGraph.Compat.WRONG_VERSION,
                "a Fabric mod capped below the profile's version read as " + sodium.compat);
        check(sodium.missing.contains("fabric-api"),
                "a genuinely absent dependency was not reported: " + sodium.missing);

        // The loader is named before the version, because a Forge jar in a Fabric profile is
        // wrong whatever version it declares and saying so names the actual problem.
        ModGraph.Result jei = find(results, "jei");
        check(jei.compat == ModGraph.Compat.WRONG_LOADER,
                "a Forge jar in a Fabric profile read as " + jei.compat);
    }

    /**
     * Fabric API is roughly forty modules that each declare what they provide, and mods depend on
     * those module ids. Ignore provides and nearly every Fabric mod reports a missing dependency,
     * which looks exactly like the feature working hard.
     */
    private static void fabricApiModules() {
        List<ModGraph.Entry> entries = Arrays.asList(
                entry("api", jar("mod-api.jar", "fabric.mod.json", FABRIC_API_JSON), true),
                entry("dependent", jar("mod-dependent.jar", "fabric.mod.json",
                        "{\"schemaVersion\":1,\"id\":\"dependent\",\"version\":\"1.0\","
                                + "\"depends\":{\"fabric-item-api-v1\":\"*\"}}"), true));
        ModGraph.Result dependent = find(
                ModGraph.resolve(entries, "1.20.4", ModRequirements.LOADER_FABRIC), "dependent");
        check(dependent.missing.isEmpty(),
                "a provided module id was reported missing: " + dependent.missing);

        ModGraph.Result api = find(
                ModGraph.resolve(entries, "1.20.4", ModRequirements.LOADER_FABRIC), "api");
        check(api.requiredBy.contains("dependent"),
                "the provider was not told what needs it: " + api.requiredBy);
    }

    /**
     * A disabled jar is on disk and absent from the game. Counting it means switching Fabric API
     * off silently stops warning about the twenty mods that need it, which is the exact case this
     * exists for.
     */
    private static void disabledJars() {
        List<ModGraph.Entry> entries = Arrays.asList(
                entry("api", jar("off-api.jar", "fabric.mod.json", FABRIC_API_JSON), false),
                entry("dependent", jar("off-dependent.jar", "fabric.mod.json",
                        "{\"schemaVersion\":1,\"id\":\"dependent\",\"version\":\"1.0\","
                                + "\"depends\":{\"fabric-item-api-v1\":\"*\"}}"), true));
        List<ModGraph.Result> results =
                ModGraph.resolve(entries, "1.20.4", ModRequirements.LOADER_FABRIC);
        check(find(results, "dependent").missing.contains("fabric-item-api-v1"),
                "a disabled jar was counted as satisfying a dependency");
        // And a disabled mod's own requirements are not the player's problem, since the game will
        // never load it.
        List<ModGraph.Entry> reversed = Arrays.asList(
                entry("api", jar("off2-api.jar", "fabric.mod.json", FABRIC_API_JSON), true),
                entry("dependent", jar("off2-dependent.jar", "fabric.mod.json",
                        "{\"schemaVersion\":1,\"id\":\"dependent\",\"version\":\"1.0\","
                                + "\"depends\":{\"absentmod\":\"*\"}}"), false));
        check(find(ModGraph.resolve(reversed, "1.20.4", ModRequirements.LOADER_FABRIC),
                        "dependent").missing.isEmpty(),
                "a switched-off mod's own missing dependency was reported");
    }

    /** Quilt loads Fabric mods. Nothing else crosses, the Forge and NeoForge names notwithstanding. */
    private static void loaderMismatch() {
        File fabric = jar("cross-fabric.jar", "fabric.mod.json", FABRIC_JSON);
        check(compat(fabric, "1.20.4", ModRequirements.LOADER_QUILT) != ModGraph.Compat.WRONG_LOADER,
                "a Fabric mod was refused on a Quilt profile");
        check(compat(fabric, "1.20.4", ModRequirements.LOADER_FORGE) == ModGraph.Compat.WRONG_LOADER,
                "a Fabric mod was accepted on a Forge profile");

        File forge = jar("cross-forge.jar", "META-INF/mods.toml", FORGE_TOML);
        check(compat(forge, "1.20.1", ModRequirements.LOADER_NEOFORGE)
                        == ModGraph.Compat.WRONG_LOADER,
                "a Forge jar was accepted on a NeoForge profile");
        File neo = jar("cross-neo.jar", "META-INF/neoforge.mods.toml", NEOFORGE_TOML);
        check(compat(neo, "1.21", ModRequirements.LOADER_FORGE) == ModGraph.Compat.WRONG_LOADER,
                "a NeoForge jar was accepted on a Forge profile");
        check(compat(neo, "1.21", ModRequirements.LOADER_NEOFORGE) == ModGraph.Compat.MATCHES,
                "a NeoForge jar was refused on its own loader");
    }

    /**
     * Nothing known means nothing checked.
     *
     * A snapshot profile has no version this launcher can parse, and a vanilla profile has no
     * loader. Neither is a profile that every installed mod conflicts with, and getting this
     * backwards is a screen where everything is wrong.
     */
    private static void nothingKnown() {
        File mod = jar("unknown-mod.jar", "fabric.mod.json", FABRIC_JSON);
        check(compat(mod, null, ModRequirements.LOADER_FABRIC) == ModGraph.Compat.UNKNOWN,
                "a profile with no known version judged a mod anyway");
        check(compat(mod, "1.20.4", null) != ModGraph.Compat.WRONG_LOADER,
                "a profile with no known loader refused a mod");

        // An unreadable jar, which is what a resource pack renamed to .jar or a corrupt download
        // looks like. It has to list, and it has to be judged as nothing at all.
        List<ModGraph.Entry> entries = Arrays.asList(
                new ModGraph.Entry("junk", null, true));
        ModGraph.Result result =
                ModGraph.resolve(entries, "1.20.4", ModRequirements.LOADER_FABRIC).get(0);
        check(result.compat == ModGraph.Compat.UNKNOWN && !result.hasProblem(),
                "an unreadable jar was reported as a problem");

        // A mod with no metadata about Minecraft at all says nothing, rather than conflicting.
        File silent = jar("silent.jar", "fabric.mod.json",
                "{\"schemaVersion\":1,\"id\":\"silent\",\"version\":\"1.0\"}");
        check(compat(silent, "1.20.4", ModRequirements.LOADER_FABRIC) == ModGraph.Compat.UNKNOWN,
                "a mod that declares no version was judged");
    }

    /* ------------------------------------------------------------------ fixtures */

    /** Sodium's shape: a version cap, a real dependency, and a recommendation that is not one. */
    private static final String FABRIC_JSON =
            "{\"schemaVersion\":1,\"id\":\"sodium\",\"version\":\"0.5.8\","
                    + "\"name\":\"Sodium\","
                    + "\"depends\":{\"minecraft\":\">=1.20.1 <1.21\",\"fabricloader\":\">=0.15.0\","
                    + "\"java\":\">=17\",\"fabric-api\":\"*\"},"
                    + "\"recommends\":{\"cloth-config\":\"*\"}}";

    /** Fabric API's shape: the module ids are in provides, not in the id. */
    private static final String FABRIC_API_JSON =
            "{\"schemaVersion\":1,\"id\":\"fabric-api\",\"version\":\"0.97.0\","
                    + "\"provides\":[\"fabric\",\"fabric-item-api-v1\",\"fabric-networking-api-v1\"],"
                    + "\"depends\":{\"minecraft\":\">=1.20\",\"fabricloader\":\">=0.15\"}}";

    private static final String QUILT_JSON =
            "{\"schema_version\":1,\"quilt_loader\":{\"group\":\"com.example\",\"id\":\"quiltmod\","
                    + "\"version\":\"1.0.0\",\"provides\":[\"quiltmod-compat\"],"
                    + "\"metadata\":{\"name\":\"Quilt Mod\"},"
                    + "\"depends\":[{\"id\":\"minecraft\",\"versions\":\"1.21.x\"},"
                    + "\"quilted_fabric_api\","
                    + "{\"id\":\"optionalmod\",\"versions\":\"*\",\"optional\":true}]}}";

    private static final String FORGE_TOML =
            "modLoader=\"javafml\"\n"
                    + "loaderVersion=\"[47,)\"\n"
                    + "license=\"MIT\"\n"
                    + "[[mods]]\n"
                    + "modId=\"jei\"\n"
                    + "version=\"${file.jarVersion}\"\n"
                    + "displayName=\"Just Enough Items\"\n"
                    + "[[dependencies.jei]]\n"
                    + "    modId=\"forge\"\n"
                    + "    mandatory=true\n"
                    + "    versionRange=\"[47,)\"\n"
                    + "    side=\"BOTH\"\n"
                    + "[[dependencies.jei]]\n"
                    + "    modId=\"minecraft\"\n"
                    + "    mandatory=true\n"
                    + "    versionRange=\"[1.20.1,1.21)\"\n"
                    + "[[dependencies.jei]]\n"
                    + "    modId=\"jade\"\n"
                    + "    mandatory=false\n"
                    + "    versionRange=\"[11,)\"\n";

    private static final String NEOFORGE_TOML =
            "modLoader=\"javafml\"\n"
                    + "loaderVersion=\"[1,)\"\n"
                    + "[[mods]]\n"
                    + "modId=\"neomod\"\n"
                    // A block description carrying exactly the two shapes this file is scanned
                    // for. Descriptions really do contain markdown brackets and code, and read as
                    // prose rather than skipped they invent a dependency nobody declared.
                    + "description='''\n"
                    + "Configure it in the config screen.\n"
                    + "[[dependencies.ghost]]\n"
                    + "modId=\"decoy\"\n"
                    + "'''\n"
                    + "[[dependencies.neomod]]\n"
                    + "    modId=\"neoforge\"\n"
                    + "    type=\"required\"\n"
                    + "    versionRange=\"[21,)\"\n"
                    + "[[dependencies.neomod]]\n"
                    + "    modId=\"minecraft\"\n"
                    + "    type=\"required\"\n"
                    + "    versionRange=\"[1.21,1.22)\"\n"
                    + "[[dependencies.neomod]]\n"
                    + "    modId=\"someoptional\"\n"
                    + "    type=\"optional\"\n"
                    + "    versionRange=\"[1,)\"\n"
                    + "[[dependencies.neomod]]\n"
                    + "    modId=\"oldmod\"\n"
                    + "    type=\"incompatible\"\n"
                    + "    versionRange=\"[1,)\"\n";

    private static final String LEGACY_INFO =
            "[{\"modid\":\"oldmod\",\"name\":\"Old Mod\",\"version\":\"1.0\","
                    + "\"mcversion\":\"1.7.10\",\"requiredMods\":[\"basemod@[1.0,)\"]}]";

    /* ------------------------------------------------------------------ plumbing */

    private static void semver(String predicate, String version, String expected) {
        VersionPredicate.Verdict verdict = VersionPredicate.test(predicate, version, false);
        check(verdict.name().equals(expected),
                "semver \"" + predicate + "\" vs " + version + ": " + verdict + " not " + expected);
    }

    private static void maven(String predicate, String version, String expected) {
        VersionPredicate.Verdict verdict = VersionPredicate.test(predicate, version, true);
        check(verdict.name().equals(expected),
                "maven \"" + predicate + "\" vs " + version + ": " + verdict + " not " + expected);
    }

    private static File jar(String name, String... entries) {
        File file = new File(sDir, name);
        try {
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file));
            try {
                for (int i = 0; i < entries.length; i += 2) {
                    out.putNextEntry(new ZipEntry(entries[i]));
                    out.write(entries[i + 1].getBytes("UTF-8"));
                    out.closeEntry();
                }
            } finally {
                out.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("could not write " + name, e);
        }
        return file;
    }

    private static ModRequirements read(File file) {
        try {
            ZipFile zip = new ZipFile(file);
            try {
                ModRequirements requirements = ModRequirements.read(zip);
                if (requirements == null) throw new RuntimeException("unreadable: " + file);
                return requirements;
            } finally {
                zip.close();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("could not read " + file, e);
        }
    }

    private static ModGraph.Entry entry(String key, File file, boolean enabled) {
        return new ModGraph.Entry(key, read(file), enabled);
    }

    private static ModGraph.Compat compat(File file, String mcVersion, String loader) {
        List<ModGraph.Entry> entries =
                Arrays.asList(new ModGraph.Entry("only", read(file), true));
        return ModGraph.resolve(entries, mcVersion, loader).get(0).compat;
    }

    private static List<String> ids(ModRequirements requirements, boolean required) {
        List<String> ids = new ArrayList<>();
        for (ModRequirements.Dep dep : requirements.dependencies) {
            if (dep.required == required) ids.add(dep.id);
        }
        return ids;
    }

    private static ModGraph.Result find(List<ModGraph.Result> results, String key) {
        for (ModGraph.Result result : results) if (result.key.equals(key)) return result;
        throw new RuntimeException("no result for " + key);
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
