package net.kdt.pojavlaunch

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.ui.home.currentProfileLabel
import net.kdt.pojavlaunch.ui.mods.Mod
import net.kdt.pojavlaunch.ui.mods.ModsScreen
import net.kdt.pojavlaunch.ui.mods.deleteMod
import net.kdt.pojavlaunch.ui.mods.installMod
import net.kdt.pojavlaunch.ui.mods.loadMods
import net.kdt.pojavlaunch.ui.mods.modsDirectory
import net.kdt.pojavlaunch.ui.mods.readMod
import net.kdt.pojavlaunch.ui.mods.readModIcon
import net.kdt.pojavlaunch.ui.mods.setModEnabled
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import java.io.File

/**
 * Hosts the mods screen.
 *
 * Its own activity rather than a fragment, for the same reason the recordings gallery is: this is
 * reached from Settings, it has nothing to do with the launch path, and an activity keeps it that
 * way. The folder it works on is resolved once here, on the main thread, because that goes through
 * `LauncherProfiles` — everything under `ui/mods` takes the resolved folder instead.
 */
class ModsActivity : BaseActivity() {
    /** BaseActivity hides the system bars by default, which is right for the game and not here. */
    override fun setFullscreen(): Boolean = false

    private val mods = mutableStateListOf<Mod>()
    private var loading by mutableStateOf(true)
    private var profileName by mutableStateOf<String?>(null)

    private lateinit var modsDirectory: File

    /** Jars already opened for their icon, so scrolling past a row does not open it again. */
    private val iconsRead = mutableSetOf<String>()

    private val pickJar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) install(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modsDirectory = runCatching { modsDirectory() }.getOrElse { File(Tools.DIR_GAME_NEW, "mods") }
        profileName = runCatching { currentProfileLabel(this)?.first }.getOrNull()
        setContent {
            AmethystXTheme {
                ModsScreen(
                    mods = mods,
                    profileName = profileName,
                    loading = loading,
                    onAdd = ::pick,
                    onToggle = ::toggle,
                    onDelete = ::delete,
                    onBack = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { loadMods(modsDirectory) }
            mods.clear()
            mods.addAll(found)
            loading = false
            // Rows appear straight away; the icons, which need every jar opened a second time,
            // arrive after.
            found.forEach { readIconFor(it) }
        }
    }

    private fun readIconFor(mod: Mod) {
        val path = mod.iconPath ?: return
        if (!iconsRead.add(mod.key)) return
        lifecycleScope.launch {
            val icon = withContext(Dispatchers.IO) { readModIcon(mod.file, path) }
            if (icon != null) update(mod.key, icon)
        }
    }

    private fun update(key: String, icon: Bitmap) {
        val index = mods.indexOfFirst { it.key == key }
        if (index < 0) return
        mods[index] = mods[index].copy(icon = icon, iconRead = true)
    }

    private fun pick() {
        // Jars are handed out as octet-stream by most providers, and a filter that matched only
        // the java-archive type hid every file the player came here to pick.
        runCatching { pickJar.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*")) }
            .onFailure { toast(R.string.mods_add_failed) }
    }

    private fun install(uri: Uri) {
        lifecycleScope.launch {
            val installed = withContext(Dispatchers.IO) { installMod(this@ModsActivity, modsDirectory, uri) }
            if (installed == null) {
                toast(R.string.mods_add_failed)
                return@launch
            }
            val mod = withContext(Dispatchers.IO) { readMod(installed) }
            Toast.makeText(this@ModsActivity, getString(R.string.mods_added, mod.name), Toast.LENGTH_SHORT).show()
            reload()
        }
    }

    /**
     * Turn a mod on or off, which renames its file.
     *
     * The row is replaced rather than the whole list reloaded: a folder of two hundred jars would
     * otherwise be re-read, and every icon dropped and fetched again, to flip one switch.
     */
    private fun toggle(mod: Mod, enabled: Boolean) {
        val renamed = setModEnabled(mod, enabled)
        if (renamed == null) {
            toast(R.string.mods_toggle_failed)
            return
        }
        val index = mods.indexOfFirst { it.key == mod.key }
        if (index >= 0) mods[index] = mods[index].copy(file = renamed, enabled = enabled)
    }

    private fun delete(mod: Mod) {
        if (!deleteMod(mod)) {
            toast(R.string.mods_delete_failed)
            return
        }
        iconsRead.remove(mod.key)
        mods.removeAll { it.key == mod.key }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }
}
