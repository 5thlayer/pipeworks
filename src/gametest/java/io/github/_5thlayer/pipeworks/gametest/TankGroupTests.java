// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPipes;
import io.github._5thlayer.pipeworks.block.StorageTankBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
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
        tests.test("tank_group_each_group_fills_to_the_fraction_of_its_own_volume", 20, TankGroupTests::ownVolume);
        tests.test("tank_group_a_drop_shows_in_the_bottom_tank_only", 20, TankGroupTests::aDrop);
        tests.test("tank_group_a_waiting_tank_stays_apart", 20, TankGroupTests::waitingApart);
        tests.test("tank_group_joins_follow_a_tank_placed_and_broken", 20, TankGroupTests::placedAndBroken);
        tests.test("tank_group_a_closed_face_keeps_two_tanks_apart", 20, TankGroupTests::closedFace);
        tests.test("tank_group_tanks_saved_without_joins_join_when_their_chunk_loads", 20, TankGroupTests::savedWithoutJoins);
    }

    private static BlockPos at(int x, int y) {
        return new BlockPos(x, y, Z);
    }

    private static void tank(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
    }

    private static void pipe(GameTestHelper helper, int x, int y) {
        helper.setBlock(at(x, y), PipeworksRegistries.PIPE.get().defaultBlockState());
    }

    private static void tank(GameTestHelper helper, int x, int y) {
        tank(helper, at(x, y));
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

    private static void level(GameTestHelper helper, BlockPos pos, int expected, String which) {
        int shown = helper.getBlockState(pos).getValue(StorageTankBlock.LEVEL);
        if (shown != expected) {
            helper.fail(which + " shows level " + shown + ", not " + expected, pos);
        }
    }

    private static void joins(GameTestHelper helper, BlockPos pos, Direction face, boolean expected, String which) {
        boolean shown = helper.getBlockState(pos).getValue(StorageTankBlock.JOINS.get(face));
        if (shown != expected) {
            helper.fail(which + (expected ? " does not join " : " joins ") + face, pos);
        }
    }

    /** Three tanks on a pipe, 37,550 of 75,100 mB: the bottom full, the middle at 7, the top empty. */
    private static void column(GameTestHelper helper) {
        pipe(helper, 0, 1);
        for (int y = 1; y <= 3; y++) {
            tank(helper, 1, y);
        }
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> water(helper, at(0, 1), 37_550)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    level(helper, at(1, 1), 15, "the bottom tank");
                    level(helper, at(1, 2), 7, "the middle tank");
                    level(helper, at(1, 3), 0, "the top tank");
                    joins(helper, at(1, 1), Direction.UP, true, "the bottom tank");
                    joins(helper, at(1, 2), Direction.DOWN, true, "the middle tank");
                    joins(helper, at(1, 2), Direction.UP, true, "the middle tank");
                    joins(helper, at(1, 3), Direction.UP, false, "the top tank");
                    joins(helper, at(1, 1), Direction.WEST, false, "the bottom tank, towards the pipe");
                }).thenSucceed();
    }

    /** A 2 by 2 floor and a tank on it, 100,080 of 125,100 mB: the floor full, the top still empty. */
    private static void wideLayer(GameTestHelper helper) {
        pipe(helper, 0, 1);
        for (int x = 1; x <= 2; x++) {
            for (int z = Z; z <= Z + 1; z++) {
                tank(helper, new BlockPos(x, 1, z));
            }
        }
        tank(helper, 1, 2);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> water(helper, at(0, 1), 100_080)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    for (int x = 1; x <= 2; x++) {
                        for (int z = Z; z <= Z + 1; z++) {
                            level(helper, new BlockPos(x, 1, z), 15, "a floor tank");
                        }
                    }
                    level(helper, at(1, 2), 0, "the tank on the floor");
                    water(helper, at(0, 1), 2_000);
                }).thenIdle(SETTLES).thenExecute(() -> level(helper, at(1, 2), 1, "the tank on the floor, with 2,000 mB more"))
                .thenSucceed();
    }

    /**
     * A column of two and a tank alone either side of a pipe, 37,550 of 75,100 mB: the column its
     * bottom tank full, the tank alone half full. Shared out over all three tanks as one group, the
     * column would show 11 and the tank alone 11 too.
     */
    private static void ownVolume(GameTestHelper helper) {
        tank(helper, 1, 1);
        tank(helper, 1, 2);
        pipe(helper, 2, 1);
        tank(helper, 3, 1);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> water(helper, at(2, 1), 37_550)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    level(helper, at(1, 1), 15, "the column's bottom");
                    level(helper, at(1, 2), 0, "the column's top");
                    level(helper, at(3, 1), 7, "the tank alone");
                }).thenSucceed();
    }

    /** A millibucket in a column of three: step 1 in the bottom tank, none above it. */
    private static void aDrop(GameTestHelper helper) {
        pipe(helper, 0, 1);
        for (int y = 1; y <= 3; y++) {
            tank(helper, 1, y);
        }
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> water(helper, at(0, 1), 1)).thenIdle(SETTLES)
                .thenExecute(() -> {
                    level(helper, at(1, 1), 1, "the bottom tank");
                    level(helper, at(1, 2), 0, "the middle tank");
                    level(helper, at(1, 3), 0, "the top tank");
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
            joins(helper, at(1, 1), Direction.EAST, false, "the water tank");
            joins(helper, at(2, 1), Direction.WEST, false, "the waiting tank");
            level(helper, at(2, 1), 0, "the waiting tank");
        }).thenSucceed();
    }

    /** A tank set on another joins it, both ways, and the bottom one parts once the top is broken. */
    private static void placedAndBroken(GameTestHelper helper) {
        tank(helper, 1, 1);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> {
            joins(helper, at(1, 1), Direction.UP, false, "a tank alone");
            tank(helper, 1, 2);
        }).thenIdle(SETTLES).thenExecute(() -> {
            joins(helper, at(1, 1), Direction.UP, true, "the bottom tank, a tank set on it");
            joins(helper, at(1, 2), Direction.DOWN, true, "the tank set on it");
            helper.setBlock(at(1, 2), Blocks.AIR.defaultBlockState());
        }).thenIdle(SETTLES).thenExecute(() -> joins(helper, at(1, 1), Direction.UP, false, "the bottom tank, the top broken"))
                .thenSucceed();
    }

    /**
     * A pipe's closed side closes the tank's face beside it too, and the face stays closed once the
     * pipe is broken (ADR 0004): a tank set there touches the first but does not join it, and the
     * two show their own segments.
     */
    private static void closedFace(GameTestHelper helper) {
        tank(helper, 1, 1);
        pipe(helper, 2, 1);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> {
            if (!FluidPipes.close(helper.getLevel(), helper.absolutePos(at(2, 1)), Direction.WEST)) {
                helper.fail("the pipe's west side did not close", at(2, 1));
            }
            helper.setBlock(at(2, 1), Blocks.AIR.defaultBlockState());
            tank(helper, 2, 1);
        }).thenIdle(SETTLES).thenExecute(() -> water(helper, at(1, 1), 12_500)).thenIdle(SETTLES).thenExecute(() -> {
            joins(helper, at(1, 1), Direction.EAST, false, "the first tank");
            joins(helper, at(2, 1), Direction.WEST, false, "the tank set beside it");
            level(helper, at(1, 1), 7, "the first tank");
            level(helper, at(2, 1), 0, "the tank set beside it");
        }).thenSucceed();
    }

    /**
     * Two stacked tanks set back to no joins, as a tank saved before tank groups loads, join again
     * once their chunk loads. The chunk is already loaded, so its load is posted as the game does.
     */
    private static void savedWithoutJoins(GameTestHelper helper) {
        tank(helper, 1, 1);
        tank(helper, 1, 2);
        helper.startSequence().thenIdle(SETTLES).thenExecute(() -> {
            for (int y = 1; y <= 2; y++) {
                BlockPos pos = helper.absolutePos(at(1, y));
                BlockState state = helper.getLevel().getBlockState(pos);
                for (BooleanProperty join : StorageTankBlock.JOINS.values()) {
                    state = state.setValue(join, false);
                }
                // The same block, so the tank stays in its segment: only its state changes.
                helper.getLevel().setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
            joins(helper, at(1, 1), Direction.UP, false, "the bottom tank, set back");
            BlockPos chunk = helper.absolutePos(at(1, 1));
            NeoForge.EVENT_BUS.post(new ChunkEvent.Load(helper.getLevel().getChunkAt(chunk), false));
        }).thenIdle(SETTLES).thenExecute(() -> {
            joins(helper, at(1, 1), Direction.UP, true, "the bottom tank, its chunk loaded");
            joins(helper, at(1, 2), Direction.DOWN, true, "the top tank, its chunk loaded");
        }).thenSucceed();
    }
}
