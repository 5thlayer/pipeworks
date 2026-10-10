---
status: accepted
---

# Pipeworks uses Factorio 2.0's fluid segments

> **Imported from FactoryWorks ADR-0110**, unchanged apart from renumbering. Pipeworks is new code
> written for the Pack, not carved from it. Issue numbers (`#n`) and ADRs cited as FactoryWorks
> refer to 5thlayer/factoryworks. [ADR 0003](0003-a-removed-node-takes-its-fluid-and-segments-are-saved-with-the-level.md)
> records where Pipeworks departs from this decision; the glossary in `GLOSSARY.md` has the current terms.

A connected run of pipes, tanks and machine fluid ports is one fluid segment: a single fluid box
holding one fluid, its capacity the sum of its parts, and flow inside it instant. Throughput is
limited only by the pumps and by the endpoints that fill and drain the segment. This is Factorio 2.0's
model (ADR-0109).

**Considered.** Per-block pressure and flow, as in Factorio 1.1 and Oritech. Rejected: it is not the
Factorio the Pack ports, it makes throughput depend on run length in ways players have to tune
around, and it costs a tick update per pipe where a segment costs one per network.

**Consequences.** Mixing two fluids in one segment is refused at placement, as in Factorio. The
in-line Pump (#293) is what separates and caps segments, so it belongs to Pipeworks.
