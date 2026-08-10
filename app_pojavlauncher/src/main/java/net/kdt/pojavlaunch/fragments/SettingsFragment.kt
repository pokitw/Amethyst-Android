package net.kdt.pojavlaunch.fragments

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import fr.spse.gamepad_remapper.Remapper
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.CustomControlsActivity
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.OnboardingActivity
import net.kdt.pojavlaunch.ContentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import net.kdt.pojavlaunch.customcontrols.keyboard.VoiceInput
import net.kdt.pojavlaunch.customcontrols.ControlSkin
import net.kdt.pojavlaunch.customcontrols.textures.ControlTextures
import net.kdt.pojavlaunch.ui.settings.ControlStyleOption
import net.kdt.pojavlaunch.customcontrols.textures.TexturePackExport
import net.kdt.pojavlaunch.customcontrols.textures.TexturePackImport
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceRendererSettingsFragment
import net.kdt.pojavlaunch.recorder.RecordingsActivity
import net.kdt.pojavlaunch.ui.home.currentAccount
import net.kdt.pojavlaunch.ui.home.currentGameDirectory
import net.kdt.pojavlaunch.ui.home.currentProfileLabel
import net.kdt.pojavlaunch.ui.content.ContentKind
import net.kdt.pojavlaunch.ui.content.contentFolder
import net.kdt.pojavlaunch.optimiser.DeviceProfile
import net.kdt.pojavlaunch.optimiser.PerformanceMode
import net.kdt.pojavlaunch.optimiser.PerformancePlan
import net.kdt.pojavlaunch.ui.mods.ModTarget
import net.kdt.pojavlaunch.ui.mods.currentModTarget
import net.kdt.pojavlaunch.ui.settings.PerformanceHost
import net.kdt.pojavlaunch.ui.settings.SettingsActions
import net.kdt.pojavlaunch.ui.settings.SettingsEnvironment
import net.kdt.pojavlaunch.ui.settings.TurnipDriverOption
import net.kdt.pojavlaunch.utils.GLInfoUtils
import net.kdt.pojavlaunch.utils.TurnipDrivers
import net.kdt.pojavlaunch.ui.settings.SettingsRoute
import net.kdt.pojavlaunch.ui.settings.SettingsStore
import net.kdt.pojavlaunch.ui.settings.SettingsScreen
import net.kdt.pojavlaunch.ui.settings.rememberSettingsStore
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Settings.
 *
 * Replaces the root of an eight-screen `PreferenceScreen` tree. The leaves that are genuinely
 * their own thing — the runtime manager, the gamepad remapper, the MobileGlues tuning — are still
 * the screens they always were; this owns the grouping and every plain preference.
 */
class SettingsFragment : Fragment() {
    companion object {
        const val TAG = "SettingsFragment"
    }

    private var route by mutableStateOf(SettingsRoute.HOME)
    private var runtimeDialog: MultiRTConfigDialog? = null

    /**
     * Performance mode's sheet, and the work behind it.
     *
     * Owned by the fragment rather than remembered in the composition, because applying the plan
     * downloads six mods and a `rememberCoroutineScope` dies the moment somebody leaves the
     * screen. Half an install is the one outcome worse than none.
     */
    private val performance = PerformanceHost()

    /**
     * Read on resume rather than during composition: it stats the filesystem and asks the package
     * manager, neither of which should happen again every time a slider moves.
     */
    private var environment by mutableStateOf(SettingsEnvironment())

    private val runtimeInstallLauncher =
        registerForActivityResult(OpenDocumentWithExtension("xz")) { uri ->
            if (uri != null) Tools.installRuntimeFromUri(context, uri)
        }

    /**
     * Anything, rather than a zip filter.
     *
     * Providers disagree about what MIME type a zip is — some hand them out as octet-stream —
     * and Game files already settled this: the picker takes what it is given and what is inside
     * decides what it was.
     */
    private val texturePackLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importTexturePack(uri)
        }

    /** Same reasoning as the texture packs: the zip filter varies by provider, the contents decide. */
    private val turnipDriverLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importTurnipDriver(uri)
        }

    /**
     * Back returns to the settings home before it leaves settings, so a detail screen is not a
     * one-way trip out of the section.
     */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            route = SettingsRoute.HOME
            isEnabled = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AmethystXTheme {
                    SettingsScreen(
                        route = route,
                        onRoute = { next ->
                            route = next
                            backCallback.isEnabled = next != SettingsRoute.HOME
                        },
                        store = rememberSettingsStore(),
                        environment = environment,
                        actions = actions,
                        performance = performance
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions and free space can both change while another screen was in front.
        environment = readEnvironment()
    }

    /** The device facts the screens show, none of which are snapshot state on their own. */
    /**
     * Every button style, with the artwork the rail needs to draw each one.
     *
     * The two flat styles first because they are what the launcher has always drawn, then the
     * packs in the order {@code ControlTextures} offers them: the shipped ones, then whatever has
     * been imported. A pack whose face will not decode still gets a tile, drawn as the flat skin,
     * which is exactly what its buttons will do and is more use than quietly dropping it from a
     * list the player put it in.
     */
    private fun controlStyles(context: android.content.Context): List<ControlStyleOption> {
        val styles = mutableListOf(
            ControlStyleOption(
                ControlSkin.STYLE_LAYOUT, getString(R.string.preference_control_style_layout)),
            ControlStyleOption(
                ControlSkin.STYLE_POCKET, getString(R.string.preference_control_style_pocket))
        )
        for (name in ControlTextures.available(context)) {
            styles += ControlStyleOption(name, name, ControlTextures.read(context, name))
        }
        return styles
    }

    private fun readEnvironment(): SettingsEnvironment {
        val context = requireContext()
        val deviceMemory = Tools.getTotalDeviceMemory(context)
        // The same headroom the memory slider always kept, so the device can still breathe.
        val maxMemory = if (Architecture.is32BitsDevice() || deviceMemory < 2048) {
            minOf(1024, deviceMemory)
        } else {
            deviceMemory - if (deviceMemory < 3064) 800 else 1024
        }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        val free = runCatching {
            Formatter.formatShortFileSize(context, currentGameDirectory().usableSpace)
        }.getOrNull().orEmpty()
        // Counted by listing rather than by opening anything: this runs on the main thread in
        // onResume, and the row only needs a number to show that there is something behind it.
        val contentCount = runCatching {
            val dir = currentGameDirectory()
            ContentKind.values().sumOf { kind ->
                contentFolder(dir, kind).listFiles()?.size ?: 0
            }
        }.getOrDefault(0)
        val launcher = activity as? LauncherActivity
        // The header says who is signed in and what they are about to play, which is what the
        // launcher's old account bar used to occupy the top of this screen to say half of.
        val account = runCatching { currentAccount(context) }.getOrNull()
        val profile = runCatching { currentProfileLabel(context) }.getOrNull()
        // GLInfoUtils caches after its first query, so this is a field read on every resume but
        // the first; the driver list is one directory listing plus a meta.json per import.
        val adreno = runCatching { GLInfoUtils.getGlInfo().isAdreno }.getOrDefault(false)
        val drivers = if (!adreno) emptyList() else buildList {
            add(TurnipDriverOption("system", getString(R.string.settings_turnip_system)))
            add(TurnipDriverOption("bundled", getString(R.string.settings_turnip_bundled)))
            runCatching { TurnipDrivers.list(context) }.getOrDefault(emptyList()).forEach {
                add(TurnipDriverOption(TurnipDrivers.importedChoice(it.folder), it.name))
            }
        }
        return SettingsEnvironment(
            versionName = version,
            freeSpace = free,
            deviceMemoryMb = deviceMemory,
            maxMemoryMb = maxMemory,
            gyroAvailable = Tools.deviceSupportsGyro(context),
            voiceAvailable = VoiceInput.isAvailable(context),
            controlStyles = controlStyles(context),
            modCount = contentCount,
            notificationPermission = launcher?.checkForNotificationPermission() ?: true,
            microphonePermission = launcher?.checkForMicrophonePermission() ?: true,
            accountName = account?.username,
            accountFace = account?.face,
            accountKindRes = when {
                account == null -> R.string.settings_account_none
                account.isDemo -> R.string.settings_account_demo
                account.isLocal -> R.string.settings_account_local
                else -> R.string.settings_account_microsoft
            },
            profileTitle = profile?.first,
            profileDetail = profile?.second?.let { " · $it" }.orEmpty(),
            adreno = adreno,
            turnipDrivers = drivers,
            performanceEnabled = PerformanceMode.isEnabled(),
            performanceSummary = performanceSummary()
        )
    }

    private val actions by lazy {
        SettingsActions(
            onBack = { Tools.backToMainMenu(requireActivity()) },
            onCustomControls = {
                startActivity(Intent(requireContext(), CustomControlsActivity::class.java))
            },
            onGamepadBindings = {
                Tools.swapFragment(
                    requireActivity(), GamepadMapperFragment::class.java,
                    GamepadMapperFragment.TAG, null
                )
            },
            onResetGamepad = {
                Remapper.wipePreferences(requireContext())
                Toast.makeText(
                    requireContext(), R.string.preference_controller_map_wiped, Toast.LENGTH_SHORT
                ).show()
            },
            onRuntimeManager = {
                val dialog = runtimeDialog ?: MultiRTConfigDialog().also {
                    it.prepare(requireContext(), runtimeInstallLauncher)
                    runtimeDialog = it
                }
                dialog.show()
            },
            onRendererTuning = {
                Tools.swapFragment(
                    requireActivity(), LauncherPreferenceRendererSettingsFragment::class.java,
                    "RENDERER_SETTINGS", null
                )
            },
            onMods = {
                startActivity(Intent(requireContext(), ContentActivity::class.java))
            },
            onRecordings = {
                startActivity(Intent(requireContext(), RecordingsActivity::class.java))
            },
            onGameFiles = ::openGameFiles,
            onNotificationPermission = {
                (activity as? LauncherActivity)?.askForNotificationPermission(null)
            },
            onMicrophonePermission = {
                (activity as? LauncherActivity)?.askForMicrophonePermission(null)
            },
            onShareLog = { Tools.shareLog(requireContext()) },
            onWiki = { Tools.openURL(requireActivity(), Tools.URL_HOME) },
            onDiscord = { Tools.openURL(requireActivity(), getString(R.string.discord_invite)) },
            onReplayWelcome = ::replayWelcome,
            onImportTexturePack = ::pickTexturePack,
            onExportTexturePack = ::exportTexturePack,
            onImportTurnipDriver = {
                runCatching { turnipDriverLauncher.launch(arrayOf("*/*")) }
                    .onFailure { toast(getString(R.string.settings_turnip_import_failed)) }
            },
            onDeleteTurnipDriver = ::deleteTurnipDriver,
            onPerformanceMode = ::openPerformanceMode
        )
    }

    /**
     * Unpack a chosen driver zip, then select it, exactly as the texture packs do: nobody imports
     * a driver in order to keep playing on the old one. The copy and the ELF check run off the
     * main thread; the toast and the environment refresh come back to it.
     */
    private fun importTurnipDriver(uri: android.net.Uri) {
        val context = requireContext().applicationContext
        // Copying a driver takes as long as it takes, and Settings can be left while it runs, so
        // everything the completion needs is captured now: the application context outlives the
        // fragment, and requireContext() from a detached one throws. This is the icon cache's
        // lesson in a different shape, which is that work started on a background thread has to
        // assume its owner is gone by the time it lands.
        val failedMessage = context.getString(R.string.settings_turnip_import_failed)
        lifecycleScope.launch {
            val driver = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri).use { stream ->
                        if (stream == null) throw java.io.IOException("No stream for $uri")
                        TurnipDrivers.importZip(context, stream)
                    }
                }.onFailure { Log.w(TAG, "Could not import a driver", it) }.getOrNull()
            }
            // lifecycleScope already cancels with the view, so reaching here means the fragment
            // is alive; isAdded is the belt to that braces, because getString still needs one.
            if (!isAdded) return@launch
            if (driver == null) {
                toast(failedMessage)
            } else {
                SettingsStore(context)
                    .put("turnipDriver", TurnipDrivers.importedChoice(driver.folder))
                environment = readEnvironment()
                toast(getString(R.string.settings_turnip_imported, driver.name))
            }
        }
    }

    private fun deleteTurnipDriver(value: String) {
        val folder = value.removePrefix("imported:")
        val context = requireContext().applicationContext
        val store = SettingsStore(context)
        // The preference is written first and on this thread, so a launch that happens during
        // the delete cannot read a choice pointing at a folder that is on its way out. Resolving
        // it would fall back to the bundled driver anyway, but the launcher is the right process
        // to make that true in the file rather than leaving it to be inferred.
        if (store.string("turnipDriver", "bundled") == value) store.put("turnipDriver", "bundled")
        lifecycleScope.launch {
            // Off the main thread: this is a recursive delete of a directory holding a Vulkan
            // driver, which is megabytes, and it was running on the thread drawing the screen.
            withContext(Dispatchers.IO) { TurnipDrivers.delete(context, folder) }
            if (!isAdded) return@launch
            environment = readEnvironment()
            toast(getString(R.string.settings_turnip_deleted))
        }
    }

    // ---------------------------------------------------------------- performance mode

    /**
     * One line saying what performance mode currently amounts to, or null when it is off.
     *
     * Two of the three numbers are preferences and are read live, so a slider moved afterwards is
     * reflected honestly rather than the row claiming a value the mode set an hour ago. The third
     * lives in Minecraft's own file and is the one thing kept as bookkeeping, because parsing
     * options.txt on every resume would be a file read on the thread drawing the screen.
     */
    private fun performanceSummary(): String? {
        if (!PerformanceMode.isEnabled()) return null
        val preferences = net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF ?: return null
        return getString(
            R.string.performance_summary_on,
            preferences.getInt("resolutionRatio", 100).toString(),
            preferences.getInt("allocation", 0).toString(),
            preferences.getInt(PerformanceMode.PREF_CHUNKS, 0).toString()
        )
    }

    /**
     * Open the sheet, having worked out what the plan would be.
     *
     * Everything read here is read on the main thread on purpose: `LauncherProfiles` is
     * unsynchronised state shared with the launch path (handbook 12.7), and the device profile
     * queries GL info and the display. Both are cheap and neither is snapshot state, which is
     * exactly why they are read once here rather than from inside the composition.
     */
    private fun openPerformanceMode() {
        val context = requireContext()
        val target = runCatching { currentModTarget() }.getOrNull()
        val device = runCatching { DeviceProfile.detect(requireActivity()) }.getOrNull()
        val deviceLine = device?.let {
            // The GPU is what the plan actually reasons about, so it leads; the phone's own name
            // is the fallback for a device that would not report a renderer string at all.
            val chip = if (it.gpuName.isNullOrEmpty()) it.deviceName else it.gpuName
            getString(
                R.string.performance_device_summary,
                chip,
                "${it.screenWidth} x ${it.screenHeight}",
                it.refreshRate.toInt()
            )
        }.orEmpty()
        val profileLine = target?.let {
            val what = it.filterLabel.ifEmpty { it.name }
            if (what.isEmpty()) "" else getString(R.string.performance_profile_line, what)
        }.orEmpty()

        if (PerformanceMode.isEnabled()) {
            performance.onApply = ::turnOffPerformanceMode
            performance.showActive(
                listOf(
                    getString(R.string.performance_done),
                    getString(R.string.performance_note_next_launch),
                    getString(R.string.performance_note_mods_stay)
                ),
                deviceLine, profileLine
            )
            return
        }
        if (device == null || target == null) {
            performance.onApply = { performance.close() }
            performance.showResult(listOf(getString(R.string.performance_failed)))
            return
        }

        val renderers = runCatching {
            Tools.getCompatibleRenderers(context).rendererIds.toList()
        }.getOrDefault(emptyList())
        val plan = PerformancePlan.build(
            PerformancePlan.Inputs(
                device.tier, device.gpu, device.pixels(), device.refreshRate,
                environment.deviceMemoryMb, environment.maxMemoryMb, device.cpuCores,
                target.mcVersion, target.loaderId, target.modsFolder != null,
                renderers, SettingsStore(context).currentRenderer()
            )
        )
        performance.onApply = { applyPerformanceMode(plan, target) }
        performance.showPreview(plan, deviceLine, profileLine)
    }

    /**
     * Apply the plan.
     *
     * Split across two threads by what each part touches. The preferences and the profile's
     * renderer are written here, on the main thread, because the profile store is shared with the
     * launch path; options.txt and the mod downloads go to IO. The order matters in one place:
     * the capture inside `applyPreferences` has to see the renderer as it is now, so it is read
     * before anything is written.
     */
    private fun applyPerformanceMode(plan: PerformancePlan.Plan, target: ModTarget) {
        val context = requireContext().applicationContext
        val store = SettingsStore(context)
        val gameDir = runCatching { currentGameDirectory() }.getOrNull()
        performance.showWorking(getString(R.string.performance_working))

        val report = PerformanceMode.applyPreferences(context, plan, store.currentRenderer())
        if (plan.rendererId != null) store.setCurrentRenderer(plan.rendererId)

        // The mod install reports from the thread it runs on; Compose state is not safe to write
        // from there, so every update is posted back. The application context is used for the
        // strings because this outlives the fragment by design.
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val progress = PerformanceMode.Progress { name, index, total ->
            main.post {
                performance.showWorking(
                    context.getString(R.string.performance_installing, name, index, total)
                )
            }
        }
        val folder = target.modsFolder
        val mcVersion = target.mcVersion
        val loaderId = target.loaderId
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (gameDir != null) PerformanceMode.applyOptions(gameDir, plan, report)
                if (folder != null && mcVersion != null && loaderId != null
                    && plan.mods.isNotEmpty()) {
                    PerformanceMode.installMods(
                        folder, plan, mcVersion, loaderId, report, progress
                    )
                }
            }
            performance.showResult(performanceResult(context, plan, report))
            if (isAdded) environment = readEnvironment()
        }
    }

    /** What happened, one sentence per fact, with the qualifications after the outcome. */
    private fun performanceResult(
        context: android.content.Context,
        plan: PerformancePlan.Plan,
        report: PerformanceMode.Report
    ): List<String> {
        if (report.settingsChanged == 0 && report.optionsChanged == 0) {
            return listOf(context.getString(R.string.performance_failed))
        }
        val lines = mutableListOf(context.getString(R.string.performance_done))
        if (report.modsInstalled.isNotEmpty()) {
            lines += context.getString(
                R.string.performance_done_mods, report.modsInstalled.size
            )
        }
        if (report.modsMissing.isNotEmpty()) {
            lines += context.getString(
                R.string.performance_done_missing, report.modsMissing.joinToString(", ")
            )
        }
        if (report.optionsFailed) {
            lines += context.getString(R.string.performance_done_options_failed)
        } else if (report.optionsAbsent) {
            lines += context.getString(R.string.performance_done_options_absent)
        }
        lines += context.getString(R.string.performance_note_next_launch)
        if (plan.mods.isNotEmpty()) {
            lines += context.getString(R.string.performance_note_mods_stay)
        }
        return lines
    }

    /**
     * Put everything back.
     *
     * The preferences and the renderer come back from the capture, so "off" means the settings
     * the player actually had rather than a launcher's idea of defaults. A capture that carried a
     * renderer of null puts back "follow the global default", which is a real answer and not a
     * missing one, which is why the restore reports the two separately.
     */
    private fun turnOffPerformanceMode() {
        val context = requireContext().applicationContext
        val gameDir = runCatching { currentGameDirectory() }.getOrNull()
        performance.showWorking(getString(R.string.performance_working))
        val restored = PerformanceMode.restorePreferences(context)
        if (restored.hasRenderer) SettingsStore(context).setCurrentRenderer(restored.renderer)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (gameDir != null) PerformanceMode.restoreOptions(gameDir)
            }
            performance.showResult(listOf(context.getString(R.string.performance_done_off)))
            if (isAdded) environment = readEnvironment()
        }
    }

    /**
     * Ask for an archive, taking anything.
     *
     * Not filtered to zips: Game files already learned that a picker narrowed by MIME type hides
     * the file the player is looking at, because what a provider calls a zip varies by provider.
     * What is inside decides whether it was a pack.
     */
    private fun pickTexturePack() {
        runCatching { texturePackLauncher.launch(arrayOf("*/*")) }
            .onFailure { toast(getString(R.string.preference_control_texture_failed)) }
    }

    /**
     * Copy the style in force out to Downloads, so it can be edited and brought back.
     *
     * The current one rather than a blank template, because the shortest path to a pack of your
     * own is changing the colours of one that already works, and because a blank canvas explains
     * nothing about which parts of the picture get stretched.
     */
    private fun exportTexturePack() {
        val style = SettingsStore(requireContext()).string("controlStyle", "pocket")
        val result = TexturePackExport.export(requireContext(), ControlSkin.texturePackIn(style))
        when (result.status) {
            TexturePackExport.Status.SAVED ->
                toast(getString(R.string.preference_control_texture_exported, result.fileName))
            else -> toast(getString(R.string.preference_control_texture_export_failed))
        }
    }

    /**
     * Unpack a chosen archive, then make it the style straight away.
     *
     * Selecting it for the player is the whole reason this is one action rather than two: nobody
     * imports a pack in order to leave it turned off, and a picker that silently gained a row is
     * the kind of thing you have to go back and check.
     */
    private fun importTexturePack(uri: android.net.Uri) {
        val importer = TexturePackImport()
        when (importer.importFrom(requireContext(), uri)) {
            TexturePackImport.Result.ADDED -> {
                val name = importer.name
                if (name != null) {
                    // Straight to the same store the screen writes through, so the reload of the
                    // PREF_ statics happens exactly as it would have from a tap on the row.
                    SettingsStore(requireContext()).put("controlStyle", name)
                    // The launcher process has the old artwork in memory; the game gets a fresh
                    // registry anyway, but the editor lives here and would keep drawing the last
                    // pack until the process died.
                    ControlTextures.invalidate()
                    environment = readEnvironment()
                    toast(getString(R.string.preference_control_texture_added, name))
                }
            }
            TexturePackImport.Result.NOT_A_PACK ->
                toast(getString(R.string.preference_control_texture_not_a_pack))
            TexturePackImport.Result.FAILED ->
                toast(getString(R.string.preference_control_texture_failed))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    /**
     * Show the welcome again.
     *
     * The done flag is deliberately left set. Clearing it would put someone back through the flow
     * on their *next* cold start as well, which is a setting nobody asked for; and
     * [OnboardingActivity] writes the flag itself on the way out, so it is already true when this
     * one ends. The activity is started plainly rather than through `TestStorageActivity`, since
     * storage has clearly been sorted out by the time anyone is reading Settings.
     */
    private fun replayWelcome() {
        startActivity(
            Intent(requireContext(), OnboardingActivity::class.java)
                .putExtra(OnboardingActivity.EXTRA_REPLAY, true)
        )
    }

    private fun openGameFiles() {
        val context = requireContext()
        when {
            // A different message on demo, since otherwise they would find the hidden demo folder.
            Tools.isDemoProfile(context) -> Tools.hasNoOnlineProfileDialog(
                requireActivity(),
                getString(R.string.demo_unsupported),
                getString(R.string.change_account)
            )
            !Tools.hasOnlineProfile() -> Tools.hasNoOnlineProfileDialog(requireActivity())
            else -> Tools.openPath(context, currentGameDirectory(), false)
        }
    }
}
