# Pipeworks

Pipes and a storage tank for Minecraft 26.1.2 on NeoForge, on Factorio 2.0's model of fluid: a connected run of pipes, tanks and machine ports is one segment holding one fluid, its capacity the sum of its parts and its flow instant. Pipeworks registers no fluid; it carries whatever a mod puts in, as Beltworks carries any item.

A 5thlayer Library: a mod the FactoryWorks Pack consumes as a pinned local jar. See `CONTEXT.md` for the domain glossary.

## What it does

- A pipe holds 100 mB and a storage tank 25,000 mB, Factorio's volumes. Placing a block that would join two segments holding different fluids is refused.
- The creative tank (`pipeworks:creative_tank`, creative tab and `/give`, no recipe) is a storage tank that keeps its segment full of the fluid a bucket sets, so a pipe run can be tried with nothing else installed. Breaking it clears it.
- Breaking a pipe, tank or port splits its segment by capacity; the broken block takes its share of the fluid. Segments are saved with the level and keep their fluid through chunk unloading.
- A mod's block entity joins a segment by implementing `FluidPort` (`io.github._5thlayer.pipeworks.api`). It calls `FluidPorts.join(this)` from `onLoad`, its block calls `FluidPorts.leave(level, pos)` when removed, and it fills and drains the segment through `FluidPorts.segment(level, pos)`, a NeoForge `ResourceHandler<FluidResource>`. A pipe or tank also exposes that handler as `Capabilities.Fluid.BLOCK`.

Not yet: the in-line pump, pipe-to-ground, pipe drag-laying and Dismantle, and a 3 by 3 tank.

## Art

The pipe, tank and creative tank models and textures are from [Oritech](https://github.com/Rearth/Oritech) v1.2.12 by Rearth, CC0 1.0. They are renamed and retextured to this Library's ids; none is from the ArtOfTecharium or unused-textures sets Oritech credits under other licences. `REUSE.toml` records each file.

## Developing

`sh ./gradlew build` runs the JUnit tests on a plain JVM, and `sh ./gradlew runGameTestServer` the game tests headless. The segment rules are `segment/SegmentGraph`, with no Minecraft types, so they are tested without a world.
