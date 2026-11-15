---
status: accepted
---

# The in-line pump moves fluid between segments and their fluid inventories

ADR 0004 keeps a pipe's connection to a fluid inventory passive: fluid crosses only when the
inventory's own block moves it. Many blocks never do. Theurgy's Sal Ammoniac Tank and Liquefaction
Cauldron only expose a handler, as does the vanilla cauldron NeoForge wraps, so a chain between two
of them can't be piped at all (#39). ADR 0004 left moving fluid to the in-line pump (#10), which was
closed as not planned and, as Factorio's, only moved fluid from one segment to another. This amends
ADR 0004's "a connection to a fluid inventory stays passive" for the segments an in-line pump serves.

## Decision

- **The in-line pump sits between two segments and is no node.** It moves fluid from the segment
  behind it to the one in front, adds no capacity, is never a waiting node, and opens only its back
  and front.
  Where the block behind or in front is a fluid inventory itself, it drains or fills it directly;
  one that takes or gives only whole buckets, as a cauldron does, gets its bucket, and the pump then
  waits it off at its rate, as ADR 0006 has the Pump wait after a block taken whole.
- **It reaches through its segments, as Create's mechanical pump reaches through its pipes.** Each
  tick the segment behind refills from every fluid inventory it touches through an open side, as far
  as it has room; the pump moves up to its rate; the segment in front empties into every fluid
  inventory it touches, as far as they accept. The inventories take turns, a different one first
  each tick. A segment no in-line pump serves stays passive, as ADR 0004 has it, and the closed side
  is what keeps an inventory out of reach.
- **Only the pump's own move is capped.** A cauldron takes and gives only whole buckets (NeoForge's
  `CauldronWrapper` moves the steps its levels allow), so a 60 mB cap on the reach would never fill
  or drain one. The two segments are the buffers, and the throughput is still the rate. A segment in
  front reaches an inventory only once it holds that inventory's smallest step: a cauldron wants
  ten pipes or a tank, ADR 0006's bucket rule again.
- **The rate is a server config, `inlinePumpRate`, Factorio's 60 mB a tick by default**, read every
  tick. Unlike the Pump's frozen numbers (ADR 0006), this one is a pack's to balance against the
  machines it pipes.
- **It never mixes.** The segment behind draws only its own fluid, or the first fluid offered that
  would not mix while it is empty; the segment in front inserts only its own. Each move is one
  transaction that commits only when its extract and insert agree.
- **A segment between two pumps, in front of one and behind another, is a conduit** and reaches no
  inventory; a loop whose back and front are one segment moves nothing. A loop through one machine,
  out of it and back, is allowed.
- **Facing sets the direction**: it points where the player looks when placing it, and an item in
  `pipeworks:closes_sides` reverses it. It takes no power, and ships a recipe of a pipe, a piston
  and a pipe in a row, which a pack may replace.

## Considered options

- **A pipe side toggled to draw or insert.** No extra block, and control per side. It needs a side
  state beyond open and closed, a way to cycle it beside the stick's toggle, and something for
  Dismantle to keep; and each side would add a rate of its own.
- **An Extractor and an Inserter, two ports facing one inventory each.** The cheapest in code, but a
  chain needs both, usually with a pipe between them.
- **One port whose sides are configured to draw or insert.** Lower cost than a pipe side, but still
  a saved state per side and a way to set it, for what one in-line pump does through closed sides.

## Consequences

- Throughput is the pump's, however many inventories a segment touches; a pack raises it per pump
  through the config, and a player by placing more pumps.
- A machine a Consumer builds as a port is a node, not a fluid inventory, and is unaffected: it
  still fills and drains its segment itself.
