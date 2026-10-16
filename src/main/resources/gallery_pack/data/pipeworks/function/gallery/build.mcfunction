# The Pipeworks Gallery: a scene of each README feature, in README order, each building and clearing a
# plot of its own, 24 blocks wide and 13 deep (libworks ADR 0010). The scenes stand in rows of three,
# read west to east and then north, each row 16 blocks north of the one before, so the whole gallery
# stays near enough to where it is run for its pumps to keep working.
function pipeworks:gallery/pipes
execute positioned ~24 ~ ~ run function pipeworks:gallery/buckets
execute positioned ~48 ~ ~ run function pipeworks:gallery/one_fluid
execute positioned ~ ~ ~-16 run function pipeworks:gallery/breaking
execute positioned ~24 ~ ~-16 run function pipeworks:gallery/tank_level
execute positioned ~48 ~ ~-16 run function pipeworks:gallery/creative_pipe
execute positioned ~ ~ ~-32 run function pipeworks:gallery/pump
execute positioned ~24 ~ ~-32 run function pipeworks:gallery/outlet
execute positioned ~48 ~ ~-32 run function pipeworks:gallery/dismantle
