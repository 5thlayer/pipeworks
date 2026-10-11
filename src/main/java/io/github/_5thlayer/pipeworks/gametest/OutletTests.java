// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.outlet.OutletBlock;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Outlet (ADR 0007): it takes a bucket's worth from its segment and sets it down as a source
 * block in the space it faces, only when that space is empty or replaceable, only once its segment
 * holds 1,000 mB, never for a fluid with no block, and never draws fluid from the world. Each test
 * stands the outlet at x 2 in a row at z 4, facing west onto x 1, with a tank at x 3 to hold a
 * bucket's worth.
 */
final class OutletTests {

    private static final BlockPos OUTLET = new BlockPos(2, 1, 4);
    private static final BlockPos FRONT = OUTLET.west();
    private static final BlockPos TANK = OUTLET.east();
    private static final int SETTLE = 3;

    private OutletTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("an_outlet_sets_a_bucket_of_water_down_as_a_source_block", 20, OutletTests::placesWater);
        tests.test("an_outlet_replaces_a_replaceable_block_it_faces", 20, OutletTests::replaces);
        tests.test("an_outlet_waits_while_the_space_it_faces_is_occupied", 30, OutletTests::waitsOnOccupied);
        tests.test("an_outlet_waits_until_its_segment_holds_a_bucket", 20, OutletTests::waitsForBucket);
        tests.test("an_outlet_ignores_a_fluid_with_no_block", 20, OutletTests::ignoresBlockless);
        tests.test("an_outlet_never_draws_fluid_from_the_world", 20, OutletTests::neverDraws);
    }

    private static void outletAndTank(GameTestHelper helper) {
        helper.setBlock(OUTLET, PipeworksRegistries.OUTLET.get().defaultBlockState().setValue(OutletBlock.FACING, Direction.WEST));
        helper.setBlock(TANK, PipeworksRegistries.STORAGE_TANK.get());
    }

    /** Fills the tank through its fluid face, as another mod's machine would. */
    private static void fill(GameTestHelper helper, Fluid fluid, int amount) {
        var tank = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(TANK), null);
        if (tank == null) {
            helper.fail("the tank exposes no fluid handler", TANK);
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            if (tank.insert(FluidResource.of(fluid), amount, tx) != amount) {
                helper.fail("the tank took less than " + amount + " mB of " + fluid, TANK);
            }
            tx.commit();
        }
    }

    private static SegmentGraph.@Nullable Contents contents(GameTestHelper helper) {
        return FluidSegments.get(helper.getLevel()).contentsAt(helper.absolutePos(TANK));
    }

    private static long amount(GameTestHelper helper) {
        var contents = contents(helper);
        return contents == null ? 0 : contents.amount();
    }

    private static void placesWater(GameTestHelper helper) {
        outletAndTank(helper);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, Fluids.WATER, 1_500)).thenIdle(SETTLE).thenExecute(() -> {
            var front = helper.getBlockState(FRONT).getFluidState();
            if (!front.isSource() || front.getType() != Fluids.WATER) {
                helper.fail("the space the outlet faces holds " + helper.getBlockState(FRONT) + ", not a water source", FRONT);
            }
            if (amount(helper) != 500) {
                helper.fail("the tank holds " + contents(helper) + ", not the 500 mB left after a bucket", TANK);
            }
        }).thenSucceed();
    }

    private static void replaces(GameTestHelper helper) {
        outletAndTank(helper);
        helper.setBlock(FRONT, Blocks.SNOW);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, Fluids.WATER, 1_000)).thenIdle(SETTLE).thenExecute(() -> {
            if (!helper.getBlockState(FRONT).getFluidState().isSource()) {
                helper.fail("the snow layer stands, not a water source", FRONT);
            }
            if (amount(helper) != 0) {
                helper.fail("the tank holds " + contents(helper) + ", not nothing", TANK);
            }
        }).thenSucceed();
    }

    private static void waitsOnOccupied(GameTestHelper helper) {
        outletAndTank(helper);
        helper.setBlock(FRONT, Blocks.STONE);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, Fluids.WATER, 1_000)).thenIdle(SETTLE + 5).thenExecute(() -> {
            if (!helper.getBlockState(FRONT).is(Blocks.STONE)) {
                helper.fail("the outlet replaced the stone it faces", FRONT);
            }
            if (amount(helper) != 1_000) {
                helper.fail("the tank holds " + contents(helper) + ", not its 1000 mB", TANK);
            }
            helper.setBlock(FRONT, Blocks.AIR);
        }).thenIdle(SETTLE).thenExecute(() -> {
            if (!helper.getBlockState(FRONT).getFluidState().isSource()) {
                helper.fail("the outlet set no water down once the space was cleared", FRONT);
            }
        }).thenSucceed();
    }

    private static void waitsForBucket(GameTestHelper helper) {
        outletAndTank(helper);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, Fluids.WATER, 999)).thenIdle(SETTLE + 5).thenExecute(() -> {
            if (!helper.getBlockState(FRONT).isAir()) {
                helper.fail("the outlet set " + helper.getBlockState(FRONT) + " down from 999 mB", FRONT);
            }
            if (amount(helper) != 999) {
                helper.fail("the tank holds " + contents(helper) + ", not its 999 mB", TANK);
            }
        }).thenSucceed();
    }

    private static void ignoresBlockless(GameTestHelper helper) {
        outletAndTank(helper);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, TestFluid.FLUID.get(), 1_000)).thenIdle(SETTLE + 5).thenExecute(() -> {
            if (!helper.getBlockState(FRONT).isAir()) {
                helper.fail("the outlet set " + helper.getBlockState(FRONT) + " down for a fluid with no block", FRONT);
            }
            if (amount(helper) != 1_000) {
                helper.fail("the tank holds " + contents(helper) + ", not its 1000 mB", TANK);
            }
        }).thenSucceed();
    }

    private static void neverDraws(GameTestHelper helper) {
        helper.setBlock(FRONT, Blocks.WATER);
        helper.setBlock(OUTLET.north(), Blocks.WATER);
        outletAndTank(helper);
        helper.startSequence().thenIdle(SETTLE + 5).thenExecute(() -> {
            if (amount(helper) != 0) {
                helper.fail("the tank holds " + contents(helper) + ", drawn from the world", TANK);
            }
            if (!helper.getBlockState(FRONT).is(Blocks.WATER) || !helper.getBlockState(OUTLET.north()).is(Blocks.WATER)) {
                helper.fail("a source block beside the outlet was taken", OUTLET);
            }
        }).thenSucceed();
    }
}
