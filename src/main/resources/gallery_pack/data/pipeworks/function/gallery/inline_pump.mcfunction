# A Pump on a water source filling a run and a storage tank, and an in-line pump at the run's end that
# fills the cauldron it faces. A cauldron takes only whole buckets, so the pump moves one at a time,
# once the run behind it holds one, which the tank lets it.
function pipeworks:gallery/clear
execute positioned ~6 ~ ~-6 run function pipeworks:gallery/pool {fluid:"water"}
setblock ~7 ~ ~-6 pipeworks:pump[facing=east]
setblock ~8 ~ ~-6 pipeworks:pipe
setblock ~9 ~ ~-6 pipeworks:pipe
setblock ~10 ~ ~-6 pipeworks:storage_tank
setblock ~11 ~ ~-6 pipeworks:pipe
setblock ~12 ~ ~-6 pipeworks:inline_pump[facing=east]
setblock ~13 ~ ~-6 minecraft:cauldron
function pipeworks:gallery/sign {scene:"inline_pump"}
