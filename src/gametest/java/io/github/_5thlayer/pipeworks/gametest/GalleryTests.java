// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.List;
import java.util.function.BiConsumer;

import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Layout;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * The Gallery pack (libworks ADR 0010), through libworks' {@link GalleryCheck}: offered and off, its
 * build running every command without failing, and each scene's key block where it stands. The scenes
 * stand in rows of three, each row 16 blocks north of the last, which keeps them near enough to where
 * the gallery is run for their pumps to tick. Beyond the build, the Pump scene fills its tank and leaves
 * its water while taking its lava, the Outlet scene sets its water down, and a scene's sign, clicked,
 * builds it again. Each test turns the pack on, which reloads the server's data, and off again,
 * passing or failing, and each builds where the others do, so each runs in a batch of its own.
 */
final class GalleryTests {

    /** Three scenes a row, each row 16 blocks north of the last, as {@code gallery/build} lays them. */
    private static final Layout ROWS = new Layout(3, 16);

    /** Where the gallery is run from: the platform's south-west corner, its plots spilling north and east of it. */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    /** Where a scene's sign stands, from where the scene is run. */
    private static final BlockPos SIGN = new BlockPos(14, 0, 0);
    /** Where the Pump scene's water source stands, from where the scene is run. */
    private static final BlockPos PUMP_WATER = new BlockPos(6, 0, -6);
    /** Where the Pump scene's water Pump fills its storage tank, from where the scene is run. */
    private static final BlockPos PUMP_WATER_TANK = new BlockPos(11, 0, -6);
    /** Where the Pump scene's lava source stands, from where the scene is run. */
    private static final BlockPos PUMP_LAVA = new BlockPos(22, 0, -6);
    /** Where the Outlet scene's outlet stands, from where the scene is run. */
    private static final BlockPos OUTLET = new BlockPos(15, 0, -6);
    /** Where the Outlet scene's outlet sets its water down, from where the scene is run. */
    private static final BlockPos OUTLET_FRONT = new BlockPos(16, 0, -6);

    /** How many plots a test of one scene loads: a scene run alone, from where the gallery is run, fills only the first. */
    private static final int ONE_SCENE = 1;

    private GalleryTests() {
    }

    /** The scenes in the order {@code build} lays them, each with a key block and where, from where the scene is run, its function places it. */
    private static List<Scene> scenes() {
        var tank = PipeworksRegistries.STORAGE_TANK.get();
        var pump = PipeworksRegistries.PUMP.get();
        return List.of(
                new Scene("pipes", new BlockPos(18, 0, -6), tank),
                new Scene("buckets", new BlockPos(18, 0, -6), tank),
                new Scene("one_fluid", new BlockPos(20, 0, -6), pump),
                new Scene("breaking", new BlockPos(20, 0, -6), tank),
                new Scene("tank_level", new BlockPos(14, 2, -6), tank),
                new Scene("creative_pipe", new BlockPos(8, 0, -6), PipeworksRegistries.CREATIVE_PIPE.get()),
                new Scene("pump", new BlockPos(21, 0, -6), pump),
                new Scene("outlet", OUTLET, PipeworksRegistries.OUTLET.get()),
                new Scene("dismantle", new BlockPos(6, 0, -6), PipeworksRegistries.PIPE.get()));
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.testOnItsOwn("gallery_builds_a_scene_of_each_feature", GalleryCheck.TICKS, GalleryTests::builds);
        tests.testOnItsOwn("gallery_pump_scene_leaves_its_water_and_takes_its_lava", GalleryCheck.TICKS, GalleryTests::pumpTakesLava);
        tests.testOnItsOwn("gallery_outlet_scene_sets_its_water_down", GalleryCheck.TICKS, GalleryTests::outletSetsWater);
        tests.testOnItsOwn("gallery_scenes_sign_clicked_builds_the_scene_again", GalleryCheck.TICKS, GalleryTests::signReplays);
    }

    private static void builds(GameTestHelper helper) {
        GalleryCheck.builds(helper, Pipeworks.MOD_ID, ORIGIN, ROWS, scenes());
    }

    /** The Pump scene: one Pump fills its tank and leaves its water source in place, the other takes its lava source whole. */
    private static void pumpTakesLava(GameTestHelper helper) {
        var level = helper.getLevel();
        var water = at(helper, PUMP_WATER);
        var tank = at(helper, PUMP_WATER_TANK);
        var lava = at(helper, PUMP_LAVA);
        withPack(helper, (source, steps) -> steps
                .thenExecute(() -> GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Pipeworks.MOD_ID, "pump")))
                .thenWaitUntil(() -> {
                    helper.assertTrue(level.getFluidState(lava).isEmpty(), "the Pump has not taken its lava source yet");
                    var drawn = FluidPorts.segment(level, tank);
                    helper.assertTrue(drawn != null && drawn.getAmountAsLong(0) > 0, "the water Pump has drawn nothing into its tank yet");
                    helper.assertTrue(level.getFluidState(water).isSourceOfType(Fluids.WATER), "the water Pump took its source");
                }));
    }

    /** The Outlet scene: its Pump fills the run, and the Outlet sets a water source down in its basin. */
    private static void outletSetsWater(GameTestHelper helper) {
        var front = at(helper, OUTLET_FRONT);
        withPack(helper, (source, steps) -> steps
                .thenExecute(() -> GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Pipeworks.MOD_ID, "outlet")))
                .thenWaitUntil(() -> {
                    var fluid = helper.getLevel().getFluidState(front);
                    helper.assertTrue(fluid.isSource() && fluid.is(Fluids.WATER), "the Outlet has set no water down in its basin yet");
                }));
    }

    /** A scene's sign, clicked, builds the scene again where it stands, leaving nothing loose: here the Outlet, taken away. */
    private static void signReplays(GameTestHelper helper) {
        var outlet = at(helper, OUTLET);
        var sign = at(helper, SIGN);
        withPack(helper, (source, steps) -> steps.thenExecute(() -> {
            GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Pipeworks.MOD_ID, "outlet"));
            helper.getLevel().removeBlock(outlet, false);
            helper.assertTrue(helper.getLevel().getBlockEntity(sign) instanceof SignBlockEntity entity
                            && entity.executeClickCommandsIfPresent(helper.getLevel(), source.getPlayer(), sign, true),
                    "the scene's sign runs no command when clicked");
            helper.assertTrue(helper.getLevel().getBlockState(outlet).is(PipeworksRegistries.OUTLET.get()),
                    "clicking the sign did not build the Outlet again where it stood");
            var plot = GalleryCheck.plot(helper.absolutePos(ORIGIN));
            var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, AABB.of(plot));
            helper.assertTrue(loose.isEmpty(), "building the scene again left items loose: " + loose.stream().map(ItemEntity::getItem).toList());
            helper.assertTrue(helper.getLevel().getBlockState(sign).is(Blocks.OAK_SIGN), "building the scene again left no sign");
        }));
    }

    /** A test of the scene run on its own from where the gallery is run, on the first plot, in libworks' frame: passing once the pack is off again. */
    private static void withPack(GameTestHelper helper, BiConsumer<CommandSourceStack, GameTestSequence> steps) {
        GalleryCheck.withPack(helper, Pipeworks.MOD_ID, ORIGIN, ROWS, ONE_SCENE, steps).thenSucceed();
    }

    /** {@code offset}, from where the gallery is run, absolute. */
    private static BlockPos at(GameTestHelper helper, BlockPos offset) {
        return helper.absolutePos(ORIGIN).offset(offset);
    }
}
