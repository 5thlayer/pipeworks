# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Writes the storage tank's blockstate and the part models it is built from (ADR 0008): a tank
# leaves out its caps and glass towards the tanks of its group it joins, and draws its fluid out to
# them. The caps and glass are cut from storage_tank.json, the whole tank the item still draws. Run
# from the repo root with Python 3: python3 tools/storage_tank_models.py

import json
from collections import namedtuple
from pathlib import Path

ASSETS = Path("src/main/resources/assets/pipeworks")
MODELS = ASSETS / "models/block"
# The steps a tank's fill is drawn in: TankGroupFill.STEPS, the level property's maximum.
STEPS = 15
# The glass box: from 1 to 15 across, inset as the caps are not, and from 2 to 14 up.
GLASS_INSET = 1
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


TEXTURE_SIZE = 64
# A clear texel inside the glass, and one of the cap ring's grey: a cell drawn in one colour is
# stretched from inside one texel, so no other texel bleeds into it.
CLEAR_TEXEL = (20, 37)
FILL_TEXEL = (19, 19)


def texel_uv(texel):
    x, y = texel
    scale = 16 / TEXTURE_SIZE
    return [num((x + 0.25) * scale), num((y + 0.25) * scale),
            num((x + 0.75) * scale), num((y + 0.75) * scale)]


# The glass: one pane a face, at the glass box's face, from 1 to 15 across and 2 to 14 up, drawn
# as a grid of cells so each edge can follow what the tank joins. The texture draws the pane's
# frame as its outermost pixel all round: on a side the tank joins, that edge samples the clear
# middle of the pane instead and the pane reaches on by a strip or a band to the block's edge, so
# the glass of a group runs on with no frame between its tanks. A face the tank joins draws
# nothing, strips included: an inside corner, where a tank joins two sides, stays open a pixel
# wide, which the fluid keeps out of, rather than a full layer showing glass at its middle.
PLANE = {"north": ("z", 1), "south": ("z", 15), "west": ("x", 1), "east": ("x", 15)}
ACROSS = {"north": ("west", "east"), "south": ("west", "east"), "west": ("north", "south"),
          "east": ("north", "south")}


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
        return u0 + (a - GLASS_INSET) / (16 - 2 * GLASS_INSET) * (u1 - u0)
    def v(b):
        return v0 + (b - (16 - GLASS_TOP)) / (GLASS_TOP - GLASS_BOTTOM) * (v1 - v0)
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


# The cells across and up a pane: (name, range, the range it samples, when).
def across_cells(low, high):
    return [
        (f"{low}", (0, 1), (7, 8), {low: "true"}),
        (f"{low}_rim", (1, 2), (1, 2), {low: "false"}),
        (f"{low}_open", (1, 2), (7, 8), {low: "true"}),
        ("mid", (2, 14), (2, 14), {}),
        (f"{high}_open", (14, 15), (8, 9), {high: "true"}),
        (f"{high}_rim", (14, 15), (14, 15), {high: "false"}),
        (f"{high}", (15, 16), (8, 9), {high: "true"}),
    ]


UP_CELLS = [
    ("down", (0, 2), (7, 9), {"down": "true"}),
    ("down_rim", (2, 3), (2, 3), {"down": "false"}),
    ("down_open", (2, 3), (7, 8), {"down": "true"}),
    ("mid", (3, 13), (3, 13), {}),
    ("up_open", (13, 14), (8, 9), {"up": "true"}),
    ("up_rim", (13, 14), (13, 14), {"up": "false"}),
    ("up", (14, 16), (7, 9), {"up": "true"}),
]

parts = []


def part(model, when):
    entry = {"apply": {"model": f"pipeworks:block/{model}"}}
    if when:
        entry["when"] = when
    parts.append(entry)


# The caps: each cut into a grid of four by four cells of 4 pixels. The outer cells hold the cap's
# rim and the dark ring inside it, drawn only on a side the tank does not join, so a tank group's
# caps draw one rim and ring round the whole group. A tank alone keeps its centre. Where a tank
# joins both sides at a corner, the 8 pixels there draw a quarter of the centre at twice its size,
# the quarter facing that corner, so four tanks meeting there draw one centre twice as wide across
# their seams, in proportion to the group as a lone tank's is to it. Everywhere else is a plain
# fill of the ring's grey. A cell's outer face is drawn only on a side the tank does not join.
CELLS = [(0, 4), (4, 8), (8, 12), (12, 16)]
# Where a lone tank draws an edge the texture's ring is the same on all four sides: east and west
# both draw the east edge and north and south the north edge, mirrored on the west and the south.
EAST_EDGE, NORTH_EDGE = (12, 16), (0, 4)
# A joined edge cell carries the rim and ring on along the seam from the 4 pixels of the edge's
# middle beside it.
JOINED_LOW, JOINED_HIGH = (4, 8), (8, 12)


def lerp(uv_lo, uv_hi, a):
    return num(uv_lo + a / 16 * (uv_hi - uv_lo))


def flat_uv(uv, face, xs, zs):
    u0, v0, u1, v1 = uv
    if face == "up":
        return [lerp(u0, u1, xs[0]), lerp(v0, v1, zs[0]),
                lerp(u0, u1, xs[1]), lerp(v0, v1, zs[1])]
    return [lerp(u0, u1, xs[0]), lerp(v0, v1, 16 - zs[1]),
            lerp(u0, u1, xs[1]), lerp(v0, v1, 16 - zs[0])]


def side_uv(uv, side, xs, zs):
    u0, v0, u1, v1 = uv
    a, b = {"north": (16 - xs[1], 16 - xs[0]), "south": xs,
            "east": (16 - zs[1], 16 - zs[0]), "west": zs}[side]
    return [lerp(u0, u1, a), v0, lerp(u0, u1, b), v1]


# One axis of a cap cell: the side whose half it lies in, the unjoined rim it is on or None, where
# it samples as a rim or middle, and where it samples in a quarter of the centre.
Axis = namedtuple("Axis", "half rim sample quarter")


def axis(cell, low, high, joins, edge_sample, mirrored):
    """One axis of a cell. An unjoined rim samples edge_sample, reversed on the side mirrored."""
    lo, hi = cell
    half = low if hi <= 8 else high
    edge = low if lo == 0 else high if hi == 16 else None
    rim = edge if edge and joins[edge] == "false" else None
    if rim:
        sample = (edge_sample[1], edge_sample[0]) if rim == mirrored else edge_sample
    elif edge:
        sample = JOINED_LOW if edge == low else JOINED_HIGH
    else:
        sample = cell
    # The quarter's other half lies across the seam on this side, so the 8 pixels here are the
    # far half of the centre, from 4 to 8 or 8 to 12, at half the scale.
    quarter = (8 + lo / 2, 8 + hi / 2) if half == low else (4 + (lo - 8) / 2, 4 + (hi - 8) / 2)
    return Axis(half, rim, sample, quarter)


def cap_cell(cap, xs, zs, joins):
    """The faces of one cell of a cap, for one set of joins, or None for the plain fill."""
    x = axis(xs, "west", "east", joins, EAST_EDGE, "west")
    z = axis(zs, "north", "south", joins, NORTH_EDGE, "south")
    if joins[x.half] == joins[z.half] == "true":
        sx, sz, rims = x.quarter, z.quarter, []
    elif x.rim or z.rim:
        sx, sz, rims = x.sample, z.sample, [r for r in (x.rim, z.rim) if r]
    elif "true" not in joins.values():
        sx, sz, rims = x.sample, z.sample, []
    else:
        return None
    faces = {f: {"uv": flat_uv(cap["faces"][f]["uv"], f, sx, sz), "texture": "#0"}
             for f in ("up", "down")}
    for side in rims:
        faces[side] = {"uv": side_uv(cap["faces"][side]["uv"], side, sx, sz), "texture": "#0"}
    return faces


def every_join():
    """Every set of horizontal joins, as the blockstate's values."""
    for bits in range(16):
        yield {side: "true" if bits >> i & 1 else "false" for i, side in enumerate(SIDES)}


for cap, which, vertical in ((cap_bottom, "bottom", "down"), (cap_top, "top", "up")):
    y1, y2 = cap["from"][1], cap["to"][1]
    for xs in CELLS:
        for zs in CELLS:
            looks = {}
            for joins in every_join():
                faces = cap_cell(cap, xs, zs, joins)
                if faces is None:
                    faces = {f: {"uv": texel_uv(FILL_TEXEL), "texture": "#0"}
                             for f in ("up", "down")}
                looks.setdefault(json.dumps(faces, sort_keys=True), []).append(joins)
            for n, (key, cases) in enumerate(looks.items()):
                name = f"storage_tank_cap_{which}_{xs[0]}_{zs[0]}_{n}"
                element = {"from": [xs[0], y1, zs[0]], "to": [xs[1], y2, zs[1]],
                           "faces": json.loads(key)}
                write(name, {"textures": TEXTURES, "elements": [element]})
                if len(cases) == 16:
                    part(name, {vertical: "false"})
                else:
                    part(name, {"AND": [{vertical: "false"}, {"OR": cases}]})

for face in SIDES:
    low_side, high_side = ACROSS[face]
    for across, arange, asample, awhen in across_cells(low_side, high_side):
        for up, yrange, ysample, ywhen in UP_CELLS:
            name = f"storage_tank_glass_{face}_{across}_{up}"
            element = pane(face, (arange, asample), (yrange, ysample))
            # Past a seam the pane's middle has stray highlights in every row and column on some
            # faces, so a cell there that does not carry on the frame is clear glass.
            joined = "true" in awhen.values() or "true" in ywhen.values()
            on_frame = across.endswith("_rim") or up.endswith("_rim")
            if joined and not on_frame:
                element["faces"][face]["uv"] = texel_uv(CLEAR_TEXEL)
            write(name, {"textures": TEXTURES, "elements": [element]})
            part(name, {face: "false", **awhen, **ywhen})

# The fluid: a white box tinted the fluid's colour, inset 1.5 from the sides it does not join and
# out to the block's edge on those it does, though never into a corner: a tank cannot see its
# diagonal neighbour, and an L-shaped group's inside corner has no glass, so fluid there would
# stand outside the tank. It runs from the bottom glass up to the tank's step. A tank
# that joins the one below has a full one there, so its fluid also fills the cap's height beneath,
# and a full tank joined above fills the cap's height above.
HORIZONTAL = {None: (FLUID_INSET, 16 - FLUID_INSET), "low": (0, FLUID_INSET),
              "high": (16 - FLUID_INSET, 16)}


def fluid(xs, ys, zs):
    face = {"uv": [0, 0, 16, 16], "texture": "#fluid", "tintindex": 0}
    return {
        "textures": {"fluid": "minecraft:block/white_concrete",
                     "particle": "minecraft:block/white_concrete"},
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

(ASSETS / "blockstates/storage_tank.json").write_text(
    json.dumps({"multipart": parts}, indent=2) + "\n")
