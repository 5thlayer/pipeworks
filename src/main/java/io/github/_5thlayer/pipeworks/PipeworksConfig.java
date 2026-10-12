// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config, {@code pipeworks-server.toml} in the game's {@code config} directory: the pipe's and
 * the tank's capacity, which a pack sets (ADR 0003). A node takes the capacity when it joins and
 * keeps the one it was saved with, so a change reaches only the pipes and tanks placed after it.
 */
public final class PipeworksConfig {

    /** At most a capacity a transfer of one {@code int} can fill, so a segment's sum stays far from overflow. */
    private static final long MAX_CAPACITY = Integer.MAX_VALUE;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.LongValue PIPE_CAPACITY = BUILDER
            .comment("The millibuckets a pipe adds to its segment, Factorio's 100 by default. A pipe takes it when placed,",
                    "so a change reaches only the pipes placed afterwards. The creative pipe takes it too.")
            .defineInRange("pipeCapacity", Pipeworks.PIPE_CAPACITY, 1, MAX_CAPACITY);

    public static final ModConfigSpec.LongValue TANK_CAPACITY = BUILDER
            .comment("The millibuckets a storage tank adds to its segment, Factorio's 25,000 by default. A tank takes it",
                    "when placed, so a change reaches only the tanks placed afterwards.")
            .defineInRange("tankCapacity", Pipeworks.TANK_CAPACITY, 1, MAX_CAPACITY);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PipeworksConfig() {
    }

    /** The capacity a pipe joins with, the default until the server config loads. */
    public static long pipeCapacity() {
        return SPEC.isLoaded() ? PIPE_CAPACITY.get() : Pipeworks.PIPE_CAPACITY;
    }

    /** The capacity a storage tank joins with, the default until the server config loads. */
    public static long tankCapacity() {
        return SPEC.isLoaded() ? TANK_CAPACITY.get() : Pipeworks.TANK_CAPACITY;
    }
}
