# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Writes the storage tank's blockstate and the part models it is built from (ADR 0008): a tank
# leaves out its caps and glass towards the tanks of its group it joins, and draws its fluid out to
# them. The caps and glass are cut from storage_tank.json, the whole tank the item still draws. Run
# from the repo root with Python 3: python3 tools/storage_tank_models.py

import json
from pathlib import Path

ASSETS = Path("src/main/resources/assets/pipeworks")
MODELS = ASSETS / "models/block"
STEPS = 15
GLASS_BOTTOM, GLASS_TOP = 2, 14
FLUID_INSET = 1.5
SIDES = ["north", "east", "south", "west"]

whole = json.loads((MODELS / "storage_tank.json").read_text())
cap_bottom, cap_top, glass = whole["elements"]
TEXTURES = {"0": "pipeworks:block/storage_tank", "particle": "pipeworks:block/storage_tank"}


def write(name, model):
    (MODELS / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n")


def num(x):
    return int(x) if float(x).is_integer() else round(x, 4)


# The glass: one pane a face, at the glass box's face, from 1 to 15 across and 2 to 14 up. A pane
# reaches out by a strip to a side or a band to a cap the tank joins, textured with the pane's own
# edge so no other part of the texture shows. A strip is drawn wherever its side joins, even on a
# face that joins too: where a tank joins two sides at a corner, the strips of those faces close
# the inside corner, which inside a full layer only the fluid sees.
PLANE = {"north": ("z", 1), "south": ("z", 15), "west": ("x", 1), "east": ("x", 15)}
ACROSS = {"north": ("x", "west", "east"), "south": ("x", "west", "east"),
          "west": ("z", "north", "south"), "east": ("z", "north", "south")}


def glass_uv(face, a1, a2, y1, y2):
    """The uv of a pane spanning a1..a2 across and y1..y2 up, inside the glass box."""
    u0, v0, u1, v1 = glass["faces"][face]["uv"]
    # Minecraft's own uv for the face, as from/to give it, then scaled into the box's.
    if face in ("north", "east"):
        ua, ub = 16 - a2, 16 - a1
    else:
        ua, ub = a1, a2
    va, vb = 16 - y2, 16 - y1
    def u(a):
        return u0 + (a - 1) / 14 * (u1 - u0)
    def v(b):
        return v0 + (b - 2) / 12 * (v1 - v0)
    return [num(u(ua)), num(v(va)), num(u(ub)), num(v(vb))]


def pane(face, across, up):
    """A glass element on face, over across (a range) and up (a range), and where it samples."""
    plane_axis, at = PLANE[face]
    (a1, a2), (s1, s2) = across
    (y1, y2), (t1, t2) = up
    lo = {"x": a1, "y": y1, "z": a1}
    hi = {"x": a2, "y": y2, "z": a2}
    lo[plane_axis] = hi[plane_axis] = at
    return {
        "from": [num(lo["x"]), num(lo["y"]), num(lo["z"])],
        "to": [num(hi["x"]), num(hi["y"]), num(hi["z"])],
        "faces": {face: {"uv": glass_uv(face, s1, s2, t1, t2), "texture": "#0"}},
    }


ACROSS_PARTS = {None: ((1, 15), (1, 15)), "low": ((0, 1), (1, 2)), "high": ((15, 16), (14, 15))}
UP_PARTS = {None: ((2, 14), (2, 14)), "down": ((0, 2), (2, 4)), "up": ((14, 16), (12, 14))}

parts = []


def part(model, when):
    entry = {"apply": {"model": f"pipeworks:block/{model}"}}
    if when:
        entry["when"] = when
    parts.append(entry)


write("storage_tank_cap_bottom", {"textures": TEXTURES, "elements": [cap_bottom]})
write("storage_tank_cap_top", {"textures": TEXTURES, "elements": [cap_top]})
part("storage_tank_cap_bottom", {"down": "false"})
part("storage_tank_cap_top", {"up": "false"})

for face in SIDES:
    _, low_side, high_side = ACROSS[face]
    for across, (arange, asample) in ACROSS_PARTS.items():
        for up, (yrange, ysample) in UP_PARTS.items():
            name = "_".join(["storage_tank_glass", face]
                            + ([{"low": low_side, "high": high_side}[across]] if across else [])
                            + ([up] if up else []))
            write(name, {"textures": TEXTURES, "elements": [pane(face, (arange, asample), (yrange, ysample))]})
            when = {} if across else {face: "false"}
            if across:
                when[{"low": low_side, "high": high_side}[across]] = "true"
            if up:
                when[up] = "true"
            part(name, when)

# The fluid: a white box tinted the fluid's colour, inset 1.5 from the sides it does not join and
# out to the block's edge on those it does, though never into a corner, where the glass may close
# an inside corner, from the bottom glass up to the tank's step. A tank
# that joins the one below has a full one there, so its fluid also fills the cap's height beneath,
# and a full tank joined above fills the cap's height above.
HORIZONTAL = {None: (FLUID_INSET, 16 - FLUID_INSET), "low": (0, FLUID_INSET), "high": (16 - FLUID_INSET, 16)}


def fluid(xs, ys, zs):
    face = {"uv": [0, 0, 16, 16], "texture": "#fluid", "tintindex": 0}
    return {
        "textures": {"fluid": "minecraft:block/white_concrete"},
        "elements": [{
            "from": [num(xs[0]), num(ys[0]), num(zs[0])],
            "to": [num(xs[1]), num(ys[1]), num(zs[1])],
            "faces": {f: dict(face) for f in ["north", "east", "south", "west", "up", "down"]},
        }],
    }


def columns():
    for x in (None, "low", "high"):
        for z in (None, "low", "high"):
            if x and z:
                continue
            joins = {}
            if x:
                joins[{"low": "west", "high": "east"}[x]] = "true"
            if z:
                joins[{"low": "north", "high": "south"}[z]] = "true"
            suffix = "".join(k[0] for k in sorted(joins, key=SIDES.index)) or "core"
            yield suffix, HORIZONTAL[x], HORIZONTAL[z], joins


for suffix, xs, zs, joins in columns():
    for level in range(1, STEPS + 1):
        top = GLASS_BOTTOM + (GLASS_TOP - GLASS_BOTTOM) * level / STEPS
        name = f"storage_tank_fluid_{level}_{suffix}"
        write(name, fluid(xs, (GLASS_BOTTOM, top), zs))
        part(name, {"level": str(level), **joins})
    name = f"storage_tank_fluid_below_{suffix}"
    write(name, fluid(xs, (0, GLASS_BOTTOM), zs))
    part(name, {"level": "|".join(str(n) for n in range(1, STEPS + 1)), "down": "true", **joins})
    name = f"storage_tank_fluid_above_{suffix}"
    write(name, fluid(xs, (GLASS_TOP, 16), zs))
    part(name, {"level": str(STEPS), "up": "true", **joins})

(ASSETS / "blockstates/storage_tank.json").write_text(json.dumps({"multipart": parts}, indent=2) + "\n")
