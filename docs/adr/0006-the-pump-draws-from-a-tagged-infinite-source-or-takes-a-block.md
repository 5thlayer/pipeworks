---
status: accepted
---

# The Pump leaves a source in the infinite tag and takes any other whole

FactoryWorks' Offshore Pump was the only origin of water, 1,200 mB a second from Factorio's own
speed, and it was sited on a "natural" source: a whitelist of two fluids, justified by a pack that
let no source form. FactoryWorks ADR 0127 gives the machine to Pipeworks as the **Pump**, in a mod
that plays alone in vanilla, where sources do form.

## Decision

- **A source in the fluid tag `pipeworks:infinite_sources` is never taken.** The tag ships with
  `minecraft:water`, and a pack adds more. The Pump adds 60 mB a tick (Factorio's 20 per tick,
  1,200 mB a second) as far as its segment has room.
- **Any other still source is taken whole, as a bucket would.** The block goes through its own
  `BucketPickup` once the segment has room for 1,000 mB, in one transaction with the insert, so a
  block taken is never lost. The Pump then waits 17 ticks, 1,000 mB at 60 mB a tick rounded up, so
  the rate holds on average.
- **The fluid is read each tick, not recorded at placement.** The Pump draws the first source beside
  it that is the segment's own fluid, or the first of any if the segment is empty, so it never mixes.
- **Placement is refused with no still source of any fluid beside it**, in the plan, so the preview
  draws it red and nothing is consumed. Facing is cosmetic. It takes no power and has no recipe.
- The numbers are Pipeworks' own, frozen from Factorio's (FactoryWorks ADR 0115) and held by
  `PumpRuleTest`, not read from a corpus.

## Consequences

- A finite fluid fills a segment only if the segment holds 1,000 mB of room: ten pipes or one tank.
  A smaller segment keeps the source in the world. This is the bucket rule taken literally.
- The Pump exposes no fluid handler of its own, so it needs no per-slot guard on its faces
  (libworks `GuardedResourceHandler`, libworks#8).
- The barrel and the oil-chain fluids FactoryWorks Core carried are dropped, not ported.
