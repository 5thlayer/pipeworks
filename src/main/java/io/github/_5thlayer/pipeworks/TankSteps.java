// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github._5thlayer.pipeworks.block.StorageTankBlock;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import io.github._5thlayer.pipeworks.segment.TankGroupFill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * What a storage tank shows of its segment, which the client cannot read since segments are the
 * server's (ADRs 0003 and 0008): the {@link StorageTankBlock#LEVEL} blockstate, its share of its tank
 * group's fill in steps, and the fluid it is full of, sent as a {@link TankFluidPayload} only when
 * that fluid changes. {@link FluidSegments} marks a segment when its fill may have changed and calls
 * {@link #update} on the level tick, so a busy segment costs a comparison a group a tick and touches
 * its tanks only when a group's fill or the fluid changes.
 */
final class TankSteps {

    /** A tank group: the nodes of its tanks. */
    private record Group(List<Long> tanks) {

        int[] heights() {
            return tanks.stream().mapToInt(tank -> BlockPos.of(tank).getY()).toArray();
        }
    }

    /** What a segment was last shown as: the {@link TankGroupFill#fifteenths} of each group, and its fluid. */
    private record Shown(Map<Group, Integer> fifteenths, @Nullable String fluid) {

        static Shown of(SegmentGraph.Contents contents, List<Group> groups) {
            Map<Group, Integer> fine = new LinkedHashMap<>();
            for (Group group : groups) {
                fine.put(group, TankGroupFill.fifteenths(contents.amount(), contents.capacity(), group.tanks().size()));
            }
            return new Shown(fine, contents.amount() > 0 ? contents.fluid() : null);
        }
    }

    private final Set<Integer> touched = new HashSet<>();
    private final Map<Integer, Shown> shown = new HashMap<>();
    /** The tank groups of each segment, found once until its tanks may have changed. */
    private final Map<Integer, List<Group>> groups = new HashMap<>();

    /** The segment's fill may have changed. */
    void touch(int segment) {
        touched.add(segment);
    }

    /** Segments merged or split, so the ids and what was shown for them no longer mean anything. */
    void forgetAll() {
        shown.clear();
        groups.clear();
    }

    /**
     * A chunk holding some of the segment's nodes loaded, so tanks its groups were found without may
     * be there now, their steps saved with the chunk: they are found again, and every tank of the
     * segment is shown on the next tick. Its fluid is kept, so it is not sent again to clients that
     * have it; a client sent the chunk is told it then.
     */
    void reshape(int segment) {
        groups.remove(segment);
        Shown before = shown.get(segment);
        if (before != null) {
            shown.put(segment, new Shown(Map.of(), before.fluid()));
        }
        touched.add(segment);
    }

    /** Shows each marked segment's fill in its tanks, for the segments whose groups' fill or fluid changed since. */
    void update(ServerLevel level, SegmentGraph graph) {
        if (touched.isEmpty()) {
            return;
        }
        for (int id : List.copyOf(touched)) {
            if (!graph.exists(id)) {
                shown.remove(id);
                groups.remove(id);
                continue;
            }
            List<Group> found = groups.computeIfAbsent(id, segment -> groupsOf(level, graph, segment));
            Shown now = Shown.of(graph.segment(id), found);
            Shown before = shown.put(id, now);
            if (now.equals(before)) {
                continue;
            }
            boolean fluidChanged = before == null || !Objects.equals(before.fluid(), now.fluid());
            now.fifteenths().forEach((group, fill) -> {
                int[] steps = TankGroupFill.steps(group.heights(), fill);
                for (int t = 0; t < steps.length; t++) {
                    show(level, BlockPos.of(group.tanks().get(t)), steps[t], now.fluid(), fluidChanged);
                }
            });
        }
        touched.clear();
    }

    /**
     * A player's client was just sent a chunk holding these nodes: it has not been told the fluid of
     * its tanks. Their steps are saved with the chunk, and set again on the next tick if they fell
     * behind, since a chunk loading {@linkplain #reshape reshapes} its segments.
     */
    void chunkSent(ServerLevel level, SegmentGraph graph, Set<Long> nodesInChunk, ServerPlayer player) {
        for (long node : nodesInChunk) {
            if (!graph.contains(node)) {
                continue;
            }
            SegmentGraph.Contents contents = graph.contents(node);
            if (contents.amount() > 0 && isLoadedTank(level, BlockPos.of(node))) {
                PacketDistributor.sendToPlayer(player, new TankFluidPayload(BlockPos.of(node), contents.fluid()));
            }
        }
    }

    /**
     * The loaded tanks of a segment in their groups: tanks beside each other that the segment links.
     * A tank in an unloaded chunk is left out until it loads.
     */
    private static List<Group> groupsOf(ServerLevel level, SegmentGraph graph, int segment) {
        Set<Long> tanks = new HashSet<>();
        for (long node : graph.nodesOf(segment)) {
            if (isLoadedTank(level, BlockPos.of(node))) {
                tanks.add(node);
            }
        }
        List<Group> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long start : tanks) {
            if (!seen.add(start)) {
                continue;
            }
            List<Long> members = new ArrayList<>();
            ArrayDeque<Long> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                long tank = queue.poll();
                members.add(tank);
                for (Direction face : Direction.values()) {
                    long other = BlockPos.of(tank).relative(face).asLong();
                    if (tanks.contains(other) && graph.linked(tank, other) && seen.add(other)) {
                        queue.add(other);
                    }
                }
            }
            found.add(new Group(List.copyOf(members)));
        }
        return found;
    }

    private static boolean isLoadedTank(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof StorageTankBlock;
    }

    /**
     * Sets the tank at {@code pos}, if it is one, to {@code step} of {@code fluid}. Its fluid, when
     * {@code sendFluid}, goes to the clients tracking it before the blockstate does, so the colour is
     * there when the chunk next draws.
     */
    private static void show(ServerLevel level, BlockPos pos, int step, @Nullable String fluid, boolean sendFluid) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(StorageTankBlock.LEVEL)) {
            return;
        }
        if (sendFluid) {
            PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(pos),
                    new TankFluidPayload(pos, fluid == null ? "" : fluid));
        }
        if (state.getValue(StorageTankBlock.LEVEL) != step) {
            level.setBlock(pos, state.setValue(StorageTankBlock.LEVEL, step), Block.UPDATE_CLIENTS);
        }
    }
}
