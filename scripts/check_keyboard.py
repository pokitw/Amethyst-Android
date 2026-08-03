#!/usr/bin/env python3
"""Check the on-screen keyboard's boards against the keycode tables they replaced.

The keyboard is a hand-written table of caps, and three things about it can be wrong in ways no
compiler would notice: a row whose weights do not add up leaves the columns visibly out of step; a
keycode outside the reversed-lookup array crashes `EfficientAndroidLWJGLKeycode.getAndroidKeycode`
at the moment the key is pressed; and a key that was on the old `AlertDialog` list but is on
neither board is functionality quietly dropped in a redesign. All three are checked here against
the shipped sources rather than a copy of them.

Run from the repository root:  python3 scripts/check_keyboard.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"
GLFW = SRC / "LwjglGlfwKeycode.java"
MAPPING = SRC / "EfficientAndroidLWJGLKeycode.java"
BOARDS = SRC / "ui/game/GameKeyboard.kt"

# Weight is a positional argument, and which position depends on the helper. -1 means the helper
# never takes one, so every cap it makes is a single unit wide.
WEIGHT_POSITION = {"gap": 0, "letter": -1, "act": 2, "mod": 2, "digit": 3, "pair": 5}

# android.view.KeyEvent's values, which are frozen public API. They are here because
# EfficientAndroidLWJGLKeycode binary-searches sAndroidKeycodes, so the add() calls must stay in
# ascending order — a rule its own source shouts about in capitals and nothing enforces. Getting
# it wrong silently breaks *physical* keyboard input, which is not what anyone touching the
# on-screen one would think to test. Several of these are corroborated by the inline `//nn`
# comments in that file.
ANDROID_KEYCODES = {
    "KEYCODE_UNKNOWN": 0, "KEYCODE_HOME": 3, "KEYCODE_BACK": 4,
    "KEYCODE_0": 7, "KEYCODE_1": 8, "KEYCODE_2": 9, "KEYCODE_3": 10, "KEYCODE_4": 11,
    "KEYCODE_5": 12, "KEYCODE_6": 13, "KEYCODE_7": 14, "KEYCODE_8": 15, "KEYCODE_9": 16,
    "KEYCODE_POUND": 18,
    "KEYCODE_DPAD_UP": 19, "KEYCODE_DPAD_DOWN": 20, "KEYCODE_DPAD_LEFT": 21,
    "KEYCODE_DPAD_RIGHT": 22,
    "KEYCODE_COMMA": 55, "KEYCODE_PERIOD": 56,
    "KEYCODE_ALT_LEFT": 57, "KEYCODE_ALT_RIGHT": 58,
    "KEYCODE_SHIFT_LEFT": 59, "KEYCODE_SHIFT_RIGHT": 60,
    "KEYCODE_TAB": 61, "KEYCODE_SPACE": 62, "KEYCODE_ENTER": 66, "KEYCODE_DEL": 67,
    "KEYCODE_GRAVE": 68, "KEYCODE_MINUS": 69, "KEYCODE_EQUALS": 70,
    "KEYCODE_LEFT_BRACKET": 71, "KEYCODE_RIGHT_BRACKET": 72, "KEYCODE_BACKSLASH": 73,
    "KEYCODE_SEMICOLON": 74, "KEYCODE_APOSTROPHE": 75, "KEYCODE_SLASH": 76, "KEYCODE_AT": 77,
    "KEYCODE_PLUS": 81, "KEYCODE_PAGE_UP": 92, "KEYCODE_PAGE_DOWN": 93,
    "KEYCODE_ESCAPE": 111, "KEYCODE_FORWARD_DEL": 112,
    "KEYCODE_CTRL_LEFT": 113, "KEYCODE_CTRL_RIGHT": 114,
    "KEYCODE_CAPS_LOCK": 115, "KEYCODE_SCROLL_LOCK": 116, "KEYCODE_SYSRQ": 120,
    "KEYCODE_BREAK": 121, "KEYCODE_MOVE_HOME": 122, "KEYCODE_MOVE_END": 123,
    "KEYCODE_INSERT": 124,
    "KEYCODE_NUM_LOCK": 143,
    "KEYCODE_NUMPAD_DIVIDE": 154, "KEYCODE_NUMPAD_MULTIPLY": 155,
    "KEYCODE_NUMPAD_SUBTRACT": 156, "KEYCODE_NUMPAD_ADD": 157, "KEYCODE_NUMPAD_DOT": 158,
    "KEYCODE_NUMPAD_COMMA": 159, "KEYCODE_NUMPAD_ENTER": 160, "KEYCODE_NUMPAD_EQUALS": 161,
}
ANDROID_KEYCODES.update({f"KEYCODE_{chr(ord('A') + i)}": 29 + i for i in range(26)})
ANDROID_KEYCODES.update({f"KEYCODE_F{i}": 130 + i for i in range(1, 13)})
ANDROID_KEYCODES.update({f"KEYCODE_NUMPAD_{i}": 144 + i for i in range(10)})


def strip_comments(text):
    """Drop `//` and block comments that are not inside a string or character literal.

    Both kinds matter, and for different reasons. A line comment beside a cap would break the
    splitter below on its first comma. A *block* comment is worse: KDoc is prose, prose contains
    apostrophes, and an unpaired apostrophe opens a character literal that the scanner then runs
    to the next one — swallowing whatever lies between, including real caps. That is not
    hypothetical; it is what an ordinary "the cap's share" in a doc comment did here.
    """
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and text[i:i + 2] == "//":
            end = text.find("\n", i)
            i = n if end == -1 else end
        elif c == "/" and text[i:i + 2] == "/*":
            end = text.find("*/", i + 2)
            i = n if end == -1 else end + 2
        elif c == '"' or c == "'":
            start, quote = i, c
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == quote:
                    i += 1
                    break
                i += 1
            out.append(text[start:i])
        else:
            out.append(c)
            i += 1
    return "".join(out)


def split_top_level(text):
    """Split on commas that are not inside brackets, a string, or a character literal."""
    parts, depth, start = [], 0, 0
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"' or c == "'":
            quote = c
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == quote:
                    break
                i += 1
        elif c in "([":
            depth += 1
        elif c in ")]":
            depth -= 1
        elif c == "," and depth == 0:
            parts.append(text[start:i])
            start = i + 1
        i += 1
    tail = text[start:]
    if tail.strip():
        parts.append(tail)
    return [p.strip() for p in parts if p.strip()]


def body_after(text, marker):
    """The text between the parentheses of the `listOf(` that follows `marker`."""
    at = text.index(marker)
    open_at = text.index("listOf(", at) + len("listOf(") - 1
    depth, i, n = 0, open_at, len(text)
    while i < n:
        c = text[i]
        if c == '"' or c == "'":
            quote = c
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == quote:
                    break
                i += 1
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return text[open_at + 1:i]
        i += 1
    raise ValueError(f"unbalanced listOf( after {marker!r}")


def parse_cap(call):
    """(glfw_name_or_None, weight) for one cap expression."""
    match = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\s*\(", call)
    if not match:
        raise ValueError(f"not a cap expression: {call!r}")
    name = match.group(1)
    args = split_top_level(call[match.end():call.rindex(")")])

    if name == "Key":  # the raw constructor, which names its weight
        weight = 1.0
        for arg in args:
            if arg.startswith("weight"):
                weight = float(arg.split("=", 1)[1].strip().rstrip("f"))
        code = args[0].strip()
        return (None if "UNKNOWN" in code else code), weight

    if name not in WEIGHT_POSITION:
        raise ValueError(f"unknown cap helper {name!r} in {call!r}")
    position = WEIGHT_POSITION[name]
    weight = 1.0
    if position >= 0 and len(args) > position:
        weight = float(args[position].strip().rstrip("f"))
    code = None if name == "gap" else args[0].strip()
    return code, weight


def rows_of(text, marker):
    return [split_top_level(row[row.index("(") + 1:row.rindex(")")])
            for row in split_top_level(body_after(text, marker))]


def main() -> int:
    glfw_source = GLFW.read_text(encoding="utf-8")
    glfw_values = {
        name: int(value)
        for name, value in re.findall(r"(GLFW_KEY_[A-Z0-9_]+)\s*=\s*(\d+)", glfw_source)
    }
    # GLFW_KEY_LAST is written as an alias of the highest key rather than as a number.
    for name, alias in re.findall(
        r"(GLFW_KEY_[A-Z0-9_]+)\s*=\s*(GLFW_KEY_[A-Z0-9_]+)", glfw_source
    ):
        if alias in glfw_values:
            glfw_values[name] = glfw_values[alias]
    last = glfw_values["GLFW_KEY_LAST"]

    board_text = strip_comments(BOARDS.read_text(encoding="utf-8"))
    units = float(
        re.search(r"const val ROW_UNITS = ([0-9.]+)f", board_text).group(1)
    )

    boards = {
        "letters": rows_of(board_text, "private val LETTER_ROWS"),
        "numpad": rows_of(board_text, "private val NUMERIC_ROWS"),
    }
    bottom = split_top_level(body_after(board_text, "private fun bottomRowWith"))

    failures = []
    used = {}
    for board, rows in boards.items():
        seen = set()
        for index, row in enumerate(rows + [bottom]):
            caps = [parse_cap(call) for call in row]
            total = sum(weight for _, weight in caps)
            if abs(total - units) > 1e-6:
                failures.append(
                    f"{board} row {index} adds up to {total:g}, not {units:g} — "
                    "its caps will not line up with the rows above and below"
                )
            for code, _ in caps:
                if code is None:
                    continue
                if code in seen:
                    failures.append(f"{board} shows {code} twice on one screen")
                seen.add(code)
                used[code] = used.get(code, 0) + 1

    # Out of range here is not a cosmetic problem: getAndroidKeycode indexes an array of exactly
    # GLFW_KEY_LAST entries with the keycode, so anything at or above it is a crash on key press.
    for code in used:
        if code not in glfw_values:
            failures.append(f"{code} is not a GLFW keycode")
        elif not 0 <= glfw_values[code] < last:
            failures.append(
                f"{code} is {glfw_values[code]}, outside the reversed lookup array of {last}"
            )

    mapping_source = MAPPING.read_text(encoding="utf-8")
    pairs = re.findall(
        r"add\(KeyEvent\.(KEYCODE_[A-Z0-9_]+),\s*LwjglGlfwKeycode\.(GLFW_KEY_[A-Z0-9_]+)\)",
        mapping_source,
    )

    # The declared length has to match, or the tail of the arrays stays zero and every key past
    # the last one written binary-searches into a hole.
    declared = int(re.search(r"KEYCODE_COUNT = (\d+)", mapping_source).group(1))
    if declared != len(pairs):
        failures.append(f"KEYCODE_COUNT is {declared} but there are {len(pairs)} add() calls")

    android_order = []
    for android, _ in pairs:
        if android not in ANDROID_KEYCODES:
            failures.append(f"{android} is not in this script's table — add its value to check it")
        else:
            android_order.append((android, ANDROID_KEYCODES[android]))
    for (before, low), (after, high) in zip(android_order, android_order[1:]):
        if low > high:
            failures.append(
                f"sAndroidKeycodes is out of order: {before} ({low}) precedes {after} ({high}); "
                "getIndexByKey binary-searches this array, so physical key input would break"
            )

    # Nothing the old dialog could send may have gone missing.
    offered = {glfw for _, glfw in pairs}
    offered.discard("GLFW_KEY_UNKNOWN")
    missing = sorted(offered - set(used))
    if missing:
        failures.append(
            "the old keycode list could send these and the keyboard cannot: " + ", ".join(missing)
        )

    if failures:
        for line in failures:
            print(f"FAIL: {line}")
        return 1

    print(
        f"ok: {len(boards)} boards, every row adds up to {units:g}, "
        f"{len(used)} distinct keys, all {len(offered)} keys from the old list are reachable"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
