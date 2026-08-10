package net.kdt.pojavlaunch.ui.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The player, drawn in software on a Compose canvas.
 *
 * <b>No GL, and that is not a compromise.</b> The game owns the only GL surface in this app
 * (handbook 12.4) and a launcher-side preview must never need one; a second context to spin a
 * cube would be the tail wagging the dog. What is actually needed is six textured boxes, and
 * the projection that makes them is thirty lines of arithmetic.
 *
 * <b>Orthographic on purpose.</b> Under an orthographic projection a rectangular face always
 * lands as a parallelogram, and a parallelogram is exactly what a 2x3 affine matrix can draw.
 * So each face is one {@code drawImage} through a matrix that maps the face's texture rectangle
 * onto its projected corners, with no per-pixel work in Kotlin at all. A perspective projection
 * would need real texture mapping and would look no better at this size; Minecraft's own
 * inventory preview is orthographic too.
 *
 * Faces are back-face culled by the sign of the projected basis, then painter-sorted by depth,
 * which is correct here because the parts are convex boxes that do not interpenetrate.
 */

/** One face ready to draw: where it lands on screen, what to sample, and how deep it is. */
private class Quad(
    val rect: SkinRect,
    val ox: Float, val oy: Float,
    val ux: Float, val uy: Float,
    val vx: Float, val vy: Float,
    val depth: Float,
    /** Flat shading, so the sides of a box are distinguishable from its front. */
    val shade: Float
)

/**
 * The corner and edge vectors of a face in model space.
 *
 * Written out per face rather than derived, because the atlas's idea of which way is "texture
 * right" differs per face and is not a rotation of one rule: unrolling a box gives right, front,
 * left, back, so the right face's texture runs back-to-front while the left face's runs
 * front-to-back. Getting one of these backwards mirrors a limb, which is the kind of bug that
 * looks like the skin is wrong rather than the viewer.
 */
private fun faceGeometry(
    face: SkinFace, hx: Float, hy: Float, hz: Float
): Triple<FloatArray, FloatArray, FloatArray> = when (face) {
    // origin (texture 0,0), u (texture +x), v (texture +y)
    SkinFace.FRONT -> Triple(
        floatArrayOf(-hx, hy, hz), floatArrayOf(2 * hx, 0f, 0f), floatArrayOf(0f, -2 * hy, 0f))
    SkinFace.BACK -> Triple(
        floatArrayOf(hx, hy, -hz), floatArrayOf(-2 * hx, 0f, 0f), floatArrayOf(0f, -2 * hy, 0f))
    SkinFace.RIGHT -> Triple(
        floatArrayOf(-hx, hy, -hz), floatArrayOf(0f, 0f, 2 * hz), floatArrayOf(0f, -2 * hy, 0f))
    SkinFace.LEFT -> Triple(
        floatArrayOf(hx, hy, hz), floatArrayOf(0f, 0f, -2 * hz), floatArrayOf(0f, -2 * hy, 0f))
    SkinFace.TOP -> Triple(
        floatArrayOf(-hx, hy, -hz), floatArrayOf(2 * hx, 0f, 0f), floatArrayOf(0f, 0f, 2 * hz))
    SkinFace.BOTTOM -> Triple(
        floatArrayOf(-hx, -hy, hz), floatArrayOf(2 * hx, 0f, 0f), floatArrayOf(0f, 0f, -2 * hz))
}

/** How much light a face gets, by which way it points. Minecraft shades its own model this way. */
private fun shadeOf(face: SkinFace): Float = when (face) {
    SkinFace.TOP -> 1f
    SkinFace.FRONT, SkinFace.BACK -> 0.86f
    SkinFace.LEFT, SkinFace.RIGHT -> 0.72f
    SkinFace.BOTTOM -> 0.6f
}

/**
 * A rotatable preview of a skin.
 *
 * @param skin      the 64x64 (or legacy 64x32) texture
 * @param slim      whether to use three-pixel arms
 * @param showOuter whether the second layer is drawn
 */
@Composable
fun SkinPreview(
    skin: ImageBitmap?,
    slim: Boolean,
    modifier: Modifier = Modifier,
    showOuter: Boolean = true,
    /** Start turned slightly, so the first frame reads as three dimensional rather than flat. */
    initialYaw: Float = 0.5f,
    initialPitch: Float = 0.08f
) {
    var yaw by remember { mutableFloatStateOf(initialYaw) }
    var pitch by remember { mutableFloatStateOf(initialPitch) }

    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures { change, drag ->
                change.consume()
                yaw += drag.x / 160f
                // Clamped rather than free: past a right angle the model is being looked at from
                // above and reads as a pile of boxes, which is not a preview of anything.
                pitch = (pitch - drag.y / 220f).coerceIn(-0.9f, 0.9f)
            }
        }
    ) {
        val image = skin ?: return@Canvas
        val legacy = image.height < 64
        val parts = skinParts(slim)

        // Fit the model, which is 32 units tall, into the canvas with a little air around it.
        val scale = min(size.width / 20f, size.height / 38f)
        val centreX = size.width / 2f
        val centreY = size.height / 2f + 16f * scale

        val cosYaw = cos(yaw); val sinYaw = sin(yaw)
        val cosPitch = cos(pitch); val sinPitch = sin(pitch)

        val quads = ArrayList<Quad>(72)

        fun project(x: Float, y: Float, z: Float): FloatArray {
            // Yaw about the vertical, then pitch about the horizontal. Orthographic, so the
            // third component is depth only and never divides anything.
            val rx = x * cosYaw + z * sinYaw
            val rz = -x * sinYaw + z * cosYaw
            val ry = y * cosPitch - rz * sinPitch
            val rz2 = y * sinPitch + rz * cosPitch
            return floatArrayOf(centreX + rx * scale, centreY - ry * scale, rz2)
        }

        fun collect(part: SkinPart, overlay: Boolean) {
            val rects = if (overlay) part.overlay else part.faces
            if (rects == null) return
            // The outer layer is drawn a touch larger so it sits on the skin rather than fighting
            // it for the same pixels, which is what the game does too.
            val grow = if (overlay) 0.55f else 0f
            val hx = part.sizeX / 2f + grow
            val hy = part.sizeY / 2f + grow
            val hz = part.sizeZ / 2f + grow
            for (face in SkinFace.values()) {
                var rect = rects[face] ?: continue
                var mirror = false
                if (legacy) {
                    val resolved = legacyMirrorOf(part, face, overlay) ?: continue
                    rect = resolved.first
                    mirror = resolved.second
                }
                val (origin, u, v) = faceGeometry(face, hx, hy, hz)
                val p0 = project(
                    part.originX + origin[0], part.originY + origin[1], part.originZ + origin[2])
                val p1 = project(
                    part.originX + origin[0] + u[0], part.originY + origin[1] + u[1],
                    part.originZ + origin[2] + u[2])
                val p2 = project(
                    part.originX + origin[0] + v[0], part.originY + origin[1] + v[1],
                    part.originZ + origin[2] + v[2])
                var ux = p1[0] - p0[0]; var uy = p1[1] - p0[1]
                val vx = p2[0] - p0[0]; val vy = p2[1] - p0[1]
                var ox = p0[0]; var oy = p0[1]
                // Back-face cull by the winding of the projected basis. A face turned away from
                // the viewer has the opposite sign, and drawing it would put the inside of the
                // head over the outside of it.
                if (ux * vy - uy * vx <= 0f) continue
                if (mirror) {
                    // A legacy left limb is its right one flipped, which is a flip of the source
                    // and is done by starting at the far corner and walking back.
                    ox += ux; oy += uy
                    ux = -ux; uy = -uy
                }
                val depth = project(part.originX, part.originY, part.originZ)[2]
                quads.add(Quad(rect, ox, oy, ux, uy, vx, vy, depth, shadeOf(face)))
            }
        }

        for (part in parts) collect(part, false)
        if (showOuter) for (part in parts) collect(part, true)

        // Painter's algorithm. The parts are convex and do not interpenetrate, so sorting whole
        // faces by the depth of the box they belong to is enough and needs no z buffer.
        quads.sortBy { it.depth }

        for (quad in quads) drawQuad(image, quad)
    }
}

/**
 * Draw one face as a textured parallelogram.
 *
 * The matrix maps texture-pixel space onto the projected corners, so the whole face is one
 * `drawImage` and Compose does the sampling. [FilterQuality.None] because a skin is pixel art
 * and any smoothing turns a crisp face into a smear.
 */
/**
 * Draw one face as a textured parallelogram.
 *
 * <b>android.graphics.Matrix, deliberately not Compose's.</b> The first version of this used
 * Compose's Matrix and wrote the translation into cells [0,3] and [1,3], which is the convention
 * an OpenGL-shaped 4x4 usually follows. Compose's is the other one: its own map() computes
 * newX = m[0,0]*x + m[1,0]*y + m[3,0], so the translation belongs at [3,0] and [3,1], and the
 * two cells I used are the perspective column. A few hundred pixels in the perspective divisor
 * collapses every face to nothing, which is exactly what it did: the whole model vanished while
 * the flat face thumbnails, which use no matrix, kept working.
 *
 * The platform matrix is used instead of correcting the indices because its element order is
 * fixed by named constants (MSCALE_X, MSKEW_X, MTRANS_X, ...) rather than by a convention that
 * has to be remembered correctly. A layout that can be read off the names cannot be got subtly
 * backwards a second time.
 *
 * The paint carries the face shading and turns filtering off, because a skin is pixel art and
 * any smoothing turns a crisp face into a smear.
 */
private fun DrawScope.drawQuad(image: ImageBitmap, quad: Quad) {
    val w = quad.rect.width.toFloat()
    val h = quad.rect.height.toFloat()
    if (w <= 0f || h <= 0f) return

    // x' = MSCALE_X * x + MSKEW_X * y + MTRANS_X
    // y' = MSKEW_Y  * x + MSCALE_Y * y + MTRANS_Y
    // with x, y in the face's own texture pixels, so the face's rectangle maps onto the
    // parallelogram its projected corners describe.
    val matrix = android.graphics.Matrix()
    matrix.setValues(
        floatArrayOf(
            quad.ux / w, quad.vx / h, quad.ox,
            quad.uy / w, quad.vy / h, quad.oy,
            0f, 0f, 1f
        )
    )

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val saved = native.save()
        native.concat(matrix)
        native.drawBitmap(
            image.asAndroidBitmap(),
            android.graphics.Rect(
                quad.rect.x, quad.rect.y, quad.rect.right, quad.rect.bottom
            ),
            android.graphics.RectF(0f, 0f, w, h),
            shadePaint(quad.shade)
        )
        native.restoreToCount(saved)
    }
}

/**
 * A paint per shading level, built once.
 *
 * Six levels exist and a frame draws up to seventy faces, so building one per face would be
 * seventy allocations a frame for six distinct objects.
 */
private val shadePaints = HashMap<Int, android.graphics.Paint>()

private fun shadePaint(shade: Float): android.graphics.Paint {
    val key = (shade * 100).toInt()
    return shadePaints.getOrPut(key) {
        android.graphics.Paint().apply {
            isAntiAlias = false
            // Nearest neighbour: the whole point is that a texture pixel stays a square.
            isFilterBitmap = false
            isDither = false
            if (shade < 0.999f) {
                colorFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix(
                        floatArrayOf(
                            shade, 0f, 0f, 0f, 0f,
                            0f, shade, 0f, 0f, 0f,
                            0f, 0f, shade, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
        }
    }
}
