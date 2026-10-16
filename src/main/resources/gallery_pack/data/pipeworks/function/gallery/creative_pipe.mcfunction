# A creative pipe, its fluid unset, feeding a run and two storage tanks, and a chest of buckets whose
# fluid its screen's slot takes.
function pipeworks:gallery/clear
setblock ~8 ~ ~-6 pipeworks:creative_pipe
setblock ~9 ~ ~-6 pipeworks:pipe
setblock ~10 ~ ~-6 pipeworks:pipe
setblock ~11 ~ ~-6 pipeworks:pipe
setblock ~12 ~ ~-6 pipeworks:pipe
setblock ~13 ~ ~-6 pipeworks:pipe
setblock ~14 ~ ~-6 pipeworks:pipe
setblock ~15 ~ ~-6 pipeworks:pipe
setblock ~16 ~ ~-6 pipeworks:pipe
setblock ~17 ~ ~-6 pipeworks:storage_tank
setblock ~17 ~1 ~-6 pipeworks:storage_tank
setblock ~12 ~ ~-3 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:water_bucket",count:1},{Slot:1b,id:"minecraft:lava_bucket",count:1}]}
function pipeworks:gallery/sign {scene:"creative_pipe"}
