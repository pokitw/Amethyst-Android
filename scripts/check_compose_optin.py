#!/usr/bin/env python3
"""Reproduce Kotlin's "This declaration needs opt-in" against the Compose APIs that need it.

There is no Kotlin compiler in this container, so CI is the only thing that sees a missing
@OptIn, and it has now cost two cycles: ModalBottomSheet on the world sheet, and
combinedClickable on the control center's page rail. Both are the same mistake, both are
invisible in review because the call looks exactly like a call to a stable API, and both are
decidable from the source.

What it checks: every use of a known experimental API has an @OptIn naming the right marker,
either on the enclosing top-level declaration or on the file. That is coarser than Kotlin's own
rule in one direction, since a propagating @ExperimentalFoundationApi annotation on the function
would also satisfy the compiler, and the check accepts those too.

Adding an API here is the point of the file: when a build fails on an opt-in, the fix is the
annotation AND a line in MARKERS, or the next one costs another cycle.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app_pojavlauncher/src/main/java")

# API name -> the marker that opts in to it.
MARKERS = {
    "combinedClickable": "ExperimentalFoundationApi",
    "ModalBottomSheet": "ExperimentalMaterial3Api",
    "rememberModalBottomSheetState": "ExperimentalMaterial3Api",
    "SheetState": "ExperimentalMaterial3Api",
    "FlowRow": "ExperimentalLayoutApi",
    "FlowColumn": "ExperimentalLayoutApi",
    "TopAppBar": "ExperimentalMaterial3Api",
    "CenterAlignedTopAppBar": "ExperimentalMaterial3Api",
    "pullRefresh": "ExperimentalMaterialApi",
    "AnimatedContent": None,  # stable since 1.5; here so it is not re-added by mistake
}

DECLARATION = re.compile(r"^(?:public |private |internal |)(?:suspend )?(?:inline )?fun\b")
ANNOTATION = re.compile(r"^\s*@")


def enclosing_annotations(lines, index):
    """Every annotation attached to the top-level declaration containing this line."""
    start = None
    for i in range(index, -1, -1):
        if DECLARATION.match(lines[i]):
            start = i
            break
    if start is None:
        return []
    annotations = []
    i = start - 1
    while i >= 0:
        line = lines[i]
        if ANNOTATION.match(line) or line.strip().startswith("*") or \
                line.strip().startswith("/*") or line.strip().startswith("//") or \
                line.strip().endswith("*/") or not line.strip():
            if ANNOTATION.match(line):
                annotations.append(line.strip())
            i -= 1
            continue
        break
    return annotations


def main():
    problems = []
    checked = 0
    for folder, _dirs, files in os.walk(SRC):
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(folder, name)
            text = open(path, encoding="utf-8").read()
            lines = text.split("\n")
            file_optin = [l for l in lines if l.startswith("@file:OptIn")]
            for api, marker in MARKERS.items():
                if marker is None:
                    continue
                # A word boundary that still matches `.combinedClickable(`, which is how a
                # Modifier extension is always called. The first draft excluded a leading dot
                # and therefore could not see the bug this file was written for: it caught
                # ModalBottomSheet, which is called bare, and passed on the one that had just
                # failed CI (16.16).
                pattern = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(api) + r"\s*\(")
                for index, line in enumerate(lines):
                    if line.lstrip().startswith("import "):
                        continue
                    if not pattern.search(line):
                        continue
                    checked += 1
                    if any(marker in l for l in file_optin):
                        continue
                    attached = enclosing_annotations(lines, index)
                    if any(marker in a for a in attached):
                        continue
                    problems.append(
                        "%s:%d uses %s with no @OptIn(%s::class) on its declaration"
                        % (os.path.relpath(path, ROOT), index + 1, api, marker))
    for problem in problems:
        print("FAIL " + problem)
    if problems:
        sys.exit(1)
    print("ok: %d experimental Compose call(s), every one opted in" % checked)


main()
