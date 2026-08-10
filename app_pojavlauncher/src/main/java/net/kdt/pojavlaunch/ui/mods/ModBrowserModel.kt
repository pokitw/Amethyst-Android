package net.kdt.pojavlaunch.ui.mods

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthMods

/** Where a row is in its own little lifecycle. */
enum class InstallState { IDLE, WORKING, DONE, FAILED }

/**
 * One search result, as the list needs it.
 *
 * A class rather than the API's own [ModrinthMods.Hit] because two of these fields are not facts
 * about the mod: the icon arrives later off a cache thread, and the install state belongs to this
 * device and this profile. Keeping them apart is what lets a re-search reuse an icon that has
 * already been fetched.
 */
@Immutable
data class ModRow(
    val hit: ModrinthMods.Hit,
    val icon: ImageBitmap? = null,
    val installed: Boolean = false,
    val state: InstallState = InstallState.IDLE,
    /** What went wrong, or what went in: shown under the row after an install. */
    val note: String? = null
)

/**
 * One project opened full, over the list.
 *
 * The [hit] is everything the search already knew, so the page can draw its header before any
 * request comes back; the body, the gallery and the versions each arrive when they arrive and
 * fill their own section in. Closing the page throws all of it away, deliberately: a page is a
 * look at a mod, not a cache of one.
 */
@Immutable
data class ProjectPage(
    val hit: ModrinthMods.Hit,
    val project: ModrinthMods.Project? = null,
    val projectFailed: Boolean = false,
    val versions: List<ModrinthMods.File> = emptyList(),
    val versionsKnown: Boolean = false,
    /** The version mid-install, so exactly one row spins. */
    val installingVersion: String? = null,
    val note: String? = null,
    val noteIsError: Boolean = false,
    /** Gallery pictures that have arrived, by URL; the strip draws what is here. */
    val gallery: Map<String, ImageBitmap> = emptyMap()
)

/** Everything the browser draws. */
@Immutable
data class ModBrowserState(
    val target: ModTarget,
    /**
     * Whether the profile has actually been read yet.
     *
     * Distinct from the target being empty, because the two mean opposite things: an unread
     * profile is "wait", and a read one with no loader is "this cannot run mods". Reading it moved
     * to a background thread, and without this the screen spent that gap telling everyone their
     * profile could not run mods.
     */
    val targetKnown: Boolean = false,
    val query: String = "",
    val rows: List<ModRow> = emptyList(),
    val loading: Boolean = false,
    /** Appending a page rather than replacing the list, so the spinner goes at the bottom. */
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
    /**
     * Which kind of failure, so the message can be true.
     *
     * Telling someone to check a connection that is fine, because Modrinth is rate-limiting the
     * app, is worse than saying nothing: it sends them to fix the one thing that is not broken.
     */
    val failure: ModrinthMods.Failure = ModrinthMods.Failure.NONE,
    /** True once a search has come back with nothing, so the empty state can be honest. */
    val empty: Boolean = false,
    /**
     * Whether the profile's version and loader are being applied.
     *
     * On by default and the whole reason the screen is fast. It can be turned off, because a
     * player who knows a mod works on a version Modrinth has not been told about should not be
     * stopped by the launcher being careful on their behalf.
     */
    val filtered: Boolean = true,
    /** Modrinth's sort index, or empty for the smart default the client picks. */
    val sort: String = "",
    /** One of Modrinth's category tags, or empty for all of them. */
    val category: String = "",
    /** The project opened over the list, or null while the list is what shows. */
    val page: ProjectPage? = null,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false
) {
    val effectiveVersion: String? get() = if (filtered) target.mcVersion else null
    val effectiveLoader: String? get() = if (filtered) target.loaderId else null
}

/** The sorts Modrinth's search takes, in the order the picker offers them. */
val MOD_SORTS = listOf("", "downloads", "follows", "newest", "updated")

/**
 * Modrinth's mod category tags.
 *
 * A fixed list rather than a request: the tag set is stable for years at a stretch, the labels
 * are the index's own names rather than launcher copy, and a picker that needs a network round
 * trip before it can open is a worse picker. A tag Modrinth retires simply finds nothing, which
 * the empty state already explains.
 */
val MOD_CATEGORIES = listOf(
    "adventure", "cursed", "decoration", "economy", "equipment", "food", "game-mechanics",
    "library", "magic", "management", "minigame", "mobs", "optimization", "social", "storage",
    "technology", "transportation", "utility", "worldgen"
)

/** A category tag as a label: the index's own name, tidied, not translated. */
fun categoryLabel(tag: String): String =
    tag.replace('-', ' ').replaceFirstChar { it.uppercaseChar() }
