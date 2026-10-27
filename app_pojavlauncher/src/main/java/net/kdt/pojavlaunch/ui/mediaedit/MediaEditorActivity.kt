package net.kdt.pojavlaunch.ui.mediaedit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.BaseActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.media.GalleryExport
import net.kdt.pojavlaunch.recorder.edit.ClipEditor
import net.kdt.pojavlaunch.recorder.edit.ClipPlan
import net.kdt.pojavlaunch.screenshot.edit.ImageEdit
import net.kdt.pojavlaunch.screenshot.edit.ImageEditor
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme
import java.io.File

/**
 * Hosts both editors: a recording's timeline and a screenshot's canvas.
 *
 * One activity rather than two because they share almost everything that is not the canvas: the
 * bar, the save, the gallery copy, the progress, the error surfacing and the rule that the
 * original is never written to. What differs is one screen each.
 *
 * <b>Every decision lives in the Java half</b>, `ClipPlan` and `ImageEdit`, both of which are
 * driven by harnesses in `scripts/`. This class holds state, moves work off the main thread and
 * reports what happened. Nothing here works out which samples or which pixels survive.
 */
class MediaEditorActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PATH = "net.kdt.pojavlaunch.mediaedit.PATH"
        private const val EXTRA_KIND = "net.kdt.pojavlaunch.mediaedit.KIND"
        private const val KIND_CLIP = "clip"
        private const val KIND_IMAGE = "image"

        /** How many pictures the filmstrip holds. Enough to navigate by, few enough to decode. */
        private const val FILMSTRIP = 12

        @JvmStatic
        fun editClip(context: Context, file: File): Intent =
            Intent(context, MediaEditorActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_KIND, KIND_CLIP)

        @JvmStatic
        fun editImage(context: Context, file: File): Intent =
            Intent(context, MediaEditorActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_KIND, KIND_IMAGE)
    }

    /** BaseActivity hides the system bars by default, which is right for the game only (§16.12). */
    override fun setFullscreen(): Boolean = false

    private lateinit var file: File
    private var isClip = true

    // Shared
    private var busy by mutableStateOf(false)
    private var progress by mutableStateOf(0f)
    private var message by mutableStateOf<String?>(null)
    private var confirmLeave by mutableStateOf(false)

    /**
     * The file the last save produced, while it is still not in the gallery.
     *
     * Null once there is nothing to offer: before a save, and after the copy has been made
     * either by the preference or by the button. That is what makes the button appear exactly
     * when it can do something.
     */
    private var pendingGallery by mutableStateOf<File?>(null)

    /** The edit as it was when it was last written, so back can tell saved work from unsaved. */
    private var savedSignature by mutableStateOf<String?>(null)

    // Clip
    private var source by mutableStateOf<ClipEditor.Source?>(null)
    private var inUs by mutableStateOf(0L)
    private var outUs by mutableStateOf(0L)
    private var playUs by mutableStateOf(0L)

    /**
     * Where the timeline is divided, and which of the resulting spans have been deleted.
     *
     * Two lists rather than one because they answer different questions: a split is a boundary
     * the player put there and keeps across everything else they do, while a deletion is a
     * property of the span between two boundaries. `cuts` is held as a subset of the current
     * spans, which is the invariant `ClipPlan.isRemoved` rests on, and `ClipPlan.retime` is
     * what restores it whenever the shape changes.
     */
    private val splits = mutableStateListOf<Long>()
    private val cuts = mutableStateListOf<ClipPlan.Range>()
    private var selected by mutableStateOf(0)
    private val thumbs = mutableStateListOf<Thumb>()

    private var playing by mutableStateOf(false)
    private var tickJob: Job? = null
    private var seekJob: Job? = null

    // Bound method references rather than lambdas that close over `player` while it is being
    // constructed. The lambda form does compile, and it reads like a cycle to everybody who
    // meets it afterwards.
    private val player = ClipPlayer(
        onPosition = ::onPlayerPosition,
        onReady = ::onPlayerReady,
        onEnded = ::onPlaybackEnded,
    )

    // Image
    private var imageInfo by mutableStateOf<ImageEditor.Info?>(null)
    private var basePreview: Bitmap? = null
    private var preview by mutableStateOf<Bitmap?>(null)
    private var orientation by mutableStateOf(ImageEdit.Orientation.identity())
    private var crop by mutableStateOf<CropFraction?>(null)
    private var orientJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent?.getStringExtra(EXTRA_PATH)
        if (path == null) {
            finish()
            return
        }
        file = File(path)
        isClip = intent?.getStringExtra(EXTRA_KIND) != KIND_IMAGE

        setContent {
            AmethystXTheme {
                if (isClip) ClipHost() else ImageHost()
                if (confirmLeave) LeaveDialog()
            }
        }

        onBackPressedDispatcher.addCallback(this, unsavedBack)
        if (isClip) loadClip() else loadImage()
    }

    override fun onPause() {
        super.onPause()
        // Sound coming out of a backgrounded editor is the one playback failure nobody forgives,
        // and the ticker is a loop that would otherwise go on seeking a player nobody can see.
        if (playing) {
            player.pause()
            playing = false
            stopTicking()
        }
    }

    override fun onDestroy() {
        stopTicking()
        seekJob?.cancel()
        player.dispose()
        super.onDestroy()
    }

    /**
     * Enable the back callback exactly while leaving would lose something.
     *
     * Called from every place that changes the edit. Leaving it enabled when there is nothing to
     * lose traps Back on this screen, which is the half of this pattern that is easy to omit.
     */
    private fun syncBackCallback() {
        // Enabled WHILE BUSY as well, which is the opposite of what it first said. The callback
        // is what makes the system back do nothing during an export; disabling it there handed
        // back to the default, which finishes the activity and takes the export's reporting
        // with it. The guard inside handleOnBackPressed was unreachable.
        unsavedBack.isEnabled = busy || hasChanges()
    }

    /* -------------------------------------------------------------- clip */

    @Composable
    private fun ClipHost() {
        // Kept in step here rather than at every mutation: a recomposition follows every change
        // that could matter, and a sprinkling of calls is exactly how one gets forgotten.
        SideEffect { syncBackCallback() }
        val live = source
        // Remembered against everything it reads. Without this the plan is rebuilt by any
        // recomposition at all, including each of the twelve filmstrip pictures arriving, and
        // the two lists have to be part of the key by VALUE: they are snapshot lists, so
        // mutating one leaves its identity unchanged and a key holding the list itself never
        // changes.
        val cutKey = cuts.joinToString(",") { it.startUs.toString() + ":" + it.endUs }
        val splitKey = splits.joinToString(",")
        val plan = remember(live, inUs, outUs, cutKey) {
            if (live == null) {
                ClipPlan.trim(0L, LongArray(0), LongArray(0), 0L, 0L)
            } else {
                ClipPlan.build(live.durationUs, live.syncUs, live.framesUs, inUs, outUs, cuts)
            }
        }
        val spans = remember(inUs, outUs, splitKey) {
            ClipPlan.timelineSegments(inUs, outUs, splits.toLongArray())
        }
        val here = selected.coerceIn(0, (spans.size - 1).coerceAtLeast(0))
        val selectedSpan = spans.getOrNull(here)
        val selectedRemoved = selectedSpan != null && ClipPlan.isRemoved(selectedSpan, cuts)
        ClipEditScreen(
            title = stringResource(R.string.editor_title_clip),
            durationUs = live?.durationUs ?: 0L,
            syncUs = live?.syncUs ?: LongArray(0),
            thumbs = thumbs,
            aspect = aspectOf(live),
            inUs = inUs,
            outUs = outUs,
            playUs = playUs,
            cuts = cuts,
            segments = spans,
            selected = here,
            plan = plan,
            playing = playing,
            busy = busy,
            canSave = plan.isUsable() && !busy && live != null && hasChanges(),
            // Not at a boundary, because a split there divides nothing, and inside the trim.
            canSplit = live != null && playUs > inUs && playUs < outUs &&
                    spans.none { it.startUs == playUs },
            // Restoring is always allowed. Deleting is not allowed to empty the output, which
            // is the one state the exporter refuses and there is no reason to let somebody
            // arrange it.
            canDelete = selectedSpan != null && (selectedRemoved || spans.any {
                it !== selectedSpan && !ClipPlan.isRemoved(it, cuts)
            }),
            selectedRemoved = selectedRemoved,
            progress = progress,
            message = message,
            canAddToGallery = pendingGallery != null,
            onAttach = { view -> view.surfaceTextureListener = player.listener() },
            onPlayPause = ::togglePlay,
            onAddToGallery = ::addToGallery,
            onIn = { at ->
                // Clamped against the other handle so the two can never cross, which an
                // ordinary drag would otherwise do and turn the selection inside out.
                //
                // Written as an explicit floor and ceiling rather than one coerceIn chain:
                // coerceIn THROWS when its minimum exceeds its maximum, so a chain whose bounds
                // are themselves derived from moving state is one off-by-one away from crashing
                // on a drag. Here the bounds cannot invert whatever the other values are.
                val ceiling = (outUs - 1L).coerceAtLeast(0L)
                inUs = if (at < 0L) 0L else if (at > ceiling) ceiling else at
                if (playUs < inUs) seekPlayhead(inUs)
                reshape()
            },
            onOut = { at ->
                val total = (live?.durationUs ?: 0L).coerceAtLeast(0L)
                val floor = (inUs + 1L).coerceAtMost(total)
                outUs = if (at < floor) floor else if (at > total) total else at
                if (playUs > outUs) seekPlayhead(outUs)
                reshape()
            },
            onPlayhead = { at -> seekPlayhead(at.coerceIn(0L, live?.durationUs ?: 0L)) },
            onSelect = { index -> if (index >= 0) selected = index },
            onSplit = {
                if (live != null && playUs > inUs && playUs < outUs && !splits.contains(playUs)) {
                    splits.add(playUs)
                    splits.sort()
                    reshape()
                    // Selection follows to the span that now starts here, which is the one the
                    // next tap is almost always aimed at.
                    selected = ClipPlan.segmentAt(
                        ClipPlan.timelineSegments(inUs, outUs, splits.toLongArray()), playUs
                    ).coerceAtLeast(0)
                    message = null
                }
            },
            onDelete = { toggleDeleted(here) },
            onReset = {
                val total = live?.durationUs ?: 0L
                inUs = 0L
                outUs = total
                splits.clear()
                cuts.clear()
                selected = 0
                message = null
                seekPlayhead(0L)
            },
            onSave = ::saveClip,
            onBack = ::leave,
        )
    }

    /**
     * Restate the deletions against the spans as they now are.
     *
     * Called from everything that changes the timeline's shape. Without it `isRemoved`, which
     * is exact equality, would stop finding a cut the moment its span's bounds moved, and the
     * shading would say a span was kept while the plan still removed it.
     */
    private fun reshape() {
        val spans = ClipPlan.timelineSegments(inUs, outUs, splits.toLongArray())
        val next = ClipPlan.retime(cuts, spans)
        // Written only when it actually differs. This runs on every pointer event of a trim
        // drag, and clearing and refilling a snapshot list is a change whatever it ends up
        // holding, so an unconditional write would recompose the whole screen sixty times a
        // second to arrive at the list it already had.
        if (!sameRanges(cuts, next)) {
            cuts.clear()
            cuts.addAll(next)
        }
        val bounded = selected.coerceIn(0, (spans.size - 1).coerceAtLeast(0))
        if (bounded != selected) selected = bounded
    }

    private fun sameRanges(a: List<ClipPlan.Range>, b: List<ClipPlan.Range>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i].startUs != b[i].startUs || a[i].endUs != b[i].endUs) return false
        }
        return true
    }

    /* -------------------------------------------------------------- playback */

    /** Delete the selected span, or give it back if it has already gone. */
    private fun toggleDeleted(index: Int) {
        val span = ClipPlan.timelineSegments(inUs, outUs, splits.toLongArray())
            .getOrNull(index) ?: return
        if (ClipPlan.isRemoved(span, cuts)) {
            cuts.removeAll { it.startUs == span.startUs && it.endUs == span.endUs }
        } else {
            cuts.add(span)
        }
        reshape()
        message = null
    }

    /**
     * The player's own duration is deliberately ignored: `ClipEditor.read` walked the track and
     * is the number every bound here is derived from, and two durations that disagree by a
     * frame would put the trim handle and the playhead on different clocks.
     */
    private fun onPlayerReady(@Suppress("UNUSED_PARAMETER") durationUs: Long) {
        stopTicking()
        playing = false
        // Seeking is what puts a frame on the surface: a prepared MediaPlayer that has never
        // been started or seeked shows nothing, so without this the editor opens on black and
        // looks broken until something is touched.
        player.seekTo(playUs)
    }

    private fun onPlaybackEnded() {
        stopTicking()
        playing = false
        seekPlayheadNow(inUs)
    }

    private fun togglePlay() {
        if (source == null) return
        if (playing) {
            player.pause()
            playing = false
            stopTicking()
            return
        }
        // Starting from the end, or from inside something deleted, starts from the top of the
        // selection instead. Pressing play and watching nothing happen is the failure here.
        if (playUs >= outUs || cutHolding(playUs) != null) seekPlayheadNow(inUs)
        player.play()
        playing = true
        startTicking()
    }

    private fun startTicking() {
        stopTicking()
        tickJob = lifecycleScope.launch {
            while (playing) {
                player.pump()
                // Four times a frame at 60fps is plenty to keep a playhead smooth and is far
                // cheaper than a per-frame callback; the position is read, not computed.
                delay(60L)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    /**
     * Keep playback inside the edit.
     *
     * This is where the preview becomes a preview of the OUTPUT rather than of the file: the
     * player plays the whole clip and the editor steps it over everything that will not be in
     * the saved copy. `ClipPlayer` is told nothing about any of this on purpose, so there is
     * only ever one thing that decides what the edit is.
     */
    private fun onPlayerPosition(atUs: Long) {
        if (atUs < 0L) return
        // A scrub in flight owns the playhead. Without this the ticker reads the position the
        // player still has, a frame behind the finger, and drags it back there.
        if (seekJob?.isActive == true) return
        if (atUs >= outUs) {
            player.pause()
            playing = false
            stopTicking()
            // Rewound rather than left at the end, so the next press of play starts the
            // selection again instead of doing nothing.
            seekPlayheadNow(inUs)
            return
        }
        if (atUs < inUs) {
            playUs = inUs
            player.seekTo(inUs)
            return
        }
        val cut = cutHolding(atUs)
        if (cut != null) {
            val resume = cut.endUs
            if (resume >= outUs) {
                player.pause()
                playing = false
                stopTicking()
                seekPlayheadNow(inUs)
            } else {
                playUs = resume
                player.seekTo(resume)
            }
            return
        }
        playUs = atUs
    }

    private fun cutHolding(atUs: Long): ClipPlan.Range? =
        cuts.firstOrNull { atUs >= it.startUs && atUs < it.endUs }

    /**
     * Move the playhead now and the picture a moment later.
     *
     * Debounced because a drag produces a value per frame and `MediaPlayer.seekTo` decodes to
     * get there; asking for sixty of them a second makes the picture lag the finger by more
     * than doing nothing would. The marker itself is not debounced, so the gesture stays
     * instant and only the frame catches up.
     */
    private fun seekPlayhead(atUs: Long) {
        playUs = atUs
        seekJob?.cancel()
        seekJob = lifecycleScope.launch {
            delay(60L)
            player.seekTo(atUs)
        }
    }

    /** Both at once, for the paths that are about to depend on where the player actually is. */
    private fun seekPlayheadNow(atUs: Long) {
        seekJob?.cancel()
        seekJob = null
        playUs = atUs
        player.seekTo(atUs)
    }

    private fun aspectOf(live: ClipEditor.Source?): Float {
        if (live == null || live.width <= 0 || live.height <= 0) return 16f / 9f
        return live.width.toFloat() / live.height.toFloat()
    }

    private fun loadClip() {
        busy = true
        message = getString(R.string.editor_reading)
        lifecycleScope.launch {
            val read = withContext(Dispatchers.IO) {
                try {
                    ClipEditor.read(file)
                } catch (t: Throwable) {
                    null
                }
            }
            busy = false
            if (read == null) {
                message = getString(R.string.editor_unreadable)
                return@launch
            }
            message = null
            if (!read.isUsable) {
                // Said here rather than at save time: there is no point letting somebody arrange
                // an edit that cannot be carried out. The reason is disambiguated because the
                // three causes have nothing to do with each other and only one of them is
                // something the player could have brought on themselves.
                message = when {
                    !read.framesInOrder -> getString(R.string.clip_out_of_order)
                    read.syncUs.isEmpty() -> getString(R.string.clip_no_sync)
                    else -> getString(R.string.editor_unreadable)
                }
                return@launch
            }
            source = read
            inUs = 0L
            outUs = read.durationUs
            playUs = 0L
            message = null
            loadFilmstrip(read)
            player.open(file)
        }
    }

    /**
     * The filmstrip.
     *
     * Frames are asked for with OPTION_CLOSEST_SYNC, which is the cheap one: it returns the sync
     * frame nearest the time rather than decoding forward to an exact frame. That is exactly
     * right here, because a sync frame is also the only place the clip is allowed to start, so
     * the pictures on the strip are the pictures a start handle can land on.
     */
    private fun loadFilmstrip(read: ClipEditor.Source) {
        lifecycleScope.launch {
            val frames = withContext(Dispatchers.IO) {
                val out = ArrayList<Thumb>()
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    for (i in 0 until FILMSTRIP) {
                        val at = read.durationUs * i / FILMSTRIP
                        val frame = try {
                            retriever.getFrameAtTime(
                                at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                            )
                        } catch (t: Throwable) {
                            null
                        } ?: continue
                        out.add(Thumb(at, scaleDown(frame, 160)))
                    }
                } catch (t: Throwable) {
                    // A clip whose frames cannot be fetched still trims perfectly well; the
                    // strip is navigation, not the feature.
                } finally {
                    releaseQuietly(retriever)
                }
                out
            }
            thumbs.clear()
            thumbs.addAll(frames)
        }
    }

    private fun saveClip() {
        val live = source ?: return
        val plan = ClipPlan.build(live.durationUs, live.syncUs, live.framesUs, inUs, outUs, cuts)
        if (!plan.isUsable()) {
            // Reachable only from a hand-built state now that the button is gated, but a Save
            // that does nothing and says nothing is the one outcome a button must never have.
            message = getString(R.string.editor_nothing_to_save)
            toast(message!!)
            return
        }
        busy = true
        progress = 0f
        message = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val out = ClipEditor.export(file, live, plan) { fraction ->
                        // Posted rather than set, because this runs on the export thread.
                        runOnUiThread { progress = fraction }
                    }
                    Result.success(out)
                } catch (t: Throwable) {
                    Result.failure<File>(t)
                }
            }
            busy = false
            result.fold(
                onSuccess = { out ->
                    val toGallery = GalleryExport.isEnabled(this@MediaEditorActivity)
                    if (toGallery) GalleryExport.publishVideoLater(this@MediaEditorActivity, out)
                    savedSignature = signature()
                    pendingGallery = if (toGallery) null else out
                    message = getString(
                        if (toGallery) R.string.editor_saved_gallery else R.string.editor_saved,
                        out.name,
                    )
                    toast(message!!)
                },
                onFailure = { t -> fail(t) },
            )
        }
    }

    /* -------------------------------------------------------------- image */

    @Composable
    private fun ImageHost() {
        SideEffect { syncBackCallback() }
        val info = imageInfo
        // Worked out here rather than from `dirty` alone, because a crop can be non null and
        // still be something ImageEdit refuses, and a turn can come back to where it started.
        // isNoOp is what tells the second case apart; it had no caller before this.
        val savePlan = if (info != null && info.isUsable) {
            ImageEdit.plan(
                info.width, info.height, orientation,
                cropToRect(
                    crop,
                    ImageEdit.displayedWidth(info.width, info.height, orientation),
                    ImageEdit.displayedHeight(info.width, info.height, orientation),
                ),
            )
        } else {
            null
        }
        ImageEditScreen(
            title = stringResource(R.string.editor_title_image),
            preview = preview,
            sourceWidth = info?.width ?: 0,
            sourceHeight = info?.height ?: 0,
            orientation = orientation,
            crop = crop,
            busy = busy,
            message = message,
            dirty = crop != null || !orientation.isIdentity,
            canSave = !busy && preview != null && savePlan != null && savePlan.isUsable
                    && !savePlan.isNoOp(info?.width ?: 0, info?.height ?: 0),
            canAddToGallery = pendingGallery != null,
            onAddToGallery = ::addToGallery,
            onCrop = { crop = it },
            onRotateLeft = { applyOrientation(orientation.rotatedLeft()) },
            onRotateRight = { applyOrientation(orientation.rotatedRight()) },
            onMirror = { applyOrientation(orientation.mirroredOnScreen()) },
            onReset = {
                crop = null
                applyOrientation(ImageEdit.Orientation.identity())
            },
            onSave = ::saveImage,
            onOpenElsewhere = {
                try {
                    Tools.openPath(this, file, false)
                } catch (t: Throwable) {
                    fail(t)
                }
            },
            onBack = ::leave,
        )
    }

    private fun loadImage() {
        busy = true
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val info = ImageEditor.read(file)
                val bitmap = if (info.isUsable) ImageEditor.preview(file) else null
                info to bitmap
            }
            busy = false
            imageInfo = loaded.first
            basePreview = loaded.second
            preview = loaded.second
            if (loaded.second == null) message = getString(R.string.editor_unreadable)
        }
    }

    /**
     * Turn or flip the preview using the exporter's own code.
     *
     * The crop is cleared, because a rectangle drawn on the picture as it was has no meaning on
     * the picture as it now is, and silently reinterpreting it in the new frame is the kind of
     * thing nobody notices until they have saved it.
     */
    private fun applyOrientation(next: ImageEdit.Orientation) {
        val base = basePreview ?: return
        orientation = next
        crop = null
        // Cancelled rather than queued: two quick taps on rotate start two turns of a bitmap,
        // and whichever finishes last wins, which is not necessarily the one that was asked for
        // last. The orientation itself is already correct above; this only re-renders it.
        orientJob?.cancel()
        orientJob = lifecycleScope.launch {
            val turned = withContext(Dispatchers.IO) {
                try {
                    val plan = ImageEdit.plan(base.width, base.height, next, null)
                    ImageEditor.apply(base, plan)
                } catch (t: Throwable) {
                    null
                }
            }
            if (turned != null) preview = turned
        }
    }

    private fun saveImage() {
        val info = imageInfo ?: return
        if (!info.isUsable) return
        val shownWidth = ImageEdit.displayedWidth(info.width, info.height, orientation)
        val shownHeight = ImageEdit.displayedHeight(info.width, info.height, orientation)
        val plan = ImageEdit.plan(
            info.width, info.height, orientation,
            cropToRect(crop, shownWidth, shownHeight),
        )
        if (!plan.isUsable) {
            message = getString(R.string.editor_nothing_to_save)
            toast(message!!)
            return
        }
        busy = true
        message = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(ImageEditor.export(this@MediaEditorActivity, file, plan))
                } catch (t: Throwable) {
                    Result.failure<File>(t)
                }
            }
            busy = false
            result.fold(
                onSuccess = { out ->
                    val toGallery = GalleryExport.isEnabled(this@MediaEditorActivity)
                    val copied = toGallery && withContext(Dispatchers.IO) {
                        // publishImage is synchronous and small; a screenshot is megabytes, not
                        // gigabytes, so it does not need the recorder's deferred path.
                        GalleryExport.publishImage(this@MediaEditorActivity, out)
                    }
                    savedSignature = signature()
                    pendingGallery = if (copied) null else out
                    message = getString(
                        if (copied) R.string.editor_saved_gallery else R.string.editor_saved,
                        out.name,
                    )
                    toast(message!!)
                },
                onFailure = { t -> fail(t) },
            )
        }
    }

    /**
     * Put the saved copy in the phone's gallery, on request.
     *
     * The setting already does this for anyone who leaves it on, so this exists for the case it
     * cannot cover: somebody who keeps the automatic copy off, because it doubles the space
     * every capture takes, but wants this one edit where a chat app can find it. Offered only
     * while there is something to copy, so it is never a button that does nothing.
     *
     * A clip goes through the deferred path on its own thread, because it can be gigabytes and
     * must not be what stands between the player and the rest of the app. A picture is copied
     * here and reports whether it worked, which below Android 10 it may not: that needs a
     * storage permission nothing asks for at runtime.
     */
    private fun addToGallery() {
        val file = pendingGallery ?: return
        pendingGallery = null
        if (isClip) {
            GalleryExport.publishVideoOnRequestLater(this, file)
            message = getString(R.string.editor_gallery_copying)
            toast(message!!)
            return
        }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                GalleryExport.publishImageOnRequest(this@MediaEditorActivity, file)
            }
            message = getString(
                if (ok) R.string.editor_gallery_done else R.string.editor_gallery_failed
            )
            toast(message!!)
            // Offered again on a failure, since trying once more is free and the cause can be a
            // permission the player can grant.
            if (!ok) pendingGallery = file
        }
    }

    /* -------------------------------------------------------------- shared */

    @Composable
    private fun LeaveDialog() {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; finish() }) {
                    Text(stringResource(R.string.editor_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(R.string.editor_keep_editing))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    /**
     * What the current edit is, as one comparable value.
     *
     * Used only to tell an edit that has been written from one that has not, which is why it is
     * a string rather than anything structured: the question is equality and nothing else.
     */
    private fun signature(): String = if (isClip) {
        // The PLAN rather than the controls that produced it, because two arrangements of
        // splits and deletions can mean the same output and this question is only ever asked
        // about the output. Splitting inside a span that is already deleted is the concrete
        // case: it turns one cut into two, changes nothing about what is written, and on the
        // controls alone would raise "your changes will be lost" over an edit already saved.
        val live = source
        val plan = if (live == null) null
        else ClipPlan.build(live.durationUs, live.syncUs, live.framesUs, inUs, outUs, cuts)
        "clip|" + (plan?.segments.orEmpty()
            .joinToString(",") { it.sourceStartUs.toString() + ":" + it.sourceEndUs })
    } else {
        "image|" + orientation.rotation + "|" + orientation.mirror + "|" + crop
    }

    /**
     * Whether leaving now would lose something.
     *
     * Two conditions, and the second is the one that was missing. An edit has to exist, and it
     * has to differ from the one that was last written: without the latch, saving and then
     * pressing back raised "the changes you have made will be lost" over an edit that had just
     * been written to disk, which is a false sentence shown at the exact moment somebody did the
     * right thing. A dialog that cries wolf is one people learn to dismiss unread, and this one
     * is also shown when it is true.
     */
    private fun hasChanges(): Boolean {
        val edited = if (isClip) {
            val live = source
            live != null && (inUs != 0L || outUs != live.durationUs || cuts.isNotEmpty())
        } else {
            crop != null || !orientation.isIdentity
        }
        return edited && signature() != savedSignature
    }

    private fun leave() {
        if (busy) return
        if (hasChanges()) confirmLeave = true else finish()
    }

    /**
     * Back, through the dispatcher rather than by overriding `onBackPressed`.
     *
     * That is the house idiom (the mod browser's project page does the same) and it is the one
     * that is not deprecated. The callback stays enabled only while there is something to lose,
     * so back is the system's own the rest of the time.
     */
    private val unsavedBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (busy) return
            if (confirmLeave) confirmLeave = false else confirmLeave = true
        }
    }

    /**
     * Report a failure in the words the failure used.
     *
     * Both editors throw IOException with a sentence saying what is wrong, which is the whole
     * reason those sentences exist (§16.19). A generic "could not save" would throw away the one
     * thing that makes a remote failure diagnosable.
     */
    private fun fail(t: Throwable) {
        // Running out of memory on a very large picture is the one failure here that is ordinary
        // rather than exceptional, so it gets a sentence of its own in the user's language. The
        // rest carry the thrower's own words, which is the point of those words existing: a
        // generic "could not save" throws away the only thing that makes a failure diagnosable
        // (§16.19), and the Java halves stay free of Context so a harness can drive them.
        message = if (t.cause is OutOfMemoryError || t is OutOfMemoryError) {
            getString(R.string.image_too_large)
        } else {
            val reason = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            getString(R.string.editor_save_failed, reason)
        }
        toast(message!!)
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun scaleDown(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val height = (bitmap.height.toLong() * maxWidth / bitmap.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun releaseQuietly(retriever: MediaMetadataRetriever) {
        try {
            retriever.release()
        } catch (t: Throwable) {
            // Releasing twice, or releasing one that never opened, is not worth a crash.
        }
    }
}
