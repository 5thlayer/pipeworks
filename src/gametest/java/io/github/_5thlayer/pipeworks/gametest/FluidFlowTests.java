// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.PipeworksRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * A pipe stands in a flowing fluid's way as a wall would, rather than being washed away and dropped
 * as a torch is (#34). Each test sets a source at x 2 in a row at z 4 with the pipe beside it at x 3,
 * or above it, and checks the fluid has spread on its open side before checking the pipe.
 */
final class FluidFlowTests {

    private static final BlockPos SOURCE = new BlockPos(2, 1, 4);
    private static final BlockPos BESIDE = SOURCE.east();
    private static final BlockPos BELOW = SOURCE.below().east();
    private static final BlockPos OPEN = SOURCE.west();
    /** Water spreads every 5 ticks in the Overworld, lava every 30. */
    private static final int WATER_WAIT = 20;
    private static final int LAVA_WAIT = 70;

    private FluidFlowTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("fluid_flow_leaves_a_pipe_beside_water", WATER_WAIT + 10,
                helper -> beside(helper, PipeworksRegistries.PIPE.get(), Blocks.WATER, Fluids.WATER, WATER_WAIT));
        tests.test("fluid_flow_leaves_a_pipe_beside_lava", LAVA_WAIT + 10,
                helper -> beside(helper, PipeworksRegistries.PIPE.get(), Blocks.LAVA, Fluids.LAVA, LAVA_WAIT));
        tests.test("fluid_flow_leaves_a_creative_pipe_beside_water", WATER_WAIT + 10,
                helper -> beside(helper, PipeworksRegistries.CREATIVE_PIPE.get(), Blocks.WATER, Fluids.WATER, WATER_WAIT));
        tests.test("fluid_flow_leaves_a_creative_pipe_beside_lava", LAVA_WAIT + 10,
                helper -> beside(helper, PipeworksRegistries.CREATIVE_PIPE.get(), Blocks.LAVA, Fluids.LAVA, LAVA_WAIT));
        tests.test("fluid_flow_leaves_a_pipe_under_falling_water", WATER_WAIT + 10, FluidFlowTests::under);
    }

    private static void beside(GameTestHelper helper, Block pipe, Block source, Fluid fluid, int wait) {
        helper.setBlock(BESIDE, pipe);
        helper.setBlock(SOURCE, source);
        helper.startSequence().thenIdle(wait).thenExecute(() -> {
            if (!helper.getLevel().getFluidState(helper.absolutePos(OPEN)).getType().isSame(fluid)) {
                helper.fail("the " + fluid + " never spread, so the pipe was never tested", OPEN);
            }
            if (!helper.getBlockState(BESIDE).is(pipe)) {
                helper.fail("the flowing " + fluid + " washed the pipe away", BESIDE);
            }
        }).thenSucceed();
    }

    /** Water falls onto the pipe below a source raised a block over the floor, and spreads instead. */
    private static void under(GameTestHelper helper) {
        BlockPos pipe = BESIDE;
        BlockPos source = pipe.above();
        helper.setBlock(pipe, PipeworksRegistries.PIPE.get());
        helper.setBlock(source, Blocks.WATER);
        helper.startSequence().thenIdle(WATER_WAIT).thenExecute(() -> {
            if (!helper.getLevel().getFluidState(helper.absolutePos(source.east())).is(Fluids.FLOWING_WATER)) {
                helper.fail("the water never spread over the pipe", source.east());
            }
            if (!helper.getBlockState(pipe).is(PipeworksRegistries.PIPE.get())) {
                helper.fail("the falling water washed the pipe away", pipe);
            }
        }).thenSucceed();
    }
}
