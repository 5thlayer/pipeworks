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
 * as a torch is (#34). The beside tests set a source at x 2 in a row at z 4 with the pipe east of it
 * at x 3; the under test sets a water source a block above a pipe at x 3. Each checks the fluid has
 * spread on an open side before checking the pipe, then takes the source away, so the flow dries up
 * before it runs off the platform.
 */
final class FluidFlowTests {

    private static final BlockPos SOURCE = new BlockPos(2, 1, 4);
    private static final BlockPos BESIDE = SOURCE.east();
    private static final BlockPos OPEN = SOURCE.west();
    private static final BlockPos UNDER = new BlockPos(3, 1, 4);
    private static final BlockPos ABOVE = UNDER.above();
    /** Water spreads every 5 ticks in the Overworld and lava every 30: each waits for two spreads at most. */
    private static final int WATER_WAIT = 10;
    private static final int LAVA_WAIT = 40;

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
            helper.setBlock(SOURCE, Blocks.AIR);
        }).thenSucceed();
    }

    /** The water lands on the pipe and spreads over it instead of falling through. */
    private static void under(GameTestHelper helper) {
        helper.setBlock(UNDER, PipeworksRegistries.PIPE.get());
        helper.setBlock(ABOVE, Blocks.WATER);
        helper.startSequence().thenIdle(WATER_WAIT).thenExecute(() -> {
            if (!helper.getLevel().getFluidState(helper.absolutePos(ABOVE.east())).is(Fluids.FLOWING_WATER)) {
                helper.fail("the water never spread over the pipe", ABOVE.east());
            }
            if (!helper.getBlockState(UNDER).is(PipeworksRegistries.PIPE.get())) {
                helper.fail("the falling water washed the pipe away", UNDER);
            }
            helper.setBlock(ABOVE, Blocks.AIR);
        }).thenSucceed();
    }
}
