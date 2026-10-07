# Fluid filters on ports

Pipeworks gives a `FluidPort` no filter: a port joins whatever segment its neighbours form, whatever
fluid that segment holds or will hold.

## Why this is out of scope

A Consumer's machine already names the fluid it moves. The FactoryWorks Pack's boiler extracts water
and inserts steam by name, so a segment holding the wrong fluid stalls the machine: nothing is taken
in, nothing is corrupted, and the player sees why. Craftworks' Assembler goes the other way on
purpose, with any connection serving any box (Craftworks ADRs 0015 and 0018), and would never set a
filter. No Consumer asks for one.

What a filter would add is refusing the connection itself, as Factorio does when a pipe touches a
machine's water input and its steam output. A filter on the port alone, refused like a mixed join,
catches only a segment that already holds fluid. Catching the empty case makes the filter a property
of the segment: a segment with a water-filtered port refuses to merge with one whose port wants
steam, and refuses a steam insert. That reaches the mixing rule in `SegmentGraph`, the waiting nodes,
what ADR 0003 saves per node, and every placement check, for a mistake the player can already see and
undo.

A Consumer that needs it later reopens the question with its case: which machine, which wrong
connection, and why a stalled machine isn't enough.

## Prior requests

- #1: "Fluid filter on FluidPort"
