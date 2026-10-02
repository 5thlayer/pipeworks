// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Whether the segments of ADR 0002 and ADR 0003 hold in a running world: the rules themselves are
 * {@code SegmentGraphTest}'s, and what only a world can show is that placing and breaking blocks
 * drives them, that a port reaches a tank through the pipes between, and that the transfer API's
 * transactions undo through them.
 *
 * <p>Each test lays its nodes in a row at z 4, which {@code at(x)} indexes.
 */
final class SegmentTests {

    private static final int Z = 4;
    private static final int PORT_JOINS = 2;

    private SegmentTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("water_put_in_at_a_port_comes_out_of_a_tank", 20, SegmentTests::portToTank);
        tests.test("breaking_a_pipe_splits_the_fluid_by_capacity", 20, SegmentTests::splitByCapacity);
        tests.test("a_pipe_between_two_fluids_is_refused", 20, SegmentTests::refusesMixing);
        tests.test("fluid_survives_a_save_and_load", 20, SegmentTests::survivesSaving);
        tests.test("an_unknown_fluid_is_dropped_on_load", 20, SegmentTests::dropsUnknownFluids);
        tests.test("a_port_between_two_fluids_waits_until_one_is_gone", 20, SegmentTests::portWaits);
        tests.test("a_pipe_set_between_two_fluids_waits_until_one_is_drained", 20, SegmentTests::pipeWaits);
        tests.test("a_handler_whose_node_is_gone_holds_nothing", 20, SegmentTests::staleHandler);
        tests.test("an_aborted_insert_is_undone_before_a_split", 20, SegmentTests::abortThenSplit);
        tests.test("an_aborted_insert_is_undone_before_a_merge", 20, SegmentTests::abortThenMerge);
    }

    /** Looked up per test: the registries are not bound when this class loads. */
    private static FluidResource water() {
        return FluidResource.of(Fluids.WATER);
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 1, Z);
    }

    private static void row(GameTestHelper helper, int from, int to) {
        for (int x = from; x <= to; x++) {
            helper.setBlock(at(x), PipeworksRegistries.PIPE.get().defaultBlockState());
        }
    }

    private static ServerLevel level(GameTestHelper helper) {
        return helper.getLevel();
    }

    private static ResourceHandler<FluidResource> portSegment(GameTestHelper helper, int x) {
        return FluidPorts.segment(level(helper), helper.absolutePos(at(x)));
    }

    private static int insert(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(fluid, amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    private static int extract(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.extract(fluid, amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    private static void portToTank(GameTestHelper helper) {
        helper.setBlock(at(0), TestPort.BLOCK.get().defaultBlockState());
        row(helper, 1, 7);
        helper.setBlock(at(8), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
        helper.startSequence().thenIdle(PORT_JOINS).thenExecute(() -> fromPortToTank(helper)).thenSucceed();
    }

    private static void fromPortToTank(GameTestHelper helper) {
        ResourceHandler<FluidResource> port = portSegment(helper, 0);
        if (port == null) {
            helper.fail("the port joined no segment", at(0));
            return;
        }
        int put = insert(port, water(), 1000);
        if (put != 1000) {
            helper.fail("the port took " + put + " mB of 1000; the run and the tank hold 25,700", at(0));
            return;
        }
        ResourceHandler<FluidResource> tank = level(helper).getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at(8)), null);
        if (tank == null) {
            helper.fail("the tank exposes no fluid face", at(8));
            return;
        }
        int taken = extract(tank, water(), 1000);
        if (taken != 1000) {
            helper.fail("the tank gave " + taken + " mB of the 1000 put in at the port", at(8));
        }
    }

    private static void splitByCapacity(GameTestHelper helper) {
        helper.setBlock(at(0), TestPort.BLOCK.get().defaultBlockState());
        row(helper, 1, 4);
        helper.startSequence().thenIdle(PORT_JOINS).thenExecute(() -> {
            insert(portSegment(helper, 0), water(), 400);
            helper.setBlock(at(3), Blocks.AIR);
            checkSplit(helper);
        }).thenSucceed();
    }

    private static void checkSplit(GameTestHelper helper) {
        long left = amountAt(helper, 1);
        long right = amountAt(helper, 4);
        if (left != 200 || right != 100) {
            helper.fail("the halves hold " + left + " and " + right + " mB; by capacity they hold 200 and 100", at(3));
        }
    }

    private static void refusesMixing(GameTestHelper helper) {
        row(helper, 0, 0);
        row(helper, 2, 2);
        insert(pipeSegment(helper, 0), water(), 50);
        insert(pipeSegment(helper, 2), FluidResource.of(Fluids.LAVA), 50);

        var segments = FluidSegments.get(level(helper));
        if (segments.canJoin(helper.absolutePos(at(1)), FluidSegments.ALL_FACES)) {
            helper.fail("a pipe between water and lava would be placed", at(1));
            return;
        }
        helper.succeed();
    }

    private static ResourceHandler<FluidResource> pipeSegment(GameTestHelper helper, int x) {
        return level(helper).getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at(x)), null);
    }

    private static void survivesSaving(GameTestHelper helper) {
        row(helper, 0, 2);
        helper.setBlock(at(3), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
        insert(pipeSegment(helper, 0), water(), 321);

        var segments = FluidSegments.get(level(helper));
        var saved = FluidSegments.CODEC.encodeStart(NbtOps.INSTANCE, segments).getOrThrow();
        var loaded = FluidSegments.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();

        var contents = loaded.contentsAt(helper.absolutePos(at(2)));
        if (contents == null || contents.amount() != 321
                || contents.capacity() != 3 * Pipeworks.PIPE_CAPACITY + Pipeworks.TANK_CAPACITY) {
            helper.fail("the saved segment came back as " + contents, at(2));
            return;
        }
        helper.succeed();
    }

    private static SegmentGraph.@Nullable Contents contentsAt(GameTestHelper helper, int x) {
        return FluidSegments.get(level(helper)).contentsAt(helper.absolutePos(at(x)));
    }

    private static long amountAt(GameTestHelper helper, int x) {
        SegmentGraph.Contents contents = contentsAt(helper, x);
        return contents == null ? -1 : contents.amount();
    }

    private static boolean arm(GameTestHelper helper, int x, Direction face) {
        return helper.getBlockState(at(x)).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(face));
    }

    private static void dropsUnknownFluids(GameTestHelper helper) {
        row(helper, 0, 0);
        row(helper, 2, 2);
        row(helper, 4, 4);
        insert(pipeSegment(helper, 0), water(), 50);
        insert(pipeSegment(helper, 2), water(), 70);
        insert(pipeSegment(helper, 4), FluidResource.of(Fluids.LAVA), 30);

        var saved = (CompoundTag) FluidSegments.CODEC.encodeStart(NbtOps.INSTANCE, FluidSegments.get(level(helper))).getOrThrow();
        ListTag contents = saved.getListOrEmpty("contents");
        for (int i = 0; i < contents.size(); i++) {
            CompoundTag entry = contents.getCompoundOrEmpty(i);
            long pos = entry.getLongOr("pos", 0);
            if (pos == helper.absolutePos(at(0)).asLong()) {
                entry.putString("fluid", "pipeworks:no_such_fluid");
            } else if (pos == helper.absolutePos(at(2)).asLong()) {
                entry.putString("fluid", "Not An Id!");
            }
        }
        var loaded = FluidSegments.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();

        for (int x : new int[] {0, 2}) {
            var dropped = loaded.contentsAt(helper.absolutePos(at(x)));
            if (dropped == null || dropped.fluid() != null || dropped.amount() != 0) {
                helper.fail("a segment saved with an unknown fluid came back as " + dropped, at(x));
                return;
            }
        }
        var kept = loaded.contentsAt(helper.absolutePos(at(4)));
        if (kept == null || kept.amount() != 30) {
            helper.fail("the lava segment came back as " + kept, at(4));
            return;
        }
        helper.succeed();
    }

    private static void portWaits(GameTestHelper helper) {
        row(helper, 0, 0);
        row(helper, 2, 2);
        insert(pipeSegment(helper, 0), water(), 50);
        insert(pipeSegment(helper, 2), FluidResource.of(Fluids.LAVA), 50);
        helper.setBlock(at(1), TestPort.BLOCK.get().defaultBlockState());
        helper.startSequence().thenIdle(PORT_JOINS).thenExecute(() -> {
            if (portSegment(helper, 1) != null) {
                helper.fail("a port between water and lava joined a segment", at(1));
            }
            if (arm(helper, 0, Direction.EAST) || arm(helper, 2, Direction.WEST)) {
                helper.fail("a pipe draws an arm to a port it is not linked to", at(1));
            }
            helper.setBlock(at(2), Blocks.AIR);
        }).thenIdle(1).thenExecute(() -> {
            var contents = contentsAt(helper, 1);
            if (portSegment(helper, 1) == null || contents == null || contents.amount() != 50) {
                helper.fail("with the lava gone the port's segment is " + contents, at(1));
            }
            if (!arm(helper, 0, Direction.EAST)) {
                helper.fail("the pipe draws no arm to the port it is now linked to", at(0));
            }
        }).thenSucceed();
    }

    private static void pipeWaits(GameTestHelper helper) {
        row(helper, 0, 0);
        row(helper, 2, 2);
        insert(pipeSegment(helper, 0), water(), 50);
        insert(pipeSegment(helper, 2), FluidResource.of(Fluids.LAVA), 50);
        row(helper, 1, 1);
        if (contentsAt(helper, 1) != null || pipeSegment(helper, 1) != null) {
            helper.fail("a pipe set between water and lava joined a segment", at(1));
            return;
        }
        if (arm(helper, 1, Direction.WEST) || arm(helper, 1, Direction.EAST) || arm(helper, 0, Direction.EAST)) {
            helper.fail("a waiting pipe draws arms", at(1));
            return;
        }
        extract(pipeSegment(helper, 2), FluidResource.of(Fluids.LAVA), 50);
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            var contents = contentsAt(helper, 1);
            if (contents == null || contents.amount() != 50 || contents.nodes() != 3) {
                helper.fail("with the lava drained the pipe's segment is " + contents, at(1));
            }
            if (!arm(helper, 1, Direction.WEST) || !arm(helper, 1, Direction.EAST) || !arm(helper, 0, Direction.EAST)) {
                helper.fail("the joined pipe draws no arms", at(1));
            }
        }).thenSucceed();
    }

    private static void staleHandler(GameTestHelper helper) {
        row(helper, 0, 1);
        insert(pipeSegment(helper, 0), water(), 50);
        ResourceHandler<FluidResource> stale = pipeSegment(helper, 1);
        helper.setBlock(at(1), Blocks.AIR);
        if (insert(stale, water(), 10) != 0 || extract(stale, water(), 10) != 0) {
            helper.fail("a handler whose pipe is gone still moves fluid", at(1));
            return;
        }
        if (!stale.getResource(0).isEmpty() || stale.getAmountAsLong(0) != 0 || stale.getCapacityAsLong(0, water()) != 0) {
            helper.fail("a handler whose pipe is gone still holds fluid", at(1));
            return;
        }
        if (pipeSegment(helper, 1) != null) {
            helper.fail("a broken pipe still exposes a fluid face", at(1));
            return;
        }
        helper.succeed();
    }

    private static void abortThenSplit(GameTestHelper helper) {
        row(helper, 0, 4);
        insert(pipeSegment(helper, 0), water(), 100);
        try (Transaction transaction = Transaction.openRoot()) {
            pipeSegment(helper, 0).insert(water(), 300, transaction);
            helper.setBlock(at(3), Blocks.AIR);
        }
        // The 100 mB the abort leaves split over 300 mB of pipe, the broken pipe and the last one.
        long left = amountAt(helper, 0);
        long right = amountAt(helper, 4);
        if (left != 60 || right != 20) {
            helper.fail("after the abort and the split the halves hold " + left + " and " + right + " mB, not 60 and 20", at(3));
            return;
        }
        helper.succeed();
    }

    private static void abortThenMerge(GameTestHelper helper) {
        row(helper, 0, 1);
        row(helper, 3, 4);
        insert(pipeSegment(helper, 0), water(), 50);
        insert(pipeSegment(helper, 4), water(), 70);
        try (Transaction transaction = Transaction.openRoot()) {
            pipeSegment(helper, 0).insert(water(), 100, transaction);
            row(helper, 2, 2);
        }
        var contents = contentsAt(helper, 0);
        if (contents == null || contents.amount() != 120 || contents.nodes() != 5) {
            helper.fail("after the abort and the merge the segment is " + contents + ", not 120 mB over 5 pipes", at(2));
            return;
        }
        helper.succeed();
    }
}
