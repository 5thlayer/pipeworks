# Machine textures prototype

The throwaway scripts that drew the renders #31 was decided from. They are a starting point for
`tools/machine_textures.py`, not part of the build, and go once it lands.

Run from this directory with Python 3 and Pillow; each writes PNGs into `proto/`:

- `proto/make.py`: the first retint and the Pump and Outlet as full cubes.
- `proto/make2.py`: the slate and iron-grey body tones.
- `proto/make3.py`: the Outlet's shapes beside the Pump, 4b being the one chosen.
- `proto/make4.py`: the Pump's top, c (the flat panel) being the one chosen.

The tiles are copied unchanged: `ut/blocks/` from malcolmriley/unused-textures at `8b85ad73`
(CC BY 4.0), and `oritech_candidates/fluid_pipe_duct_block.png` from Oritech v1.2.12 (CC0 1.0).
