# Clears a scene's plot, from ~2 to ~25 east and from ~-12 to ~0 south, 16 high, onto a stone floor:
# its blocks, then the items dropped, both those left lying and those the fills knock loose, such as
# the sign, whose floor goes before it does. structure_void first, which no scene places, so each fill
# changes a block, and water and lava go with the rest.
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=minecraft:item,dx=23,dy=16,dz=12] run kill @s
