---
status: accepted
---

# A removed node takes its fluid, and segments are saved with the level

ADR 0002 says what a segment is. It leaves six things open, which this decides.

**A broken node takes its share.** When a pipe, tank or port leaves a segment, the segment splits
into the runs that remain and each keeps fluid in proportion to its capacity, rounded so that no unit
appears or vanishes. The node itself takes the share its capacity held, as a broken pipe does in
Factorio, and that share is gone. Spreading the share over the runs instead would make breaking a
full tank hand its 25,000 mB to a 100 mB pipe's neighbours; there is nowhere for it to go.

**The graph is the level's, not the blocks'.** The segments are one `SavedData` per level: each node's
position, capacity, open faces and whether it waits (below), and what each segment holds. Links are
not saved, since two joined neighbours are linked exactly where both open the face between them. A
saved fluid the game no longer has, or a malformed id, empties its segment on load with one warning
in the log: an amount of no fluid could never be drained. A segment therefore keeps
its fluid while its chunks are unloaded, a pipe or tank is a plain block with no block entity, and
joining and leaving are driven by `onPlace` and `affectNeighborsAfterRemoval`, never by a chunk
loading. Only a Consumer's port has a block entity, and it joins from `onLoad`, which does nothing
for a port already in the graph.

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

**The tracer holds back.** The storage tank is one block of Factorio's 25,000, not the 3 by 3 it
occupies there, and the in-line Pump, pipe-to-ground, pipe drag-laying and pipe Dismantle (ADR 0002,
FactoryWorks ADR-0110) are later work. The capacities are constants until a pack needs to set them.

FactoryWorks ADR-0109, that the Pack depends on no third-party content mod, stays with the Pack: it
is why Pipeworks exists, and nothing in this Library applies it.
