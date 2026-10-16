# A Pump on a water source filling a run of pipes and a storage tank, whose glass shows it filling.
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
setblock ~15 ~ ~-6 pipeworks:pipe
setblock ~16 ~ ~-6 pipeworks:pipe
setblock ~17 ~ ~-6 pipeworks:pipe
setblock ~18 ~ ~-6 pipeworks:storage_tank
function pipeworks:gallery/sign {scene:"pipes"}
