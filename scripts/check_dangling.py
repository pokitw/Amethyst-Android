#!/usr/bin/env python3
"""A modifier left behind by an edit attaches itself to whatever came next.

Two shapes, one mistake, both of which cost a CI cycle in the same session.

Written after an anchored replacement in ControlEditorState.kt consumed the line
`var showInMenu by mutableStateOf(...)` and not the `private set` indented beneath it. The
accessor then attached itself to the next declaration, which was a `val`, and the build failed
on "A 'val' property cannot have a setter" and "Property must be initialized" three minutes
into CI. Nothing about the diff looked wrong: the new code was correct and the damage was to a
line nobody had touched.

The second was the same thing in Java: an anchored replacement took
`public void onClickedGameKeyboard() {` and not the `@Override` above it, so that annotation
slid down in front of the next method, which already had one, and javac refused with
"Override is not a repeatable annotation interface".

That is 16.18 in a new costume, twice. A search for `var x by mutableStateOf(` or for a method
signature finds the declaration and not the modifier glued above or below it, so an edit
anchored on what the search returned silently drops half of a declaration, and the damage is to
a line the diff never shows. The general rule is to include everything grammatically attached
to an anchor; this check is the part of that rule a script can hold.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app_pojavlauncher/src/main/java")

ACCESSOR = re.compile(r"^\s+(private |internal |protected |)(set|get)\b")
SKIP = re.compile(r"^\s*(//|\*|/\*)")


DECLARATION = re.compile(r"^\s*(private |internal |protected |)(var|val)\s")


def owner_of(lines, index):
    """The declaration an accessor belongs to, walking up through continuation lines.

    A declaration spanning several lines ends on its own closing bracket, so taking the line
    directly above would report every multi-line `var` as having no owner. The walk stops at a
    blank line or a comment, which is what genuinely separates an accessor from a declaration.
    """
    for i in range(index - 1, -1, -1):
        line = lines[i]
        if not line.strip() or SKIP.match(line):
            return None
        if ACCESSOR.match(line) and line.strip() in ("set", "get", "private set",
                                                     "private get", "internal set",
                                                     "protected set"):
            continue  # a get above a set, both on the same property
        if DECLARATION.match(line):
            return line
        # Anything else is the middle of the declaration being walked up through.
    return None


BLANK_OR_COMMENT = re.compile(r"^\s*(//|\*|/\*|$)")
ANNOTATION_LINE = re.compile(r"^\s*@(\w+)\s*$")


# Named rather than "any annotation", because some genuinely repeat and a check that is wrong
# about one of those is a check somebody turns off.
NOT_REPEATABLE = ("Override", "Composable", "Keep", "JvmStatic")


def java_problems(path, lines, relative):
    """An annotation must reach its declaration without passing a copy of itself.

    A comment or a blank line between the annotation and the declaration is legal Java and is
    also exactly what an orphan looks like, so the thing worth refusing is the duplicate the
    compiler refuses: two of the same annotation arriving at one declaration.
    """
    found = []
    for index, line in enumerate(lines):
        match = ANNOTATION_LINE.match(line)
        if not match or match.group(1) not in NOT_REPEATABLE:
            continue
        for i in range(index + 1, min(index + 12, len(lines))):
            ahead = lines[i]
            if BLANK_OR_COMMENT.match(ahead):
                continue
            name = match.group(1)
            second = ANNOTATION_LINE.match(ahead)
            if second and second.group(1) == name:
                found.append(
                    "%s:%d a second @%s reaches the same declaration. One of them was left "
                    "behind by an edit that took a declaration and not the annotation above "
                    "it." % (relative, index + 1, name))
            elif ahead.strip().startswith("@" + name + " "):
                found.append(
                    "%s:%d @%s is followed by a declaration that already carries one"
                    % (relative, index + 1, name))
            break
    return found


def main():
    problems = []
    checked = 0
    for folder, _dirs, files in os.walk(SRC):
        for name in sorted(files):
            path = os.path.join(folder, name)
            if not name.endswith(".java") and not name.endswith(".kt"):
                continue
            lines = open(path, encoding="utf-8").read().split("\n")
            problems.extend(java_problems(path, lines, os.path.relpath(path, ROOT)))
            if not name.endswith(".kt"):
                continue
            path = os.path.join(folder, name)
            lines = open(path, encoding="utf-8").read().split("\n")
            for index, line in enumerate(lines):
                match = ACCESSOR.match(line)
                # Only the bare accessor shorthand, not `set(value) { ... }` with a body, and
                # not a `get() = ...` on the declaration line itself.
                if not match or line.strip() not in ("set", "get",
                                                     "private set", "private get",
                                                     "internal set", "protected set"):
                    continue
                checked += 1
                owner = owner_of(lines, index)
                relative = os.path.relpath(path, ROOT)
                if owner is None:
                    problems.append("%s:%d `%s` is attached to nothing"
                                    % (relative, index + 1, line.strip()))
                    continue
                stripped = owner.strip()
                if match.group(2) == "set" and not re.match(r"(private |internal |)var\b",
                                                            stripped):
                    problems.append(
                        "%s:%d `%s` sits under `%s`, which is not a var. An accessor belongs to "
                        "the declaration above it, so one left behind by an edit attaches "
                        "itself to whatever came next."
                        % (relative, index + 1, line.strip(), stripped[:60]))
    for problem in problems:
        print("FAIL " + problem)
    if problems:
        sys.exit(1)
    print("ok: %d Kotlin accessor(s) placed, and no stranded @Override" % checked)


main()
