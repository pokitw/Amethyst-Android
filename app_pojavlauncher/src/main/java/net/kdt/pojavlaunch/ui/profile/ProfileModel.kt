package net.kdt.pojavlaunch.ui.profile

import android.content.Context
import androidx.compose.runtime.Immutable
import net.kdt.pojavlaunch.JMinecraftVersionList
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.multirt.MultiRTUtils
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile
import java.io.File
import java.util.Locale

/**
 * What the profile editor and the version picker need, read off the launcher's own files.
 *
 * The profile itself is still a [MinecraftProfile] written through [LauncherProfiles] — the editor
 * only changed shape, not where a profile lives — so nothing here duplicates the launch path's
 * idea of what a profile is.
 */

/** One entry in the version picker, already sorted into the section it belongs to. */
@Immutable
data class VersionEntry(
    val id: String,
    val kind: VersionKind,
    /** Release date as the manifest gives it, trimmed to the day. Empty for installed-only ids. */
    val released: String,
    val installed: Boolean
)

enum class VersionKind { INSTALLED, RELEASE, SNAPSHOT, BETA, ALPHA }

/**
 * Every version worth offering, in one flat list.
 *
 * Flat rather than the four-level expandable list this replaces: the picker filters and searches
 * across all of them, and a section header is a property of a row rather than a level of nesting.
 */
fun loadVersions(): List<VersionEntry> {
    val manifest = runCatching {
        ExtraCore.getValue(ExtraConstants.RELEASE_TABLE) as? JMinecraftVersionList
    }.getOrNull()

    val installed = runCatching {
        File(Tools.DIR_GAME_NEW, "versions").list()?.toSortedSet() ?: emptySet<String>()
    }.getOrDefault(emptySet())

    val remote = manifest?.versions.orEmpty().mapNotNull { version ->
        val kind = when (version.type) {
            "release" -> VersionKind.RELEASE
            "snapshot" -> VersionKind.SNAPSHOT
            "old_beta" -> VersionKind.BETA
            "old_alpha" -> VersionKind.ALPHA
            else -> null
        } ?: return@mapNotNull null
        VersionEntry(
            id = version.id,
            kind = kind,
            released = version.releaseTime?.take(10).orEmpty(),
            installed = installed.contains(version.id)
        )
    }

    // Anything on disk the manifest does not know about — modded and custom ids — would otherwise
    // be unreachable from the picker even though it is the version most likely to be wanted.
    val remoteIds = remote.mapTo(HashSet()) { it.id }
    val custom = installed.filterNot { remoteIds.contains(it) }
        .map { VersionEntry(it, VersionKind.INSTALLED, "", true) }

    return custom + remote
}

/** Narrow the list by section and by what was typed, best match first. */
fun filterVersions(
    all: List<VersionEntry>,
    kind: VersionKind?,
    query: String,
    installedOnly: Boolean
): List<VersionEntry> {
    val trimmed = query.trim().lowercase(Locale.getDefault())
    return all.asSequence()
        .filter { kind == null || it.kind == kind }
        .filter { !installedOnly || it.installed }
        .filter { trimmed.isEmpty() || it.id.lowercase(Locale.getDefault()).contains(trimmed) }
        .toList()
}

/** The runtimes the profile can be pinned to, with the "follow the default" entry first. */
fun runtimeNames(): List<String> =
    runCatching { MultiRTUtils.getInstalledRuntimes().map { it.name } }.getOrDefault(emptyList())

/**
 * Load a profile for editing.
 *
 * A copy, not the stored instance: the editor must be abandonable without having already written
 * half of itself into the profile the launch path reads.
 */
fun loadProfileForEdit(key: String?): MinecraftProfile {
    LauncherProfiles.load()
    val stored = key?.let { LauncherProfiles.mainProfileJson?.profiles?.get(it) }
    return if (stored != null) MinecraftProfile(stored) else MinecraftProfile.createTemplate()
}

/** The key to save under: the one being edited, or a fresh one for a profile being created. */
fun resolveProfileKey(key: String?): String {
    if (key != null) return key
    // getFreeProfileKey reads the loaded profile map directly, so it cannot run before a load.
    LauncherProfiles.load()
    return LauncherProfiles.getFreeProfileKey()
}

/**
 * Write the profile back and tell the launcher.
 *
 * [ExtraConstants.REFRESH_VERSION_SPINNER] is what the home screen listens on to re-read itself
 * and to move the selection onto what was just saved, so saving without raising it would leave
 * the home screen showing the profile as it was before the edit.
 */
fun saveProfile(key: String, profile: MinecraftProfile) {
    LauncherProfiles.load()
    LauncherProfiles.mainProfileJson.profiles[key] = profile
    LauncherProfiles.write()
    ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, key)
}

/**
 * Remove a profile, refusing to remove the last one.
 * @return whether anything was removed
 */
fun deleteProfile(key: String): Boolean {
    LauncherProfiles.load()
    val profiles = LauncherProfiles.mainProfileJson?.profiles ?: return false
    if (profiles.size <= 1) return false
    profiles.remove(key)
    LauncherProfiles.write()
    return true
}

/** Control layouts the profile can be pinned to, as bare names rather than paths. */
fun controlLayoutNames(): List<String> = runCatching {
    File(Tools.CTRLMAP_PATH).list { _, name -> name.endsWith(".json") }
        ?.map { it.removeSuffix(".json") }
        ?.sorted()
        .orEmpty()
}.getOrDefault(emptyList())

/** Whether the version is already on disk, so the editor can say so before a launch discovers it. */
fun isVersionInstalled(context: Context, versionId: String?): Boolean {
    if (!Tools.isValidString(versionId)) return false
    if (MinecraftProfile.LATEST_RELEASE.equals(versionId, true) ||
        MinecraftProfile.LATEST_SNAPSHOT.equals(versionId, true)
    ) return true
    val directory = File(Tools.DIR_HOME_VERSION, versionId!!)
    return File(directory, "$versionId.json").exists() || File(directory, "$versionId.jar").exists()
}
