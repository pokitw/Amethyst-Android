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
    val nextOffset: Int = 0,
    val hasMore: Boolean = false
) {
    val effectiveVersion: String? get() = if (filtered) target.mcVersion else null
    val effectiveLoader: String? get() = if (filtered) target.loaderId else null
}
