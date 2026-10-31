package net.kdt.pojavlaunch.ui.content

import android.util.Log
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModInstall
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthMods
import net.kdt.pojavlaunch.modmigrate.ModMigration
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.io.FileInputStream

/**
 * The I/O half of moving a mod set to another Minecraft version.
 *
 * Every rule about what should happen lives in [ModMigration], which is plain Java and driven by
 * `scripts/migratesim`. This file is the part that cannot be: reading jars, talking to Modrinth,
 * downloading and renaming. The split is the one `ClipPlan` and `ClipEditor` already make, and for
 * the same reason (16.25): a wrong decision here is somebody's mod folder rearranged, which
 * compiles perfectly and is not noticed until the game will not start.
 */

/** How far through the checking or the downloading it has got. */
data class MigrateProgress(val done: Int, val total: Int, val name: String)

/**
 * A plan, and the versions it was built from.
 *
 * The files are carried rather than looked up again at install time, which is the screenshot
 * editor's rule (14, "what is on screen is the output"): re-fetching a version by id would mean
 * the thing installed came from a second request, which can answer differently from the one the
 * player was shown and read as the plan having lied.
 */
data class MigrationPlan(
    val plan: ModMigration.Plan,
    val files: Map<String, ModrinthMods.File>
)

/** What actually happened, in the terms the sheet reports. */
data class MigrateOutcome(
    val moved: List<String>,
    val failed: List<String>,
    val missingDependencies: List<String>,
    val disabled: Int
)

/** How many candidate projects a single jar is worth examining before giving up on it. */
private const val MAX_CANDIDATES = 5

/** How many search hits per term are worth considering. */
private const val HITS_PER_TERM = 5

/**
 * Work out what would happen, without writing anything.
 *
 * Blocks on the network, so it runs on a background thread. Nothing here changes a file: the plan
 * is shown first and confirmed, because the obvious implementation of this feature is also the
 * one that would silently replace a working mod set on a tap (14, "a guess that is right nearly
 * always is still a guess").
 */
fun planMigration(
    folder: File,
    target: ModMigration.Target,
    onProgress: (MigrateProgress) -> Unit = {}
): MigrationPlan {
    val jars = folder.listFiles { file: File ->
        // `.disabled` jars are deliberately skipped: they are not in the game, so bringing them
        // to a new version would be turning on mods the player had turned off.
        file.isFile && file.name.lowercase().endsWith(".jar")
    }?.sortedBy { it.name.lowercase() } ?: emptyList()

    val locals = jars.map { readLocal(it) }
    // The fast path: one request per hundred jars, against a search and a version list each
    // otherwise. Every hash it does not answer for falls through to the slow path below.
    val byHash = runCatching { ModrinthMods.versionsByHash(locals.mapNotNull { it.sha1 }) }
        .getOrElse { emptyMap() }

    val steps = ArrayList<ModMigration.Step?>(locals.size)
    val files = HashMap<String, ModrinthMods.File>()
    locals.forEachIndexed { index, local ->
        onProgress(MigrateProgress(index, locals.size, local.declaredName ?: local.fileName))
        steps.add(stepFor(local, target, byHash, files))
    }
    onProgress(MigrateProgress(locals.size, locals.size, ""))
    return MigrationPlan(ModMigration.plan(steps), files)
}

/**
 * Carry out a plan.
 *
 * The order inside one mod is **install, then disable**, and it is not interchangeable: the old
 * jar is only taken out of the game once the new one is on disk, so a download that fails leaves
 * the player exactly where they started rather than with a mod folder missing a mod.
 */
fun applyMigration(
    folder: File,
    target: ModMigration.Target,
    planned: MigrationPlan,
    onProgress: (MigrateProgress) -> Unit = {}
): MigrateOutcome {
    val moved = ArrayList<String>()
    val failed = ArrayList<String>()
    val missing = ArrayList<String>()
    var disabled = 0

    val steps = planned.plan.installs()
    steps.forEachIndexed { index, step ->
        val file = step.install?.versionId?.let { planned.files[it] } ?: return@forEachIndexed
        onProgress(MigrateProgress(index, steps.size, step.label()))
        val result = ModInstall.install(folder, file, target.mcVersion, target.loaderId) {
            name, _, _, _ ->
            // The band says which file, the same shape the mod browser's install already
            // reports, so the two read identically.
            onProgress(MigrateProgress(index, steps.size, name))
        }
        if (!result.ok()) {
            failed.add(step.label())
            return@forEachIndexed
        }
        moved.add(step.label())
        missing.addAll(result.missing)
        if (step.disablesOld(target) && disable(folder, step.local.fileName)) disabled++
    }
    onProgress(MigrateProgress(steps.size, steps.size, ""))
    return MigrateOutcome(moved, failed, missing, disabled)
}

/**
 * One jar, as the planner needs it.
 *
 * The hash is read even for a jar whose metadata will not parse, because the hash is the only
 * identity that works for those: a jar with no readable `fabric.mod.json` can still be matched
 * byte for byte to the file Modrinth serves.
 */
private fun readLocal(jar: File): ModMigration.Local {
    val sha1 = runCatching {
        FileInputStream(jar).use { String(Hex.encodeHex(DigestUtils.sha1(it))) }
    }.getOrNull()
    // The same reader the folder's own list uses, which opens the jar once for both the name and
    // the requirements: the name in this plan is therefore the name in that list by construction
    // rather than by two readers being kept in step.
    val meta = readModMetadata(jar)
    return ModMigration.Local(jar.name, sha1, meta?.requirements?.modId, meta?.name)
}

private fun stepFor(
    local: ModMigration.Local,
    target: ModMigration.Target,
    byHash: Map<String, ModrinthMods.File>,
    files: MutableMap<String, ModrinthMods.File>
): ModMigration.Step? {
    val current = identify(local, target, byHash) ?: return ModMigration.step(local, null, null, null)
    val served = runCatching {
        ModrinthMods.versions(current.projectId, target.mcVersion, target.loaderId)
    }.getOrElse { emptyList() }
    served.forEach { files[it.versionId] = it }
    val forTarget = candidates(served, current.projectId, current.projectTitle)
    // Only asked for when there is nothing for the target, which is the one case where the answer
    // is worth a request: a row that says "no build for 1.21.1" and nothing else reads as a
    // failure, and "newest is 1.20.1" reads as a mod that has not been updated.
    val newest = if (forTarget.isEmpty()) newestVersionOf(current.projectId, target.loaderId) else null
    return ModMigration.step(local, current, forTarget, newest)
}

/**
 * Which project this jar came from, proved by hash.
 *
 * The fast path answers directly. The slow path searches for what the jar says it is, then checks
 * each candidate project's versions for this exact file: the search only ever proposes, and a
 * candidate is accepted on the hash and never on the search's own ranking, because installing the
 * wrong mod is the failure this must not have.
 */
private fun identify(
    local: ModMigration.Local,
    target: ModMigration.Target,
    byHash: Map<String, ModrinthMods.File>
): ModMigration.Candidate? {
    val hash = local.sha1?.lowercase()
    byHash[hash]?.let { return candidate(it, it.projectId, null) }

    var examined = 0
    for (term in ModMigration.searchTerms(local)) {
        val page = runCatching {
            ModrinthMods.search(term, null, target.loaderId, 0, "mod", null, null)
        }.getOrNull() ?: continue
        for (hit in page.hits) {
            if (examined >= MAX_CANDIDATES) return null
            examined++
            val versions = runCatching {
                ModrinthMods.versions(hit.projectId, null, target.loaderId)
            }.getOrElse { emptyList() }
            ModMigration.matches(local, candidates(versions, hit.projectId, hit.title))
                ?.let { return it }
            if (examined >= HITS_PER_TERM * 2) break
        }
    }
    return null
}

private fun newestVersionOf(projectId: String, loaderId: String?): String? = runCatching {
    ModrinthMods.versions(projectId, null, loaderId).firstOrNull()?.versionNumber
}.getOrNull()

private fun candidates(
    files: List<ModrinthMods.File>,
    projectId: String,
    title: String?
): List<ModMigration.Candidate> = files.map { candidate(it, projectId, title) }

private fun candidate(
    file: ModrinthMods.File,
    projectId: String,
    title: String?
): ModMigration.Candidate = ModMigration.Candidate(
    projectId, title, file.versionId, file.versionNumber, file.fileName, file.sha1, file.isRelease
)

/**
 * Take the old jar out of the game, keeping the file.
 *
 * `.disabled` is the suffix the mod checker, the duplicate check and the folder's own list already
 * understand, so a jar renamed here is already modelled everywhere as absent from the game and
 * present on disk. A failure to rename is reported rather than retried: the new mod is installed
 * either way and the player can switch the old one off themselves.
 */
private fun disable(folder: File, fileName: String): Boolean = runCatching {
    val jar = File(folder, fileName)
    if (!jar.isFile) return false
    val off = File(folder, fileName + DISABLED_SUFFIX)
    if (off.exists()) return jar.delete()
    jar.renameTo(off)
}.onFailure { Log.w("ModMigrate", "Could not switch $fileName off", it) }.getOrDefault(false)
