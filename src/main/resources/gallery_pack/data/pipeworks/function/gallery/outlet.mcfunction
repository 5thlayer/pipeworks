# A Pump on a water source filling a run and a storage tank, which an Outlet empties a bucket at a time
# into the glass basin it faces. The run alone would hold less than the bucket the Outlet waits for.
function pipeworks:gallery/clear
execute positioned ~6 ~ ~-6 run function pipeworks:gallery/pool {fluid:"water"}
setblock ~7 ~ ~-6 pipeworks:pump[facing=east]
setblock ~8 ~ ~-6 pipeworks:pipe
setblock ~9 ~ ~-6 pipeworks:pipe
setblock ~10 ~ ~-6 pipeworks:pipe
setblock ~11 ~ ~-6 pipeworks:pipe
setblock ~12 ~ ~-6 pipeworks:pipe
setblock ~13 ~ ~-6 pipeworks:pipe
setblock ~14 ~ ~-6 pipeworks:pipe
setblock ~11 ~1 ~-6 pipeworks:storage_tank
setblock ~15 ~ ~-6 pipeworks:outlet[facing=east]
setblock ~17 ~ ~-6 minecraft:glass
setblock ~16 ~ ~-5 minecraft:glass
setblock ~16 ~ ~-7 minecraft:glass
setblock ~16 ~1 ~-6 minecraft:glass
function pipeworks:gallery/sign {scene:"outlet"}
