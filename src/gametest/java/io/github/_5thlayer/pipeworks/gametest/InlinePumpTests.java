// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.concurrent.atomic.AtomicLong;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksConfig;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.block.FluidPipeBlock;
import io.github._5thlayer.pipeworks.inlinepump.InlinePumpBlock;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The in-line pump (ADR 0009): it moves fluid from the segment behind it to the one in front at its
 * rate, the segment behind refills from the fluid inventories it touches and the one in front empties
 * into those it touches, through open sides only, never mixing; a segment between two pumps reaches
 * none, and one no pump serves stays passive. Each test lays its row at z 4, which {@code at(x)}
 * indexes, with the pump pumping east unless it says otherwise, and waits a tick after placing a pump,
 * whose block entity tells the segments it is there as it loads.
 */
final class InlinePumpTests {

    private static final int Z = 4;
    /** Long enough to move a bucket at 60 mB a tick, with time to spare. */
    private static final int BUCKET = 40;

    private InlinePumpTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("inline_pump_is_no_node_and_pipes_arm_only_toward_its_back_and_front", 20, InlinePumpTests::armsAndNoNode);
        tests.test("inline_pump_moves_at_most_its_rate_a_tick", 40, InlinePumpTests::rate);
        tests.test("inline_pump_draws_a_passive_inventory_into_the_segment_in_front", 80, InlinePumpTests::drawsInventory);
        tests.test("inline_pump_fills_an_inventory_the_segment_in_front_touches", 80, InlinePumpTests::fillsInventory);
        tests.test("inline_pump_fills_a_cauldron_once_its_segment_holds_a_bucket", 100, InlinePumpTests::fillsCauldron);
        tests.test("inline_pump_draws_an_inventory_directly_behind_it", 80, InlinePumpTests::drawsDirectly);
        tests.test("inline_pump_fills_an_inventory_directly_in_front_of_it", 80, InlinePumpTests::fillsDirectly);
        tests.test("inline_pump_fills_a_cauldron_directly_in_front_of_it", 80, InlinePumpTests::fillsCauldronDirectly);
        tests.test("inline_pump_draws_nothing_through_a_closed_side", 60, InlinePumpTests::drawsNothingClosed);
        tests.test("inline_pump_fills_nothing_through_a_closed_side", 60, InlinePumpTests::fillsNothingClosed);
        tests.test("inline_pump_segment_between_two_pumps_reaches_no_inventory", 100, InlinePumpTests::conduit);
        tests.test("inline_pump_absent_leaves_a_passive_inventory_alone", 40, InlinePumpTests::noPumpIsPassive);
        tests.test("inline_pump_segment_holding_one_fluid_draws_no_other_from_an_inventory", 40, InlinePumpTests::refillNeverMixes);
        tests.test("inline_pump_empty_segment_takes_the_first_fluid_offered_and_no_other", 40, InlinePumpTests::emptyRefillTakesOneFluid);
        tests.test("inline_pump_moves_no_fluid_into_a_segment_holding_another", 40, InlinePumpTests::moveNeverMixes);
        tests.test("inline_pump_turns_round_when_clicked_with_a_side_closing_item", 80, InlinePumpTests::turnsRound);
        tests.testOnItsOwn("inline_pump_rate_follows_the_server_config", 60, InlinePumpTests::configuredRate);
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 1, Z);
    }

    private static void pipe(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, PipeworksRegistries.PIPE.get().defaultBlockState());
    }

    private static void tank(GameTestHelper helper, int x) {
        helper.setBlock(at(x), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
    }

    private static void pump(GameTestHelper helper, int x, Direction facing) {
        helper.setBlock(at(x), PipeworksRegistries.INLINE_PUMP.get().defaultBlockState().setValue(InlinePumpBlock.FACING, facing));
    }

    /** A test inventory at {@code pos} exposing its handler on {@code face}, holding {@code amount} of {@code fluid}. */
    private static void inventory(GameTestHelper helper, BlockPos pos, Direction face, @Nullable Fluid fluid, int amount) {
        helper.setBlock(pos, TestInventory.BLOCK.get().defaultBlockState());
        TestInventory.InventoryEntity entity = inventoryAt(helper, pos);
        entity.expose(face);
        if (fluid != null && insert(entity.handler(), fluid, amount) != amount) {
            helper.fail("the test inventory took less than " + amount + " mB", pos);
        }
    }

    private static TestInventory.InventoryEntity inventoryAt(GameTestHelper helper, BlockPos pos) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof TestInventory.InventoryEntity entity) {
            return entity;
        }
        throw new IllegalStateException("no test inventory at " + pos);
    }

    private static long held(GameTestHelper helper, BlockPos pos) {
        return inventoryAt(helper, pos).handler().getAmountAsLong(0);
    }

    private static int insert(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return moved;
        }
    }

    /** Fills the segment at {@code x} through its fluid face, as another mod's machine would. */
    private static void fill(GameTestHelper helper, int x, Fluid fluid, int amount) {
        var handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at(x)), null);
        if (handler == null || insert(handler, fluid, amount) != amount) {
            helper.fail("the segment took less than " + amount + " mB of " + fluid, at(x));
        }
    }

    private static SegmentGraph.@Nullable Contents contents(GameTestHelper helper, int x) {
        return FluidSegments.get(helper.getLevel()).contentsAt(helper.getLevel(), helper.absolutePos(at(x)));
    }

    private static long amount(GameTestHelper helper, int x) {
        var contents = contents(helper, x);
        return contents == null ? 0 : contents.amount();
    }

    private static void assertSegment(GameTestHelper helper, int x, @Nullable Fluid fluid, long amount) {
        var contents = contents(helper, x);
        String expected = fluid == null ? null : BuiltInRegistries.FLUID.getKey(fluid).toString();
        long got = contents == null ? 0 : contents.amount();
        if (got != amount || amount > 0 && (contents == null || !expected.equals(contents.fluid()))) {
            helper.fail("the segment holds " + contents + ", not " + amount + " mB of " + expected, at(x));
        }
    }

    private static void assertHeld(GameTestHelper helper, BlockPos pos, long amount) {
        long got = held(helper, pos);
        if (got != amount) {
            helper.fail("the inventory holds " + got + " mB, not " + amount, pos);
        }
    }

    private static void assertFullCauldron(GameTestHelper helper, BlockPos pos) {
        var state = helper.getBlockState(pos);
        if (!state.is(Blocks.WATER_CAULDRON) || state.getValue(LayeredCauldronBlock.LEVEL) != 3) {
            helper.fail("the cauldron is " + state + ", not full of water", pos);
        }
    }

    private static void armsAndNoNode(GameTestHelper helper) {
        pipe(helper, at(1));
        pump(helper, 2, Direction.EAST);
        pipe(helper, at(3));
        pipe(helper, at(2).above());
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            if (!FluidPipeBlock.drawsArm(helper.getBlockState(at(1)), Direction.EAST)
                    || !FluidPipeBlock.drawsArm(helper.getBlockState(at(3)), Direction.WEST)) {
                helper.fail("a pipe at the in-line pump's back or front draws no arm toward it", at(2));
            } else if (FluidPipeBlock.drawsArm(helper.getBlockState(at(2).above()), Direction.DOWN)) {
                helper.fail("a pipe beside the in-line pump's side draws an arm toward it", at(2).above());
            } else if (FluidSegments.get(helper.getLevel()).contains(helper.absolutePos(at(2)))) {
                helper.fail("the in-line pump is a node of a segment", at(2));
            } else if (contents(helper, 1).segment() == contents(helper, 3).segment()) {
                helper.fail("the pipes behind and in front of the in-line pump share a segment", at(2));
            }
        }).thenSucceed();
    }

    private static void rate(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        AtomicLong before = new AtomicLong();
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, 1, Fluids.WATER, 10_000)).thenIdle(5)
                .thenExecute(() -> before.set(amount(helper, 3))).thenIdle(10).thenExecute(() -> {
                    long moved = amount(helper, 3) - before.get();
                    if (moved != 10L * PipeworksConfig.inlinePumpRate()) {
                        helper.fail("the in-line pump moved " + moved + " mB in 10 ticks, not 10 times its rate", at(2));
                    }
                }).thenSucceed();
    }

    private static void configuredRate(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        int was = PipeworksConfig.INLINE_PUMP_RATE.get();
        AtomicLong before = new AtomicLong();
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, 1, Fluids.WATER, 10_000)).thenIdle(5)
                .thenExecute(() -> {
                    PipeworksConfig.INLINE_PUMP_RATE.set(100);
                    before.set(amount(helper, 3));
                }).thenIdle(10).thenExecute(() -> {
                    long moved;
                    try {
                        moved = amount(helper, 3) - before.get();
                    } finally {
                        PipeworksConfig.INLINE_PUMP_RATE.set(was);
                    }
                    if (moved != 1_000) {
                        helper.fail("at a rate of 100 the in-line pump moved " + moved + " mB in 10 ticks, not 1000", at(2));
                    }
                }).thenSucceed();
    }

    private static void drawsInventory(GameTestHelper helper) {
        inventory(helper, at(0), Direction.EAST, Fluids.WATER, 1_000);
        pipe(helper, at(1));
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        helper.startSequence().thenIdle(BUCKET).thenExecute(() -> {
            assertHeld(helper, at(0), 0);
            assertSegment(helper, 3, Fluids.WATER, 1_000);
        }).thenSucceed();
    }

    private static void fillsInventory(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        pipe(helper, at(3));
        inventory(helper, at(4), Direction.WEST, null, 0);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, 1, Fluids.WATER, 1_000)).thenIdle(BUCKET).thenExecute(() -> {
            assertHeld(helper, at(4), 1_000);
            assertSegment(helper, 1, null, 0);
        }).thenSucceed();
    }

    /** A cauldron takes only whole buckets, so the run in front of the pump holds one: a tank and a pipe. */
    private static void fillsCauldron(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        pipe(helper, at(4));
        helper.setBlock(at(5), Blocks.CAULDRON);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, 1, Fluids.WATER, 2_000)).thenIdle(2 * BUCKET).thenExecute(() -> {
            assertFullCauldron(helper, at(5));
            if (amount(helper, 1) + amount(helper, 3) != 1_000) {
                helper.fail("the runs hold " + (amount(helper, 1) + amount(helper, 3)) + " mB, not the 1000 left after a bucket", at(3));
            }
        }).thenSucceed();
    }

    private static void drawsDirectly(GameTestHelper helper) {
        inventory(helper, at(1), Direction.EAST, Fluids.WATER, 1_000);
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        helper.startSequence().thenIdle(BUCKET).thenExecute(() -> {
            assertHeld(helper, at(1), 0);
            assertSegment(helper, 3, Fluids.WATER, 1_000);
        }).thenSucceed();
    }

    private static void fillsDirectly(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        inventory(helper, at(3), Direction.WEST, null, 0);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, 1, Fluids.WATER, 1_000)).thenIdle(BUCKET).thenExecute(() -> {
            assertHeld(helper, at(3), 1_000);
            assertSegment(helper, 1, null, 0);
        }).thenSucceed();
    }

    /** Too small a move for a cauldron at the rate, so the pump moves a whole bucket and waits it off. */
    private static void fillsCauldronDirectly(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        helper.setBlock(at(3), Blocks.CAULDRON);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, 1, Fluids.WATER, 2_000)).thenIdle(10).thenExecute(() -> {
            assertFullCauldron(helper, at(3));
            assertSegment(helper, 1, Fluids.WATER, 1_000);
        }).thenSucceed();
    }

    private static void drawsNothingClosed(GameTestHelper helper) {
        inventory(helper, at(0), Direction.EAST, Fluids.WATER, 1_000);
        pipe(helper, at(1));
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        FluidSegments.get(helper.getLevel()).setSide(helper.getLevel(), helper.absolutePos(at(1)), Direction.WEST, false);
        helper.startSequence().thenIdle(BUCKET).thenExecute(() -> {
            assertHeld(helper, at(0), 1_000);
            assertSegment(helper, 3, null, 0);
        }).thenSucceed();
    }

    private static void fillsNothingClosed(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        pipe(helper, at(3));
        inventory(helper, at(4), Direction.WEST, null, 0);
        FluidSegments.get(helper.getLevel()).setSide(helper.getLevel(), helper.absolutePos(at(3)), Direction.EAST, false);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, 1, Fluids.WATER, 1_000)).thenIdle(BUCKET).thenExecute(() -> {
            assertHeld(helper, at(4), 0);
            assertSegment(helper, 3, Fluids.WATER, PipeworksConfig.pipeCapacity());
        }).thenSucceed();
    }

    /** The pipe between the two pumps passes the water on, and its inventory above it keeps its 500 mB. */
    private static void conduit(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        pipe(helper, at(3));
        inventory(helper, at(3).above(), Direction.DOWN, Fluids.WATER, 500);
        pump(helper, 4, Direction.EAST);
        tank(helper, 5);
        helper.startSequence().thenIdle(1).thenExecute(() -> fill(helper, 1, Fluids.WATER, 1_000)).thenIdle(2 * BUCKET).thenExecute(() -> {
            assertHeld(helper, at(3).above(), 500);
            assertSegment(helper, 5, Fluids.WATER, 1_000);
        }).thenSucceed();
    }

    private static void noPumpIsPassive(GameTestHelper helper) {
        inventory(helper, at(0), Direction.EAST, Fluids.WATER, 1_000);
        pipe(helper, at(1));
        pipe(helper, at(2));
        tank(helper, 3);
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            assertHeld(helper, at(0), 1_000);
            assertSegment(helper, 3, null, 0);
        }).thenSucceed();
    }

    /** The pipe behind the pump holds water with room to spare, and the run in front is full, so nothing moves. */
    private static void refillNeverMixes(GameTestHelper helper) {
        inventory(helper, at(0), Direction.EAST, Fluids.LAVA, 1_000);
        pipe(helper, at(1));
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        fill(helper, 1, Fluids.WATER, 50);
        fill(helper, 3, Fluids.WATER, (int) PipeworksConfig.tankCapacity());
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            assertHeld(helper, at(0), 1_000);
            assertSegment(helper, 1, Fluids.WATER, 50);
        }).thenSucceed();
    }

    /**
     * The empty pipe behind the pump touches an inventory of lava and one of water: it takes whichever
     * comes first, and the other is refused while that one still flows, a bucket at 60 mB a tick taking
     * longer than the 8 ticks waited. Only once the pipe is empty again may it take the other.
     */
    private static void emptyRefillTakesOneFluid(GameTestHelper helper) {
        inventory(helper, at(0), Direction.EAST, Fluids.LAVA, 1_000);
        pipe(helper, at(1));
        inventory(helper, at(1).above(), Direction.DOWN, Fluids.WATER, 1_000);
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        helper.startSequence().thenIdle(8).thenExecute(() -> {
            long lava = held(helper, at(0));
            long water = held(helper, at(1).above());
            if ((lava == 1_000) == (water == 1_000)) {
                helper.fail("the inventories hold " + lava + " mB of lava and " + water + " mB of water: not one drawn and one untouched", at(1));
                return;
            }
            Fluid drawn = lava == 1_000 ? Fluids.WATER : Fluids.LAVA;
            long taken = 1_000 - (drawn == Fluids.WATER ? water : lava);
            assertSegment(helper, 1, drawn, amount(helper, 1));
            assertSegment(helper, 3, drawn, taken - amount(helper, 1));
        }).thenSucceed();
    }

    private static void moveNeverMixes(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        fill(helper, 1, Fluids.LAVA, 1_000);
        fill(helper, 3, Fluids.WATER, 1_000);
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            assertSegment(helper, 1, Fluids.LAVA, 1_000);
            assertSegment(helper, 3, Fluids.WATER, 1_000);
        }).thenSucceed();
    }

    /** Turned round before any fluid comes, the pump then fills the tank it first drew from. */
    private static void turnsRound(GameTestHelper helper) {
        tank(helper, 1);
        pump(helper, 2, Direction.EAST);
        tank(helper, 3);
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            click(helper, 2, true);
            if (helper.getBlockState(at(2)).getValue(InlinePumpBlock.FACING) != Direction.EAST) {
                helper.fail("a sneak-click with a stick turned the in-line pump round", at(2));
                return;
            }
            click(helper, 2, false);
            if (helper.getBlockState(at(2)).getValue(InlinePumpBlock.FACING) != Direction.WEST) {
                helper.fail("a click with a stick did not turn the in-line pump round", at(2));
                return;
            }
            fill(helper, 3, Fluids.WATER, 1_000);
        }).thenIdle(BUCKET).thenExecute(() -> {
            assertSegment(helper, 1, Fluids.WATER, 1_000);
            assertSegment(helper, 3, null, 0);
        }).thenSucceed();
    }

    private static void click(GameTestHelper helper, int x, boolean sneaking) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        player.setShiftKeyDown(sneaking);
        BlockPos pos = helper.absolutePos(at(x));
        helper.useBlock(at(x), player, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
    }
}
