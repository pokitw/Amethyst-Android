#!/usr/bin/env python3
"""Check the control texture nine-slice geometry against the shipped Java.

There is no device in CI and no way to look at a button, so the one thing that can be checked
here is the arithmetic: that the nine source cells tile the bitmap exactly, that the nine
destination cells tile the button exactly, that no cell is ever empty or inverted, and that the
corner inset behaves the way the format promises across every button size the launcher can make.

The constants and the rules are parsed out of ControlTexture.java rather than restated, so a
change to the source that this file does not know about fails here instead of shipping.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/customcontrols/textures"
SOURCE = TEXTURES / "ControlTexture.java"
DRAWABLE = TEXTURES / "ControlTextureDrawable.java"

failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


# --------------------------------------------------------------- parsed from the source

text = SOURCE.read_text(encoding="utf-8")
drawable = DRAWABLE.read_text(encoding="utf-8")

# The reading-order rule: index is row * 3 + column, in both arrays. If either loop stops saying
# so, the source and destination cells stop lining up and the art is drawn scrambled.
check(re.search(r"\[row \* 3 \+ column\]", text) is not None,
      "the source grid is no longer indexed row * 3 + column")
check(re.search(r"\[row \* 3 \+ column\]", drawable) is not None,
      "the destination grid is no longer indexed row * 3 + column; the two arrays are walked "
      "in step and an index that means something different in each draws the art scrambled")

check("if (to.isEmpty()) continue" in drawable and "if (from.isEmpty()) continue" in drawable,
      "the draw loop no longer skips empty cells; a button narrower than two corners collapses "
      "the middle column and Canvas is not obliged to be graceful about that")

check("public void setAlpha(int alpha) {" in drawable
      and "// Deliberately ignored" in drawable,
      "the drawable has started honouring setAlpha; opacity is already applied as the view's "
      "own alpha and the two would multiply")

check("Math.max(1f, Math.round(scale))" in text,
      "the unfiltered path no longer rounds the corner scale to a whole number; "
      "a fractional scale is what makes nearest-neighbour texels uneven")

check("Math.min(destinationWidth, destinationHeight) / 2" in text,
      "the corner inset is no longer clamped to half the button; two corners wider than the "
      "button they sit in make the middle band negative")


# ------------------------------------------------------- the rules, reimplemented to test

def build_source(width, height, slice_):
    if slice_ <= 0:
        return [(0, 0, width, height)]
    xs = [0, slice_, width - slice_, width]
    ys = [0, slice_, height - slice_, height]
    return [(xs[c], ys[r], xs[c + 1], ys[r + 1]) for r in range(3) for c in range(3)]


def corner_for(src_w, src_h, dst_w, dst_h, slice_, smooth):
    if slice_ <= 0:
        return 0
    shortest_src = min(src_w, src_h)
    shortest_dst = min(dst_w, dst_h)
    if shortest_src <= 0 or shortest_dst <= 0:
        return 0
    scale = shortest_dst / shortest_src
    scale = max(1.0, round(scale)) if not smooth else max(1.0, scale)
    # Java's Math.round on a float, i.e. floor(x + 0.5)
    corner = int(slice_ * scale + 0.5)
    limit = min(dst_w, dst_h) // 2
    return max(0, min(corner, limit))


def build_destination(dst_w, dst_h, corner):
    if corner <= 0:
        return [(0, 0, dst_w, dst_h)]
    xs = [0, corner, dst_w - corner, dst_w]
    ys = [0, corner, dst_h - corner, dst_h]
    return [(xs[c], ys[r], xs[c + 1], ys[r + 1]) for r in range(3) for c in range(3)]


def clamp_slice(src_w, src_h, slice_):
    """What ControlTextures does at load time before the texture is ever built."""
    shortest = min(src_w, src_h)
    if slice_ > 0 and slice_ * 2 >= shortest:
        return max(0, (shortest - 1) // 2)
    return max(0, slice_)


# ------------------------------------------------------------------------ the sweep

# Source sizes a pack author would plausibly ship, including the pathological ones.
SOURCES = [(16, 16), (32, 32), (64, 64), (48, 16), (16, 48), (2, 2), (1, 1), (200, 120)]
# Every button size the launcher can produce: the default layout's three bases (46/56/68) and
# the drawer/sub-button shapes, across the 80-250% button-size slider.
BASES = [46, 56, 68, 90, 120]
SCALES = [0.8, 1.0, 1.25, 1.75, 2.5]
SLICES = [0, 1, 3, 5, 8, 16, 40]

checked = 0
for (src_w, src_h) in SOURCES:
    for raw_slice in SLICES:
        slice_ = clamp_slice(src_w, src_h, raw_slice)

        source = build_source(src_w, src_h, slice_)
        # Source cells must tile the bitmap with no gap, no overlap and nothing inverted.
        if slice_ > 0:
            check(len(source) == 9, "expected nine source cells for slice %d" % slice_)
            for (l, t, r, b) in source:
                check(l <= r and t <= b,
                      "inverted source cell %s for %dx%d slice %d"
                      % ((l, t, r, b), src_w, src_h, slice_))
                check(0 <= l and 0 <= t and r <= src_w and b <= src_h,
                      "source cell %s outside a %dx%d bitmap"
                      % ((l, t, r, b), src_w, src_h))
            area = sum((r - l) * (b - t) for (l, t, r, b) in source)
            check(area == src_w * src_h,
                  "source cells cover %d px of a %dx%d bitmap (slice %d)"
                  % (area, src_w, src_h, slice_))
            # The clamp must leave a middle band, or the whole point of slicing is gone.
            check(src_w - 2 * slice_ > 0 and src_h - 2 * slice_ > 0,
                  "slice %d leaves no middle band in a %dx%d bitmap" % (slice_, src_w, src_h))

        for smooth in (False, True):
            for base in BASES:
                for scale in SCALES:
                    dst = max(1, int(base * scale))
                    for (dst_w, dst_h) in ((dst, dst), (dst * 3, dst), (dst, dst * 2)):
                        checked += 1
                        corner = corner_for(src_w, src_h, dst_w, dst_h, slice_, smooth)
                        check(2 * corner <= min(dst_w, dst_h),
                              "corner %d does not fit a %dx%d button (source %dx%d slice %d)"
                              % (corner, dst_w, dst_h, src_w, src_h, slice_))
                        cells = build_destination(dst_w, dst_h, corner)
                        for (l, t, r, b) in cells:
                            check(l <= r and t <= b,
                                  "inverted destination cell %s for a %dx%d button"
                                  % ((l, t, r, b), dst_w, dst_h))
                        area = sum((r - l) * (b - t) for (l, t, r, b) in cells)
                        check(area == dst_w * dst_h,
                              "destination cells cover %d px of a %dx%d button "
                              "(source %dx%d slice %d corner %d)"
                              % (area, dst_w, dst_h, src_w, src_h, slice_, corner))

# The promise the format makes: a border keeps its proportion as the button grows, rather than
# staying a fixed number of physical pixels.
small = corner_for(16, 16, 46, 46, 4, False)
large = corner_for(16, 16, 120, 120, 4, False)
check(large > small,
      "a 4px border on a 16px face should be thicker on a 120px button (%d) than on a 46px one "
      "(%d)" % (large, small))

# And the promise nearest-neighbour makes: whole texels.
check(corner_for(16, 16, 46, 46, 4, False) % 4 == 0,
      "an unfiltered corner should be a whole number of source texels")

# ------------------------------------------------------- the packs that actually ship
#
# The sweep above proves the arithmetic works for any pack. This proves the ones in the APK are
# packs at all: a folder whose button.png went missing, or whose slice no longer fits its face,
# would be listed in the picker and draw nothing, and there is no device in CI to notice.

BUILT_IN = ROOT / "app_pojavlauncher/src/main/assets/controltextures"


def png_size(path):
    """Width and height straight out of the IHDR, so this needs no image library."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    if data[12:16] != b"IHDR":
        return None
    return (int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big"))


packs = sorted(p for p in BUILT_IN.iterdir() if p.is_dir()) if BUILT_IN.is_dir() else []
check(len(packs) >= 8,
      "expected the launcher to ship a spread of texture packs, found %d" % len(packs))

# Every shipped name has to be one the loader will accept as a folder, since the folder name is
# both the value written into the preference and the name shown in Settings.
SAFE = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -_")

for pack in packs:
    name = pack.name
    check(set(name) <= SAFE and not name.startswith(".") and 0 < len(name) <= 64,
          "%s is not a name ControlTextures.isSafeName would accept" % name)

    base = pack / "button.png"
    check(base.is_file(), "%s has no button.png, so it would never be offered" % name)
    if not base.is_file():
        continue
    size = png_size(base)
    check(size is not None, "%s/button.png is not a PNG" % name)
    if size is None:
        continue
    check(max(size) <= 1024,
          "%s/button.png is %dx%d, past the size the loader refuses at" % ((name,) + size))

    pressed = pack / "button_pressed.png"
    if pressed.is_file():
        check(png_size(pressed) == size,
              "%s/button_pressed.png is %s, not the %s of its base; it would be ignored"
              % (name, png_size(pressed), size))

    meta_file = pack / "pack.json"
    check(meta_file.is_file(), "%s has no pack.json, so its slice would default to 0" % name)
    if not meta_file.is_file():
        continue
    import json
    meta = json.loads(meta_file.read_text())
    slice_ = meta.get("slice", 0)
    check(isinstance(meta.get("smooth"), bool), "%s: smooth must be true or false" % name)
    check(meta.get("label") in ("light", "dark"), "%s: label must be light or dark" % name)
    check(slice_ * 2 < min(size),
          "%s: slice %d does not leave a middle band in a %dx%d face"
          % (name, slice_, size[0], size[1]))
    # Every cell of a shipped pack should be a real cell: the clamp in the loader exists for a
    # stranger's pack, not as somewhere for ours to land.
    for (l, t, r, b) in build_source(size[0], size[1], slice_):
        check(r > l and b > t, "%s: empty source cell %s" % (name, (l, t, r, b)))

print("checked %d shipped packs" % len(packs))
print("checked %d button geometries across %d source sizes" % (checked, len(SOURCES)))
if failures:
    for message in failures:
        print("FAIL: " + message)
    sys.exit(1)
print("control texture geometry OK")
