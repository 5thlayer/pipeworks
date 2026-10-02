// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import io.github._5thlayer.pipeworks.gametest.PipeworksGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The Library's entry point. It registers the game tests, which exist only when game tests are
 * enabled, and nothing else yet.
 */
@Mod(Pipeworks.MOD_ID)
public final class Pipeworks {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "pipeworks";

    public Pipeworks(IEventBus modBus) {
        PipeworksGameTests.register(modBus);
    }
}
