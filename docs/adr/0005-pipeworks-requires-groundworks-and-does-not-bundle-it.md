---
status: accepted
---

# Pipeworks requires Groundworks and does not bundle it

The pipe Dismantle Family and drag-laying are bindings onto Groundworks' Dismantle and Stretch:
Groundworks runs the gesture, the stored start, the route and the charge, and a family or a leg
builder supplies only what is particular to its blocks. They lived in FactoryWorks Core and move here
with the pipes they read (FactoryWorks ADR 0127, ADR 0128).

## Decision

**Groundworks is a required dependency, installed beside Pipeworks.** It is Wireworks' choice in its
ADR 0010, and `build.gradle` compiles and runs against it without `jarJar`. `neoforge.mods.toml`
requires `groundworksRange` (`[0.5.5,0.6)`), and the Modrinth and CurseForge uploads list Groundworks
as a required dependency. The lower bound is the version built against and the upper bound the next
minor, so a Groundworks patch needs no Pipeworks release.

**Considered: an optional dependency.** Groundworks has no soft integration point: a family and a
leg builder are registered by calling `Dismantles.register` and `Stretches.register`, which a mod
without Groundworks cannot name. Keeping Pipeworks loadable alone would put both behind a class that
loads only when Groundworks is present, and the Pump would still want Groundworks' placement plan.
FactoryWorks ADR 0127 makes Groundworks the one dependency every Module shares, so a pack that takes
Pipeworks has it already. Rejected.

## Consequences

- A player installing Pipeworks alone must also install Groundworks. The changelog says so.
- `groundworks:dismantles` gains `#minecraft:pickaxes` and, if present, `#c:tools/wrench`, as in
  Beltworks, so a sneak-click with a pickaxe stores a pipe's start. A pack trims the tag.
- The pipe family's tag is `pipeworks:dismantle/pipes`, holding the pipe and the creative pipe.
  A span is the shortest path through pipes that draw an arm toward each other, so a closed side
  (ADR 0004) ends it.
