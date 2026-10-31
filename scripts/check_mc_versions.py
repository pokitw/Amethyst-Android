#!/usr/bin/env python3
"""Check the Minecraft version matcher in ui/mods/ModTarget.kt.

The pattern is pulled out of the shipped Kotlin and re-run here, which is the idiom
check_crash_rules.py already uses: the regex subset involved is shared between the two
languages, there is no arithmetic to inherit (16.26), and a Python re-implementation of the
surrounding segment logic would be a check of the copy (16.25) rather than of the code.

What this exists for: the pattern used to require a leading "1.", which is an assumption about
Mojang's naming with nothing checking it. A profile numbered any other way read as having no
Minecraft version, and because a null here means "do not filter" rather than "filter wrongly",
the mod browser's version filter, the mod compatibility check and the migration planner all went
quiet without any of them reporting anything. Widening it is only safe because matching is
anchored to a whole segment and NeoForge ids never reach the matcher, so both of those are
asserted too.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/ui/mods/ModTarget.kt"

failures = []
checks = 0


def check(condition, message):
    global checks
    checks += 1
    if not condition:
        failures.append(message)


text = SOURCE.read_text(encoding="utf-8")

found = re.search(r'private val MC_VERSION = Regex\("""(.+?)"""\)', text)
if not found:
    sys.exit("could not find MC_VERSION in %s" % SOURCE)
pattern = re.compile(found.group(1))

# A version somebody could actually be running. The matcher is used with .matches(), which is a
# whole-string match in Kotlin, so fullmatch is the faithful translation.
ACCEPT = [
    "1.20.1", "1.20", "1.7.10", "1.21.9", "1.8.9", "1.16.5",
    # Not pinned to a leading 1: the whole point of the widening. Whatever Mojang numbers a
    # release, a dotted version is read rather than discarded.
    "26.2", "26.3", "26.3.1", "2.0", "25.1",
]

# Each of these is something that appears in a real version id and is not a Minecraft version.
# The first two are the hazard the anchoring exists for: Forge and NeoForge build numbers.
REJECT = [
    "1614", "47", "21", "",
    # A Fabric loader version, which is what would be read as the game's version if a Fabric id
    # ever arrived without its trailing Minecraft version.
    "0.16.5", "0.15.7", "0.1", "0.16.5.1",
    # Whole ids: the matcher sees segments, never the id, and must refuse one if it ever does.
    "1.20.1-forge-47.2.0", "fabric-loader-0.15.7-1.20.1", "neoforge-21.1.66",
    "quilt-loader-0.23.1-1.20.1",
    # Snapshots and the pre-1.0 names, which have never matched and still must not: they fall
    # through to inheritsFrom in the manifest, which is the honest answer for them.
    "26w10a", "23w33a", "b1.7.3", "a1.2.6", "rd-132211", "inf-20100618", "c0.30",
    # Four components is not a shape Minecraft uses, and accepting it would mean accepting a
    # library version somewhere.
    "1.2.3.4",
]

for version in ACCEPT:
    check(pattern.fullmatch(version) is not None,
          "%r is a Minecraft version and was refused" % version)
for version in REJECT:
    check(pattern.fullmatch(version) is None,
          "%r is not a Minecraft version and was accepted" % version)

# The widening rests on two things upstream of the matcher, so neither may quietly go away.
#
# Every NeoForge id contains a dotted build number that now looks exactly like a version
# ("21.1.66"), and the only thing stopping it being read as one is that NeoForge ids never reach
# the matcher at all. Before the widening this line was belt and braces; it is now load bearing.
check('id.startsWith("neoforge") -> emptyList()' in text,
      "the NeoForge early exit is gone, so a build number like 21.1.66 now reads as a "
      "Minecraft version")
# And the match has to stay whole-segment. `contains` or `find` here would pull "1.66" out of
# neoforge-21.1.66 and "0.15" out of a Fabric loader version.
check("MC_VERSION.matches(" in text,
      "the matcher is no longer applied to a whole segment, so a version can be found inside a "
      "build number")

print("checked %d version strings against the shipped matcher" % checks)
if failures:
    for failure in failures:
        print("FAIL " + failure)
    sys.exit("%d failure(s)" % len(failures))
print("Minecraft version matcher OK")
