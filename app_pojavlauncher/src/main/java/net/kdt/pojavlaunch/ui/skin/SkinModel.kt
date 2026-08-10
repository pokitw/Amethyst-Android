package net.kdt.pojavlaunch.ui.skin

/**
 * The Minecraft skin atlas, stated once.
 *
 * <b>Everything in the skin editor reads this table and nothing else works the layout out for
 * itself.</b> A skin is a 64 by 64 texture in which every face of every body part is a fixed
 * rectangle, and those rectangles are not derivable: they are a layout Mojang chose, with the
 * arms and legs moved in 1.8, a whole second set of rectangles for the outer layer, and two
 * different arm widths. Encoded twice, the two copies drift and a leg ends up wearing a sleeve.
 *
 * <b>Two things about the layout that surprise people, and that the numbers here already
 * account for.</b> The top and bottom faces of every box are stored side by side ABOVE the
 * side faces, not with them. And the four side faces are laid out right, front, left, back in
 * that order, so "front" is not the first rectangle.
 *
 * The legacy 64 by 32 skin is read by inference rather than by a second table: it is the top
 * half of this one, it has no outer layer except the hat, and its left limbs are the right ones
 * mirrored. [legacyMirrorOf] is the whole of that difference.
 */

/** A rectangle in the atlas, in texture pixels. */
data class SkinRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
}

/** Which side of a box a rectangle is. The order is the order Minecraft stores them in. */
enum class SkinFace { RIGHT, FRONT, LEFT, BACK, TOP, BOTTOM }

/** One box of the model: its six faces, its size in model units, and where it sits on the body. */
data class SkinPart(
    val id: String,
    /** What a player calls it, as a string resource. */
    val labelRes: Int,
    /** Box size in model units: width (x), height (y), depth (z). */
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    /** Centre of the box in model space, y up, origin between the feet. */
    val originX: Float,
    val originY: Float,
    val originZ: Float,
    /** The six faces of the base layer. */
    val faces: Map<SkinFace, SkinRect>,
    /** The six faces of the outer layer, absent on parts that never had one. */
    val overlay: Map<SkinFace, SkinRect>?,
    /** True for the two arms, which are the parts that change width on a slim skin. */
    val isArm: Boolean = false
)

/**
 * Build the six face rectangles of a box laid out the way Minecraft lays them out.
 *
 * The atlas stores a box as a cross: the top and bottom faces sit above the row of side faces,
 * offset by the box depth, and the sides run right, front, left, back with widths that alternate
 * between the box depth and the box width. Every part in the table below is built through here,
 * so a part cannot be laid out differently by accident.
 *
 * @param u,v    the top-left of the whole cross in the atlas
 * @param w,h,d  the box width, height and depth in texture pixels
 */
private fun box(u: Int, v: Int, w: Int, h: Int, d: Int): Map<SkinFace, SkinRect> = mapOf(
    SkinFace.TOP to SkinRect(u + d, v, w, d),
    SkinFace.BOTTOM to SkinRect(u + d + w, v, w, d),
    SkinFace.RIGHT to SkinRect(u, v + d, d, h),
    SkinFace.FRONT to SkinRect(u + d, v + d, w, h),
    SkinFace.LEFT to SkinRect(u + d + w, v + d, d, h),
    SkinFace.BACK to SkinRect(u + d + w + d, v + d, w, h)
)

/** The full 64 by 64 model. Arm width is 4 on a classic skin and 3 on a slim one. */
fun skinParts(slim: Boolean): List<SkinPart> {
    val arm = if (slim) 3 else 4
    // A slim arm is a pixel narrower but hangs in the same place, so it sits half a pixel further
    // out from the body than a classic one. Getting this wrong is the classic "floating arm".
    val armOffset = if (slim) 5.5f else 6f
    return listOf(
        SkinPart(
            id = "head", labelRes = net.kdt.pojavlaunch.R.string.skin_part_head,
            sizeX = 8, sizeY = 8, sizeZ = 8,
            originX = 0f, originY = 28f, originZ = 0f,
            faces = box(0, 0, 8, 8, 8),
            overlay = box(32, 0, 8, 8, 8)
        ),
        SkinPart(
            id = "body", labelRes = net.kdt.pojavlaunch.R.string.skin_part_body,
            sizeX = 8, sizeY = 12, sizeZ = 4,
            originX = 0f, originY = 18f, originZ = 0f,
            faces = box(16, 16, 8, 12, 4),
            overlay = box(16, 32, 8, 12, 4)
        ),
        SkinPart(
            id = "rightArm", labelRes = net.kdt.pojavlaunch.R.string.skin_part_right_arm,
            sizeX = arm, sizeY = 12, sizeZ = 4,
            originX = -armOffset, originY = 18f, originZ = 0f,
            faces = box(40, 16, arm, 12, 4),
            overlay = box(40, 32, arm, 12, 4),
            isArm = true
        ),
        SkinPart(
            id = "leftArm", labelRes = net.kdt.pojavlaunch.R.string.skin_part_left_arm,
            sizeX = arm, sizeY = 12, sizeZ = 4,
            originX = armOffset, originY = 18f, originZ = 0f,
            faces = box(32, 48, arm, 12, 4),
            overlay = box(48, 48, arm, 12, 4),
            isArm = true
        ),
        SkinPart(
            id = "rightLeg", labelRes = net.kdt.pojavlaunch.R.string.skin_part_right_leg,
            sizeX = 4, sizeY = 12, sizeZ = 4,
            originX = -2f, originY = 6f, originZ = 0f,
            faces = box(0, 16, 4, 12, 4),
            overlay = box(0, 32, 4, 12, 4)
        ),
        SkinPart(
            id = "leftLeg", labelRes = net.kdt.pojavlaunch.R.string.skin_part_left_leg,
            sizeX = 4, sizeY = 12, sizeZ = 4,
            originX = 2f, originY = 6f, originZ = 0f,
            faces = box(16, 48, 4, 12, 4),
            overlay = box(0, 48, 4, 12, 4)
        )
    )
}

/**
 * Where a legacy 64 by 32 skin keeps a rectangle that the modern layout puts in the bottom half.
 *
 * A 1.7 skin has one arm and one leg, and the game mirrors them onto the other side. So a read
 * of the left arm has to come from the right arm's rectangle, flipped. Everything in the top
 * half of the atlas is already in the right place, and the only outer layer that existed was the
 * hat, which is why every other overlay reads as empty.
 *
 * @return the rectangle to sample instead, and whether to mirror it, or null when the part does
 *         not exist on a legacy skin at all
 */
fun legacyMirrorOf(part: SkinPart, face: SkinFace, overlay: Boolean): Pair<SkinRect, Boolean>? {
    if (overlay) {
        // The hat is the one overlay a 64x32 skin has.
        return if (part.id == "head") skinParts(false)[0].overlay!![face]?.let { it to false }
        else null
    }
    val classic = skinParts(false)
    return when (part.id) {
        "leftArm" -> classic[2].faces[mirrorFace(face)]?.let { it to true }
        "leftLeg" -> classic[4].faces[mirrorFace(face)]?.let { it to true }
        else -> part.faces[face]?.let { it to false }
    }
}

/** Mirroring a box swaps its left and right faces; the other four are their own mirror. */
private fun mirrorFace(face: SkinFace): SkinFace = when (face) {
    SkinFace.LEFT -> SkinFace.RIGHT
    SkinFace.RIGHT -> SkinFace.LEFT
    else -> face
}

/**
 * Whether a 64 by 64 skin is slim, guessed the way every skin tool guesses it.
 *
 * <b>There is no flag in the file.</b> Slim is a property of the Mojang profile, not of the PNG,
 * so a skin loaded from disk has to be read for it. The only evidence is width: a classic arm
 * cross is sixteen texture pixels wide and a slim one is fourteen, so the last two columns of
 * the right arm's strip are painted on a classic skin and left transparent on a slim one.
 *
 * Note it is the trailing columns and not the front face's fourth column, which is the obvious
 * guess and the wrong one: a slim layout SHIFTS every face left rather than leaving a hole in
 * the middle, so the fourth column of a slim arm is the first column of its left face and is
 * just as opaque as a classic skin's is.
 *
 * @param alphaAt reads the alpha of a texture pixel, 0 to 255
 */
fun guessSlim(alphaAt: (Int, Int) -> Int): Boolean {
    // x 54 and 55 over the arm's twelve rows: the strip a classic arm needs and a slim one does
    // not. Counted rather than sampled once, because a single stray pixel should not decide it.
    var opaque = 0
    for (x in 54..55) {
        for (y in 20 until 32) {
            if (alphaAt(x, y) > 8) opaque++
        }
    }
    return opaque < 4
}
