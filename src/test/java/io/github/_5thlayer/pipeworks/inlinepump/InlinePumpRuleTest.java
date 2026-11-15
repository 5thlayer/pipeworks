// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.inlinepump;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.github._5thlayer.pipeworks.inlinepump.InlinePumpRule.Role;
import org.junit.jupiter.api.Test;

/** The in-line pump's rate, which segments reach their fluid inventories, and in what turn (ADR 0009). */
class InlinePumpRuleTest {

    @Test
    void theDefaultRateIsFactoriosSixtyMillibucketsATick() {
        assertEquals(60, InlinePumpRule.DEFAULT_RATE);
    }

    @Test
    void aMoveWithinTheRateWaitsNoTick() {
        assertEquals(0, InlinePumpRule.ticksToWait(60, 60));
        assertEquals(0, InlinePumpRule.ticksToWait(1, 60));
    }

    @Test
    void aBucketMovedWholeIsPaidForAtTheRate() {
        // 1,000 mB at 60 a tick is 17 ticks: the tick it moved in, and 16 more.
        assertEquals(16, InlinePumpRule.ticksToWait(1_000, 60));
    }

    @Test
    void aSegmentBehindAPumpReachesItsInventoriesToRefill() {
        assertEquals(Role.BEHIND, InlinePumpRule.role(true, false));
    }

    @Test
    void aSegmentInFrontOfAPumpReachesItsInventoriesToEmpty() {
        assertEquals(Role.IN_FRONT, InlinePumpRule.role(false, true));
    }

    @Test
    void aSegmentBetweenTwoPumpsIsAConduit() {
        assertEquals(Role.CONDUIT, InlinePumpRule.role(true, true));
    }

    @Test
    void aSegmentNoPumpServesStaysPassive() {
        assertEquals(Role.PASSIVE, InlinePumpRule.role(false, false));
    }

    @Test
    void theInventoriesTakeTurnsAtGoingFirst() {
        List<String> all = List.of("a", "b", "c");
        assertEquals(List.of("a", "b", "c"), InlinePumpRule.inTurn(all, 0));
        assertEquals(List.of("b", "c", "a"), InlinePumpRule.inTurn(all, 1));
        assertEquals(List.of("c", "a", "b"), InlinePumpRule.inTurn(all, 2));
        assertEquals(List.of("a", "b", "c"), InlinePumpRule.inTurn(all, 3));
    }

    @Test
    void noInventoriesTakeNoTurns() {
        assertEquals(List.of(), InlinePumpRule.inTurn(List.<String>of(), 7));
    }
}
