package net.kdt.pojavlaunch.ui.skin

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb

/** What a tap on the canvas does. */
enum class SkinTool { PENCIL, ERASER, FILL, PICKER }

/**
 * One undoable change: the pixels that were different, and what they were.
 *
 * <b>A diff rather than a snapshot.</b> A 64 by 64 skin is sixteen kilobytes, so twenty snapshots
 * would be a third of a megabyte of bitmaps held live for a feature nobody thinks about. A stroke
 * touches tens of pixels and a fill a few hundred, so the diff is almost always smaller than the
 * image and never larger in the cases that matter.
 *
 * Both the before and after colours are kept, which is what makes redo free.
 */
private class PixelDiff {
    val xs = ArrayList<Int>()
    val ys = ArrayList<Int>()
    val before = ArrayList<Int>()
    val after = ArrayList<Int>()

    val size: Int get() = xs.size

    fun add(x: Int, y: Int, was: Int, now: Int) {
        xs.add(x); ys.add(y); before.add(was); after.add(now)
    }

    fun applyBefore(bitmap: Bitmap) {
        for (i in 0 until size) bitmap.setPixel(xs[i], ys[i], before[i])
    }

    fun applyAfter(bitmap: Bitmap) {
        for (i in 0 until size) bitmap.setPixel(xs[i], ys[i], after[i])
    }
}

/**
 * The editor's mutable world: the texture being painted, the tool, and the history.
 *
 * <b>The bitmap is the single copy and it is mutated in place.</b> Compose cannot see inside a
 * Bitmap, so every change bumps [revision], and every composable that draws the skin reads that
 * counter. It is the smallest honest way to hold a mutable canvas in a declarative tree: the
 * alternative, a new ImageBitmap per stroke, allocates sixteen kilobytes per painted pixel.
 *
 * Undo is capped at [MAX_HISTORY] steps. Unlimited undo on a canvas nobody saves is a memory leak
 * with a nice name.
 */
class SkinEditorState(bitmap: Bitmap, slim: Boolean, val name: String) {

    companion object {
        const val MAX_HISTORY = 60
        const val SIZE = 64
    }

    var bitmap: Bitmap = bitmap
        private set

    /** Bumped on every pixel change, so composables that draw the texture recompose. */
    var revision by mutableIntStateOf(0)
        private set

    var slim by mutableStateOf(slim)
        private set

    var tool by mutableStateOf(SkinTool.PENCIL)
        private set

    var colour by mutableStateOf(Color(0xFF8A5A3B))
        private set

    /** Which part and face is on the canvas. Painting one face at a time is the whole design. */
    var partIndex by mutableIntStateOf(0)
        private set
    var face by mutableStateOf(SkinFace.FRONT)
        private set
    /** Whether the canvas is showing the outer layer of the current part. */
    var editingOverlay by mutableStateOf(false)
        private set

    var dirty by mutableStateOf(false)
        private set

    private val undoStack = ArrayList<PixelDiff>()
    private val redoStack = ArrayList<PixelDiff>()
    private var stroke: PixelDiff? = null

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    val parts: List<SkinPart> get() = skinParts(slim)
    val part: SkinPart get() = parts[partIndex.coerceIn(0, parts.size - 1)]

    /** The atlas rectangle currently under the brush, or null when this part has no outer layer. */
    val rect: SkinRect?
        get() {
            val target = if (editingOverlay) part.overlay else part.faces
            return target?.get(face)
        }

    fun image(): ImageBitmap = bitmap.asImageBitmap()

    /* ------------------------------------------------------------------ selection */

    fun selectPart(index: Int) {
        partIndex = index.coerceIn(0, parts.size - 1)
        if (editingOverlay && part.overlay == null) editingOverlay = false
    }

    fun selectFace(next: SkinFace) {
        face = next
    }

    fun applyOverlay(value: Boolean) {
        editingOverlay = value && part.overlay != null
    }

    fun applyTool(next: SkinTool) {
        tool = next
    }

    fun applyColour(next: Color) {
        colour = next
    }

    /**
     * Switch the model between classic and slim arms.
     *
     * The pixels are left exactly as they are. A slim skin simply stops using the last two
     * columns of each arm strip, so nothing has to be moved and nothing is lost by switching
     * back. Mojang stores this on the profile rather than in the file, which is why it is a
     * property of the editor and gets sent with the upload.
     */
    fun applySlim(value: Boolean) {
        slim = value
        dirty = true
    }

    /* ------------------------------------------------------------------ painting */

    /** Begin a stroke, so a drag from one pixel to the next is one undo step and not thirty. */
    fun beginStroke() {
        stroke = PixelDiff()
    }

    fun endStroke() {
        val current = stroke ?: return
        stroke = null
        if (current.size == 0) return
        undoStack.add(current)
        // The oldest step falls off the end rather than the newest being refused: someone who
        // has painted for an hour wants the last twenty back, not the first twenty.
        if (undoStack.size > MAX_HISTORY) undoStack.removeAt(0)
        redoStack.clear()
        refreshHistory()
        dirty = true
    }

    /**
     * Paint one texture pixel, in atlas coordinates.
     *
     * Silently ignores anything outside the texture, because a finger dragged off the edge of the
     * canvas is a finger dragged off the edge of the canvas and not an error.
     */
    fun paint(x: Int, y: Int) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return
        val target = when (tool) {
            SkinTool.PENCIL -> colour.toArgb()
            SkinTool.ERASER -> 0
            else -> return
        }
        setPixel(x, y, target)
    }

    /** Read a pixel, which is what the eyedropper does and what the fill compares against. */
    fun pixelAt(x: Int, y: Int): Int =
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) 0 else bitmap.getPixel(x, y)

    fun pick(x: Int, y: Int) {
        val argb = pixelAt(x, y)
        // A fully transparent pixel carries no colour worth taking; picking it would silently set
        // the brush to invisible black and the next stroke would look like it did nothing.
        if (android.graphics.Color.alpha(argb) == 0) return
        colour = Color(argb)
        tool = SkinTool.PENCIL
    }

    /**
     * Flood fill, bounded to the face being edited.
     *
     * <b>Bounded on purpose.</b> The atlas is one image, so an unbounded fill of a transparent
     * area runs straight out of the head's front face, across every other face of every other
     * part, and repaints the whole skin. Nobody has ever wanted that. The bucket fills the
     * rectangle on screen, which is the thing being looked at.
     */
    fun fill(x: Int, y: Int) {
        val bounds = rect ?: return
        if (x < bounds.x || y < bounds.y || x >= bounds.right || y >= bounds.bottom) return
        val from = bitmap.getPixel(x, y)
        val to = colour.toArgb()
        if (from == to) return

        beginStroke()
        // An explicit queue rather than recursion: a face fill is up to a few thousand deep and
        // the obvious recursive version overflows on a real device before it finishes.
        //
        // Neighbours are bounds-checked BEFORE they are packed, not after. Packing a coordinate
        // as (x shl 8 or y) is only reversible while both are in range: y = -1 makes `or` set
        // every bit of the word, and the pair comes back out as something else entirely.
        val queue = ArrayDeque<Int>()
        val seen = HashSet<Int>()
        fun push(px: Int, py: Int) {
            if (px < bounds.x || py < bounds.y || px >= bounds.right || py >= bounds.bottom) return
            queue.addLast(px shl 8 or py)
        }
        push(x, y)
        while (queue.isNotEmpty()) {
            val key = queue.removeFirst()
            if (!seen.add(key)) continue
            val px = key shr 8
            val py = key and 0xFF
            if (bitmap.getPixel(px, py) != from) continue
            setPixel(px, py, to)
            push(px + 1, py)
            push(px - 1, py)
            push(px, py + 1)
            push(px, py - 1)
        }
        endStroke()
    }

    /**
     * Mirror the current face left to right.
     *
     * The one bulk operation worth having: faces are symmetrical far more often than not, so
     * painting half of one and mirroring it is how most faces actually get drawn.
     */
    fun mirrorFace() {
        val bounds = rect ?: return
        beginStroke()
        for (y in bounds.y until bounds.bottom) {
            for (x in 0 until bounds.width / 2) {
                val left = bounds.x + x
                val right = bounds.right - 1 - x
                val a = bitmap.getPixel(left, y)
                val b = bitmap.getPixel(right, y)
                setPixel(left, y, b)
                setPixel(right, y, a)
            }
        }
        endStroke()
    }

    /** Clear the current face to transparent. */
    fun clearFace() {
        val bounds = rect ?: return
        beginStroke()
        for (y in bounds.y until bounds.bottom) {
            for (x in bounds.x until bounds.right) setPixel(x, y, 0)
        }
        endStroke()
    }

    private fun setPixel(x: Int, y: Int, argb: Int) {
        val was = bitmap.getPixel(x, y)
        if (was == argb) return
        bitmap.setPixel(x, y, argb)
        stroke?.add(x, y, was, argb)
        revision++
    }

    /* ------------------------------------------------------------------ history */

    fun undo() {
        if (undoStack.isEmpty()) return
        val diff = undoStack.removeAt(undoStack.size - 1)
        diff.applyBefore(bitmap)
        redoStack.add(diff)
        revision++
        refreshHistory()
        dirty = true
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val diff = redoStack.removeAt(redoStack.size - 1)
        diff.applyAfter(bitmap)
        undoStack.add(diff)
        revision++
        refreshHistory()
        dirty = true
    }

    private fun refreshHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun markSaved() {
        dirty = false
    }
}
