package net.kdt.pojavlaunch

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.skin.SkinUpload
import net.kdt.pojavlaunch.ui.home.currentAccountName
import net.kdt.pojavlaunch.ui.skin.ImportSkinResult
import net.kdt.pojavlaunch.ui.skin.SkinCanvasScreen
import net.kdt.pojavlaunch.ui.skin.SkinEditorState
import net.kdt.pojavlaunch.ui.skin.SkinEntry
import net.kdt.pojavlaunch.ui.skin.SkinGalleryScreen
import net.kdt.pojavlaunch.ui.skin.SkinGalleryState
import net.kdt.pojavlaunch.ui.skin.SkinImport
import net.kdt.pojavlaunch.ui.skin.blankSkin
import net.kdt.pojavlaunch.ui.skin.classicOnlyArmPixels
import net.kdt.pojavlaunch.ui.skin.deleteSkin
import net.kdt.pojavlaunch.ui.skin.listSkins
import net.kdt.pojavlaunch.ui.skin.loadSkinBitmap
import net.kdt.pojavlaunch.ui.skin.saveSkin
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.value.MinecraftAccount
import java.io.File

/**
 * Hosts the skin gallery and the editor.
 *
 * Two screens, one activity, and the editor is a route rather than a second activity for the
 * same reason the version picker is a route inside the profile editor: Back has to land on the
 * gallery with the selection intact, and an editor that can be reached without a skin to edit is
 * a screen with nothing in it.
 *
 * <b>Bitmaps are decoded off the main thread and painted on it.</b> The editor holds one mutable
 * bitmap and mutates it in place, which is the only way a paint app can work; everything that
 * reads a file to produce one does so on IO first.
 */
class SkinActivity : BaseActivity() {
    /** BaseActivity hides the system bars by default, which is right for the game and not here. */
    override fun setFullscreen(): Boolean = false

    private var gallery by mutableStateOf(SkinGalleryState())
    private var editor by mutableStateOf<SkinEditorState?>(null)

    private val editorBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeEditor()
        }
    }

    /**
     * Anything, rather than an image filter.
     *
     * Game files settled this already: a provider's idea of a PNG's MIME type varies, and a
     * narrowed picker hides the file the player is looking straight at. What is inside decides
     * whether it was a skin, and [SkinImport] refuses it with a reason when it was not.
     */
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importSkin(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, editorBack)
        setContent {
            AmethystXTheme {
                val editing = editor
                if (editing != null) {
                    SkinCanvasScreen(
                        state = editing,
                        onSave = ::saveEditor,
                        onBack = ::closeEditor
                    )
                } else {
                    SkinGalleryScreen(
                        state = gallery,
                        onSelect = { gallery = gallery.copy(selected = it, note = null) },
                        onEdit = ::openEditor,
                        onDelete = ::removeSkin,
                        onApply = ::applySelected,
                        onSlim = ::applySlim,
                        onNew = ::newSkin,
                        onImport = { runCatching { importLauncher.launch(arrayOf("*/*")) } },
                        onBack = ::finish
                    )
                }
            }
        }
        refresh(select = null)
    }

    override fun onResume() {
        super.onResume()
        // The account can change while this screen is in the background, and whether a skin can
        // be worn at all hangs off it.
        gallery = gallery.copy(
            canApply = accountCanWearSkins(),
            accountName = currentAccountName(this)
        )
    }

    /* ------------------------------------------------------------------ the gallery */

    private fun refresh(select: File?) {
        gallery = gallery.copy(loading = true)
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                listSkins().map { stored ->
                    SkinEntry(stored, loadSkinBitmap(stored.file)?.asImageBitmap())
                }
            }
            val chosen = entries.firstOrNull { it.stored.file == select }
                ?: entries.firstOrNull { it.stored.file == gallery.selected?.stored?.file }
                ?: entries.firstOrNull()
            gallery = gallery.copy(
                skins = entries,
                selected = chosen,
                loading = false,
                canApply = accountCanWearSkins(),
                accountName = currentAccountName(this@SkinActivity)
            )
        }
    }

    /**
     * Whether the signed-in account has a Mojang profile to hang a skin on.
     *
     * An offline account stores "0" as its access token in this launcher, and a demo account has
     * no profile either. Both can use the editor; neither can wear the result, and the screen
     * says so rather than failing at the moment of applying.
     */
    private fun accountCanWearSkins(): Boolean = runCatching {
        val name = currentAccountName(this) ?: return false
        val account = MinecraftAccount.load(name) ?: return false
        !account.isLocal && !account.isDemo &&
                account.accessToken != null && account.accessToken != "0"
    }.getOrDefault(false)

    private fun newSkin() {
        val bitmap = blankSkin()
        val name = uniqueName(getString(R.string.skin_default_name))
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { saveSkin(bitmap, name) }
            if (file != null) {
                refresh(select = file)
                editor = SkinEditorState(bitmap, slim = false, name = file.nameWithoutExtension)
                editorBack.isEnabled = true
            }
        }
    }

    /** A name nobody else has, so a second new skin does not silently overwrite the first. */
    private fun uniqueName(base: String): String {
        val taken = gallery.skins.map { it.stored.name }.toSet()
        if (base !in taken) return base
        var index = 2
        while ("$base $index" in taken) index++
        return "$base $index"
    }

    private fun importSkin(uri: Uri) {
        lifecycleScope.launch {
            val importer = SkinImport()
            val result = withContext(Dispatchers.IO) {
                importer.importFrom(this@SkinActivity, uri, uniqueName("Skin"))
            }
            when (result) {
                ImportSkinResult.ADDED -> refresh(select = importer.file)
                ImportSkinResult.WRONG_SIZE -> note(R.string.skin_import_wrong_size, error = true)
                else -> note(R.string.skin_import_failed, error = true)
            }
        }
    }

    private fun removeSkin(entry: SkinEntry) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { deleteSkin(entry.stored.file) }
            // The selection is dropped only when it was the one deleted, so removing a skin from
            // the end of the rail does not throw away what is on the model.
            if (gallery.selected?.stored?.file == entry.stored.file) {
                gallery = gallery.copy(selected = null)
            }
            refresh(select = null)
        }
    }

    /**
     * Change the arm width of the selected skin.
     *
     * Stored by rewriting the file's pixels rather than in a preference, because slimness has to
     * survive being handed to Mojang and being read back on another device, and the pixels are
     * the only thing that travels. Clearing the two columns a slim arm does not use is what
     * makes [guessSlim] read it back the same way.
     */
    private fun applySlim(slim: Boolean) {
        val entry = gallery.selected ?: return
        lifecycleScope.launch {
            val file = entry.stored.file
            withContext(Dispatchers.IO) {
                val bitmap = loadSkinBitmap(file) ?: return@withContext
                // Both arms and both layers, worked out from the UV table. Going slim clears the
                // strip a slim arm has no room for; coming back copies the nearest column the
                // arm still wears, so the sleeve continues rather than turning into a stripe of
                // whatever colour the launcher felt like.
                for ((target, source) in classicOnlyArmPixels()) {
                    val argb = if (slim) 0 else bitmap.getPixel(source.first, source.second)
                    bitmap.setPixel(target.first, target.second, argb)
                }
                saveSkin(bitmap, file.nameWithoutExtension)
                bitmap.recycle()
            }
            refresh(select = file)
        }
    }

    private fun applySelected() {
        val entry = gallery.selected ?: return
        if (!gallery.canApply) return
        gallery = gallery.copy(applying = true, note = null)
        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) {
                runCatching {
                    currentAccountName(this@SkinActivity)
                        ?.let { MinecraftAccount.load(it) }?.accessToken
                }.getOrNull()
            }
            val result = withContext(Dispatchers.IO) {
                SkinUpload.upload(token, entry.stored.file, entry.stored.slim)
            }
            gallery = gallery.copy(applying = false)
            // Mojang's own words where there are any. A generic refusal on top of a specific one
            // is the launcher hiding the answer, which is what the first version did.
            val reason = SkinUpload.lastReason()
            when (result) {
                SkinUpload.Result.OK -> note(R.string.skin_applied, error = false)
                SkinUpload.Result.SIGNED_OUT -> note(R.string.skin_error_signed_out, error = true)
                SkinUpload.Result.RATE_LIMITED -> note(R.string.skin_error_rate_limited, error = true)
                SkinUpload.Result.TOO_LARGE -> note(R.string.skin_error_too_large, error = true)
                SkinUpload.Result.OFFLINE -> note(R.string.skin_error_offline, error = true)
                SkinUpload.Result.NOT_A_SKIN -> gallery = gallery.copy(
                    note = getString(R.string.skin_error_not_a_skin, reason.orEmpty()),
                    noteIsError = true
                )
                else -> gallery = gallery.copy(
                    note = if (reason.isNullOrEmpty()) getString(R.string.skin_error_rejected)
                    else getString(R.string.skin_error_rejected_reason, reason),
                    noteIsError = true
                )
            }
        }
    }

    private fun note(res: Int, error: Boolean) {
        gallery = gallery.copy(note = getString(res), noteIsError = error)
    }

    /* ------------------------------------------------------------------ the editor */

    private fun openEditor(entry: SkinEntry) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadSkinBitmap(entry.stored.file) }
                ?: return@launch
            // The editor paints into this bitmap, so it must be mutable and it must not be the
            // one the gallery is drawing.
            val editable = if (bitmap.isMutable) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, true)
            editor = SkinEditorState(editable, entry.stored.slim, entry.stored.name)
            editorBack.isEnabled = true
        }
    }

    private fun saveEditor() {
        val state = editor ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { saveSkin(state.bitmap, state.name) }
            state.markSaved()
            refresh(select = File(net.kdt.pojavlaunch.ui.skin.skinFolder(), state.name + ".png"))
        }
    }

    private fun closeEditor() {
        val state = editor
        // Saved on the way out rather than lost. A paint app that throws away work because
        // someone pressed Back is a paint app nobody uses twice, and there is nothing here worth
        // the confirmation dialog that would be the alternative.
        if (state != null && state.dirty) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { saveSkin(state.bitmap, state.name) }
                state.markSaved()
                editor = null
                editorBack.isEnabled = false
                refresh(select = File(net.kdt.pojavlaunch.ui.skin.skinFolder(), state.name + ".png"))
            }
            return
        }
        editor = null
        editorBack.isEnabled = false
        refresh(select = null)
    }
}
