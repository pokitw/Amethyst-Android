#!/bin/sh
# Compile the shipped screenshot edit geometry and drive it over every pixel.
#
# Android's Bitmap.createBitmap(src, x, y, w, h, matrix, filter) crops in SOURCE coordinates and
# then applies the matrix. A player draws a crop box on the image as they can SEE it, already
# rotated. Something has to carry that rectangle back, and a mistake in it produces a picture that
# is mirrored, or turned the wrong way, or cropped somewhere else entirely, none of which is
# visible in the source code. That is the skin atlas lesson (CLAUDE.md section 14): a leg wearing
# a sleeve is invisible in a table and obvious in a render, so the geometry is stated once and
# checked against independently written ground truth.
#
# The checks worth knowing about:
#
#   mappingIsABijectionOverEveryPixel - every pixel of seven differently shaped images, at all
#       eight orientations, lands on a distinct in bounds displayed pixel. A transform that drops,
#       doubles or overflows a row cannot survive this, and no amount of reading would find it.
#
#   theConventionIsWhatTheCommentSays - ground truth written from the outside: under a 90 degree
#       clockwise turn the source's top left corner is the displayed top RIGHT corner. A formula
#       that is merely self consistent can still be the wrong formula, and only a statement of
#       where a corner actually goes can tell the difference.
#
#   mirrorOnScreenIsHorizontalAtEveryRotation - the one line somebody will simplify to !mirror.
#       The stored mirror is applied BEFORE the rotation, so flipping what the player sees has to
#       turn the rotation around as well at 90 and 270, or the flip comes out vertical. Asserted
#       by its effect on pixels rather than by its representation.
#
#   aCropComesBackToTheRightPixels - for every rectangle in every image at every orientation, the
#       plan's source rect is compared against the bounding box of the pixels actually under the
#       crop, computed independently by mapping every pixel forward.
#
# Mutations this is known to catch:
#
#   * any rotation case's formula is altered       (the bijection breaks, or ground truth fails)
#   * 90 and 270 are swapped                       (the image turns the wrong way)
#   * the mirror is applied after the rotation      (a vertical flip when the player asked for a
#                                                    horizontal one, at 90 and 270 only)
#   * mirroredOnScreen becomes a plain !mirror      (same, and the most likely edit in the file)
#   * displayedWidth/Height stop swapping on a      (every crop at 90 or 270 is clamped to the
#     quarter turn                                   wrong bounds)
#   * the crop maps right/bottom instead of          (the source rect is one pixel too large and
#     right-1/bottom-1                                reads past the edge of the bitmap)
#   * outputWidth/Height are taken from the source  (a 90 degree crop comes out transposed)
#   * the crop is not clamped to the displayed      (createBitmap throws on a drag that left the
#     bounds                                          image, which a finger does constantly)
#   * a reversed crop is not normalised             (dragging up and left selects nothing)
#   * an arbitrary angle is accepted                 (the transform stops being a permutation)
#
# What this CANNOT check: that Bitmap.createBitmap applies the matrix the way this assumes, that a
# large PNG decodes without running out of memory, or that the written file looks right. Those
# need a device. The geometry is the half that can be made certain.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

# ImageEdit has no Android imports, on purpose, so nothing has to be stubbed out.
javac -nowarn -source 8 -target 8 \
    -sourcepath "$SRC:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/screenshot/edit/ImageEdit.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes" net.kdt.pojavlaunch.screenshot.edit.Harness
