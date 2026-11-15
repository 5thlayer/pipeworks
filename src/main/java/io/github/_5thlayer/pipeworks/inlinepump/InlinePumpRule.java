// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.inlinepump;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.pipeworks.pump.PumpRule;

/**
 * How fast the in-line pump moves fluid, which segments reach their fluid inventories, and in what
 * turn, with no Minecraft types so a plain JVM test holds it (ADR 0009).
 */
public final class InlinePumpRule {

    /** Factorio's in-line pump, 1,200 mB a second: the Pump's 60 mB a Minecraft tick. */
    public static final int DEFAULT_RATE = PumpRule.milliBucketsPerTick();

    /** What a segment does with the fluid inventories it touches, given the in-line pumps beside it. */
    public enum Role {
        /** No in-line pump serves it: fluid crosses only when an inventory moves it (ADR 0004). */
        PASSIVE,
        /** Behind a pump only: it refills from its inventories. */
        BEHIND,
        /** In front of a pump only: it empties into its inventories. */
        IN_FRONT,
        /** Behind one pump and in front of another: it passes fluid on and reaches no inventory. */
        CONDUIT
    }

    private InlinePumpRule() {
    }

    public static Role role(boolean behindAPump, boolean inFrontOfAPump) {
        if (behindAPump && inFrontOfAPump) {
            return Role.CONDUIT;
        }
        if (behindAPump) {
            return Role.BEHIND;
        }
        return inFrontOfAPump ? Role.IN_FRONT : Role.PASSIVE;
    }

    /**
     * The ticks a pump waits after moving {@code moved}, so a move above the rate, which an end that
     * takes or gives only whole buckets forces, is still paid for at {@code rate}: as ADR 0006 has the
     * Pump wait after a block taken whole. None for a move within the rate.
     */
    public static int ticksToWait(int moved, int rate) {
        return Math.max(0, Math.ceilDiv(moved, rate) - 1);
    }

    /** {@code all} starting from a different one each tick, so none is always last. */
    public static <T> List<T> inTurn(List<T> all, long tick) {
        if (all.isEmpty()) {
            return List.of();
        }
        int first = (int) Math.floorMod(tick, (long) all.size());
        List<T> turn = new ArrayList<>(all.subList(first, all.size()));
        turn.addAll(all.subList(0, first));
        return turn;
    }
}
