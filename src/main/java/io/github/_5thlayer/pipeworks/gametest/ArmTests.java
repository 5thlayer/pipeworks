// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPipes;
import io.github._5thlayer.pipeworks.block.FluidPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Whether a pipe draws an arm toward a fluid inventory (#11): one beside it that exposes a fluid
 * handler on the facing side, followed as its capability appears and goes away with no block update.
 * What a pipe draws between two nodes is {@code SegmentTests}'.
 *
 * <p>Each test lays its pipes in a row at z 4, which {@code at(x)} indexes, and puts the inventory
 * beside or above them. The inventory beside the pipe at x faces it on its west side.
 */
final class ArmTests {

    private static final int Z = 4;
    /** Ticks for the level tick to recheck a side marked dirty, with one to spare. */
    private static final int RECHECK = 2;

    private ArmTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_pipe_draws_an_arm_toward_a_fluid_inventory", 20, ArmTests::armsTowardInventory);
        tests.test("a_pipe_placed_beside_an_inventory_draws_an_arm", 20, ArmTests::armsWhenPlacedBeside);
        tests.test("an_arm_follows_the_inventory_handler_with_no_block_update", 20, ArmTests::followsHandler);
        tests.test("a_pipe_draws_no_arm_toward_a_block_with_no_handler_on_its_side", 20, ArmTests::noArmWithoutHandler);
        tests.test("a_waiting_pipe_draws_no_arm_toward_an_inventory", 20, ArmTests::waitingDrawsNone);
        tests.test("a_pipe_draws_no_arm_toward_a_waiting_pipe", 20, ArmTests::noArmTowardWaiting);
        tests.test("a_fluid_inventory_does_not_join_the_segment", 20, ArmTests::doesNotJoin);
        tests.test("a_planned_pipe_would_draw_an_arm_toward_an_inventory", 20, ArmTests::wouldDrawArm);
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 1, Z);
    }

    private static ServerLevel level(GameTestHelper helper) {
        return helper.getLevel();
    }

    private static void pipe(GameTestHelper helper, int x) {
        helper.setBlock(at(x), PipeworksRegistries.PIPE.get().defaultBlockState());
    }

    /** Puts an inventory at {@code pos} exposing its handler on {@code face} only, or on none for null. */
    private static void inventory(GameTestHelper helper, BlockPos pos, @Nullable Direction face) {
        helper.setBlock(pos, TestInventory.BLOCK.get().defaultBlockState());
        expose(helper, pos, face);
    }

    private static void expose(GameTestHelper helper, BlockPos pos, @Nullable Direction face) {
        if (level(helper).getBlockEntity(helper.absolutePos(pos)) instanceof TestInventory.InventoryEntity entity) {
            entity.expose(face);
        } else {
            helper.fail("no test inventory at " + pos, pos);
        }
    }

    private static boolean arm(GameTestHelper helper, int x, Direction side) {
        return FluidPipeBlock.drawsArm(helper.getBlockState(at(x)), side);
    }

    private static int insert(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(fluid, amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    private static void armsTowardInventory(GameTestHelper helper) {
        pipe(helper, 1);
        inventory(helper, at(2), Direction.WEST);
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            if (!arm(helper, 1, Direction.EAST)) {
                helper.fail("the pipe draws no arm toward the inventory beside it", at(1));
            } else if (arm(helper, 1, Direction.WEST) || arm(helper, 1, Direction.UP)) {
                helper.fail("the pipe draws an arm toward air", at(1));
            }
        }).thenSucceed();
    }

    private static void armsWhenPlacedBeside(GameTestHelper helper) {
        inventory(helper, at(2), Direction.WEST);
        pipe(helper, 1);
        if (!arm(helper, 1, Direction.EAST)) {
            helper.fail("the pipe placed beside the inventory draws no arm toward it", at(1));
            return;
        }
        helper.succeed();
    }

    private static void followsHandler(GameTestHelper helper) {
        pipe(helper, 1);
        inventory(helper, at(2), null);
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            if (arm(helper, 1, Direction.EAST)) {
                helper.fail("the pipe draws an arm toward an inventory that exposes no handler", at(1));
                return;
            }
            expose(helper, at(2), Direction.WEST);
        }).thenIdle(RECHECK).thenExecute(() -> {
            if (!arm(helper, 1, Direction.EAST)) {
                helper.fail("the arm did not follow the handler appearing", at(1));
                return;
            }
            expose(helper, at(2), null);
        }).thenIdle(RECHECK).thenExecute(() -> {
            if (arm(helper, 1, Direction.EAST)) {
                helper.fail("the arm did not follow the handler going away", at(1));
                return;
            }
            expose(helper, at(2), Direction.WEST);
        }).thenIdle(RECHECK).thenExecute(() -> {
            if (!arm(helper, 1, Direction.EAST)) {
                helper.fail("the arm did not follow the handler appearing again", at(1));
            }
        }).thenSucceed();
    }

    private static void noArmWithoutHandler(GameTestHelper helper) {
        pipe(helper, 1);
        helper.setBlock(at(2), Blocks.STONE);
        // Exposes its handler on its east side, not the west side facing the pipe.
        inventory(helper, at(1).above(), Direction.EAST);
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            if (arm(helper, 1, Direction.EAST)) {
                helper.fail("the pipe draws an arm toward stone", at(1));
            } else if (arm(helper, 1, Direction.UP)) {
                helper.fail("the pipe draws an arm toward an inventory exposing a handler only on another face", at(1));
            }
        }).thenSucceed();
    }

    /** A pipe of water at x 0 and one of lava at x 2, so a pipe at x 1 waits. */
    private static void waterAndLava(GameTestHelper helper) {
        pipe(helper, 0);
        pipe(helper, 2);
        insert(level(helper).getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at(0)), null), FluidResource.of(Fluids.WATER), 50);
        insert(level(helper).getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at(2)), null), FluidResource.of(Fluids.LAVA), 50);
    }

    private static void waitingDrawsNone(GameTestHelper helper) {
        waterAndLava(helper);
        pipe(helper, 1);
        if (!FluidSegments.get(level(helper)).waits(helper.absolutePos(at(1)))) {
            helper.fail("the pipe between water and lava did not wait", at(1));
            return;
        }
        inventory(helper, at(1).above(), Direction.DOWN);
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            if (arm(helper, 1, Direction.UP)) {
                helper.fail("a waiting pipe draws an arm toward an inventory", at(1));
            }
        }).thenSucceed();
    }

    private static void noArmTowardWaiting(GameTestHelper helper) {
        waterAndLava(helper);
        pipe(helper, 1);
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            if (arm(helper, 0, Direction.EAST) || arm(helper, 2, Direction.WEST)) {
                helper.fail("a pipe draws an arm toward a waiting pipe, whose block registers the capability", at(1));
            }
        }).thenSucceed();
    }

    private static void doesNotJoin(GameTestHelper helper) {
        pipe(helper, 1);
        pipe(helper, 2);
        inventory(helper, at(3), Direction.WEST);
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            var segments = FluidSegments.get(level(helper));
            var contents = segments.contentsAt(helper.absolutePos(at(2)));
            if (!arm(helper, 2, Direction.EAST)) {
                helper.fail("the pipe draws no arm toward the inventory", at(2));
            } else if (segments.contains(helper.absolutePos(at(3))) || segments.contentsAt(helper.absolutePos(at(3))) != null) {
                helper.fail("the inventory joined the segment", at(3));
            } else if (contents == null || contents.capacity() != 2 * Pipeworks.PIPE_CAPACITY) {
                helper.fail("the segment is " + contents + ", not the two pipes' " + 2 * Pipeworks.PIPE_CAPACITY + " mB", at(2));
            }
        }).thenSucceed();
    }

    private static void wouldDrawArm(GameTestHelper helper) {
        inventory(helper, at(2), Direction.WEST);
        pipe(helper, 3);
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            ServerLevel level = level(helper);
            BlockPos planned = helper.absolutePos(at(1));
            if (!FluidPipes.wouldDrawArm(level, planned, Direction.EAST)) {
                helper.fail("a planned pipe would draw no arm toward the inventory", at(1));
            } else if (FluidPipes.wouldLink(level, planned, Direction.EAST)) {
                helper.fail("a planned pipe would link to a fluid inventory", at(1));
            } else if (FluidPipes.wouldDrawArm(level, planned, Direction.WEST) || FluidPipes.wouldDrawArm(level, planned, Direction.UP)) {
                helper.fail("a planned pipe would draw an arm toward air", at(1));
            } else if (!FluidPipes.wouldDrawArm(level, helper.absolutePos(at(4)), Direction.WEST)) {
                helper.fail("a planned pipe would draw no arm toward the pipe beside it", at(4));
            }
        }).thenSucceed();
    }
}
