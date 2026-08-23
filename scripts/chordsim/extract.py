#!/usr/bin/env python3
"""Lift the three shipped functions this bug lives in, verbatim, into a compilable file.

Verbatim rather than retyped: input_bridge_v3.c pulls in JNI, EGL, SDL and the whole Android
logging stack, so it cannot be compiled here as it stands, and a hand-copied version would be a
harness checking its own arithmetic rather than the launcher's. This is the same trick repeatsim
uses on ControlData.
"""
import re, sys, pathlib

src = pathlib.Path(sys.argv[1]).read_text()
out = pathlib.Path(sys.argv[2])

def grab(pattern, what):
    m = re.search(pattern, src, re.S)
    if not m:
        sys.exit("could not find " + what + " in the shipped file")
    return m.group(0)

parts = [
    grab(r'#define max\(a,b\).*?_a > _b \? _a : _b; \}\)', 'the max macro'),
    grab(r'void sendData\(int type, int i1, int i2, int i3, int i4\) \{.*?\n\}', 'sendData'),
    grab(r'void pojavPumpEvents\(void\* window\) \{.*?\n\}', 'pojavPumpEvents'),
    grab(r'void critical_send_key\(jint key, jint scancode, jint action, jint mods\) \{.*?\n\}',
         'critical_send_key'),
]

out.write_text('#include "shim.h"\n\n' + "\n\n".join(parts) + "\n")
print("lifted %d functions verbatim" % (len(parts) - 1))
