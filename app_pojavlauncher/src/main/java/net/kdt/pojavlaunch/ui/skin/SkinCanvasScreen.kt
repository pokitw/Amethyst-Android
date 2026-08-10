package net.kdt.pojavlaunch.ui.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppScaffold
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import kotlin.math.floor

/**
 * Painting a skin, one face at a time.
 *
 * <b>A face, not the atlas.</b> The raw 64 by 64 sheet is the format's problem, not the player's:
 * it is an unrolled cross with the head's top wedged above its sides and the left arm hiding in
 * the bottom corner, and nobody can paint a face while looking at it. So the canvas shows one
 * rectangle blown up with a grid, the picker above it says which part and which side, and the
 * atlas is never shown at all. The preview beside it is what ties the two together.
 *
 * <b>The preview is live.</b> Every stroke lands on the model as it is painted, which is the one
 * thing that makes a phone skin editor usable: there is no room for a separate preview step and
 * no patience for one.
 */
@Composable
fun SkinCanvasScreen(
    state: SkinEditorState,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    AppScaffold(
        title = state.name,
        subtitle = stringResource(
            if (state.dirty) R.string.skin_unsaved else R.string.skin_saved
        ),
        onBack = onBack,
        barAction = { SaveButton(state.dirty, onSave) }
    ) {
        Spacer(Modifier.height(10.dp))

        // Preview and canvas side by side: the whole point is watching the model change, and a
        // preview below the fold would be a preview nobody sees while painting.
        Row(
            Modifier.fillMaxWidth().height(230.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .weight(0.85f)
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainer)
            ) {
                // Reading the revision counter is what subscribes this to the painting: Compose
                // cannot see inside a Bitmap, so the counter is the change.
                @Suppress("UNUSED_EXPRESSION") state.revision
                SkinPreview(
                    skin = state.image(),
                    slim = state.slim,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainer)
                    .padding(8.dp)
            ) {
                PaintCanvas(state, Modifier.fillMaxSize())
            }
        }

        Spacer(Modifier.height(14.dp))
        ToolRow(state)

        Spacer(Modifier.height(14.dp))
        SectionLabel(stringResource(R.string.skin_section_colour))
        SettingsCard { ColourPalette(state) }

        Spacer(Modifier.height(4.dp))
        SectionLabel(stringResource(R.string.skin_section_part))
        PartPicker(state)
        Spacer(Modifier.height(8.dp))
        FacePicker(state)
        Spacer(Modifier.height(8.dp))
        LayerRow(state)

        Spacer(Modifier.height(18.dp))
    }
}

/* ------------------------------------------------------------------ the canvas */

/**
 * The zoomed face, with a grid.
 *
 * Touches are mapped back to texture pixels by the same scale the drawing uses, so what is under
 * the finger is what changes. A drag paints continuously; the whole drag is one undo step,
 * because undoing a stroke a pixel at a time is not undo.
 */
@Composable
private fun PaintCanvas(state: SkinEditorState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rect = state.rect
    if (rect == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.skin_no_overlay),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        return
    }

    val checkerA = colors.surfaceContainerHigh
    val checkerB = colors.surfaceContainerHighest
    val gridColour = colors.outline.copy(alpha = 0.45f)

    Canvas(
        modifier.pointerInput(rect, state.tool) {
            val cellW = size.width.toFloat() / rect.width
            val cellH = size.height.toFloat() / rect.height
            val cell = minOf(cellW, cellH)
            val originX = (size.width - cell * rect.width) / 2f
            val originY = (size.height - cell * rect.height) / 2f

            fun toTexture(position: Offset): Pair<Int, Int>? {
                val tx = floor((position.x - originX) / cell).toInt()
                val ty = floor((position.y - originY) / cell).toInt()
                if (tx < 0 || ty < 0 || tx >= rect.width || ty >= rect.height) return null
                return rect.x + tx to rect.y + ty
            }

            detectTapGestures(
                onTap = { position ->
                    val texture = toTexture(position) ?: return@detectTapGestures
                    when (state.tool) {
                        SkinTool.FILL -> state.fill(texture.first, texture.second)
                        SkinTool.PICKER -> state.pick(texture.first, texture.second)
                        else -> {
                            state.beginStroke()
                            state.paint(texture.first, texture.second)
                            state.endStroke()
                        }
                    }
                }
            )
        }.pointerInput(rect, state.tool) {
            val cellW = size.width.toFloat() / rect.width
            val cellH = size.height.toFloat() / rect.height
            val cell = minOf(cellW, cellH)
            val originX = (size.width - cell * rect.width) / 2f
            val originY = (size.height - cell * rect.height) / 2f

            fun toTexture(position: Offset): Pair<Int, Int>? {
                val tx = floor((position.x - originX) / cell).toInt()
                val ty = floor((position.y - originY) / cell).toInt()
                if (tx < 0 || ty < 0 || tx >= rect.width || ty >= rect.height) return null
                return rect.x + tx to rect.y + ty
            }

            // Only the two painting tools drag. Dragging a bucket across a face would fire a
            // fill per pixel crossed, and dragging the eyedropper would end on whatever colour
            // the finger happened to lift over.
            if (state.tool != SkinTool.PENCIL && state.tool != SkinTool.ERASER) {
                return@pointerInput
            }
            detectDragGestures(
                onDragStart = { position ->
                    state.beginStroke()
                    toTexture(position)?.let { state.paint(it.first, it.second) }
                },
                onDragEnd = { state.endStroke() },
                onDragCancel = { state.endStroke() }
            ) { change, _ ->
                change.consume()
                toTexture(change.position)?.let { state.paint(it.first, it.second) }
            }
        }
    ) {
        // Subscribe to the paint.
        @Suppress("UNUSED_EXPRESSION") state.revision

        val cell = minOf(size.width / rect.width, size.height / rect.height)
        val originX = (size.width - cell * rect.width) / 2f
        val originY = (size.height - cell * rect.height) / 2f

        // A checkerboard under the pixels, because a skin is full of deliberate transparency and
        // a flat backdrop makes "erased" and "painted the backdrop colour" look identical.
        for (x in 0 until rect.width) {
            for (y in 0 until rect.height) {
                drawRect(
                    color = if ((x + y) % 2 == 0) checkerA else checkerB,
                    topLeft = Offset(originX + x * cell, originY + y * cell),
                    size = androidx.compose.ui.geometry.Size(cell, cell)
                )
            }
        }

        // The face itself, drawn as one image so the sampling is the platform's problem.
        drawImage(
            image = state.image(),
            srcOffset = IntOffset(rect.x, rect.y),
            srcSize = IntSize(rect.width, rect.height),
            dstOffset = IntOffset(originX.toInt(), originY.toInt()),
            dstSize = IntSize((cell * rect.width).toInt(), (cell * rect.height).toInt()),
            filterQuality = FilterQuality.None
        )

        // The grid last, so it sits over the paint. Skipped when the cells are too small for it
        // to be anything but noise.
        if (cell >= 7f) {
            for (x in 0..rect.width) {
                drawLine(
                    gridColour,
                    Offset(originX + x * cell, originY),
                    Offset(originX + x * cell, originY + cell * rect.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..rect.height) {
                drawLine(
                    gridColour,
                    Offset(originX, originY + y * cell),
                    Offset(originX + cell * rect.width, originY + y * cell),
                    strokeWidth = 1f
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ controls */

@Composable
private fun ToolRow(state: SkinEditorState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolButton(R.drawable.ic_x_pencil, R.string.skin_tool_pencil,
            state.tool == SkinTool.PENCIL) { state.applyTool(SkinTool.PENCIL) }
        ToolButton(R.drawable.ic_x_eraser, R.string.skin_tool_eraser,
            state.tool == SkinTool.ERASER) { state.applyTool(SkinTool.ERASER) }
        ToolButton(R.drawable.ic_x_fill, R.string.skin_tool_fill,
            state.tool == SkinTool.FILL) { state.applyTool(SkinTool.FILL) }
        ToolButton(R.drawable.ic_x_dropper, R.string.skin_tool_picker,
            state.tool == SkinTool.PICKER) { state.applyTool(SkinTool.PICKER) }
        Spacer(Modifier.weight(1f))
        ToolButton(R.drawable.ic_x_undo, R.string.skin_undo, false,
            enabled = state.canUndo) { state.undo() }
        ToolButton(R.drawable.ic_x_redo, R.string.skin_redo, false,
            enabled = state.canRedo) { state.redo() }
    }
}

@Composable
private fun ToolButton(
    iconRes: Int,
    labelRes: Int,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (selected) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainer
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = stringResource(labelRes),
            tint = when {
                !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
                selected -> colors.primary
                else -> colors.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * The palette.
 *
 * A fixed set rather than a colour wheel, because a wheel on a phone is a fiddly control that
 * gets you a colour you did not mean, and because skins are made of skin, cloth and hair, which
 * is a small and knowable set. The eyedropper covers everything else: any colour already in the
 * skin, including one imported from a picture, is one tap away.
 */
private val PALETTE = listOf(
    0xFF000000, 0xFF3F3F3F, 0xFF7A7A7A, 0xFFBFBFBF, 0xFFFFFFFF,
    0xFF5B3A29, 0xFF8A5A3B, 0xFFC68642, 0xFFE0AC69, 0xFFF1C27D,
    0xFF8B1A1A, 0xFFD32F2F, 0xFFEF6C00, 0xFFF9A825, 0xFFFFF176,
    0xFF1B5E20, 0xFF388E3C, 0xFF7CB342, 0xFF00838F, 0xFF26C6DA,
    0xFF0D47A1, 0xFF1976D2, 0xFF64B5F6, 0xFF4A148C, 0xFF9649B8,
    0xFFAD1457, 0xFFEC407A, 0xFFF8BBD0, 0xFF4E342E, 0xFF212121
)

@Composable
private fun ColourPalette(state: SkinEditorState) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        PALETTE.chunked(10).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { argb ->
                    val colour = Color(argb)
                    val selected = state.colour.toArgb() == colour.toArgb()
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(colour)
                            .clickable { state.applyColour(colour) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Which body part, as a rail of names. Six of them, so a rail beats a dropdown. */
@Composable
private fun PartPicker(state: SkinEditorState) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.parts.forEachIndexed { index, part ->
            val selected = index == state.partIndex
            Text(
                stringResource(part.labelRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) colors.primary else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer
                    )
                    .clickable { state.selectPart(index) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}

/** Which side of that part, plus the outer layer switch and the two bulk actions. */
@Composable
private fun FacePicker(state: SkinEditorState) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SkinFace.values().forEach { face ->
            val selected = face == state.face
            Text(
                stringResource(faceLabel(face)),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer
                    )
                    .clickable { state.selectFace(face) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * The outer layer switch, and the two bulk actions.
 *
 * <b>The outer layer is not an extra.</b> The hat and the jacket are where most of a skin's
 * character lives, and they are a whole second set of rectangles that the atlas hides in the
 * corners, so a canvas that could not reach them would be an editor that cannot make a normal
 * skin. Mirror is here for the same reason: faces are symmetrical far more often than not, so
 * painting half of one and mirroring it is how most of them actually get drawn.
 */
@Composable
private fun LayerRow(state: SkinEditorState) {
    val colors = MaterialTheme.colorScheme
    val hasOverlay = state.part.overlay != null
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.skin_outer_layer),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !hasOverlay -> colors.onSurfaceVariant.copy(alpha = 0.38f)
                state.editingOverlay -> colors.primary
                else -> colors.onSurfaceVariant
            },
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (state.editingOverlay) colors.primary.copy(alpha = 0.14f)
                    else colors.surfaceContainer
                )
                .clickable(enabled = hasOverlay) { state.applyOverlay(!state.editingOverlay) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            stringResource(R.string.skin_mirror),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surfaceContainer)
                .clickable { state.mirrorFace() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Text(
            stringResource(R.string.skin_clear_face),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surfaceContainer)
                .clickable { state.clearFace() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

private fun faceLabel(face: SkinFace): Int = when (face) {
    SkinFace.FRONT -> R.string.skin_face_front
    SkinFace.BACK -> R.string.skin_face_back
    SkinFace.LEFT -> R.string.skin_face_left
    SkinFace.RIGHT -> R.string.skin_face_right
    SkinFace.TOP -> R.string.skin_face_top
    SkinFace.BOTTOM -> R.string.skin_face_bottom
}

@Composable
private fun SaveButton(dirty: Boolean, onSave: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.skin_save),
        style = MaterialTheme.typography.titleSmall,
        color = if (dirty) net.kdt.pojavlaunch.ui.theme.Amethyst20 else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (dirty) colors.primary else colors.surfaceContainer)
            .clickable(onClick = onSave)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
