// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** The Pump's rate from Factorio's figure, and which source beside it the pump draws (ADR 0006). */
class PumpRuleTest {

    @Test
    void factoriosTwentyPerTickIsTwelveHundredAMillibucketsASecond() {
        assertEquals(1_200, PumpRule.milliBucketsPerSecond());
    }

    @Test
    void aMinecraftTickSpendsSixtyOfIt() {
        assertEquals(60, PumpRule.milliBucketsPerTick());
    }

    @Test
    void theTwoTickRatesAreNotInterchangeable() {
        // Taking Factorio's 60 a second for Minecraft's 20 would run the pump at a third of its rate.
        assertEquals(3, PumpRule.milliBucketsPerSecond() / (PumpRule.MINECRAFT_TICKS_PER_SECOND * PumpRule.PUMPING_SPEED));
    }

    @Test
    void aBlockTakenWholeWaitsSeventeenTicks() {
        assertEquals(17, PumpRule.ticksPerBlock());
    }

    @Test
    void anEmptySegmentTakesTheFirstSource() {
        assertEquals(Optional.of("lava"), PumpRule.choose(List.of("lava", "water"), null));
    }

    @Test
    void aSegmentHoldingAFluidTakesTheFirstSourceOfThatFluid() {
        assertEquals(Optional.of("water"), PumpRule.choose(List.of("lava", "water"), "water"));
    }

    @Test
    void aSegmentIsNeverGivenAFluidItDoesNotHold() {
        assertEquals(Optional.empty(), PumpRule.choose(List.of("lava"), "water"));
    }

    @Test
    void noSourceTakesNothing() {
        assertEquals(Optional.empty(), PumpRule.choose(List.<String>of(), null));
    }
}
