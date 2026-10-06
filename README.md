![Pipeworks](https://raw.githubusercontent.com/5thlayer/pipeworks/main/publish/pipeworks-cover.png)

Pipeworks adds pipes and a storage tank on Factorio 2.0's model of fluid: a connected run of pipes, tanks and machine ports is one **segment**, holding one fluid, with its capacity the sum of its parts and its flow instant. There is no pressure and no fluid creeping block to block. A run is full or empty as one.

**NeoForge, Minecraft 26.1.2 only.** Pipeworks is early work, so please report any issues you find on [GitHub](https://github.com/5thlayer/pipeworks/issues).

> **Pipeworks is built for modpacks and other mods.** It registers no fluid and no machine. It carries whatever fluid another mod puts in, as Beltworks carries any item, and the creative pipe lets you try it with nothing else installed.

## Features

- **Pipes and a storage tank.** A pipe holds 100 mB and a tank 25,000 mB, Factorio's volumes. Pipes draw an arm toward each pipe, tank or port they link to, and toward any block beside them that holds fluid.
- **One fluid per segment.** A pipe or tank that would join two segments holding different fluids is refused, with a message saying so.
- **Breaking splits fairly.** Breaking a pipe, tank or port splits its segment by capacity, and the broken block takes its share of the fluid. Segments are saved with the world and keep their fluid while their chunks are unloaded.
- **See the level.** A storage tank shows how full its segment is, in fifteen steps, as fluid of the right colour in its glass. With [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) installed, looking at a pipe, tank or port shows its segment's fluid, amount and capacity.
- **The creative pipe.** `pipeworks:creative_pipe`, in the creative tab, keeps its segment full of the fluid its screen sets. Click its slot with a bucket, or any item holding a fluid, to set it, and with an empty cursor to clear it. With JEI or EMI installed, a fluid can be dragged onto the slot.

Not yet: the in-line pump, pipe-to-ground, and a 3 by 3 tank.

## For pack authors

- **Closing a pipe's sides.** A pipe's side can be closed, to keep two runs apart or cut a pipe off from a block holding fluid. It is off until a pack allows it: put the items that should do it in the item tag `pipeworks:closes_sides`, which ships empty. A right-click on a pipe with one of them toggles the side clicked. Opening a side that would mix two fluids is refused.

## For mod authors

- **Ports.** A block entity joins the segments beside it by implementing `FluidPort` (`io.github._5thlayer.pipeworks.api`) and calling `FluidPorts.join(this)` from `onLoad`. Its block calls `FluidPorts.leave(level, pos)` when removed, and it fills and drains the segment through `FluidPorts.segment(level, pos)`, a NeoForge `ResourceHandler<FluidResource>`. A pipe or tank also exposes that handler as `Capabilities.Fluid.BLOCK`, except on a closed side.
- **Planning pipes.** `FluidPipes.wouldLink` and `FluidPipes.wouldDrawArm` read what a pipe placed at a position would link to and draw, for a mod that lays pipes for the player. `FluidPipes.toggle`, `close`, `open` and `isClosed` close sides from a mod's own tool.
- `CHANGELOG.md` lists what each version changed for a mod building against it.

## Dependencies

None required. **Jade**, **JEI** and **EMI** are optional, as above.

## Art

The pipe, tank and creative pipe models and textures are from [Oritech](https://github.com/Rearth/Oritech) v1.2.12 by Rearth, CC0 1.0. They are renamed and retextured to Pipeworks' ids; none is from the ArtOfTecharium or unused-textures sets Oritech credits under other licences. `REUSE.toml` records each file.

## Developing

A 5thlayer Library: a mod the FactoryWorks Pack consumes as a pinned local jar. See `CONTEXT.md` for the domain glossary. `sh ./gradlew build` runs the JUnit tests on a plain JVM, and `sh ./gradlew runGameTestServer` the game tests headless. The segment rules are `segment/SegmentGraph`, with no Minecraft types, so they are tested without a world.

## License

MIT, with the art above under CC0 1.0. Source is on [GitHub](https://github.com/5thlayer/pipeworks).
