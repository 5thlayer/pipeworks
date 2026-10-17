# Changelog

Written for Consumers: what a mod building against Pipeworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

## 0.4.0

- In the creative pipe's screen, a shift-click on a bucket or other fluid item in the inventory sets the pipe's fluid, as a click on its slot with that item does, and takes nothing; a shift-click on the slot clears it (#33).
- Flowing water and lava stop at a pipe or a creative pipe as at a wall, where they washed it away and dropped it. A pipe now counts as solid to vanilla too: a sign or a banner stands on one, a painting or an item frame hangs on one, rain stops at one, and farmland or a dirt path under one turns to dirt (#34).
- The Pump and the Outlet have textures of their own in the pipe and tank's slate and iron grey, where they had the furnace's, the dispenser's and the dropper's. The Outlet narrows in steps to a nozzle instead of filling the block, though its hitbox is still the whole block; it no longer hides the faces of the blocks beside it, and lets light through as a hopper does; the model `pipeworks:block/outlet_vertical` is gone, the blockstate turns `pipeworks:block/outlet` to each facing (#31).
- The pipe and the storage tank are iron grey where they were Oritech's orange copper, to sit with the Pump and the Outlet; the creative pipe stays purple (#30).
- The jar nests libworks-runtime 0.1.11, and `neoforge.mods.toml` requires `libworks` from 0.1.11 to the next minor. Pipeworks' gallery game tests use libworks' `GalleryCheck` row layout and pack frame instead of copies of their own (5thlayer/libworks#31).
- Pipeworks' game-test code and structures no longer ship in its jar (libworks ADR 0011): a Consumer's game tests and dev client no longer register Pipeworks' test type `pipeworks:code`, nor carry its `gametest/platform` structure. Pipeworks builds on libworks-plugin 0.4.0.
- Storage tanks that touch draw as one tank: the glass runs on with no frame between them and the caps draw one rim round the group, and the fluid fills the group from its bottom tank up instead of every tank showing its segment's level (ADR 0008, #27). Each tank's `level` blockstate is now its own share of its group's fill, and the tank has six new blockstate properties, `north` to `down`, for the tanks of its group beside it.

## 0.3.2

- The Gallery pack, **Pipeworks Gallery**: a datapack in the jar, off by default, whose `/function pipeworks:gallery/build` builds a working scene of each README feature, in rows of three north-east of the player, each with a sign naming it that rebuilds the scene when clicked; `pipeworks:gallery/<scene>` builds one. It needs libworks-runtime 0.1.9, which the jar nests (libworks ADR 0010, #26).

## 0.3.1

### Fixed

- A dev client with Jade no longer crashes the first time the title screen opens: the Jade option for a pipe's closed sides has its line in the lang file, `config.jade.plugin_pipeworks.closed` (#25).

## 0.3.0

- `pipeworks-server.toml`, a server config: `pipeCapacity` (default 100) and `tankCapacity` (default 25,000) set the millibuckets a pipe, creative pipe and storage tank add to their segment, each at least 1. A node takes the value when it is placed and keeps the one it was saved with, so a change reaches only the pipes and tanks placed after it, and one segment may hold both. `Pipeworks.PIPE_CAPACITY` and `TANK_CAPACITY` are the defaults, and `PipeworksConfig.pipeCapacity()` and `tankCapacity()` read the configured values (ADR 0003, #2).
- A **fluid item** used on a pipe or storage tank fills or empties its segment: a bucket, or any item with `Capabilities.Fluid.ITEM`, such as another mod's portable tank, through its first slot. A full item empties only whole, into a segment holding its fluid or none; an empty or part-full one fills from the segment's fluid and never takes another. A refused item does nothing, so a bucket never sets its fluid down beside the node. The hand changes as at a cauldron: unchanged in creative, and one of a stack swapped with the result put in the inventory. The creative pipe still opens its screen (5thlayer/factoryworks ADR-0121, #12).
- Every Pipeworks item shows in the suite's shared **Works** creative tab (`libworks:works`) and in no vanilla tab, so the pipe, storage tank, Pump and outlet leave Functional Blocks, and the Pipeworks tab `pipeworks:items` and its lang key `itemGroup.pipeworks.items` are gone. The jar nests `libworks-runtime` 0.1.1 by jar-in-jar, so a player installs nothing more; `neoforge.mods.toml` requires `libworks` from 0.1.1 to the next minor (FactoryWorks ADR-0128, #17).
- `pipeworks:pipe` (three from three copper ingots) and `pipeworks:storage_tank` (four iron ingots, four glass and a copper ingot) have crafting recipes from vanilla items, so Pipeworks is craftable with Groundworks alone. The creative pipe has none: it is a dev tool, kept out of survival (5thlayer/factoryworks#610, #24).
- `pipeworks:pump` (four pipes, a piston and a bucket) and `pipeworks:outlet` (four pipes and a dispenser) have crafting recipes, so the Library's fluid loop is playable in survival with Groundworks alone. A pack may replace either (#24).
- The item tag `pipeworks:closes_sides` ships `minecraft:stick`, where it shipped empty: a player with no pack can close a pipe side. A pack that names other items replaces the tag with `"replace": true`; one that adds to it keeps the stick (ADR 0004, #15).
- `pipeworks:outlet`, the **Outlet**: a port that sets its segment's fluid down as one source block in the space it faces, with no power. It faces away from the block it is placed against and joins pipes on every other face. Once the segment holds 1,000 mB and that space is empty or holds a replaceable block (grass, snow, flowing fluid) and no source block, it takes the bucket and places the block, and the replaced block drops what it would if broken. A pipe, a torch or any other block that is only not solid is left standing. A fluid with no block stays in the pipes, and it never draws fluid from the world (#20).
- `pipeworks:pump`, the **Pump**: a port that draws a still source block beside it into its segment, on any side, with no power. A source in the fluid tag `pipeworks:infinite_sources` (ships `minecraft:water`) is never taken, and the pump adds 60 mB a tick, Factorio's 1,200 mB a second. Any other source is taken whole, as a bucket would, once the segment has room for 1,000 mB, and the pump then waits 17 ticks. It draws only the segment's own fluid, so it never mixes, and it is refused where no still source stands beside it. `PumpBlock.sourcesBeside` and `PumpBlockEntity.INFINITE_SOURCES` are the pieces a Consumer can read (#16).

- **Breaking for installs: Pipeworks requires Groundworks** `0.5.5` or later in the 0.5 series, as a separate download and not bundled (ADR 0005). `neoforge.mods.toml` and the uploads' related projects name it (#16).
- Pipes are a Groundworks **Dismantle Family**, the block tag `pipeworks:dismantle/pipes` (the pipe and the creative pipe). A sneak-click with an item in `groundworks:dismantles` stores a start, and a click names the end: the span is the shortest path through pipes that draw an arm toward each other, so a closed side ends it. A start and an end with no path, or two equally short ones, are refused with Pipeworks' own message. `groundworks:dismantles` ships with `#minecraft:pickaxes` and `#c:tools/wrench`, which a pack trims (#16).
- Pipes are laid by Groundworks' **Stretch**: a rise climbs straight up in place, then the leg runs level, each pipe open to the next and to a pipe or fluid inventory beside it, as one placement does. A run that would join two fluids, or crosses a block a pipe cannot replace, is refused whole (#16).

### Fixed

- A pipe, tank or port removed without a neighbour update, as `/setblock` and `/fill` in strict mode, a structure clearing its area or another mod can do, now leaves its segment as a broken one does: the segment splits and the node takes its share. The level checks a bounded number of nodes against the world each tick, and a port whose segment is reached through its position (`FluidPorts.segment`) gets none once its block is gone. A node in a chunk that is not loaded is never judged. `FluidSegments.handlerAt` and `contentsAt` now take the `ServerLevel` (ADR 0003, #4).

## 0.2.0

- A pipe's side can be closed and opened, for a pack that wants two runs kept apart or a pipe cut off from a fluid inventory. It is off until a Consumer allows it: the item tag `pipeworks:closes_sides` ships empty, and a plain right-click on a pipe or creative pipe with an item in it toggles a side (a sneak-click is left to Groundworks' Dismantle). A click on an arm picks that side, a click on the core the face clicked. The pipe links to no node on a closed side, and the face of a pipe or tank beside it closes too; a port's faces are its own. The side draws no arm, the pipe's fluid handler is null there and Pipeworks tells the level its capabilities changed, and Jade names the pipe's closed sides. Closing splits the segment with its fluid kept, and opening a side that would mix two fluids is refused as a placement is, toward a port too. One click opens a closed face from either side, even when the pipe beside it was placed after the side was closed. A closed side is saved with the level, and every side starts open (#9).
- `FluidPipes.toggle`, `close`, `open` and `isClosed` do the same for a Consumer's own tool, with no item involved, and `FluidPipes.CLOSES_SIDES` is the tag. `Capabilities.Fluid.BLOCK` on a pipe or tank is now null on a closed side; it was the segment on every face (#9).
- A pipe draws an arm toward a fluid inventory beside it: a block that is no node but exposes a fluid handler on the side facing the pipe. The arm is display only, since the inventory never joins the segment, and it follows the handler as it appears and goes away, with or without a block update, within a tick or two. A Pipeworks node beside a pipe gets an arm only where it is linked, and a waiting pipe draws none toward an inventory (#11).
- `FluidPipes.wouldDrawArm(level, pos, side)` reads whether a pipe placed at a position would draw an arm on a side, for a Consumer that plans pipes: a link, or a fluid inventory. On the server it answers as placement does; on the client the handler lookup is best-effort. `wouldLink` still answers only whether the pipe would link to a node (#11).
- `FluidPipeBlock.isLinked(state, side)` is now `drawsArm(state, side)`: the arm is drawn toward a fluid inventory too, so it no longer means a link. Ask the segments, not the blockstate, whether two nodes are linked (#11).

## 0.1.2

- `pipeworks:creative_pipe`, in the creative tab and `/give` with no recipe: a pipe (100 mB, the same shape, arms and linking) that keeps its segment full of the fluid its screen sets, so the Library can be played alone. Right-click opens the screen; clicking its slot with a bucket, or any item holding a fluid, sets the fluid, and an empty cursor clears it. A fluid of another kind than the segment holds, or than another creative pipe in it is set to, is refused with the mixed-fluids message. With JEI or EMI installed, a fluid can be dragged onto the slot. Breaking it stops the refill. The fluid is saved with the level (#6, #8).
- A storage tank has a `level` blockstate property, 0 to 15, which the server sets from the fill of its segment (`SegmentGraph.step`), and draws that high a layer of its fluid in its glass, tinted by the fluid. The server sends the tank's fluid id to the clients tracking it; the amount is not sent. A tank that waits shows 0. Pipes show no fill (#7).
- With Jade installed, a pipe, tank or port shows its segment's fluid, amount and capacity in Jade's own fluid bar, or a line that it waits (`PipeworksJadePlugin`, optional: the Library loads without Jade) (#7).

## 0.1.1

- `FluidPipes.wouldLink(level, pos, side)` reads whether a pipe placed at a position would link on a side, for a Consumer that plans pipes: on the server as placement would, so not to a waiting node and nowhere for a pipe that would join two fluids; on the client, whether the block beside opens towards it (#5).

## 0.1.0

- The pipe (100 mB) and the storage tank (25,000 mB), Factorio's volumes, join into fluid segments: a connected run is one fluid box holding one fluid, its capacity the sum of its parts and its flow instant. A pipe or tank that would join two fluids is not placed.
- The `FluidPort` API (`io.github._5thlayer.pipeworks.api`): a block entity that implements it and calls `FluidPorts.join` from `onLoad` joins the segments beside it, and moves fluid through `FluidPorts.segment`. Its block calls `FluidPorts.leave` when removed.
- Pipes and tanks expose `Capabilities.Fluid.BLOCK`, a segment as one fluid slot.
- A port, or a pipe or tank set by something other than a player, that would join two fluids waits in no segment, drawing no pipe arms, and joins once it no longer would.
- A node placed or removed while a transaction is open joins or leaves once it closes, so an aborted transaction puts its fluid back before a segment splits or merges.
- Breaking a node splits its segment by capacity and the node takes its share. Segments are saved with the level and keep their fluid while chunks are unloaded; a saved fluid the game no longer has empties its segment.
- `FluidPipeBlock.isLinked(state, side)` reads whether a pipe's segment links it on a side, the arm it draws.
- Registers no fluid.
- The pipe and tank models and textures are Oritech's, CC0.
