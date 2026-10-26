#!/usr/bin/env python3
"""Count printf conversions against arguments in every LOG call under jni/.

`sdl_hook.c` shipped with

    LOGI("Successfully initialized SDL hooks, stubs: %p, %p\\n", stub_SDL_InitSubSystem);

two conversions and one argument. LOGI is `__android_log_print`, which is varargs, so this reads
an uninitialised vararg slot on the hook-init path at every single game launch. It is undefined
behaviour that happens to be survivable on arm64 because the register holding the absent argument
usually contains something printable, which is exactly why it went unnoticed.

The compiler would have caught it: `__android_log_print` is declared with
`__attribute__((format(printf, 3, 4)))` in the NDK, so `-Wformat` warns. It is not an error, and a
warning in an ndkBuild log nobody reads is a warning nobody reads. This makes it a failure.

Deliberately a counter and not a type checker. Matching conversion specifiers to argument types
needs a C parser, and the bug class worth catching here is the arity mismatch, which is both the
one that reads uninitialised memory and the one a human eye slides over. Anything it cannot count
confidently it skips and reports as skipped, so the number of checks is honest about coverage
rather than quietly shrinking.
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
JNI = ROOT / "app_pojavlauncher/src/main/jni"

LOG_MACROS = ("LOGI", "LOGW", "LOGE", "LOGD", "LOGV", "LOGF",
              "__android_log_print", "printf", "fprintf")

# %% is a literal percent and takes no argument; %*d consumes an extra argument for the width.
CONVERSION = re.compile(r'%(?:%|[-+ #0]*(\*?)(?:\d+)?(?:\.(?:\d+|\*))?(?:hh|h|ll|l|L|z|j|t)?[diouxXeEfgGaAcspn])')

failures = []
skipped = []
checks = 0


def check(ok, message):
    global checks
    checks += 1
    if not ok:
        failures.append(message)


def split_top_level(args: str):
    """Split a C argument list on top-level commas."""
    out, depth, current, i = [], 0, [], 0
    while i < len(args):
        ch = args[i]
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        elif ch == '"':
            current.append(ch)
            i += 1
            while i < len(args) and args[i] != '"':
                if args[i] == '\\':
                    current.append(args[i])
                    i += 1
                if i < len(args):
                    current.append(args[i])
                    i += 1
            if i < len(args):
                current.append(args[i])
                i += 1
            continue
        elif ch == "'":
            current.append(ch)
            i += 1
            while i < len(args) and args[i] != "'":
                if args[i] == '\\':
                    current.append(args[i])
                    i += 1
                if i < len(args):
                    current.append(args[i])
                    i += 1
            if i < len(args):
                current.append(args[i])
                i += 1
            continue
        elif ch == ',' and depth == 0:
            out.append(''.join(current))
            current = []
            i += 1
            continue
        current.append(ch)
        i += 1
    out.append(''.join(current))
    return [a.strip() for a in out]


def literal_format(arg: str):
    """Return the concatenated string literal if the whole argument is one, else None."""
    pieces = re.findall(r'"((?:[^"\\]|\\.)*)"', arg)
    if not pieces:
        return None
    # Anything outside the literals other than whitespace means it is not a plain literal
    # (a macro, a ternary, a variable), and the count cannot be trusted.
    if re.sub(r'"(?:[^"\\]|\\.)*"', '', arg).strip():
        return None
    return ''.join(pieces)


def count_conversions(fmt: str) -> int:
    total = 0
    for m in CONVERSION.finditer(fmt):
        if m.group(0) == '%%':
            continue
        total += 1
        if m.group(1) == '*':      # %*d takes its width from an argument too
            total += 1
    return total


def main():
    if not JNI.is_dir():
        print("jni directory not found at %s" % JNI)
        return 1

    sources = sorted(list(JNI.rglob("*.c")) + list(JNI.rglob("*.h")) + list(JNI.rglob("*.cpp")))
    # Vendored third party code is not ours to police.
    sources = [p for p in sources
               if not any(part in ("bytehook", "shady", "lwjgl", "SDL3", "SDL2") for part in p.parts)]

    call = re.compile(r'\b(' + '|'.join(LOG_MACROS) + r')\s*\(')
    for path in sources:
        text = path.read_text(encoding="utf-8", errors="replace")
        for m in call.finditer(text):
            name = m.group(1)
            # Walk to the matching close paren.
            i = m.end()
            depth, start = 1, i
            while i < len(text) and depth:
                if text[i] == '"':
                    i += 1
                    while i < len(text) and text[i] != '"':
                        i += 2 if text[i] == '\\' else 1
                elif text[i] == '(':
                    depth += 1
                elif text[i] == ')':
                    depth -= 1
                i += 1
            if depth:
                continue
            args = split_top_level(text[start:i - 1])
            # __android_log_print(prio, tag, fmt, ...) and fprintf(stream, fmt, ...) put the
            # format later in the list than the LOGx macros do.
            if name == "__android_log_print":
                fmt_index = 2
            elif name == "fprintf":
                fmt_index = 1
            else:
                fmt_index = 0
            if len(args) <= fmt_index:
                continue
            fmt = literal_format(args[fmt_index])
            line = text[:m.start()].count('\n') + 1
            where = "%s:%d %s" % (path.relative_to(ROOT), line, name)
            if fmt is None:
                skipped.append(where + " (format is not a plain literal)")
                continue
            if '%' not in fmt:
                continue
            wanted = count_conversions(fmt)
            given = len(args) - fmt_index - 1
            check(wanted == given,
                  "%s wants %d argument(s) and is given %d: %r"
                  % (where, wanted, given, fmt[:70]))

    print("%d checks, %d skipped" % (checks, len(skipped)))
    for s in skipped:
        print("  skipped: " + s)
    for f in failures:
        print("FAIL: " + f)
    if failures:
        print("%d FAILURES" % len(failures))
        return 1
    print("all passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
