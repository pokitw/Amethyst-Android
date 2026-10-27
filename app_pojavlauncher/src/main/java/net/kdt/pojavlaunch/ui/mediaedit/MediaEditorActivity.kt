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

    // Clip
    private var source by mutableStateOf<ClipEditor.Source?>(null)
    private var inUs by mutableStateOf(0L)
    private var outUs by mutableStateOf(0L)
    private var playUs by mutableStateOf(0L)
    private val cuts = mutableStateListOf<ClipPlan.Range>()
    private val thumbs = mutableStateListOf<Thumb>()
    private var previewFrame by mutableStateOf<Bitmap?>(null)
    private var previewJob: Job? = null

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

    /**
     * Enable the back callback exactly while leaving would lose something.
     *
     * Called from every place that changes the edit. Leaving it enabled when there is nothing to
     * lose traps Back on this screen, which is the half of this pattern that is easy to omit.
     */
    private fun syncBackCallback() {
        unsavedBack.isEnabled = hasChanges() && !busy
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
        // the cut list has to be part of the key by VALUE: it is a snapshot list, so mutating
        // it leaves its identity unchanged and a key holding the list itself never changes.
        val cutKey = cuts.joinToString(",") { it.startUs.toString() + ":" + it.endUs }
        val plan = remember(live, inUs, outUs, cutKey) {
            if (live == null) {
                ClipPlan.trim(0L, LongArray(0), LongArray(0), 0L, 0L)
            } else {
                ClipPlan.build(live.durationUs, live.syncUs, live.framesUs, inUs, outUs, cuts)
            }
        }
        ClipEditScreen(
            title = stringResource(R.string.editor_title_clip),
            durationUs = live?.durationUs ?: 0L,
            syncUs = live?.syncUs ?: LongArray(0),
            thumbs = thumbs,
            previewFrame = previewFrame,
            aspect = aspectOf(live),
            inUs = inUs,
            outUs = outUs,
            playUs = playUs,
            cuts = cuts,
            plan = plan,
            busy = busy,
            progress = progress,
            message = message,
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
                if (playUs < inUs) playUs = inUs
            },
            onOut = { at ->
                val total = (live?.durationUs ?: 0L).coerceAtLeast(0L)
                val floor = (inUs + 1L).coerceAtMost(total)
                outUs = if (at < floor) floor else if (at > total) total else at
                if (playUs > outUs) playUs = outUs
            },
            onPlayhead = { at ->
                playUs = at.coerceIn(0L, live?.durationUs ?: 0L)
                schedulePreview(playUs)
            },
            onAddCut = ::addCut,
            onRemoveCut = { index -> if (index in cuts.indices) cuts.removeAt(index) },
            onReset = {
                val total = live?.durationUs ?: 0L
                inUs = 0L
                outUs = total
                playUs = 0L
                cuts.clear()
                message = null
                schedulePreview(0L)
            },
            onSave = ::saveClip,
            onBack = ::leave,
        )
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
            schedulePreview(0L)
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

    /**
     * Fetch the frame under the position marker, a moment after it stops moving.
     *
     * Debounced because a drag produces a value per frame and each fetch opens a decoder. The
     * previous job is cancelled rather than queued, so a long drag costs one decode.
     */
    private fun schedulePreview(atUs: Long) {
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            delay(90L)
            val frame = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (t: Throwable) {
                    null
                } finally {
                    releaseQuietly(retriever)
                }
            }
            if (frame != null) previewFrame = frame
        }
    }

    /**
     * Add a cut around the position marker.
     *
     * Two seconds by default, because that is the interval between the points a clip can resume
     * at: a shorter one would usually be rounded up to this anyway, and offering a length that
     * cannot be honoured is worse than offering the one that can. It is clamped inside the
     * selection and stops at the next cut.
     */
    private fun addCut() {
        if (source == null) return
        if (!canCutAt(playUs, inUs, outUs, cuts)) return
        val wanted = 2_000_000L
        var end = (playUs + wanted).coerceAtMost(outUs)
        cuts.forEach { existing ->
            if (existing.startUs in (playUs + 1)..end) end = existing.startUs
        }
        if (end <= playUs) return
        cuts.add(ClipPlan.Range(playUs, end))
        cuts.sortBy { it.startUs }
        message = null
        // The marker deliberately stays where it was: the next thing somebody usually does is
        // widen the cut they just made, and moving it would take the handle out from under the
        // thumb that is already there.
    }

    private fun saveClip() {
        val live = source ?: return
        val plan = ClipPlan.build(live.durationUs, live.syncUs, live.framesUs, inUs, outUs, cuts)
        if (!plan.isUsable()) return
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
        if (!plan.isUsable) return
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
                    if (toGallery) {
                        // publishImage is synchronous and small; a screenshot is megabytes, not
                        // gigabytes, so it does not need the recorder's deferred path.
                        withContext(Dispatchers.IO) {
                            GalleryExport.publishImage(this@MediaEditorActivity, out)
                        }
                    }
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

    private fun hasChanges(): Boolean = if (isClip) {
        val live = source
        live != null && (inUs != 0L || outUs != live.durationUs || cuts.isNotEmpty())
    } else {
        crop != null || !orientation.isIdentity
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
