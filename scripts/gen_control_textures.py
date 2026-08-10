#!/usr/bin/env python3
"""Draw the texture packs that ship with the launcher.

The packs are generated rather than hand-painted, and that is the point: a button face in this
format is a bevelled frame around a flat middle, which is a rule, not a picture. Written out as a
rule it can be read, argued with and re-run; shipped as ten PNGs it could only be replaced.

<b>Why every pack looks the way it does.</b> ControlTextureDrawable maps nine source cells onto
nine destination cells with drawBitmap, so only the four corners are drawn unstretched. The top and
bottom edges stretch horizontally, the left and right edges stretch vertically, and the middle
stretches both ways. So the middle has to be flat, an edge may only vary along the axis it is not
stretched on, and everything with any character in it lives in the corners. A bevelled frame around
a flat interior is not a stylistic choice here, it is the shape the format can actually draw, and
it happens to be exactly what the Bedrock button looks like.

Run from anywhere:  python3 scripts/gen_control_textures.py
"""

import json
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("This needs Pillow: pip install pillow")

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app_pojavlauncher/src/main/assets/controltextures"

# How much bigger the rounded faces are drawn before being scaled back down. Their corners are
# curves rather than steps, so they are the one thing here that needs real antialiasing.
SUPERSAMPLE = 8


def rgba(value, alpha=255):
    """0xRRGGBB plus an alpha, the way the rest of the launcher writes a colour."""
    return ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, alpha)


class Blocky:
    """A square, unfiltered face: outline, bevel, flat middle. Sixteen pixels, like the game.

    Sixteen rather than thirty-two because the scale that a corner is drawn at is rounded to a
    whole number of source texels for unfiltered packs, and a smaller source means a larger scale
    means the rounding moves the border proportionally less between one button size and the next.
    """

    size = 16
    slice = 3
    smooth = False

    def __init__(self, outline, light, base, dark, fill, label="light"):
        self.outline, self.light, self.base = outline, light, base
        self.dark, self.fill, self.label = dark, fill, label

    def draw(self, pressed=False):
        n = self.size
        image = Image.new("RGBA", (n, n))
        pixels = image.load()
        # A press inverts the bevel, which is what a real one does: the light edge moves to the
        # bottom right and the face sinks. Better than the white wash the flat skin has to use,
        # and the reason every pack here ships a pressed face of its own.
        light = self.dark if pressed else self.light
        dark = self.light if pressed else self.dark
        fill = self.fill if not pressed else self._sink(self.fill)
        base = self.base if not pressed else self._sink(self.base)
        for y in range(n):
            for x in range(n):
                edge = min(x, y, n - 1 - x, n - 1 - y)
                if edge == 0:
                    colour = self.outline
                elif edge == 1:
                    top_left = y == 1 or x == 1
                    bottom_right = y == n - 2 or x == n - 2
                    # The two off corners of this ring belong to neither side, so they take the
                    # face colour and the bevel reads as a chamfer rather than as a cross.
                    if top_left and not bottom_right:
                        colour = light
                    elif bottom_right and not top_left:
                        colour = dark
                    else:
                        colour = base
                elif edge == 2:
                    colour = base
                else:
                    colour = fill
                pixels[x, y] = colour
        return image

    @staticmethod
    def _sink(colour):
        r, g, b, a = colour
        return (int(r * 0.82), int(g * 0.82), int(b * 0.82), a)


class Rounded:
    """A soft, filtered face: a rounded rectangle with a hairline keyline and a flat interior.

    Its slice is large enough that the whole curve lives inside the corner cells, where nothing is
    stretched: a radius wider than the slice would run into the edge cells and be pulled along the
    button, which is a rounded rectangle turning into a lens.

    <b>Thirty-two pixels, and that number is load-bearing.</b> cornerFor never scales a corner
    below 1:1, so a source larger than the button leaves the corner at source size: at sixty-four
    it drew an eighteen pixel radius on a forty-six pixel button, which is not a rounded button, it
    is a circle, and the same eighteen pixels on a large button read as barely rounded at all. At
    thirty-two the ratio is above one for every button a layout can make, so the radius scales with
    the button and the shape keeps its proportions across the whole size slider.
    """

    size = 32
    slice = 8
    smooth = True

    def __init__(self, border, fill, label="light", radius=6, width=2):
        self.border, self.fill, self.label = border, fill, label
        self.radius, self.width = radius, width

    def draw(self, pressed=False):
        s = SUPERSAMPLE
        n = self.size
        big = Image.new("RGBA", (n * s, n * s), (0, 0, 0, 0))
        canvas = ImageDraw.Draw(big)
        fill = self.fill if not pressed else self._lift(self.fill)
        border = self.border if not pressed else self._lift(self.border)
        canvas.rounded_rectangle(
            [0, 0, n * s - 1, n * s - 1],
            radius=self.radius * s,
            fill=fill,
            outline=border,
            width=self.width * s,
        )
        return big.resize((n, n), Image.LANCZOS)

    @staticmethod
    def _lift(colour):
        """A press brightens rather than sinking: there is no bevel here to invert."""
        r, g, b, a = colour
        return (min(255, int(r + (255 - r) * 0.22)),
                min(255, int(g + (255 - g) * 0.22)),
                min(255, int(b + (255 - b) * 0.22)),
                min(255, int(a * 1.15)))


# ---------------------------------------------------------------------------- the packs
#
# Ten, and they are a spread rather than a gradient: two of them are the launcher's own dark
# surfaces, and the other eight are the blocks a player already knows the colour of. Naming them
# after blocks is not decoration, it is the whole of how someone picks one.
#
# The folder name is the name shown in Settings, so it is written the way it should read.

PACKS = {
    # The Bedrock button, which is what started all this: pale stone frame, dark interior.
    "Stone": Blocky(
        outline=rgba(0x2B2B2B), light=rgba(0xC5C5C5), base=rgba(0x8C8C8C),
        dark=rgba(0x5A5A5A), fill=rgba(0x3E3E3E, 200)),
    "Deepslate": Blocky(
        outline=rgba(0x101014), light=rgba(0x5E5E68), base=rgba(0x3B3B44),
        dark=rgba(0x22222A), fill=rgba(0x191920, 210)),
    "Oak": Blocky(
        outline=rgba(0x3A2814), light=rgba(0xC0965A), base=rgba(0x9A703C),
        dark=rgba(0x654626, 255), fill=rgba(0x4A3319, 205)),
    # Pale enough that white content on top of it disappears, which is what "label" is for.
    "Iron": Blocky(
        outline=rgba(0x4A4A4A), light=rgba(0xF2F2F2), base=rgba(0xD2D2D2),
        dark=rgba(0x9C9C9C), fill=rgba(0xBFBFBF, 235), label="dark"),
    "Gold": Blocky(
        outline=rgba(0x5A4208), light=rgba(0xFFE68A), base=rgba(0xE8C13C),
        dark=rgba(0xA8871F), fill=rgba(0x6B5312, 215), label="light"),
    "Emerald": Blocky(
        outline=rgba(0x0B3A22), light=rgba(0x6BE8A0), base=rgba(0x2FB86C),
        dark=rgba(0x1A7A46), fill=rgba(0x11482B, 210)),
    "Redstone": Blocky(
        outline=rgba(0x3A0C0C), light=rgba(0xFF6B60), base=rgba(0xC42B22),
        dark=rgba(0x8A1A14), fill=rgba(0x4A1210, 210)),
    "Lapis": Blocky(
        outline=rgba(0x0C1C3A), light=rgba(0x6C9BE8), base=rgba(0x2B5BB8),
        dark=rgba(0x1A3C7A), fill=rgba(0x121F3E, 210)),
    # The brand, on the accent ramp from the handbook rather than on a colour invented here.
    "Amethyst": Blocky(
        outline=rgba(0x2A1338), light=rgba(0xD6B4F2), base=rgba(0x9649B8),
        dark=rgba(0x542A73), fill=rgba(0x2F2839, 215)),
    # The two soft ones, on the launcher's own neutral ramp. Between them they show that the
    # format does rounded and nearly invisible as readily as it does square and chunky.
    "Slate": Rounded(border=rgba(0x3A3245), fill=rgba(0x1D1826, 225)),
    # Smoked rather than clear. A pale wash with a hairline border is what "glass" wants to be and
    # it vanishes over a bright sky, which is the same thing that makes the control glyphs solid
    # shapes instead of strokes. Tinting it dark keeps it obviously see-through against a cave and
    # still legible against midday, which is the only version of this worth shipping.
    "Glass": Rounded(border=rgba(0xE9E2EF, 190), fill=rgba(0x0E0B12, 105), radius=7, width=2),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    written = 0
    for name, pack in PACKS.items():
        folder = OUT / name
        folder.mkdir(exist_ok=True)
        pack.draw(False).save(folder / "button.png", optimize=True)
        pack.draw(True).save(folder / "button_pressed.png", optimize=True)
        (folder / "pack.json").write_text(
            json.dumps({"slice": pack.slice, "smooth": pack.smooth, "label": pack.label},
                       indent=2) + "\n", encoding="utf-8")
        written += 1
        print("  %-10s %2dpx  slice %-2d %s  %s"
              % (name, pack.size, pack.slice,
                 "smooth" if pack.smooth else "pixels", pack.label))
    print("wrote %d packs to %s" % (written, OUT.relative_to(ROOT)))


if __name__ == "__main__":
    main()
