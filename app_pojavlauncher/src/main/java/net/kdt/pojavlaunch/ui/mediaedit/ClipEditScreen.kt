package net.kdt.pojavlaunch.ui.mediaedit

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.recorder.edit.ClipPlan
import net.kdt.pojavlaunch.ui.common.AppBackButton
import net.kdt.pojavlaunch.ui.recordings.formatDuration

private val GRAB_SLOP = 28.dp
private val MIN_TICK_SPACING = 7.dp

private enum class Grab { START, END, PLAYHEAD }

/** One frame of the filmstrip. */
data class Thumb(val atUs: Long, val bitmap: Bitmap)

/**
 * The clip editor: a preview you can actually play, a timeline you can split, and the tools
 * underneath.
 *
 * ## Why it is shaped the way it is
 *
 * The first version had the preview, a scrub bar and a "remove this part" button that cut a
 * fixed two seconds at the marker, and no playback at all. Every one of those is the wrong
 * answer to the question somebody actually has, which is "watch this, and take out the bit I do
 * not want". You cannot find the bit you do not want without watching, and you cannot say how
 * long it is in advance. So the shape is the one every video editor has converged on, for the
 * same reasons they converged on it:
 *
 * - the **picture** is the biggest thing and it plays;
 * - the **transport** sits directly under it, where a thumb reaching up from the bottom lands;
 * - the **timeline** is a filmstrip with the playhead on it;
 * - the **tools act on what is selected**, from a bar at the bottom.
 *
 * ## Splitting rather than cutting a length
 *
 * Put the playhead somewhere and press Split, and the span divides in two. Select a span and
 * press Delete and it is gone from the output. That is two steps instead of one, and it is
 * better because each step is a thing you can see the result of: the previous model asked for a
 * duration, which is the one number nobody knows.
 *
 * `ClipPlan.timelineSegments` owns the arithmetic and `clipsim` drives it, so what a span is and
 * what deleting it removes are decided in the half of the feature that can be executed here.
 *
 * ## Playback previews the edit
 *
 * The transport plays the file, and the editor steps the playhead over anything deleted. So
 * pressing play shows the clip as it will be saved, not as it is on disk. That is the single
 * most useful thing playback can do here and it is why the skipping lives in the host rather
 * than in `ClipPlayer`: the player plays a file, the editor decides where the player is allowed
 * to be.
 */
@Composable
fun ClipEditScreen(
    title: String,
    durationUs: Long,
    syncUs: LongArray,
    thumbs: List<Thumb>,
    aspect: Float,
    inUs: Long,
    outUs: Long,
    playUs: Long,
    cuts: List<ClipPlan.Range>,
    segments: List<ClipPlan.Range>,
    selected: Int,
    plan: ClipPlan.Plan,
    playing: Boolean,
    busy: Boolean,
    canSave: Boolean,
    /** Nothing has been trimmed, split or deleted yet, so the how-to is still worth showing. */
    pristine: Boolean,
    canSplit: Boolean,
    canDelete: Boolean,
    selectedRemoved: Boolean,
    progress: Float,
    message: String?,
    canAddToGallery: Boolean,
    onAttach: (TextureView) -> Unit,
    onPlayPause: () -> Unit,
    onAddToGallery: () -> Unit,
    onIn: (Long) -> Unit,
    onOut: (Long) -> Unit,
    onPlayhead: (Long) -> Unit,
    onSelect: (Int) -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Two columns on a short screen, which is the control center's rule applied to the
        // other orientation: stacked, the bar, the transport, the timeline, the tools and the
        // summary come to more than a phone is tall in landscape, and the thing that gives way
        // is the weighted child, which is the picture. An editor whose picture is nothing is
        // not an editor. Side by side there is width to spare and the picture keeps its height.
        val sideBySide = maxHeight < 520.dp && maxWidth > maxHeight

        val preview: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { context -> TextureView(context).also(onAttach) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (aspect > 0f) aspect else 16f / 9f),
                )
            }
        }

        val controls: @Composable () -> Unit = {
            Transport(
                playing = playing,
                // The position inside the EDIT, not inside the file. A clock counting the
                // source would be reporting on material the player is about to stop having,
                // and it would jump by the length of every deleted span as playback stepped
                // over it.
                atUs = outputPositionOf(plan, playUs),
                totalUs = plan.outputDurationUs.takeIf { it > 0L } ?: durationUs,
                enabled = durationUs > 0L && !busy,
                part = if (segments.size > 1) {
                    stringResource(R.string.clip_part_of, selected + 1, segments.size)
                } else {
                    null
                },
                onPlayPause = onPlayPause,
            )
            Timeline(
                durationUs = durationUs,
                syncUs = syncUs,
                thumbs = thumbs,
                inUs = inUs,
                outUs = outUs,
                playUs = playUs,
                cuts = cuts,
                segments = segments,
                selected = selected,
                onIn = onIn,
                onOut = onOut,
                onPlayhead = onPlayhead,
                onSelect = onSelect,
            )
            Tools(
                canSplit = canSplit && !busy,
                canDelete = canDelete && !busy,
                removed = selectedRemoved,
                canReset = !busy,
                onSplit = onSplit,
                onDelete = onDelete,
                onReset = onReset,
            )
        }

        val details: @Composable (Modifier) -> Unit = { modifier ->
            Column(
                modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (busy) ProgressTrack(progress)
                Summary(plan, durationUs, syncUs, inUs, pristine)
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canAddToGallery && !busy) {
                    QuietButton(stringResource(R.string.editor_add_to_gallery), onAddToGallery)
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            EditorBar(title, canSave, busy, onSave, onBack)
            if (sideBySide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        controls()
                        details(Modifier)
                    }
                }
            } else {
                // The picture takes the room that is left, which is what makes this read as an
                // editor rather than as a settings screen with a video on it.
                preview(Modifier.weight(1f).fillMaxWidth())
                controls()
                details(
                    Modifier
                        .heightIn(max = 148.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ transport */

/**
 * Play, pause, and where we are.
 *
 * The time on the left is the position inside the **edit**, not inside the file, and the total
 * is the edit's length. Showing the file's own clock would be reporting on something the player
 * is about to stop having.
 */
@Composable
private fun Transport(
    playing: Boolean,
    atUs: Long,
    totalUs: Long,
    enabled: Boolean,
    part: String?,
    onPlayPause: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatUs(atUs) + " / " + formatUs(totalUs),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .then(if (enabled) Modifier.clickable(onClick = onPlayPause) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (playing) R.drawable.ic_x_pause else R.drawable.ic_x_play
                ),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        // The third the spacer used to hold. Which part the tools are about to act on is the
        // one fact the timeline says only through a 2.5dp ring, and it is the fact somebody is
        // checking before they press Delete.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (part != null) {
                Text(
                    part,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ the timeline */

/**
 * The filmstrip, the trim handles, the spans and the playhead.
 *
 * The gesture is keyed on [Unit] and reads through [rememberUpdatedState]. Keying it on the
 * handle positions instead looks right and cancels the drag on the first pixel of movement,
 * because the key changes as soon as the value does.
 */
@Composable
private fun Timeline(
    durationUs: Long,
    syncUs: LongArray,
    thumbs: List<Thumb>,
    inUs: Long,
    outUs: Long,
    playUs: Long,
    cuts: List<ClipPlan.Range>,
    segments: List<ClipPlan.Range>,
    selected: Int,
    onIn: (Long) -> Unit,
    onOut: (Long) -> Unit,
    onPlayhead: (Long) -> Unit,
    onSelect: (Int) -> Unit,
) {
    val live = rememberUpdatedState(longArrayOf(inUs, outUs, playUs, durationUs))
    val liveSpans = rememberUpdatedState(segments)
    var grab by remember { mutableStateOf<Grab?>(null) }
    val slopPx = with(LocalDensity.current) { GRAB_SLOP.toPx() }
    val minTickPx = with(LocalDensity.current) { MIN_TICK_SPACING.toPx() }

    // Wrapped once rather than inside the draw pass: asImageBitmap allocates a wrapper per
    // call, and the strip is redrawn on every frame of a drag. Keyed on the count because the
    // strip is filled in one go and is never edited afterwards.
    val images = remember(thumbs.size) {
        thumbs.map { it.atUs to it.bitmap.asImageBitmap() }
    }

    val accent = MaterialTheme.colorScheme.primary
    val well = MaterialTheme.colorScheme.surfaceContainerHighest
    val scrim = MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
    // A deleted span is covered, not tinted. A wash of the error colour over a filmstrip of
    // grass and sky reads as a slightly different clip rather than as absent, which is the one
    // thing this band has to say; drawn over a picture, only taking the picture away says it.
    val cutScrim = MaterialTheme.colorScheme.background.copy(alpha = 0.82f)
    val cutEdge = MaterialTheme.colorScheme.error
    val tickColour = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val head = MaterialTheme.colorScheme.onSurface

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(86.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(well)
            .pointerInput(Unit) {
                // Horizontal only. detectDragGestures is omnidirectional and consumes on slop,
                // so in the landscape layout, where this sits inside a vertical scroll, the
                // 86dp full width strip was the largest grabbable band in the pane and could
                // not be used to scroll past it. A timeline has nothing to say about vertical
                // movement anyway.
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        val total = live.value[3].coerceAtLeast(1L)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        fun px(us: Long) = us.toFloat() / total * w
                        // END first: minByOrNull keeps the first minimum, so two handles on one
                        // pixel hand back the end, which is the one that can move away. Picking
                        // the start there traps the selection with no way out but a reset.
                        val near = listOf(
                            Grab.END to px(live.value[1]),
                            Grab.START to px(live.value[0]),
                            Grab.PLAYHEAD to px(live.value[2]),
                        ).minByOrNull { kotlin.math.abs(it.second - at.x) }
                        grab = if (near != null && kotlin.math.abs(near.second - at.x) <= slopPx) {
                            near.first
                        } else {
                            Grab.PLAYHEAD
                        }
                        if (grab == Grab.PLAYHEAD) onPlayhead(usAt(at.x, w, total))
                    },
                    onDragEnd = { grab = null },
                    onDragCancel = { grab = null },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val total = live.value[3].coerceAtLeast(1L)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val at = usAt(change.position.x, w, total)
                        when (grab) {
                            Grab.START -> onIn(at)
                            Grab.END -> onOut(at)
                            Grab.PLAYHEAD, null -> onPlayhead(at)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // A separate recogniser: detectDragGestures only wakes past the touch slop, so a
                // plain tap reaches none of it. A tap both moves the playhead and selects the
                // span it landed in, which is what makes Delete need no second gesture.
                detectTapGestures { at ->
                    val total = live.value[3].coerceAtLeast(1L)
                    val tapped = usAt(at.x, size.width.toFloat().coerceAtLeast(1f), total)
                    onPlayhead(tapped)
                    onSelect(ClipPlan.segmentAt(liveSpans.value, tapped))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val total = durationUs.coerceAtLeast(1L)
            fun x(us: Long) = (us.toFloat() / total * w).coerceIn(0f, w)

            images.forEachIndexed { index, (atUs, bitmap) ->
                // Placed at the time it was taken from, not at its index. The two agree while
                // the strip is sampled evenly, and relying on that would make the picture
                // silently wrong the first time it is not.
                val from = x(atUs)
                val to = if (index + 1 < images.size) x(images[index + 1].first) else w
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(from.toInt(), 0),
                    dstSize = IntSize((to - from).toInt().coerceAtLeast(1), h.toInt()),
                    alpha = 0.85f,
                )
            }

            // Outside the trim is dimmed rather than hidden, so the whole clip stays legible
            // and the selection reads as a window onto it.
            drawRect(scrim, Offset.Zero, Size(x(inUs), h))
            drawRect(scrim, Offset(x(outUs), 0f), Size((w - x(outUs)).coerceAtLeast(0f), h))

            cuts.forEach { cut ->
                val l = x(cut.startUs)
                val r = x(cut.endUs)
                if (r > l) {
                    drawRect(cutScrim, Offset(l, 0f), Size(r - l, h))
                    val rule = 2f * density
                    drawRect(cutEdge, Offset(l, (h - rule) / 2f), Size(r - l, rule))
                }
            }

            // Split boundaries, drawn as the gaps a cut editor shows between clips.
            for (i in 1 until segments.size) {
                val at = x(segments[i].startUs)
                drawRect(well, Offset(at - 1f, 0f), Size(2f, h))
            }

            // The selected span wears a ring, which is what makes Delete unambiguous.
            if (selected in segments.indices) {
                val span = segments[selected]
                val l = x(span.startUs)
                val r = x(span.endUs)
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(l + 1f, 1f),
                    size = Size((r - l - 2f).coerceAtLeast(2f), h - 2f),
                    cornerRadius = CornerRadius(6f * density, 6f * density),
                    style = Stroke(width = 2.5f * density),
                )
            }

            // The points the clip may start from, only while they are far enough apart to carry
            // information: at forty minutes there are twelve hundred and they merge into a line.
            if (syncUs.size > 1) {
                val spacing = w / (syncUs.size - 1).coerceAtLeast(1)
                if (spacing >= minTickPx) {
                    syncUs.forEach { at ->
                        drawRect(tickColour, Offset(x(at), 0f), Size(1.5f, h * 0.16f))
                    }
                }
            }

            drawRect(accent, Offset(x(inUs) - 1.5f, 0f), Size(3f, h))
            drawRect(accent, Offset(x(outUs) - 1.5f, 0f), Size(3f, h))

            val gripW = 10f * density
            val gripH = h * 0.38f
            listOf(x(inUs), x(outUs)).forEach { at ->
                drawRoundRect(
                    color = accent,
                    // coerceIn throws when its bounds invert, and both of these are derived
                    // from a measured width, so the ceiling is floored rather than trusted.
                    topLeft = Offset(
                        (at - gripW / 2f).coerceIn(0f, (w - gripW).coerceAtLeast(0f)),
                        (h - gripH) / 2f,
                    ),
                    size = Size(gripW, gripH),
                    cornerRadius = CornerRadius(gripW / 2f, gripW / 2f),
                )
            }

            // The playhead last, so it is never hidden by a span or a grip.
            val px = x(playUs)
            drawRect(head, Offset(px - 1.5f, 0f), Size(3f, h))
            drawRoundRect(
                color = head,
                topLeft = Offset(px - 5f * density, 0f),
                size = Size(10f * density, 7f * density),
                cornerRadius = CornerRadius(2f * density, 2f * density),
            )
        }
    }
}

/* ------------------------------------------------------------------ tools */

@Composable
private fun Tools(
    canSplit: Boolean,
    canDelete: Boolean,
    removed: Boolean,
    canReset: Boolean,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolAction(R.drawable.ic_x_split, R.string.clip_split, canSplit, false, onSplit)
        // One control with two labels rather than a Delete and a separate Restore. The span is
        // selected and this says what pressing it does to that span, which is what makes a
        // mis-tap cost one tap instead of the whole edit through Reset.
        ToolAction(
            if (removed) R.drawable.ic_x_undo else R.drawable.ic_x_trash,
            if (removed) R.string.clip_restore else R.string.clip_delete,
            canDelete,
            !removed,
            onDelete,
        )
        ToolAction(R.drawable.ic_x_undo, R.string.editor_reset, canReset, false, onReset)
    }
}

/**
 * One tool.
 *
 * Delete is tinted with the error colour and sits in the middle rather than at an end, which is
 * the one place the handbook's "destructive actions do not get prime real estate" is bent: on a
 * cutting screen removing a span is the ordinary thing somebody came to do, and it is undoable
 * until Save because nothing is written until then.
 */
@Composable
private fun ToolAction(
    iconRes: Int,
    labelRes: Int,
    enabled: Boolean,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .width(96.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/* ------------------------------------------------------------------ the rest */

@Composable
private fun Summary(
    plan: ClipPlan.Plan,
    durationUs: Long,
    syncUs: LongArray,
    inUs: Long,
    pristine: Boolean,
) {
    // Only once there is a clip. Before that the plan is empty by construction and would print
    // "this clip has no point the video can start from" underneath the real reason.
    val problem = if (durationUs <= 0L) null else when (plan.problem) {
        ClipPlan.Problem.EMPTY_SELECTION -> stringResource(R.string.clip_selection_empty)
        ClipPlan.Problem.NOTHING_LEFT -> stringResource(R.string.clip_nothing_left)
        ClipPlan.Problem.NO_SYNC_FRAMES -> stringResource(R.string.clip_no_sync)
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (problem != null) {
            Text(
                problem,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (durationUs > 0L) {
            Text(
                stringResource(
                    R.string.clip_length,
                    formatUs(plan.outputDurationUs),
                    formatUs(durationUs),
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Said only when the start moved under the finger, which is when it is an explanation
        // rather than noise.
        val snapped = ClipPlan.syncAtOrAfter(syncUs, inUs)
        if (snapped >= 0L && snapped != inUs) {
            Text(
                stringResource(R.string.clip_start_snapped, formatUs(snapped)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // A whole second, not a microsecond: the label renders at second granularity, so
        // anything smaller prints "0:00 more was removed than you asked for".
        if (plan.extraRemovedUs >= 1_000_000L) {
            Text(
                stringResource(R.string.clip_extra_removed, formatUs(plan.extraRemovedUs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Only until the first edit. It is three lines of how-to, which is exactly what is
        // wanted on arrival and is clutter under a toolbar for the rest of the session; the
        // moment somebody has split or trimmed anything they have already read it or worked
        // it out, and the line below is the one that stays worth saying.
        if (pristine) {
            Text(
                stringResource(R.string.clip_grid_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.editor_original_kept),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressTrack(progress: Float) {
    // Drawn rather than a LinearProgressIndicator, for the reason the launch console draws its
    // own: a thin track on the app's own ramp, and no dependency on which overload of a Material
    // component this BOM happens to carry.
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/* ------------------------------------------------------------------ small pieces */

@Composable
internal fun EditorBar(
    title: String,
    saveEnabled: Boolean,
    busy: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBackButton(onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        AccentButton(
            text = stringResource(if (busy) R.string.editor_saving else R.string.editor_save),
            iconRes = null,
            enabled = saveEnabled,
            onClick = onSave,
        )
    }
}

@Composable
internal fun AccentButton(text: String, iconRes: Int?, enabled: Boolean, onClick: () -> Unit) {
    val background = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val foreground = if (enabled) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            // 48dp floor: the padding alone leaves this about 35dp tall, under the handbook's
            // minimum for anything tappable.
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

@Composable
internal fun QuietButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/* ------------------------------------------------------------------ arithmetic */

/**
 * Where the playhead is, measured in the output.
 *
 * `mapToOutput` answers -1 for a source time that no segment holds, and coercing that to zero
 * is the wrong repair: the sentinel and the very start of the clip then read identically, so
 * parking the marker inside a deleted span, or outside the trim, or exactly on the clip's end,
 * all printed "0:00" over a marker sitting in the middle of the strip.
 *
 * The honest answer for a time that is not in the output is where playback will next be, which
 * is the output start of the first segment that begins at or after it, and the output's full
 * length once there are none. That also makes the clock monotonic as playback steps over a cut.
 */
internal fun outputPositionOf(plan: ClipPlan.Plan, atUs: Long): Long {
    val direct = plan.mapToOutput(atUs)
    if (direct >= 0L) return direct
    plan.segments.forEach { segment ->
        if (segment.sourceStartUs >= atUs) return segment.outputStartUs
    }
    return plan.outputDurationUs
}

internal fun usAt(x: Float, width: Float, totalUs: Long): Long =
    ((x / width).coerceIn(0f, 1f) * totalUs).toLong()

internal fun formatUs(us: Long): String = formatDuration(us / 1000L)
