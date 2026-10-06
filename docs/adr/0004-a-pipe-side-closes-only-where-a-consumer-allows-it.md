---
status: accepted
---

# A pipe side closes only where a Consumer allows it

Factorio has no manual disconnects: two parallel pipes merge, and a player keeps them apart with
spacing or pipe-to-ground. ADR 0002 follows Factorio, so Pipeworks does the same unless a Consumer
asks otherwise. A Consumer that wants it gets the **closed side** (#9): a player right-clicks a pipe
with an item in `pipeworks:closes_sides`, which is empty by default, or the Consumer's own tool calls
the API. A sneak-click is left to Groundworks' Dismantle, which a pack binds to the same wrenches.

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

**Considered.** Closing always on: it departs from Factorio for every pack, not only the packs that
choose it. Leaving it out: Pipeworks would have nothing to offer until pipe-to-ground lands, and a
pack with no room for spacing would have no way to keep two runs apart. A plug model on a closed
side: a third value per arm, 3⁶ states for a pipe instead of 2⁶, for something Jade already shows.
