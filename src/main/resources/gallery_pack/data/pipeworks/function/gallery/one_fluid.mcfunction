# A run of water and a run of lava, one block apart, each filled by its own Pump, and a chest of pipes:
# a pipe set in the gap would join them, so it is refused.
function pipeworks:gallery/clear
execute positioned ~5 ~ ~-6 run function pipeworks:gallery/pool {fluid:"water"}
setblock ~6 ~ ~-6 pipeworks:pump[facing=east]
setblock ~7 ~ ~-6 pipeworks:pipe
setblock ~8 ~ ~-6 pipeworks:pipe
setblock ~9 ~ ~-6 pipeworks:pipe
setblock ~10 ~ ~-6 pipeworks:pipe
setblock ~11 ~ ~-6 pipeworks:pipe
setblock ~12 ~ ~-6 pipeworks:pipe
setblock ~14 ~ ~-6 pipeworks:pipe
setblock ~15 ~ ~-6 pipeworks:pipe
setblock ~16 ~ ~-6 pipeworks:pipe
setblock ~17 ~ ~-6 pipeworks:pipe
setblock ~18 ~ ~-6 pipeworks:pipe
setblock ~19 ~ ~-6 pipeworks:storage_tank
execute positioned ~21 ~ ~-6 run function pipeworks:gallery/pool {fluid:"lava"}
setblock ~20 ~ ~-6 pipeworks:pump[facing=west]
setblock ~13 ~ ~-3 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"pipeworks:pipe",count:4}]}
function pipeworks:gallery/sign {scene:"one_fluid"}
