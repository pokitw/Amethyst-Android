#!/bin/sh
# Compile the shipped mod metadata parser and drive it with real jars.
#
# Four mutually incompatible metadata dialects, two version grammars, and a folder full of jars a
# stranger sent. Every one of those is a chance to say "this mod will not work" about a mod that
# works perfectly, which is the failure that matters: a missed warning leaves a player exactly
# where they already were, a false one sends them to break their own install.
#
# The harness writes actual jars with ZipOutputStream and reads them back through the shipped
# classes, the way nbtsim writes a real level.dat. The metadata inside them is copied from the
# shape mods really ship rather than from what the parser expects, because a fixture written from
# the same assumption as the code confirms the assumption instead of testing it (16.20).
#
# Mutations this is known to catch:
#
#   * the format order is swapped                (a Quilt jar reports as Fabric, a NeoForge jar as
#                                                 Forge, and both are then judged by the wrong
#                                                 loader's version grammar)
#   * provides is not read                       (a missing dependency on nearly every Fabric mod
#                                                 installed, because Fabric API is forty modules)
#   * platform ids are not excluded              (every single mod reports a missing dependency)
#   * a disabled jar satisfies a dependency      (turning Fabric API off silently stops warning
#                                                 about the mods that needed it)
#   * NeoForge's type= is not read               (every NeoForge dependency becomes required or
#                                                 optional, and both are silent)
#   * type="incompatible" reads as a requirement (a mod is reported as needing what it refuses to
#                                                 run beside)
#   * an optional dependency reads as required   (warnings about things nobody has to install)
#   * the Fabric array form reads as a conjunction  (unsatisfiable, so every mod written that way
#                                                    reports a conflict)
#   * the OR separator is a space                (">=1.20.1 <1.21" splits into alternatives and
#                                                 passes the exact version it excludes)
#   * maven unions split on every comma          ("[1.20.1,1.21)" becomes two broken halves)
#   * an exclusive maven bound becomes inclusive (off by one release at the boundary)
#   * a bare maven version reads as equality     (condemns a large number of fine Forge mods)
#   * a bare semver version reads as a prefix    ("1.20" quietly accepts 1.20.6)
#   * an unparseable version returns CONFLICTS   (snapshots and pre-releases condemn everything)
#   * the TOML block description is not skipped  (a bracket or a key inside a mod's own prose
#                                                 invents a dependency it never declared)
#   * Quilt stops loading Fabric mods            (every Fabric mod on a Quilt profile is refused)
#   * the caret ignores SemVer's zero-major rule (^0.2.3 wrongly admits 0.3)
#   * an unknown alternative stops poisoning     (an OR where one branch was not understood
#     the OR                                      concludes a conflict it cannot know)
#
# One input is guarded in three places and no SINGLE removal is observable: a null profile
# version, which a snapshot profile really produces. ModGraph short-circuits it, VersionPredicate
# refuses it, and the version parser returns null for it. Removing all three IS caught. The two
# outer guards are kept even so, because each states the intent at the level it belongs to, and
# "a snapshot profile checks nothing" is exactly the rule a later reader would otherwise delete.
#
# Needs the Gson jar the app itself compiles against, so the parse here is the phone's parse.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

if [ -z "$GSON_JAR" ]; then
    GSON_JAR=$(ls "$ROOT"/app_pojavlauncher/libs/gson-*.jar 2>/dev/null | head -1)
fi
if [ -z "$GSON_JAR" ]; then
    GSON_JAR=$(find "$HOME/.gradle" "$HOME/.m2" -name 'gson-*.jar' 2>/dev/null \
        | grep -v sources | head -1)
fi
if [ -z "$GSON_JAR" ] || [ ! -f "$GSON_JAR" ]; then
    echo "skipping: no gson jar found (set GSON_JAR to one)"
    exit 0
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/jars"

# No Android imports in any of the three, on purpose, so nothing has to be stubbed out.
javac -nowarn -source 8 -target 8 \
    -cp "$GSON_JAR" \
    -sourcepath "$SRC:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/modmeta/ModRequirements.java" \
    "$SRC/net/kdt/pojavlaunch/modmeta/VersionPredicate.java" \
    "$SRC/net/kdt/pojavlaunch/modmeta/ModGraph.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes:$GSON_JAR" net.kdt.pojavlaunch.modmeta.Harness "$OUT/jars"
