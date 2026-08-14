package net.kdt.pojavlaunch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Amethyst X's palette.
 *
 * Built as a tonal ramp around the launcher's amethyst, rather than as a set of unrelated
 * colours, so surfaces stacked on one another stay distinguishable without needing borders. The
 * neutrals carry a little of the same violet, which keeps large dark areas from reading as flat
 * grey next to the accent.
 */

// Accent ramp
internal val Amethyst20 = Color(0xFF3B1B52)
internal val Amethyst30 = Color(0xFF542A73)
internal val Amethyst40 = Color(0xFF6E3D94)
/** The brand amethyst itself, kept as a token because gradients need the deep end of the ramp. */
internal val Amethyst50 = Color(0xFF9649B8)
internal val Amethyst70 = Color(0xFFC08CE8)
internal val Amethyst80 = Color(0xFFD6B4F2)
internal val Amethyst90 = Color(0xFFEEDCFA)

// Violet leaning neutrals, darkest first
internal val Neutral04 = Color(0xFF0E0B12)
internal val Neutral08 = Color(0xFF16121C)
internal val Neutral12 = Color(0xFF1D1826)
internal val Neutral16 = Color(0xFF251F30)
internal val Neutral22 = Color(0xFF2F2839)
internal val Neutral30 = Color(0xFF3A3245)
internal val Neutral70 = Color(0xFFA79DB4)
internal val Neutral80 = Color(0xFFCBC2D4)
internal val Neutral95 = Color(0xFFE9E2EF)

// Status
internal val Danger70 = Color(0xFFFFB4AB)
internal val Danger20 = Color(0xFF690005)
internal val Danger30 = Color(0xFF93000A)
internal val Success70 = Color(0xFF7FD69A)

/**
 * A warning that is not a failure, which until the log viewer nothing here had to say.
 *
 * Warm rather than yellow, and desaturated enough to sit on the neutral ramp without shouting:
 * on a screen where a third of the lines can be warnings, a true amber reads as an alarm and
 * makes the errors beside it count for less.
 */
internal val Warning70 = Color(0xFFE8C07D)

/** Live indicator, deliberately outside the accent ramp so it cannot be mistaken for chrome. */
val RecordingRed = Color(0xFFE5484D)
