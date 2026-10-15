---
status: accepted
---

# The Outlet sets a bucket down only into an empty or replaceable space

FactoryWorks ADR 0127 gives Pipeworks the **Outlet**: a pipe end that places its fluid in the world
as a source block, a bucket's worth at a time. With crude and lava it is how tar is automated, but
the Outlet knows nothing about either. It is the Pump's mirror (ADR 0006), and keeps the bucket
rule.

## Decision

- **The Outlet is a port** (`FluidPort`): it joins the segment of the pipes beside it and takes its
  fluid through the segment's fluid handler. It joins on every face but its front, which is where it
  sets fluid down, so a pipe there is never linked through it.
- **It faces away from the block it was placed against**, any of six ways, so a player clicking the
  end of a pipe aims it outward.
- **It sets a block down only once the segment holds 1,000 mB**, in one transaction with the
  extract, so a bucket taken is never lost and a part bucket stays in the pipes. It waits on nothing
  else: once the block is set, the space holds a source and the next one waits until it is gone.
- **The space must be empty or hold a replaceable block** (snow, grass, flowing fluid), **and no
  source block.** Replaceable is the block's own flag, not a bucket's wider rule, which takes any
  block that is not solid and so would break a pipe, a torch or a rail. A replaced block drops what
  it would if broken, once the source has taken its place. A source is never overwritten, of the same
  fluid or another, and the Outlet does not waterlog a block.
- **A fluid with no source block is left in the pipes.** No other fluid is singled out: it does not
  vaporize water in an ultrawarm dimension as a bucket does, since it is a machine and not a player.
- **It never draws fluid from the world.** It takes no power. The Module ships a crafting
  recipe (four pipes and a dispenser), which a pack may replace. This amends the first decision, no
  recipe, which left it to a pack before each Module played alone (FactoryWorks ADR-0127, #24).

## Consequences

- An Outlet beside fewer than ten pipes and no tank never fills to a bucket, the Pump's case in
  reverse.
- The Outlet places at most one block for as long as it stands: whatever takes the source (a Pump, a
  player's bucket, a tar reaction) is what sets the pace.
