---
status: accepted
---

# A pipe side closes with a pack's items, or a stick without a pack

Two parallel pipes merge into one segment (ADR 0002), and the **closed side** (#9) keeps them apart:
a player right-clicks a pipe with an item in `pipeworks:closes_sides`, or a Consumer's own tool calls
the API. A sneak-click is
left to Groundworks' Dismantle, which a pack binds to the same wrenches.

**A stick closes a side when no pack names an item.** The tag ships empty, and while it is empty a
stick does what an item in it would (#15). Pipeworks is played without a pack too, from Modrinth and
CurseForge, and a player there had no way to close a side. A pack that names its own items in the tag
gets only those, with no stick beside them, so it never has to replace the tag to be rid of it.
Considered: shipping the stick in the tag. A pack that adds its own tool with `"replace": false`, as
the FactoryWorks Pack does with its Engineer's Picks, would keep the stick as well.

**What a closed side is.** It opens no face: the node's mask, already saved with the level (ADR 0003),
loses that bit, so the pipe links to no node there and exposes no fluid handler on that side. A
connection to a fluid inventory stays passive, as it was before: the neighbour moves fluid through the
handler, and the pipe moves none. Moving fluid is the in-line pump's job (#10). Closing the face
between two nodes closes it on both, so one click undoes it from either side. A side can be closed
with nothing beside it, which is what keeps a later run apart, and every side starts open. Opening a
side that would mix two fluids is refused, as such a placement is. A port's faces stay its own.

**A closed side is drawn as an empty one.** Every mod surveyed draws it that way
(`docs/research/pipe-connections-and-wrenches.md`), the blockstate keeps one arm property per side,
and Jade, which reads the server, names the closed sides.

**Considered.** Leaving it out: Pipeworks would have nothing to offer until pipe-to-ground lands, and a
pack with no room for spacing would have no way to keep two runs apart. A plug model on a closed
side: a third value per arm, 3⁶ states for a pipe instead of 2⁶, for something Jade already shows.
