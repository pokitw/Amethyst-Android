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
    All three vanilla dimensions, spelled exactly the way 1.20.1's own codecs write them.

    <b>All three are load-bearing, and that is Mojang's rule, not a guess.</b> On load,
    DimensionOptionsRegistryHolder.toConfig unions the datapack dimension registry with this
    compound and then decides the world's lifecycle:

        Lifecycle lifecycle = list.size() == VANILLA_KEY_COUNT ? Lifecycle.stable()
                                                               : Lifecycle.experimental();

    VANILLA_KEY_COUNT is 3, vanilla ships no dimension datapack, so a level.dat that declares
    fewer than three dimensions IS an "experimental settings" world; loading one asks for
    confirmation, and quick play cannot confirm anything, it just runs its cancel callback,
    which returns to the title screen. That is precisely the failure this file shipped once.

    The nether and end below must also each pass isNetherVanilla / isTheEndVanilla, or their
    per-entry lifecycle goes experimental and the same gate closes. Field for field:
      - DimensionOptions.CODEC:      "type" (dimension type ref) + "generator"
      - NoiseChunkGenerator.CODEC:   "biome_source" + "settings" (registry ref), nothing else
      - MultiNoiseBiomeSource PRESET_CODEC: {"type": "minecraft:multi_noise", "preset": ...}
      - TheEndBiomeSource.CODEC:     five RegistryOps.getEntryCodec entries, which serialize
                                     NOTHING, so {"type": "minecraft:the_end"} alone is exact.
    Verified against the deobfuscated 1.20.1 source, not memory; see verify() below, which
    re-parses the emitted file and enforces this whole contract on every run.
    """
    return c({
        "minecraft:overworld": c({
            "type": s_("minecraft:overworld"),
            "generator": flat_generator(),
        }),
        "minecraft:the_nether": c({
            "type": s_("minecraft:the_nether"),
            "generator": c({
                "type": s_("minecraft:noise"),
                "settings": s_("minecraft:nether"),
                "biome_source": c({
                    "type": s_("minecraft:multi_noise"),
                    "preset": s_("minecraft:nether"),
                }),
            }),
        }),
        "minecraft:the_end": c({
            "type": s_("minecraft:the_end"),
            "generator": c({
                "type": s_("minecraft:noise"),
                "settings": s_("minecraft:end"),
                "biome_source": c({"type": s_("minecraft:the_end")}),
            }),
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


# --- verification. The half of this file that exists because the other half was wrong twice. ---

END_, BYTE_, SHORT_, INT_, LONG_, FLOAT_, DOUBLE_ = 0, 1, 2, 3, 4, 5, 6
BYTE_ARRAY_, STRING_, LIST_, COMPOUND_ = 7, 8, 9, 10


def _read(data):
    """A reader written against the NBT spec, deliberately not sharing code with the writer."""
    import struct as _st
    pos = [0]

    def take(fmt, n):
        v = _st.unpack_from(fmt, data, pos[0])[0]
        pos[0] += n
        return v

    def name():
        n = take(">H", 2)
        v = data[pos[0]:pos[0] + n].decode("utf-8")
        pos[0] += n
        return v

    def payload(kind):
        if kind == BYTE_: return take(">b", 1)
        if kind == SHORT_: return take(">h", 2)
        if kind == INT_: return take(">i", 4)
        if kind == LONG_: return take(">q", 8)
        if kind == FLOAT_: return take(">f", 4)
        if kind == DOUBLE_: return take(">d", 8)
        if kind == STRING_: return name()
        if kind == LIST_:
            ek = take(">b", 1)
            n = take(">i", 4)
            return [payload(ek) for _ in range(n)]
        if kind == COMPOUND_:
            out = {}
            while True:
                t = take(">b", 1)
                if t == END_:
                    return out
                key = name()
                out[key] = payload(t)
        raise ValueError("unreadable tag kind %d" % kind)

    t = take(">b", 1)
    root_name = name()
    root = payload(t)
    if pos[0] != len(data):
        raise ValueError("trailing bytes: consumed %d of %d" % (pos[0], len(data)))
    return root_name, root


def verify(path):
    """
    Re-parse the emitted file and hold it to 1.20.1's own rules.

    The two failures this has actually shipped are both encoded here so they can never come
    back quietly: a dimensions compound with fewer than the three vanilla keys is an
    experimental-lifecycle world that quick play refuses (VANILLA_KEY_COUNT in
    DimensionOptionsRegistryHolder.toConfig), and a nether or end that differs from the exact
    vanilla shape fails isNetherVanilla / isTheEndVanilla and closes the same gate.
    """
    raw = gzip.open(path, "rb").read()
    root_name, root = _read(raw)
    assert root_name == "", "level.dat root compound must be unnamed, got %r" % root_name
    data = root["Data"]

    # Pinned to the literal, not to DATA_VERSION: a check that reads the same constant as the
    # writer agrees with whatever the writer says, which is not a check (handbook 16.20). 3465
    # is 1.20.1 in Mojang's version manifest and nowhere else in this file.
    assert data["DataVersion"] == 3465, "DataVersion must be 3465 (1.20.1)"
    assert data["version"] == 19133, "storage version must be 19133"
    assert data["Version"]["Id"] == 3465
    assert data["Version"]["Name"] == "1.20.1"

    dims = data["WorldGenSettings"]["dimensions"]
    expected = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}
    assert set(dims.keys()) == expected, \
        "dimensions must be exactly the vanilla three (Lifecycle.stable needs " \
        "VANILLA_KEY_COUNT == 3), got %s" % sorted(dims.keys())

    ov = dims["minecraft:overworld"]
    assert ov["type"] == "minecraft:overworld"
    assert ov["generator"]["type"] == "minecraft:flat"
    flat = ov["generator"]["settings"]
    assert flat["features"] == 0 and flat["lakes"] == 0
    assert [l["block"] for l in flat["layers"]] == \
        ["minecraft:bedrock", "minecraft:dirt", "minecraft:grass_block"]
    assert "structure_overrides" not in flat, "optional field left out on purpose"

    nether = dims["minecraft:the_nether"]
    assert nether["type"] == "minecraft:the_nether"
    assert nether["generator"] == {
        "type": "minecraft:noise", "settings": "minecraft:nether",
        "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"},
    }, "the nether must match isNetherVanilla exactly, or the lifecycle goes experimental"

    end = dims["minecraft:the_end"]
    assert end["type"] == "minecraft:the_end"
    assert end["generator"] == {
        "type": "minecraft:noise", "settings": "minecraft:end",
        "biome_source": {"type": "minecraft:the_end"},
    }, "the end must match isTheEndVanilla exactly, or the lifecycle goes experimental"

    rules = data["GameRules"]
    assert all(isinstance(v, str) for v in rules.values()), "game rules are strings"
    assert rules["doMobSpawning"] == "false"

    # A second, independent decoder where one is installed. It caught nothing the reader above
    # does not, but an assertion that costs one import is the cheapest kind there is.
    try:
        import nbtlib
        nbtlib.load(path)
    except ImportError:
        pass

    return len(raw)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "app_pojavlauncher/src/main/assets/testworld/level.dat"
    name = sys.argv[2] if len(sys.argv) > 2 else "Control test"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    raw = encode("", level(name))
    with gzip.open(out, "wb", compresslevel=9) as fh:
        fh.write(raw)
    verified = verify(out)
    print("%s  %d bytes raw, %d gzipped, verified" % (out, verified, os.path.getsize(out)))


if __name__ == "__main__":
    main()
