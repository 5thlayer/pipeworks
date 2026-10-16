// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.Arrays;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Whether a node removed without {@code UPDATE_NEIGHBORS}, which never hears it go, still leaves its
 * segment (ADR 0003, #4): found by the level tick's sweep, or as soon as its segment is reached
 * through it.
 *
 * <p>Each test lays a pipe, a node and a pipe at x 1 to 3, one row at z 2 removed without telling
 * its neighbours and one at z 6 broken as a player would, so the split the first ends with is the
 * one a broken node leaves.
 */
final class GhostNodeTests {

    /** What {@code /setblock} sets a block with in strict mode. */
    private static final int STRICT = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    /** What {@code StructureTemplate.placeInWorld} clears a test's area with. */
    private static final int STRUCTURE = Block.UPDATE_INVISIBLE | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private static final int GHOST = 2;
    private static final int BROKEN = 6;
    private static final int PORT_JOINS = 2;

    private GhostNodeTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_pipe_set_to_air_strictly_leaves_its_segment", 100,
                helper -> leavesUnheard(helper, pipe(), Blocks.AIR.defaultBlockState(), STRICT));
        tests.test("a_tank_set_to_air_strictly_leaves_its_segment", 100,
                helper -> leavesUnheard(helper, PipeworksRegistries.STORAGE_TANK.get().defaultBlockState(), Blocks.AIR.defaultBlockState(), STRICT));
        tests.test("a_pipe_a_structure_clears_leaves_its_segment", 100,
                helper -> leavesUnheard(helper, pipe(), Blocks.BARRIER.defaultBlockState(), STRUCTURE));
        tests.test("a_port_set_to_air_strictly_leaves_its_segment", 100,
                helper -> leavesUnheard(helper, TestPort.BLOCK.get().defaultBlockState(), Blocks.AIR.defaultBlockState(), STRICT));
        tests.test("a_segment_reached_through_a_ghost_has_no_handler_there", 20, GhostNodeTests::reachedThroughGhost);
        tests.test("a_node_in_a_chunk_not_loaded_is_not_judged", 60, GhostNodeTests::notLoadedNotJudged);
    }

    private static BlockState pipe() {
        return PipeworksRegistries.PIPE.get().defaultBlockState();
    }

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, 1, z);
    }

    private static ServerLevel level(GameTestHelper helper) {
        return helper.getLevel();
    }

    private static int insert(ResourceHandler<FluidResource> handler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(FluidResource.of(Fluids.WATER), amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    /** A pipe, {@code node} and a pipe at z {@code z}. */
    private static void row(GameTestHelper helper, int z, BlockState node) {
        helper.setBlock(at(1, z), pipe());
        helper.setBlock(at(2, z), node);
        helper.setBlock(at(3, z), pipe());
    }

    /** Puts 50 mB of water in the row at z {@code z}. */
    private static void fill(GameTestHelper helper, int z) {
        insert(level(helper).getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at(1, z)), null), 50);
    }

    private static boolean isNode(GameTestHelper helper, int x, int z) {
        return FluidSegments.get(level(helper)).contains(helper.absolutePos(at(x, z)));
    }

    private static SegmentGraph.@Nullable Contents contentsAt(GameTestHelper helper, int x, int z) {
        return FluidSegments.get(level(helper)).contentsAt(level(helper), helper.absolutePos(at(x, z)));
    }

    /**
     * Removes the middle node of one row with {@code flags} and breaks the other's, then waits for the
     * sweep alone to find the first: the removed position is never reached until it has left.
     */
    private static void leavesUnheard(GameTestHelper helper, BlockState node, BlockState replacement, int flags) {
        row(helper, GHOST, node);
        row(helper, BROKEN, node);
        helper.startSequence().thenIdle(PORT_JOINS).thenExecute(() -> {
            fill(helper, GHOST);
            fill(helper, BROKEN);
            if (!isNode(helper, 2, GHOST) || !isNode(helper, 2, BROKEN)) {
                helper.fail("the middle node joined no segment", at(2, GHOST));
                return;
            }
            level(helper).setBlock(helper.absolutePos(at(2, GHOST)), replacement, flags);
            helper.setBlock(at(2, BROKEN), Blocks.AIR);
        }).thenWaitUntil(() -> {
            if (isNode(helper, 2, GHOST)) {
                helper.fail("the removed node is still in a segment", at(2, GHOST));
            }
        }).thenExecute(() -> sameSplit(helper)).thenSucceed();
    }

    /** The removed row split as the broken one did: a run on each side, each of its own pipe alone. */
    private static void sameSplit(GameTestHelper helper) {
        long[] ghost = sides(helper, GHOST);
        long[] broken = sides(helper, BROKEN);
        if (ghost == null || broken == null) {
            helper.fail("a pipe beside a removed node is not a run of itself alone", at(1, GHOST));
            return;
        }
        // The two runs weigh the same, so which takes a rounded-up unit may differ between rows.
        Arrays.sort(ghost);
        Arrays.sort(broken);
        if (!Arrays.equals(ghost, broken)) {
            helper.fail("the removed node's row holds " + Arrays.toString(ghost) + " mB, a broken one's "
                    + Arrays.toString(broken), at(2, GHOST));
        }
    }

    /** What the pipes on either side of the middle hold, or null if either is not a run of itself alone. */
    private static long @Nullable [] sides(GameTestHelper helper, int z) {
        SegmentGraph.Contents left = contentsAt(helper, 1, z);
        SegmentGraph.Contents right = contentsAt(helper, 3, z);
        if (left == null || right == null || left.capacity() != Pipeworks.PIPE_CAPACITY || right.capacity() != Pipeworks.PIPE_CAPACITY) {
            return null;
        }
        return new long[] {left.amount(), right.amount()};
    }

    /**
     * A port and a pipe removed strictly, then reached through their own positions on the same tick,
     * before any sweep: neither yields a handler or contents, and both have left.
     */
    private static void reachedThroughGhost(GameTestHelper helper) {
        row(helper, GHOST, TestPort.BLOCK.get().defaultBlockState());
        row(helper, BROKEN, pipe());
        helper.startSequence().thenIdle(PORT_JOINS).thenExecute(() -> {
            fill(helper, GHOST);
            fill(helper, BROKEN);
            BlockPos port = helper.absolutePos(at(2, GHOST));
            BlockPos pipe = helper.absolutePos(at(2, BROKEN));
            level(helper).setBlock(port, Blocks.AIR.defaultBlockState(), STRICT);
            level(helper).setBlock(pipe, Blocks.AIR.defaultBlockState(), STRICT);
            if (FluidPorts.segment(level(helper), port) != null) {
                helper.fail("a port's segment reached through its ghost has a handler", at(2, GHOST));
            } else if (level(helper).getCapability(Capabilities.Fluid.BLOCK, port, null) != null) {
                helper.fail("the ghost port's position exposes a fluid face", at(2, GHOST));
            } else if (isNode(helper, 2, GHOST)) {
                helper.fail("the port reached through its ghost is still in a segment", at(2, GHOST));
            } else if (contentsAt(helper, 2, BROKEN) != null) {
                helper.fail("a pipe's segment reached through its ghost has contents", at(2, BROKEN));
            } else if (isNode(helper, 2, BROKEN)) {
                helper.fail("the pipe reached through its ghost is still in a segment", at(2, BROKEN));
            } else if (sides(helper, GHOST) == null || sides(helper, BROKEN) == null) {
                helper.fail("a pipe beside a ghost is not a run of itself alone", at(1, GHOST));
            }
        }).thenSucceed();
    }

    /**
     * A node saved in a chunk that is not loaded, as one is while its player is away: neither the
     * sweep nor reaching it judges it, so it keeps its fluid.
     */
    private static void notLoadedNotJudged(GameTestHelper helper) {
        ServerLevel level = level(helper);
        BlockPos far = helper.absolutePos(at(2, GHOST)).east(1 << 20);
        if (level.isLoaded(far)) {
            helper.fail("the far position is loaded", at(2, GHOST));
            return;
        }
        FluidSegments segments = FluidSegments.get(level);
        segments.join(level, far, 100, FluidSegments.ALL_FACES);
        ResourceHandler<FluidResource> handler = segments.handlerAt(level, far);
        if (handler == null || insert(handler, 30) != 30) {
            helper.fail("the node in a chunk not loaded was judged when its segment was reached", at(2, GHOST));
            segments.leave(level, far);
            return;
        }
        helper.startSequence().thenIdle(40).thenExecute(() -> {
            SegmentGraph.Contents contents = segments.contentsAt(level, far);
            segments.leave(level, far);
            if (contents == null || contents.amount() != 30) {
                helper.fail("the node in a chunk not loaded came back as " + contents + " after the sweep", at(2, GHOST));
            }
        }).thenSucceed();
    }
}
