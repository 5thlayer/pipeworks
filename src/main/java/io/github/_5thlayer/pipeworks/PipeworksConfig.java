// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import io.github._5thlayer.pipeworks.inlinepump.InlinePumpRule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config, {@code pipeworks-server.toml} in the game's {@code config} directory: the pipe's and
 * the tank's capacity, which a pack sets (ADR 0003), and the in-line pump's rate (ADR 0009). A node takes
 * the capacity when it joins and keeps the one it was saved with, so a change reaches only the pipes and
 * tanks placed after it; the rate is read every tick.
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

    public static final ModConfigSpec.IntValue INLINE_PUMP_RATE = BUILDER
            .comment("The millibuckets an in-line pump moves a tick, Factorio's 60 (1,200 a second) by default. Read every",
                    "tick, so a change reaches the pumps already placed (ADR 0009).")
            .defineInRange("inlinePumpRate", InlinePumpRule.DEFAULT_RATE, 1, Integer.MAX_VALUE);

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

    /** The millibuckets an in-line pump moves this tick, the default until the server config loads. */
    public static int inlinePumpRate() {
        return SPEC.isLoaded() ? INLINE_PUMP_RATE.get() : InlinePumpRule.DEFAULT_RATE;
    }
}
