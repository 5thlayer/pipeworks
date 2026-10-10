// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.pump;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * What the Pump draws and how fast, with no Minecraft types so a plain JVM test holds it (ADR 0006).
 */
public final class PumpRule {

    /** Factorio's offshore pump speed, per Factorio tick. */
    static final int PUMPING_SPEED = 20;

    static final int FACTORIO_TICKS_PER_SECOND = 60;

    static final int MINECRAFT_TICKS_PER_SECOND = 20;

    /** What a source block holds, in millibuckets. */
    public static final long BLOCK = 1_000;

    private PumpRule() {
    }

    /** Factorio's per-tick figure as millibuckets a second: 20 becomes 1,200. */
    public static int milliBucketsPerSecond() {
        return PUMPING_SPEED * FACTORIO_TICKS_PER_SECOND;
    }

    /** What an infinite source adds to the segment each Minecraft tick: 60 mB. */
    public static int milliBucketsPerTick() {
        return milliBucketsPerSecond() / MINECRAFT_TICKS_PER_SECOND;
    }

    /**
     * Ticks a pump waits after taking a source block, so a block a pump takes whole is still paid for
     * at the same rate: 1,000 mB at 60 mB a tick is 17 ticks, rounded up.
     */
    public static int ticksPerBlock() {
        return (int) Math.ceilDiv(BLOCK, (long) milliBucketsPerTick());
    }

    /**
     * The fluid to draw from the sources beside the pump, in the order they are asked: the first of
     * the segment's own fluid if it holds one, since another would mix, or the first source if it is
     * empty. Empty when no source qualifies.
     *
     * @param sources the fluid of each still source block beside the pump
     * @param held    the fluid the pump's segment holds, or null if it is empty
     */
    public static <F> Optional<F> choose(List<? extends F> sources, @Nullable F held) {
        for (F source : sources) {
            if (held == null || held.equals(source)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
