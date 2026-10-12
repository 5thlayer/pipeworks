// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.fluiditem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github._5thlayer.pipeworks.fluiditem.FluidItemRule.Box;
import io.github._5thlayer.pipeworks.fluiditem.FluidItemRule.Move;
import org.junit.jupiter.api.Test;

/** What a fluid item used on a node moves between itself and the segment (5thlayer/factoryworks ADR-0121). */
class FluidItemRuleTest {

    private static final Box<String> EMPTY_BUCKET = new Box<>(null, 0, 1_000);
    private static final Box<String> WATER_BUCKET = new Box<>("water", 1_000, 1_000);
    private static final Box<String> LAVA_BUCKET = new Box<>("lava", 1_000, 1_000);

    private static Box<String> segment(String fluid, long amount, long capacity) {
        return new Box<>(fluid, amount, capacity);
    }

    @Test
    void aFullBucketEmptiesIntoAnEmptySegment() {
        assertEquals(Move.empty(1_000), FluidItemRule.decide(WATER_BUCKET, segment(null, 0, 25_000)));
    }

    @Test
    void aFullBucketEmptiesIntoASegmentOfItsFluid() {
        assertEquals(Move.empty(1_000), FluidItemRule.decide(WATER_BUCKET, segment("water", 500, 25_000)));
    }

    @Test
    void aFullBucketIsRefusedByASegmentOfAnotherFluid() {
        assertEquals(Move.NONE, FluidItemRule.decide(LAVA_BUCKET, segment("water", 500, 25_000)));
    }

    @Test
    void aFullBucketOfAnyFluidEmptiesIntoADrainedSegment() {
        // A drained segment holds no fluid, so it takes any.
        assertEquals(Move.empty(1_000), FluidItemRule.decide(LAVA_BUCKET, segment(null, 0, 25_000)));
    }

    @Test
    void aFullItemEmptiesWholeOrNotAtAll() {
        assertEquals(Move.NONE, FluidItemRule.decide(WATER_BUCKET, segment("water", 24_500, 25_000)));
        assertEquals(Move.NONE, FluidItemRule.decide(new Box<>("water", 4_000, 4_000), segment(null, 0, 300)));
    }

    @Test
    void aFullItemEmptiesIntoExactlyTheRoomLeft() {
        assertEquals(Move.empty(1_000), FluidItemRule.decide(WATER_BUCKET, segment("water", 24_000, 25_000)));
    }

    @Test
    void anEmptyBucketFillsFromTheSegment() {
        assertEquals(Move.fill(1_000), FluidItemRule.decide(EMPTY_BUCKET, segment("water", 5_000, 25_000)));
    }

    @Test
    void anEmptyItemTakesWhatTheSegmentHasUpToItsRoom() {
        // A bucket then refuses the 300 mB, which only a part-filled item can take.
        assertEquals(Move.fill(300), FluidItemRule.decide(new Box<>(null, 0, 4_000), segment("water", 300, 300)));
    }

    @Test
    void anEmptyItemTakesNothingFromAnEmptySegment() {
        assertEquals(Move.NONE, FluidItemRule.decide(EMPTY_BUCKET, segment(null, 0, 25_000)));
    }

    @Test
    void aPartFullItemFillsFromASegmentOfItsFluid() {
        assertEquals(Move.fill(3_000), FluidItemRule.decide(new Box<>("water", 1_000, 4_000), segment("water", 5_000, 25_000)));
    }

    @Test
    void aPartFullItemNeverTakesAnotherFluid() {
        assertEquals(Move.NONE, FluidItemRule.decide(new Box<>("lava", 1_000, 4_000), segment("water", 5_000, 25_000)));
    }

    @Test
    void aPartFullItemDoesNotEmpty() {
        assertEquals(Move.NONE, FluidItemRule.decide(new Box<>("water", 1_000, 4_000), segment(null, 0, 25_000)));
    }

    @Test
    void anItemThatHoldsNothingAndCanHoldNothingMovesNothing() {
        assertEquals(Move.NONE, FluidItemRule.decide(new Box<>(null, 0, 0), segment("water", 5_000, 25_000)));
    }
}
