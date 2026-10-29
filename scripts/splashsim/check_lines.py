#!/usr/bin/env python3
"""The splash and tip tables, checked as content.

A splash line that wraps is not a splash line, and a tip that runs past two lines on a phone is
not read. Neither failure is visible anywhere in the build, and both are the kind of thing that
creeps in one line at a time, so the bounds are asserted rather than trusted.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
STRINGS = os.path.join(ROOT, "app_pojavlauncher/src/main/res/values/strings.xml")

LIMITS = {"splash_lines": 44, "loading_tips": 90}
MINIMUM = {"splash_lines": 12, "loading_tips": 8}


def main():
    text = open(STRINGS, encoding="utf-8").read()
    problems = []
    for name, limit in LIMITS.items():
        match = re.search(r'<string-array name="%s">(.*?)</string-array>' % name, text, re.S)
        if not match:
            problems.append("%s is missing from strings.xml" % name)
            continue
        items = re.findall(r"<item>(.*?)</item>", match.group(1), re.S)
        if len(items) < MINIMUM[name]:
            problems.append("%s holds only %d entries" % (name, len(items)))
        for item in items:
            if len(item) > limit:
                problems.append("%s: %d chars, over %d: %s" % (name, len(item), limit, item))
            if "—" in item:
                problems.append("%s: em dash: %s" % (name, item))
            if item != item.strip():
                problems.append("%s: padded with whitespace: %r" % (name, item))
        if len(set(items)) != len(items):
            problems.append("%s holds a duplicate" % name)
    for problem in problems:
        print("FAIL " + problem)
    if problems:
        sys.exit(1)
    print("ok: splash and tip tables")


main()
