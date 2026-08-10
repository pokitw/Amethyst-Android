// In the package under test on purpose: facets() and safeName() are internal helpers, and the
// alternative to reaching them from inside is widening the API so a test can see it, which is the
// wrong way round.
package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Drives the shipped Modrinth parsing against fixtures.
 *
 * Modrinth cannot be reached from the build container, so the wire format here is the documented
 * v2 contract rather than a captured response. What this harness can still prove is the half that
 * actually breaks: that the shipped parser picks the right file out of a version, survives every
 * field being absent, and chooses the version a one-tap install would install. Those are the
 * places where being wrong means the launcher quietly installs a sources jar, or a beta, or
 * crashes on a null.
 *
 * Compiled against the same stubs the rest of the Java checks use, at source 8, so what runs here
 * is the file that ships and not a copy of it.
 */
public class Harness {
    private static int failures = 0;

    private static void check(boolean condition, String message) {
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        List<ModrinthMods.File> files = ModrinthMods.parseVersions(array);

        // Seven entries in, five usable out: the one with no files and the one with no id are
        // dropped rather than returned half-built.
        check(files.size() == 5,
                "expected 5 usable versions out of 7 fixtures, got " + files.size());

        ModrinthMods.File first = files.get(0);
        check("vRELEASE1".equals(first.versionId), "first version id was " + first.versionId);

        // The primary file, not files[0]. This is the bug the modpack parser would have had here:
        // its files[0] is the sources jar in this fixture.
        check(first.fileName.endsWith("0.5.8.jar"),
                "picked " + first.fileName + " instead of the primary jar");
        check(!first.fileName.contains("sources"), "picked the sources jar");
        check("2222222222222222222222222222222222222222".equals(first.sha1),
                "took the hash of the wrong file: " + first.sha1);
        check(first.size == 900, "took the size of the wrong file: " + first.size);

        // Only the required dependency survives; the optional one is not something a launcher
        // gets to decide on someone's behalf.
        check(first.dependencies.size() == 2, "both dependencies should be parsed");
        int required = 0;
        for (ModrinthMods.Dependency dependency : first.dependencies) {
            if (dependency.isRequired()) required++;
        }
        check(required == 1, "exactly one dependency is required, counted " + required);

        // A file with no primary flag anywhere still yields the only file there is.
        ModrinthMods.File noPrimary = byId(files, "vNOPRIMARY");
        check(noPrimary != null && noPrimary.fileName.endsWith("0.5.0.jar"),
                "a version with no primary flag should fall back to the first file");

        // No hashes at all is legal and must not throw; it just means nothing to verify against.
        ModrinthMods.File noHash = byId(files, "vNOHASH");
        check(noHash != null && noHash.sha1 == null, "a missing hash should read as null");

        // Every string field null: the parser must produce an object, not an exception, and must
        // default the channel rather than leaving it null for isRelease() to dereference.
        ModrinthMods.File nulls = byId(files, "vNULLFIELDS");
        check(nulls != null, "a version with null strings should still parse");
        check(nulls != null && "release".equals(nulls.channel),
                "a null version_type should default to release");
        check(nulls != null && nulls.versionNumber != null,
                "a null version_number should read as empty, not null");
        check(nulls != null && nulls.dependencies.size() == 1
                        && "vPINNED".equals(nulls.dependencies.get(0).versionId),
                "a dependency pinned to a version id should keep it");

        // What a one-tap install picks: the newest release, never the newer beta.
        ModrinthMods.File best = ModrinthMods.best(files);
        check(best != null && "vRELEASE1".equals(best.versionId),
                "best() should pick the newest release, picked "
                        + (best == null ? "nothing" : best.versionId));

        // With only betas to choose from, something is better than nothing, but only then.
        List<ModrinthMods.File> betaOnly = files.subList(1, 2);
        ModrinthMods.File bestBeta = ModrinthMods.best(betaOnly);
        check(bestBeta != null && "vBETA1".equals(bestBeta.versionId),
                "best() should fall back to a beta when there is no release");
        check(ModrinthMods.best(new java.util.ArrayList<ModrinthMods.File>()) == null,
                "best() of nothing should be null");

        // The facet expression, which is the other half that has to be exactly right: a malformed
        // one is a silently unfiltered search, which is the failure mode this feature exists to
        // avoid. The loader goes under categories; that is a real wrinkle of the search index.
        String facets = ModrinthMods.facets("1.20.1", "fabric", "mod");
        check(facets.equals("[[\"project_type:mod\"],[\"versions:1.20.1\"],[\"categories:fabric\"]]"),
                "facets were " + facets);
        check(ModrinthMods.facets(null, null, "mod").equals("[[\"project_type:mod\"]]"),
                "an unfiltered search should still constrain the project type");
        check(ModrinthMods.facets("1.20.1", "", "mod")
                        .equals("[[\"project_type:mod\"],[\"versions:1.20.1\"]]"),
                "an empty loader should be left out rather than sent as an empty facet");
        // A category is a second categories group, AND-ed with the loader's, which is exactly
        // what "Fabric mods in this category" means to the search index.
        check(ModrinthMods.facets("1.20.1", "fabric", "mod", "optimization").equals(
                "[[\"project_type:mod\"],[\"versions:1.20.1\"],"
                        + "[\"categories:fabric\"],[\"categories:optimization\"]]"),
                "a category should ride as its own AND group");

        // The project page: links, gallery, license and body all survive the parse, and a
        // gallery entry with no URL is dropped rather than drawn as an empty tile.
        String projectJson = new String(
                Files.readAllBytes(Paths.get(args[0]).resolveSibling("project.json")),
                StandardCharsets.UTF_8);
        ModrinthMods.Project project = ModrinthMods.parseProject(
                JsonParser.parseString(projectJson).getAsJsonObject());
        check(project != null, "the project fixture should parse");
        if (project != null) {
            check("AANobbMI".equals(project.projectId), "project id was " + project.projectId);
            check("sodium".equals(project.slug), "slug was " + project.slug);
            check(project.body.contains("# Sodium"), "the body should arrive verbatim");
            check(project.downloads == 12345678, "downloads were " + project.downloads);
            check(project.followers == 24567, "followers were " + project.followers);
            check("https://github.com/CaffeineMC/sodium".equals(project.sourceUrl),
                    "source url was " + project.sourceUrl);
            check(project.wikiUrl == null, "a JSON null wiki url should read as null");
            check(project.gallery.size() == 2,
                    "the entry with no url should be dropped, got " + project.gallery.size());
            check(project.licenseName != null && project.licenseName.startsWith("GNU"),
                    "license name was " + project.licenseName);
            check(project.pageUrl().equals("https://modrinth.com/mod/sodium"),
                    "page url was " + project.pageUrl());
        }

        // File names come from a remote index, and this is the third place in the launcher that
        // writes one to disk from a stranger.
        check(ModInstall.safeName("../../../etc/passwd").equals("passwd"),
                "a traversing name should be reduced to its last segment");
        check(ModInstall.safeName("a/b/c/mod.jar").equals("mod.jar"), "path separators survived");
        check(ModInstall.safeName("..").equals("mod.jar"), "a dotted name should be replaced");
        check(ModInstall.safeName("").equals("mod.jar"), "an empty name should be replaced");
        check(ModInstall.safeName("sodium-fabric-1.0.jar").equals("sodium-fabric-1.0.jar"),
                "an ordinary name should be left alone");

        System.out.println("checked " + files.size() + " parsed versions, "
                + "file selection, null tolerance, version choice, facets and name safety");
        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("modrinth parsing OK");
    }

    private static ModrinthMods.File byId(List<ModrinthMods.File> files, String id) {
        for (ModrinthMods.File file : files) {
            if (id.equals(file.versionId)) return file;
        }
        return null;
    }
}
