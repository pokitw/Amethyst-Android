package net.kdt.pojavlaunch.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles

/**
 * Reads and writes the launcher's preferences for the settings screens.
 *
 * Every write goes back through [LauncherPreferences.loadPreferences], because most of these are
 * mirrored into static fields that the rest of the launcher reads rather than being consulted from
 * SharedPreferences at the point of use. Skipping that is how a setting appears to save and then
 * does nothing.
 */
@Stable
class SettingsStore(private val context: Context) {
    private val preferences: SharedPreferences = LauncherPreferences.DEFAULT_PREF

    /**
     * Bumped on every write. Reads touch it so that the composition that read a value is also
     * subscribed to it changing — SharedPreferences is not snapshot state and cannot do that.
     */
    private var revision by mutableIntStateOf(0)

    private fun track(): Int = revision

    fun bool(key: String, default: Boolean): Boolean {
        track()
        return preferences.getBoolean(key, default)
    }

    fun int(key: String, default: Int): Int {
        track()
        return preferences.getInt(key, default)
    }

    /** ListPreference stores numbers as strings, so numeric choices come back through here. */
    fun string(key: String, default: String): String {
        track()
        return runCatching { preferences.getString(key, default) }.getOrNull() ?: default
    }

    fun put(key: String, value: Boolean) = write { putBoolean(key, value) }

    fun put(key: String, value: Int) = write { putInt(key, value) }

    fun put(key: String, value: String) = write { putString(key, value) }

    private inline fun write(block: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(block).apply()
        LauncherPreferences.loadPreferences(context)
        revision++
    }

    /**
     * The renderer of the profile that was last played.
     *
     * It is stored per profile rather than globally, which must not change — different modpacks
     * genuinely need different renderers. Settings edits the current profile's copy so the setting
     * is at least findable, and says as much on the row.
     */
    fun currentRenderer(): String? = runCatching {
        LauncherProfiles.load()
        val key = LauncherPreferences.DEFAULT_PREF
            .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null)
        track()
        LauncherProfiles.mainProfileJson?.profiles?.get(key)?.pojavRendererName
    }.getOrNull()

    fun setCurrentRenderer(rendererId: String?) {
        runCatching {
            LauncherProfiles.load()
            val key = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null) ?: return@runCatching
            val profile = LauncherProfiles.mainProfileJson?.profiles?.get(key) ?: return@runCatching
            profile.pojavRendererName = rendererId
            LauncherProfiles.write()
        }
        revision++
    }

    /** Renderer ids and their display names, filtered to what this device can actually run. */
    fun renderers(): Tools.RenderersList? =
        runCatching { Tools.getCompatibleRenderers(context) }.getOrNull()

    /** The display name of a renderer id, or null when the profile follows the global default. */
    fun rendererLabel(): String? {
        val id = currentRenderer() ?: return null
        val list = renderers() ?: return id
        val index = list.rendererIds.indexOf(id)
        return if (index >= 0) list.rendererDisplayNames[index] else id
    }
}

@Composable
fun rememberSettingsStore(): SettingsStore {
    val context = LocalContext.current.applicationContext
    return remember { SettingsStore(context) }
}
