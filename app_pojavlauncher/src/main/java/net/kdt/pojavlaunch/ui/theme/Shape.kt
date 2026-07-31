package net.kdt.pojavlaunch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner rounding, a touch softer than the Material defaults.
 *
 * The steps are far enough apart to read as a hierarchy: chips and thumbnails sit tight, cards
 * are noticeably rounder, and sheets rounder still, so an element's role is legible from its
 * silhouette before any text is read.
 */
val AmethystXShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)
