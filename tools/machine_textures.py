# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Writes the Pump's and the Outlet's textures (#31) from the tiles in tools/textures: Malcolm
# Riley's unused-textures, retinted to the pipe and tank's slate and iron grey, and Oritech's fluid
# duct ring, its copper turned to iron grey, laid over the faces that join pipes as a flange. Run from
# the repo root with Python 3 and Pillow: python3 tools/machine_textures.py

from pathlib import Path

from PIL import Image

SOURCES = Path("tools/textures")
TEXTURES = Path("src/main/resources/assets/pipeworks/textures/block")

# Tone ramps, from a tile's brightness to a colour: slate for a body, iron grey for a nozzle.
SLATE = [(0, (18, 22, 30)), (45, (32, 40, 52)), (88, (65, 71, 88)), (105, (86, 92, 109)),
         (125, (150, 152, 156)), (165, (203, 203, 203)), (255, (252, 252, 252))]
IRON = [(0, (32, 40, 52)), (45, (65, 71, 88)), (70, (120, 124, 132)), (88, (163, 163, 163)),
        (105, (183, 183, 183)), (125, (203, 203, 203)), (165, (225, 225, 225)), (255, (252, 252, 252))]
# A tile is shifted so its median tone lands here, the slate (65,71,88) or the iron (163,163,163).
MIDDLE = 88
RIVET = (203, 203, 203)
PIXELS = [(x, y) for y in range(16) for x in range(16)]


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def ramp(l, stops):
    l = max(l, 0)
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if l <= b:
            t = (l - a) / (b - a)
            return tuple(round(ca[i] + (cb[i] - ca[i]) * t) for i in range(3))
    return stops[-1][1]


def retint(name, stops):
    """An unused-textures tile in the tones of a ramp, its rivets iron."""
    im = Image.open(SOURCES / "unused-textures" / f"{name}.png").convert("RGBA")
    shift = MIDDLE - sorted(lum(im.getpixel(p)) for p in PIXELS)[128]
    out = Image.new("RGBA", (16, 16))
    for p in PIXELS:
        c = im.getpixel(p)
        out.putpixel(p, (RIVET if c[0] >= 160 else ramp(lum(c) + shift, stops)) + (c[3],))
    return out


DUCT = Image.open(SOURCES / "oritech" / "fluid_pipe_duct_block.png").convert("RGBA")
DUCT_FRAME = DUCT.getpixel((0, 0))


def ring(base, on_iron=False):
    """Oritech's duct ring over a tile's centre: its copper iron grey on slate, or slate on iron."""
    out = base.copy()
    # The ring and the hole it frames fill texels 3 to 12 of the duct's face.
    for y in range(3, 13):
        for x in range(3, 13):
            c = DUCT.getpixel((x, y))
            if c[:3] == DUCT_FRAME[:3]:
                continue
            if c[0] > c[1] + 30:  # copper, by its red; the ring's brightness runs 80 to 126
                t = min(max((lum(c) - 80) / 46, 0), 1)
                g = round(65 + 40 * t) if on_iron else round(150 + 90 * t)
                c = (g, g, g + 12, 255) if on_iron else (g, g, g, 255)
            out.putpixel((x, y), c)
    return out


casing = retint("industrial_metal_duct_sides_1", SLATE)
nozzle = retint("industrial_metal_duct_flat_1", IRON)
written = {
    "machine_side": ring(casing),
    "pump_front": retint("industrial_metal_duct_intake", SLATE),
    "pump_top": retint("industrial_metal_duct_flat_1", SLATE),
    "outlet_casing": casing,
    "outlet_step": retint("industrial_metal_duct_sides_1", IRON),
    "outlet_nozzle": nozzle,
    "outlet_mouth": ring(nozzle, on_iron=True),
}
for name, im in written.items():
    im.save(TEXTURES / f"{name}.png")
