# Two Pumps: one on a water source, which stays and keeps giving, and one on a lava source, which it
# takes whole, as a bucket would.
function pipeworks:gallery/clear
execute positioned ~6 ~ ~-6 run function pipeworks:gallery/pool {fluid:"water"}
setblock ~7 ~ ~-6 pipeworks:pump[facing=east]
setblock ~8 ~ ~-6 pipeworks:pipe
setblock ~9 ~ ~-6 pipeworks:pipe
setblock ~10 ~ ~-6 pipeworks:pipe
setblock ~11 ~ ~-6 pipeworks:storage_tank
setblock ~14 ~ ~-6 pipeworks:storage_tank
setblock ~15 ~ ~-6 pipeworks:pipe
setblock ~16 ~ ~-6 pipeworks:pipe
setblock ~17 ~ ~-6 pipeworks:pipe
setblock ~18 ~ ~-6 pipeworks:pipe
setblock ~19 ~ ~-6 pipeworks:pipe
setblock ~20 ~ ~-6 pipeworks:pipe
execute positioned ~22 ~ ~-6 run function pipeworks:gallery/pool {fluid:"lava"}
setblock ~21 ~ ~-6 pipeworks:pump[facing=west]
function pipeworks:gallery/sign {scene:"pump"}
