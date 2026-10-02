// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The creative tank, the one block a player can fill a segment from alone. Each test lays its nodes
 * in a row at z 4, the creative tank at x 0, which {@code at(x)} indexes.
 */
final class CreativeTankTests {

    private static final int Z = 4;
    private static final int PORT_JOINS = 2;

    private CreativeTankTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_bucket_of_water_on_a_creative_tank_fills_its_segment_every_tick", 20, CreativeTankTests::fillsSegment);
        tests.test("a_bucket_of_lava_is_refused_by_a_creative_tank_whose_segment_holds_water", 20, CreativeTankTests::refusesLava);
        tests.test("a_creative_tank_set_to_water_refuses_lava", 20, CreativeTankTests::refusesOtherThanSet);
        tests.test("an_empty_bucket_fills_from_a_creative_tank_any_number_of_times", 20, CreativeTankTests::fillsBuckets);
        tests.test("a_creative_tanks_fluid_survives_a_save_and_load", 20, CreativeTankTests::survivesSaving);
        tests.test("breaking_a_creative_tank_stops_it_sourcing", 20, CreativeTankTests::breakingClears);
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 1, Z);
    }

    private static void creativeTank(GameTestHelper helper) {
        helper.setBlock(at(0), PipeworksRegistries.CREATIVE_TANK.get().defaultBlockState());
    }

    private static Player holding(GameTestHelper helper, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static SegmentGraph.@Nullable Contents contentsAt(GameTestHelper helper, int x) {
        return FluidSegments.get(helper.getLevel()).contentsAt(helper.absolutePos(at(x)));
    }

    private static ResourceHandler<FluidResource> faceAt(GameTestHelper helper, int x) {
        return helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at(x)), null);
    }

    private static int insert(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(fluid, amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    private static int extract(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.extract(fluid, amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    private static void fillsSegment(GameTestHelper helper) {
        creativeTank(helper);
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(at(x), PipeworksRegistries.PIPE.get().defaultBlockState());
        }
        helper.setBlock(at(4), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
        helper.setBlock(at(5), TestPort.BLOCK.get().defaultBlockState());
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        Player player = holding(helper, bucket);
        helper.startSequence().thenIdle(PORT_JOINS).thenExecute(() -> {
            helper.useBlock(at(0), player);
            if (!bucket.is(Items.WATER_BUCKET) || bucket.getCount() != 1 || !player.getMainHandItem().is(Items.WATER_BUCKET)) {
                helper.fail("the bucket did not stay full: " + player.getMainHandItem(), at(0));
            }
        }).thenIdle(1).thenExecute(() -> {
            long capacity = 2 * Pipeworks.TANK_CAPACITY + 3 * Pipeworks.PIPE_CAPACITY;
            var contents = contentsAt(helper, 4);
            if (contents == null || contents.amount() != capacity || !"minecraft:water".equals(contents.fluid())) {
                helper.fail("the segment is " + contents + ", not " + capacity + " mB of water", at(4));
                return;
            }
            int drained = extract(FluidPorts.segment(helper.getLevel(), helper.absolutePos(at(5))), FluidResource.of(Fluids.WATER), 5000);
            if (drained != 5000) {
                helper.fail("the port drained " + drained + " mB of 5000", at(5));
            }
        }).thenIdle(1).thenExecute(() -> {
            var contents = contentsAt(helper, 5);
            if (contents == null || contents.amount() != contents.capacity()) {
                helper.fail("the drained port's segment is " + contents + ", not full again", at(5));
            }
        }).thenSucceed();
    }

    private static void refusesLava(GameTestHelper helper) {
        creativeTank(helper);
        helper.setBlock(at(1), PipeworksRegistries.PIPE.get().defaultBlockState());
        insert(faceAt(helper, 1), FluidResource.of(Fluids.WATER), 50);
        helper.useBlock(at(0), holding(helper, new ItemStack(Items.LAVA_BUCKET)));
        extract(faceAt(helper, 1), FluidResource.of(Fluids.WATER), 50);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            var contents = contentsAt(helper, 1);
            if (contents == null || contents.fluid() != null || contents.amount() != 0) {
                helper.fail("a lava bucket on a creative tank beside water set it: " + contents, at(0));
            }
        }).thenSucceed();
    }

    private static void refusesOtherThanSet(GameTestHelper helper) {
        creativeTank(helper);
        helper.useBlock(at(0), holding(helper, new ItemStack(Items.WATER_BUCKET)));
        helper.useBlock(at(0), holding(helper, new ItemStack(Items.LAVA_BUCKET)));
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            var contents = contentsAt(helper, 0);
            if (contents == null || !"minecraft:water".equals(contents.fluid()) || contents.amount() != Pipeworks.TANK_CAPACITY) {
                helper.fail("a lava bucket changed a creative tank set to water: " + contents, at(0));
            }
        }).thenSucceed();
    }

    private static void fillsBuckets(GameTestHelper helper) {
        creativeTank(helper);
        Player player = holding(helper, new ItemStack(Items.BUCKET));
        helper.useBlock(at(0), player);
        if (!player.getMainHandItem().is(Items.BUCKET) || player.getInventory().countItem(Items.WATER_BUCKET) != 0) {
            helper.fail("an empty bucket filled from a creative tank that holds nothing", at(0));
            return;
        }
        helper.useBlock(at(0), holding(helper, new ItemStack(Items.WATER_BUCKET)));
        Player filler = holding(helper, new ItemStack(Items.BUCKET, 3));
        for (int i = 0; i < 3; i++) {
            helper.useBlock(at(0), filler);
        }
        int filled = filler.getInventory().countItem(Items.WATER_BUCKET);
        if (filled != 3 || filler.getInventory().countItem(Items.BUCKET) != 0) {
            helper.fail("three empty buckets became " + filled + " buckets of water", at(0));
            return;
        }
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            var contents = contentsAt(helper, 0);
            if (contents == null || contents.amount() != Pipeworks.TANK_CAPACITY) {
                helper.fail("filling buckets drained the creative tank: " + contents, at(0));
            }
        }).thenSucceed();
    }

    private static void survivesSaving(GameTestHelper helper) {
        creativeTank(helper);
        helper.useBlock(at(0), holding(helper, new ItemStack(Items.WATER_BUCKET)));
        var saved = FluidSegments.CODEC.encodeStart(NbtOps.INSTANCE, FluidSegments.get(helper.getLevel())).getOrThrow();
        var loaded = FluidSegments.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        if (loaded.sourceAt(helper.absolutePos(at(0))) != Fluids.WATER) {
            helper.fail("the saved creative tank came back sourcing " + loaded.sourceAt(helper.absolutePos(at(0))), at(0));
            return;
        }
        helper.succeed();
    }

    private static void breakingClears(GameTestHelper helper) {
        creativeTank(helper);
        helper.setBlock(at(1), PipeworksRegistries.PIPE.get().defaultBlockState());
        helper.useBlock(at(0), holding(helper, new ItemStack(Items.WATER_BUCKET)));
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            helper.setBlock(at(0), Blocks.AIR);
            extract(faceAt(helper, 1), FluidResource.of(Fluids.WATER), (int) Pipeworks.PIPE_CAPACITY);
        }).thenIdle(2).thenExecute(() -> {
            var contents = contentsAt(helper, 1);
            if (FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(0))) != null) {
                helper.fail("a broken creative tank still sources", at(0));
            } else if (contents == null || contents.amount() != 0) {
                helper.fail("the pipe is still being refilled: " + contents, at(1));
            }
        }).thenSucceed();
    }
}
