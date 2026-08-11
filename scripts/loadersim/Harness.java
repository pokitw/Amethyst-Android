// In the package under test: the parsers are package-private and widening them so a harness could
// see them would be the wrong way round.
package net.kdt.pojavlaunch.modloaders;

import net.kdt.pojavlaunch.modloaders.LoaderIndex.Build;
import net.kdt.pojavlaunch.modloaders.LoaderIndex.GameVersion;
import net.kdt.pojavlaunch.modloaders.LoaderIndex.Index;
import net.kdt.pojavlaunch.modloaders.LoaderIndex.Loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the shipped loader index against the shapes those four APIs actually send.
 *
 * None of meta.fabricmc.net, meta.quiltmc.org, maven.minecraftforge.net or maven.neoforged.net is
 * reachable from the build container, so the parse is the only checkable part, and it is the part
 * where being wrong is silent: a Forge version split on the wrong hyphen files builds under a
 * Minecraft version that has never existed, and a NeoForge version read by the wrong rule hides
 * every build it has.
 *
 * The fixtures are real version strings, including the awkward historical ones.
 */
public class Harness {
    private static int failures = 0;
    private static int checks = 0;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    private static String maven(String... versions) {
        StringBuilder xml = new StringBuilder(
                "<metadata><groupId>net.minecraftforge</groupId><versioning><versions>");
        for (String version : versions) xml.append("<version>").append(version).append("</version>");
        return xml.append("</versions></versioning></metadata>").toString();
    }

    // ---------------------------------------------------------------- Forge

    private static void forge() {
        List<Build> builds = LoaderIndex.parseForge(maven(
                "1.7.10-10.13.4.1614-1.7.10",
                "1.12.2-14.23.5.2859",
                "1.20.1-47.2.0",
                "1.20.1-47.3.0",
                "1.21.1-52.0.20"));
        check(builds.size() == 5, "expected 5 Forge builds, got " + builds.size());

        // The historical shape. Splitting on the LAST hyphen calls the Minecraft version "1614",
        // which is the mistake this fixture exists to catch.
        Build legacy = find(builds, "1.7.10-10.13.4.1614-1.7.10");
        check(legacy != null, "the 1.7.10 build was dropped");
        check(legacy != null && "1.7.10".equals(legacy.gameVersion),
                "1.7.10 Forge was filed under " + (legacy == null ? "null" : legacy.gameVersion));
        check(legacy != null && "10.13.4.1614-1.7.10".equals(legacy.label),
                "the 1.7.10 label was " + (legacy == null ? "null" : legacy.label));

        Build modern = find(builds, "1.20.1-47.2.0");
        check(modern != null && "1.20.1".equals(modern.gameVersion), "1.20.1 Forge misfiled");
        check(modern != null && "47.2.0".equals(modern.label),
                "the label shown should be the Forge version, not the whole id");
        // The download task is handed the whole maven id, not the label.
        check(modern != null && "1.20.1-47.2.0".equals(modern.loaderVersion),
                "the installer id was not the full maven version");

        // Maven lists oldest first; the screen wants newest first.
        check("1.21.1-52.0.20".equals(builds.get(0).loaderVersion),
                "Forge builds were not reversed to newest first");

        // Nothing that is not a version may become one.
        List<Build> junk = LoaderIndex.parseForge(maven("", "-", "1.20.1-", "noHyphenAtAll"));
        check(junk.isEmpty(), "junk maven entries produced " + junk.size() + " builds");
        check(LoaderIndex.parseForge(null).isEmpty(), "a null document produced builds");
        check(LoaderIndex.parseForge("not xml at all").isEmpty(), "a non-XML body produced builds");
    }

    // ---------------------------------------------------------------- NeoForge

    private static void neoforge() {
        // NeoForge states the Minecraft version nowhere: it is encoded in the build number, and
        // their first line kept Forge's numbering instead. Both rules are checked here because
        // getting either wrong hides every build for those versions.
        check("1.20.1".equals(LoaderIndex.neoForgeGameVersion("47.1.106")),
                "the 1.20.1 NeoForge line was not recognised");
        check("1.20.2".equals(LoaderIndex.neoForgeGameVersion("20.2.88")),
                "20.2.88 did not read as 1.20.2");
        check("1.21".equals(LoaderIndex.neoForgeGameVersion("21.0.167")),
                "21.0.167 did not read as 1.21, with the trailing zero dropped");
        check("1.21.1".equals(LoaderIndex.neoForgeGameVersion("21.1.66")),
                "21.1.66 did not read as 1.21.1");
        check("1.20.4".equals(LoaderIndex.neoForgeGameVersion("20.4.237")),
                "20.4.237 did not read as 1.20.4");
        // A beta carries a suffix on the patch, which must not confuse the minor.
        check("1.21.1".equals(LoaderIndex.neoForgeGameVersion("21.1.1-beta")),
                "a beta suffix broke the version derivation");
        check(LoaderIndex.neoForgeGameVersion("nonsense") == null, "nonsense derived a version");
        check(LoaderIndex.neoForgeGameVersion("1") == null, "a single number derived a version");
        check(LoaderIndex.neoForgeGameVersion("19.2.3") == null,
                "a major below the scheme's start derived a version");

        List<Build> builds = LoaderIndex.parseNeoForge(maven(
                "47.1.106", "20.2.88", "21.1.66", "21.1.1-beta"));
        check(builds.size() == 4, "expected 4 NeoForge builds, got " + builds.size());
        Build beta = find(builds, "21.1.1-beta");
        check(beta != null && !beta.stable, "a beta was marked stable");
        Build release = find(builds, "21.1.66");
        check(release != null && release.stable, "a release was marked unstable");
    }

    // ---------------------------------------------------------------- Fabric and Quilt

    private static void fabric() {
        List<String[]> games = LoaderIndex.parseFabricGames(
                "[{\"version\":\"1.21.1\",\"stable\":true},"
                        + "{\"version\":\"24w33a\",\"stable\":false},"
                        + "{\"version\":\"1.20.1\",\"stable\":true}]");
        check(games.size() == 3, "expected 3 game versions, got " + games.size());
        check("1.21.1".equals(games.get(0)[0]), "the newest game version was not first");
        check("true".equals(games.get(0)[1]), "a release was not marked stable");
        check("false".equals(games.get(1)[1]), "a snapshot was marked stable");

        List<Build> loaders = LoaderIndex.parseFabricLoaders(Loader.FABRIC,
                "[{\"version\":\"0.16.5\",\"stable\":true},"
                        + "{\"version\":\"0.16.4\",\"stable\":false}]");
        check(loaders.size() == 2, "expected 2 loader builds, got " + loaders.size());
        // A Fabric loader build belongs to no particular game version, and saying otherwise here
        // would be inventing a cross product before anything asked for one.
        check(loaders.get(0).gameVersion == null,
                "a Fabric loader build claimed a game version of its own");
        check(loaders.get(0).stable && !loaders.get(1).stable, "loader stability was not read");

        // Quilt's v3 nests the same object under "loader" on some routes.
        List<Build> quilt = LoaderIndex.parseFabricLoaders(Loader.QUILT,
                "[{\"loader\":{\"version\":\"0.26.0\",\"stable\":true}}]");
        check(quilt.size() == 1, "the nested Quilt shape was not read");
        check(quilt.size() == 1 && "0.26.0".equals(quilt.get(0).label),
                "the nested Quilt version was " + (quilt.isEmpty() ? "none" : quilt.get(0).label));

        // Nothing that is not a version may become one.
        check(LoaderIndex.parseFabricGames("not json").isEmpty(), "junk produced game versions");
        check(LoaderIndex.parseFabricGames(null).isEmpty(), "null produced game versions");
        check(LoaderIndex.parseFabricLoaders(Loader.FABRIC, "[null,{},{\"version\":\"\"}]").isEmpty(),
                "empty objects produced loader builds");
    }

    // ---------------------------------------------------------------- the index

    private static Index build() {
        Map<String, Boolean> games = new LinkedHashMap<>();
        games.put("1.21.1", true);
        games.put("24w33a", false);
        games.put("1.20.1", true);
        Map<Loader, List<Build>> byLoader = new LinkedHashMap<>();
        byLoader.put(Loader.FABRIC, LoaderIndex.parseFabricLoaders(Loader.FABRIC,
                "[{\"version\":\"0.17.0\",\"stable\":false},{\"version\":\"0.16.5\",\"stable\":true}]"));
        byLoader.put(Loader.FORGE, LoaderIndex.parseForge(maven("1.20.1-47.2.0", "1.19.2-43.2.0")));
        byLoader.put(Loader.NEOFORGE, LoaderIndex.parseNeoForge(maven("21.1.66")));
        return LoaderIndex.index(games, byLoader, new ArrayList<Loader>());
    }

    private static void indexing() {
        Index index = build();

        // Fabric's builds land on every game version it listed, including the snapshot.
        GameVersion snapshot = row(index, "24w33a");
        check(snapshot != null, "the snapshot lost its row");
        check(snapshot != null && snapshot.has(Loader.FABRIC), "Fabric did not reach the snapshot");
        check(snapshot != null && !snapshot.release, "the snapshot was marked a release");
        check(snapshot != null && !snapshot.has(Loader.FORGE),
                "Forge appeared on a snapshot it does not build for");

        // A Forge-only Minecraft version still gets a row, even though Fabric never named it.
        GameVersion forgeOnly = row(index, "1.19.2");
        check(forgeOnly != null, "1.19.2 lost its row for not being in Fabric's game list");
        check(forgeOnly != null && forgeOnly.has(Loader.FORGE), "1.19.2 lost its Forge build");
        check(forgeOnly != null && forgeOnly.release,
                "a Forge-only version was not treated as a release");

        // The row people came for.
        GameVersion current = row(index, "1.20.1");
        check(current != null && current.loaderCount() == 2,
                "1.20.1 offered " + (current == null ? "no" : current.loaderCount()) + " loaders");
        Build fabric = current == null ? null : current.best(Loader.FABRIC);
        // Newest STABLE, not newest. Fabric lists newest first, so the unstable 0.17.0 is above
        // the stable 0.16.5, and a default that simply took the head of the list would install a
        // prerelease on somebody who asked for nothing of the sort. The fixture is deliberately
        // in that order: with the stable one first the two answers coincide and this checks
        // nothing, which is exactly the hole a mutation run found here.
        check(fabric != null && "0.16.5".equals(fabric.label),
                "the default Fabric build was " + (fabric == null ? "null" : fabric.label));
        // And when nothing is stable, a row still has something to offer rather than going dead.
        List<Build> unstableOnly = LoaderIndex.parseFabricLoaders(Loader.FABRIC,
                "[{\"version\":\"0.99.0\",\"stable\":false}]");
        Map<Loader, List<Build>> onlyBeta = new LinkedHashMap<>();
        onlyBeta.put(Loader.FABRIC, unstableOnly);
        Map<String, Boolean> oneGame = new LinkedHashMap<>();
        oneGame.put("1.20.1", true);
        GameVersion betaRow = row(LoaderIndex.index(oneGame, onlyBeta, new ArrayList<Loader>()),
                "1.20.1");
        check(betaRow != null && betaRow.best(Loader.FABRIC) != null,
                "a version with only prerelease builds offered nothing at all");
        check(fabric != null && "1.20.1".equals(fabric.gameVersion),
                "the Fabric build did not take the row's game version");

        // Every row offers something, or it is a row that cannot be tapped.
        for (GameVersion version : index.versions) {
            check(version.loaderCount() > 0, version.id + " has a row and no loaders");
            for (Loader loader : Loader.values()) {
                if (!version.has(loader)) continue;
                Build best = version.best(loader);
                check(best != null, version.id + " says it has " + loader + " and offers nothing");
                check(best != null && best.loaderVersion != null && !best.loaderVersion.isEmpty(),
                        version.id + " " + loader + " has no version to install");
            }
        }

        // A loader that could not be fetched is named rather than silently absent.
        List<Loader> down = new ArrayList<>();
        down.add(Loader.QUILT);
        Index partial = LoaderIndex.index(new LinkedHashMap<String, Boolean>(),
                new LinkedHashMap<Loader, List<Build>>(), down);
        check(partial.isEmpty(), "an index with no sources was not empty");
        check(partial.unavailable.contains(Loader.QUILT), "a failed loader was not reported");
    }

    private static void ordering() {
        // String order puts 1.9 above 1.10, which is the classic way a version list goes wrong.
        check(LoaderIndex.compareVersions("1.10", "1.9") > 0, "1.10 did not sort above 1.9");
        check(LoaderIndex.compareVersions("1.20.1", "1.20") > 0, "1.20.1 did not sort above 1.20");
        check(LoaderIndex.compareVersions("1.20.1", "1.20.1") == 0, "equal versions differed");
        check(LoaderIndex.compareVersions("1.21", "1.20.4") > 0, "1.21 did not sort above 1.20.4");
        // The one that string comparison gets wrong in the other direction.
        check(LoaderIndex.compareVersions("1.9", "1.10") < 0, "1.9 did not sort below 1.10");
        check(LoaderIndex.compareVersions("1.100", "1.99") > 0, "1.100 did not sort above 1.99");
    }

    private static GameVersion row(Index index, String id) {
        for (GameVersion version : index.versions) if (version.id.equals(id)) return version;
        return null;
    }

    private static Build find(List<Build> builds, String loaderVersion) {
        for (Build build : builds) if (build.loaderVersion.equals(loaderVersion)) return build;
        return null;
    }

    public static void main(String[] args) {
        forge();
        neoforge();
        fabric();
        indexing();
        ordering();

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
        System.out.println("loader index OK");
    }
}
