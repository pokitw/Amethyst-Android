#!/usr/bin/env python3
"""Evaluate the shipped control layouts the way the launcher will.

A layout's positions are exp4j expressions with ``${...}`` substituted by a plain string replace
(JSONUtils.insertSingleJSONValue), so they can be reproduced exactly: substitute, map ``px(n)`` to
``n * density``, and evaluate. That turns "does every button land on screen" from a hope into a
check, which matters because there is no device in CI and a control that lands off screen is a
control that cannot be tapped or even dragged back.

What is asserted, and where the honest limits are:
- every control sits fully on screen for every swept screen, density and button scale;
- controls do not overlap each other through 125% scale on screens of 640dp and up, through
  100% on the 320dp-tall outliers, and through the full 175% where there is tablet room. Past
  those, buttons on a small display start to touch: the top rows meet in the middle and the
  action cluster reaches the joystick, which is inherent to this many controls at that size and
  is the caveat the default has always carried. Both layouts are editable;
- the keycodes used actually exist: positive ones inside GLFW's range, negative ones no lower
  than the last SPECIALBTN the launcher defines;
- every layout keeps a control visible in the ungrabbed state, and a menu button among them.
  Every GUI in the game ungrabs the cursor, the pull-tab fallback hides whenever a layout has a
  menu button of its own, and a chat box that cannot be typed into is a player stranded.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app_pojavlauncher/src/main/assets"
LAYOUTS = ["default.json", "Bedrock.json"]

# The launcher's own vocabulary limits: the last appended special button, and GLFW's top keycode.
CONTROL_DATA = (ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/"
                       "customcontrols/ControlData.java").read_text(encoding="utf-8")
SPECIALS = [int(m) for m in re.findall(r"SPECIALBTN_\w+\s*=\s*(-\d+)", CONTROL_DATA)]
LOWEST_SPECIAL = min(SPECIALS)
GLFW_LAST = 348  # GLFW_KEY_MENU, the end of the keycode table

# Screens as dp, swept at phone densities. 569x320 is an hdpi 854x480 phone, the smallest
# landscape screen minSdk 21 still meets; 640x360 is the smallest common one.
SCREENS_DP = [(569, 320), (640, 360), (732, 412), (851, 393), (915, 412), (1024, 600)]
DENSITIES = [1.75, 2.625, 3.0]
SCALES = [80, 100, 125, 150, 175]

failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def evaluate(expression, width_px, height_px, screen_w, screen_h, scale, density):
    """Exactly the launcher's pipeline: string replace, then arithmetic."""
    expression = (expression
                  .replace("${screen_width}", str(screen_w))
                  .replace("${screen_height}", str(screen_h))
                  .replace("${width}", str(width_px))
                  .replace("${height}", str(height_px))
                  .replace("${preferred_scale}", str(float(scale)))
                  .replace("${margin}", "0"))
    expression = re.sub(r"px\(([-0-9.]+)\)", lambda m: str(float(m.group(1)) * density),
                        expression)
    expression = expression.replace("^", "**")
    if "${" in expression:
        raise ValueError("unsubstituted token in: " + expression)
    return eval(expression, {"__builtins__": {}}, {})


def controls_of(layout):
    """Every positioned control: plain buttons, drawer handles, joysticks."""
    out = list(layout.get("mControlDataList", []))
    out += [d["properties"] for d in layout.get("mDrawerDataList", [])]
    out += list(layout.get("mJoystickDataList", []))
    return out


for name in LAYOUTS:
    path = ASSETS / name
    check(path.is_file(), "%s is not shipped" % name)
    if not path.is_file():
        continue
    layout = json.loads(path.read_text(encoding="utf-8"))
    check(layout.get("version") == 8, "%s: version must be 8, the current format" % name)
    check(layout.get("scaledAt") == 100, "%s: shipped layouts are authored at 100%%" % name)

    controls = controls_of(layout)
    menu_visible = [c for c in layout.get("mControlDataList", []) if c.get("displayInMenu")]
    check(any(-9 in c.get("keycodes", []) for c in menu_visible),
          "%s: no menu button survives the ungrabbed state; every GUI ungrabs the cursor and "
          "the pull tab hides when a layout has its own menu button, so this strands the player"
          % name)
    check(any(-1 in c.get("keycodes", []) or -12 in c.get("keycodes", [])
              for c in menu_visible),
          "%s: no keyboard survives the ungrabbed state, so chat opens and cannot be typed into"
          % name)
    # A pointer is the third thing a GUI needs, and the one the first draft of Bedrock.json left
    # out. SPECIALBTN_VIRTUALMOUSE on a control button is the only route to MainActivity.toggleMouse
    # in the whole launcher, so a layout without one cannot summon the cursor and, worse, cannot
    # dismiss it either: with "virtual mouse at start" on, the touchpad comes up at surface-ready
    # and stays up for the session, and while it is up InGUIEventProcessor skips the tap-to-position
    # path that would otherwise have made menus usable without it.
    check(any(-5 in c.get("keycodes", []) for c in menu_visible),
          "%s: no virtual-mouse toggle survives the ungrabbed state; it is the only thing that can "
          "turn the touchpad on or off, and a GUI with no pointer is a GUI that cannot be worked"
          % name)
    for control in controls:
        keys = [k for k in control.get("keycodes", []) if k != 0]
        for key in keys:
            check(LOWEST_SPECIAL <= key <= GLFW_LAST,
                  "%s: %s sends keycode %d, outside every table the launcher has"
                  % (name, control["name"], key))

    for (screen_w_dp, screen_h_dp) in SCREENS_DP:
        for density in DENSITIES:
            screen_w, screen_h = screen_w_dp * density, screen_h_dp * density
            for scale in SCALES:
                rects = []
                for control in controls:
                    # The launcher rescales sizes by preferred_scale / scaledAt at load.
                    w = control["width"] * density * scale / 100.0
                    h = control["height"] * density * scale / 100.0
                    x = evaluate(control["dynamicX"], w, h, screen_w, screen_h, scale, density)
                    y = evaluate(control["dynamicY"], w, h, screen_w, screen_h, scale, density)
                    rects.append((control["name"], x, y, x + w, y + h))
                    check(-0.5 <= x and -0.5 <= y
                          and x + w <= screen_w + 0.5 and y + h <= screen_h + 0.5,
                          "%s: %s lands off a %dx%ddp screen at %d%% (x=%.0f y=%.0f w=%.0f)"
                          % (name, control["name"], screen_w_dp, screen_h_dp, scale, x, y, w))

                # Overlap: strict through 125% on ordinary screens, 100% on the 320dp-tall
                # outliers, and through 175% where there is tablet room. Above those, buttons
                # on a small display start to touch, which both layouts have always accepted.
                if ((scale <= 125 and screen_w_dp >= 640) or scale <= 100
                        or screen_w_dp >= 1024):
                    for i in range(len(rects)):
                        for j in range(i + 1, len(rects)):
                            a, b = rects[i], rects[j]
                            apart = (a[3] <= b[1] or b[3] <= a[1]
                                     or a[4] <= b[2] or b[4] <= a[2])
                            check(apart,
                                  "%s: %s and %s overlap on a %dx%ddp screen at %d%%"
                                  % (name, a[0], b[0], screen_w_dp, screen_h_dp, scale))

print("evaluated %d layouts across %d screens x %d densities x %d scales"
      % (len(LAYOUTS), len(SCREENS_DP), len(DENSITIES), len(SCALES)))
if failures:
    seen = set()
    for message in failures:
        if message not in seen:
            print("FAIL: " + message)
            seen.add(message)
    sys.exit(1)
print("control layouts OK")
