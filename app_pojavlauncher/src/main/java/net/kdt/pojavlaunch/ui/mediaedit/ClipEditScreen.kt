package net.kdt.pojavlaunch.ui.mediaedit

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.ui.common.AppBackButton
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.recorder.edit.ClipPlan
import net.kdt.pojavlaunch.ui.recordings.formatDuration

/**
 * The timeline, the preview and the controls for trimming and cutting a recording.
 *
 * <b>Every decision is made by `ClipPlan`</b>, which is plain Java driven by `scripts/clipsim`.
 * This file holds four numbers (in, out, the position marker and a list of cuts), hands them
 * over, and draws the answer. Nothing here works out which samples survive, and nothing here
 * should start: a wrong plan is a corrupt video that compiles perfectly, and only the Java half
 * can be executed before a user runs it.
 *
 * ## Showing the constraint rather than explaining it
 *
 * A clip can only begin at a sync frame, roughly every two seconds, while it can end anywhere.
 * That asymmetry is invisible and surprising, so the timeline draws it: the start handle lands on
 * a tick and says where it landed, and the end handle moves freely. Ticks are drawn only while
 * they are far enough apart to mean something, because at forty minutes there are twelve hundred
 * of them and a solid grey line is worse than nothing.
 */

/** How much of the timeline a finger has to be within to grab a handle. */
private val GRAB_SLOP = 28.dp

/** Below this spacing the sync ticks merge into a line and stop being information. */
private val MIN_TICK_SPACING = 7.dp

private enum class Grab { START, END, PLAYHEAD }

/** One frame of the filmstrip. */
data class Thumb(val atUs: Long, val bitmap: Bitmap)

@Composable
fun ClipEditScreen(
    title: String,
    durationUs: Long,
    syncUs: LongArray,
    thumbs: List<Thumb>,
    previewFrame: Bitmap?,
    aspect: Float,
    inUs: Long,
    outUs: Long,
    playUs: Long,
    cuts: List<ClipPlan.Range>,
    plan: ClipPlan.Plan,
    busy: Boolean,
    progress: Float,
    message: String?,
    canAddToGallery: Boolean,
    onAddToGallery: () -> Unit,
    onIn: (Long) -> Unit,
    onOut: (Long) -> Unit,
    onPlayhead: (Long) -> Unit,
    onAddCut: () -> Unit,
    onRemoveCut: (Int) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
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
            saveEnabled = plan.isUsable() && !busy,
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
            FramePreview(previewFrame, aspect, formatUs(playUs), formatUs(durationUs))

            Timeline(
                durationUs = durationUs,
                syncUs = syncUs,
                thumbs = thumbs,
                inUs = inUs,
                outUs = outUs,
                playUs = playUs,
                cuts = cuts,
                onIn = onIn,
                onOut = onOut,
                onPlayhead = onPlayhead,
            )

            // What the edit will produce, before it is produced. The length is the plan's own
            // answer rather than (out - in), so a cut is reflected the moment it is added.
            PlanSummary(plan, durationUs, syncUs, inUs)

            CutControls(
                cuts = cuts,
                canAdd = canCutAt(playUs, inUs, outUs, cuts),
                onAddCut = onAddCut,
                onRemoveCut = onRemoveCut,
            )

            if (busy) ProgressTrack(progress)
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                stringResource(R.string.clip_lossless) + " "
                        + stringResource(R.string.editor_original_kept),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuietButton(stringResource(R.string.editor_reset), onReset)
                    // Offered only when the saved copy did not already go there, which is the
                    // setting's job. A button that repeated what had just happened by itself
                    // would be a second way to do one thing.
                    if (canAddToGallery) {
                        QuietButton(
                            stringResource(R.string.editor_add_to_gallery),
                            onAddToGallery,
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ the timeline */

/**
 * The filmstrip, the selection and the three handles.
 *
 * The gesture is keyed on [Unit] and reads its values through [rememberUpdatedState]. Keying it
 * on the handle positions instead looks right and cancels the drag on the first pixel of
 * movement, because the key changes as soon as the value does.
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
    onIn: (Long) -> Unit,
    onOut: (Long) -> Unit,
    onPlayhead: (Long) -> Unit,
) {
    val live = rememberUpdatedState(longArrayOf(inUs, outUs, playUs, durationUs))
    var grab by remember { mutableStateOf<Grab?>(null) }
    // Deliberately no `val density` here. DrawScope exposes its own `density` as a Float, and a
    // local of that name would shadow it inside the Canvas below, where `10f * density` would
    // then be Float times Density and fail to compile.
    val slopPx = with(LocalDensity.current) { GRAB_SLOP.toPx() }
    val minTickPx = with(LocalDensity.current) { MIN_TICK_SPACING.toPx() }

    val accent = MaterialTheme.colorScheme.primary
    val wellColour = MaterialTheme.colorScheme.surfaceContainerHighest
    val scrim = MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
    val cutColour = MaterialTheme.colorScheme.error.copy(alpha = 0.34f)
    val tickColour = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val onAccent = MaterialTheme.colorScheme.onSurface

    Box(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(wellColour)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { at ->
                        val total = live.value[3].coerceAtLeast(1L)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        fun px(us: Long) = us.toFloat() / total * w
                        val candidates = listOf(
                            Grab.START to px(live.value[0]),
                            Grab.END to px(live.value[1]),
                            Grab.PLAYHEAD to px(live.value[2]),
                        )
                        val nearest = candidates.minByOrNull { kotlin.math.abs(it.second - at.x) }
                        // Outside every handle's reach the touch moves the position marker,
                        // because that is what a tap on a timeline means everywhere else.
                        grab = if (nearest != null
                            && kotlin.math.abs(nearest.second - at.x) <= slopPx
                        ) nearest.first else Grab.PLAYHEAD
                        if (grab == Grab.PLAYHEAD) {
                            onPlayhead(usAt(at.x, w, total))
                        }
                    },
                    onDragEnd = { grab = null },
                    onDragCancel = { grab = null },
                    onDrag = { change, _ ->
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
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val total = durationUs.coerceAtLeast(1L)
            fun x(us: Long) = (us.toFloat() / total * w).coerceIn(0f, w)

            // The filmstrip. Each thumbnail is drawn in the slot it belongs to, so the pictures
            // line up with the times they were taken at rather than merely filling the bar.
            thumbs.forEachIndexed { index, thumb ->
                // Placed at the time it was taken from, not at its index. The two agree while
                // the strip is sampled evenly, which it is, and relying on that would make the
                // picture silently wrong the first time it is not.
                val from = x(thumb.atUs)
                val to = if (index + 1 < thumbs.size) x(thumbs[index + 1].atUs) else w
                drawImage(
                    image = thumb.bitmap.asImageBitmap(),
                    dstOffset = IntOffset(from.toInt(), 0),
                    dstSize = IntSize((to - from).toInt().coerceAtLeast(1), h.toInt()),
                    alpha = 0.85f,
                )
            }

            // Everything outside the selection is dimmed rather than hidden, so the whole clip
            // stays legible and the selection reads as a window onto it.
            drawRect(scrim, Offset.Zero, Size(x(inUs), h))
            drawRect(scrim, Offset(x(outUs), 0f), Size((w - x(outUs)).coerceAtLeast(0f), h))

            // The cuts, inside the selection, in the error colour at low alpha. Nothing has gone
            // wrong, so it is a wash rather than a solid block.
            cuts.forEach { cut ->
                val left = x(cut.startUs)
                val right = x(cut.endUs)
                if (right > left) drawRect(cutColour, Offset(left, 0f), Size(right - left, h))
            }

            // The points the clip is allowed to start from, drawn only while they are far enough
            // apart to carry information.
            if (syncUs.size > 1) {
                val spacing = w / (syncUs.size - 1).coerceAtLeast(1)
                if (spacing >= minTickPx) {
                    syncUs.forEach { at ->
                        val tx = x(at)
                        drawRect(tickColour, Offset(tx, 0f), Size(1.5f, h * 0.18f))
                    }
                }
            }

            // The selection's own edges.
            drawRect(accent, Offset(x(inUs) - 1.5f, 0f), Size(3f, h))
            drawRect(accent, Offset(x(outUs) - 1.5f, 0f), Size(3f, h))

            // The position marker, thinner and in the foreground colour so it never reads as a
            // third edge of the selection.
            val px = x(playUs)
            drawRect(onAccent, Offset(px - 1f, 0f), Size(2f, h))

            // Grips, drawn rather than placed as views: a view over the timeline is a view that
            // can swallow the drag the timeline is listening for, which is the same rule the
            // control layout editor follows for its own decorations.
            val gripW = 10f * density
            val gripH = h * 0.38f
            listOf(x(inUs), x(outUs)).forEach { at ->
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(
                        (at - gripW / 2f).coerceIn(0f, w - gripW),
                        (h - gripH) / 2f,
                    ),
                    size = Size(gripW, gripH),
                    cornerRadius = CornerRadius(gripW / 2f, gripW / 2f),
                )
            }
        }

    }
}

/* ------------------------------------------------------------------ the rest */

@Composable
private fun FramePreview(frame: Bitmap?, aspect: Float, at: String, total: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (aspect > 0f) aspect else 16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.66f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                "$at / $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PlanSummary(
    plan: ClipPlan.Plan,
    durationUs: Long,
    syncUs: LongArray,
    inUs: Long,
) {
    val problem = when (plan.problem) {
        ClipPlan.Problem.EMPTY_SELECTION -> stringResource(R.string.clip_selection_empty)
        ClipPlan.Problem.NOTHING_LEFT -> stringResource(R.string.clip_nothing_left)
        ClipPlan.Problem.NO_SYNC_FRAMES -> stringResource(R.string.clip_no_sync)
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (problem != null) {
            Text(
                problem,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
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
        // Where the start actually landed, said only when it is not where it was put. A caption
        // that always showed would be noise; one that appears exactly when something moved under
        // the finger is an explanation.
        val snapped = ClipPlan.syncAtOrAfter(syncUs, inUs)
        if (snapped >= 0L && snapped != inUs) {
            Text(
                stringResource(R.string.clip_start_snapped, formatUs(snapped)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // How much more than was asked for came out. Said only when there is some, because a
        // forward snap can take up to two seconds of wanted footage with it and finding that
        // out by watching the result is the kind of surprise this screen exists to prevent.
        if (plan.extraRemovedUs > 0L) {
            Text(
                stringResource(R.string.clip_extra_removed, formatUs(plan.extraRemovedUs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.clip_grid_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CutControls(
    cuts: List<ClipPlan.Range>,
    canAdd: Boolean,
    onAddCut: () -> Unit,
    onRemoveCut: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.clip_cuts),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            AccentButton(
                text = stringResource(R.string.clip_add_cut),
                iconRes = R.drawable.ic_x_cut,
                enabled = canAdd,
                onClick = onAddCut,
            )
        }
        if (cuts.isEmpty()) {
            Text(
                stringResource(R.string.clip_no_cuts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            cuts.forEachIndexed { index, cut ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.clip_cut_row,
                            formatUs(cut.startUs),
                            formatUs(cut.endUs),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    QuietButton(stringResource(R.string.clip_cut_remove)) { onRemoveCut(index) }
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(progress: Float) {
    // Drawn rather than a LinearProgressIndicator, for the same reason the launch console draws
    // its own: a thin track on the app's own ramp, and no dependency on which overload of a
    // Material component this BOM happens to carry.
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
                .height(4.dp)
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
internal fun AccentButton(
    text: String,
    iconRes: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val foreground = if (enabled) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .then(
                if (enabled) Modifier.clickableRow(onClick) else Modifier
            )
            // 48dp floor: the padding alone leaves this about 35dp tall, which is under the
            // handbook's minimum for anything tappable.
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
            .clickableRow(onClick)
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/* ------------------------------------------------------------------ arithmetic */

internal fun usAt(x: Float, width: Float, totalUs: Long): Long =
    ((x / width).coerceIn(0f, 1f) * totalUs).toLong()

internal fun formatUs(us: Long): String = formatDuration(us / 1000L)

/**
 * Whether the position marker is somewhere a cut could start.
 *
 * Inside the selection and not already inside a cut. The length of the cut is decided when it is
 * added, so the only question here is whether the marker is in a sensible place.
 */
internal fun canCutAt(
    playUs: Long,
    inUs: Long,
    outUs: Long,
    cuts: List<ClipPlan.Range>,
): Boolean {
    if (playUs <= inUs || playUs >= outUs) return false
    return cuts.none { playUs >= it.startUs && playUs < it.endUs }
}
