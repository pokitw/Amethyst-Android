#!/usr/bin/env python3
"""The special button table is positional, and nothing enforced that but a comment.

ControlData.getSpecialButtons() must stay in -(index + 1) order. The raw negative integer is
what gets written into saved layout JSON and what LayoutConverter copies through unmigrated,
and the editor converts position to keycode by arithmetic over the list, so inserting one in
the middle silently re-points every button in every layout anybody has ever saved. There is no
error, no crash and no way to tell afterwards which button somebody meant.

That rule is written down in ControlData's own javadoc and was checked by nobody. Nine entries
have been appended since it was written.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATH = os.path.join(ROOT, "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/"
                          "customcontrols/ControlData.java")


def main():
    text = open(PATH, encoding="utf-8").read()
    constants = {name: int(value) for name, value
                 in re.findall(r"(SPECIALBTN_\w+)\s*=\s*(-\d+)\s*;", text)}
    if not constants:
        print("FAIL no SPECIALBTN constants found; has the file moved?")
        sys.exit(1)

    body = re.search(r"SPECIAL_BUTTONS\s*=\s*new ControlData\[\]\s*\{(.*?)\n\s*\};",
                     text, re.S)
    if body is None:
        print("FAIL could not find the SPECIAL_BUTTONS array")
        sys.exit(1)

    used = re.findall(r"new int\[\]\s*\{\s*(SPECIALBTN_\w+)\s*\}", body.group(1))
    problems = []
    for index, name in enumerate(used):
        if name not in constants:
            problems.append("%s is in the array and is not declared" % name)
            continue
        expected = -(index + 1)
        if constants[name] != expected:
            problems.append(
                "%s sits at position %d, so it must be %d and is %d. New entries are APPENDED: "
                "inserting one re-points that keycode in every layout already saved."
                % (name, index, expected, constants[name]))

    declared = set(constants)
    listed = set(used)
    for missing in sorted(declared - listed):
        problems.append("%s is declared and is not in the array, so nothing can send it"
                        % missing)
    if len(used) != len(listed):
        problems.append("the array lists a special button twice")

    for problem in problems:
        print("FAIL " + problem)
    if problems:
        sys.exit(1)
    print("ok: %d special buttons, every one at -(index + 1)" % len(used))


main()
