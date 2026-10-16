// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.segment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * How a tank group shows its segment's fill (ADR 0008): the segment's fraction of the group's own
 * volume, from its lowest layer up. A layer holds as much as it has tanks, and every tank in a layer
 * shows the same step.
 */
public final class TankGroupFill {

    /** The steps one tank's fill is shown in. */
    public static final int STEPS = 15;

    private TankGroupFill() {
    }

    /**
     * The group's fill in fifteenths of a tank, 0 to {@code STEPS * tanks}: {@link SegmentGraph#step},
     * so never 0 while the segment holds any. Every tank's step follows from it alone, so a fill that
     * leaves it unchanged leaves every tank as it was.
     */
    public static int fifteenths(long amount, long capacity, int tanks) {
        return SegmentGraph.step(amount, capacity, STEPS * tanks);
    }

    /**
     * The step of each tank of a group, by the height of each in {@code heights}, for a group filled
     * to {@code fifteenths}.
     */
    public static int[] steps(int[] heights, int fifteenths) {
        TreeMap<Integer, Integer> layers = new TreeMap<>();
        for (int height : heights) {
            layers.merge(height, 1, Integer::sum);
        }
        Map<Integer, Integer> stepOfLayer = new HashMap<>();
        int below = 0;
        for (var layer : layers.entrySet()) {
            int tanks = layer.getValue();
            int left = fifteenths - STEPS * below;
            int step = left <= 0 ? 0 : left >= STEPS * tanks ? STEPS : Math.max(1, left / tanks);
            stepOfLayer.put(layer.getKey(), step);
            below += tanks;
        }
        return Arrays.stream(heights).map(stepOfLayer::get).toArray();
    }
}
