#!/usr/bin/env python3
"""Catch the string resource mistakes aapt refuses to compile, before CI does.

Written after a push failed with six "Can not extract resource from ParsedResource"
errors, which is aapt's way of saying a string holds a bare apostrophe. The message
names no string and no line, so the only way to find them from the log is to count
them and guess; the failure costs a whole CI cycle to learn nothing.

It mirrors aapt's own severities rather than inventing stricter ones, which is the
difference between a check that gets run and one that gets skipped:

  * FAIL on a bare ' in a string body. That is a compile error; it must be \\'.
    This is the one that actually bit, six times in one commit.
  * FAIL on an em dash. Not aapt's rule, this project's: section 15 of the handbook.
  * WARN on several non-positional %conversions in one string. aapt warns rather
    than refusing, and thirteen strings inherited from upstream have it, so failing
    here would mean a check that is red before anybody touches anything. It is
    still worth printing: in a NEW string it is usually a per cent sign meant
    literally, which getString will try to format.

A bare " is deliberately not checked at all. The documentation says to escape it
and aapt accepts it, and three shipped strings rely on that.

Deliberately NOT a general linter: it reads values/strings.xml only, which is the
file this project writes (translations are upstream's, §15), and it reports every
offender rather than stopping at the first, because they come in batches.
"""
import re
import sys
import xml.etree.ElementTree as ET

PATH = 'app_pojavlauncher/src/main/res/values/strings.xml'

# A conversion aapt would recognise. Positional ones carry an argument index.
CONVERSION = re.compile(r'%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]')


def unescaped(text, ch):
    """Occurrences of ch not preceded by a backslash."""
    return [i for i, c in enumerate(text) if c == ch and (i == 0 or text[i - 1] != '\\')]


def main():
    root = ET.parse(PATH).getroot()
    problems = []
    warnings = []
    checked = 0
    # iter() rather than the root's own children: a <string-array>'s items are nested, and
    # a bare apostrophe in one of those is the same aapt error as in any other string.
    for element in root.iter():
        if element.tag not in ('string', 'item'):
            continue
        name = element.get('name') or '(item)'
        # itertext, so a string carrying <b> or <xliff:g> is read whole.
        text = ''.join(element.itertext())
        if text is None:
            continue
        checked += 1
        if unescaped(text, "'"):
            problems.append(name + ': bare apostrophe, write \\\'')
        # Section 15 has banned these in user-facing copy since it was written, and nothing
        # enforced it. Written at the moment the count was zero, so it is green on the first
        # run: a check a file already fails is one somebody suppresses instead of using.
        # The en dash is deliberately allowed, "3-16 characters" being a numeric range.
        if '\u2014' in text:
            problems.append(name + ': em dash; a full stop, a comma or a colon says the same')
        found = CONVERSION.findall(text)
        positional = [f for f in found if f[0]]
        if len(found) > 1 and len(positional) != len(found):
            warnings.append(
                name + ': ' + str(len(found)) + ' format conversions and not all are '
                'positional; write %1$s style, or %% for a literal per cent')
    for w in warnings:
        print('warn ' + w)
    for p in problems:
        print('FAIL ' + p)
    print('checked ' + str(checked) + ' strings in ' + PATH + ', '
          + str(len(warnings)) + ' warning(s)')
    if problems:
        print(str(len(problems)) + ' problem(s)')
        return 1
    print('ok')
    return 0


if __name__ == '__main__':
    sys.exit(main())
