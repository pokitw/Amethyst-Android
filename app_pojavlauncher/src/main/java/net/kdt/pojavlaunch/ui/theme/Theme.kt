package net.kdt.pojavlaunch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Amethyst X's theme.
 *
 * Dark only, deliberately: the launcher is used beside a game that is itself dark, and a light
 * mode would be jarring rather than useful, so the system setting is not consulted. Dynamic
 * colour is left off for the same reason the palette exists at all, that the amethyst is the
 * brand and should not be replaced by whatever the wallpaper happens to be.
 */
private val AmethystXColors = darkColorScheme(
    primary = Amethyst70,
    onPrimary = Amethyst20,
    primaryContainer = Amethyst30,
    onPrimaryContainer = Amethyst90,
    inversePrimary = Amethyst40,

    secondary = Amethyst80,
    onSecondary = Amethyst20,
    secondaryContainer = Neutral22,
    onSecondaryContainer = Amethyst90,

    tertiary = Amethyst80,
    onTertiary = Amethyst20,
    tertiaryContainer = Amethyst30,
    onTertiaryContainer = Amethyst90,

    background = Neutral04,
    onBackground = Neutral95,

    surface = Neutral04,
    onSurface = Neutral95,
    surfaceVariant = Neutral16,
    onSurfaceVariant = Neutral80,

    // The container ramp is what gives stacked cards and sheets their separation.
    surfaceContainerLowest = Neutral04,
    surfaceContainerLow = Neutral08,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral16,
    surfaceContainerHighest = Neutral22,

    outline = Neutral30,
    outlineVariant = Neutral16,

    error = Danger70,
    onError = Danger20,
    errorContainer = Danger30,
    onErrorContainer = Neutral95,

    scrim = Neutral04,
)

@Composable
fun AmethystXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmethystXColors,
        typography = AmethystXTypography,
        shapes = AmethystXShapes,
        content = content
    )
}
