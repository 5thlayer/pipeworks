// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.dismantle;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.groundworks.DismantleFamily;
import io.github._5thlayer.groundworks.DismantleSpan;
import io.github._5thlayer.groundworks.Dismantles;
import io.github._5thlayer.groundworks.Refusal;
import io.github._5thlayer.groundworks.ShortestPath;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.block.FluidPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pipes as a Groundworks Dismantle Family: a span is the shortest path between its ends through
 * pipes that draw an arm toward each other (ADR 0005).
 */
public final class PipeFamily implements DismantleFamily {

    /** The blocks the family claims, so a pack can add another mod's pipe or take one out. */
    public static final TagKey<Block> PIPES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "dismantle/pipes"));

    private PipeFamily() {
    }

    public static void register() {
        Dismantles.register(new PipeFamily());
    }

    @Override
    public boolean claims(BlockState state) {
        return state.is(PIPES);
    }

    @Override
    public DismantleSpan span(Level level, BlockPos start, BlockPos end) {
        ShortestPath.Result<BlockPos> path = ShortestPath.between(start, end, new ShortestPath.Graph<>() {
            @Override
            public boolean member(BlockPos pos) {
                // A span stops at a chunk's edge rather than loading the next one.
                return level.isLoaded(pos) && claims(level.getBlockState(pos));
            }

            @Override
            public Iterable<BlockPos> neighbours(BlockPos pos) {
                List<BlockPos> around = new ArrayList<>(6);
                for (Direction side : Direction.values()) {
                    around.add(pos.relative(side));
                }
                return around;
            }

            @Override
            public boolean joined(BlockPos a, BlockPos b) {
                return PipeFamily.joined(level, a, b);
            }
        });
        return path.refusal() == null ? DismantleSpan.taking(path.path()) : DismantleSpan.refused(path.refusal());
    }

    /**
     * Two pipes are joined where each draws an arm toward the other. Between two pipes an arm is a
     * link, since a fluid inventory is no pipe, and the segments hold no query for two existing
     * nodes (ADR 0005).
     */
    static boolean joined(Level level, BlockPos a, BlockPos b) {
        for (Direction side : Direction.values()) {
            if (a.relative(side).equals(b)) {
                return FluidPipeBlock.drawsArm(level.getBlockState(a), side)
                        && FluidPipeBlock.drawsArm(level.getBlockState(b), side.getOpposite());
            }
        }
        return false;
    }

    @Override
    public Component message(Refusal refusal) {
        if (!(refusal instanceof ShortestPath.Refused refused)) {
            throw new IllegalArgumentException("the pipe family never refuses with " + refusal);
        }
        return Component.translatable(switch (refused) {
            case OUTSIDE_FAMILY -> "message.pipeworks.dismantle.outside_family";
            case NOT_JOINED -> "message.pipeworks.dismantle.not_joined";
            case TIED -> "message.pipeworks.dismantle.tied";
        });
    }
}
