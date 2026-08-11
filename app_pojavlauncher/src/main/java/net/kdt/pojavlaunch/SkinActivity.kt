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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import net.kdt.pojavlaunch.skin.MojangSkins
import net.kdt.pojavlaunch.skin.SkinUpload
import net.kdt.pojavlaunch.ui.home.currentAccountName
import net.kdt.pojavlaunch.ui.skin.ImportSkinResult
import net.kdt.pojavlaunch.ui.skin.BrowsedSkin
import net.kdt.pojavlaunch.ui.skin.SkinBrowseScreen
import net.kdt.pojavlaunch.ui.skin.SkinBrowseState
import net.kdt.pojavlaunch.ui.skin.SkinCanvasScreen
import net.kdt.pojavlaunch.ui.skin.SkinEditorState
import net.kdt.pojavlaunch.ui.skin.SkinEntry
import net.kdt.pojavlaunch.ui.skin.SkinGalleryScreen
import net.kdt.pojavlaunch.ui.skin.SkinGalleryState
import net.kdt.pojavlaunch.ui.skin.SkinImport
import net.kdt.pojavlaunch.ui.skin.blankSkin
import net.kdt.pojavlaunch.ui.skin.classicOnlyArmPixels
import net.kdt.pojavlaunch.ui.skin.SkinHistoryEntry
import net.kdt.pojavlaunch.ui.skin.SkinOrigin
import net.kdt.pojavlaunch.ui.skin.deleteSkin
import net.kdt.pojavlaunch.ui.skin.forgetSkinHistory
import net.kdt.pojavlaunch.ui.skin.historyFolder
import net.kdt.pojavlaunch.ui.skin.readSkinHistory
import net.kdt.pojavlaunch.ui.skin.recordSkinHistory
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

    /** Non-null while the find screen is up. A route inside this activity, like the editor. */
    private var browse by mutableStateOf<SkinBrowseState?>(null)

    /**
     * The history entries behind the pictures on the find screen.
     *
     * Kept beside the [BrowsedSkin] list rather than inside it, because a BrowsedSkin is what the
     * screen draws and has no business carrying a file name. Matched by position, which is safe
     * because both are rebuilt together and never separately.
     */
    private var historyEntries: List<SkinHistoryEntry> = emptyList()

    private val editorBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // The editor is reached from the find screen as well as the gallery, so back has to
            // unwind one layer at a time rather than always landing on the gallery.
            if (editor != null) closeEditor() else closeBrowse()
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
                val finding = browse
                if (editing != null) {
                    SkinCanvasScreen(
                        state = editing,
                        onSave = ::saveEditor,
                        onBack = ::closeEditor
                    )
                } else if (finding != null) {
                    SkinBrowseScreen(
                        state = finding,
                        onQuery = { browse = finding.copy(query = it) },
                        onSearch = ::searchPlayer,
                        onSave = ::saveBrowsed,
                        onForget = ::forgetBrowsed,
                        onBack = ::closeBrowse
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
                        onFind = ::openBrowse,
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
            withContext(Dispatchers.IO) { writeArmWidth(file, slim) }
            refresh(select = file)
        }
    }

    /**
     * Write an arm width into a skin file's pixels.
     *
     * Shared by the model switch and by saving a skin found on another player, so there is one
     * definition of what slim means on disk. Both arms and both layers, worked out from the UV
     * table: going slim clears the strip a slim arm has no room for, and coming back copies the
     * nearest column the arm still wears, so the sleeve continues rather than turning into a
     * stripe of whatever colour the launcher felt like. Runs on IO; the caller is already there.
     */
    private fun writeArmWidth(file: File, slim: Boolean) {
        val bitmap = loadSkinBitmap(file) ?: return
        for ((target, source) in classicOnlyArmPixels()) {
            val argb = if (slim) 0 else bitmap.getPixel(source.first, source.second)
            bitmap.setPixel(target.first, target.second, argb)
        }
        saveSkin(bitmap, file.nameWithoutExtension)
        bitmap.recycle()
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
            if (result == SkinUpload.Result.OK) {
                // Recorded only on a success, and only here: this is the single point at which a
                // skin actually goes onto the account, so it is the only place that can honestly
                // claim one was worn. The clock is passed in so the record is the caller's.
                val stamp = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    loadSkinBitmap(entry.stored.file)?.let { bitmap ->
                        recordSkinHistory(
                            bitmap, entry.stored.slim, SkinOrigin.APPLIED,
                            entry.stored.file.nameWithoutExtension, stamp
                        )
                        bitmap.recycle()
                    }
                }
            }
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

    /* ------------------------------------------------------------------ finding skins */

    /**
     * Open the find screen and start reading the account.
     *
     * The account read is fired here rather than on every resume: it is two network requests, and
     * the answer only matters on the screen that shows it.
     */
    private fun openBrowse() {
        val canAsk = accountCanWearSkins()
        browse = SkinBrowseState(accountLoading = canAsk, accountUnavailable = !canAsk)
        editorBack.isEnabled = true
        loadHistory()
        if (canAsk) loadAccountSkins()
    }

    private fun closeBrowse() {
        browse = null
        editorBack.isEnabled = false
        // The library may have gained a skin while the find screen was up.
        refresh(select = null)
    }

    /** The launcher's own record, which is the only skin history there is. */
    private fun loadHistory() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { readSkinHistory() }
            val skins = withContext(Dispatchers.IO) {
                entries.map { entry ->
                    val bitmap = runCatching {
                        BitmapFactory.decodeFile(File(historyFolder(), entry.file).absolutePath)
                    }.getOrNull()
                    BrowsedSkin(
                        name = entry.label.ifEmpty { getString(R.string.skin_default_name) },
                        image = bitmap?.asImageBitmap(),
                        slim = entry.slim,
                        detail = getString(
                            if (entry.slim) R.string.skin_browse_variant_slim
                            else R.string.skin_browse_variant_classic
                        )
                    )
                }
            }
            historyEntries = entries
            browse = browse?.copy(history = skins)
        }
    }

    /**
     * What Mojang says is on the account.
     *
     * Worth its own section because it is the one thing here the launcher cannot know on its own:
     * a skin set from the Minecraft website or another device is otherwise invisible.
     */
    private fun loadAccountSkins() {
        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) {
                runCatching {
                    currentAccountName(this@SkinActivity)
                        ?.let { MinecraftAccount.load(it) }?.accessToken
                }.getOrNull()
            }
            val skins = withContext(Dispatchers.IO) {
                MojangSkins.ownSkins(token).mapNotNull { entry ->
                    val bytes = MojangSkins.texture(entry.url) ?: return@mapNotNull null
                    val bitmap = runCatching {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull() ?: return@mapNotNull null
                    BrowsedSkin(
                        name = getString(
                            if (entry.active) R.string.skin_browse_worn_now
                            else R.string.skin_browse_worn_before
                        ),
                        image = bitmap.asImageBitmap(),
                        slim = entry.slim,
                        detail = getString(
                            if (entry.slim) R.string.skin_browse_variant_slim
                            else R.string.skin_browse_variant_classic
                        )
                    )
                }
            }
            browse = browse?.copy(account = skins, accountLoading = false)
        }
    }

    /**
     * Look a player up by name.
     *
     * Two requests, and both of them can say something worth repeating: a name nobody has is not a
     * failure, and a 429 is Mojang asking for a pause rather than a connection problem. Telling
     * those apart is why this does not simply report "could not find that".
     */
    private fun searchPlayer() {
        val name = browse?.query?.trim().orEmpty()
        if (name.isEmpty()) return
        browse = browse?.copy(searching = true, message = null, found = null)
        lifecycleScope.launch {
            val player = withContext(Dispatchers.IO) { MojangSkins.search(name) }
            val bitmap = withContext(Dispatchers.IO) {
                val url = player?.skinUrl ?: return@withContext null
                val bytes = MojangSkins.texture(url) ?: return@withContext null
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            }
            val failure = MojangSkins.lastFailure()
            browse = when {
                player == null -> browse?.copy(
                    searching = false,
                    message = when (failure) {
                        MojangSkins.Failure.NOT_FOUND ->
                            getString(R.string.skin_browse_not_found, name)
                        MojangSkins.Failure.RATE_LIMITED ->
                            getString(R.string.skin_browse_rate_limited)
                        MojangSkins.Failure.OFFLINE -> getString(R.string.skin_browse_offline)
                        else -> getString(R.string.skin_browse_failed)
                    },
                    messageIsError = true
                )
                else -> browse?.copy(
                    searching = false,
                    message = null,
                    found = BrowsedSkin(
                        name = player.name.ifEmpty { name },
                        image = bitmap?.asImageBitmap(),
                        slim = player.slim,
                        detail = getString(
                            if (player.slim) R.string.skin_browse_variant_slim
                            else R.string.skin_browse_variant_classic
                        )
                    )
                )
            }
        }
    }

    /** Copy a found skin into the library, where it can be worn or edited like any other. */
    private fun saveBrowsed(skin: BrowsedSkin) {
        val image = skin.image ?: return
        lifecycleScope.launch {
            val bitmap = image.asAndroidBitmap()
            val file = withContext(Dispatchers.IO) {
                saveSkin(bitmap, uniqueName(skin.name))
            }
            if (file == null) {
                browse = browse?.copy(
                    message = getString(R.string.skin_browse_save_failed), messageIsError = true)
                return@launch
            }
            // The arm model travels with it, written into the pixels the same way the gallery's
            // own switch writes it. A slim skin saved as classic is the one mistake here that is
            // invisible until somebody looks at the model.
            withContext(Dispatchers.IO) { writeArmWidth(file, skin.slim) }
            browse = browse?.copy(
                message = getString(R.string.skin_browse_saved, skin.name), messageIsError = false)
        }
    }

    /** Long press on a history tile. Matched by position, since both lists are built together. */
    private fun forgetBrowsed(skin: BrowsedSkin) {
        val index = browse?.history?.indexOf(skin) ?: return
        val entry = historyEntries.getOrNull(index) ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { forgetSkinHistory(entry) }
            loadHistory()
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
