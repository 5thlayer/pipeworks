// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.pipeworks.dismantle.PipeFamily;
import io.github._5thlayer.pipeworks.gametest.PipeworksGameTests;
import io.github._5thlayer.pipeworks.network.CreativePipeFluidPayload;
import io.github._5thlayer.pipeworks.stretch.PipeLegs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Pipes, a storage tank and the fluid-port API that join machines to them, on Factorio 2.0's fluid
 * segments (ADR 0002). The Module registers no fluid: it carries whatever a Consumer puts in.
 */
@Mod(Pipeworks.MOD_ID)
public final class Pipeworks {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "pipeworks";

    /** Factorio's pipe volume, in millibuckets: the default of {@link PipeworksConfig#PIPE_CAPACITY}. */
    public static final long PIPE_CAPACITY = 100;

    /** Factorio's storage tank volume, in millibuckets: the default of {@link PipeworksConfig#TANK_CAPACITY}. */
    public static final long TANK_CAPACITY = 25_000;

    public Pipeworks(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, PipeworksConfig.SPEC);
        PipeworksRegistries.register(modBus);
        PipeFamily.register();
        PipeLegs.register();
        PipeworksGameTests.register(modBus);
        GalleryPack.register(modBus, MOD_ID);
        modBus.addListener(Pipeworks::registerPayloads);
        modBus.addListener(TankFluidPayload::register);
        NeoForge.EVENT_BUS.addListener(FluidSegments::onLevelTick);
        NeoForge.EVENT_BUS.addListener(FluidSegments::onChunkSent);
        NeoForge.EVENT_BUS.addListener(FluidSegments::onChunkLoad);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(CreativePipeFluidPayload.TYPE, CreativePipeFluidPayload.STREAM_CODEC,
                CreativePipeFluidPayload::handle);
    }
}
