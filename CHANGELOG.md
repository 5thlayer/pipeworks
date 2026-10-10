# Changelog

Written for Consumers: what a mod building against Pipeworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

- **Breaking for installs: Pipeworks requires Groundworks** `0.5.5` or later in the 0.5 series, as a separate download and not bundled (ADR 0005). `neoforge.mods.toml` and the uploads' related projects name it (#16).
- Pipes are a Groundworks **Dismantle Family**, the block tag `pipeworks:dismantle/pipes` (the pipe and the creative pipe). A sneak-click with an item in `groundworks:dismantles` stores a start, and a click names the end: the span is the shortest path through pipes that draw an arm toward each other, so a closed side ends it. A start and an end with no path, or two equally short ones, are refused with Pipeworks' own message. `groundworks:dismantles` ships with `#minecraft:pickaxes` and `#c:tools/wrench`, which a pack trims (#16).
- Pipes are laid by Groundworks' **Stretch**: a rise climbs straight up in place, then the leg runs level, each pipe open to the next and to a pipe or fluid inventory beside it, as one placement does. A run that would join two fluids, or crosses a block a pipe cannot replace, is refused whole (#16).

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
