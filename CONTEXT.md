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

**Creative tank**:
A storage tank that keeps its segment full of the fluid a bucket set it to, with no recipe. A segment has at most one fluid among its creative tanks.
_Avoid_: infinite tank, source block (collides with the game's fluid source blocks)

**Mixing**:
Joining two segments that hold different fluids. A player's placement that would is refused.
_Avoid_: contamination

**Waiting node**:
A node that would mix two fluids if it joined, so it is in no segment and has no fluid slot. It joins once it no longer would.
_Avoid_: orphan, isolated node

### Parties

**Pipeworks**:
This Library.

**Consumer**:
A mod that builds against this Library: the FactoryWorks Pack, or another Library.
_Avoid_: client (collides with the game's client side), dependent, integration
