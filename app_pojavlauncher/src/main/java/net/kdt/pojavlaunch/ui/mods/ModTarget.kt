package net.kdt.pojavlaunch.ui.mods

import android.util.Log
import net.kdt.pojavlaunch.JMinecraftVersionList
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile
import java.io.File
import java.util.Locale

/**
 * What the profile you are about to add mods to actually is.
 *
 * <b>This is the feature.</b> Searching Modrinth is a request; knowing the answer should be
 * filtered to Fabric and 1.20.1 without being asked is what turns adding a mod from a browser
 * expedition into one tap. Everything the filter needs, the launcher already knows, and it has
 * never been used for this.
 *
 * The two facts are recovered differently on purpose. The **loader** is in the version id and
 * nowhere else, which is the same conclusion the home screen's launch card came to. The
 * **Minecraft version** is not: three of the four loaders put it in the id and NeoForge does not
 * (`neoforge-21.1.66` names only itself), so the id is tried first and the installed version
 * manifest's `inheritsFrom` is the fallback, that being the field the game itself uses to find
 * the vanilla version a modded one is built on.
 *
 * @param name       the profile's own name, for the header
 * @param mcVersion  the vanilla Minecraft version, or null when it could not be worked out
 * @param loaderId   Modrinth's id for the loader ("fabric", "forge", ...), or null for vanilla
 * @param loaderName the loader as a player writes it, for the chip
 * @param modsFolder where an installed jar goes, or null when there is no usable profile
 */
data class ModTarget(
    val name: String,
    val mcVersion: String?,
    val loaderId: String?,
    val loaderName: String?,
    val modsFolder: File?
) {
    /** A profile with no loader cannot run mods at all, and the screen has to say so. */
    val canRunMods: Boolean get() = loaderId != null

    val filterLabel: String
        get() = listOfNotNull(loaderName, mcVersion).joinToString(" · ")
}

/**
 * The loaders Modrinth indexes, and how their names appear in a version id.
 *
 * Order matters and always has: every NeoForge id also contains "forge", so the more specific
 * name has to be tested first. This is the same list the launch card matches on, kept apart from
 * it because this one additionally needs Modrinth's own spelling.
 */
private val LOADERS = listOf(
    Triple("neoforge", "neoforge", "NeoForge"),
    Triple("fabric", "fabric", "Fabric"),
    Triple("quilt", "quilt", "Quilt"),
    Triple("forge", "forge", "Forge")
)

/**
 * A Minecraft version, and only when it is the whole of what it is matched against.
 *
 * Anchored on purpose. A loose search inside a version id finds "1.66" in `neoforge-21.1.66`,
 * which is not a Minecraft version, has never existed, and would have filtered every NeoForge
 * profile's search down to nothing while looking like it was working. The id is split into
 * segments by the format each loader writes and each candidate segment is matched whole.
 */
private val MC_VERSION = Regex("""1\.\d{1,2}(\.\d{1,2})?""")

/**
 * Read on the main thread, like every other reader of [LauncherProfiles] (see handbook, 12.7):
 * the statics it touches are unsynchronised and shared with the launch path.
 *
 * Resolved the way the home screen resolves it, not through `getCurrentProfile()`: that method
 * assumes the store is already loaded and <i>throws</i> when the selected key is missing, which
 * is the ordinary state of a fresh launcher process. Swallowing that throw is how this screen
 * shipped with an install button that was silently dead.
 */
fun currentModTarget(): ModTarget {
    val profile = runCatching {
        LauncherProfiles.load()
        val key = LauncherPreferences.DEFAULT_PREF
            .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null)
            ?.takeIf { it.isNotEmpty() }
        LauncherProfiles.mainProfileJson?.profiles?.let { profiles ->
            // The launcher's own selection first; failing that, the only profile there is.
            // A player with exactly one profile has unambiguously told us which one they mean,
            // and an empty screen because a preference was never written helps nobody.
            key?.let(profiles::get) ?: profiles.values.singleOrNull()
        }
    }.getOrNull() ?: return ModTarget("", null, null, null, null)
    val versionId = profile.lastVersionId.orEmpty()
    val haystack = (versionId + " " + profile.icon.orEmpty()).lowercase(Locale.ROOT)
    val loader = LOADERS.firstOrNull { (needle, _, _) -> haystack.contains(needle) }
    return ModTarget(
        name = profile.name.orEmpty().ifEmpty { versionId },
        mcVersion = minecraftVersionOf(versionId),
        loaderId = loader?.second,
        loaderName = loader?.third,
        modsFolder = modsFolderOf(profile)
    )
}

/**
 * The vanilla Minecraft version a version id runs.
 *
 * <b>Read by the format that wrote it, not searched for.</b> Each loader lays its id out a fixed
 * way, so the segment that would hold the Minecraft version is known before looking:
 * `1.20.1-forge-47.2.0` puts it first, `fabric-loader-0.15.7-1.20.1` and its Quilt twin put it
 * last, a vanilla id is nothing else, and `neoforge-21.1.66` does not contain it at all. Only a
 * segment that is a Minecraft version in its entirety counts, which is what stops a NeoForge
 * build number reading as one.
 *
 * Anything not recognised, NeoForge included, falls through to the manifest on disk. Both can
 * fail, and a null means the version filter is not applied rather than applied wrongly.
 */
private fun minecraftVersionOf(versionId: String): String? {
    if (versionId.isEmpty()) return null
    val id = versionId.lowercase(Locale.ROOT)
    val candidates = when {
        // Checked before "forge", because every NeoForge id contains that word too, and unlike
        // the others it genuinely has no Minecraft version to offer.
        id.startsWith("neoforge") -> emptyList()
        id.startsWith("fabric-loader-") || id.startsWith("quilt-loader-") ->
            listOf(versionId.substringAfterLast('-'))
        id.contains("-forge-") -> listOf(versionId.substringBefore('-'))
        else -> listOf(versionId)
    }
    candidates.firstOrNull { MC_VERSION.matches(it) }?.let { return it }
    return inheritedVersionOf(versionId)
}

/**
 * `inheritsFrom` out of the installed version manifest.
 *
 * Read straight off disk rather than through the downloader's helpers, because this runs while
 * someone is typing in a search box: it must not touch the network, and it must not throw. A
 * profile whose version has never been downloaded has no manifest, which is a null, not an error.
 */
private fun inheritedVersionOf(versionId: String): String? = runCatching {
    val manifest = File(Tools.DIR_HOME_VERSION, "$versionId/$versionId.json")
    if (!manifest.isFile) return@runCatching null
    val version = Tools.GLOBAL_GSON.fromJson(
        Tools.read(manifest.absolutePath), JMinecraftVersionList.Version::class.java
    )
    version?.inheritsFrom?.takeIf { it.isNotEmpty() }
}.onFailure { Log.w("ModTarget", "Could not read the version manifest for $versionId", it) }
    .getOrNull()

private fun modsFolderOf(profile: MinecraftProfile): File? = runCatching {
    File(Tools.getGameDirPath(profile), "mods")
}.getOrNull()
