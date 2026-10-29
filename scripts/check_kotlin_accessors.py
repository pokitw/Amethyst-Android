#!/usr/bin/env python3
"""A `private set` must belong to a `var`, and an orphaned one is a CI-only error.

Written after an anchored replacement in ControlEditorState.kt consumed the line
`var showInMenu by mutableStateOf(...)` and not the `private set` indented beneath it. The
accessor then attached itself to the next declaration, which was a `val`, and the build failed
on "A 'val' property cannot have a setter" and "Property must be initialized" three minutes
into CI. Nothing about the diff looked wrong: the new code was correct and the damage was to a
line nobody had touched.

That is 16.18 in a new costume. A search for `var x by mutableStateOf(` finds the declaration
and not the accessor glued to it, so an edit anchored on what the search returned is an edit
that silently drops half of a declaration. The general rule is to include everything
grammatically attached to an anchor; this check is the part of that rule a script can hold.
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


def main():
    problems = []
    checked = 0
    for folder, _dirs, files in os.walk(SRC):
        for name in sorted(files):
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
    print("ok: %d property accessor(s), every one on a property that can have it" % checked)


main()
