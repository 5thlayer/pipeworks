// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * The fluids a client has been told its storage tanks are full of, which the tank's colour reads
 * (ADR 0003). One map for the one level a client has open, emptied when that level unloads. Holds no
 * Minecraft client types, so the common payload can hand to it.
 */
public final class TankFluids {

    private static final Map<Long, Fluid> FLUIDS = new HashMap<>();

    private TankFluids() {
    }

    static void accept(Level level, BlockPos pos, String fluid) {
        Identifier id = Identifier.tryParse(fluid);
        Fluid known = id == null ? null : BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (known == null || known == Fluids.EMPTY) {
            FLUIDS.remove(pos.asLong());
        } else {
            FLUIDS.put(pos.asLong(), known);
        }
        // The colour is part of the chunk's mesh, which a new blockstate rebuilds but a new fluid at the same step does not.
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 0);
    }

    /** The fluid the tank at {@code pos} was last said to hold, or null for none. */
    public static @Nullable Fluid at(BlockPos pos) {
        return FLUIDS.get(pos.asLong());
    }

    public static void clear() {
        FLUIDS.clear();
    }
}
