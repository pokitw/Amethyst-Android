package net.kdt.pojavlaunch.ui.content

import android.content.Context
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.modmeta.ModGraph
import net.kdt.pojavlaunch.sodium.SodiumSupport
import net.kdt.pojavlaunch.ui.mods.ModTarget

/**
 * What the mods in this folder look like when read against the profile, and against each other.
 *
 * <b>The check is worth having here and nowhere else.</b> The mod browser already filters Modrinth
 * by the profile's version and loader, so a mod installed through it cannot be wrong. Game files'
 * add button takes any jar at all, which on a phone is most of them: people are handed jars in
 * Discord threads and have no way to check them, because the folder they land in cannot even be
 * browsed to from Android 11.
 *
 * <p>Kotlin rather than Java, unlike the parser it calls, and deliberately: this is a join over
 * data that has already been parsed, and every way it can be wrong shows up on the screen
 * immediately. The parsing underneath is where being wrong is silent, and that is Java so
 * `scripts/modmetasim` can drive it.
 */

/**
 * Everything the rows can be told about the profile they are sitting in.
 *
 * Two passes rather than one, because they answer different questions over different kinds: the
 * graph reads what the mods declare, and the shader pass reads only the renderer.
 */
fun applyModCompat(
    context: Context,
    items: List<ContentItem>,
    target: ModTarget?,
    rendererId: String?,
    gpu: SodiumSupport.Gpu?
): List<ContentItem> = applyGraph(context, applyShaderCompat(context, items, rendererId, gpu), target)

private fun applyGraph(
    context: Context,
    items: List<ContentItem>,
    target: ModTarget?
): List<ContentItem> {
    val mods = items.filter { it.kind == ContentKind.MOD }
    if (mods.isEmpty()) return items

    // A jar that has not been opened yet has nothing to be judged on, and judging it as "nothing
    // declared" would put a verdict on screen that changes a moment later when the read lands.
    if (mods.none { it.detailed }) return items

    val entries = mods.map { item ->
        ModGraph.Entry(item.id, item.requirements, item.enabled)
    }
    val results = ModGraph.resolve(
        entries,
        target?.mcVersion,
        ModGraph.loaderOf(target?.loaderId)
    ).associateBy { it.key }

    // Titles rather than ids, because a player recognises "Just Enough Items" and has never seen
    // "jei". Built from the rows themselves, so a mod that has not been read yet falls back to its
    // filename rather than disappearing from the sentence.
    val titles = mods.associate { it.id to it.title }
    val duplicated = duplicateModIds(mods)

    return items.map { item ->
        if (item.kind != ContentKind.MOD) return@map item
        val result = results[item.id] ?: return@map item
        val duplicate = item.enabled && item.requirements?.modId?.let { duplicated.contains(it) } == true
        val warning = describeProblem(context, result, target, titles, duplicate)
        val neededBy = result.requiredBy.map { titles[it] ?: it }
        if (item.warning == warning && item.neededBy == neededBy && item.missing == result.missing) {
            item
        } else {
            item.copy(warning = warning, neededBy = neededBy, missing = result.missing)
        }
    }
}

/**
 * The one thing a shader pack row can say for itself, which is whether the renderer can run it.
 *
 * <b>A shader pack on the wrong renderer is otherwise completely silent.</b> It installs, it
 * appears in the folder, it appears in the game's own shader list, and it does nothing, with no
 * error anywhere. That is the gap this closes: the launch dialog only speaks when a Sodium family
 * mod is present, and somebody can perfectly well drop a shader pack in before installing Iris.
 *
 * The question is deliberately narrower than Sodium's own floor, which is why
 * [SodiumSupport.shadersViable] is a separate predicate: Iris needs Sodium to start at all, and a
 * shader pack on top of that needs compute shaders and storage buffers that a GL to GL ES
 * translator will not carry even where it scrapes past OpenGL 3.3.
 *
 * It is a question about the renderer AND the device. Asking only about the renderer, which is
 * what shipped first, made this pass silent on Zink everywhere, which removed the warning on
 * exactly the Mali devices that most needed it.
 *
 * It replaces the row's accent line rather than adding a third one, the same way a mod's verdict
 * does, and it is in `Warning70` and not `Danger70`: nothing has failed, and the pack is still
 * there.
 *
 * The renderer is handed in rather than read here, for the reason `modTarget` is: reading it means
 * loading and parsing the profile JSON, and this runs again on every background batch during a
 * folder scan (16.11). The activity reads it once when it is created.
 */
private fun applyShaderCompat(
    context: Context,
    items: List<ContentItem>,
    rendererId: String?,
    gpu: SodiumSupport.Gpu?
): List<ContentItem> {
    if (items.none { it.kind == ContentKind.SHADER_PACK }) return items
    if (SodiumSupport.shadersViable(rendererId, gpu ?: SodiumSupport.Gpu.unknown())) return items
    val warning = context.getString(R.string.content_shader_renderer)
    return items.map { item ->
        if (item.kind != ContentKind.SHADER_PACK || item.warning == warning) item
        else item.copy(warning = warning)
    }
}

/**
 * The one sentence a broken row shows instead of its accent line.
 *
 * One problem, not a list. A mod for the wrong loader also fails every version test, and a mod
 * missing four dependencies has one thing wrong with it. Naming the most fundamental cause is what
 * makes the line actionable; naming all of them is what makes it unreadable at row width.
 */
private fun describeProblem(
    context: Context,
    result: ModGraph.Result,
    target: ModTarget?,
    titles: Map<String, String>,
    duplicate: Boolean
): String? = when {
    result.compat == ModGraph.Compat.WRONG_LOADER ->
        context.getString(R.string.content_mod_wrong_loader, target?.loaderName.orEmpty())

    result.compat == ModGraph.Compat.WRONG_VERSION ->
        context.getString(R.string.content_mod_wrong_version, target?.mcVersion.orEmpty())

    // Above a missing dependency, below the two that say the mod cannot run here at all. A
    // second copy is the more fundamental problem of the two that are left: the loader refuses
    // to start at all rather than loading without something, so the game does not even reach
    // the point where a dependency could be missed.
    duplicate -> context.getString(R.string.content_mod_duplicate)

    result.missing.isNotEmpty() -> context.resources.getQuantityString(
        R.plurals.content_mod_missing, result.missing.size,
        result.missing.size, result.missing.joinToString(", ") { titles[it] ?: it }
    )

    else -> null
}

/**
 * Mod ids that more than one ENABLED jar claims.
 *
 * <b>This is the one failure in the folder that stops the game starting rather than stopping a
 * mod loading.</b> Every loader refuses outright on a duplicate id, naming an id the player has
 * never seen, and the usual cause is the most ordinary thing anybody does: updating a mod by
 * adding the new jar without taking the old one out. Both files look right, both have sensible
 * names, and nothing anywhere says they are the same mod.
 *
 * <b>Matched on the declared id, never on the file name.</b> A name is a guess: "sodium-0.5.jar"
 * and "sodium-0.6.jar" are the same mod and "reeses-sodium-options.jar" is not, and no amount of
 * stemming tells those apart reliably. The id is what the loader itself compares, so this agrees
 * with the thing that will do the refusing. The cost is that a jar whose metadata would not
 * parse is not checked, which is the safe direction: it is exactly {@code UNKNOWN never
 * CONFLICTS} again.
 *
 * Disabled jars are left out for the same reason the rest of the graph leaves them out: a
 * .disabled file is not in the game, so two of them are not a clash.
 */
fun duplicateModIds(items: List<ContentItem>): Set<String> = items
    .asSequence()
    .filter { it.kind == ContentKind.MOD && it.enabled }
    .mapNotNull { it.requirements?.modId?.takeIf(String::isNotEmpty) }
    .groupingBy { it }
    .eachCount()
    .filterValues { it > 1 }
    .keys

/**
 * Every enabled mod "turn them off" should switch off, with one copy of each duplicate kept.
 *
 * <b>The survivor has to be a copy that can actually run.</b> Picking the newest by timestamp
 * and leaving it at that had a hole that is reachable by the single most ordinary route there
 * is. Update a mod by adding the new jar and forgetting the old one, and have the new one be
 * built for a version this profile is not: the newer jar carries the wrong-version warning, so
 * it is turned off as broken, and the older one carries only "another copy is also on", so it
 * is turned off as the older duplicate. Both go, the mod is gone from the next launch, and the
 * copy that would have worked is the one that was removed. Exactly the outcome the feature
 * exists to prevent.
 *
 * So the broken ones are worked out first, and a group's survivor is chosen from what is left.
 * Where every copy of a mod is broken for its own reasons they all go, which is right: none of
 * them can run, and keeping one would be keeping a mod that does not work.
 *
 * Told apart by the sentence the row is SHOWING rather than by whether the mod is duplicated,
 * because the one warning line names the most fundamental problem and a duplicate with a
 * compat failure is reporting the compat failure.
 *
 * @param duplicateWarning the exact string a row shows when duplication is its only complaint
 */
fun modsToTurnOff(items: List<ContentItem>, duplicateWarning: String): List<ContentItem> {
    val enabledMods = items.filter { it.kind == ContentKind.MOD && it.enabled }
    val broken = enabledMods.filter { it.warning != null && it.warning != duplicateWarning }
    val brokenKeys = broken.map { it.file.absolutePath }.toHashSet()

    val duplicated = duplicateModIds(enabledMods)
    val surplus = ArrayList<ContentItem>()
    if (duplicated.isNotEmpty()) {
        enabledMods
            .filter { it.requirements?.modId?.let(duplicated::contains) == true }
            .groupBy { it.requirements!!.modId }
            .forEach { (_, group) ->
                val runnable = group.filterNot { brokenKeys.contains(it.file.absolutePath) }
                // Nothing runnable in this group: every copy is already in `broken`, so there
                // is no survivor to choose and nothing to add here.
                if (runnable.isEmpty()) return@forEach
                // Newest wins, by the file's own timestamp, which is when it was written and
                // so when it was installed. Ties broken by name, so two jars copied in the
                // same second give the same answer on both of two reads.
                val keep = runnable.sortedWith(
                    compareByDescending<ContentItem> { it.modifiedMs }.thenBy { it.file.name }
                ).first()
                group.forEach { item ->
                    if (item.file.absolutePath != keep.file.absolutePath &&
                        !brokenKeys.contains(item.file.absolutePath)
                    ) {
                        surplus.add(item)
                    }
                }
            }
    }
    return broken + surplus
}

/**
 * How many mods in this folder will not load, for the line above the list.
 *
 * Counted from the warnings already worked out rather than by resolving again, so the number and
 * the rows can never disagree about how many there are.
 */
fun countModProblems(items: List<ContentItem>): Int =
    items.count { it.kind == ContentKind.MOD && it.warning != null && it.enabled }

/**
 * One dependency id that nothing installed provides, if there is one.
 *
 * The first, from the first row that has any. A search takes one thing, and somebody missing three
 * installs them one at a time anyway, with the count going down as they do.
 */
fun firstMissingMod(items: List<ContentItem>): String? = items
    .firstOrNull { it.kind == ContentKind.MOD && it.enabled && it.missing.isNotEmpty() }
    ?.missing?.firstOrNull()
