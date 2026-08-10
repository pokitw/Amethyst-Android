package net.kdt.pojavlaunch.ui.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import net.kdt.pojavlaunch.Tools
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Skins as files, in a folder the launcher owns.
 *
 * <b>This is the whole reason the feature was asked for.</b> The request was from someone who
 * could not reach their skin folder, which is not a complaint about file managers: everything
 * this launcher writes lives under `Android/data`, and from Android 11 that directory cannot be
 * browsed at all. A skin sitting there is a skin nobody can put anywhere. So the launcher keeps
 * them, lists them, and is the thing that applies them.
 *
 * Skins live beside the game rather than in internal storage so they survive alongside worlds and
 * are picked up by the same backup a player already makes of their game folder.
 */
private const val FOLDER = "amethyst_skins"

/** One skin on disk. The bitmap is loaded lazily by the gallery, not by the scan. */
@Immutable
class StoredSkin(
    val file: File,
    val name: String,
    val slim: Boolean,
    val modified: Long
)

fun skinFolder(): File = File(Tools.DIR_GAME_HOME, FOLDER)

/**
 * Every skin in the folder, newest first.
 *
 * Slimness is read from the pixels rather than remembered, because the file is the only thing
 * that survives a reinstall and a sidecar would be one more thing to keep in step. It costs one
 * decode of a 64 by 64 image per skin, which is nothing.
 */
fun listSkins(): List<StoredSkin> {
    val folder = skinFolder()
    val files = folder.listFiles() ?: return emptyList()
    return files
        .filter { it.isFile && it.name.lowercase(Locale.ROOT).endsWith(".png") }
        .mapNotNull { file ->
            val slim = runCatching { readSlim(file) }.getOrDefault(false)
            StoredSkin(file, file.nameWithoutExtension, slim, file.lastModified())
        }
        .sortedByDescending { it.modified }
}

private fun readSlim(file: File): Boolean {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
    return try {
        if (bitmap.width < 64 || bitmap.height < 64) false
        else guessSlim { x, y -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) }
    } finally {
        bitmap.recycle()
    }
}

/** Decode a stored skin for editing or preview. Mutable, because the editor paints into it. */
fun loadSkinBitmap(file: File): Bitmap? = runCatching {
    val options = BitmapFactory.Options().apply {
        inMutable = true
        // A skin is pixel art and must never be pre-scaled by density, which is what
        // decodeFile does by default and is why a skin loaded the obvious way comes out 192px.
        inScaled = false
    }
    BitmapFactory.decodeFile(file.absolutePath, options)
}.onFailure { Log.w("SkinStore", "Could not decode ${file.name}", it) }.getOrNull()

/**
 * Write a skin, returning the file.
 *
 * Always PNG and always at the texture's own size: a skin that has been through a JPEG encoder
 * has lost the hard alpha edge the second layer depends on, and one that has been scaled has
 * lost the format entirely.
 */
fun saveSkin(bitmap: Bitmap, name: String): File? = runCatching {
    val folder = skinFolder()
    if (!folder.isDirectory && !folder.mkdirs()) return null
    val file = File(folder, safeSkinName(name))
    FileOutputStream(file).use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    file
}.onFailure { Log.w("SkinStore", "Could not save $name", it) }.getOrNull()

fun deleteSkin(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

/**
 * Import a picture the player picked, checking it is actually a skin.
 *
 * <b>A picture is not a skin.</b> The format is 64 by 64 (or the legacy 64 by 32), and anything
 * else will read as garbage through the UV table rather than fail loudly, so the size is checked
 * here and the import refused with a reason. A photo scaled to 64 by 64 is technically importable
 * and will look like a photo wrapped round a person, which is the player's business.
 */
enum class ImportSkinResult { ADDED, NOT_AN_IMAGE, WRONG_SIZE, FAILED }

class SkinImport {
    var file: File? = null
        private set

    fun importFrom(context: Context, uri: Uri, name: String): ImportSkinResult {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) return ImportSkinResult.NOT_AN_IMAGE
                BitmapFactory.decodeStream(stream, null,
                    BitmapFactory.Options().apply { inScaled = false; inMutable = true })
            }
        }.getOrNull() ?: return ImportSkinResult.NOT_AN_IMAGE

        try {
            val legal = bitmap.width == 64 && (bitmap.height == 64 || bitmap.height == 32)
            if (!legal) return ImportSkinResult.WRONG_SIZE
            // A legacy 64 by 32 is grown to 64 by 64 on the way in rather than carried as a second
            // shape through the whole editor: the extra rows are exactly the left limbs, which
            // the model already knows how to mirror, and everything downstream then has one case.
            val stored = if (bitmap.height == 64) bitmap else expandLegacy(bitmap)
            val saved = saveSkin(stored, name) ?: return ImportSkinResult.FAILED
            file = saved
            return ImportSkinResult.ADDED
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * Grow a 64 by 32 skin into the modern layout.
 *
 * The old format has one arm and one leg and the game mirrors them; the new one stores all four.
 * So the bottom half is filled by copying the right limbs across, mirrored, into the rectangles
 * the left limbs occupy. [legacyMirrorOf] already says which rectangle maps to which, so the
 * layout is not restated here.
 */
private fun expandLegacy(legacy: Bitmap): Bitmap {
    val grown = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(grown)
    canvas.drawBitmap(legacy, 0f, 0f, null)
    val parts = skinParts(false)
    for (part in parts) {
        if (part.id != "leftArm" && part.id != "leftLeg") continue
        for (face in SkinFace.values()) {
            val target = part.faces[face] ?: continue
            val (source, mirror) = legacyMirrorOf(part, face, false) ?: continue
            for (x in 0 until target.width) {
                for (y in 0 until target.height) {
                    val sx = if (mirror) source.x + source.width - 1 - x else source.x + x
                    grown.setPixel(target.x + x, target.y + y, legacy.getPixel(sx, source.y + y))
                }
            }
        }
    }
    return grown
}

/** A file name from a player, reduced to something a filesystem will take. */
fun safeSkinName(name: String): String {
    var safe = name.trim().replace(Regex("[^A-Za-z0-9._ +-]"), "_")
    if (safe.endsWith(".png", ignoreCase = true)) safe = safe.dropLast(4)
    if (safe.isEmpty() || safe == "." || safe == "..") safe = "skin"
    if (safe.length > 48) safe = safe.substring(0, 48)
    return "$safe.png"
}

/** A blank skin to start from: fully transparent, which is what an empty canvas means here. */
fun blankSkin(): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
