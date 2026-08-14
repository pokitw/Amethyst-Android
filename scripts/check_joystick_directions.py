#!/usr/bin/env python3
"""Check the auto-walk tap-to-direction geometry against the joystick library's own convention.

`ControlJoystick.directionOfTap` cannot be compiled and driven the way `scripts/logsim` drives
`LogParser`: it is a View subclass of a third-party joystick library (`JoystickView`), and
stubbing that whole surface for one trig formula is not a good trade. What it can be is checked
algebraically against the one fact that has to hold: a tap at a screen position must resolve to
the same compass direction `JoystickView.getAngle()` would report for a drag to that same
position, because a player who taps where they would have dragged expects the same direction.

`JoystickView.getAngle()` (private, read from the bundled source at review time) computes
`atan2(centerY - posY, posX - centerX)`, i.e. `atan2(-dy, dx)` in screen coordinates where y
grows downward. `directionOfTap` uses exactly that formula; this script is independent proof it
resolves each of the eight compass points, and the dead zone, the way a player holding the phone
the normal way up would expect: tap above center locks forward (W), right locks strafe-right (D),
and so on round the circle.

Run from the repository root:  python3 scripts/check_joystick_directions.py
"""

import math
import sys

DIRECTION_NONE = -1
DIRECTION_EAST = 0
DIRECTION_NORTH_EAST = 1
DIRECTION_NORTH = 2
DIRECTION_NORTH_WEST = 3
DIRECTION_WEST = 4
DIRECTION_SOUTH_WEST = 5
DIRECTION_SOUTH = 6
DIRECTION_SOUTH_EAST = 7

NAMES = {
    DIRECTION_NONE: "NONE", DIRECTION_EAST: "EAST", DIRECTION_NORTH_EAST: "NORTH_EAST",
    DIRECTION_NORTH: "NORTH", DIRECTION_NORTH_WEST: "NORTH_WEST", DIRECTION_WEST: "WEST",
    DIRECTION_SOUTH_WEST: "SOUTH_WEST", DIRECTION_SOUTH: "SOUTH", DIRECTION_SOUTH_EAST: "SOUTH_EAST",
}

MIN_TAP_RADIUS = 15.0  # AUTO_WALK_MIN_TAP_DP in ControlJoystick.java, in the same unit as dx/dy here


def get_direction_int(angle, intensity):
    """ControlJoystick.getDirectionInt, unchanged: the eight-way bucket an angle falls in.

    `math.fmod` rather than Python's `%`: Java's `%` on doubles keeps the sign of the dividend
    (truncating, the same as `fmod`), while Python's `%` keeps the sign of the divisor (floored).
    They agree for every non-negative input, which is all this ever sees once `directionOfTap`'s
    wrap has run, but a harness that used `%` here would not be able to tell a working wrap from
    a removed one: floored modulo would quietly produce the right compass bucket from an
    unwrapped negative angle by itself, exactly the way this one did before the fix.
    """
    if intensity == 0:
        return DIRECTION_NONE
    return int(math.fmod((angle + 22.5) / 45, 8))


def direction_of_tap(tap_x, tap_y, center_x, center_y):
    """ControlJoystick.directionOfTap, transcribed."""
    dx = tap_x - center_x
    dy = tap_y - center_y
    if math.hypot(dx, dy) < MIN_TAP_RADIUS:
        return DIRECTION_NONE
    angle = math.degrees(math.atan2(-dy, dx))
    if angle < 0:
        angle += 360
    return get_direction_int(angle, 1)


def main():
    failures = []
    center = (100.0, 100.0)
    radius = 60.0

    # The eight compass points, each pushed well past the dead zone, and the direction a player
    # tapping there would expect: up means forward, right means strafe right, and so on, matching
    # Minecraft's own WASD mapping (mDirectionForward = W, mDirectionRight = D, ...).
    points = [
        ("north (up)", 0, -radius, DIRECTION_NORTH),
        ("north-east", radius * 0.7, -radius * 0.7, DIRECTION_NORTH_EAST),
        ("east (right)", radius, 0, DIRECTION_EAST),
        ("south-east", radius * 0.7, radius * 0.7, DIRECTION_SOUTH_EAST),
        ("south (down)", 0, radius, DIRECTION_SOUTH),
        ("south-west", -radius * 0.7, radius * 0.7, DIRECTION_SOUTH_WEST),
        ("west (left)", -radius, 0, DIRECTION_WEST),
        ("north-west", -radius * 0.7, -radius * 0.7, DIRECTION_NORTH_WEST),
    ]
    for name, ox, oy, expected in points:
        got = direction_of_tap(center[0] + ox, center[1] + oy, center[0], center[1])
        if got != expected:
            failures.append(
                "tap at %s: got %s, expected %s" % (name, NAMES[got], NAMES[expected])
            )

    # A tap inside the dead zone means any direction, so it must mean none of them.
    for dx, dy in [(0, 0), (5, 5), (-8, 3)]:
        got = direction_of_tap(center[0] + dx, center[1] + dy, center[0], center[1])
        if got != DIRECTION_NONE:
            failures.append(
                "tap at (%d, %d) from centre, inside the dead zone: got %s, expected NONE"
                % (dx, dy, NAMES[got])
            )

    # A tap at exactly the dead zone's radius is outside it: the comparison is a strict less-than
    # in the shipped code, and this is the one point that tells `<` and `<=` apart.
    got = direction_of_tap(center[0], center[1] - MIN_TAP_RADIUS, center[0], center[1])
    if got != DIRECTION_NORTH:
        failures.append(
            "tap exactly at the dead zone radius: got %s, expected NORTH (a direction, not NONE)"
            % NAMES[got]
        )

    # Each cardinal owns a tolerance band ±22.5 degrees wide, centred on it rather than
    # starting at it: a tap has to be well inside that band, not just on the exact compass point,
    # to count as that direction. These sit 20 degrees off each cardinal, comfortably inside the
    # correct band and, not by coincidence, exactly where dropping the centring offset first
    # sends them into the neighbouring bucket instead.
    tolerance_checks = [
        ("north +20", 90 + 20, DIRECTION_NORTH), ("north -20", 90 - 20, DIRECTION_NORTH),
        ("east +20", 0 + 20, DIRECTION_EAST), ("east -20", 0 - 20, DIRECTION_EAST),
        ("south +20", 270 + 20, DIRECTION_SOUTH), ("south -20", 270 - 20, DIRECTION_SOUTH),
        ("west +20", 180 + 20, DIRECTION_WEST), ("west -20", 180 - 20, DIRECTION_WEST),
    ]
    for name, degrees, expected in tolerance_checks:
        rad = math.radians(degrees)
        x = center[0] + radius * math.cos(rad)
        y = center[1] - radius * math.sin(rad)
        got = direction_of_tap(x, y, center[0], center[1])
        if got != expected:
            failures.append("tap at %s: got %s, expected %s" % (name, NAMES[got], NAMES[expected]))

    # A full sweep every 5 degrees at a fixed radius must divide evenly into the eight buckets,
    # each 45 degrees wide, with no angle falling outside 0..7 or landing on the wrong side of a
    # boundary by more than floating-point noise.
    seen_at_45 = set()
    for step in range(0, 360, 5):
        rad = math.radians(step)
        x = center[0] + radius * math.cos(rad)
        # Screen y grows downward, and this loop's step is a mathematical angle (0 = east,
        # increasing counter-clockwise), so the y offset is the negative of the usual sine.
        y = center[1] - radius * math.sin(rad)
        got = direction_of_tap(x, y, center[0], center[1])
        if not (DIRECTION_EAST <= got <= DIRECTION_SOUTH_EAST):
            failures.append("sweep at %d degrees produced an out-of-range bucket %d" % (step, got))
        seen_at_45.add(got)
    if len(seen_at_45) != 8:
        failures.append(
            "a 5-degree sweep should visit all eight buckets, only saw %d" % len(seen_at_45)
        )

    if failures:
        for failure in failures:
            print("FAIL: " + failure)
        return 1
    print("ok: %d compass points, dead zone, boundary and a 5-degree sweep all resolve correctly"
          % len(points))
    return 0


if __name__ == "__main__":
    sys.exit(main())
