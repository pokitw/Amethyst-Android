package net.kdt.pojavlaunch.ui.mediaedit

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.screenshot.edit.ImageEdit

/**
 * Viewing and editing a screenshot: crop, turn and flip.
 *
 * <b>Every piece of geometry is decided by `ImageEdit`</b>, which is plain Java driven over every
 * pixel of several images by `scripts/imageeditsim`. This file holds a crop rectangle expressed
 * as four fractions and the current orientation, and draws what it is given.
 *
 * ## Why the preview is produced by the exporter's own code
 *
 * Turning the picture on screen could be done with a `graphicsLayer` rotation, which would be
 * cheaper. It is not, because then the preview would be produced by one piece of arithmetic and
 * the saved file by another, and the two can disagree in exactly the ways that are invisible in
 * source: a flip that comes out vertical at ninety degrees, a crop taken from the wrong corner.
 * Instead the preview bitmap is run through the same `ImageEdit.plan` and `ImageEditor.apply`
 * that the export uses, on a downsampled copy, so <b>what is on screen is the output</b>. It
 * costs a few milliseconds on a button press rather than nothing, which is the right trade.
 *
 * ## Why the crop is fractions
 *
 * The preview is downsampled and the export works in full resolution source pixels. Holding the
 * crop as fractions of the displayed image keeps the sample factor out of the arithmetic
 * entirely: a plan built against a half size preview and applied to a full size bitmap crops the
 * wrong half of the picture and looks entirely plausible doing it.
 */

/** A crop as fractions of the displayed image. Null means the whole picture. */
data class CropFraction(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun normalised(): CropFraction = CropFraction(
        minOf(left, right).coerceIn(0f, 1f),
        minOf(top, bottom).coerceIn(0f, 1f),
        maxOf(left, right).coerceIn(0f, 1f),
        maxOf(top, bottom).coerceIn(0f, 1f),
    )

    fun isUsable(): Boolean {
        val n = normalised()
        return n.right - n.left > 0.02f && n.bottom - n.top > 0.02f
    }
}

/** Which corner a drag took hold of, or null for a fresh rectangle. */
private enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private val CORNER_GRAB = 32.dp

@Composable
fun ImageEditScreen(
    title: String,
    preview: Bitmap?,
    sourceWidth: Int,
    sourceHeight: Int,
    orientation: ImageEdit.Orientation,
    crop: CropFraction?,
    busy: Boolean,
    message: String?,
    dirty: Boolean,
    canSave: Boolean,
    canAddToGallery: Boolean,
    onAddToGallery: () -> Unit,
    onCrop: (CropFraction?) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onMirror: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onOpenElsewhere: () -> Unit,
    onBack: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EditorBar(
            title = title,
            saveEnabled = canSave,
            busy = busy,
            onSave = onSave,
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (preview == null) {
                Text(
                    message ?: stringResource(R.string.editor_unreadable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                CropCanvas(preview, crop, onCrop)

                val shownWidth = ImageEdit.displayedWidth(sourceWidth, sourceHeight, orientation)
                val shownHeight = ImageEdit.displayedHeight(sourceWidth, sourceHeight, orientation)
                val out = ImageEdit.plan(
                    sourceWidth, sourceHeight, orientation,
                    cropToRect(crop, shownWidth, shownHeight),
                )
                Text(
                    stringResource(R.string.image_size, out.outputWidth, out.outputHeight),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.image_crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ToolButton(R.drawable.ic_x_rotate_left, R.string.image_rotate_left, onRotateLeft)
                    ToolButton(R.drawable.ic_x_rotate_right, R.string.image_rotate_right, onRotateRight)
                    ToolButton(R.drawable.ic_x_mirror, R.string.image_mirror, onMirror)
                    if (crop != null) {
                        ToolButton(R.drawable.ic_x_crop, R.string.image_crop_reset) { onCrop(null) }
                    }
                }

                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    stringResource(R.string.editor_original_kept),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dirty) QuietButton(stringResource(R.string.editor_reset), onReset)
                    if (canAddToGallery) {
                        QuietButton(
                            stringResource(R.string.editor_add_to_gallery),
                            onAddToGallery,
                        )
                    }
                    QuietButton(stringResource(R.string.content_screenshot_open), onOpenElsewhere)
                }
            }
        }
    }
}

/**
 * The picture with a crop rectangle drawn over it.
 *
 * The gesture reads its crop through [rememberUpdatedState] and is keyed on [Unit], because
 * keying it on the crop cancels the drag on the first pixel of movement.
 */
@Composable
private fun CropCanvas(
    preview: Bitmap,
    crop: CropFraction?,
    onCrop: (CropFraction?) -> Unit,
) {
    val live = rememberUpdatedState(crop)
    var corner by remember { mutableStateOf<Corner?>(null) }
    var anchor by remember { mutableStateOf(Offset.Zero) }
    val grabPx = with(LocalDensity.current) { CORNER_GRAB.toPx() }

    val accent = MaterialTheme.colorScheme.primary
    val scrim = MaterialTheme.colorScheme.background.copy(alpha = 0.6f)

    val ratio = (preview.width.toFloat() / preview.height.toFloat().coerceAtLeast(1f))
        .coerceAtLeast(0.05f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
        ) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { at ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val current = live.value?.normalised()
                                corner = current?.let { nearestCorner(it, at, w, h, grabPx) }
                                if (corner == null) {
                                    // A press anywhere else starts a fresh rectangle from here,
                                    // which is what a drag on an uncropped picture has to mean.
                                    anchor = at
                                    onCrop(
                                        CropFraction(
                                            at.x / w, at.y / h, at.x / w, at.y / h
                                        )
                                    )
                                } else {
                                    anchor = oppositeOf(corner!!, current!!, w, h)
                                }
                            },
                            onDragEnd = {
                                corner = null
                                val settled = live.value?.normalised()
                                // A tap or a tiny drag is not a crop. Clearing it rather than
                                // keeping a sliver is what makes a mis-touch harmless.
                                if (settled == null || !settled.isUsable()) onCrop(null)
                                else onCrop(settled)
                            },
                            onDragCancel = {
                                // Settled the same way a finished drag is. Left alone, a drag
                                // the system takes away mid-gesture strands whatever sliver had
                                // been drawn so far, which enables Save on a crop the exporter
                                // will refuse.
                                corner = null
                                val settled = live.value?.normalised()
                                if (settled == null || !settled.isUsable()) onCrop(null)
                                else onCrop(settled)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val p = change.position
                                onCrop(
                                    CropFraction(
                                        anchor.x / w, anchor.y / h,
                                        p.x.coerceIn(0f, w) / w, p.y.coerceIn(0f, h) / h,
                                    )
                                )
                            },
                        )
                    }
            ) {
                val box = live.value?.normalised() ?: return@Canvas
                val w = size.width
                val h = size.height
                val l = box.left * w
                val t = box.top * h
                val r = box.right * w
                val b = box.bottom * h

                // Everything outside the crop is dimmed, so the selection reads as the picture
                // being kept rather than as a box drawn on top of it.
                drawRect(scrim, Offset.Zero, Size(w, t))
                drawRect(scrim, Offset(0f, b), Size(w, (h - b).coerceAtLeast(0f)))
                drawRect(scrim, Offset(0f, t), Size(l, (b - t).coerceAtLeast(0f)))
                drawRect(scrim, Offset(r, t), Size((w - r).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))

                val stroke = 2f * density
                drawRect(
                    color = accent,
                    topLeft = Offset(l, t),
                    size = Size((r - l).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)),
                    style = Stroke(width = stroke),
                )
                val arm = 16f * density
                listOf(
                    Offset(l, t) to Offset(1f, 1f),
                    Offset(r, t) to Offset(-1f, 1f),
                    Offset(l, b) to Offset(1f, -1f),
                    Offset(r, b) to Offset(-1f, -1f),
                ).forEach { (at, dir) ->
                    drawRect(accent, Offset(at.x - if (dir.x < 0) arm else 0f, at.y - stroke / 2f),
                        Size(arm, stroke * 2f))
                    drawRect(accent, Offset(at.x - stroke, at.y - if (dir.y < 0) arm else 0f),
                        Size(stroke * 2f, arm))
                }
            }
        }
    }
}

@Composable
private fun ToolButton(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickableTool(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Modifier.clickableTool(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/* ------------------------------------------------------------------ arithmetic */

/** The corner within reach of a press, or null. */
private fun nearestCorner(
    box: CropFraction,
    at: Offset,
    w: Float,
    h: Float,
    grabPx: Float,
): Corner? {
    val points = listOf(
        Corner.TOP_LEFT to Offset(box.left * w, box.top * h),
        Corner.TOP_RIGHT to Offset(box.right * w, box.top * h),
        Corner.BOTTOM_LEFT to Offset(box.left * w, box.bottom * h),
        Corner.BOTTOM_RIGHT to Offset(box.right * w, box.bottom * h),
    )
    val nearest = points.minByOrNull { (_, p) ->
        (p.x - at.x) * (p.x - at.x) + (p.y - at.y) * (p.y - at.y)
    } ?: return null
    val dx = nearest.second.x - at.x
    val dy = nearest.second.y - at.y
    // Radial rather than per axis, for the reason slide-to-repeat's threshold is: comparing the
    // axes separately reaches 1.41 times as far along a diagonal, which a finger notices.
    return if (dx * dx + dy * dy <= grabPx * grabPx) nearest.first else null
}

/** The corner diagonally opposite the one being dragged, which stays put. */
private fun oppositeOf(corner: Corner, box: CropFraction, w: Float, h: Float): Offset = when (corner) {
    Corner.TOP_LEFT -> Offset(box.right * w, box.bottom * h)
    Corner.TOP_RIGHT -> Offset(box.left * w, box.bottom * h)
    Corner.BOTTOM_LEFT -> Offset(box.right * w, box.top * h)
    Corner.BOTTOM_RIGHT -> Offset(box.left * w, box.top * h)
}

/** Fractions of the displayed image to the pixel rectangle `ImageEdit.plan` expects. */
fun cropToRect(crop: CropFraction?, displayedWidth: Int, displayedHeight: Int): ImageEdit.Rect? {
    if (crop == null) return null
    val n = crop.normalised()
    return ImageEdit.Rect(
        (n.left * displayedWidth).toInt(),
        (n.top * displayedHeight).toInt(),
        (n.right * displayedWidth).toInt(),
        (n.bottom * displayedHeight).toInt(),
    )
}
