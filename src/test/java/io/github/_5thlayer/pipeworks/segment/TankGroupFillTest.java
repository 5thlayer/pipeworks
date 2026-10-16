// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.segment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** How a tank group shares its segment's fill out to its tanks, from the bottom up (ADR 0008). */
class TankGroupFillTest {

    private static final long TANK = 25_000;

    private static int[] steps(long amount, long capacity, int... heights) {
        return TankGroupFill.steps(heights, TankGroupFill.fifteenths(amount, capacity, heights.length));
    }

    @Test
    void aTankAloneShowsItsSegmentsStep() {
        assertArrayEquals(new int[] {7}, steps(12_550, 25_100, 0));
        assertArrayEquals(new int[] {15}, steps(25_100, 25_100, 0));
        assertArrayEquals(new int[] {0}, steps(0, 25_100, 0));
    }

    @Test
    void aHalfFullColumnOfThreeFillsFromTheBottom() {
        assertArrayEquals(new int[] {15, 7, 0}, steps(3 * TANK / 2, 3 * TANK, 0, 1, 2));
    }

    @Test
    void theOrderOfTheTanksDoesNotMatter() {
        assertArrayEquals(new int[] {0, 15, 7}, steps(3 * TANK / 2, 3 * TANK, 2, 0, 1));
    }

    @Test
    void aSegmentHoldingAnyShowsAtLeastOneInTheBottomLayerOnly() {
        assertArrayEquals(new int[] {1, 0, 0}, steps(1, 3 * TANK, 0, 1, 2));
        assertArrayEquals(new int[] {1, 1, 0}, steps(1, 3 * TANK, 0, 0, 1));
    }

    @Test
    void aWideLayerFillsBeforeTheTankOnIt() {
        // A 2 by 2 floor and one tank on it: four fifths full is the floor full and the top empty.
        assertArrayEquals(new int[] {15, 15, 15, 15, 0}, steps(4 * TANK, 5 * TANK, 0, 0, 0, 0, 1));
        assertArrayEquals(new int[] {7, 7, 7, 7, 0}, steps(2 * TANK, 5 * TANK, 0, 0, 0, 0, 1));
    }

    @Test
    void aFullSegmentShowsEveryTankFull() {
        assertArrayEquals(new int[] {15, 15, 15}, steps(3 * TANK, 3 * TANK, 0, 1, 2));
    }

    @Test
    void aGroupFillsToItsSegmentsFractionWhateverElseTheSegmentHolds() {
        // A column of two on a segment half full, whatever else is in it: its bottom tank full.
        assertArrayEquals(new int[] {15, 0}, steps(50_050, 100_100, 0, 1));
    }
}
