// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Whether the segments of ADR-0110 hold in a running world: the rules themselves are
 * {@code SegmentGraphTest}'s, and what only a world can show is that placing and breaking blocks
 * drives them and that a port reaches a tank through the pipes between.
 *
 * <p>The layout is a row at z 4: a port at x 0, pipes at x 1 to 7 and a tank at x 8.
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
        // A block entity's onLoad runs on the next tick, which is when the port joins.
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

        var segments = FluidSegments.get(level(helper));
        var left = segments.heldAt(helper.absolutePos(at(1))).amount();
        var right = segments.heldAt(helper.absolutePos(at(4))).amount();
        // 400 mB over the run at x 0 to 2 (200 mB of pipe), the broken pipe (100) and the pipe at x 4 (100).
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

        var held = loaded.heldAt(helper.absolutePos(at(2)));
        if (held.amount() != 321 || held.capacity() != 3 * Pipeworks.PIPE_CAPACITY + Pipeworks.TANK_CAPACITY) {
            helper.fail("the saved segment came back as " + held, at(2));
            return;
        }
        helper.succeed();
    }
}
