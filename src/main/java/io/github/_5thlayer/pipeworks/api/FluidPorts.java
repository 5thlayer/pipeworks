// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.api;

import java.util.function.Predicate;

import io.github._5thlayer.pipeworks.FluidSegments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/** What a {@link FluidPort}'s block and block entity call to join, leave and use a segment. */
public final class FluidPorts {

    private FluidPorts() {
    }

    /**
     * Joins the segments of the pipes and tanks beside the port, on the faces both open. Safe to call
     * on every load: a port already in a segment stays where it is, so a world's fluid survives
     * a reload. A port that would have joined two fluids stays out of every segment.
     *
     * @return whether the port is in a segment
     */
    public static <T extends BlockEntity & FluidPort> boolean join(T port) {
        if (!(port.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        return FluidSegments.get(level).join(port.getBlockPos(), port.capacity(), maskOf(port::connectsOn));
    }

    /** Takes the port out of its segment. Call it when the block is removed, never when its chunk unloads. */
    public static void leave(ServerLevel level, BlockPos pos) {
        FluidSegments.get(level).leave(pos);
    }

    /**
     * Whether a port block may be placed at {@code pos}: false when it would join segments holding
     * different fluids. A block's {@code getStateForPlacement} returns null when this is false, as
     * the pipes' do. The client cannot tell and answers true.
     */
    public static boolean canJoin(Level level, BlockPos pos, Predicate<Direction> connectsOn) {
        return !(level instanceof ServerLevel server) || FluidSegments.get(server).canJoin(pos, maskOf(connectsOn));
    }

    /**
     * The segment the port at {@code pos} is in, as one fluid slot, or null if it is in none. Filling
     * it fills every pipe, tank and port of the segment at once.
     */
    public static @Nullable ResourceHandler<FluidResource> segment(Level level, BlockPos pos) {
        return level instanceof ServerLevel server ? FluidSegments.get(server).handlerAt(pos) : null;
    }

    private static int maskOf(Predicate<Direction> faces) {
        int mask = 0;
        for (Direction face : Direction.values()) {
            if (faces.test(face)) {
                mask |= FluidSegments.bit(face);
            }
        }
        return mask;
    }
}
