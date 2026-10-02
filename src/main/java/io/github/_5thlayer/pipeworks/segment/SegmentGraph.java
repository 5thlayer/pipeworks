// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.segment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factorio 2.0's fluid model (ADR 0002): a connected run of nodes is one segment holding one
 * fluid, its capacity the sum of its nodes' and its flow instant. The graph knows nothing of
 * Minecraft: a node is a {@code long} (a packed position), a fluid a string (a registry id), an
 * amount whole millibuckets. Who is adjacent to whom is the caller's to say.
 */
public final class SegmentGraph {

    /** What {@link #check} and {@link #add} say of a node joining. */
    public sealed interface Placement {
        record Accepted() implements Placement {
        }

        /** The node would have joined segments holding these different fluids. */
        record Refused(Set<String> fluids) implements Placement {
        }
    }

    /** What a segment holds, as the caller reads it. {@code fluid} is null when it holds none. */
    public record Contents(int segment, String fluid, long amount, long capacity, int nodes) {
    }

    /** A node and its links, for saving. */
    public record NodeData(long node, long capacity, long[] links) {
    }

    /** One node of a segment and what it holds, for saving; the segment is rebuilt from the links. */
    public record SegmentData(long node, String fluid, long amount) {
    }

    private static final class Node {
        final long capacity;
        final Set<Long> links = new HashSet<>();
        Segment segment;

        Node(long capacity) {
            this.capacity = capacity;
        }
    }

    private static final class Segment {
        final int id;
        final Set<Long> nodes = new LinkedHashSet<>();
        long capacity;
        String fluid;
        long amount;

        Segment(int id) {
            this.id = id;
        }

        void set(String fluid, long amount) {
            this.amount = amount;
            this.fluid = amount > 0 ? fluid : null;
        }
    }

    private final Map<Long, Node> nodes = new HashMap<>();
    private final Map<Integer, Segment> segments = new HashMap<>();
    private int nextId = 1;

    public boolean contains(long node) {
        return nodes.containsKey(node);
    }

    public int size() {
        return nodes.size();
    }

    public int segmentCount() {
        return segments.size();
    }

    /** Whether a node linked to these neighbours may join, without joining. */
    public Placement check(long... neighbours) {
        Set<String> fluids = new HashSet<>();
        Set<Integer> seen = new HashSet<>();
        for (long neighbour : neighbours) {
            Node other = nodes.get(neighbour);
            if (other != null && seen.add(other.segment.id) && other.segment.fluid != null) {
                fluids.add(other.segment.fluid);
            }
        }
        if (fluids.size() > 1) {
            return new Placement.Refused(fluids);
        }
        return new Placement.Accepted();
    }

    /**
     * Adds a node linked to those of {@code neighbours} that exist, merging their segments. A join
     * that would mix two fluids changes nothing and is refused.
     */
    public Placement add(long node, long capacity, long... neighbours) {
        if (nodes.containsKey(node)) {
            throw new IllegalArgumentException("node " + node + " is already in the graph");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity " + capacity);
        }
        if (check(neighbours) instanceof Placement.Refused refused) {
            return refused;
        }
        Node added = new Node(capacity);
        nodes.put(node, added);
        Segment own = newSegment();
        own.nodes.add(node);
        own.capacity = capacity;
        added.segment = own;

        Segment survivor = own;
        for (long neighbour : neighbours) {
            Node other = nodes.get(neighbour);
            if (other == null || neighbour == node) {
                continue;
            }
            added.links.add(neighbour);
            other.links.add(node);
            if (other.segment != survivor) {
                survivor = merge(survivor, other.segment);
            }
        }
        return new Placement.Accepted();
    }

    /**
     * Removes a node. Its segment splits into the runs it was holding together, each taking a share
     * of the fluid in proportion to its capacity, and the node's own share is lost with it, as a
     * broken pipe's is in Factorio (ADR 0003).
     */
    public void remove(long node) {
        Node gone = nodes.remove(node);
        if (gone == null) {
            return;
        }
        Segment old = gone.segment;
        old.nodes.remove(node);
        segments.remove(old.id);
        for (long link : gone.links) {
            nodes.get(link).links.remove(node);
        }

        List<Set<Long>> runs = runs(old.nodes);
        long[] weights = new long[runs.size() + 1];
        for (int i = 0; i < runs.size(); i++) {
            for (long member : runs.get(i)) {
                weights[i] += nodes.get(member).capacity;
            }
        }
        weights[runs.size()] = gone.capacity;
        long[] shares = proportionally(old.amount, weights);

        for (int i = 0; i < runs.size(); i++) {
            Segment run = newSegment();
            run.nodes.addAll(runs.get(i));
            run.capacity = weights[i];
            run.set(old.fluid, shares[i]);
            for (long member : run.nodes) {
                nodes.get(member).segment = run;
            }
        }
    }

    public int segmentOf(long node) {
        return require(node).segment.id;
    }

    /** Whether both nodes are in the graph and linked to each other. */
    public boolean linked(long a, long b) {
        Node found = nodes.get(a);
        return found != null && found.links.contains(b);
    }

    public Contents contents(long node) {
        return view(require(node).segment);
    }

    public Contents segment(int id) {
        Segment segment = segments.get(id);
        if (segment == null) {
            throw new IllegalArgumentException("no segment " + id);
        }
        return view(segment);
    }

    /** Whether a segment of this id exists, as a merge or a split retires ids. */
    public boolean exists(int id) {
        return segments.containsKey(id);
    }

    /** The nodes of a segment, or none for a segment that no longer exists, as a merge or a split retires ids. */
    public List<Long> nodesOf(int id) {
        Segment segment = segments.get(id);
        return segment == null ? List.of() : List.copyOf(segment.nodes);
    }

    /**
     * Which of {@code steps} a segment's fill shows as: 0 for none, then rounded down but never below
     * 1 while it holds any, so only a full segment reaches the top. A tank's level property is this.
     */
    public static int step(long amount, long capacity, int steps) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        if (amount >= capacity) {
            return steps;
        }
        return Math.max(1, (int) (amount * steps / capacity));
    }

    /** Puts in what fits and returns it; a segment holding another fluid takes none. */
    public long insert(long node, String fluid, long amount, boolean simulate) {
        Segment segment = require(node).segment;
        if (amount <= 0 || (segment.fluid != null && !segment.fluid.equals(fluid))) {
            return 0;
        }
        long moved = Math.min(amount, segment.capacity - segment.amount);
        if (moved > 0 && !simulate) {
            segment.set(fluid, segment.amount + moved);
        }
        return Math.max(moved, 0);
    }

    /** Takes out up to {@code amount} of {@code fluid}, or of whatever it holds when {@code fluid} is null. */
    public long extract(long node, String fluid, long amount, boolean simulate) {
        Segment segment = require(node).segment;
        if (amount <= 0 || segment.fluid == null || (fluid != null && !fluid.equals(segment.fluid))) {
            return 0;
        }
        long moved = Math.min(amount, segment.amount);
        if (!simulate) {
            segment.set(segment.fluid, segment.amount - moved);
        }
        return moved;
    }

    /** Sets what a segment holds, for a caller undoing an aborted transaction. */
    public void setContents(int id, String fluid, long amount) {
        Segment segment = segments.get(id);
        if (segment == null || amount < 0 || amount > segment.capacity) {
            throw new IllegalArgumentException("segment " + id + " cannot hold " + amount);
        }
        segment.set(fluid, amount);
    }

    public List<NodeData> nodeData() {
        List<NodeData> out = new ArrayList<>();
        nodes.forEach((node, data) -> out.add(new NodeData(node, data.capacity,
                data.links.stream().mapToLong(Long::longValue).sorted().toArray())));
        out.sort(Comparator.comparingLong(NodeData::node));
        return out;
    }

    /** One entry per segment that holds fluid. */
    public List<SegmentData> segmentData() {
        List<SegmentData> out = new ArrayList<>();
        for (Segment segment : segments.values()) {
            if (segment.fluid != null) {
                out.add(new SegmentData(segment.nodes.iterator().next(), segment.fluid, segment.amount));
            }
        }
        out.sort(Comparator.comparingLong(SegmentData::node));
        return out;
    }

    /** Rebuilds a graph from what {@link #nodeData} and {@link #segmentData} gave. */
    public static SegmentGraph restore(List<NodeData> nodeData, List<SegmentData> segmentData) {
        SegmentGraph graph = new SegmentGraph();
        for (NodeData data : nodeData) {
            Node node = new Node(data.capacity());
            for (long link : data.links()) {
                node.links.add(link);
            }
            graph.nodes.put(data.node(), node);
        }
        Set<Long> unassigned = new LinkedHashSet<>(graph.nodes.keySet());
        while (!unassigned.isEmpty()) {
            long start = unassigned.iterator().next();
            Segment segment = graph.newSegment();
            for (long member : graph.run(start)) {
                Node node = graph.nodes.get(member);
                node.segment = segment;
                segment.nodes.add(member);
                segment.capacity += node.capacity;
                unassigned.remove(member);
            }
        }
        for (SegmentData contents : segmentData) {
            Node node = graph.nodes.get(contents.node());
            if (node != null) {
                node.segment.set(contents.fluid(), Math.min(contents.amount(), node.segment.capacity));
            }
        }
        return graph;
    }

    private Node require(long node) {
        Node found = nodes.get(node);
        if (found == null) {
            throw new IllegalArgumentException("node " + node + " is not in the graph");
        }
        return found;
    }

    private Segment newSegment() {
        Segment segment = new Segment(nextId++);
        segments.put(segment.id, segment);
        return segment;
    }

    private static Contents view(Segment segment) {
        return new Contents(segment.id, segment.fluid, segment.amount, segment.capacity, segment.nodes.size());
    }

    /** Folds the smaller into the larger, so a long run joined by one pipe is not walked again. */
    private Segment merge(Segment a, Segment b) {
        Segment into = a.nodes.size() >= b.nodes.size() ? a : b;
        Segment from = into == a ? b : a;
        for (long member : from.nodes) {
            nodes.get(member).segment = into;
        }
        into.nodes.addAll(from.nodes);
        into.capacity += from.capacity;
        if (from.fluid != null) {
            into.set(from.fluid, into.amount + from.amount);
        }
        segments.remove(from.id);
        return into;
    }

    private List<Set<Long>> runs(Set<Long> members) {
        List<Set<Long>> runs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long member : members) {
            if (!seen.contains(member)) {
                Set<Long> run = run(member);
                seen.addAll(run);
                runs.add(run);
            }
        }
        return runs;
    }

    private Set<Long> run(long start) {
        Set<Long> run = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        run.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            for (long link : nodes.get(queue.poll()).links) {
                if (run.add(link)) {
                    queue.add(link);
                }
            }
        }
        return run;
    }

    /**
     * Splits {@code amount} by {@code weights} to whole units, the shares summing to exactly
     * {@code amount}. The units rounding leaves go to the largest remainders, so no share exceeds
     * its weight while the amount fits the weights' total.
     */
    static long[] proportionally(long amount, long[] weights) {
        long total = Arrays.stream(weights).sum();
        long[] shares = new long[weights.length];
        if (amount == 0 || total == 0) {
            return shares;
        }
        long[] remainders = new long[weights.length];
        long given = 0;
        for (int i = 0; i < weights.length; i++) {
            long product = Math.multiplyExact(amount, weights[i]);
            shares[i] = product / total;
            remainders[i] = product % total;
            given += shares[i];
        }
        Integer[] order = new Integer[weights.length];
        Arrays.setAll(order, i -> i);
        Arrays.sort(order, Comparator.<Integer>comparingLong(i -> -remainders[i]).thenComparingInt(i -> i));
        for (int k = 0; k < amount - given; k++) {
            shares[order[k]]++;
        }
        return shares;
    }
}
