// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.inlinepump;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksConfig;
import io.github._5thlayer.pipeworks.inlinepump.InlinePumpRule.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * One level's loaded in-line pumps, and what they move each tick (ADR 0009), in three steps: every
 * segment behind a pump refills from the fluid inventories it touches, as far as it has room; every
 * pump moves up to its rate from behind it to in front of it; every segment in front of a pump empties
 * into the inventories it touches, as far as they accept. Only the middle step is capped, so an
 * inventory that takes or gives only whole buckets, as a cauldron does, still fills and drains, and the
 * segments are the buffers. A segment between two pumps passes fluid on and reaches no inventory. Not
 * saved: each pump tells the segments it is there as it loads.
 */
public final class InlinePumps {

    /** One end of a pump: a segment, with its id, or a fluid inventory, with none. */
    private record End(ResourceHandler<FluidResource> handler, @Nullable Integer segment) {
    }

    private record Pump(long pos, End back, End front) {
    }

    private final Set<Long> pumps = new LinkedHashSet<>();
    /** The ticks each pump waits after a move above its rate. */
    private final Map<Long, Integer> waiting = new HashMap<>();
    /** Counts the level ticks pumps ran in, which picks the inventory that goes first. */
    private long turn;

    public void add(BlockPos pos) {
        pumps.add(pos.asLong());
    }

    public void remove(BlockPos pos) {
        pumps.remove(pos.asLong());
        waiting.remove(pos.asLong());
    }

    /** Runs the three steps for every loaded pump. Called once a level tick, with no transaction open. */
    public void tick(ServerLevel level, FluidSegments segments) {
        if (pumps.isEmpty()) {
            return;
        }
        List<Pump> serving = new ArrayList<>();
        Set<Integer> behind = new HashSet<>();
        Set<Integer> inFront = new HashSet<>();
        Map<Integer, ResourceHandler<FluidResource>> handlers = new HashMap<>();
        for (long node : List.copyOf(pumps)) {
            Pump pump = pump(level, segments, node);
            if (pump == null) {
                continue;
            }
            serving.add(pump);
            note(pump.back(), behind, handlers);
            note(pump.front(), inFront, handlers);
        }
        Map<Integer, Role> roles = new HashMap<>();
        for (int segment : handlers.keySet()) {
            roles.put(segment, InlinePumpRule.role(behind.contains(segment), inFront.contains(segment)));
        }
        Set<Integer> reaching = new HashSet<>();
        roles.forEach((segment, role) -> {
            if (role == Role.BEHIND || role == Role.IN_FRONT) {
                reaching.add(segment);
            }
        });
        roles.keySet().retainAll(reaching);
        Map<Integer, List<ResourceHandler<FluidResource>>> inventories = segments.inventoriesOf(level, reaching);
        long tick = turn++;
        reach(Role.BEHIND, roles, handlers, inventories, tick);
        int rate = PipeworksConfig.inlinePumpRate();
        // Pumps sharing a segment behind them take turns too, so a slow refill starves none of them.
        for (Pump pump : InlinePumpRule.inTurn(serving, tick)) {
            pumpOnce(pump, rate);
        }
        reach(Role.IN_FRONT, roles, handlers, inventories, tick);
    }

    /**
     * Has every segment in {@code role} reach its inventories, each in turn: one behind a pump refills
     * from them, one in front empties into them, as far as they go, uncapped.
     */
    private static void reach(Role role, Map<Integer, Role> roles, Map<Integer, ResourceHandler<FluidResource>> handlers,
            Map<Integer, List<ResourceHandler<FluidResource>>> inventories, long tick) {
        roles.forEach((segment, its) -> {
            if (its != role) {
                return;
            }
            ResourceHandler<FluidResource> pipes = handlers.get(segment);
            for (var inventory : InlinePumpRule.inTurn(inventories.getOrDefault(segment, List.of()), tick)) {
                if (role == Role.BEHIND) {
                    move(inventory, pipes, Integer.MAX_VALUE);
                } else {
                    move(pipes, inventory, Integer.MAX_VALUE);
                }
            }
        });
    }

    private static void note(End end, Set<Integer> role, Map<Integer, ResourceHandler<FluidResource>> handlers) {
        if (end.segment() != null) {
            role.add(end.segment());
            handlers.putIfAbsent(end.segment(), end.handler());
        }
    }

    /** The pump at {@code node} with both its ends, or null if it is not loaded, gone, or lacks an end. */
    private @Nullable Pump pump(ServerLevel level, FluidSegments segments, long node) {
        BlockPos pos = BlockPos.of(node);
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof InlinePumpBlock)) {
            remove(pos);
            return null;
        }
        Direction facing = state.getValue(InlinePumpBlock.FACING);
        End back = end(level, segments, pos.relative(facing.getOpposite()), facing);
        End front = end(level, segments, pos.relative(facing), facing.getOpposite());
        return back == null || front == null ? null : new Pump(node, back, front);
    }

    /**
     * What is at {@code pos} for a pump beside it on {@code face}, a face of that block: a segment whose
     * node opens that face, or a fluid inventory exposing a handler there. A node that waits, or has
     * closed that face, is neither.
     */
    private static @Nullable End end(ServerLevel level, FluidSegments segments, BlockPos pos, Direction face) {
        Integer segment = segments.segmentOpenOn(level, pos, face);
        if (segment != null) {
            ResourceHandler<FluidResource> handler = segments.handlerAt(level, pos, face);
            return handler == null ? null : new End(handler, segment);
        }
        if (!FluidSegments.isFluidInventory(level, pos, face)) {
            return null;
        }
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, face);
        return handler == null ? null : new End(handler, null);
    }

    /**
     * Moves up to {@code rate} from behind the pump to in front of it. An end that moves nothing that
     * small, a cauldron taking whole buckets, gets what it will take, and the pump then waits so the
     * rate holds on average.
     */
    private void pumpOnce(Pump pump, int rate) {
        Integer wait = waiting.get(pump.pos());
        if (wait != null) {
            if (wait <= 1) {
                waiting.remove(pump.pos());
            } else {
                waiting.put(pump.pos(), wait - 1);
            }
            return;
        }
        if (pump.back().segment() != null && pump.back().segment().equals(pump.front().segment())) {
            return;
        }
        if (move(pump.back().handler(), pump.front().handler(), rate) > 0) {
            return;
        }
        int moved = move(pump.back().handler(), pump.front().handler(), Integer.MAX_VALUE);
        int ticks = InlinePumpRule.ticksToWait(moved, rate);
        if (ticks > 0) {
            waiting.put(pump.pos(), ticks);
        }
    }

    /**
     * Moves up to {@code max} of one fluid from {@code from} to {@code to}: the first fluid {@code from}
     * holds that {@code to} takes, so a segment holding a fluid is given only that one. Asks first, in a
     * transaction it aborts, how much both agree on, then moves that in one that commits only if both
     * moved it all, so each handler's own rules hold.
     *
     * @return the millibuckets moved
     */
    static int move(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to, int max) {
        for (int slot = 0; slot < from.size(); slot++) {
            FluidResource fluid = from.getResource(slot);
            if (fluid.isEmpty() || from.getAmountAsLong(slot) <= 0) {
                continue;
            }
            int agreed;
            try (Transaction asking = Transaction.openRoot()) {
                int out = from.extract(fluid, max, asking);
                agreed = out == 0 ? 0 : to.insert(fluid, out, asking);
            }
            if (agreed == 0) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                if (from.extract(fluid, agreed, tx) == agreed && to.insert(fluid, agreed, tx) == agreed) {
                    tx.commit();
                    return agreed;
                }
            }
        }
        return 0;
    }
}
