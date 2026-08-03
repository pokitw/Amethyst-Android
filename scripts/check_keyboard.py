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


def strip_line_comments(text):
    """Drop `//` comments that are not inside a string or character literal.

    Without this, annotating a cap inline would break the splitter below on the first comma in the
    comment — which is exactly how a checker stops checking without anyone noticing.
    """
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and text[i:i + 2] == "//":
            end = text.find("\n", i)
            i = n if end == -1 else end
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

    board_text = strip_line_comments(BOARDS.read_text(encoding="utf-8"))
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

    # Nothing the old dialog could send may have gone missing.
    offered = set(
        re.findall(
            r"add\(KeyEvent\.KEYCODE_[A-Z0-9_]+,\s*LwjglGlfwKeycode\.(GLFW_KEY_[A-Z0-9_]+)\)",
            MAPPING.read_text(encoding="utf-8"),
        )
    )
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
