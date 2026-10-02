// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.segment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import io.github._5thlayer.pipeworks.segment.SegmentGraph.Placement;
import org.junit.jupiter.api.Test;

class SegmentGraphTest {

    private static final long PIPE = 100;
    private static final long TANK = 25_000;
    private static final String WATER = "minecraft:water";
    private static final String OIL = "factoryworks:crude_oil";

    /** A line of pipes 0..length-1, each linked to the one before. */
    private static SegmentGraph line(int length) {
        SegmentGraph graph = new SegmentGraph();
        for (int i = 0; i < length; i++) {
            graph.add(i, PIPE, i - 1);
        }
        return graph;
    }

    private static long totalHeld(SegmentGraph graph, long... nodes) {
        return Arrays.stream(nodes).mapToInt(graph::segmentOf).distinct()
                .mapToLong(id -> graph.segment(id).amount()).sum();
    }

    @Test
    void aLoneNodeIsASegmentOfItsOwnCapacity() {
        SegmentGraph graph = new SegmentGraph();
        graph.add(1, PIPE);
        assertEquals(new SegmentGraph.Contents(graph.segmentOf(1), null, 0, PIPE, 1), graph.contents(1));
    }

    @Test
    void aRunOfPartsIsOneSegmentWhoseCapacityIsTheirSum() {
        SegmentGraph graph = line(5);
        graph.add(5, TANK, 4);
        assertEquals(1, graph.segmentCount());
        assertEquals(5 * PIPE + TANK, graph.contents(0).capacity());
        assertEquals(6, graph.contents(3).nodes());
        assertEquals(graph.segmentOf(0), graph.segmentOf(5));
    }

    @Test
    void aNodeJoiningTwoRunsMergesThemAndTheirFluid() {
        SegmentGraph graph = new SegmentGraph();
        graph.add(0, PIPE);
        graph.add(2, PIPE);
        graph.insert(0, WATER, 60, false);
        graph.insert(2, WATER, 30, false);
        graph.add(1, PIPE, 0, 2);
        assertEquals(1, graph.segmentCount());
        assertEquals(new SegmentGraph.Contents(graph.segmentOf(1), WATER, 90, 3 * PIPE, 3), graph.contents(1));
    }

    @Test
    void aFluidFlowsInstantlyFromOneEndOfARunToTheOther() {
        SegmentGraph graph = line(50);
        assertEquals(500, graph.insert(0, WATER, 500, false));
        assertEquals(500, graph.extract(49, WATER, 500, false));
        assertNull(graph.contents(0).fluid());
    }

    @Test
    void insertingStopsAtCapacity() {
        SegmentGraph graph = line(3);
        assertEquals(300, graph.insert(0, WATER, 1000, false));
        assertEquals(0, graph.insert(2, WATER, 1, false));
        assertEquals(300, graph.contents(1).amount());
    }

    @Test
    void aSimulatedInsertOrExtractChangesNothing() {
        SegmentGraph graph = line(2);
        assertEquals(150, graph.insert(0, WATER, 150, true));
        assertEquals(0, graph.contents(0).amount());
        graph.insert(0, WATER, 150, false);
        assertEquals(40, graph.extract(1, null, 40, true));
        assertEquals(150, graph.contents(0).amount());
    }

    @Test
    void aSegmentHoldsOneFluid() {
        SegmentGraph graph = line(2);
        graph.insert(0, WATER, 10, false);
        assertEquals(0, graph.insert(1, OIL, 10, false));
        assertEquals(0, graph.extract(1, OIL, 10, false));
        assertEquals(10, graph.contents(0).amount());
    }

    @Test
    void anEmptiedSegmentTakesAnyFluidAgain() {
        SegmentGraph graph = line(2);
        graph.insert(0, WATER, 10, false);
        graph.extract(0, null, 10, false);
        assertEquals(10, graph.insert(0, OIL, 10, false));
        assertEquals(OIL, graph.contents(1).fluid());
    }

    @Test
    void aNodeThatWouldJoinTwoFluidsIsRefusedAndChangesNothing() {
        SegmentGraph graph = new SegmentGraph();
        graph.add(0, PIPE);
        graph.add(2, PIPE);
        graph.insert(0, WATER, 10, false);
        graph.insert(2, OIL, 10, false);

        Placement refused = graph.add(1, PIPE, 0, 2);

        assertEquals(new Placement.Refused(Set.of(WATER, OIL)), refused);
        assertFalse(graph.contains(1));
        assertEquals(2, graph.segmentCount());
        assertEquals(10, graph.contents(0).amount());
        assertEquals(10, graph.contents(2).amount());
    }

    @Test
    void checkAnswersWithoutJoining() {
        SegmentGraph graph = new SegmentGraph();
        graph.add(0, PIPE);
        graph.add(2, PIPE);
        graph.insert(0, WATER, 10, false);
        graph.insert(2, OIL, 10, false);
        assertInstanceOf(Placement.Refused.class, graph.check(0, 2));
        assertInstanceOf(Placement.Accepted.class, graph.check(0));
        assertEquals(2, graph.size());
    }

    @Test
    void aFluidAndAnEmptySegmentMayJoin() {
        SegmentGraph graph = new SegmentGraph();
        graph.add(0, PIPE);
        graph.add(2, PIPE);
        graph.insert(0, WATER, 10, false);
        assertInstanceOf(Placement.Accepted.class, graph.add(1, PIPE, 0, 2));
        assertEquals(10, graph.contents(2).amount());
    }

    @Test
    void twoRunsHoldingTheSameFluidMayJoin() {
        SegmentGraph graph = new SegmentGraph();
        graph.add(0, PIPE);
        graph.add(2, PIPE);
        graph.insert(0, WATER, 10, false);
        graph.insert(2, WATER, 20, false);
        assertInstanceOf(Placement.Accepted.class, graph.add(1, PIPE, 0, 2));
        assertEquals(30, graph.contents(1).amount());
    }

    @Test
    void breakingTheMiddleOfARunSplitsItsFluidByCapacity() {
        SegmentGraph graph = line(3);
        graph.add(3, TANK, 2);
        graph.insert(0, WATER, 25_300, false);

        graph.remove(1);

        assertEquals(2, graph.segmentCount());
        assertNotEquals(graph.segmentOf(0), graph.segmentOf(2));
        // The broken pipe's 100 mB of capacity takes 100 of the 25,300.
        assertEquals(100, graph.contents(0).amount());
        assertEquals(25_100, graph.contents(2).amount());
    }

    @Test
    void aSplitConservesEveryUnit() {
        for (long amount = 0; amount <= 1000; amount += 7) {
            SegmentGraph graph = line(4);
            graph.add(4, 0, 3);
            graph.add(5, PIPE, 4);
            graph.add(6, TANK, 5);
            graph.insert(0, WATER, amount, false);
            // A port of no capacity takes no share, so every unit stays in the two runs.
            graph.remove(4);
            assertEquals(amount, totalHeld(graph, 0, 5), "amount " + amount);
        }
    }

    @Test
    void aSplitNeverOverfillsAHalf() {
        SegmentGraph graph = line(7);
        graph.insert(0, WATER, 700, false);
        graph.remove(2);
        for (int node : new int[] {0, 3}) {
            SegmentGraph.Contents contents = graph.contents(node);
            assertTrue(contents.amount() <= contents.capacity(), contents.toString());
        }
        assertEquals(200, graph.contents(0).amount());
        assertEquals(400, graph.contents(3).amount());
    }

    @Test
    void roundingGoesToTheLargestRemainder() {
        // 1 unit over three equal weights: one share gets it, the total stays 1.
        long[] shares = SegmentGraph.proportionally(1, new long[] {100, 100, 100});
        assertEquals(1, Arrays.stream(shares).sum());
        // 10 over 100 + 200: 3.33 and 6.67 round to 3 and 7.
        assertEquals(List.of(3L, 7L), Arrays.stream(SegmentGraph.proportionally(10, new long[] {100, 200}))
                .boxed().toList());
    }

    @Test
    void aSplitHalfHoldingNothingForgetsTheFluid() {
        SegmentGraph graph = line(3);
        graph.insert(0, WATER, 100, false);
        graph.remove(0);
        assertEquals(WATER, graph.contents(1).fluid());
        graph.extract(1, null, graph.contents(1).amount(), false);
        assertNull(graph.contents(1).fluid());
        assertEquals(200, graph.insert(1, OIL, 500, false));
    }

    @Test
    void removingTheLastNodeLosesAllItsFluid() {
        SegmentGraph graph = new SegmentGraph();
        graph.add(0, PIPE);
        graph.insert(0, WATER, 80, false);
        graph.remove(0);
        assertEquals(0, graph.segmentCount());
        assertFalse(graph.contains(0));
    }

    @Test
    void removingAnEndKeepsOneRun() {
        SegmentGraph graph = line(4);
        graph.insert(0, WATER, 400, false);
        graph.remove(3);
        assertEquals(1, graph.segmentCount());
        assertEquals(300, graph.contents(0).amount());
    }

    @Test
    void aLoopSurvivesLosingOneNode() {
        SegmentGraph graph = new SegmentGraph();
        graph.add(0, PIPE);
        graph.add(1, PIPE, 0);
        graph.add(2, PIPE, 1);
        graph.add(3, PIPE, 2, 0);
        graph.insert(0, WATER, 400, false);
        graph.remove(2);
        assertEquals(1, graph.segmentCount());
        assertEquals(300, graph.contents(0).amount());
    }

    @Test
    void aPortOfNoCapacityJoinsARunWithoutAddingToIt() {
        SegmentGraph graph = line(2);
        graph.add(2, 0, 1);
        assertEquals(2 * PIPE, graph.contents(2).capacity());
        assertEquals(200, graph.insert(2, WATER, 500, false));
        assertEquals(200, graph.extract(0, WATER, 500, false));
    }

    @Test
    void breakingAPortLosesNoneOfTheRunsFluid() {
        SegmentGraph graph = line(2);
        graph.add(2, 0, 1);
        graph.insert(2, WATER, 200, false);
        graph.remove(2);
        assertEquals(200, graph.contents(0).amount());
    }

    @Test
    void savingAndRestoringKeepsSegmentsAndFluid() {
        SegmentGraph graph = line(4);
        graph.add(4, TANK, 3);
        graph.add(10, PIPE);
        graph.insert(0, WATER, 1234, false);
        graph.insert(10, OIL, 42, false);

        SegmentGraph restored = SegmentGraph.restore(graph.nodeData(), graph.segmentData());

        assertEquals(graph.size(), restored.size());
        assertEquals(2, restored.segmentCount());
        assertEquals(1234, restored.contents(2).amount());
        assertEquals(WATER, restored.contents(4).fluid());
        assertEquals(4 * PIPE + TANK, restored.contents(0).capacity());
        assertEquals(OIL, restored.contents(10).fluid());
        graph.remove(1);
        restored.remove(1);
        assertEquals(graph.contents(0).amount(), restored.contents(0).amount());
        assertEquals(graph.contents(3).amount(), restored.contents(3).amount());
    }

    @Test
    void linksAreBetweenNodesThatJoinedEachOther() {
        SegmentGraph graph = line(2);
        graph.add(5, PIPE);
        assertTrue(graph.linked(0, 1));
        assertTrue(graph.linked(1, 0));
        assertFalse(graph.linked(1, 5));
        graph.remove(1);
        assertFalse(graph.linked(0, 1));
        assertFalse(graph.linked(1, 0));
    }

    @Test
    void anAddedNodeAlreadyInTheGraphIsAnError() {
        SegmentGraph graph = line(1);
        assertThrows(IllegalArgumentException.class, () -> graph.add(0, PIPE));
    }

    @Test
    void setContentsRestoresWhatATransactionChanged() {
        SegmentGraph graph = line(2);
        graph.insert(0, WATER, 50, false);
        int segment = graph.segmentOf(0);
        graph.insert(0, WATER, 100, false);
        graph.setContents(segment, WATER, 50);
        assertEquals(50, graph.contents(1).amount());
        graph.setContents(segment, null, 0);
        assertNull(graph.contents(1).fluid());
    }
}
