---
status: accepted
---

# A removed node takes its fluid, and segments are saved with the level

ADR 0002 says what a segment is. It leaves four things open, which this decides.

**A broken node takes its share.** When a pipe, tank or port leaves a segment, the segment splits
into the runs that remain and each keeps fluid in proportion to its capacity, rounded so that no unit
appears or vanishes. The node itself takes the share its capacity held, as a broken pipe does in
Factorio, and the removal reports it. Spreading the share over the runs instead would make breaking a
full tank hand its 25,000 mB to a 100 mB pipe's neighbours; there is nowhere for it to go.

**The graph is the level's, not the blocks'.** The segments are one `SavedData` per level: each node's
position, capacity, open faces and links, and what each segment holds. A segment therefore keeps
its fluid while its chunks are unloaded, a pipe or tank is a plain block with no block entity, and
joining and leaving are driven by `onPlace` and `affectNeighborsAfterRemoval`, never by a chunk
loading. Only a Consumer's port has a block entity, and it joins from `onLoad`, which does nothing
for a port already in the graph.

**A segment is one fluid slot.** Seen from a pipe, a tank or a port, a segment is a
`ResourceHandler<FluidResource>` of one index, so a Consumer's machine moves fluid with NeoForge's
transfer API and an aborted transaction is undone. Fluid with data components is refused, as a
segment does not keep two such fluids apart from plain ones.

**The tracer holds back.** The storage tank is one block of Factorio's 25,000, not the 3 by 3 it
occupies there, and the in-line Pump, pipe-to-ground, pipe drag-laying and pipe Dismantle (ADR 0002,
FactoryWorks ADR-0110) are later work. The capacities are constants until a pack needs to set them.

FactoryWorks ADR-0109, that the Pack depends on no third-party content mod, stays with the Pack: it
is why Pipeworks exists, and nothing in this Library applies it.
