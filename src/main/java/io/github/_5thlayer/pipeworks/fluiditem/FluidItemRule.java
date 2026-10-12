// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.fluiditem;

import org.jspecify.annotations.Nullable;

/**
 * What a fluid item used on a pipe or tank moves between itself and the segment, as
 * 5thlayer/factoryworks ADR-0121 has it, with no Minecraft types so a plain JVM test holds it. A
 * full item empties whole or not at all; any other fills from the segment's fluid, and never takes
 * another. Neither ever adds a second fluid to a segment.
 */
public final class FluidItemRule {

    private FluidItemRule() {
    }

    /**
     * What an item or a segment holds and can hold: a fluid or null for none, its amount, and its
     * capacity. An empty item's capacity is for the fluid it would take.
     */
    public record Box<F>(@Nullable F fluid, long amount, long capacity) {

        long room() {
            return capacity - amount;
        }
    }

    /** Which way fluid moves and how much: from the item into the segment, or out of it. */
    public record Move(Way way, long amount) {

        public static final Move NONE = new Move(Way.NONE, 0);

        public static Move empty(long amount) {
            return new Move(Way.EMPTY, amount);
        }

        public static Move fill(long amount) {
            return new Move(Way.FILL, amount);
        }
    }

    /** Which way fluid moves between a fluid item and the segment it is used on. */
    public enum Way {
        /** Nothing moves. */
        NONE,
        /** The item empties into the segment. */
        EMPTY,
        /** The item fills from the segment. */
        FILL
    }

    /**
     * What moves when {@code item} is used on a node of {@code segment}. An item holding its whole
     * capacity empties all of it, if the segment holds its fluid or none and has room for every unit,
     * and otherwise nothing moves. Any other item, empty or part-full, takes as much of the
     * segment's fluid as it has room for, but nothing from an empty segment or one of another fluid.
     */
    public static <F> Move decide(Box<F> item, Box<F> segment) {
        if (item.amount() > 0 && item.room() <= 0) {
            boolean sameOrNone = segment.fluid() == null || segment.fluid().equals(item.fluid());
            return sameOrNone && segment.room() >= item.amount() ? Move.empty(item.amount()) : Move.NONE;
        }
        boolean takes = segment.fluid() != null && (item.fluid() == null || item.fluid().equals(segment.fluid()));
        long taken = takes ? Math.min(item.room(), segment.amount()) : 0;
        return taken > 0 ? Move.fill(taken) : Move.NONE;
    }
}
