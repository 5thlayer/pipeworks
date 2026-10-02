// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * server's (ADR 0003): the {@link StorageTankBlock#LEVEL} blockstate, its fill in steps, and the
 * fluid it is full of, sent as a {@link TankFluidPayload} only when that fluid changes.
 * {@link FluidSegments} marks a segment when its fill may have changed and calls {@link #update} on
 * the level tick, so a busy segment costs one comparison a tick and touches its nodes only when its
 * step or fluid changes.
 */
final class TankSteps {

    /** The steps a fill is shown in: {@link StorageTankBlock#LEVEL}'s maximum. */
    static final int STEPS = 15;

    /** What a segment was last shown as. A segment with no step shows no fluid. */
    private record Shown(int step, @Nullable String fluid) {

        static Shown of(SegmentGraph.Contents contents) {
            int step = SegmentGraph.step(contents.amount(), contents.capacity(), STEPS);
            return new Shown(step, step > 0 ? contents.fluid() : null);
        }
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
            if (!graph.exists(id)) {
                shown.remove(id);
                continue;
            }
            Shown now = Shown.of(graph.segment(id));
            Shown before = shown.put(id, now);
            if (now.equals(before)) {
                continue;
            }
            boolean fluidChanged = before == null || !Objects.equals(before.fluid(), now.fluid());
            for (long node : graph.nodesOf(id)) {
                show(level, BlockPos.of(node), now, fluidChanged, null);
            }
        }
        touched.clear();
    }

    /**
     * A player's client was just sent a chunk holding these nodes. Its tanks' blockstates are saved
     * with the chunk, but a segment can have changed while it was unloaded, and the client has not
     * been told any fluid.
     */
    void chunkSent(ServerLevel level, SegmentGraph graph, Set<Long> nodesInChunk, ServerPlayer player) {
        for (long node : nodesInChunk) {
            if (graph.contains(node)) {
                show(level, BlockPos.of(node), Shown.of(graph.contents(node)), true, player);
            }
        }
    }

    /**
     * Sets the tank at {@code pos}, if it is one, to {@code now}. Its fluid, when {@code sendFluid},
     * goes to the clients before the blockstate does, so the colour is there when the chunk next
     * draws: to {@code only}, or with a null {@code only} to everyone tracking the tank.
     */
    private static void show(ServerLevel level, BlockPos pos, Shown now, boolean sendFluid, @Nullable ServerPlayer only) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(StorageTankBlock.LEVEL)) {
            return;
        }
        if (sendFluid) {
            TankFluidPayload payload = new TankFluidPayload(pos, now.fluid() == null ? "" : now.fluid());
            if (only != null) {
                if (now.fluid() != null) {
                    PacketDistributor.sendToPlayer(only, payload);
                }
            } else {
                PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(pos), payload);
            }
        }
        if (state.getValue(StorageTankBlock.LEVEL) != now.step()) {
            level.setBlock(pos, state.setValue(StorageTankBlock.LEVEL, now.step()), Block.UPDATE_CLIENTS);
        }
    }
}
