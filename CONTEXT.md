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

**Node**:
A pipe, a storage tank or a port: one position in a segment, with a capacity and the faces it opens.
_Avoid_: part, pipe (a pipe is one kind of node)

**Port**:
A Consumer's block entity that joins a segment through the `FluidPort` API, so a machine fills and drains pipes. It adds no capacity unless it says so.
_Avoid_: connector, fluid face (a face is NeoForge's capability on one side of a block)

**Mixing**:
Joining two segments that hold different fluids. A placement that would is refused.
_Avoid_: contamination

### Parties

**Pipeworks**:
This Library.

**Consumer**:
A mod that builds against this Library: the FactoryWorks Pack, or another Library.
_Avoid_: client (collides with the game's client side), dependent, integration
