---
status: accepted
---

# Touching tanks draw as one tank group

A segment holds one fluid at one level across all its nodes (ADR 0002), and ADR 0003 had every
storage tank show the segment's step in its own block, inside its own frame and glass. A column of
three then reads as three tanks at the same level, not one tall tank filling from the bottom (#27).
This replaces ADR 0003's paragraph "A tank shows its segment's fill as a blockstate" where the two
differ; the fluid's colour, sent as before, is unchanged.

## Decision

- **A tank group draws as one tank.** Storage tanks that touch through open faces, on any of the six
  sides, form a tank group (see the glossary). The frame and glass between two tanks of a group are
  not drawn. Tanks look joined only when they share fluid: a waiting tank, linked to nothing, stays
  apart. A stick closes only a pipe's side (ADR 0004), so two touching tanks that both joined are
  always one group.
- **The server says which faces join**, as six connection properties on the tank's blockstate, set
  the way `redraw` sets a pipe's arms. The client draws what it is told, as ADR 0003 has it.
  Considered: a model that reads its neighbours while it renders, as connected textures do; it needs
  a custom model loader, and the client would guess at closed faces it cannot see.
- **A group shows the segment's fill fraction over its own volume, from the bottom up.** A segment
  of pipes and several groups still has one level; each group fills to that fraction of its own
  capacity, so two columns on one pipe both show half full when the segment is. The fill goes
  through the group's layers from its lowest Y, a layer holding as much as it has tanks, so a wide
  layer takes longer to fill; every tank in a layer shows the same step.
- **Each tank keeps its own `level`, 0 to 15**, now its own share of the group's fill: full below the
  surface, empty above it, and partly full at it. A column of three shows 45 steps rather than 15.
  The rule that a holding segment never shows 0 applies to the group, through its bottom layer, not
  to every tank. The server changes a tank's state only when its own step does, as before.
  Considered: every tank showing the segment's step with the seams gone; a column still reads as
  the same line repeated in bands, which is what #27 is about.

## Consequences

- What a stack shows is where the fluid would sit, not the segment's level: Jade, which reads the
  segment, gives one amount next to a stack drawn part full. That is accepted.
- The tank's blockstate grows from 16 states to 16 × 64, with matching multipart models.
- Tanks saved before this have no connection properties set; they are set again when their segments
  are rebuilt on load, as pipe arms are.
