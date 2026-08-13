#!/usr/bin/env python3
"""
Build the superflat world the control test launches into.

Written as a generator rather than committed as a mystery blob for the same reason the control
texture packs are: a level.dat is a rule, not a picture, and every field in it is a decision that
somebody later has to be able to argue with. Run it and the asset is rebuilt; read it and the
world's whole configuration is one screenful.

The output is assets/testworld/level.dat, gzipped NBT, which the launcher copies into the test
profile's saves folder. Minecraft creates everything else in the folder on first load.
"""
import gzip
import os
import struct
import sys

# --- the NBT writer. Enough of the format to write one level.dat, and no more. ---

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE = 0, 1, 2, 3, 4, 5, 6
BYTE_ARRAY, STRING, LIST, COMPOUND = 7, 8, 9, 10


class Tag:
    def __init__(self, kind, value):
        self.kind = kind
        self.value = value


def b(v):  return Tag(BYTE, v)
def i(v):  return Tag(INT, v)
def l(v):  return Tag(LONG, v)
def f(v):  return Tag(FLOAT, v)
def d(v):  return Tag(DOUBLE, v)
def s_(v): return Tag(STRING, v)
def c(v):  return Tag(COMPOUND, v)
def lst(kind, items): return Tag(LIST, (kind, items))


def write_string(out, text):
    raw = text.encode("utf-8")
    out += struct.pack(">H", len(raw))
    out += raw


def write_payload(out, tag):
    k, v = tag.kind, tag.value
    if k == BYTE:     out += struct.pack(">b", v)
    elif k == SHORT:  out += struct.pack(">h", v)
    elif k == INT:    out += struct.pack(">i", v)
    elif k == LONG:   out += struct.pack(">q", v)
    elif k == FLOAT:  out += struct.pack(">f", v)
    elif k == DOUBLE: out += struct.pack(">d", v)
    elif k == STRING: write_string(out, v)
    elif k == LIST:
        kind, items = v
        out += struct.pack(">b", kind)
        out += struct.pack(">i", len(items))
        for item in items:
            write_payload(out, item)
    elif k == COMPOUND:
        for name, child in v.items():
            out += struct.pack(">b", child.kind)
            write_string(out, name)
            write_payload(out, child)
        out += struct.pack(">b", END)
    else:
        raise ValueError("unwritable tag kind %d" % k)


def encode(root_name, root):
    out = bytearray()
    out += struct.pack(">b", COMPOUND)
    write_string(out, root_name)
    write_payload(out, root)
    return bytes(out)


# --- the world itself ---

# 1.20.1. The oldest release that can be opened straight into with --quickPlaySingleplayer, which
# is the whole reason the test launch is not pointed at something older and lighter still.
DATA_VERSION = 3465
VERSION_NAME = "1.20.1"

# Bedrock, two dirt, grass. The thinnest floor that is still a floor: one layer of bedrock stops
# anything falling out of the world, and the grass is what makes it obvious which way is up.
LAYERS = [
    c({"block": s_("minecraft:bedrock"), "height": i(1)}),
    c({"block": s_("minecraft:dirt"), "height": i(2)}),
    c({"block": s_("minecraft:grass_block"), "height": i(1)}),
]

# Everything here is chosen to cost the device as little as possible while still being a world you
# can walk around and hit things in. Values are strings because that is how Minecraft stores rules.
GAME_RULES = {
    "doDaylightCycle": "false",     # noon forever: no lighting recalculation, and a bright world
    "doWeatherCycle": "false",      # rain is particles, and particles are frames
    "doMobSpawning": "false",       # nothing to render, nothing to tick, nothing to kill you
    "doFireTick": "false",
    "randomTickSpeed": "0",         # no grass spread, no crop growth, no block updates at all
    "mobGriefing": "false",
    "doEntityDrops": "false",
    "doTileDrops": "false",
    "announceAdvancements": "false",
    "doTraderSpawning": "false",
    "doPatrolSpawning": "false",
    "doInsomnia": "false",
    "spawnRadius": "0",             # always the same spot, so the world never generates elsewhere
    "keepInventory": "true",
    "fallDamage": "false",
    "showDeathMessages": "false",
}


def flat_generator():
    return c({
        "type": s_("minecraft:flat"),
        "settings": c({
            "layers": lst(COMPOUND, LAYERS),
            "biome": s_("minecraft:plains"),
            # Both off: structures and lakes are the two things a superflat can still spend
            # chunk generation on, and neither helps anybody test a button.
            "features": b(0),
            "lakes": b(0),
        }),
    })


def dimensions():
    """
    The overworld, and only the overworld.

    <b>The first draft declared the nether and the end too, and that is what broke it.</b> Their
    generator configurations are the fiddliest part of this format, they were written from memory
    against a Minecraft that is not in this container, and one wrong field in either fails the
    whole WorldGenSettings codec rather than just that dimension, which takes the entire world
    down with it. A world that will not load looks exactly like a world that was never created.

    Minecraft fills in whatever dimensions this does not declare from the datapack defaults, so
    naming the two that will never be visited bought nothing at all and cost the feature.
    """
    return c({
        "minecraft:overworld": c({
            "type": s_("minecraft:overworld"),
            "generator": flat_generator(),
        }),
    })


def level(name):
    data = {
        "DataVersion": i(DATA_VERSION),
        "version": i(19133),
        "LevelName": s_(name),
        # Creative and peaceful: nothing can hurt you, nothing needs killing, and flying is one of
        # the things worth having a button for.
        "GameType": i(1),
        "Difficulty": b(0),
        "DifficultyLocked": b(0),
        "hardcore": b(0),
        "allowCommands": b(1),
        # False on purpose. Minecraft finishes setting the world up on first load, which is the
        # path a freshly created world takes anyway, so this asks for the ordinary thing.
        "initialized": b(0),
        "Time": l(0),
        "DayTime": l(6000),          # noon
        "LastPlayed": l(0),
        "SpawnX": i(0), "SpawnY": i(5), "SpawnZ": i(0), "SpawnAngle": f(0.0),
        "raining": b(0), "thundering": b(0),
        "rainTime": i(1000000), "thunderTime": i(1000000), "clearWeatherTime": i(1000000),
        "WasModded": b(1),
        "BorderCenterX": d(0.0), "BorderCenterZ": d(0.0),
        "BorderSize": d(256.0),      # a small border, so nothing generates that nobody will visit
        "BorderSizeLerpTarget": d(256.0), "BorderSizeLerpTime": l(0),
        "BorderSafeZone": d(5.0),
        "BorderWarningBlocks": d(5.0), "BorderWarningTime": d(15.0),
        "BorderDamagePerBlock": d(0.0),
        "Version": c({
            "Id": i(DATA_VERSION),
            "Name": s_(VERSION_NAME),
            "Snapshot": b(0),
            "Series": s_("main"),
        }),
        "GameRules": c({k: s_(v) for k, v in GAME_RULES.items()}),
        "WorldGenSettings": c({
            "seed": l(0),
            "generate_features": b(0),
            "bonus_chest": b(0),
            "dimensions": dimensions(),
        }),
        "DataPacks": c({"Enabled": lst(STRING, [s_("vanilla")]), "Disabled": lst(STRING, [])}),
        "ServerBrands": lst(STRING, [s_("vanilla")]),
    }
    return c({"Data": c(data)})


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "app_pojavlauncher/src/main/assets/testworld/level.dat"
    name = sys.argv[2] if len(sys.argv) > 2 else "Control test"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    raw = encode("", level(name))
    with gzip.open(out, "wb", compresslevel=9) as fh:
        fh.write(raw)
    print("%s  %d bytes raw, %d gzipped" % (out, len(raw), os.path.getsize(out)))


if __name__ == "__main__":
    main()
