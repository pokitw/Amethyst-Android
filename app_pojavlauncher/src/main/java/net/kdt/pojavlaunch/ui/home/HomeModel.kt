package net.kdt.pojavlaunch.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import net.kdt.pojavlaunch.PojavProfile
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.profiles.ProfileIconCache
import net.kdt.pojavlaunch.ui.recordings.recordingsDirectory
import net.kdt.pojavlaunch.value.MinecraftAccount
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile
import java.io.File
import java.util.Locale

/**
 * What the home screen needs to know, read off the launcher's own files.
 *
 * Everything here touches disk, so it is meant to be called from a background dispatcher and the
 * results held in state. Nothing is cached beyond what the launcher already caches itself, since
 * profiles and accounts can be changed by any of the screens this one leads to.
 */

/**
 * A launchable profile, with the parts of it that decide how the game will actually start.
 *
 * The launcher already knows the renderer, the memory and whether the version is on disk; none of
 * it was ever shown before pressing Play, which is where most "why did it launch like that"
 * confusion came from.
 */
data class GameProfile(
    val key: String,
    val title: String,
    val versionLabel: String,
    val icon: ImageBitmap?,
    val loader: String?,
    val rendererLabel: String?,
    val memoryLabel: String,
    val installed: Boolean
) {
    /** Renderer, memory and the version when the title is showing a profile name instead. */
    fun details(): String = buildList {
        if (versionLabel != title) add(versionLabel)
        rendererLabel?.let { add(it) }
        add(memoryLabel)
    }.joinToString(" · ")
}

/** One saved account, as the chip and the account sheet need it. */
data class Account(
    val username: String,
    val face: ImageBitmap?,
    val isLocal: Boolean,
    val isDemo: Boolean
)

/**
 * Version ids carry their loader in the name, which is the only place it is recorded. The order
 * matters: every NeoForge id also contains "forge".
 */
private val LOADERS = listOf(
    "neoforge" to "NeoForge",
    "fabric" to "Fabric",
    "quilt" to "Quilt",
    "optifine" to "OptiFine",
    "lwjgl3ify" to "LWJGL3ify",
    "forge" to "Forge"
)

private const val ICON_SIZE_PX = 96

fun loadProfiles(context: Context): List<GameProfile> {
    LauncherProfiles.load()
    val profiles = LauncherProfiles.mainProfileJson?.profiles ?: return emptyList()
    val renderers = runCatching { Tools.getCompatibleRenderers(context) }.getOrNull()
    return profiles.entries
        .mapNotNull { (key, profile) -> profile?.let { key to it } }
        .map { (key, profile) -> readProfile(context, key, profile, renderers) }
        // Profiles are stored in a hash map, so without this the list would reorder itself every
        // time it was rebuilt.
        .sortedBy { it.title.lowercase(Locale.getDefault()) }
}

private fun readProfile(
    context: Context,
    key: String,
    profile: MinecraftProfile,
    renderers: Tools.RenderersList?
): GameProfile {
    val versionLabel = versionLabel(context, profile)
    val name = profileName(profile)

    return GameProfile(
        key = key,
        title = name ?: versionLabel,
        versionLabel = versionLabel,
        icon = runCatching {
            ProfileIconCache.fetchIcon(context.resources, key, profile.icon).toImageBitmap()
        }.getOrNull(),
        loader = detectLoader(profile),
        rendererLabel = rendererLabel(profile, renderers),
        memoryLabel = formatMemory(LauncherPreferences.PREF_RAM_ALLOCATION),
        installed = isInstalled(profile.lastVersionId)
    )
}

/** How a version id should read: the two "latest" placeholders are names, not versions. */
private fun versionLabel(context: Context, profile: MinecraftProfile): String {
    val versionId = profile.lastVersionId
    return when {
        MinecraftProfile.LATEST_RELEASE.equals(versionId, ignoreCase = true) ->
            context.getString(R.string.profiles_latest_release)
        MinecraftProfile.LATEST_SNAPSHOT.equals(versionId, ignoreCase = true) ->
            context.getString(R.string.profiles_latest_snapshot)
        Tools.isValidString(versionId) -> versionId!!
        else -> context.getString(R.string.home_version_unset)
    }
}

/** "New" was historically the default profile name, so it says as little as no name at all. */
private fun profileName(profile: MinecraftProfile): String? =
    profile.name?.takeIf { Tools.isValidString(it) && !it.equals("New", true) }

/**
 * Just enough of the selected profile to name it.
 *
 * Settings' header shows two words, so it goes through here rather than through [loadProfiles],
 * which rasterises an icon for every profile and asks the device for its renderer list. A header
 * should not pay for a gallery.
 *
 * @return the profile's title and its mod loader, or null when nothing is selected
 */
fun currentProfileLabel(context: Context): Pair<String, String?>? {
    LauncherProfiles.load()
    val key = currentProfileKey() ?: return null
    val profile = LauncherProfiles.mainProfileJson?.profiles?.get(key) ?: return null
    val title = profileName(profile) ?: versionLabel(context, profile)
    return title to detectLoader(profile)
}

/** The account the launcher is signed in as, without decoding every other account's skin. */
fun currentAccount(context: Context): Account? {
    val name = currentAccountName(context) ?: return null
    val account = MinecraftAccount.load(name) ?: return null
    return Account(
        username = account.username,
        face = account.skinFace?.asImageBitmap(),
        isLocal = account.isLocal,
        isDemo = account.isDemo
    )
}

private fun detectLoader(profile: MinecraftProfile): String? {
    // The icon is a loader hint only while it is a name. Installers write "fabric" or "quilt"
    // there, but a profile carrying a picture stores the whole PNG as a base64 data URI, and a
    // five-letter needle in tens of kilobytes of base64 eventually finds itself. Same guard as
    // ui/mods/ModTarget.kt, and it has to stay the same or the launch card and the mod browser
    // will disagree about what the very same profile is.
    val iconName = (profile.icon ?: "").takeIf { it.length <= 24 && !it.contains(':') } ?: ""
    val haystack = ((profile.lastVersionId ?: "") + " " + iconName).lowercase(Locale.ROOT)
    return LOADERS.firstOrNull { (needle, _) -> haystack.contains(needle) }?.second
}

/** The display name of the profile's renderer, or null when it follows the global default. */
private fun rendererLabel(profile: MinecraftProfile, renderers: Tools.RenderersList?): String? {
    val id = profile.pojavRendererName ?: return null
    val index = renderers?.rendererIds?.indexOf(id) ?: -1
    return if (index >= 0) renderers!!.rendererDisplayNames[index] else id
}

private fun formatMemory(megabytes: Int): String = when {
    megabytes >= 1024 && megabytes % 1024 == 0 ->
        String.format(Locale.getDefault(), "%d GB", megabytes / 1024)
    megabytes >= 1024 -> String.format(Locale.getDefault(), "%.1f GB", megabytes / 1024f)
    else -> String.format(Locale.getDefault(), "%d MB", megabytes)
}

/**
 * Whether the version is already on disk.
 *
 * Modded versions inherit the vanilla jar rather than shipping their own, so the manifest is the
 * reliable signal that a launch will not have to download the whole game first.
 */
private fun isInstalled(versionId: String?): Boolean {
    if (!Tools.isValidString(versionId)) return false
    // The "latest" placeholders only resolve once the version list has been fetched, so treat
    // them as installed rather than warning about a download that may not happen.
    if (MinecraftProfile.LATEST_RELEASE.equals(versionId, true) ||
        MinecraftProfile.LATEST_SNAPSHOT.equals(versionId, true)
    ) return true
    val directory = File(Tools.DIR_HOME_VERSION, versionId!!)
    return File(directory, "$versionId.json").exists() || File(directory, "$versionId.jar").exists()
}

fun currentProfileKey(): String? = LauncherPreferences.DEFAULT_PREF
    .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null)
    ?.takeIf { it.isNotEmpty() }

fun selectProfile(key: String) {
    LauncherPreferences.DEFAULT_PREF.edit()
        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, key)
        .apply()
}

/** The directory the selected profile plays out of, falling back to the default game directory. */
fun currentGameDirectory(): File {
    val key = currentProfileKey() ?: return File(Tools.DIR_GAME_NEW)
    LauncherProfiles.load()
    val profile = LauncherProfiles.mainProfileJson?.profiles?.get(key)
        ?: return File(Tools.DIR_GAME_NEW)
    return Tools.getGameDirPath(profile)
}

fun loadAccounts(): List<Account> =
    PojavProfile.getAllProfilesList()
        .mapNotNull { name -> MinecraftAccount.load(name) }
        .map { account ->
            Account(
                username = account.username,
                face = account.skinFace?.asImageBitmap(),
                isLocal = account.isLocal,
                isDemo = account.isDemo
            )
        }
        .sortedBy { it.username.lowercase(Locale.getDefault()) }

fun currentAccountName(context: Context): String? =
    PojavProfile.getCurrentProfileName(context)?.takeIf { it.isNotEmpty() }

/** How many clips are waiting in the gallery, for the Recordings tile. */
fun recordingCount(): Int = runCatching {
    recordingsDirectory()
        ?.list { _, name -> name.lowercase(Locale.ROOT).endsWith(".mp4") }
        ?.size ?: 0
}.getOrDefault(0)

/**
 * Rasterise a profile icon so Compose can draw it.
 *
 * Profile icons are either a decoded bitmap or one of the bundled vectors, and the drawable is
 * shared through [ProfileIconCache], so it is copied before its bounds are set.
 */
fun Drawable.toImageBitmap(): ImageBitmap {
    (this as? BitmapDrawable)?.bitmap?.let { return it.asImageBitmap() }
    val drawable = constantState?.newDrawable()?.mutate() ?: this
    val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
    drawable.draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}
