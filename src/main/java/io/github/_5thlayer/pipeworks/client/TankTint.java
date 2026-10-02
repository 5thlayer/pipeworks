// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.TankFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Colours the fluid in a storage tank's glass (ADR 0003): the tank's model draws it white, at the
 * height its level says, and this tints it with the fluid the server last said the tank holds.
 *
 * <p>Called only on the client, from {@code PipeworksClient}.
 */
public final class TankTint {

    /** Each fluid's colour, read on every rebuild of a section holding a tank. */
    private static final Map<Fluid, Integer> COLOURS = new ConcurrentHashMap<>();

    private static final BlockTintSource FLUID = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            Fluid fluid = TankFluids.at(pos);
            return fluid == null ? -1 : colorOf(fluid);
        }
    };

    private TankTint() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(TankTint::registerTintSources);
        NeoForge.EVENT_BUS.addListener(TankTint::onLevelUnload);
    }

    private static void registerTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(FLUID), PipeworksRegistries.STORAGE_TANK.get());
    }

    /** What a client has been told of the tanks must not outlive its world. */
    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            TankFluids.clear();
            COLOURS.clear();
        }
    }

    /**
     * The fluid's own tint where it has one, as water does; otherwise the average of its still
     * texture, so lava is orange though its model asks for no tint. The fluid layer is a white texture.
     */
    private static int colorOf(Fluid fluid) {
        return COLOURS.computeIfAbsent(fluid, TankTint::computeColor);
    }

    private static int computeColor(Fluid fluid) {
        FluidState state = fluid.defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(state);
        FluidTintSource tint = model.fluidTintSource();
        if (tint != null) {
            return ARGB.opaque(tint.color(state));
        }
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        int width = sprite.contents().width();
        int height = sprite.contents().height();
        long red = 0;
        long green = 0;
        long blue = 0;
        long seen = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = sprite.getPixelRGBA(0, x, y);
                if (ARGB.alpha(pixel) > 0) {
                    red += ARGB.red(pixel);
                    green += ARGB.green(pixel);
                    blue += ARGB.blue(pixel);
                    seen++;
                }
            }
        }
        return seen == 0 ? -1 : ARGB.color((int) (red / seen), (int) (green / seen), (int) (blue / seen));
    }
}
