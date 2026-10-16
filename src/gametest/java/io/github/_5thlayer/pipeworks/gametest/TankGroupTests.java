// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.block.StorageTankBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Touching storage tanks draw as one tank group, filled from the bottom up (ADR 0008). Each test
 * lays its tanks and a 100 mB pipe at z 4 and fills them through the pipe; a tank holds 25,000 mB.
 * Steps are set on the level tick, so each waits a tick or two before it reads.
 */
final class TankGroupTests {

    private static final int Z = 4;
    private static final int SETTLES = 2;

    private TankGroupTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("tank_group_a_half_full_column_fills_from_the_bottom", 20, TankGroupTests::column);
        tests.test("tank_group_a_wide_layer_fills_before_the_tank_on_it", 20, TankGroupTests::wideLayer);
        tests.test("tank_group_two_columns_on_a_pipe_show_the_same_fraction", 20, TankGroupTests::twoColumns);
        tests.test("tank_group_a_drop_shows_in_the_bottom_tank_only", 20, TankGroupTests::aDrop);
        tests.test("tank_group_a_waiting_tank_stays_apart", 20, TankGroupTests::waitingApart);
        tests.test("tank_group_joins_follow_a_tank_placed_and_broken", 20, TankGroupTests::placedAndBroken);
    }

    private static BlockPos at(int x, int y) {
        return new BlockPos(x, y, Z);
    }

    private static void pipe(GameTestHelper helper, int x, int y) {
        helper.setBlock(at(x, y), PipeworksRegistries.PIPE.get().defaultBlockState());
    }

    private static void tank(GameTestHelper helper, int x, int y) {
        helper.setBlock(at(x, y), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
    }

    private static void fill(GameTestHelper helper, BlockPos pos, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), null)
                    .insert(fluid, amount, transaction);
            transaction.commit();
        }
    }

    private static void water(GameTestHelper helper, BlockPos pos, int amount) {
        fill(helper, pos, FluidResource.of(Fluids.WATER), amount);
    }

    private static boolean level(GameTestHelper helper, BlockPos pos, int expected, String which) {
        int shown = helper.getBlockState(pos).getValue(StorageTankBlock.LEVEL);
        if (shown != expected) {
            helper.fail(which + " shows level " + shown + ", not " + expected, pos);
            return false;
        }
        return true;
    }

    private static boolean joins(GameTestHelper helper, BlockPos pos, Direction face, boolean expected, String which) {
        boolean shown = helper.getBlockState(pos).getValue(StorageTankBlock.JOINS.get(face));
        if (shown != expected) {
            helper.fail(which + (expected ? " does not join " : " joins ") + face, pos);
            return false;
        }
        return true;
    }

    /** Three tanks on a pipe, 37,550 of 75,100 mB: the bottom full, the middle at 7, the top empty. */
    private static void column(GameTestHelper helper) {
        pipe(helper, 0, 1);
        for (int y = 1; y <= 3; y++) {
            tank(helper, 1, y);
        }
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> water(helper, at(0, 1), 37_550)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    boolean shown = level(helper, at(1, 1), 15, "the bottom tank")
                            && level(helper, at(1, 2), 7, "the middle tank")
                            && level(helper, at(1, 3), 0, "the top tank")
                            && joins(helper, at(1, 1), Direction.UP, true, "the bottom tank")
                            && joins(helper, at(1, 2), Direction.DOWN, true, "the middle tank")
                            && joins(helper, at(1, 2), Direction.UP, true, "the middle tank")
                            && joins(helper, at(1, 3), Direction.UP, false, "the top tank")
                            && joins(helper, at(1, 1), Direction.WEST, false, "the bottom tank, towards the pipe");
                }).thenSucceed();
    }

    /** A 2 by 2 floor and a tank on it, 100,080 of 125,100 mB: the floor full, the top still empty. */
    private static void wideLayer(GameTestHelper helper) {
        pipe(helper, 0, 1);
        for (int x = 1; x <= 2; x++) {
            for (int z = Z; z <= Z + 1; z++) {
                helper.setBlock(new BlockPos(x, 1, z), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
            }
        }
        tank(helper, 1, 2);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> water(helper, at(0, 1), 100_080)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    for (int x = 1; x <= 2; x++) {
                        for (int z = Z; z <= Z + 1; z++) {
                            if (!level(helper, new BlockPos(x, 1, z), 15, "a floor tank")) {
                                return;
                            }
                        }
                    }
                    if (level(helper, at(1, 2), 0, "the tank on the floor")) {
                        water(helper, at(0, 1), 2_000);
                    }
                }).thenIdle(SETTLES).thenExecute(() -> level(helper, at(1, 2), 1, "the tank on the floor, with 2,000 mB more"))
                .thenSucceed();
    }

    /** Two columns of two either side of a pipe, 50,050 of 100,100 mB: each its bottom tank full. */
    private static void twoColumns(GameTestHelper helper) {
        tank(helper, 1, 1);
        tank(helper, 1, 2);
        pipe(helper, 2, 1);
        tank(helper, 3, 1);
        tank(helper, 3, 2);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> water(helper, at(2, 1), 50_050)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    boolean shown = level(helper, at(1, 1), 15, "the west column's bottom")
                            && level(helper, at(1, 2), 0, "the west column's top")
                            && level(helper, at(3, 1), 15, "the east column's bottom")
                            && level(helper, at(3, 2), 0, "the east column's top");
                }).thenSucceed();
    }

    private static void aDrop(GameTestHelper helper) {
        pipe(helper, 0, 1);
        for (int y = 1; y <= 3; y++) {
            tank(helper, 1, y);
        }
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> water(helper, at(0, 1), 1)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    boolean shown = level(helper, at(1, 1), 1, "the bottom tank")
                            && level(helper, at(1, 2), 0, "the middle tank")
                            && level(helper, at(1, 3), 0, "the top tank");
                }).thenSucceed();
    }

    /** A tank set between a tank of water and a pipe of lava waits, linked to nothing, so joins nothing. */
    private static void waitingApart(GameTestHelper helper) {
        pipe(helper, 0, 1);
        tank(helper, 1, 1);
        pipe(helper, 3, 1);
        water(helper, at(0, 1), 50);
        fill(helper, at(3, 1), FluidResource.of(Fluids.LAVA), 50);
        tank(helper, 2, 1);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> {
            boolean shown = joins(helper, at(1, 1), Direction.EAST, false, "the water tank")
                    && joins(helper, at(2, 1), Direction.WEST, false, "the waiting tank")
                    && level(helper, at(2, 1), 0, "the waiting tank");
        }).thenSucceed();
    }

    private static void placedAndBroken(GameTestHelper helper) {
        tank(helper, 1, 1);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> {
            if (joins(helper, at(1, 1), Direction.UP, false, "a tank alone")) {
                tank(helper, 1, 2);
            }
        }).thenIdle(SETTLES).thenExecute(() -> {
            if (joins(helper, at(1, 1), Direction.UP, true, "the bottom tank, a tank set on it")
                    && joins(helper, at(1, 2), Direction.DOWN, true, "the tank set on it")) {
                helper.setBlock(at(1, 2), Blocks.AIR.defaultBlockState());
            }
        }).thenIdle(SETTLES).thenExecute(() -> joins(helper, at(1, 1), Direction.UP, false, "the bottom tank, the top broken"))
                .thenSucceed();
    }
}
