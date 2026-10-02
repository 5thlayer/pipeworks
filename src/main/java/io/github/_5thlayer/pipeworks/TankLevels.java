// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github._5thlayer.pipeworks.block.StorageTankBlock;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * What a storage tank shows of its segment, which the client cannot read since segments are the
 * server's (ADR 0003): the {@link StorageTankBlock#LEVEL} blockstate, and the fluid it is full of,
 * sent as a {@link TankFluidPayload}. {@link FluidSegments} marks a segment when its fill may have
 * changed and calls {@link #update} on the level tick, so a busy segment costs one change a tick
 * and none until its step changes.
 */
final class TankLevels {

    /** The steps a fill is shown in: {@link StorageTankBlock#LEVEL}'s maximum. */
    static final int STEPS = 15;

    /** What a segment was last shown as: a fluid's change at the same step is a change. */
    private record Shown(int step, @Nullable String fluid) {
    }

    private final Set<Integer> touched = new HashSet<>();
    private final Map<Integer, Shown> shown = new HashMap<>();

    /** The segment's fill may have changed. */
    void touch(int segment) {
        touched.add(segment);
    }

    /** Segments merged or split, so the ids and what was shown for them no longer mean anything. */
    void forgetAll() {
        shown.clear();
    }

    /** Shows each marked segment's step in its tanks, for the segments whose step or fluid changed since. */
    void update(ServerLevel level, SegmentGraph graph) {
        if (touched.isEmpty()) {
            return;
        }
        for (int id : List.copyOf(touched)) {
            List<Long> nodes = graph.nodesOf(id);
            if (nodes.isEmpty()) {
                shown.remove(id);
                continue;
            }
            SegmentGraph.Contents contents = graph.segment(id);
            Shown now = new Shown(SegmentGraph.step(contents.amount(), contents.capacity(), STEPS), contents.fluid());
            if (!now.equals(shown.put(id, now))) {
                for (long node : nodes) {
                    show(level, BlockPos.of(node), now, null);
                }
            }
        }
        touched.clear();
    }

    /**
     * A player's client was just sent a chunk. Its tanks' blockstates are saved with the chunk, but a
     * segment can have changed while it was unloaded, and the client has not been told any fluid.
     */
    void chunkSent(ServerLevel level, SegmentGraph graph, Iterable<Long> nodes, ServerPlayer player, ChunkPos chunk) {
        for (long node : nodes) {
            BlockPos pos = BlockPos.of(node);
            if (graph.contains(node) && pos.getX() >> 4 == chunk.x() && pos.getZ() >> 4 == chunk.z()) {
                SegmentGraph.Contents contents = graph.contents(node);
                show(level, pos, new Shown(SegmentGraph.step(contents.amount(), contents.capacity(), STEPS), contents.fluid()), player);
            }
        }
    }

    /**
     * Sets the tank at {@code pos}, if it is one, to {@code now}: its fluid goes to the clients before
     * the blockstate does, so the colour is there when the chunk next draws. A null {@code only} is
     * everyone tracking the tank.
     */
    private static void show(ServerLevel level, BlockPos pos, Shown now, @Nullable ServerPlayer only) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(StorageTankBlock.LEVEL)) {
            return;
        }
        // Only an empty segment shows no fluid; a tank in one needs no entry on the client either.
        TankFluidPayload payload = new TankFluidPayload(pos, now.step() > 0 && now.fluid() != null ? now.fluid() : "");
        if (only != null) {
            if (now.step() > 0) {
                PacketDistributor.sendToPlayer(only, payload);
            }
        } else {
            PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(pos), payload);
        }
        if (state.getValue(StorageTankBlock.LEVEL) != now.step()) {
            level.setBlock(pos, state.setValue(StorageTankBlock.LEVEL, now.step()), Block.UPDATE_CLIENTS);
        }
    }
}
