#!/bin/sh
# Drives the shipped ControlPages, which is plain Java with no Android imports so that this
# compiles the real class rather than a transcription of it (16.25).
#
# The question behind every check here is the same: can an arrangement of pages take a control
# off the screen that the player did not take off themselves. Two assertions carry most of it.
# anOldLayoutIsUntouched sweeps mask 0, which is what every layout ever saved deserialises to,
# across every page count and page; and theLastPageCannotBeUnticked covers the trap built into
# a scheme where zero means "every page", since a control unticked everywhere would otherwise
# canonicalise straight back to being on all of them.
#
# Mutations this is known to catch:
#   - onPage returning false for an unknown page    -> outOfRangeShowsRatherThanHides
#   - visible() filtering the ungrabbed branch too  -> theMenuSetIsNeverFilteredByPage
#   - dropping the mask == ALL_PAGES short circuit  -> anOldLayoutIsUntouched
#   - dropping the resolved == full canonicalisation-> tickingEveryPageIsTheSameAsAll
#   - dropping the resolved == 0 guard              -> theLastPageCannotBeUnticked
#   - resolveTarget clamping instead of staying put -> aTargetPastTheEndStaysPut
#   - resolveTarget reading targetPage as 0 based   -> aTargetNamesItsPage
#   - deadEndPages ignoring displayInGame           -> everyControlIsReachableOnSomePage
#   - deadEndPages counting a self target as an exit-> aSelfTargetIsADeadEnd
set -e
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -nowarn -source 8 -target 8 -d "$out" \
  "$root/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/customcontrols/ControlPages.java" \
  "$here/Drive.java" 2>&1 | grep -v 'bootstrap class path' || true
java -cp "$out" Drive
