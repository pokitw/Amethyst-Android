package net.kdt.pojavlaunch.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An icon well shaped like an inventory slot.
 *
 * This is where the Minecraft reference lives, and it is deliberately the whole of it: a squarish
 * tile with a light inset along the top-left and a dark one along the bottom-right, which is the
 * bevel of a slot rendered entirely in Material tonal surfaces. No pixel art, no texture. It
 * should read as Minecraft to someone who has played and as ordinary Material to everyone else.
 *
 * @param size the outer size; the corner radius follows it so the shape stays proportional
 * @param color the well's fill, usually a step above the surface it sits on
 */
@Composable
fun SlotWell(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(size / 3.4f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(color)
            .border(1.5.dp, SlotBevel, shape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Light from the top left, shadow to the bottom right — the whole trick, in one gradient. */
private val SlotBevel = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.07f),
    0.5f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.32f)
)
