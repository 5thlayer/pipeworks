// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.EnumSet;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPipes;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import io.github._5thlayer.pipeworks.block.FluidPipeBlock;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The closed side (#9, ADR 0004): a click with an item in {@code pipeworks:closes_sides}, and the
 * API a Consumer's own tool calls, close and open a pipe's side. The game test datapack
 * ({@code src/gametest/resources}) puts a stick in the tag, which the shipped tag leaves empty; a
 * stone is an item that is not in it.
 *
 * <p>Each test lays its nodes in a row at z 4, which {@code at(x)} indexes. A click lands on an arm
 * where it hits the arm, on the core where it hits the core, as the pipe's shape puts it.
 */
final class ClosedSideTests {

    private static final int Z = 4;
    /** Ticks for the level tick to recheck a side marked dirty, with one to spare. */
    private static final int RECHECK = 2;

    private ClosedSideTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_click_on_an_arm_closes_the_face_between_two_pipes_and_splits_the_run", 20, ClosedSideTests::closesBetweenPipes);
        tests.test("a_click_on_the_core_of_either_pipe_opens_the_closed_face_and_the_run_merges", 20, ClosedSideTests::reopensFromEither);
        tests.test("a_side_closed_with_nothing_beside_it_stays_closed_when_a_pipe_is_placed", 20, ClosedSideTests::staysClosedWithNothingBeside);
        tests.test("closing_a_side_toward_a_fluid_inventory_removes_the_arm_and_the_capability", 20, ClosedSideTests::closesTowardInventory);
        tests.test("a_closed_side_tells_a_capability_cache_of_another_mod", 20, ClosedSideTests::tellsCaches);
        tests.test("opening_a_side_that_would_mix_two_fluids_is_refused_and_changes_nothing", 20, ClosedSideTests::refusesMixing);
        tests.test("opening_a_side_toward_a_port_that_would_mix_is_refused_and_changes_nothing", 20, ClosedSideTests::refusesMixingTowardPort);
        tests.test("one_click_opens_a_face_closed_before_the_pipe_beside_it_was_placed", 20, ClosedSideTests::opensInOneClick);
        tests.test("an_item_outside_the_tag_and_a_sneak_click_toggle_nothing", 20, ClosedSideTests::ignoresOtherClicks);
        tests.test("a_click_with_the_item_leaves_the_creative_pipes_screen_to_the_other_clicks", 20, ClosedSideTests::creativePipeClicks);
        tests.test("a_closed_side_survives_a_save_and_load", 20, ClosedSideTests::survivesSaving);
        tests.test("the_api_closes_and_opens_a_side_with_no_item", 20, ClosedSideTests::api);
        tests.test("closing_a_side_toward_a_tank_closes_the_tanks_face_and_a_ports_stays_its_own", 20, ClosedSideTests::tankAndPort);
        tests.test("a_side_closed_during_a_transaction_splits_the_run_once_it_is_over", 20, ClosedSideTests::heldBack);
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 1, Z);
    }

    private static ServerLevel level(GameTestHelper helper) {
        return helper.getLevel();
    }

    private static BlockPos abs(GameTestHelper helper, int x) {
        return helper.absolutePos(at(x));
    }

    private static void row(GameTestHelper helper, int from, int to) {
        for (int x = from; x <= to; x++) {
            helper.setBlock(at(x), PipeworksRegistries.PIPE.get().defaultBlockState());
        }
    }

    private static ResourceHandler<FluidResource> segment(GameTestHelper helper, int x) {
        return level(helper).getCapability(Capabilities.Fluid.BLOCK, abs(helper, x), null);
    }

    private static int insert(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(fluid, amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    private static SegmentGraph.@Nullable Contents contents(GameTestHelper helper, int x) {
        return FluidSegments.get(level(helper)).contentsAt(abs(helper, x));
    }

    private static boolean sameSegment(GameTestHelper helper, int a, int b) {
        var first = contents(helper, a);
        var second = contents(helper, b);
        return first != null && second != null && first.segment() == second.segment();
    }

    private static long amount(GameTestHelper helper, int x) {
        var contents = contents(helper, x);
        return contents == null ? -1 : contents.amount();
    }

    private static boolean arm(GameTestHelper helper, int x, Direction side) {
        return FluidPipeBlock.drawsArm(helper.getBlockState(at(x)), side);
    }

    private static boolean closed(GameTestHelper helper, int x, Direction side) {
        return FluidPipes.isClosed(level(helper), abs(helper, x), side);
    }

    /**
     * A plain right-click on the pipe at {@code x} with {@code stack} in the main hand, hitting the
     * arm on {@code side} when {@code onArm}, and the core's face on {@code side} otherwise. An arm is hit
     * on its top, so the face the hit reports is no hint to the arm's side.
     */
    private static void click(GameTestHelper helper, int x, ItemStack stack, Direction side, boolean onArm, boolean sneaking) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(sneaking);
        helper.useBlock(at(x), player, hit(helper, x, side, onArm));
    }

    private static BlockHitResult hit(GameTestHelper helper, int x, Direction side, boolean onArm) {
        BlockPos pos = abs(helper, x);
        double reach = onArm ? 0.375 : 0.1875;
        Vec3 where = Vec3.atCenterOf(pos).add(side.getStepX() * reach, side.getStepY() * reach, side.getStepZ() * reach);
        return new BlockHitResult(where, onArm ? Direction.UP : side, pos, false);
    }

    private static void toggleArm(GameTestHelper helper, int x, Direction side) {
        click(helper, x, new ItemStack(Items.STICK), side, true, false);
    }

    private static void toggleCore(GameTestHelper helper, int x, Direction side) {
        click(helper, x, new ItemStack(Items.STICK), side, false, false);
    }

    /** Four pipes holding 80 mB of water, the face between the second and the third at x 1 and 2. */
    private static void waterRun(GameTestHelper helper) {
        row(helper, 0, 3);
        insert(segment(helper, 0), FluidResource.of(Fluids.WATER), 80);
    }

    private static void closesBetweenPipes(GameTestHelper helper) {
        waterRun(helper);
        toggleArm(helper, 1, Direction.EAST);
        if (arm(helper, 1, Direction.EAST) || arm(helper, 2, Direction.WEST)) {
            helper.fail("an arm is still drawn across the closed face", at(1));
        } else if (!arm(helper, 1, Direction.WEST) || !arm(helper, 2, Direction.EAST)) {
            helper.fail("the click took the arms of the other faces", at(1));
        } else if (!closed(helper, 1, Direction.EAST) || !closed(helper, 2, Direction.WEST)) {
            helper.fail("the face is not closed on both pipes", at(1));
        } else if (sameSegment(helper, 1, 2) || !sameSegment(helper, 0, 1) || !sameSegment(helper, 2, 3)) {
            helper.fail("the run did not split into two segments at the closed face", at(1));
        } else if (amount(helper, 0) + amount(helper, 3) != 80 || amount(helper, 0) != 40) {
            helper.fail("the halves hold " + amount(helper, 0) + " and " + amount(helper, 3) + " mB of the 80", at(1));
        } else {
            helper.succeed();
        }
    }

    private static void reopensFromEither(GameTestHelper helper) {
        waterRun(helper);
        toggleArm(helper, 1, Direction.EAST);
        // A closed side has no arm to hit: the core's face is clicked.
        toggleCore(helper, 2, Direction.WEST);
        if (!sameSegment(helper, 0, 3) || !arm(helper, 1, Direction.EAST) || !arm(helper, 2, Direction.WEST)) {
            helper.fail("a click on the core of the pipe the face was not closed from did not open it", at(2));
            return;
        }
        if (amount(helper, 0) != 80 || contents(helper, 0).capacity() != 4 * Pipeworks.PIPE_CAPACITY) {
            helper.fail("the merged segment is " + contents(helper, 0), at(2));
            return;
        }
        toggleArm(helper, 2, Direction.WEST);
        if (sameSegment(helper, 1, 2)) {
            helper.fail("the face did not close again", at(2));
            return;
        }
        toggleCore(helper, 1, Direction.EAST);
        if (!sameSegment(helper, 0, 3) || amount(helper, 3) != 80) {
            helper.fail("a click on the core of the pipe that closed it did not open it", at(1));
            return;
        }
        helper.succeed();
    }

    private static void staysClosedWithNothingBeside(GameTestHelper helper) {
        row(helper, 1, 1);
        toggleCore(helper, 1, Direction.EAST);
        if (!closed(helper, 1, Direction.EAST) || closed(helper, 1, Direction.WEST)) {
            helper.fail("the side with nothing beside it did not close, or another did", at(1));
            return;
        }
        row(helper, 2, 2);
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            if (sameSegment(helper, 1, 2)) {
                helper.fail("a pipe placed beside a closed side linked to it", at(2));
            } else if (arm(helper, 1, Direction.EAST) || arm(helper, 2, Direction.WEST)) {
                helper.fail("an arm is drawn toward the pipe placed beside a closed side", at(1));
            }
        }).thenSucceed();
    }

    private static void closesTowardInventory(GameTestHelper helper) {
        row(helper, 1, 1);
        helper.setBlock(at(2), TestInventory.BLOCK.get().defaultBlockState());
        exposeWest(helper, at(2));
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            if (!arm(helper, 1, Direction.EAST) || faceHandler(helper, Direction.EAST) == null) {
                helper.fail("every side starts open: no arm, or no handler, toward the inventory", at(1));
                return;
            }
            toggleArm(helper, 1, Direction.EAST);
            if (arm(helper, 1, Direction.EAST)) {
                helper.fail("the arm toward the inventory stays on a closed side", at(1));
            } else if (faceHandler(helper, Direction.EAST) != null) {
                helper.fail("the capability on the closed side is not null", at(1));
            } else if (faceHandler(helper, Direction.WEST) == null) {
                helper.fail("closing the east side took the handler of the west", at(1));
            }
        }).thenIdle(RECHECK).thenExecute(() -> {
            if (arm(helper, 1, Direction.EAST)) {
                helper.fail("a recheck of the side drew its arm again", at(1));
                return;
            }
            toggleCore(helper, 1, Direction.EAST);
        }).thenIdle(RECHECK).thenExecute(() -> {
            if (!arm(helper, 1, Direction.EAST) || faceHandler(helper, Direction.EAST) == null) {
                helper.fail("reopening the side did not restore the arm and the capability", at(1));
            }
        }).thenSucceed();
    }

    private static void exposeWest(GameTestHelper helper, BlockPos pos) {
        if (level(helper).getBlockEntity(helper.absolutePos(pos)) instanceof TestInventory.InventoryEntity entity) {
            entity.expose(Direction.WEST);
        } else {
            helper.fail("no test inventory at " + pos, pos);
        }
    }

    /** What the pipe at x 1 exposes on {@code side}. */
    private static @Nullable ResourceHandler<FluidResource> faceHandler(GameTestHelper helper, Direction side) {
        return level(helper).getCapability(Capabilities.Fluid.BLOCK, abs(helper, 1), side);
    }

    private static void tellsCaches(GameTestHelper helper) {
        row(helper, 1, 1);
        BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> cache =
                BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level(helper), abs(helper, 1), Direction.EAST);
        if (cache.getCapability() == null) {
            helper.fail("the pipe exposes no handler on an open side", at(1));
            return;
        }
        FluidPipes.close(level(helper), abs(helper, 1), Direction.EAST);
        if (cache.getCapability() != null) {
            helper.fail("a cache of the pipe's capability still holds a handler on the closed side", at(1));
            return;
        }
        FluidPipes.open(level(helper), abs(helper, 1), Direction.EAST);
        if (cache.getCapability() == null) {
            helper.fail("a cache of the pipe's capability has no handler on the reopened side", at(1));
            return;
        }
        helper.succeed();
    }

    private static void refusesMixing(GameTestHelper helper) {
        row(helper, 0, 1);
        FluidPipes.close(level(helper), abs(helper, 0), Direction.EAST);
        insert(segment(helper, 0), FluidResource.of(Fluids.WATER), 50);
        insert(segment(helper, 1), FluidResource.of(Fluids.LAVA), 50);

        toggleCore(helper, 1, Direction.WEST);
        if (!closed(helper, 0, Direction.EAST) || !closed(helper, 1, Direction.WEST)) {
            helper.fail("a click opened a side that would mix water and lava", at(1));
        } else if (FluidPipes.open(level(helper), abs(helper, 0), Direction.EAST)) {
            helper.fail("the API opened a side that would mix water and lava", at(0));
        } else if (sameSegment(helper, 0, 1) || amount(helper, 0) != 50 || amount(helper, 1) != 50) {
            helper.fail("a refused opening changed the segments", at(0));
        } else if (arm(helper, 0, Direction.EAST) || arm(helper, 1, Direction.WEST)) {
            helper.fail("an arm is drawn across a refused opening", at(0));
        } else {
            helper.succeed();
        }
    }

    private static void refusesMixingTowardPort(GameTestHelper helper) {
        // A port adds no capacity, so the lava is in the pipe on its far side, in the port's segment.
        row(helper, 1, 1);
        helper.setBlock(at(2), TestPort.BLOCK.get().defaultBlockState());
        row(helper, 3, 3);
        // A port joins on the tick after it is placed.
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            ServerLevel level = level(helper);
            FluidPipes.close(level, abs(helper, 1), Direction.EAST);
            insert(segment(helper, 1), FluidResource.of(Fluids.WATER), 50);
            insert(FluidPorts.segment(level, abs(helper, 2)), FluidResource.of(Fluids.LAVA), 50);
            if (!sameSegment(helper, 2, 3) || sameSegment(helper, 1, 2)) {
                helper.fail("the port is not in the segment of the pipe beside it alone", at(2));
                return;
            }
            toggleCore(helper, 1, Direction.EAST);
            if (!closed(helper, 1, Direction.EAST)) {
                helper.fail("a click opened a side toward a port that would mix water and lava", at(1));
            } else if (FluidPipes.open(level, abs(helper, 1), Direction.EAST)) {
                helper.fail("the API opened a side toward a port that would mix water and lava", at(1));
            }
        }).thenIdle(RECHECK).thenExecute(() -> {
            ServerLevel level = level(helper);
            var port = FluidSegments.get(level).contentsAt(abs(helper, 2));
            if (!closed(helper, 1, Direction.EAST) || arm(helper, 1, Direction.EAST)) {
                helper.fail("the refused side is open, or draws an arm, after the tick", at(1));
            } else if (port == null || port.segment() == contents(helper, 1).segment()) {
                helper.fail("the pipe and the port are in one segment after a refused opening", at(1));
            } else if (amount(helper, 1) != 50 || port.amount() != 50
                    || !FluidSegments.get(level).closedSides(abs(helper, 1)).equals(EnumSet.of(Direction.EAST))) {
                helper.fail("a refused opening changed the contents or the mask: " + amount(helper, 1) + ", " + port.amount(), at(1));
            }
        }).thenSucceed();
    }

    private static void opensInOneClick(GameTestHelper helper) {
        row(helper, 1, 1);
        FluidPipes.close(level(helper), abs(helper, 1), Direction.EAST);
        row(helper, 2, 2);
        helper.startSequence().thenIdle(RECHECK).thenExecute(() -> {
            if (sameSegment(helper, 1, 2) || !closed(helper, 1, Direction.EAST) || closed(helper, 2, Direction.WEST)) {
                helper.fail("the setup is not a closed side beside a pipe that is open toward it", at(2));
                return;
            }
            toggleCore(helper, 2, Direction.WEST);
        }).thenIdle(RECHECK).thenExecute(() -> {
            if (closed(helper, 1, Direction.EAST) || closed(helper, 2, Direction.WEST)) {
                helper.fail("one click did not open the face on both pipes", at(2));
            } else if (!sameSegment(helper, 1, 2) || !arm(helper, 1, Direction.EAST) || !arm(helper, 2, Direction.WEST)) {
                helper.fail("the pipes did not join, or draw no arm, after the click", at(2));
            } else if (!FluidSegments.get(level(helper)).closedSides(abs(helper, 1)).isEmpty()) {
                helper.fail("the pipe still lists a closed side", at(1));
            }
        }).thenSucceed();
    }

    private static void ignoresOtherClicks(GameTestHelper helper) {
        waterRun(helper);
        click(helper, 1, new ItemStack(Items.STONE), Direction.EAST, true, false);
        click(helper, 1, new ItemStack(Items.STONE), Direction.EAST, false, false);
        click(helper, 1, ItemStack.EMPTY, Direction.EAST, true, false);
        click(helper, 1, new ItemStack(Items.STICK), Direction.EAST, true, true);
        click(helper, 1, new ItemStack(Items.STICK), Direction.EAST, false, true);
        if (closed(helper, 1, Direction.EAST) || closed(helper, 2, Direction.WEST) || !arm(helper, 1, Direction.EAST)) {
            helper.fail("an item outside the tag, an empty hand or a sneak-click closed a side", at(1));
        } else if (!sameSegment(helper, 0, 3)) {
            helper.fail("the run split", at(1));
        } else {
            helper.succeed();
        }
    }

    private static void creativePipeClicks(GameTestHelper helper) {
        helper.setBlock(at(0), PipeworksRegistries.CREATIVE_PIPE.get().defaultBlockState());
        BlockState state = helper.getBlockState(at(0));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = hit(helper, 0, Direction.EAST, false);
        // Anything else is passed on to the empty-hand use, which opens the screen.
        InteractionResult other = state.useItemOn(new ItemStack(Items.STONE), level(helper), player, InteractionHand.MAIN_HAND, hit);
        if (!(other instanceof InteractionResult.TryEmptyHandInteraction)) {
            helper.fail("a click with an item outside the tag no longer reaches the creative pipe's screen: " + other, at(0));
            return;
        }
        InteractionResult tagged = state.useItemOn(new ItemStack(Items.STICK), level(helper), player, InteractionHand.MAIN_HAND, hit);
        if (!tagged.consumesAction() || !closed(helper, 0, Direction.EAST)) {
            helper.fail("a click with the item did not close the creative pipe's side: " + tagged, at(0));
            return;
        }
        helper.succeed();
    }

    private static void survivesSaving(GameTestHelper helper) {
        waterRun(helper);
        toggleArm(helper, 1, Direction.EAST);
        FluidPipes.close(level(helper), abs(helper, 0), Direction.UP);

        var saved = FluidSegments.CODEC.encodeStart(NbtOps.INSTANCE, FluidSegments.get(level(helper))).getOrThrow();
        var loaded = FluidSegments.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();

        var first = loaded.contentsAt(abs(helper, 1));
        var second = loaded.contentsAt(abs(helper, 2));
        if (!loaded.closed(abs(helper, 1), Direction.EAST) || !loaded.closed(abs(helper, 2), Direction.WEST)
                || !loaded.closed(abs(helper, 0), Direction.UP)) {
            helper.fail("a closed side did not come back closed", at(1));
        } else if (loaded.closed(abs(helper, 1), Direction.WEST) || loaded.closed(abs(helper, 0), Direction.DOWN)) {
            helper.fail("a side that was open came back closed", at(1));
        } else if (first == null || second == null || first.segment() == second.segment()) {
            helper.fail("the run did not come back as two segments: " + first + " and " + second, at(1));
        } else if (first.amount() + second.amount() != 80 || first.capacity() != 2 * Pipeworks.PIPE_CAPACITY) {
            helper.fail("the segments came back as " + first + " and " + second, at(1));
        } else {
            helper.succeed();
        }
    }

    private static void api(GameTestHelper helper) {
        waterRun(helper);
        ServerLevel level = level(helper);
        if (!FluidPipes.close(level, abs(helper, 1), Direction.EAST) || !closed(helper, 2, Direction.WEST) || sameSegment(helper, 1, 2)) {
            helper.fail("close did not close the face between the pipes", at(1));
        } else if (FluidPipes.close(level, abs(helper, 2), Direction.WEST)) {
            helper.fail("closing a closed side reports a change", at(2));
        } else if (!FluidPipes.open(level, abs(helper, 2), Direction.WEST) || !sameSegment(helper, 0, 3) || amount(helper, 3) != 80) {
            helper.fail("open from the other pipe did not merge the run", at(2));
        } else if (FluidPipes.open(level, abs(helper, 2), Direction.WEST)) {
            helper.fail("opening an open side reports a change", at(2));
        } else if (!FluidPipes.toggle(level, abs(helper, 1), Direction.UP) || !closed(helper, 1, Direction.UP)
                || !FluidPipes.toggle(level, abs(helper, 1), Direction.UP) || closed(helper, 1, Direction.UP)) {
            helper.fail("toggle did not close and open a side with nothing beside it", at(1));
        } else if (FluidPipes.close(level, abs(helper, 1).above(), Direction.UP)) {
            helper.fail("close changed the side of a position that is no pipe", at(1));
        } else {
            helper.succeed();
        }
    }

    private static void tankAndPort(GameTestHelper helper) {
        row(helper, 1, 1);
        helper.setBlock(at(2), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
        helper.setBlock(at(0), TestPort.BLOCK.get().defaultBlockState());
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            ServerLevel level = level(helper);
            BlockPos tank = abs(helper, 2);
            FluidPipes.close(level, abs(helper, 1), Direction.EAST);
            if (level.getCapability(Capabilities.Fluid.BLOCK, tank, Direction.WEST) != null
                    || level.getCapability(Capabilities.Fluid.BLOCK, tank, Direction.EAST) == null) {
                helper.fail("closing the pipe's side did not close exactly the tank's face toward it", at(2));
                return;
            }
            FluidPipes.close(level, abs(helper, 1), Direction.WEST);
            if (!closed(helper, 1, Direction.WEST) || !FluidSegments.get(level).closedSides(abs(helper, 0)).isEmpty()) {
                helper.fail("closing a side toward a port changed the port's faces", at(0));
                return;
            }
            FluidPipes.open(level, abs(helper, 1), Direction.EAST);
            if (level.getCapability(Capabilities.Fluid.BLOCK, tank, Direction.WEST) == null || !sameSegment(helper, 1, 2)) {
                helper.fail("opening the pipe's side did not open the tank's face again", at(2));
            }
        }).thenSucceed();
    }

    private static void heldBack(GameTestHelper helper) {
        waterRun(helper);
        ServerLevel level = level(helper);
        try (Transaction transaction = Transaction.openRoot()) {
            if (!FluidPipes.close(level, abs(helper, 1), Direction.EAST)) {
                helper.fail("the side did not close during a transaction", at(1));
                return;
            }
            if (!sameSegment(helper, 1, 2)) {
                helper.fail("the run split while a transaction was open", at(1));
                return;
            }
            if (segment(helper, 1) == null || level.getCapability(Capabilities.Fluid.BLOCK, abs(helper, 1), Direction.EAST) != null) {
                helper.fail("the closed side exposes a handler while the split is held back", at(1));
                return;
            }
        }
        if (sameSegment(helper, 1, 2) || amount(helper, 0) + amount(helper, 3) != 80) {
            helper.fail("the run did not split, with its fluid kept, once the transaction was over", at(1));
            return;
        }
        helper.succeed();
    }
}
