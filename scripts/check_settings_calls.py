#!/usr/bin/env python3
"""Check that every call to a shared settings component still matches its declaration.

The settings rows in ``ui/settings/SettingsComponents.kt`` are called from a dozen screens,
almost always with positional arguments, and adding an optional parameter in the middle of one
silently re-points every one of those positions. That is not a hypothetical: adding ``value``,
``iconRes`` and ``inert`` to ``SwitchRow`` before its callback broke eight calls in
``ControlEditorPanel.kt`` that passed the callback as the fourth positional argument, and the
only thing that caught it was a CI run three minutes long.

What is reproduced here is the exact error Kotlin gives, ``No value passed for parameter 'x'``:
for every call site, work out which parameters are actually satisfied (the positional prefix,
plus any named arguments, plus the trailing lambda if there is one) and check that every
parameter without a default is among them. The type check beside it catches the same mistake
arriving from the other direction, a callback landing in a slot that does not take one.

This is a text scan, not a parser. It is deliberately conservative: anything it cannot read
confidently it skips rather than guessing, because a check that cries wolf gets turned off.
"""

import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app_pojavlauncher", "src", "main", "java")
COMPONENTS = os.path.join(
    SRC, "net", "kdt", "pojavlaunch", "ui", "settings", "SettingsComponents.kt")

FUNCTION_TYPE = re.compile(r"(\(\s*[^()]*\)\s*->)|(^\s*@Composable)")


def split_top_level(text, separator=","):
    """Split on a separator that is not inside brackets, quotes or a string template.

    Angle brackets are deliberately NOT counted. A generic with a comma in it is rare inside a
    call argument, while ``size > 2`` is not, and counting ">" as a closing bracket sent the
    depth negative and merged every argument after a comparison into one.
    """
    parts, depth, quote, current = [], 0, None, []
    index = 0
    while index < len(text):
        char = text[index]
        if quote:
            current.append(char)
            if char == "\\":
                if index + 1 < len(text):
                    current.append(text[index + 1])
                    index += 2
                    continue
            elif char == quote:
                quote = None
            index += 1
            continue
        if char in "\"'":
            quote = char
            current.append(char)
        elif char in "([{":
            depth += 1
            current.append(char)
        elif char in ")]}":
            depth -= 1
            current.append(char)
        elif char == separator and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(char)
        index += 1
    parts.append("".join(current))
    return [p.strip() for p in parts if p.strip()]


def strip_comments(source):
    """Blank out comments so a commented-out call is never read as a real one."""
    out, index, length = [], 0, len(source)
    while index < length:
        two = source[index:index + 2]
        if two == "//":
            end = source.find("\n", index)
            end = length if end < 0 else end
            out.append(" " * (end - index))
            index = end
        elif two == "/*":
            end = source.find("*/", index)
            end = length if end < 0 else end + 2
            out.append("".join(c if c == "\n" else " " for c in source[index:end]))
            index = end
        else:
            out.append(source[index])
            index += 1
    return "".join(out)


def parse_declarations(source):
    """Every public composable in the components file, as an ordered parameter list."""
    declarations = {}
    for match in re.finditer(r"\nfun ([A-Z]\w*)\s*\(", source):
        name = match.group(1)
        start = match.end() - 1
        depth, index = 0, start
        while index < len(source):
            if source[index] == "(":
                depth += 1
            elif source[index] == ")":
                depth -= 1
                if depth == 0:
                    break
            index += 1
        parameters = []
        for raw in split_top_level(source[start + 1:index]):
            raw = raw.strip()
            if not raw:
                continue
            head = raw.split(":", 1)
            if len(head) != 2:
                continue
            parameter = head[0].strip().split()[-1]
            rest = head[1]
            has_default = "=" in split_top_level(rest, "=")[0] or "=" in rest
            # A default is an "=" outside brackets, which split_top_level already respects.
            has_default = len(split_top_level(rest, "=")) > 1
            declared = split_top_level(rest, "=")[0].strip()
            parameters.append({
                "name": parameter,
                "type": declared,
                "default": has_default,
                "functional": "->" in declared,
            })
        declarations[name] = parameters
    return declarations


def find_calls(source, name):
    """Every call to ``name``, as (line number, argument text, has_trailing_lambda)."""
    calls = []
    for match in re.finditer(r"(?<![\w.])" + name + r"\s*\(", source):
        # Skip the declaration itself.
        prefix = source[max(0, match.start() - 5):match.start()]
        if prefix.endswith("fun "):
            continue
        start = match.end() - 1
        depth, index = 0, start
        while index < len(source):
            if source[index] == "(":
                depth += 1
            elif source[index] == ")":
                depth -= 1
                if depth == 0:
                    break
            index += 1
        if index >= len(source):
            continue
        arguments = source[start + 1:index]
        after = source[index + 1:index + 40].lstrip()
        trailing = after.startswith("{")
        calls.append((source[:match.start()].count("\n") + 1, arguments, trailing))
    return calls


def main():
    if not os.path.isfile(COMPONENTS):
        print("cannot find SettingsComponents.kt")
        return 1
    declarations = parse_declarations(strip_comments(open(COMPONENTS, encoding="utf-8").read()))
    if not declarations:
        print("parsed no declarations, which means this check is not checking anything")
        return 1

    problems = []
    checked = 0
    for path in glob.glob(os.path.join(SRC, "**", "*.kt"), recursive=True):
        source = strip_comments(open(path, encoding="utf-8").read())
        for name, parameters in declarations.items():
            for line, arguments, trailing in find_calls(source, name):
                if os.path.abspath(path) == os.path.abspath(COMPONENTS):
                    continue
                checked += 1
                supplied = set()
                positional = 0
                for argument in split_top_level(arguments):
                    named = re.match(r"^(\w+)\s*=(?!=)", argument)
                    if named and named.group(1) in {p["name"] for p in parameters}:
                        supplied.add(named.group(1))
                        continue
                    if positional < len(parameters):
                        parameter = parameters[positional]
                        supplied.add(parameter["name"])
                        # A callback landing in a slot that does not take one is the same
                        # mistake seen from the other side, and gives a worse error message.
                        looks_functional = ("::" in argument
                                            or argument.startswith("{")
                                            or argument.endswith("}"))
                        if looks_functional and not parameter["functional"]:
                            problems.append(
                                "%s:%d %s: argument %d looks like a callback but lands on "
                                "'%s: %s'" % (os.path.relpath(path, ROOT), line, name,
                                              positional + 1, parameter["name"],
                                              parameter["type"]))
                    else:
                        problems.append(
                            "%s:%d %s: %d positional arguments for %d parameters"
                            % (os.path.relpath(path, ROOT), line, positional + 1,
                               name, len(parameters)))
                    positional += 1
                if trailing and parameters:
                    supplied.add(parameters[-1]["name"])
                for parameter in parameters:
                    if not parameter["default"] and parameter["name"] not in supplied:
                        problems.append(
                            "%s:%d %s: no value passed for parameter '%s'"
                            % (os.path.relpath(path, ROOT), line, name, parameter["name"]))

    for problem in sorted(set(problems)):
        print("FAIL:", problem)
    if problems:
        print("%d problems across %d call sites" % (len(set(problems)), checked))
        return 1
    print("ok: %d calls to %d settings components, every required parameter supplied"
          % (checked, len(declarations)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
