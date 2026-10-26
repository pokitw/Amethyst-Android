#!/usr/bin/env python3
"""Guards for two upstream fixes that could be silently undone.

Neither can be driven: one is a manifest declaration only the platform reads, the other lives
inside an Android view class whose dependencies would have to be stubbed wholesale to compile it.
So these are structural assertions, which is weaker than executing the code and is said plainly
rather than dressed up. They exist because both fixes look like noise in a diff and a later tidy
could remove them without anything else noticing.

GAME MODE. `<meta-data>` is only valid under application, activity, service, receiver, provider or
activity-alias, and the platform reads `android.game_mode_config` from the *application's*
meta-data. Upstream c11e6f995 put theirs as a direct child of `<manifest>`, where it is very
likely inert, so "we have the same declaration as upstream" is not the thing to check. The
placement is.

IME CLAMPING. Upstream 8da3cf972 fixed a crash from swiping an IME's space key to the end of the
input box. The file is byte-identical to upstream's post-fix version, and the two things that
would be easy to "simplify" back are the clamping of both ends of a range and deriving
selectionLeft from this call's own arguments rather than propagating the previous state's.
"""
import re
import sys
import pathlib
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = ROOT / "app_pojavlauncher/src/main/AndroidManifest.xml"
GAME_MODE = ROOT / "app_pojavlauncher/src/main/res/xml-v33/game_mode_config.xml"
IME = ROOT / ("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/"
              "TouchControllerInputView.java")

failures = []
checks = 0


def check(ok, message):
    global checks
    checks += 1
    if not ok:
        failures.append(message)


def game_mode():
    root = ET.parse(MANIFEST).getroot()
    app = root.find("application")
    check(app is not None, "AndroidManifest has no <application>")
    if app is None:
        return

    under_app = [m.get(ANDROID + "name") for m in app.findall("meta-data")]
    under_manifest = [m.get(ANDROID + "name") for m in root.findall("meta-data")]

    check("android.game_mode_config" in under_app,
          "android.game_mode_config is not a <meta-data> child of <application>, which is the "
          "only place the platform reads it from")
    check("android.game_mode_config" not in under_manifest,
          "android.game_mode_config is declared directly under <manifest>, where <meta-data> is "
          "not valid and the platform does not look; that is upstream's placement and it is "
          "very likely inert")

    # The declaration only matters because the platform treats this app as a game.
    check(app.get(ANDROID + "appCategory") == "game",
          'android:appCategory is no longer "game", so the game mode declaration is moot')
    check(app.get(ANDROID + "isGame") == "true",
          'android:isGame is no longer "true", so the game mode declaration is moot')

    check(GAME_MODE.is_file(),
          "res/xml-v33/game_mode_config.xml is missing; the manifest reference will not resolve")
    if not GAME_MODE.is_file():
        return

    # xml-v33 is the correct bucket: nothing below API 33 reads this, and putting it in plain
    # res/xml would ship it to every device for no reason.
    check(GAME_MODE.parent.name == "xml-v33",
          "game_mode_config.xml is not in res/xml-v33")
    check(not (ROOT / "app_pojavlauncher/src/main/res/xml/game_mode_config.xml").exists(),
          "a second copy of game_mode_config.xml exists in res/xml")

    cfg = ET.parse(GAME_MODE).getroot()
    check(cfg.tag == "game-mode-config",
          "the root element of game_mode_config.xml is <%s>, not <game-mode-config>" % cfg.tag)
    # Both interventions are refused because this launcher owns both knobs itself and a player
    # set them deliberately. Flipping either hands an OEM the right to overrule that silently.
    for attribute in ("allowGameDownscaling", "allowGameFpsOverride"):
        check(cfg.get(ANDROID + attribute) == "false",
              "%s is not false. The resolution scaler and the reachable game area are settings a "
              "player chose, and an OEM intervention stacked on them rewrites a saved preference "
              "silently." % attribute)
    for attribute in ("supportsBatteryGameMode", "supportsPerformanceGameMode"):
        check(cfg.get(ANDROID + attribute) == "true",
              "%s is not true; the point is to opt into the modes while refusing the "
              "interventions" % attribute)


def ime_clamping():
    source = IME.read_text(encoding="utf-8")

    check("private int clamp(" in source or "int clamp(" in source,
          "the clamp() helper is gone from TouchControllerInputView")

    for method in ("setComposingRegion", "setSelection"):
        # Grab the method body so the assertion is about the right code and not the file.
        at = source.find("public boolean %s(" % method)
        check(at >= 0, "%s is gone from TouchControllerInputView" % method)
        if at < 0:
            continue
        body = source[at:at + 1200]
        check("clamp(" in body,
              "%s no longer clamps its range. An out of range or inverted range from a broken "
              "IME produces a negative length or an offset past the text, which is the crash "
              "upstream 8da3cf972 fixed (swiping the Fcitx space key to the end of the box)."
              % method)

    at = source.find("public boolean setSelection(")
    if at >= 0:
        body = source[at:at + 1200]
        check("start > end" in body,
              "setSelection no longer derives selectionLeft from start > end. Propagating the "
              "previous state's value is the behaviour bug fixed alongside the clamping, and it "
              "is independent of it.")
        check("getSelectionLeft()" not in body,
              "setSelection is reading getSelectionLeft() again, which propagates the previous "
              "state's value instead of deriving it from this call")


def main():
    game_mode()
    ime_clamping()
    print("%d checks" % checks)
    for f in failures:
        print("FAIL: " + f)
    if failures:
        print("%d FAILURES" % len(failures))
        return 1
    print("all passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
