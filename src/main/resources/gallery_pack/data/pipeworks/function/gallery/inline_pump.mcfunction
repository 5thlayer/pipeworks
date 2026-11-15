# A Pump on a water source filling a short run, which an in-line pump moves on, 60 mB a tick, into a
# storage tank and a run that ends at a cauldron. The run in front reaches the cauldron, which takes
# only whole buckets, once the tank and the pipes hold one.
function pipeworks:gallery/clear
execute positioned ~6 ~ ~-6 run function pipeworks:gallery/pool {fluid:"water"}
setblock ~7 ~ ~-6 pipeworks:pump[facing=east]
setblock ~8 ~ ~-6 pipeworks:pipe
setblock ~9 ~ ~-6 pipeworks:pipe
setblock ~10 ~ ~-6 pipeworks:pipe
setblock ~11 ~ ~-6 pipeworks:inline_pump[facing=east]
setblock ~12 ~ ~-6 pipeworks:storage_tank
setblock ~13 ~ ~-6 pipeworks:pipe
setblock ~14 ~ ~-6 pipeworks:pipe
setblock ~15 ~ ~-6 pipeworks:pipe
setblock ~16 ~ ~-6 minecraft:cauldron
function pipeworks:gallery/sign {scene:"inline_pump"}
