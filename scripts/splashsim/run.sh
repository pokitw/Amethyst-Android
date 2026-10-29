#!/bin/sh
# Drives the shipped SplashPicker, which is plain Java with no Android imports so that this can
# compile the real class rather than a transcription of it (16.25).
#
# Mutations this is known to catch:
#   - deleting the seam correction in shuffle()           -> neverTwiceInARow, twoLinesAlternate
#   - starting mCursor at 0 instead of past the end       -> a reproducible identity first bag
#   - using nextInt(mBag.length) in the Fisher-Yates loop -> everyLineBeforeAnyRepeat survives it,
#     because a biased shuffle is still a permutation; that one is a fairness bug this does not
#     claim to see, and it is stated rather than hidden.
#   - returning mBag[mCursor] without advancing           -> neverTwiceInARow
#   - dropping the count <= 0 guard                       -> emptyTableIsNotACrash
#   - handing out the bag before the first shuffle        -> theFirstLineVariesByLaunch
#
# A fifth mutation, removing a `length == 1` early return, was not caught, and that was the
# right answer: with one entry the shuffle loop does not run and the seam swap exchanges index
# zero with itself, so the branch was dead and has been deleted rather than covered.
set -e
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -nowarn -source 8 -target 8 -d "$out" \
  "$root/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/splash/SplashPicker.java" \
  "$here/Drive.java" 2>&1 | grep -v 'bootstrap class path' || true
java -cp "$out" Drive
python3 "$here/check_lines.py"
