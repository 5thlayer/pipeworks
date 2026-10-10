// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.pipeworks.Pipeworks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Library's game tests, run by the {@code gameTestServer} Gradle run: the Minecraft-integration
 * seam, with a real player on a real server. Each test stands on the {@code gametest/platform}
 * structure, a stone floor that {@code scripts/build-gametest-structures.py} writes, and sets up
 * what it needs itself, so the setup is in the diff.
 */
public final class PipeworksGameTests {

    private static final Identifier PLATFORM = id("gametest/platform");

    /**
     * Set by the Library's own {@code gameTestServer} run. A Consumer's game test server also has
     * game tests enabled, and there the Library's test port block has no business existing.
     */
    private static final String OWN_RUN = "pipeworks.gametest";

    /** Set by the {@code client} run, which turns on {@code dev_pack/}. */
    private static final String DEV_PACK = "pipeworks.devPack";

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Pipeworks.MOD_ID);

    static {
        TEST_TYPES.register("code", () -> CodeGameTest.CODEC);
    }

    private PipeworksGameTests() {
    }

    public static void register(IEventBus modBus) {
        TEST_TYPES.register(modBus);
        if (Boolean.getBoolean(DEV_PACK)) {
            modBus.addListener(PipeworksGameTests::addDevPack);
        }
        if (!GameTestHooks.isGametestEnabled() || !Boolean.getBoolean(OWN_RUN)) {
            return;
        }
        TestPort.register(modBus);
        TestInventory.register(modBus);
        modBus.addListener(PipeworksGameTests::registerTests);
        modBus.addListener(PipeworksGameTests::addTestPack);
    }

    /**
     * The datapack in {@code src/gametest/resources}, which puts a stick in {@code pipeworks:closes_sides}
     * for the tests.
     */
    private static void addTestPack(AddPackFindersEvent event) {
        event.addPackFinders(id("gametest_pack"), PackType.SERVER_DATA, Component.literal("Pipeworks game tests"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    /**
     * The same stick for trying closed sides in the dev client, where the shipped tag is empty. In
     * {@code src/gametest/resources}, so not in the jar; only the {@code client} run sets
     * {@value #DEV_PACK}.
     */
    private static void addDevPack(AddPackFindersEvent event) {
        event.addPackFinders(id("dev_pack"), PackType.SERVER_DATA, Component.literal("Pipeworks dev tags"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var tests = new Registrar(event, environment);
        LoadTests.register(tests);
        SegmentTests.register(tests);
        CreativePipeTests.register(tests);
        TankLevelTests.register(tests);
        ArmTests.register(tests);
        ClosedSideTests.register(tests);
        PipeDismantleTests.register(tests);
        PipeStretchTests.register(tests);
        PumpTests.register(tests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, path);
    }

    /** What a test class is handed: a name, a tick budget and a body per test. */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            var id = id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(environment, PLATFORM, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
