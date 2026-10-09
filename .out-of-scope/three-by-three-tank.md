# A 3 by 3 storage tank

Pipeworks' storage tank is one block. It doesn't take Factorio's 3 by 3 footprint with four corner
connections.

## Why this is out of scope

A segment's node is one position with a mask of open faces (ADRs 0002 and 0003). A 3 by 3 tank needs
either a node that spans nine positions, which every join, split, waiting node and save would have to
learn, or eight footprint parts acting as ports of the origin that open only at the corners. Either
way the tank would stand on Groundworks' footprint (Groundworks ADR 0009), the Library's first
dependency, and the footprint's origin usually carries a block entity where ADR 0003 keeps the tank a
plain block.

What it would buy is the look of Factorio's tank. The volume is already the pack's to set: the tank's
capacity is a server config (#2), so a pack that wants Factorio's 25,000 mB over nine blocks sets one
block to about 2,778.

ADR 0003's paragraph "The storage tank is one block" records the decision.

## Prior requests

- #3: "Storage tank on Factorio's 3x3 footprint"
