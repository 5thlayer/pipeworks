# A Pump on a water source filling a column of three storage tanks, whose glass rises step by step.
# Jade, which the gallery may not need, adds the segment's amount and capacity.
function pipeworks:gallery/clear
execute positioned ~12 ~ ~-6 run function pipeworks:gallery/pool {fluid:"water"}
setblock ~13 ~ ~-6 pipeworks:pump[facing=east]
setblock ~14 ~ ~-6 pipeworks:storage_tank
setblock ~14 ~1 ~-6 pipeworks:storage_tank
setblock ~14 ~2 ~-6 pipeworks:storage_tank
function pipeworks:gallery/sign {scene:"tank_level"}
