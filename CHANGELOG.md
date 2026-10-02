# Changelog

Written for Consumers: what a mod building against Pipeworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

- `pipeworks:creative_pipe`, in the creative tab and `/give` with no recipe: a pipe (100 mB, the same shape, arms and linking) that keeps its segment full of the fluid its screen sets, so the Library can be played alone. Right-click opens the screen; clicking its slot with a bucket, or any item holding a fluid, sets the fluid, and an empty cursor clears it. A fluid of another kind than the segment holds, or than another creative pipe in it is set to, is refused with the mixed-fluids message. With JEI or EMI installed, a fluid can be dragged onto the slot. Breaking it stops the refill. The fluid is saved with the level (#6, #8).

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
