// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.pump.PumpBlockEntity;
import io.github._5thlayer.pipeworks.pump.PumpItem;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Pump (ADR 0006): it adds 60 mB a tick from a source in {@code pipeworks:infinite_sources} and
 * leaves the block, takes any other source whole once a block's worth fits, never joins a fluid to
 * another, and is refused where no source stands beside it. Each test stands the pump at x 2 in a
 * row at z 4, with a tank at x 3 to hold a block's worth.
 */
final class PumpTests {

    private static final BlockPos PUMP = new BlockPos(2, 1, 4);
    private static final BlockPos NEXT = new BlockPos(3, 1, 4);
    private static final int SETTLE = 3;

    private PumpTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_pump_beside_water_fills_its_pipe_and_leaves_the_water", 20, PumpTests::water);
        tests.test("a_pump_beside_lava_takes_the_source_block_into_a_tank", 20, PumpTests::lava);
        tests.test("a_pump_takes_a_second_block_only_after_the_first_blocks_ticks", 60, PumpTests::rate);
        tests.test("a_pump_leaves_lava_where_a_block_does_not_fit_the_segment", 20, PumpTests::doesNotFit);
        tests.test("a_pump_does_not_mix_into_a_segment_holding_another_fluid", 20, PumpTests::noMixing);
        tests.test("the_shipped_infinite_tag_holds_water_and_not_lava", 20, PumpTests::tag);
        tests.test("a_pump_item_is_refused_where_no_source_stands_beside_it", 20, PumpTests::refusedWithoutSource);
        tests.test("a_pump_item_places_beside_a_source", 20, PumpTests::placesBesideSource);
    }

    private static void pumpAndTank(GameTestHelper helper, Block source, BlockPos at) {
        helper.setBlock(at, source);
        helper.setBlock(PUMP, PipeworksRegistries.PUMP.get());
        helper.setBlock(NEXT, PipeworksRegistries.STORAGE_TANK.get());
    }

    private static SegmentGraph.@Nullable Contents contents(GameTestHelper helper) {
        return FluidSegments.get(helper.getLevel()).contentsAt(helper.absolutePos(NEXT));
    }

    private static void water(GameTestHelper helper) {
        helper.setBlock(PUMP.north(), Blocks.WATER);
        helper.setBlock(PUMP, PipeworksRegistries.PUMP.get());
        helper.setBlock(NEXT, PipeworksRegistries.PIPE.get());
        helper.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var contents = contents(helper);
            if (contents == null || !contents.fluid().equals("minecraft:water") || contents.amount() != 100) {
                helper.fail("the pipe holds " + contents + ", not 100 mB of water", NEXT);
            }
            if (!helper.getBlockState(PUMP.north()).is(Blocks.WATER)) {
                helper.fail("the pump took the water source", PUMP.north());
            }
        }).thenSucceed();
    }

    private static void lava(GameTestHelper helper) {
        pumpAndTank(helper, Blocks.LAVA, PUMP.north());
        helper.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var contents = contents(helper);
            if (contents == null || !contents.fluid().equals("minecraft:lava") || contents.amount() != 1_000) {
                helper.fail("the tank holds " + contents + ", not 1000 mB of lava", NEXT);
            }
            if (!helper.getBlockState(PUMP.north()).isAir()) {
                helper.fail("the lava source block stands after the pump took it", PUMP.north());
            }
        }).thenSucceed();
    }

    private static void rate(GameTestHelper helper) {
        pumpAndTank(helper, Blocks.LAVA, PUMP.north());
        helper.setBlock(PUMP.above(), Blocks.LAVA);
        helper.startSequence().thenIdle(SETTLE + 5).thenExecute(() -> {
            int left = (helper.getBlockState(PUMP.above()).is(Blocks.LAVA) ? 1 : 0)
                    + (helper.getBlockState(PUMP.north()).is(Blocks.LAVA) ? 1 : 0);
            if (left != 1) {
                helper.fail(left + " of the 2 lava sources stand, not 1 before the first block's ticks are over", PUMP);
            }
        }).thenIdle(20).thenExecute(() -> {
            var contents = contents(helper);
            if (contents == null || contents.amount() != 2_000) {
                helper.fail("the tank holds " + contents + ", not 2000 mB of lava", NEXT);
            }
        }).thenSucceed();
    }

    private static void doesNotFit(GameTestHelper helper) {
        helper.setBlock(PUMP.north(), Blocks.LAVA);
        helper.setBlock(PUMP, PipeworksRegistries.PUMP.get());
        helper.setBlock(NEXT, PipeworksRegistries.PIPE.get());
        helper.startSequence().thenIdle(SETTLE + 5).thenExecute(() -> {
            if (!helper.getBlockState(PUMP.north()).is(Blocks.LAVA)) {
                helper.fail("the pump took a block that did not fit a 100 mB pipe", PUMP.north());
            }
            var contents = contents(helper);
            if (contents != null && contents.amount() != 0) {
                helper.fail("the pipe holds " + contents + ", not nothing", NEXT);
            }
        }).thenSucceed();
    }

    private static void noMixing(GameTestHelper helper) {
        pumpAndTank(helper, Blocks.LAVA, PUMP.north());
        helper.startSequence().thenExecute(() -> {
            var tank = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                    helper.absolutePos(NEXT), null);
            try (Transaction tx = Transaction.openRoot()) {
                tank.insert(FluidResource.of(Fluids.WATER), 500, tx);
                tx.commit();
            }
        }).thenIdle(SETTLE + 5).thenExecute(() -> {
            if (!helper.getBlockState(PUMP.north()).is(Blocks.LAVA)) {
                helper.fail("the pump drew lava into a segment holding water", PUMP.north());
            }
            var contents = contents(helper);
            if (contents == null || !contents.fluid().equals("minecraft:water") || contents.amount() != 500) {
                helper.fail("the tank holds " + contents + ", not its 500 mB of water", NEXT);
            }
        }).thenSucceed();
    }

    private static void tag(GameTestHelper helper) {
        if (!Fluids.WATER.defaultFluidState().is(PumpBlockEntity.INFINITE_SOURCES)) {
            helper.fail("water is not in pipeworks:infinite_sources");
        }
        if (Fluids.LAVA.defaultFluidState().is(PumpBlockEntity.INFINITE_SOURCES)) {
            helper.fail("lava is in pipeworks:infinite_sources");
        }
        helper.succeed();
    }

    private static ListeningPlayer holdingPump(GameTestHelper helper) {
        ListeningPlayer player = new ListeningPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 0))));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PipeworksRegistries.PUMP_ITEM.get(), 4));
        return player;
    }

    /** A click on the floor under {@link #PUMP}, which places the pump at {@code PUMP}. */
    private static BlockHitResult onFloor(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(PUMP.below());
        return new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false);
    }

    private static void refusedWithoutSource(GameTestHelper helper) {
        ListeningPlayer player = holdingPump(helper);
        PlacementPlan plan = Placements.planFor(player.level(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(), onFloor(helper));
        if (plan == null || plan.refusal() != PumpItem.Siting.NO_SOURCE) {
            helper.fail("the plan was " + plan + ", not a refusal for no source", PUMP);
        }
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, onFloor(helper));
        if (!helper.getBlockState(PUMP).isAir() || player.getMainHandItem().getCount() != 4) {
            helper.fail("a refused pump was placed or consumed", PUMP);
        }
        if (!player.heard.contains("message.pipeworks.pump.no_source")) {
            helper.fail("the player was told " + player.heard, PUMP);
        }
        helper.succeed();
    }

    private static void placesBesideSource(GameTestHelper helper) {
        helper.setBlock(PUMP.east(), Blocks.LAVA);
        ListeningPlayer player = holdingPump(helper);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, onFloor(helper));
        if (!helper.getBlockState(PUMP).is(PipeworksRegistries.PUMP.get()) || player.getMainHandItem().getCount() != 3) {
            helper.fail("the pump was not placed beside a lava source", PUMP);
        }
        helper.succeed();
    }
}
