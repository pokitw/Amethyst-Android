package net.kdt.pojavlaunch.ui.content

import android.content.Context
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.modmeta.ModGraph
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

/** The graph's verdicts, folded back into the rows they came from. */
fun applyModCompat(
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

    return items.map { item ->
        if (item.kind != ContentKind.MOD) return@map item
        val result = results[item.id] ?: return@map item
        val warning = describeProblem(context, result, target, titles)
        val neededBy = result.requiredBy.map { titles[it] ?: it }
        if (item.warning == warning && item.neededBy == neededBy && item.missing == result.missing) {
            item
        } else {
            item.copy(warning = warning, neededBy = neededBy, missing = result.missing)
        }
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
    titles: Map<String, String>
): String? = when {
    result.compat == ModGraph.Compat.WRONG_LOADER ->
        context.getString(R.string.content_mod_wrong_loader, target?.loaderName.orEmpty())

    result.compat == ModGraph.Compat.WRONG_VERSION ->
        context.getString(R.string.content_mod_wrong_version, target?.mcVersion.orEmpty())

    result.missing.isNotEmpty() -> context.resources.getQuantityString(
        R.plurals.content_mod_missing, result.missing.size,
        result.missing.size, result.missing.joinToString(", ") { titles[it] ?: it }
    )

    else -> null
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
