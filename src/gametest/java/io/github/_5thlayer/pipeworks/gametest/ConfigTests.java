// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksConfig;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;

/**
 * Whether {@code pipeworks-server.toml} sets the pipe's and the tank's capacity for the nodes placed
 * after it changes, and only those (ADR 0003, #2).
 *
 * <p>The tests run beside others, so one that changes a capacity places its node and puts the value
 * back in the same call, before any other test places a pipe. Each lays its nodes in a row at z 4,
 * which {@code at(x)} indexes.
 */
final class ConfigTests {

    private static final int Z = 4;

    private ConfigTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_fresh_server_writes_the_capacities_at_their_defaults", 1, ConfigTests::writesDefaults);
        tests.test("a_pipe_placed_after_a_change_takes_the_new_capacity", 20, ConfigTests::pipeCapacity);
        tests.test("a_tank_placed_after_a_change_takes_the_new_capacity", 20, ConfigTests::tankCapacity);
        tests.test("a_capacity_below_one_is_corrected_to_one", 1, ConfigTests::belowOne);
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 1, Z);
    }

    private static SegmentGraph.@Nullable Contents contentsAt(GameTestHelper helper, int x) {
        return FluidSegments.get(helper.getLevel()).contentsAt(helper.getLevel(), helper.absolutePos(at(x)));
    }

    private static void writesDefaults(GameTestHelper helper) {
        Path file = FMLPaths.CONFIGDIR.get().resolve("pipeworks-server.toml");
        String toml;
        try {
            toml = Files.readString(file);
        } catch (IOException e) {
            helper.fail("no server config at " + file + ": " + e);
            return;
        }
        for (String line : new String[] {"pipeCapacity = 100", "tankCapacity = 25000", "#The millibuckets a pipe", "#The millibuckets a storage tank"}) {
            if (!toml.contains(line)) {
                helper.fail("the server config has no line " + line + ":\n" + toml);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Places {@code state} at {@code x} with {@code value} set, then puts the value back, all before
     * any other test runs.
     */
    private static void placeWith(GameTestHelper helper, ModConfigSpec.LongValue capacity, long value, int x, BlockState state) {
        long was = capacity.get();
        capacity.set(value);
        try {
            helper.setBlock(at(x), state);
        } finally {
            capacity.set(was);
        }
    }

    private static void pipeCapacity(GameTestHelper helper) {
        helper.setBlock(at(0), PipeworksRegistries.PIPE.get().defaultBlockState());
        placeWith(helper, PipeworksConfig.PIPE_CAPACITY, 250, 1, PipeworksRegistries.PIPE.get().defaultBlockState());
        placeWith(helper, PipeworksConfig.PIPE_CAPACITY, 250, 2, PipeworksRegistries.CREATIVE_PIPE.get().defaultBlockState());
        SegmentGraph.Contents contents = contentsAt(helper, 0);
        long expected = Pipeworks.PIPE_CAPACITY + 250 + 250;
        if (contents == null || contents.capacity() != expected || contents.nodes() != 3) {
            helper.fail("the run of an old pipe and two new ones is " + contents + ", not " + expected + " mB", at(1));
            return;
        }
        helper.succeed();
    }

    private static void tankCapacity(GameTestHelper helper) {
        helper.setBlock(at(0), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
        placeWith(helper, PipeworksConfig.TANK_CAPACITY, 1_000, 1, PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
        SegmentGraph.Contents contents = contentsAt(helper, 0);
        long expected = Pipeworks.TANK_CAPACITY + 1_000;
        if (contents == null || contents.capacity() != expected || contents.nodes() != 2) {
            helper.fail("an old tank and a new one are " + contents + ", not " + expected + " mB", at(1));
            return;
        }
        helper.succeed();
    }

    private static void belowOne(GameTestHelper helper) {
        for (ModConfigSpec.LongValue capacity : new ModConfigSpec.LongValue[] {PipeworksConfig.PIPE_CAPACITY, PipeworksConfig.TANK_CAPACITY}) {
            var spec = capacity.getSpec();
            if (spec.test(0L) || spec.test(-5L) || !Long.valueOf(1).equals(spec.correct(0L))) {
                helper.fail(capacity.getPath() + " takes " + spec.correct(0L) + " for 0");
                return;
            }
        }
        helper.succeed();
    }
}
