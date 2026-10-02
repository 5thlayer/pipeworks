// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.api;

import io.github._5thlayer.pipeworks.FluidSegments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** What a Consumer that plans pipes before placing them asks, such as a drag that lays a run. */
public final class FluidPipes {

    private FluidPipes() {
    }

    /**
     * Whether a pipe placed at {@code pos} would link on {@code side}: the arm it would draw. The
     * server answers as placement does, so a pipe would not link to a node waiting outside every
     * segment, and a pipe that would join two fluids links nowhere. The client cannot tell and
     * answers whether the block on that side is a pipe or tank, or a port that opens towards it.
     * Only the world is read, so a planner adds the links between the pipes it plans itself, and a
     * run that would join two fluids only once laid whole is not caught here.
     */
    public static boolean wouldLink(Level level, BlockPos pos, Direction side) {
        if (level instanceof ServerLevel server) {
            return FluidSegments.get(server).wouldLink(pos, side);
        }
        BlockPos beside = pos.relative(side);
        return level.getBlockState(beside).getBlock() instanceof FluidSegments.SegmentBlock
                || level.getBlockEntity(beside) instanceof FluidPort port && port.connectsOn(side.getOpposite());
    }
}
