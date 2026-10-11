---
status: accepted
---

# A pipe side closes with a stick, or the items a pack names

Two parallel pipes merge into one segment (ADR 0002), and the **closed side** (#9) keeps them apart:
a player right-clicks a pipe with an item in `pipeworks:closes_sides`, or a Consumer's own tool calls
the API. A sneak-click is
left to Groundworks' Dismantle, which a pack binds to the same wrenches.

**The tag ships a stick.** `pipeworks:closes_sides` holds `minecraft:stick`, so a player with
Groundworks and Pipeworks alone can close a side (#15, FactoryWorks ADR-0127). A pack that wants
other items replaces the tag (`"replace": true`); a pack that adds to it keeps the stick too. This
amends the first decision, a tag that shipped empty with a stick standing in while it stayed so:
that meant a pack could not tell the stand-in from a choice.

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

**Considered.** An empty tag, which the Library first shipped: a player on Groundworks alone could
not close a side, and Craftworks' Assembler needs closed sides to keep its fluid connections apart.
Leaving it out: Pipeworks would have nothing to offer until pipe-to-ground lands, and a pack with no
room for spacing would have no way to keep two runs apart. A plug model on a closed side: a third
value per arm, 3⁶ states for a pipe instead of 2⁶, for something Jade already shows.
