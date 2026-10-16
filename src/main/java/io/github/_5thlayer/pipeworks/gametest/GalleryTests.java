// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * The Gallery pack (libworks ADR 0010), through libworks' {@link GalleryCheck}: offered and off, its
 * build running every command without failing, and each scene's key block where it stands. The scenes
 * stand in rows of three, each row 16 blocks north of the last, which keeps them near enough to where
 * the gallery is run for their pumps to tick; {@link GalleryCheck#builds} lays one long row, so the
 * test runs its parts a row at a time. Beyond the build, the Outlet scene sets its water down, and a
 * scene's sign, clicked, builds it again. Each test turns the pack on, which reloads the server's
 * data, and off again, passing or failing, so they run in a batch of their own.
 */
final class GalleryTests {

    private static final String PACK = GalleryPack.id(Pipeworks.MOD_ID);

    /** How many scenes a row holds, and how far north each row is of the last. */
    private static final int ROW = 3;
    private static final int ROW_DEPTH = 16;

    /** Where the gallery is run from: the platform's south-west corner, its plots spilling north and east of it. */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    /** Where a scene's sign stands, from where the scene is run. */
    private static final BlockPos SIGN = new BlockPos(14, 0, 0);
    /** Where the Outlet scene's outlet sets its water down, from where the scene is run. */
    private static final BlockPos OUTLET_FRONT = new BlockPos(16, 0, -6);

    private GalleryTests() {
    }

    /** The scenes in the order {@code build} lays them, each with a block it places. */
    private static List<Scene> scenes() {
        var tank = PipeworksRegistries.STORAGE_TANK.get();
        var pump = PipeworksRegistries.PUMP.get();
        return List.of(
                new Scene("pipes", new BlockPos(18, 0, -6), tank),
                new Scene("buckets", new BlockPos(18, 1, -6), tank),
                new Scene("one_fluid", new BlockPos(20, 0, -6), pump),
                new Scene("breaking", new BlockPos(20, 0, -6), tank),
                new Scene("tank_level", new BlockPos(14, 2, -6), tank),
                new Scene("creative_pipe", new BlockPos(8, 0, -6), PipeworksRegistries.CREATIVE_PIPE.get()),
                new Scene("pump", new BlockPos(21, 0, -6), pump),
                new Scene("outlet", new BlockPos(15, 0, -6), PipeworksRegistries.OUTLET.get()),
                new Scene("dismantle", new BlockPos(6, 0, -6), PipeworksRegistries.PIPE.get()));
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("gallery_builds_a_scene_of_each_feature", GalleryCheck.TICKS, GalleryTests::builds);
        tests.test("gallery_outlet_scene_sets_its_water_down", GalleryCheck.TICKS, GalleryTests::outletSetsWater);
        tests.test("gallery_scenes_sign_clicked_builds_the_scene_again", GalleryCheck.TICKS, GalleryTests::signReplays);
    }

    private static void builds(GameTestHelper helper) {
        var scenes = scenes();
        withPack(helper, scenes.size(), source -> {
            GalleryCheck.assertLoads(helper, source, Pipeworks.MOD_ID);
            for (var scene : scenes) {
                helper.assertTrue(helper.getLevel().getServer().getFunctions().get(GalleryPack.scene(Pipeworks.MOD_ID, scene.name())).isPresent(),
                        "there is no scene function " + scene.name());
            }
            GalleryCheck.assertRuns(helper, source, GalleryPack.build(Pipeworks.MOD_ID));
            for (int row = 0; row * ROW < scenes.size(); row++) {
                GalleryCheck.assertKeyBlocks(helper, rowOrigin(helper, row),
                        scenes.subList(row * ROW, Math.min(scenes.size(), (row + 1) * ROW)));
            }
        }, null);
    }

    /** The Outlet scene: its Pump fills the run, and the Outlet sets a water source down in its basin. */
    private static void outletSetsWater(GameTestHelper helper) {
        var front = helper.absolutePos(ORIGIN).offset(OUTLET_FRONT);
        withPack(helper, 1, source -> GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Pipeworks.MOD_ID, "outlet")),
                () -> {
                    var fluid = helper.getLevel().getFluidState(front);
                    helper.assertTrue(fluid.isSource() && fluid.is(Fluids.WATER), "the Outlet has set no water down in its basin yet");
                });
    }

    /** A scene's sign, clicked, builds the scene again where it stands, leaving nothing loose: here the Outlet, taken away. */
    private static void signReplays(GameTestHelper helper) {
        var outlet = helper.absolutePos(ORIGIN).offset(15, 0, -6);
        var sign = helper.absolutePos(ORIGIN).offset(SIGN);
        withPack(helper, 1, source -> {
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
        }, null);
    }

    /**
     * Turns the pack on over the loaded plots of a gallery of {@code scenes}, runs {@code body} as an
     * operator player standing where it is run, waits for {@code until} if any, then clears the plots
     * and turns the pack off, passing or failing.
     */
    private static void withPack(GameTestHelper helper, int scenes, Consumer<CommandSourceStack> body, Runnable until) {
        var server = helper.getLevel().getServer();
        GalleryCheck.assertOfferedAndOff(helper, PACK);
        var area = area(helper, scenes);
        area.intersectingChunks().forEach(chunk -> helper.getLevel().setChunkForced(chunk.x(), chunk.z(), true));
        var source = GalleryCheck.operator(helper, helper.absolutePos(ORIGIN));
        var on = GalleryCheck.select(server, PACK, true);
        helper.testInfo.addListener(new ClearAwayIfFailed(helper, area, on));
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), PACK + " is not on yet"))
                .thenWaitUntil(() -> helper.assertTrue(area.intersectingChunks().allMatch(chunk -> helper.getLevel().hasChunk(chunk.x(), chunk.z())),
                        "the gallery's plots are not loaded yet"))
                .thenExecute(() -> body.accept(source))
                .thenWaitUntil(() -> {
                    if (until != null) {
                        until.run();
                    }
                })
                .thenExecute(() -> off.set(clearAway(helper, area)))
                .thenWaitUntil(() -> helper.assertTrue(off.get() != null && off.get().isDone(), PACK + " is not off yet"))
                .thenSucceed();
    }

    /** Where row {@code row} of the gallery is run from, absolute. */
    private static BlockPos rowOrigin(GameTestHelper helper, int row) {
        return helper.absolutePos(ORIGIN).north(ROW_DEPTH * row);
    }

    /** Every plot of a gallery's first {@code scenes} scenes, their floors included. */
    private static BoundingBox area(GameTestHelper helper, int scenes) {
        int rows = Math.ceilDiv(scenes, ROW);
        return BoundingBox.encapsulatingBoxes(List.of(
                GalleryCheck.plot(helper.absolutePos(ORIGIN)),
                GalleryCheck.strip(rowOrigin(helper, rows - 1), Math.min(scenes, ROW)))).orElseThrow();
    }

    /** Air where the plots stood, their entities gone, and the pack off. The runner releases the forced chunks once the batch ends. */
    private static CompletableFuture<Void> clearAway(GameTestHelper helper, BoundingBox area) {
        StructureUtils.clearSpaceForStructure(area, helper.getLevel());
        return GalleryCheck.select(helper.getLevel().getServer(), PACK, false);
    }

    /** A failed test skips its own last steps, which would leave the pack on; it turns it off once turning it on is done. */
    private record ClearAwayIfFailed(GameTestHelper helper, BoundingBox area, CompletableFuture<Void> on) implements GameTestListener {

        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {
        }

        @Override
        public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
        }

        @Override
        public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
            var server = helper.getLevel().getServer();
            on.whenComplete((done, failed) -> server.execute(() -> clearAway(helper, area)));
        }

        @Override
        public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
        }
    }
}
