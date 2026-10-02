// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.pipeworks.api.FluidPort;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * One level's fluid segments (ADR-0110). The graph is saved with the level and never reads a chunk,
 * so a segment keeps its fluid while its blocks are unloaded and the pipes need no block entity.
 * A node's mask holds the faces it opens, and two neighbours are linked where both open the face
 * between them.
 */
public final class FluidSegments extends SavedData {

    public static final int ALL_FACES = 0b111111;

    private record NodeRecord(long node, long capacity, int mask, List<Long> links) {
        static final Codec<NodeRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(NodeRecord::node),
                Codec.LONG.fieldOf("capacity").forGetter(NodeRecord::capacity),
                Codec.INT.fieldOf("mask").forGetter(NodeRecord::mask),
                Codec.LONG.listOf().fieldOf("links").forGetter(NodeRecord::links)
        ).apply(i, NodeRecord::new));
    }

    private record HeldRecord(long node, String fluid, long amount) {
        static final Codec<HeldRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(HeldRecord::node),
                Codec.STRING.fieldOf("fluid").forGetter(HeldRecord::fluid),
                Codec.LONG.fieldOf("amount").forGetter(HeldRecord::amount)
        ).apply(i, HeldRecord::new));
    }

    public static final Codec<FluidSegments> CODEC = RecordCodecBuilder.create(i -> i.group(
            NodeRecord.CODEC.listOf().fieldOf("nodes").forGetter(FluidSegments::nodeRecords),
            HeldRecord.CODEC.listOf().fieldOf("held").forGetter(FluidSegments::heldRecords)
    ).apply(i, FluidSegments::new));

    public static final SavedDataType<FluidSegments> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "fluid_segments"), FluidSegments::new, CODEC);

    private final SegmentGraph graph;
    private final Map<Long, Integer> masks = new HashMap<>();
    private final Map<Integer, Journal> journals = new HashMap<>();

    private FluidSegments() {
        this.graph = new SegmentGraph();
    }

    private FluidSegments(List<NodeRecord> nodes, List<HeldRecord> held) {
        this.graph = SegmentGraph.restore(
                nodes.stream().map(n -> new SegmentGraph.NodeData(n.node(), n.capacity(),
                        n.links().stream().mapToLong(Long::longValue).toArray())).toList(),
                held.stream().map(h -> new SegmentGraph.SegmentData(h.node(), h.fluid(), h.amount())).toList());
        nodes.forEach(n -> masks.put(n.node(), n.mask()));
    }

    public static FluidSegments get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The mask bit of a face. */
    public static int bit(Direction face) {
        return 1 << face.ordinal();
    }

    /** Whether the block at {@code pos} opens {@code face} to a pipe, for a pipe drawing its arms. */
    public static boolean opensOn(BlockGetter level, BlockPos pos, Direction face) {
        if (level.getBlockState(pos).getBlock() instanceof SegmentBlock) {
            return true;
        }
        return level.getBlockEntity(pos) instanceof FluidPort port && port.connectsOn(face);
    }

    /** Implemented by the blocks that are nodes of a segment themselves, with no block entity. */
    public interface SegmentBlock {
        /** The millibuckets this block adds to its segment. */
        long capacity();
    }

    public boolean contains(BlockPos pos) {
        return graph.contains(pos.asLong());
    }

    /** Whether a node opening {@code mask} at {@code pos} would join segments without mixing two fluids. */
    public boolean canJoin(BlockPos pos, int mask) {
        return graph.check(neighbours(pos, mask)) instanceof SegmentGraph.Placement.Accepted;
    }

    /**
     * Joins the segments beside {@code pos}, or does nothing if it is already a node. A join that
     * would mix two fluids is refused and the node forms a segment of its own, which only a block
     * placed by something other than a player reaches (the pipes refuse the placement itself).
     *
     * @return whether it joined its neighbours
     */
    public boolean join(BlockPos pos, long capacity, int mask) {
        long node = pos.asLong();
        if (graph.contains(node)) {
            return true;
        }
        masks.put(node, mask);
        journals.clear();
        setDirty();
        if (graph.add(node, capacity, neighbours(pos, mask)) instanceof SegmentGraph.Placement.Refused) {
            graph.add(node, capacity);
            return false;
        }
        return true;
    }

    public void leave(BlockPos pos) {
        long node = pos.asLong();
        if (graph.contains(node)) {
            graph.remove(node);
            masks.remove(node);
            journals.clear();
            setDirty();
        }
    }

    /** The segment holding {@code pos} as one fluid slot, or null if the position is no node. */
    public @Nullable ResourceHandler<FluidResource> handlerAt(BlockPos pos) {
        return graph.contains(pos.asLong()) ? new Handler(pos.asLong()) : null;
    }

    public SegmentGraph.Held heldAt(BlockPos pos) {
        return graph.held(pos.asLong());
    }

    private long[] neighbours(BlockPos pos, int mask) {
        List<Long> found = new ArrayList<>();
        for (Direction face : Direction.values()) {
            if ((mask & bit(face)) == 0) {
                continue;
            }
            long other = pos.relative(face).asLong();
            Integer otherMask = masks.get(other);
            if (otherMask != null && (otherMask & bit(face.getOpposite())) != 0) {
                found.add(other);
            }
        }
        return found.stream().mapToLong(Long::longValue).toArray();
    }

    private List<NodeRecord> nodeRecords() {
        return graph.nodeData().stream().map(n -> new NodeRecord(n.node(), n.capacity(), masks.getOrDefault(n.node(), 0),
                java.util.Arrays.stream(n.links()).boxed().toList())).toList();
    }

    private List<HeldRecord> heldRecords() {
        return graph.segmentData().stream().map(s -> new HeldRecord(s.node(), s.fluid(), s.amount())).toList();
    }

    private static String key(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    private static Fluid fluid(String key) {
        Fluid fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(key));
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    /** Undoes an aborted transaction on one segment, which is what a snapshot of it is. */
    private final class Journal extends SnapshotJournal<SegmentGraph.Held> {

        private final int segment;

        Journal(int segment) {
            this.segment = segment;
        }

        @Override
        protected SegmentGraph.Held createSnapshot() {
            return graph.segment(segment);
        }

        @Override
        protected void revertToSnapshot(SegmentGraph.Held snapshot) {
            graph.setHeld(segment, snapshot.fluid(), snapshot.amount());
            setDirty();
        }
    }

    /** A segment seen from one of its nodes, as one slot of its fluid. */
    private final class Handler implements ResourceHandler<FluidResource> {

        private final long node;

        Handler(long node) {
            this.node = node;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            SegmentGraph.Held held = graph.held(node);
            return held.fluid() == null ? FluidResource.EMPTY : FluidResource.of(fluid(held.fluid()));
        }

        @Override
        public long getAmountAsLong(int index) {
            return graph.held(node).amount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return isValid(index, resource) ? graph.held(node).capacity() : 0;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            String held = graph.held(node).fluid();
            return index == 0 && plain(resource) && (held == null || held.equals(key(resource.getFluid())));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (index != 0 || !plain(resource)) {
                return 0;
            }
            String fluid = key(resource.getFluid());
            long moved = graph.insert(node, fluid, amount, true);
            if (moved > 0) {
                journal(transaction);
                graph.insert(node, fluid, moved, false);
                setDirty();
            }
            return (int) moved;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (index != 0 || !plain(resource)) {
                return 0;
            }
            String fluid = key(resource.getFluid());
            long moved = graph.extract(node, fluid, amount, true);
            if (moved > 0) {
                journal(transaction);
                graph.extract(node, fluid, moved, false);
                setDirty();
            }
            return (int) moved;
        }

        private void journal(TransactionContext transaction) {
            journals.computeIfAbsent(graph.segmentOf(node), Journal::new).updateSnapshots(transaction);
        }

        /** A fluid with data components is not one this segment keeps apart from plain ones, so it is refused. */
        private boolean plain(FluidResource resource) {
            return !resource.isEmpty() && resource.getComponentsPatch().isEmpty();
        }
    }
}
