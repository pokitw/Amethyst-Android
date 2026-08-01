package net.kdt.pojavlaunch.fragments

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Base64OutputStream
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.profiles.ProfileIconCache
import net.kdt.pojavlaunch.ui.home.toImageBitmap
import net.kdt.pojavlaunch.ui.profile.ProfileEditorActions
import net.kdt.pojavlaunch.ui.profile.ProfileEditorScreen
import net.kdt.pojavlaunch.ui.profile.ProfileEditorState
import net.kdt.pojavlaunch.ui.profile.VersionEntry
import net.kdt.pojavlaunch.ui.profile.VersionPickerScreen
import net.kdt.pojavlaunch.ui.profile.controlLayoutNames
import net.kdt.pojavlaunch.ui.profile.deleteProfile
import net.kdt.pojavlaunch.ui.profile.isVersionInstalled
import net.kdt.pojavlaunch.ui.profile.loadProfileForEdit
import net.kdt.pojavlaunch.ui.profile.loadVersions
import net.kdt.pojavlaunch.ui.profile.resolveProfileKey
import net.kdt.pojavlaunch.ui.profile.runtimeNames
import net.kdt.pojavlaunch.ui.profile.saveProfile
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import net.kdt.pojavlaunch.utils.CropperUtils
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * The profile editor, and the version picker it opens.
 *
 * The picker is a route inside this fragment rather than a fragment of its own: it exists only to
 * answer one question this screen asked, so putting it on the back stack would mean a Back from
 * the picker could land somewhere other than the editor that opened it.
 *
 * Which profile is edited still comes from the stored selection rather than an argument, because
 * that is the contract the rest of the launcher has with this screen — an argument bundle instead
 * means "create a new one".
 */
class ProfileEditorFragment : Fragment(), CropperUtils.CropperListener {
    companion object {
        const val TAG = "ProfileEditorFragment"
        const val DELETED_PROFILE = "deleted_profile"
    }

    private enum class Route { EDITOR, VERSION }

    private var route by mutableStateOf(Route.EDITOR)
    private var state by mutableStateOf(ProfileEditorState())
    private var versions by mutableStateOf<List<VersionEntry>>(emptyList())

    /** The profile being edited, kept as a copy until Save so leaving abandons the edit. */
    private lateinit var profile: MinecraftProfile
    private lateinit var profileKey: String
    private var creating = false

    private val cropperLauncher = CropperUtils.registerCropper(this, this)

    /** Which folder the file selector was opened for, since it reports back through one event. */
    private var awaitingGameDir = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            route = Route.EDITOR
            isEnabled = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // A folder chosen in the file selector comes back as a one-shot value rather than a result.
        (ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR) as? String)?.let { chosen ->
            if (awaitingGameDir && ::profile.isInitialized) profile.gameDir = chosen
            awaitingGameDir = false
        }

        creating = arguments != null
        if (!::profile.isInitialized) load()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AmethystXTheme {
                    when (route) {
                        Route.EDITOR -> ProfileEditorScreen(state, creating, actions)
                        Route.VERSION -> VersionPickerScreen(
                            versions = versions,
                            selected = profile.lastVersionId,
                            onPick = { id ->
                                profile.lastVersionId = id
                                route = Route.EDITOR
                                backCallback.isEnabled = false
                                refresh()
                            },
                            onBack = {
                                route = Route.EDITOR
                                backCallback.isEnabled = false
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Runtimes and control layouts can both be added by screens this one leads to.
        refresh()
    }

    /**
     * Read the profile being edited.
     *
     * Reloads the profile file first: the launcher can be killed in the background and restored
     * straight back into this fragment, leaving the static profile holder uninitialised.
     */
    private fun load() {
        LauncherProfiles.load()
        val stored = LauncherPreferences.DEFAULT_PREF
            .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "")
            ?.takeIf { it.isNotEmpty() && !creating }
        profile = loadProfileForEdit(stored)
        profileKey = resolveProfileKey(stored)
        // A shipped hack: OSMesa Zink became Kopper, and a profile pinned to the old id would
        // otherwise silently fall back to the global default.
        if ("vulkan_zink" == profile.pojavRendererName) {
            profile.pojavRendererName = "opengles3_desktopgl_zink_kopper"
        }
        versions = loadVersions()
        refresh()
    }

    /** Rebuild the immutable snapshot the screen renders from. */
    private fun refresh() {
        val context = requireContext()
        val renderers = runCatching { Tools.getCompatibleRenderers(context) }.getOrNull()
        val runtimes = runtimeNames()
        val layouts = controlLayoutNames()
        // Through the home screen's converter rather than a cast: a profile icon can be one of the
        // bundled vectors as well as a decoded bitmap, and that one already handles both.
        val icon = runCatching {
            ProfileIconCache.fetchIcon(resources, profileKey, profile.icon).toImageBitmap()
        }.getOrNull()

        state = ProfileEditorState(
            name = profile.name.orEmpty(),
            versionId = profile.lastVersionId.orEmpty(),
            versionInstalled = isVersionInstalled(context, profile.lastVersionId),
            icon = icon,
            rendererNames = renderers?.rendererDisplayNames?.toList().orEmpty(),
            rendererIds = renderers?.rendererIds?.toList().orEmpty(),
            rendererSelected = profile.pojavRendererName.orEmpty(),
            runtimes = runtimes,
            runtimeSelected = profile.javaDir
                ?.removePrefix(Tools.LAUNCHERPROFILES_RTPREFIX).orEmpty(),
            controlLayouts = layouts,
            controlSelected = profile.controlFile?.removeSuffix(".json").orEmpty(),
            gameDir = profile.gameDir.orEmpty(),
            javaArgs = profile.javaArgs.orEmpty(),
            deletable = (LauncherProfiles.mainProfileJson?.profiles?.size ?: 0) > 1
        )
    }

    private val actions by lazy {
        ProfileEditorActions(
            onName = { profile.name = it; refresh() },
            onPickVersion = {
                versions = loadVersions()
                route = Route.VERSION
                backCallback.isEnabled = true
            },
            onPickIcon = { CropperUtils.startCropper(cropperLauncher) },
            onRenderer = { profile.pojavRendererName = it.ifEmpty { null }; refresh() },
            onRuntime = {
                profile.javaDir =
                    if (it.isEmpty()) null else Tools.LAUNCHERPROFILES_RTPREFIX + it
                refresh()
            },
            onControl = {
                profile.controlFile = if (it.isEmpty()) null else "$it.json"
                refresh()
            },
            onGameDir = {
                awaitingGameDir = true
                val bundle = Bundle(3).apply {
                    putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, true)
                    putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Tools.DIR_GAME_HOME)
                    putBoolean(FileSelectorFragment.BUNDLE_SHOW_FILE, false)
                }
                Tools.swapFragment(
                    requireActivity(), FileSelectorFragment::class.java,
                    FileSelectorFragment.TAG, bundle
                )
            },
            onJavaArgs = {
                // Newlines would be pasted straight into the command line otherwise.
                profile.javaArgs = it.replace(Regex("[\r\n]+"), " ").trim().ifEmpty { null }
                refresh()
            },
            onSave = ::save,
            onDelete = ::delete,
            onBack = { Tools.removeCurrentFragment(requireActivity()) }
        )
    }

    private fun save() {
        if (profile.name.isNullOrBlank()) profile.name = profile.lastVersionId
        ProfileIconCache.dropIcon(profileKey)
        saveProfile(profileKey, profile)
        Tools.backToMainMenu(requireActivity())
    }

    private fun delete() {
        if (deleteProfile(profileKey)) {
            ProfileIconCache.dropIcon(profileKey)
            ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, DELETED_PROFILE)
        }
        Tools.removeCurrentFragment(requireActivity())
    }

    /**
     * Store the cropped icon on the profile as a data URI.
     *
     * WEBP at quality 60, which is what a profile icon has always been stored as — the format is
     * part of the file the vanilla launcher also reads, so it is not ours to change.
     */
    override fun onCropped(contentBitmap: Bitmap) {
        val bytes = ByteArrayOutputStream()
        try {
            Base64OutputStream(bytes, Base64.NO_WRAP).use { base64 ->
                contentBitmap.compress(
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP
                    else Bitmap.CompressFormat.WEBP_LOSSY,
                    60,
                    base64
                )
                base64.flush()
            }
        } catch (e: IOException) {
            Tools.showErrorRemote(e)
            return
        }
        profile.icon = "data:image/webp;base64," +
                String(bytes.toByteArray(), StandardCharsets.UTF_8)
        // Dropped so the cache re-reads the icon that was just set rather than the previous one.
        ProfileIconCache.dropIcon(profileKey)
        state = state.copy(icon = contentBitmap.asImageBitmap())
    }

    override fun onFailed(exception: Exception) {
        Tools.showErrorRemote(exception)
    }
}
