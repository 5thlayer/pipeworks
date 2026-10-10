# Pipeworks

Pipes, a storage tank and a fluid-port API for Minecraft, on Factorio 2.0's fluid segments. It
registers no fluid and carries whichever a Consumer puts in, as Beltworks carries any item. The
FactoryWorks Pack binds it to Factorio's numbers and fluids; Craftworks is meant to nest it, so a
machine's fluid slots and the pipes feeding them share one model.

## Language

The Library's terms, each with what it is and the words to avoid. `/domain-modeling` adds them as they are resolved.

### Fluid

**Segment**:
A connected run of nodes, which holds one fluid in one fluid box. Its capacity is the sum of its nodes' and flow inside it is instant.
_Avoid_: network, pipeline, fluid box (that is what a segment holds, not what it is)

**Contents**:
What a segment holds: one fluid and its amount, or nothing.
_Avoid_: held (collides with the Pack's Held recipe), level

**Node**:
A pipe, a storage tank or a port: one position in a segment, with a capacity and the faces it opens.
_Avoid_: part, pipe (a pipe is one kind of node)

**Port**:
A Consumer's block entity that joins a segment through the `FluidPort` API, so a machine fills and drains pipes. It adds no capacity unless it says so.
_Avoid_: connector, fluid face (a face is NeoForge's capability on one side of a block)

**Fluid inventory**:
A block beside a node that is no node itself but exposes a fluid handler on the facing side, such as another mod's tank or machine. It moves fluid in and out of a segment through that handler and never joins it.
_Avoid_: machine (a port is often one), foreign tank, fluid face

**Arm**:
What a pipe draws towards a side where fluid can move: a node it is linked to, or a fluid inventory.
_Avoid_: connection, link (a link is between two nodes in a segment; an arm is only what is drawn)

**Closed side**:
A side of a pipe a player has shut, where it links to no node and moves no fluid with a fluid inventory. Closing the face between two nodes closes it on both. Every side starts open, and a port's sides are its own to open.
_Avoid_: disconnected side, wrenched side, blocked side

**Creative pipe**:
A pipe in every way but its refill: it keeps its segment full of the fluid its screen sets it to, with no recipe. A segment has at most one fluid among its creative pipes.
_Avoid_: infinite pipe, source block (collides with the game's fluid source blocks)

**Mixing**:
Joining two segments that hold different fluids. A player's placement that would is refused.
_Avoid_: contamination

**Waiting node**:
A node that would mix two fluids if it joined, so it is in no segment and has no fluid slot. It joins once it no longer would.
_Avoid_: orphan, isolated node

### Machines and tools

**Pump**:
The machine that draws a still source block beside it into its segment. A source in the infinite-source tag, water by default, stays in place and gives a fixed rate; any other is taken whole, as a bucket would take it.
_Avoid_: Offshore Pump, in-line pump (a pump that moves fluid along a pipe, which is not built)

**Dismantle Family**:
The pipes a Groundworks Dismantle takes up as one span: the block tag `pipeworks:dismantle/pipes`, joined only where each pipe draws an arm toward the other, so a closed side ends a span.
_Avoid_: dismantle group

### Parties

**Pipeworks**:
This Library.

**Consumer**:
A mod that builds against this Library: the FactoryWorks Pack, or another Library.
_Avoid_: client (collides with the game's client side), dependent, integration
