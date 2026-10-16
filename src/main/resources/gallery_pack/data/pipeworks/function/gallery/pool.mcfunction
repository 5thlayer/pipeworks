# A still source of $(fluid) where it is run, walled in glass on its four sides and over it, so it never
# flows out of its plot: a fluid outside the plot would survive the next clear. The scene then sets the
# Pump that draws it in place of one wall. No two pools share a wall, which would fail its setblock.
setblock ~1 ~ ~ minecraft:glass
setblock ~-1 ~ ~ minecraft:glass
setblock ~ ~ ~1 minecraft:glass
setblock ~ ~ ~-1 minecraft:glass
setblock ~ ~1 ~ minecraft:glass
$setblock ~ ~ ~ minecraft:$(fluid)
