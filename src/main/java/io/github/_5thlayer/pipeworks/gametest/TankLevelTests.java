// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.block.StorageTankBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The storage tank's level, which the server sets from its segment's fill (ADR 0003). Each test lays
 * a pipe at x 0 and a tank at x 1, 25,100 mB between them, at z 4, which {@code at(x)} indexes. A
 * level is set on the level tick, so each waits a tick or two before it reads.
 */
final class TankLevelTests {

    private static final int Z = 4;
    private static final int SETTLES = 2;

    private TankLevelTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_tank_shows_the_step_its_segment_is_filled_to", 20, TankLevelTests::showsHalf);
        tests.test("a_tank_drained_to_nothing_shows_level_zero", 20, TankLevelTests::showsEmpty);
        tests.test("a_tank_changes_level_only_when_its_step_changes", 20, TankLevelTests::changesWithTheStep);
        tests.test("a_pipe_joining_a_filled_tanks_segment_lowers_its_level", 20, TankLevelTests::followsMerge);
        tests.test("a_tank_that_waits_shows_level_zero", 20, TankLevelTests::waitingShowsNothing);
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 1, Z);
    }

    private static FluidResource water() {
        return FluidResource.of(Fluids.WATER);
    }

    private static void pipeAndTank(GameTestHelper helper) {
        helper.setBlock(at(0), PipeworksRegistries.PIPE.get().defaultBlockState());
        helper.setBlock(at(1), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
    }

    private static ResourceHandler<FluidResource> faceAt(GameTestHelper helper, int x) {
        return helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at(x)), null);
    }

    private static void fill(GameTestHelper helper, int x, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            faceAt(helper, x).insert(fluid, amount, transaction);
            transaction.commit();
        }
    }

    private static void drain(GameTestHelper helper, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            faceAt(helper, 0).extract(water(), amount, transaction);
            transaction.commit();
        }
    }

    private static boolean expect(GameTestHelper helper, int expected, String when) {
        int shown = helper.getBlockState(at(1)).getValue(StorageTankBlock.LEVEL);
        if (shown != expected) {
            helper.fail("the tank shows level " + shown + " " + when + ", not " + expected, at(1));
            return false;
        }
        return true;
    }

    private static void showsHalf(GameTestHelper helper) {
        pipeAndTank(helper);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> {
            if (expect(helper, 0, "before it holds anything")) {
                fill(helper, 0, water(), 12_550);
            }
        }).thenIdle(SETTLES).thenExecute(() -> expect(helper, 7, "with its segment half full")).thenSucceed();
    }

    private static void showsEmpty(GameTestHelper helper) {
        pipeAndTank(helper);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> fill(helper, 0, water(), 25_100)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    if (expect(helper, 15, "with its segment full")) {
                        drain(helper, 25_100);
                    }
                }).thenIdle(SETTLES).thenExecute(() -> expect(helper, 0, "once drained")).thenSucceed();
    }

    /** 25,100 mB: step 1 holds from 1,674 (a fifteenth) to 3,346, where step 2 begins. */
    private static void changesWithTheStep(GameTestHelper helper) {
        pipeAndTank(helper);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> fill(helper, 0, water(), 1_700)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    if (expect(helper, 1, "at 1,700 mB")) {
                        fill(helper, 0, water(), 1_000);
                    }
                }).thenIdle(SETTLES).thenExecute(() -> {
                    if (expect(helper, 1, "at 2,700 mB, inside its step")) {
                        fill(helper, 0, water(), 1_000);
                    }
                }).thenIdle(SETTLES).thenExecute(() -> expect(helper, 2, "at 3,700 mB")).thenSucceed();
    }

    private static void followsMerge(GameTestHelper helper) {
        pipeAndTank(helper);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> fill(helper, 0, water(), 25_100)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    if (expect(helper, 15, "full")) {
                        // 100 mB more capacity beside the tank: 25,100 of 25,200 is step 14.
                        helper.setBlock(at(2), PipeworksRegistries.PIPE.get().defaultBlockState());
                    }
                }).thenIdle(SETTLES).thenExecute(() -> expect(helper, 14, "after a pipe joined its segment"))
                .thenSucceed();
    }

    private static void waitingShowsNothing(GameTestHelper helper) {
        helper.setBlock(at(0), PipeworksRegistries.PIPE.get().defaultBlockState());
        helper.setBlock(at(2), PipeworksRegistries.PIPE.get().defaultBlockState());
        fill(helper, 0, water(), 50);
        fill(helper, 2, FluidResource.of(Fluids.LAVA), 50);
        // Set, not placed by a player, so between water and lava it waits instead of being refused.
        helper.setBlock(at(1), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> expect(helper, 0, "while it waits between water and lava"))
                .thenSucceed();
    }
}
