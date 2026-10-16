# A run of pipes to take up, and a chest with a pickaxe and pipes to lay: sneak-click a pipe with the
# pickaxe and click another to take up the run between them, and drag a line of pipes to lay one.
function pipeworks:gallery/clear
setblock ~6 ~ ~-6 pipeworks:pipe
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
setblock ~20 ~ ~-6 pipeworks:pipe
setblock ~21 ~ ~-6 pipeworks:pipe
setblock ~14 ~ ~-3 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:iron_pickaxe",count:1},{Slot:1b,id:"pipeworks:pipe",count:32}]}
function pipeworks:gallery/sign {scene:"dismantle"}
