// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.inlinepump;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What tells the level's segments an in-line pump is there while its chunk is loaded. It holds and
 * saves nothing: the segments' level tick does the pumping, all pumps together, since whether a
 * segment reaches its inventories depends on every pump beside it (ADR 0009).
 */
public class InlinePumpBlockEntity extends BlockEntity {

    public InlinePumpBlockEntity(BlockPos pos, BlockState state) {
        super(PipeworksRegistries.INLINE_PUMP_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            FluidSegments.get(server).inlinePumps().add(worldPosition);
        }
    }

    /**
     * Removed or unloaded with its chunk: either way it pumps no more until it loads again. The segments
     * are read as they are, not settled, since a chunk unloading is no time to change them.
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            FluidSegments segments = server.getDataStorage().get(FluidSegments.TYPE);
            if (segments != null) {
                segments.inlinePumps().remove(worldPosition);
            }
        }
    }
}
