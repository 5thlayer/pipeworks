// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import io.github._5thlayer.pipeworks.block.CreativePipeMenu;
import io.github._5thlayer.pipeworks.block.FluidPipeBlock;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The creative pipe, the one block a player can fill a segment from alone, set through its menu as a
 * click on the screen's slot would. Each test lays its nodes in a row at z 4, the creative pipe at
 * x 0, which {@code at(x)} indexes.
 */
final class CreativePipeTests {

    private static final int Z = 4;
    private static final int PORT_JOINS = 2;

    private CreativePipeTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("a_click_with_a_bucket_of_water_sets_a_creative_pipe_to_fill_its_segment_every_tick", 20, CreativePipeTests::fillsSegment);
        tests.test("a_creative_pipe_adds_a_pipes_100_mb_and_links_like_a_pipe", 20, CreativePipeTests::isAPipe);
        tests.test("a_click_changes_neither_the_cursor_stack_nor_the_inventory", 20, CreativePipeTests::takesNothing);
        tests.test("a_click_with_an_item_that_holds_no_fluid_changes_nothing", 20, CreativePipeTests::ignoresOtherItems);
        tests.test("the_menu_shows_the_fluid_as_its_bucket", 20, CreativePipeTests::showsBucket);
        tests.test("a_click_with_an_empty_cursor_clears_a_creative_pipe", 20, CreativePipeTests::clears);
        tests.test("a_creative_pipe_set_by_a_viewers_drop_takes_the_same_path_as_a_click", 20, CreativePipeTests::dropsLikeClicks);
        tests.test("a_flowing_fluid_sets_a_creative_pipe_to_its_source", 20, CreativePipeTests::flowingIsSource);
        tests.test("a_bucket_of_lava_is_refused_by_a_creative_pipe_whose_segment_holds_water", 20, CreativePipeTests::refusesLava);
        tests.test("a_creative_pipe_set_to_water_refuses_lava_until_it_is_cleared", 20, CreativePipeTests::refusesOtherThanSet);
        tests.test("a_creative_pipe_refuses_a_fluid_another_in_its_drained_segment_sources", 20, CreativePipeTests::refusesOtherSource);
        tests.test("a_creative_pipes_fluid_survives_a_save_and_load", 20, CreativePipeTests::survivesSaving);
        tests.test("breaking_a_creative_pipe_stops_it_sourcing", 20, CreativePipeTests::breakingClears);
        tests.test("a_menu_of_a_broken_creative_pipe_sets_nothing", 20, CreativePipeTests::brokenPipeSetsNothing);
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 1, Z);
    }

    private static void creativePipe(GameTestHelper helper) {
        helper.setBlock(at(0), PipeworksRegistries.CREATIVE_PIPE.get().defaultBlockState());
    }

    /** The menu of the creative pipe at x, opened by a player whose cursor holds {@code carried}. */
    private static CreativePipeMenu menuAt(GameTestHelper helper, int x, ItemStack carried) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CreativePipeMenu menu = new CreativePipeMenu(1, player.getInventory(), helper.absolutePos(at(x)));
        menu.setCarried(carried);
        return menu;
    }

    /** A click on the fluid slot, as the screen sends it, with {@code carried} on the cursor. */
    private static CreativePipeMenu click(GameTestHelper helper, int x, ItemStack carried) {
        CreativePipeMenu menu = menuAt(helper, x, carried);
        click(menu, helper);
        return menu;
    }

    private static void click(CreativePipeMenu menu, GameTestHelper helper) {
        menu.clicked(CreativePipeMenu.FLUID_SLOT, 0, ContainerInput.PICKUP, helper.makeMockPlayer(GameType.SURVIVAL));
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
        creativePipe(helper);
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(at(x), PipeworksRegistries.PIPE.get().defaultBlockState());
        }
        helper.setBlock(at(4), PipeworksRegistries.STORAGE_TANK.get().defaultBlockState());
        helper.setBlock(at(5), TestPort.BLOCK.get().defaultBlockState());
        helper.startSequence().thenIdle(PORT_JOINS).thenExecute(() -> {
            click(helper, 0, new ItemStack(Items.WATER_BUCKET));
        }).thenIdle(1).thenExecute(() -> {
            long capacity = 4 * Pipeworks.PIPE_CAPACITY + Pipeworks.TANK_CAPACITY;
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

    private static void isAPipe(GameTestHelper helper) {
        creativePipe(helper);
        helper.setBlock(at(1), PipeworksRegistries.PIPE.get().defaultBlockState());
        click(helper, 0, new ItemStack(Items.WATER_BUCKET));
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            var contents = contentsAt(helper, 0);
            long capacity = 2 * Pipeworks.PIPE_CAPACITY;
            if (contents == null || contents.capacity() != capacity || contents.amount() != capacity) {
                helper.fail("a creative pipe and a pipe hold " + contents + ", not " + capacity + " mB", at(0));
                return;
            }
            if (!FluidPipeBlock.isLinked(helper.getBlockState(at(0)), Direction.EAST)
                    || !FluidPipeBlock.isLinked(helper.getBlockState(at(1)), Direction.WEST)) {
                helper.fail("a creative pipe and a pipe draw no arm towards each other", at(0));
                return;
            }
        }).thenSucceed();
    }

    private static void takesNothing(GameTestHelper helper) {
        creativePipe(helper);
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        CreativePipeMenu menu = click(helper, 0, bucket);
        if (menu.getCarried() != bucket || !bucket.is(Items.WATER_BUCKET) || bucket.getCount() != 1) {
            helper.fail("the cursor stack changed: " + menu.getCarried(), at(0));
            return;
        }
        helper.succeed();
    }

    private static void ignoresOtherItems(GameTestHelper helper) {
        creativePipe(helper);
        click(helper, 0, new ItemStack(Items.WATER_BUCKET));
        click(helper, 0, new ItemStack(Items.STICK));
        click(helper, 0, new ItemStack(Items.BUCKET));
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            var contents = contentsAt(helper, 0);
            if (FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(0))) != Fluids.WATER
                    || contents == null || contents.amount() != Pipeworks.PIPE_CAPACITY) {
                helper.fail("an item with no fluid changed a creative pipe set to water: " + contents, at(0));
            }
        }).thenSucceed();
    }

    private static void showsBucket(GameTestHelper helper) {
        creativePipe(helper);
        CreativePipeMenu menu = click(helper, 0, new ItemStack(Items.LAVA_BUCKET));
        menu.broadcastChanges();
        if (!menu.shown().is(Items.LAVA_BUCKET) || !menu.getSlot(CreativePipeMenu.FLUID_SLOT).getItem().is(Items.LAVA_BUCKET)) {
            helper.fail("the slot shows " + menu.shown() + ", not a bucket of lava", at(0));
            return;
        }
        if (!menuAt(helper, 0, ItemStack.EMPTY).shown().is(Items.LAVA_BUCKET)) {
            helper.fail("a menu opened later does not show the fluid", at(0));
            return;
        }
        helper.succeed();
    }

    private static void clears(GameTestHelper helper) {
        creativePipe(helper);
        helper.setBlock(at(1), PipeworksRegistries.PIPE.get().defaultBlockState());
        click(helper, 0, new ItemStack(Items.WATER_BUCKET));
        CreativePipeMenu menu = click(helper, 0, ItemStack.EMPTY);
        menu.broadcastChanges();
        if (!menu.shown().isEmpty() || FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(0))) != null) {
            helper.fail("an empty cursor did not clear the creative pipe: " + menu.shown(), at(0));
            return;
        }
        extract(faceAt(helper, 1), FluidResource.of(Fluids.WATER), Integer.MAX_VALUE);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            var contents = contentsAt(helper, 1);
            if (contents == null || contents.amount() != 0) {
                helper.fail("a cleared creative pipe still refills: " + contents, at(1));
            }
        }).thenSucceed();
    }

    private static void dropsLikeClicks(GameTestHelper helper) {
        creativePipe(helper);
        CreativePipeMenu menu = menuAt(helper, 0, ItemStack.EMPTY);
        menu.setFluid(Fluids.LAVA);
        if (FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(0))) != Fluids.LAVA) {
            helper.fail("a dropped fluid did not set the creative pipe", at(0));
            return;
        }
        menu.setFluid(Fluids.EMPTY);
        if (FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(0))) != null) {
            helper.fail("a dropped empty fluid did not clear the creative pipe", at(0));
            return;
        }
        helper.succeed();
    }

    private static void flowingIsSource(GameTestHelper helper) {
        creativePipe(helper);
        menuAt(helper, 0, ItemStack.EMPTY).setFluid(Fluids.FLOWING_WATER);
        Fluid set = FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(0)));
        if (set != Fluids.WATER) {
            helper.fail("flowing water set the creative pipe to " + set + ", not water", at(0));
            return;
        }
        helper.succeed();
    }

    private static void refusesLava(GameTestHelper helper) {
        creativePipe(helper);
        helper.setBlock(at(1), PipeworksRegistries.PIPE.get().defaultBlockState());
        insert(faceAt(helper, 1), FluidResource.of(Fluids.WATER), 50);
        click(helper, 0, new ItemStack(Items.LAVA_BUCKET));
        extract(faceAt(helper, 1), FluidResource.of(Fluids.WATER), 50);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            var contents = contentsAt(helper, 1);
            if (contents == null || contents.fluid() != null || contents.amount() != 0) {
                helper.fail("a bucket of lava on a creative pipe beside water set it: " + contents, at(0));
            }
        }).thenSucceed();
    }

    private static void refusesOtherThanSet(GameTestHelper helper) {
        creativePipe(helper);
        click(helper, 0, new ItemStack(Items.WATER_BUCKET));
        click(helper, 0, new ItemStack(Items.LAVA_BUCKET));
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            var contents = contentsAt(helper, 0);
            if (contents == null || !"minecraft:water".equals(contents.fluid()) || contents.amount() != Pipeworks.PIPE_CAPACITY) {
                helper.fail("a bucket of lava changed a creative pipe set to water: " + contents, at(0));
                return;
            }
            click(helper, 0, ItemStack.EMPTY);
            extract(faceAt(helper, 0), FluidResource.of(Fluids.WATER), Integer.MAX_VALUE);
            click(helper, 0, new ItemStack(Items.LAVA_BUCKET));
            if (FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(0))) != Fluids.LAVA) {
                helper.fail("a cleared and drained creative pipe did not take lava", at(0));
            }
        }).thenSucceed();
    }

    private static void refusesOtherSource(GameTestHelper helper) {
        creativePipe(helper);
        helper.setBlock(at(1), PipeworksRegistries.PIPE.get().defaultBlockState());
        helper.setBlock(at(2), PipeworksRegistries.CREATIVE_PIPE.get().defaultBlockState());
        click(helper, 0, new ItemStack(Items.WATER_BUCKET));
        extract(faceAt(helper, 1), FluidResource.of(Fluids.WATER), Integer.MAX_VALUE);
        click(helper, 2, new ItemStack(Items.LAVA_BUCKET));
        if (FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(2))) != null) {
            helper.fail("a creative pipe took lava while another in its drained segment sources water", at(2));
            return;
        }
        helper.succeed();
    }

    private static void survivesSaving(GameTestHelper helper) {
        creativePipe(helper);
        click(helper, 0, new ItemStack(Items.WATER_BUCKET));
        var saved = FluidSegments.CODEC.encodeStart(NbtOps.INSTANCE, FluidSegments.get(helper.getLevel())).getOrThrow();
        var loaded = FluidSegments.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        if (loaded.sourceAt(helper.absolutePos(at(0))) != Fluids.WATER) {
            helper.fail("the saved creative pipe came back sourcing " + loaded.sourceAt(helper.absolutePos(at(0))), at(0));
            return;
        }
        var contents = loaded.contentsAt(helper.absolutePos(at(0)));
        if (contents == null || !"minecraft:water".equals(contents.fluid()) || contents.amount() != Pipeworks.PIPE_CAPACITY) {
            helper.fail("the saved creative pipe's segment came back as " + contents, at(0));
            return;
        }
        helper.succeed();
    }

    private static void breakingClears(GameTestHelper helper) {
        creativePipe(helper);
        helper.setBlock(at(1), PipeworksRegistries.PIPE.get().defaultBlockState());
        click(helper, 0, new ItemStack(Items.WATER_BUCKET));
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            helper.setBlock(at(0), Blocks.AIR);
            extract(faceAt(helper, 1), FluidResource.of(Fluids.WATER), (int) Pipeworks.PIPE_CAPACITY);
        }).thenIdle(2).thenExecute(() -> {
            var contents = contentsAt(helper, 1);
            if (FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(0))) != null) {
                helper.fail("a broken creative pipe still sources", at(0));
            } else if (contents == null || contents.amount() != 0) {
                helper.fail("the pipe is still being refilled: " + contents, at(1));
            }
        }).thenSucceed();
    }

    private static void brokenPipeSetsNothing(GameTestHelper helper) {
        creativePipe(helper);
        CreativePipeMenu menu = menuAt(helper, 0, new ItemStack(Items.WATER_BUCKET));
        helper.setBlock(at(0), PipeworksRegistries.PIPE.get().defaultBlockState());
        click(menu, helper);
        if (FluidSegments.get(helper.getLevel()).sourceAt(helper.absolutePos(at(0))) != null) {
            helper.fail("a menu opened on a creative pipe set a pipe that replaced it", at(0));
            return;
        }
        helper.succeed();
    }
}
