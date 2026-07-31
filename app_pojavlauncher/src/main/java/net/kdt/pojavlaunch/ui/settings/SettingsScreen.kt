package net.kdt.pojavlaunch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.SlotWell

/** Which settings screen is showing. Held by the host so the back key can pop it. */
enum class SettingsRoute { HOME, PERFORMANCE, CONTROLS, RECORDING, FILES, ABOUT }

/** The settings that are not settings: other screens, permissions, one-shot actions. */
@Immutable
class SettingsActions(
    val onBack: () -> Unit = {},
    val onCustomControls: () -> Unit = {},
    val onGamepadBindings: () -> Unit = {},
    val onResetGamepad: () -> Unit = {},
    val onRuntimeManager: () -> Unit = {},
    val onRendererTuning: () -> Unit = {},
    val onRecordings: () -> Unit = {},
    val onGameFiles: () -> Unit = {},
    val onNotificationPermission: () -> Unit = {},
    val onMicrophonePermission: () -> Unit = {},
    val onShareLog: () -> Unit = {},
    val onWiki: () -> Unit = {},
    val onDiscord: () -> Unit = {}
)

/** Facts the screens show but cannot work out for themselves. */
@Immutable
class SettingsEnvironment(
    val versionName: String = "",
    val freeSpace: String = "",
    val deviceMemoryMb: Int = 4096,
    val maxMemoryMb: Int = 3072,
    val gyroAvailable: Boolean = true,
    val notificationPermission: Boolean = true,
    val microphonePermission: Boolean = true
)

/**
 * Settings.
 *
 * The old tree had 48 preferences across 8 screens grouped by where the code lived: the system
 * Vulkan driver sat under "Miscellaneous", the renderer was not here at all, and memory — the one
 * people actually change — was third in a screen called "Java Tweaks". These five are grouped by
 * what someone came here to do, and each carries a summary of its own current state so the common
 * questions are answered without opening anything.
 */
@Composable
fun SettingsScreen(
    route: SettingsRoute,
    onRoute: (SettingsRoute) -> Unit,
    store: SettingsStore,
    environment: SettingsEnvironment,
    actions: SettingsActions
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        when (route) {
            SettingsRoute.HOME -> SettingsHome(onRoute, store, environment, actions)
            SettingsRoute.PERFORMANCE -> PerformanceScreen(store, environment, actions) {
                onRoute(SettingsRoute.HOME)
            }
            SettingsRoute.CONTROLS -> ControlsScreen(store, environment, actions) {
                onRoute(SettingsRoute.HOME)
            }
            SettingsRoute.RECORDING -> RecordingScreen(store, actions) {
                onRoute(SettingsRoute.HOME)
            }
            SettingsRoute.FILES -> GameFilesScreen(store, environment, actions) {
                onRoute(SettingsRoute.HOME)
            }
            SettingsRoute.ABOUT -> AboutScreen(store, environment, actions) {
                onRoute(SettingsRoute.HOME)
            }
        }
    }
}

/** Shared chrome: a back affordance, a large title, and a scrolling body on 20dp gutters. */
@Composable
private fun SettingsScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            // The launcher window lays out full-screen when it is told to ignore the notch, so
            // this asks for whatever inset has not already been applied further up.
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(Modifier.padding(start = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.recordings_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SettingsHome(
    onRoute: (SettingsRoute) -> Unit,
    store: SettingsStore,
    environment: SettingsEnvironment,
    actions: SettingsActions
) {
    val renderer = store.rendererLabel() ?: stringResource(R.string.global_default)
    val memory = store.int("allocation", environment.maxMemoryMb)
    val resolution = store.int("resolutionRatio", 100)
    val buttons = store.int("buttonscale", 100)
    val gyro = store.bool("enableGyro", false)
    val recorderResolution = stringArrayResource(R.array.recorder_resolution_names).toList()
    val recorderResolutionValues = stringArrayResource(R.array.recorder_resolution_values).toList()
    val recorderRate = store.string("recorderFrameRate", "30")
    val currentRes = store.string("recorderResolution", "1280")
    val resolutionIndex = recorderResolutionValues.indexOf(currentRes)
    val resolutionLabel =
        if (resolutionIndex >= 0) recorderResolution[resolutionIndex] else currentRes

    SettingsScaffold(
        title = stringResource(R.string.home_settings),
        subtitle = stringResource(R.string.settings_home_subtitle),
        onBack = actions.onBack
    ) {
        Spacer(Modifier.height(10.dp))
        Destination(
            R.drawable.ic_x_performance,
            stringResource(R.string.settings_dest_performance),
            stringResource(
                R.string.settings_summary_performance, renderer, formatMemory(memory), resolution
            )
        ) { onRoute(SettingsRoute.PERFORMANCE) }
        Destination(
            R.drawable.ic_x_controls,
            stringResource(R.string.preference_control_title),
            stringResource(
                R.string.settings_summary_controls, buttons,
                stringResource(if (gyro) R.string.settings_gyro_on else R.string.settings_gyro_off)
            )
        ) { onRoute(SettingsRoute.CONTROLS) }
        Destination(
            R.drawable.ic_x_recordings,
            stringResource(R.string.preference_recorder_title),
            stringResource(
                R.string.settings_summary_recording, resolutionLabel, recorderRate,
                describeAudio(store)
            )
        ) { onRoute(SettingsRoute.RECORDING) }
        Destination(
            R.drawable.ic_x_files,
            stringResource(R.string.home_tile_files),
            stringResource(R.string.settings_summary_files, environment.freeSpace)
        ) { onRoute(SettingsRoute.FILES) }
        Destination(
            R.drawable.ic_x_info,
            stringResource(R.string.settings_dest_about),
            stringResource(R.string.settings_summary_about, environment.versionName)
        ) { onRoute(SettingsRoute.ABOUT) }
    }
}

@Composable
private fun describeAudio(store: SettingsStore): String {
    if (!store.bool("recorderCaptureAudio", true)) {
        return stringResource(R.string.control_center_no_audio)
    }
    val names = stringArrayResource(R.array.recorder_audio_source_names).toList()
    val values = stringArrayResource(R.array.recorder_audio_source_values).toList()
    val index = values.indexOf(store.string("recorderAudioSource", "internal"))
    return if (index >= 0 && index < names.size) names[index] else names.firstOrNull().orEmpty()
}

@Composable
private fun Destination(iconRes: Int, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlotWell {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------- Performance

@Composable
private fun PerformanceScreen(
    store: SettingsStore,
    environment: SettingsEnvironment,
    actions: SettingsActions,
    onBack: () -> Unit
) {
    val renderers = store.renderers()
    val rendererIds = renderers?.rendererIds?.toList() ?: emptyList()
    val rendererNames = renderers?.rendererDisplayNames?.toList() ?: emptyList()
    val defaultLabel = stringResource(R.string.global_default)

    SettingsScaffold(
        stringResource(R.string.settings_dest_performance),
        stringResource(R.string.settings_performance_subtitle),
        onBack
    ) {
        SectionLabel(stringResource(R.string.settings_section_graphics))
        SettingsCard {
            if (rendererIds.isNotEmpty()) {
                ChoiceRow(
                    title = stringResource(R.string.settings_renderer_title),
                    description = stringResource(R.string.settings_renderer_description),
                    // The empty value stands for "follow the global default", which is how the
                    // profile stores an unset renderer.
                    names = rendererNames + defaultLabel,
                    values = rendererIds + "",
                    selected = store.currentRenderer() ?: "",
                    badge = stringResource(R.string.settings_this_profile),
                    onSelect = { store.setCurrentRenderer(it.ifEmpty { null }) }
                )
            }
            SliderRow(
                title = stringResource(R.string.mcl_setting_title_resolution_scaler),
                description = stringResource(R.string.mcl_setting_subtitle_resolution_scaler),
                value = store.int("resolutionRatio", 100),
                min = 25, max = 100, step = 5,
                format = { "$it%" },
                onValueChange = { store.put("resolutionRatio", it) }
            )
        }

        SectionLabel(stringResource(R.string.settings_section_memory))
        SettingsCard {
            SliderRow(
                title = stringResource(R.string.mcl_memory_allocation),
                description = stringResource(R.string.mcl_memory_allocation_subtitle),
                value = store.int("allocation", environment.maxMemoryMb),
                min = 256, max = environment.maxMemoryMb.coerceAtLeast(512), step = 8,
                format = { formatMemory(it) + " / " + formatMemory(environment.deviceMemoryMb) },
                onValueChange = { store.put("allocation", it) }
            )
            NavRow(
                title = stringResource(R.string.multirt_title),
                description = stringResource(R.string.multirt_subtitle),
                onClick = actions.onRuntimeManager
            )
        }

        AdvancedSection(count = 9) {
            SettingsCard {
                SwitchRow(
                    stringResource(R.string.preference_force_vsync_title),
                    stringResource(R.string.preference_force_vsync_description),
                    store.bool("force_vsync", false)
                ) { store.put("force_vsync", it) }
                SwitchRow(
                    stringResource(R.string.preference_vsync_in_zink_title),
                    stringResource(R.string.preference_vsync_in_zink_description),
                    store.bool("vsync_in_zink", true)
                ) { store.put("vsync_in_zink", it) }
                SwitchRow(
                    stringResource(R.string.preference_sustained_performance_title),
                    stringResource(R.string.preference_sustained_performance_description),
                    store.bool("sustainedPerformance", false)
                ) { store.put("sustainedPerformance", it) }
                SwitchRow(
                    stringResource(R.string.mcl_setting_title_use_surface_view),
                    stringResource(R.string.mcl_setting_subtitle_use_surface_view),
                    store.bool("alternate_surface", false)
                ) { store.put("alternate_surface", it) }
                SwitchRow(
                    stringResource(R.string.preference_vulkan_driver_system_title),
                    stringResource(R.string.preference_vulkan_driver_system_description),
                    store.bool("zinkPreferSystemDriver", false)
                ) { store.put("zinkPreferSystemDriver", it) }
                SwitchRow(
                    stringResource(R.string.preference_force_big_core_title),
                    stringResource(R.string.preference_force_big_core_desc),
                    store.bool("bigCoreAffinity", false)
                ) { store.put("bigCoreAffinity", it) }
                SwitchRow(
                    stringResource(R.string.preference_shader_dump_title),
                    stringResource(R.string.preference_shader_dump_description),
                    store.bool("dump_shaders", false)
                ) { store.put("dump_shaders", it) }
                SwitchRow(
                    stringResource(R.string.mcl_setting_title_ignore_notch),
                    stringResource(R.string.mcl_setting_subtitle_ignore_notch),
                    store.bool("ignoreNotch", false)
                ) { store.put("ignoreNotch", it) }
                TextRow(
                    title = stringResource(R.string.mcl_setting_title_javaargs),
                    description = stringResource(R.string.mcl_setting_subtitle_javaargs),
                    value = store.string("javaArgs", ""),
                    placeholder = defaultLabel,
                    onValueChange = { store.put("javaArgs", it) }
                )
                NavRow(
                    title = stringResource(R.string.mcl_setting_title_renderer_settings),
                    description = stringResource(R.string.mcl_setting_title_renderer_subtitle),
                    onClick = actions.onRendererTuning
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Controls

@Composable
private fun ControlsScreen(
    store: SettingsStore,
    environment: SettingsEnvironment,
    actions: SettingsActions,
    onBack: () -> Unit
) {
    SettingsScaffold(
        stringResource(R.string.preference_control_title),
        stringResource(R.string.settings_controls_subtitle),
        onBack
    ) {
        Spacer(Modifier.height(16.dp))
        SettingsCard {
            NavRow(
                title = stringResource(R.string.preference_edit_controls_title),
                description = stringResource(R.string.preference_edit_controls_summary),
                iconRes = R.drawable.ic_x_controls,
                onClick = actions.onCustomControls
            )
        }

        SectionLabel(stringResource(R.string.settings_section_buttons))
        SettingsCard {
            SliderRow(
                title = stringResource(R.string.mcl_setting_title_buttonscale),
                description = stringResource(R.string.mcl_setting_subtitle_buttonscale),
                value = store.int("buttonscale", 100),
                min = 80, max = 250, step = 5,
                format = { "$it%" },
                onValueChange = { store.put("buttonscale", it) }
            )
            SwitchRow(
                stringResource(R.string.mcl_setting_title_buttonallcaps),
                stringResource(R.string.mcl_setting_subtitle_buttonallcaps),
                store.bool("buttonAllCaps", true)
            ) { store.put("buttonAllCaps", it) }
            SwitchRow(
                stringResource(R.string.mcl_setting_title_keyboard_panning),
                stringResource(R.string.mcl_setting_subtitle_keyboard_panning),
                store.bool("keyboardPanning", true)
            ) { store.put("keyboardPanning", it) }
        }

        SectionLabel(stringResource(R.string.settings_section_mouse))
        SettingsCard {
            SliderRow(
                title = stringResource(R.string.mcl_setting_title_mousescale),
                description = stringResource(R.string.mcl_setting_subtitle_mousescale),
                value = store.int("mousescale", 100),
                min = 25, max = 300, step = 5,
                format = { "$it%" },
                onValueChange = { store.put("mousescale", it) }
            )
            SliderRow(
                title = stringResource(R.string.mcl_setting_title_mousespeed),
                description = stringResource(R.string.mcl_setting_subtitle_mousespeed),
                value = store.int("mousespeed", 100),
                min = 25, max = 300, step = 5,
                format = { "$it%" },
                onValueChange = { store.put("mousespeed", it) }
            )
            SwitchRow(
                stringResource(R.string.preference_mouse_start_title),
                stringResource(R.string.preference_mouse_start_description),
                store.bool("mouse_start", false)
            ) { store.put("mouse_start", it) }
            SwitchRow(
                stringResource(R.string.mcl_setting_title_grab_mouse),
                stringResource(R.string.mcl_setting_subtitle_grab_mouse),
                store.bool("always_grab_mouse", false)
            ) { store.put("always_grab_mouse", it) }
        }

        SectionLabel(stringResource(R.string.settings_section_gestures))
        SettingsCard {
            SwitchRow(
                stringResource(R.string.mcl_disable_gestures),
                stringResource(R.string.mcl_disable_gestures_subtitle),
                store.bool("disableGestures", false)
            ) { store.put("disableGestures", it) }
            SwitchRow(
                stringResource(R.string.mcl_disable_swap_hand),
                stringResource(R.string.mcl_disable_swap_hand_subtitle),
                store.bool("disableDoubleTap", false)
            ) { store.put("disableDoubleTap", it) }
            SliderRow(
                title = stringResource(R.string.mcl_setting_title_longpresstrigger),
                description = stringResource(R.string.mcl_setting_subtitle_longpresstrigger),
                value = store.int("timeLongPressTrigger", 300),
                min = 100, max = 1000, step = 10,
                format = { "$it ms" },
                onValueChange = { store.put("timeLongPressTrigger", it) }
            )
        }

        if (environment.gyroAvailable) {
            SectionLabel(stringResource(R.string.preference_category_gyro_controls))
            SettingsCard {
                SwitchRow(
                    stringResource(R.string.preference_enable_gyro_title),
                    stringResource(R.string.preference_enable_gyro_description),
                    store.bool("enableGyro", false)
                ) { store.put("enableGyro", it) }
                if (store.bool("enableGyro", false)) {
                    SliderRow(
                        title = stringResource(R.string.preference_gyro_sensitivity_title),
                        description = stringResource(R.string.preference_gyro_sensitivity_description),
                        value = store.int("gyroSensitivity", 100),
                        min = 25, max = 300, step = 5,
                        format = { "$it%" },
                        onValueChange = { store.put("gyroSensitivity", it) }
                    )
                    SliderRow(
                        title = stringResource(R.string.preference_gyro_sample_rate_title),
                        description = stringResource(R.string.preference_gyro_sample_rate_description),
                        value = store.int("gyroSampleRate", 16),
                        min = 5, max = 50,
                        format = { "$it ms" },
                        onValueChange = { store.put("gyroSampleRate", it) }
                    )
                    SwitchRow(
                        stringResource(R.string.preference_gyro_smoothing_title),
                        stringResource(R.string.preference_gyro_smoothing_description),
                        store.bool("gyroSmoothing", true)
                    ) { store.put("gyroSmoothing", it) }
                    SwitchRow(
                        stringResource(R.string.preference_gyro_invert_x_axis),
                        stringResource(R.string.preference_gyro_invert_x_axis_description),
                        store.bool("gyroInvertX", false)
                    ) { store.put("gyroInvertX", it) }
                    SwitchRow(
                        stringResource(R.string.preference_gyro_invert_y_axis),
                        stringResource(R.string.preference_gyro_invert_y_axis_description),
                        store.bool("gyroInvertY", false)
                    ) { store.put("gyroInvertY", it) }
                }
            }
        }

        AdvancedSection(count = 5) {
            SettingsCard {
                NavRow(
                    title = stringResource(R.string.preference_remap_controller_title),
                    description = stringResource(R.string.preference_remap_controller_description),
                    onClick = actions.onGamepadBindings
                )
                NavRow(
                    title = stringResource(R.string.preference_wipe_controller_title),
                    description = stringResource(R.string.preference_wipe_controller_description),
                    onClick = actions.onResetGamepad
                )
                SliderRow(
                    title = stringResource(R.string.preference_deadzone_scale_title),
                    description = stringResource(R.string.preference_deadzone_scale_description),
                    value = store.int("gamepad_deadzone_scale", 100),
                    min = 50, max = 200, step = 5,
                    format = { "$it%" },
                    onValueChange = { store.put("gamepad_deadzone_scale", it) }
                )
                SwitchRow(
                    stringResource(R.string.preference_force_enable_touchcontroller_title),
                    stringResource(R.string.preference_force_enable_touchcontroller_description),
                    store.bool("forceEnableTouchController", false)
                ) { store.put("forceEnableTouchController", it) }
                SliderRow(
                    title = stringResource(R.string.preference_touchcontroller_vibrate_length_title),
                    description = stringResource(R.string.preference_touchcontroller_vibrate_length_description),
                    value = store.int("touchControllerVibrateLength", 100),
                    min = 10, max = 1000, step = 10,
                    format = { "$it ms" },
                    onValueChange = { store.put("touchControllerVibrateLength", it) }
                )
            }
        }
    }
}

// ----------------------------------------------------------------- Recording

@Composable
private fun RecordingScreen(
    store: SettingsStore,
    actions: SettingsActions,
    onBack: () -> Unit
) {
    SettingsScaffold(
        stringResource(R.string.preference_recorder_title),
        stringResource(R.string.settings_recording_subtitle),
        onBack
    ) {
        Spacer(Modifier.height(16.dp))
        SettingsCard {
            NavRow(
                title = stringResource(R.string.preference_recorder_view_title),
                description = stringResource(R.string.preference_recorder_view_description),
                iconRes = R.drawable.ic_x_recordings,
                onClick = actions.onRecordings
            )
        }

        SectionLabel(stringResource(R.string.settings_section_video))
        SettingsCard {
            ChoiceRow(
                title = stringResource(R.string.preference_recorder_resolution_title),
                names = stringArrayResource(R.array.recorder_resolution_names).toList(),
                values = stringArrayResource(R.array.recorder_resolution_values).toList(),
                selected = store.string("recorderResolution", "1280"),
                onSelect = { store.put("recorderResolution", it) }
            )
            ChoiceRow(
                title = stringResource(R.string.preference_recorder_framerate_title),
                names = stringArrayResource(R.array.recorder_framerate_names).toList(),
                values = stringArrayResource(R.array.recorder_framerate_values).toList(),
                selected = store.string("recorderFrameRate", "30"),
                onSelect = { store.put("recorderFrameRate", it) }
            )
            SliderRow(
                title = stringResource(R.string.preference_recorder_video_bitrate_title),
                description = stringResource(R.string.preference_recorder_video_bitrate_description),
                value = store.int("recorderVideoBitrate", 12),
                min = 2, max = 50,
                format = { "$it Mbps" },
                onValueChange = { store.put("recorderVideoBitrate", it) }
            )
        }

        SectionLabel(stringResource(R.string.settings_section_audio))
        SettingsCard {
            SwitchRow(
                stringResource(R.string.preference_recorder_audio_title),
                stringResource(R.string.preference_recorder_audio_description),
                store.bool("recorderCaptureAudio", true)
            ) { store.put("recorderCaptureAudio", it) }
            if (store.bool("recorderCaptureAudio", true)) {
                ChoiceRow(
                    title = stringResource(R.string.preference_recorder_audio_source_title),
                    names = stringArrayResource(R.array.recorder_audio_source_names).toList(),
                    values = stringArrayResource(R.array.recorder_audio_source_values).toList(),
                    selected = store.string("recorderAudioSource", "internal"),
                    onSelect = { store.put("recorderAudioSource", it) }
                )
                ChoiceRow(
                    title = stringResource(R.string.preference_recorder_audio_bitrate_title),
                    names = stringArrayResource(R.array.recorder_audio_bitrate_names).toList(),
                    values = stringArrayResource(R.array.recorder_audio_bitrate_values).toList(),
                    selected = store.string("recorderAudioBitrate", "192"),
                    onSelect = { store.put("recorderAudioBitrate", it) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Game files

@Composable
private fun GameFilesScreen(
    store: SettingsStore,
    environment: SettingsEnvironment,
    actions: SettingsActions,
    onBack: () -> Unit
) {
    SettingsScaffold(
        stringResource(R.string.home_tile_files),
        stringResource(R.string.settings_files_subtitle),
        onBack
    ) {
        Spacer(Modifier.height(16.dp))
        SettingsCard {
            NavRow(
                title = stringResource(R.string.mcl_button_open_directory),
                description = stringResource(R.string.settings_summary_files, environment.freeSpace),
                iconRes = R.drawable.ic_x_files,
                onClick = actions.onGameFiles
            )
        }

        SectionLabel(stringResource(R.string.settings_section_downloads))
        SettingsCard {
            ChoiceRow(
                title = stringResource(R.string.preference_download_source_title),
                description = stringResource(R.string.preference_download_source_description),
                names = stringArrayResource(R.array.download_source_names).toList(),
                values = stringArrayResource(R.array.download_source_values).toList(),
                selected = store.string("downloadSource", "default"),
                onSelect = { store.put("downloadSource", it) }
            )
            SwitchRow(
                stringResource(R.string.preference_verify_manifest_title),
                stringResource(R.string.preference_verify_manifest_description),
                store.bool("verifyManifest", true)
            ) { store.put("verifyManifest", it) }
            SwitchRow(
                stringResource(R.string.mcl_setting_check_libraries),
                stringResource(R.string.mcl_setting_check_libraries_subtitle),
                store.bool("checkLibraries", true)
            ) { store.put("checkLibraries", it) }
        }

        SectionLabel(stringResource(R.string.settings_section_cosmetics))
        SettingsCard {
            SwitchRow(
                stringResource(R.string.arc_capes_title),
                stringResource(R.string.arc_capes_desc),
                store.bool("arc_capes", false)
            ) { store.put("arc_capes", it) }
        }
    }
}

// -------------------------------------------------------------------- About

@Composable
private fun AboutScreen(
    store: SettingsStore,
    environment: SettingsEnvironment,
    actions: SettingsActions,
    onBack: () -> Unit
) {
    SettingsScaffold(
        stringResource(R.string.settings_dest_about),
        stringResource(R.string.settings_about_subtitle),
        onBack
    ) {
        Spacer(Modifier.height(16.dp))
        SettingsCard {
            InfoRow(
                title = stringResource(R.string.app_name),
                description = stringResource(R.string.settings_about_blurb),
                value = environment.versionName
            )
        }

        SectionLabel(stringResource(R.string.settings_section_app))
        SettingsCard {
            SwitchRow(
                stringResource(R.string.preference_force_english_title),
                stringResource(R.string.preference_force_english_description),
                store.bool("force_english", false)
            ) { store.put("force_english", it) }
        }

        // Only worth showing what has not already been granted; a row that does nothing when
        // tapped is worse than no row.
        if (!environment.notificationPermission || !environment.microphonePermission) {
            SectionLabel(stringResource(R.string.settings_section_permissions))
            SettingsCard {
                if (!environment.notificationPermission) {
                    NavRow(
                        title = stringResource(R.string.preference_ask_for_notification_title),
                        description = stringResource(R.string.preference_ask_for_notification_description),
                        onClick = actions.onNotificationPermission
                    )
                }
                if (!environment.microphonePermission) {
                    NavRow(
                        title = stringResource(R.string.preference_ask_for_microphone_title),
                        description = stringResource(R.string.preference_ask_for_microphone_description),
                        onClick = actions.onMicrophonePermission
                    )
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_section_help))
        SettingsCard {
            NavRow(title = stringResource(R.string.mcl_tab_wiki), onClick = actions.onWiki)
            NavRow(title = stringResource(R.string.mcl_button_discord), onClick = actions.onDiscord)
            NavRow(title = stringResource(R.string.main_share_logs), onClick = actions.onShareLog)
        }
    }
}

/** Megabytes as something readable, matching how the launcher home says it. */
private fun formatMemory(megabytes: Int): String = when {
    megabytes >= 1024 && megabytes % 1024 == 0 -> "${megabytes / 1024} GB"
    megabytes >= 1024 -> String.format("%.1f GB", megabytes / 1024f)
    else -> "$megabytes MB"
}
