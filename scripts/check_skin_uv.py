"""Check the skin UV table against the real Minecraft atlas layout.

A wrong rectangle here is a leg wearing a sleeve, and it is invisible until someone opens the
editor on a real skin. The ground truth below is the published 64x64 layout, written out by hand
rather than derived from the same box() the table uses, so the two cannot agree by sharing a bug.
"""
import re, sys, pathlib

SRC = pathlib.Path("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/ui/skin/SkinModel.kt").read_text()

# Reproduce box() independently.
def box(u, v, w, h, d):
    return {
        "TOP":    (u + d, v, w, d),
        "BOTTOM": (u + d + w, v, w, d),
        "RIGHT":  (u, v + d, d, h),
        "FRONT":  (u + d, v + d, w, h),
        "LEFT":   (u + d + w, v + d, d, h),
        "BACK":   (u + d + w + d, v + d, w, h),
    }

# Parse the part table out of the Kotlin: id, box(...) for faces and overlay.
parts = {}
for m in re.finditer(r'id = "(\w+)".*?faces = box\(([^)]*)\),\s*\n\s*overlay = box\(([^)]*)\)', SRC, re.S):
    pid, f, o = m.group(1), m.group(2), m.group(3)
    parts[pid] = (f.strip(), o.strip())

fail = 0
def check(cond, msg):
    global fail
    if not cond:
        print("FAIL:", msg); fail += 1

check(len(parts) == 6, "expected 6 parts, parsed %d" % len(parts))

# Ground truth crosses for a CLASSIC skin, from the published layout.
GROUND = {
    "head":     ((0, 0, 8, 8, 8),   (32, 0, 8, 8, 8)),
    "body":     ((16, 16, 8, 12, 4), (16, 32, 8, 12, 4)),
    "rightArm": ((40, 16, 4, 12, 4), (40, 32, 4, 12, 4)),
    "leftArm":  ((32, 48, 4, 12, 4), (48, 48, 4, 12, 4)),
    "rightLeg": ((0, 16, 4, 12, 4),  (0, 32, 4, 12, 4)),
    "leftLeg":  ((16, 48, 4, 12, 4), (0, 48, 4, 12, 4)),
}

def resolve(expr, arm):
    return [arm if t.strip() == "arm" else int(t) for t in expr.split(",")]

for pid, (fexpr, oexpr) in parts.items():
    check(pid in GROUND, "unknown part %s" % pid)
    if pid not in GROUND: continue
    got_f, got_o = resolve(fexpr, 4), resolve(oexpr, 4)
    want_f, want_o = GROUND[pid]
    check(tuple(got_f) == want_f, "%s base cross %s, expected %s" % (pid, got_f, want_f))
    check(tuple(got_o) == want_o, "%s overlay cross %s, expected %s" % (pid, got_o, want_o))

# Known individual rectangles, spot-checked against the layout everyone draws from.
SPOT = [
    ("head", "FRONT", False, (8, 8, 8, 8)),
    ("head", "TOP", False, (8, 0, 8, 8)),
    ("head", "FRONT", True, (40, 8, 8, 8)),
    ("body", "FRONT", False, (20, 20, 8, 12)),
    ("body", "RIGHT", False, (16, 20, 4, 12)),
    ("rightArm", "FRONT", False, (44, 20, 4, 12)),
    ("rightLeg", "FRONT", False, (4, 20, 4, 12)),
    ("leftArm", "FRONT", False, (36, 52, 4, 12)),
    ("leftLeg", "FRONT", False, (20, 52, 4, 12)),
]
for pid, face, ov, want in SPOT:
    cross = resolve(parts[pid][1 if ov else 0], 4)
    got = box(*cross)[face]
    check(got == want, "%s %s%s = %s, expected %s" % (pid, face, " overlay" if ov else "", got, want))

# Nothing may leave the 64x64 atlas, at either arm width.
for arm in (3, 4):
    for pid, (fexpr, oexpr) in parts.items():
        for expr in (fexpr, oexpr):
            for face, (x, y, w, h) in box(*resolve(expr, arm)).items():
                check(0 <= x and x + w <= 64 and 0 <= y and y + h <= 64,
                      "%s %s at arm=%d leaves the atlas: %s" % (pid, face, arm, (x, y, w, h)))

# No two BASE rectangles may overlap, or painting one face would smear another.
for arm in (3, 4):
    seen = {}
    for pid, (fexpr, _) in parts.items():
        for face, (x, y, w, h) in box(*resolve(fexpr, arm)).items():
            for px in range(x, x + w):
                for py in range(y, y + h):
                    key = (px, py)
                    if key in seen:
                        check(False, "arm=%d: %s %s overlaps %s at %s" % (arm, pid, face, seen[key], key))
                        break
                    seen[key] = "%s %s" % (pid, face)

# The slim detector's columns must be inside the CLASSIC right arm and outside the SLIM one.
classic = box(*resolve(parts["rightArm"][0], 4))
slim = box(*resolve(parts["rightArm"][0], 3))
def covered(rects, x, y):
    return any(rx <= x < rx + rw and ry <= y < ry + rh for (rx, ry, rw, rh) in rects.values())
for x in (54, 55):
    check(covered(classic, x, 25), "slim probe column %d is not painted on a classic arm" % x)
    check(not covered(slim, x, 25), "slim probe column %d IS painted on a slim arm" % x)

# The strip a classic arm uses and a slim one does not. Switching arm width writes here, and
# guessSlim reads part of it, so the two have to agree: a probe column outside the written set
# means a skin can be converted and then read back as the width it was converted away from.
classic_only = set()
slim_cover = set()
for slim_arm, target in ((3, slim_cover), (4, classic_only)):
    for pid in ("rightArm", "leftArm"):
        for which in (0, 1):
            for (x, y, w, h) in box(*resolve(parts[pid][which], slim_arm)).values():
                for px in range(x, x + w):
                    for py in range(y, y + h):
                        target.add((px, py))
classic_only -= slim_cover
check(len(classic_only) == 128,
      "expected 128 classic-only arm pixels, got %d" % len(classic_only))
check({x for x, _ in classic_only} == {42, 43, 46, 47, 50, 51, 54, 55, 58, 59, 62, 63},
      "classic-only columns were %s" % sorted({x for x, _ in classic_only}))
for px in (54, 55):
    for py in range(20, 32):
        check((px, py) in classic_only,
              "the slim probe pixel %s is not one the width switch clears" % ((px, py),))
# Every classic-only pixel must have a slim pixel to its left on the same row to rebuild from,
# or switching back to classic has nothing to copy and would invent a colour.
for (px, py) in classic_only:
    source = px - 1
    while source > 0 and (source, py) not in slim_cover:
        source -= 1
    check(source > 0, "no slim source column left of %s to rebuild from" % ((px, py),))

print("checked 6 parts, both arm widths, %d spot rectangles, bounds, overlap, the slim probe "
      "and the %d pixel arm-width strip" % (len(SPOT), len(classic_only)))
if fail:
    print("%d failure(s)" % fail); sys.exit(1)
print("skin UV table OK")
