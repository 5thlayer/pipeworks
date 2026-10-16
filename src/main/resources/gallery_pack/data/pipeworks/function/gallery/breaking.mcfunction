# A Pump on a water source filling a long run with a storage tank at each end, and a pickaxe in a chest.
# A pipe broken splits the run: the far tank keeps its share and stops rising while the near one fills.
function pipeworks:gallery/clear
execute positioned ~5 ~ ~-6 run function pipeworks:gallery/pool {fluid:"water"}
setblock ~6 ~ ~-6 pipeworks:pump[facing=east]
setblock ~7 ~ ~-6 pipeworks:pipe
setblock ~8 ~ ~-6 pipeworks:pipe
setblock ~9 ~ ~-6 pipeworks:pipe
setblock ~10 ~ ~-6 pipeworks:pipe
setblock ~11 ~ ~-6 pipeworks:pipe
setblock ~12 ~ ~-6 pipeworks:pipe
setblock ~13 ~ ~-6 pipeworks:pipe
setblock ~14 ~ ~-6 pipeworks:pipe
setblock ~15 ~ ~-6 pipeworks:pipe
setblock ~16 ~ ~-6 pipeworks:pipe
setblock ~17 ~ ~-6 pipeworks:pipe
setblock ~18 ~ ~-6 pipeworks:pipe
setblock ~19 ~ ~-6 pipeworks:pipe
setblock ~9 ~1 ~-6 pipeworks:storage_tank
setblock ~20 ~ ~-6 pipeworks:storage_tank
setblock ~14 ~ ~-3 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:iron_pickaxe",count:1}]}
function pipeworks:gallery/sign {scene:"breaking"}
