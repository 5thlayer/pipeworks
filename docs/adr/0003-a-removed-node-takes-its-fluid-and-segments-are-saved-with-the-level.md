---
status: accepted
---

# A removed node takes its fluid, and segments are saved with the level

ADR 0002 says what a segment is. It leaves seven things open, which this decides.

**A broken node takes its share.** When a pipe, tank or port leaves a segment, the segment splits
into the runs that remain and each keeps fluid in proportion to its capacity, rounded so that no unit
appears or vanishes. The node itself takes the share its capacity held, as a broken pipe does in
Factorio, and that share is gone. Spreading the share over the runs instead would make breaking a
full tank hand its 25,000 mB to a 100 mB pipe's neighbours; there is nowhere for it to go.

**The graph is the level's, not the blocks'.** The segments are one `SavedData` per level: each node's
position, capacity, open faces and whether it waits (below), what each segment holds, and the fluid
each creative pipe is set to, which keeps its segment full (#6). Links are
not saved, since two joined neighbours are linked exactly where both open the face between them. A
saved fluid the game no longer has, or a malformed id, empties its segment on load with one warning
in the log: an amount of no fluid could never be drained. A creative pipe set to such a fluid is
cleared the same way. A segment therefore keeps
its fluid while its chunks are unloaded, a pipe or tank is a plain block with no block entity, and
joining and leaving are driven by `onPlace` and `affectNeighborsAfterRemoval`, never by a chunk
loading. Only a Consumer's port has a block entity, and it joins from `onLoad`, which does nothing
for a port already in the graph.

**A node whose block is gone leaves.** Vanilla calls `affectNeighborsAfterRemoval` only when a
removal's flags include `UPDATE_NEIGHBORS`, and `/setblock` or `/fill` in strict mode, a structure
clearing its area, or another mod can leave it out (#4). So the graph is also checked against the
world: on the level tick, a bounded number of nodes at a time, and whenever a node's segment is
reached through it. A node whose position is loaded and holds no pipe, tank or port leaves as a
broken one does, taking its share. A position that is not loaded is never judged. The reverse is not
caught: a pipe placed with `UPDATE_SKIP_ON_PLACE`, as strict mode does, never joins, since nothing
finds a block the graph does not know. Considered: a mixin into `LevelChunk.setBlockState`, which
sees every change whatever its flags, placements included. It would be the Library's first mixin,
to be checked again on every Minecraft update, for removals that only commands and other mods make.

**A node that would mix waits.** A port, or a pipe or tank set by something other than a player,
whose join would mix two fluids is in no segment: it has no fluid slot, it links to nothing, and a
pipe draws arms only to the nodes it is linked to. Every waiting node tries again each tick and
whenever the level's segments are reached, and joins once it no longer mixes, because a neighbour
was removed or drained. Considered: making it a segment of its own.
Later neighbours then link to it and, being in the graph, it never tries again, so a port stays cut
off for good. Considered: retrying only when a neighbour is placed or removed. That misses a
neighbouring segment a machine drains.

**A segment is one fluid slot.** Seen from a pipe, a tank or a port, a segment is a
`ResourceHandler<FluidResource>` of one index, so a Consumer's machine moves fluid with NeoForge's
transfer API and an aborted transaction is undone. Fluid with data components is refused, as a
segment does not keep two such fluids apart from plain ones. A handler whose node has left holds and
takes nothing, since a capability cache may still hand it out.

**Joins and removals wait for the transaction.** A node placed or removed while a transaction is
open joins or leaves once none is, so an aborted transaction reverts a segment that still exists, and
the fluid it moved is put back before the segment splits or merges. Until then the new node has no
fluid slot and the removed one's capacity still counts in its segment. Considered: undoing a transaction's
change onto whichever segments followed a merge or a split. A split spreads the moved fluid over the
runs and the broken node's share, and it cannot be taken back exactly.

**A tank shows its segment's fill as a blockstate.** The client has no segments, so what a player
sees of one is what the server tells it. The storage tank has a `level` property, 0 to 15, which the
server sets when the fill fraction of the tank's segment crosses a step (`SegmentGraph.step`: 0 for
none, 15 for full, rounded down in between and never below 1 while it holds any). The way `redraw`
sets a pipe's arms, it does so on the level tick for the segments whose fill changed since, and only
when the step or the fluid did, so a busy segment costs at most one change a tick and a flow inside
a step none. A waiting tank is in no segment and shows 0. The model draws a fluid layer in the
glass at the height of the step, in a white texture. Its colour is the fluid's, which a blockstate
cannot hold: the server sends the tank's fluid id to the clients tracking it when it changes, and
to a player when a chunk reaches them, found through an index of the nodes by chunk, and a block colour handler tints the layer with it. That is all that is sent,
not the amount. Only tanks show their fill; a pipe is too thin to show it and Jade, which reads the
server's segment, covers every node. Considered: a block entity with a renderer, which draws the
fluid's own texture and an exact height, but it gives up the plain block: every tank would carry a
block entity holding only what its segment already knows.

**The storage tank is one block.** It holds Factorio's 25,000 mB in one block, not the 3 by 3 it
occupies there, and a pack that wants Factorio's volume per block sets the tank's capacity (below).
Considered: a 3 by 3 tank on Groundworks' footprint (Groundworks ADR 0009), with Factorio's four
corner connections (#3). It would be the Library's first dependency, and a segment has no node that
spans several positions: one would join, split, wait and be saved across nine, or eight parts would
be ports of the origin that open only at the corners, for a tank that only looks more like Factorio's.

**The tracer holds back.** The in-line Pump, pipe-to-ground, pipe drag-laying and pipe Dismantle
(ADR 0002, FactoryWorks ADR-0110) are later work.

**A pack sets the capacities by server config.** `pipeworks-server.toml` holds the pipe's and the
tank's capacity, 100 and 25,000 mB by default, as the other 5thlayer Libraries' figures are set (#2).
A node takes the configured capacity when it joins, and keeps the one it was saved with: changing the
config changes the pipes and tanks placed afterwards, so a segment may hold nodes of both. Considered:
giving every saved node the configured capacity on load, which voids what a shrunk segment no longer
holds, for a change a pack makes once, before a world has pipes.

FactoryWorks ADR-0109, that the Pack depends on no third-party content mod, stays with the Pack: it
is why Pipeworks exists, and nothing in this Library applies it.
