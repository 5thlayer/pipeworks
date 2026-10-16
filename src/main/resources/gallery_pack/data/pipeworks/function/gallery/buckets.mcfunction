# An empty run of pipes and a storage tank, whose glass rises a step for about each two buckets, and a
# chest of buckets to fill and empty it with. Its one lava bucket goes in only while the run is empty,
# as the segment holds one fluid.
function pipeworks:gallery/clear
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
setblock ~18 ~ ~-6 pipeworks:storage_tank
setblock ~13 ~ ~-3 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:water_bucket",count:1},{Slot:1b,id:"minecraft:water_bucket",count:1},{Slot:2b,id:"minecraft:water_bucket",count:1},{Slot:6b,id:"minecraft:water_bucket",count:1},{Slot:7b,id:"minecraft:water_bucket",count:1},{Slot:8b,id:"minecraft:water_bucket",count:1},{Slot:3b,id:"minecraft:lava_bucket",count:1},{Slot:4b,id:"minecraft:bucket",count:1},{Slot:5b,id:"minecraft:bucket",count:1}]}
function pipeworks:gallery/sign {scene:"buckets"}
