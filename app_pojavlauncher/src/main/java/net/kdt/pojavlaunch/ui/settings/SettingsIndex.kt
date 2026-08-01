package net.kdt.pojavlaunch.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import net.kdt.pojavlaunch.R
import java.util.Locale

/**
 * Every setting, in one indexable place.
 *
 * The five screens are written out by hand on purpose — they read as the lists of settings they
 * are — but that leaves nothing for search to look at, which is exactly why search was the one
 * thing the rebuilt Settings still could not do. This is the missing half: a flat description of
 * what exists and where it lives, kept beside the screens rather than generating them, so a screen
 * stays a screen and this stays a table of contents.
 *
 * Adding a setting to a screen means adding a line here. The two are checked against each other by
 * eye, which is the same contract the screens already had with the preference XML they replaced.
 */
@Immutable
class SettingEntry(
    val titleRes: Int,
    val route: SettingsRoute,
    val descriptionRes: Int = 0,
    val sectionRes: Int = 0,
    /**
     * What someone would type who does not know what the setting is called: "fps", "lag", "ram".
     * Never a repeat of the title, which is already searched.
     */
    val keywords: String = "",
    /** Offered before anything has been typed, because a blank search screen is a dead end. */
    val suggested: Boolean = false
)

/** A [SettingEntry] with its strings resolved, so matching is plain string work. */
@Immutable
class IndexedSetting(
    val entry: SettingEntry,
    val title: String,
    val description: String,
    val section: String,
    private val haystack: String
) {
    fun matches(tokens: List<String>): Boolean = tokens.all { haystack.contains(it) }

    /** Lower sorts first: a title that starts with what you typed beats one that merely holds it. */
    fun rank(query: String): Int {
        val lower = title.lowercase(Locale.getDefault())
        return when {
            lower.startsWith(query) -> 0
            lower.contains(query) -> 1
            description.lowercase(Locale.getDefault()).contains(query) -> 2
            else -> 3
        }
    }
}

val SETTINGS_INDEX: List<SettingEntry> = listOf(
    // ---------------------------------------------------------------- Performance
    SettingEntry(
        R.string.settings_renderer_title, SettingsRoute.PERFORMANCE,
        R.string.settings_renderer_description, R.string.settings_section_graphics,
        "opengl gles vulkan zink mobileglues driver gl4es", suggested = true
    ),
    SettingEntry(
        R.string.mcl_setting_title_resolution_scaler, SettingsRoute.PERFORMANCE,
        R.string.mcl_setting_subtitle_resolution_scaler, R.string.settings_section_graphics,
        "resolution scale render fps performance lag sharpness", suggested = true
    ),
    SettingEntry(
        R.string.mcl_memory_allocation, SettingsRoute.PERFORMANCE,
        R.string.mcl_memory_allocation_subtitle, R.string.settings_section_memory,
        "ram memory heap xmx crash out of memory", suggested = true
    ),
    SettingEntry(
        R.string.multirt_title, SettingsRoute.PERFORMANCE,
        R.string.multirt_subtitle, R.string.settings_section_memory,
        "java jre jvm runtime version 8 17 21 install"
    ),
    SettingEntry(
        R.string.preference_force_vsync_title, SettingsRoute.PERFORMANCE,
        R.string.preference_force_vsync_description, R.string.settings_advanced,
        "vsync tearing frame pacing fps"
    ),
    SettingEntry(
        R.string.preference_vsync_in_zink_title, SettingsRoute.PERFORMANCE,
        R.string.preference_vsync_in_zink_description, R.string.settings_advanced,
        "vsync zink vulkan"
    ),
    SettingEntry(
        R.string.preference_sustained_performance_title, SettingsRoute.PERFORMANCE,
        R.string.preference_sustained_performance_description, R.string.settings_advanced,
        "throttle heat thermal battery"
    ),
    SettingEntry(
        R.string.mcl_setting_title_use_surface_view, SettingsRoute.PERFORMANCE,
        R.string.mcl_setting_subtitle_use_surface_view, R.string.settings_advanced,
        "surface view texture black screen"
    ),
    SettingEntry(
        R.string.preference_vulkan_driver_system_title, SettingsRoute.PERFORMANCE,
        R.string.preference_vulkan_driver_system_description, R.string.settings_advanced,
        "vulkan driver turnip adreno system"
    ),
    SettingEntry(
        R.string.preference_force_big_core_title, SettingsRoute.PERFORMANCE,
        R.string.preference_force_big_core_desc, R.string.settings_advanced,
        "cpu core affinity big little performance"
    ),
    SettingEntry(
        R.string.preference_shader_dump_title, SettingsRoute.PERFORMANCE,
        R.string.preference_shader_dump_description, R.string.settings_advanced,
        "shader dump debug developer"
    ),
    SettingEntry(
        R.string.mcl_setting_title_ignore_notch, SettingsRoute.PERFORMANCE,
        R.string.mcl_setting_subtitle_ignore_notch, R.string.settings_advanced,
        "notch cutout display fullscreen safe area"
    ),
    SettingEntry(
        R.string.mcl_setting_title_javaargs, SettingsRoute.PERFORMANCE,
        R.string.mcl_setting_subtitle_javaargs, R.string.settings_advanced,
        "jvm arguments flags xmx garbage collector"
    ),
    SettingEntry(
        R.string.mcl_setting_title_renderer_settings, SettingsRoute.PERFORMANCE,
        R.string.mcl_setting_title_renderer_subtitle, R.string.settings_advanced,
        "mobileglues angle fsr multidraw glsl cache"
    ),

    // ------------------------------------------------------------------- Controls
    SettingEntry(
        R.string.preference_edit_controls_title, SettingsRoute.CONTROLS,
        R.string.preference_edit_controls_summary, 0,
        "layout editor buttons move add joystick", suggested = true
    ),
    SettingEntry(
        R.string.preference_control_pocket_title, SettingsRoute.CONTROLS,
        R.string.preference_control_pocket_description, R.string.settings_section_style,
        "pocket bedrock style look theme translucent round"
    ),
    SettingEntry(
        R.string.preference_control_glyphs_title, SettingsRoute.CONTROLS,
        R.string.preference_control_glyphs_description, R.string.settings_section_style,
        "icons glyphs symbols labels text jump sneak"
    ),
    SettingEntry(
        R.string.mcl_setting_title_buttonscale, SettingsRoute.CONTROLS,
        R.string.mcl_setting_subtitle_buttonscale, R.string.settings_section_buttons,
        "size bigger smaller touch target", suggested = true
    ),
    SettingEntry(
        R.string.mcl_setting_title_buttonallcaps, SettingsRoute.CONTROLS,
        R.string.mcl_setting_subtitle_buttonallcaps, R.string.settings_section_buttons,
        "uppercase caps label text"
    ),
    SettingEntry(
        R.string.mcl_setting_title_keyboard_panning, SettingsRoute.CONTROLS,
        R.string.mcl_setting_subtitle_keyboard_panning, R.string.settings_section_buttons,
        "keyboard pan shift screen typing"
    ),
    SettingEntry(
        R.string.mcl_setting_title_mousescale, SettingsRoute.CONTROLS,
        R.string.mcl_setting_subtitle_mousescale, R.string.settings_section_mouse,
        "cursor size pointer"
    ),
    SettingEntry(
        R.string.mcl_setting_title_mousespeed, SettingsRoute.CONTROLS,
        R.string.mcl_setting_subtitle_mousespeed, R.string.settings_section_mouse,
        "sensitivity cursor pointer speed"
    ),
    SettingEntry(
        R.string.preference_mouse_start_title, SettingsRoute.CONTROLS,
        R.string.preference_mouse_start_description, R.string.settings_section_mouse,
        "virtual mouse start launch cursor"
    ),
    SettingEntry(
        R.string.mcl_setting_title_grab_mouse, SettingsRoute.CONTROLS,
        R.string.mcl_setting_subtitle_grab_mouse, R.string.settings_section_mouse,
        "grab capture pointer lock"
    ),
    SettingEntry(
        R.string.mcl_disable_gestures, SettingsRoute.CONTROLS,
        R.string.mcl_disable_gestures_subtitle, R.string.settings_section_gestures,
        "tap long press break place mining"
    ),
    SettingEntry(
        R.string.mcl_disable_swap_hand, SettingsRoute.CONTROLS,
        R.string.mcl_disable_swap_hand_subtitle, R.string.settings_section_gestures,
        "double tap offhand swap"
    ),
    SettingEntry(
        R.string.mcl_setting_title_longpresstrigger, SettingsRoute.CONTROLS,
        R.string.mcl_setting_subtitle_longpresstrigger, R.string.settings_section_gestures,
        "hold delay long press milliseconds"
    ),
    SettingEntry(
        R.string.preference_enable_gyro_title, SettingsRoute.CONTROLS,
        R.string.preference_enable_gyro_description, R.string.preference_category_gyro_controls,
        "gyroscope motion aim tilt"
    ),
    SettingEntry(
        R.string.preference_gyro_sensitivity_title, SettingsRoute.CONTROLS,
        R.string.preference_gyro_sensitivity_description, R.string.preference_category_gyro_controls,
        "gyroscope motion aim speed"
    ),
    SettingEntry(
        R.string.preference_gyro_sample_rate_title, SettingsRoute.CONTROLS,
        R.string.preference_gyro_sample_rate_description, R.string.preference_category_gyro_controls,
        "gyroscope polling rate smooth"
    ),
    SettingEntry(
        R.string.preference_gyro_smoothing_title, SettingsRoute.CONTROLS,
        R.string.preference_gyro_smoothing_description, R.string.preference_category_gyro_controls,
        "gyroscope jitter filter"
    ),
    SettingEntry(
        R.string.preference_gyro_invert_x_axis, SettingsRoute.CONTROLS,
        R.string.preference_gyro_invert_x_axis_description, R.string.preference_category_gyro_controls,
        "gyroscope invert horizontal"
    ),
    SettingEntry(
        R.string.preference_gyro_invert_y_axis, SettingsRoute.CONTROLS,
        R.string.preference_gyro_invert_y_axis_description, R.string.preference_category_gyro_controls,
        "gyroscope invert vertical"
    ),
    SettingEntry(
        R.string.preference_remap_controller_title, SettingsRoute.CONTROLS,
        R.string.preference_remap_controller_description, R.string.settings_advanced,
        "gamepad controller bindings remap buttons"
    ),
    SettingEntry(
        R.string.preference_wipe_controller_title, SettingsRoute.CONTROLS,
        R.string.preference_wipe_controller_description, R.string.settings_advanced,
        "gamepad controller reset wipe bindings"
    ),
    SettingEntry(
        R.string.preference_deadzone_scale_title, SettingsRoute.CONTROLS,
        R.string.preference_deadzone_scale_description, R.string.settings_advanced,
        "gamepad stick drift deadzone"
    ),
    SettingEntry(
        R.string.preference_force_enable_touchcontroller_title, SettingsRoute.CONTROLS,
        R.string.preference_force_enable_touchcontroller_description, R.string.settings_advanced,
        "touchcontroller mod touch"
    ),
    SettingEntry(
        R.string.preference_touchcontroller_vibrate_length_title, SettingsRoute.CONTROLS,
        R.string.preference_touchcontroller_vibrate_length_description, R.string.settings_advanced,
        "vibration haptics rumble"
    ),

    // ------------------------------------------------------------------ Recording
    SettingEntry(
        R.string.preference_recorder_view_title, SettingsRoute.RECORDING,
        R.string.preference_recorder_view_description, 0,
        "gallery clips videos watch share", suggested = true
    ),
    SettingEntry(
        R.string.preference_recorder_resolution_title, SettingsRoute.RECORDING,
        0, R.string.settings_section_video,
        "recording resolution 720p 1080p quality size"
    ),
    SettingEntry(
        R.string.preference_recorder_framerate_title, SettingsRoute.RECORDING,
        0, R.string.settings_section_video,
        "recording fps 30 60 smooth"
    ),
    SettingEntry(
        R.string.preference_recorder_video_bitrate_title, SettingsRoute.RECORDING,
        R.string.preference_recorder_video_bitrate_description, R.string.settings_section_video,
        "recording quality mbps file size"
    ),
    SettingEntry(
        R.string.preference_recorder_audio_title, SettingsRoute.RECORDING,
        R.string.preference_recorder_audio_description, R.string.settings_section_audio,
        "sound record audio capture"
    ),
    SettingEntry(
        R.string.preference_recorder_audio_source_title, SettingsRoute.RECORDING,
        0, R.string.settings_section_audio,
        "microphone mic commentary voice game sound"
    ),
    SettingEntry(
        R.string.preference_recorder_audio_bitrate_title, SettingsRoute.RECORDING,
        0, R.string.settings_section_audio,
        "audio quality kbps"
    ),

    // ----------------------------------------------------------------- Game files
    SettingEntry(
        R.string.mcl_button_open_directory, SettingsRoute.FILES,
        0, 0,
        "folder storage worlds saves resource packs mods open"
    ),
    SettingEntry(
        R.string.preference_download_source_title, SettingsRoute.FILES,
        R.string.preference_download_source_description, R.string.settings_section_downloads,
        "mirror bmclapi china download server"
    ),
    SettingEntry(
        R.string.preference_verify_manifest_title, SettingsRoute.FILES,
        R.string.preference_verify_manifest_description, R.string.settings_section_downloads,
        "verify checksum manifest integrity"
    ),
    SettingEntry(
        R.string.mcl_setting_check_libraries, SettingsRoute.FILES,
        R.string.mcl_setting_check_libraries_subtitle, R.string.settings_section_downloads,
        "libraries sha1 verify redownload"
    ),
    SettingEntry(
        R.string.arc_capes_title, SettingsRoute.FILES,
        R.string.arc_capes_desc, R.string.settings_section_cosmetics,
        "cape cosmetics skin"
    ),

    // ---------------------------------------------------------------------- About
    SettingEntry(
        R.string.preference_force_english_title, SettingsRoute.ABOUT,
        R.string.preference_force_english_description, R.string.settings_section_app,
        "language locale english translation"
    ),
    SettingEntry(
        R.string.preference_ask_for_notification_title, SettingsRoute.ABOUT,
        R.string.preference_ask_for_notification_description, R.string.settings_section_permissions,
        "permission notification allow"
    ),
    SettingEntry(
        R.string.preference_ask_for_microphone_title, SettingsRoute.ABOUT,
        R.string.preference_ask_for_microphone_description, R.string.settings_section_permissions,
        "permission microphone mic allow record"
    ),
    SettingEntry(
        R.string.mcl_tab_wiki, SettingsRoute.ABOUT, 0, R.string.settings_section_help,
        "help documentation guide website"
    ),
    SettingEntry(
        R.string.mcl_button_discord, SettingsRoute.ABOUT, 0, R.string.settings_section_help,
        "help community chat support"
    ),
    SettingEntry(
        R.string.main_share_logs, SettingsRoute.ABOUT, 0, R.string.settings_section_help,
        "log crash report bug send debug"
    )
)

/**
 * The index with its strings resolved.
 *
 * Resolved once per configuration rather than per keystroke, and outside the composition that
 * types into the field, because fifty resource lookups on every character is a cost search does
 * not need to pay.
 */
@Composable
fun rememberIndexedSettings(): List<IndexedSetting> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        SETTINGS_INDEX.map { entry ->
            val title = context.getString(entry.titleRes)
            val description =
                if (entry.descriptionRes != 0) context.getString(entry.descriptionRes) else ""
            val section =
                if (entry.sectionRes != 0) context.getString(entry.sectionRes) else ""
            val destination = context.getString(destinationTitle(entry.route))
            IndexedSetting(
                entry = entry,
                title = title,
                description = description,
                section = section,
                haystack = listOf(title, description, section, destination, entry.keywords)
                    .joinToString(" ")
                    .lowercase(Locale.getDefault())
            )
        }
    }
}

/** Which of the five screens a result lives on, as the breadcrumb under it says. */
fun destinationTitle(route: SettingsRoute): Int = when (route) {
    SettingsRoute.PERFORMANCE -> R.string.settings_dest_performance
    SettingsRoute.CONTROLS -> R.string.settings_dest_controls
    SettingsRoute.RECORDING -> R.string.settings_dest_recording
    SettingsRoute.FILES -> R.string.home_tile_files
    SettingsRoute.ABOUT -> R.string.settings_dest_about
    else -> R.string.home_settings
}

/** Everything matching what was typed, best first. Blank queries offer the suggestions instead. */
fun searchSettings(all: List<IndexedSetting>, query: String): List<IndexedSetting> {
    val trimmed = query.trim().lowercase(Locale.getDefault())
    if (trimmed.isEmpty()) return all.filter { it.entry.suggested }
    val tokens = trimmed.split(' ').filter { it.isNotEmpty() }
    return all.filter { it.matches(tokens) }.sortedBy { it.rank(trimmed) }
}
