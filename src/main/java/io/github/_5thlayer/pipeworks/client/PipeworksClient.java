// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.client;

import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The Library's client side: the creative pipe's screen and the tanks' fluid tint. */
@Mod(value = Pipeworks.MOD_ID, dist = Dist.CLIENT)
public final class PipeworksClient {

    public PipeworksClient(IEventBus modBus) {
        modBus.addListener(PipeworksClient::registerScreens);
        TankTint.register(modBus);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PipeworksRegistries.CREATIVE_PIPE_MENU.get(), CreativePipeScreen::new);
    }
}
