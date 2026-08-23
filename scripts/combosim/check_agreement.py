#!/usr/bin/env python3
"""Two lists of modifier keycodes must be the same list.

KeyCombo.isModifier decides which keys a button sends FIRST; CallbackBridge.setModifiers decides
which keys write a held flag. A key in one and not the other is silent in both directions: ordered
but flagless promises a modifier nothing downstream can honour, flagged but unordered sets the flag
after the key it was meant to modify has already gone.

Compared as declarations rather than by re-implementing either, so this is not the trap of a
harness that checks its own copy of the logic.
"""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app_pojavlauncher/src/main/java"

combo = (SRC / "net/kdt/pojavlaunch/customcontrols/KeyCombo.java").read_text()
bridge = (SRC / "org/lwjgl/glfw/CallbackBridge.java").read_text()
codes = (SRC / "net/kdt/pojavlaunch/LwjglGlfwKeycode.java").read_text()

names = {n: int(v) for n, v in re.findall(r'(GLFW_KEY_\w+)\s*=\s*(\d+)', codes)}

body = re.search(r'boolean isModifier\(int keycode\)\s*\{(.*?)\n    \}', combo, re.S)
if not body:
    sys.exit("could not find KeyCombo.isModifier")
ordered = {int(v) for v in re.findall(r'case\s+(\d+)\s*:', body.group(1))}

body = re.search(r'void setModifiers\(int keyCode, boolean isDown\)\s*\{(.*?)\n    \}', bridge, re.S)
if not body:
    sys.exit("could not find CallbackBridge.setModifiers")
flagged = set()
for name in re.findall(r'case\s+LwjglGlfwKeycode\.(GLFW_KEY_\w+)\s*:', body.group(1)):
    if name not in names:
        sys.exit("unknown keycode name in setModifiers: " + name)
    flagged.add(names[name])

if not ordered or not flagged:
    sys.exit("one of the two lists came out empty, so the parse is wrong")

if ordered != flagged:
    only_ordered = sorted(ordered - flagged)
    only_flagged = sorted(flagged - ordered)
    print("the two modifier lists have drifted apart")
    if only_ordered:
        print("  ordered first but no flag is written:", only_ordered)
    if only_flagged:
        print("  a flag is written but not ordered first:", only_flagged)
    sys.exit(1)

print("ok: %d modifier keycodes, and KeyCombo.isModifier agrees with "
      "CallbackBridge.setModifiers exactly" % len(ordered))
