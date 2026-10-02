// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * One level's fluid segments (ADR 0002), saved with the level (ADR 0003). A node's mask holds the
 * faces it opens, and two joined neighbours are linked where both open the face between them, so
 * the links are not saved.
 */
public final class FluidSegments extends SavedData {

    public static final int ALL_FACES = 0b111111;

    private static final Logger LOGGER = LogUtils.getLogger();

    private record NodeRecord(long node, long capacity, int mask, boolean waiting) {
        static final Codec<NodeRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(NodeRecord::node),
                Codec.LONG.fieldOf("capacity").forGetter(NodeRecord::capacity),
                Codec.INT.fieldOf("mask").forGetter(NodeRecord::mask),
                Codec.BOOL.optionalFieldOf("waiting", false).forGetter(NodeRecord::waiting)
        ).apply(i, NodeRecord::new));
    }

    private record ContentsRecord(long node, String fluid, long amount) {
        static final Codec<ContentsRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(ContentsRecord::node),
                Codec.STRING.fieldOf("fluid").forGetter(ContentsRecord::fluid),
                Codec.LONG.fieldOf("amount").forGetter(ContentsRecord::amount)
        ).apply(i, ContentsRecord::new));
    }

    public static final Codec<FluidSegments> CODEC = RecordCodecBuilder.create(i -> i.group(
            NodeRecord.CODEC.listOf().fieldOf("nodes").forGetter(FluidSegments::nodeRecords),
            ContentsRecord.CODEC.listOf().fieldOf("contents").forGetter(FluidSegments::contentsRecords)
    ).apply(i, FluidSegments::new));

    public static final SavedDataType<FluidSegments> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "fluid_segments"), FluidSegments::new, CODEC);

    private record Spec(long capacity, int mask) {
    }

    private final SegmentGraph graph;
    /** Every node in the level, joined or waiting. One not waiting is in the graph. */
    private final Map<Long, Spec> specs = new HashMap<>();
    private final Set<Long> waiting = new LinkedHashSet<>();
    /** Removed from the level while a transaction was open, so still in the graph (ADR 0003). */
    private final Set<Long> leaving = new LinkedHashSet<>();
    private final Map<Integer, Journal> journals = new HashMap<>();

    private FluidSegments() {
        this.graph = new SegmentGraph();
    }

    private FluidSegments(List<NodeRecord> nodes, List<ContentsRecord> contents) {
        for (NodeRecord node : nodes) {
            specs.put(node.node(), new Spec(node.capacity(), node.mask()));
            if (node.waiting()) {
                waiting.add(node.node());
            }
        }
        List<SegmentGraph.NodeData> joined = nodes.stream().filter(n -> !n.waiting())
                .map(n -> new SegmentGraph.NodeData(n.node(), n.capacity(), neighbours(BlockPos.of(n.node()), n.mask())))
                .toList();
        List<SegmentGraph.SegmentData> known = new ArrayList<>();
        Set<String> unknown = new HashSet<>();
        for (ContentsRecord record : contents) {
            if (fluid(record.fluid()) != null) {
                known.add(new SegmentGraph.SegmentData(record.node(), record.fluid(), record.amount()));
            } else {
                unknown.add(record.fluid());
            }
        }
        if (!unknown.isEmpty()) {
            LOGGER.warn("Emptied the fluid segments holding fluids this game does not have: {}", unknown);
        }
        this.graph = SegmentGraph.restore(joined, known);
    }

    /** The level's segments, with what an open transaction held back now applied (ADR 0003). */
    public static FluidSegments get(ServerLevel level) {
        FluidSegments segments = level.getDataStorage().computeIfAbsent(TYPE);
        segments.settle(level);
        return segments;
    }

    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FluidSegments segments = level.getDataStorage().get(TYPE);
            if (segments != null) {
                segments.settle(level);
            }
        }
    }

    /** The mask bit of a face. */
    public static int bit(Direction face) {
        return 1 << face.ordinal();
    }

    /** Implemented by the blocks that are nodes of a segment themselves, with no block entity. */
    public interface SegmentBlock {
        /** The millibuckets this block adds to its segment. */
        long capacity();

        /** This block's state given the faces it is linked on, for a pipe drawing its arms. */
        default BlockState withLinks(BlockState state, Predicate<Direction> linked) {
            return state;
        }
    }

    /** Whether {@code pos} is a node in a segment. */
    public boolean contains(BlockPos pos) {
        return joined(pos.asLong());
    }

    /** Whether a node opening {@code mask} at {@code pos} would join segments without mixing two fluids. */
    public boolean canJoin(BlockPos pos, int mask) {
        return graph.check(neighbours(pos, mask)) instanceof SegmentGraph.Placement.Accepted;
    }

    /**
     * Places a node at {@code pos}, or does nothing if one is there. A node that would mix two fluids
     * waits in no segment and joins once it no longer would (ADR 0003).
     *
     * @return whether it is in a segment
     */
    public boolean join(ServerLevel level, BlockPos pos, long capacity, int mask) {
        long node = pos.asLong();
        if (specs.putIfAbsent(node, new Spec(capacity, mask)) == null) {
            waiting.add(node);
            setDirty();
            settle(level);
        }
        return joined(node);
    }

    public void leave(ServerLevel level, BlockPos pos) {
        long node = pos.asLong();
        if (specs.remove(node) == null) {
            return;
        }
        setDirty();
        if (!waiting.remove(node)) {
            leaving.add(node);
            settle(level);
        }
    }

    /** The segment holding {@code pos} as one fluid slot, or null if the position is in none. */
    public @Nullable ResourceHandler<FluidResource> handlerAt(BlockPos pos) {
        return joined(pos.asLong()) ? new Handler(pos.asLong()) : null;
    }

    /** What the segment holding {@code pos} holds, or null if the position is in none. */
    public SegmentGraph.@Nullable Contents contentsAt(BlockPos pos) {
        return joined(pos.asLong()) ? graph.contents(pos.asLong()) : null;
    }

    private boolean joined(long node) {
        return specs.containsKey(node) && !waiting.contains(node);
    }

    /**
     * Applies the removals and joins held back while a transaction was open, so that an abort
     * reverts segments that still exist, and retries the waiting nodes (ADR 0003).
     */
    private void settle(ServerLevel level) {
        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            return;
        }
        for (long node : List.copyOf(leaving)) {
            graph.remove(node);
            changed(level, node);
        }
        leaving.clear();
        for (long node : List.copyOf(waiting)) {
            Spec spec = specs.get(node);
            if (graph.add(node, spec.capacity(), neighbours(BlockPos.of(node), spec.mask()))
                    instanceof SegmentGraph.Placement.Accepted) {
                waiting.remove(node);
                changed(level, node);
            }
        }
    }

    private void changed(ServerLevel level, long node) {
        journals.clear();
        setDirty();
        BlockPos pos = BlockPos.of(node);
        level.invalidateCapabilities(pos);
        redraw(level, pos);
        for (Direction face : Direction.values()) {
            redraw(level, pos.relative(face));
        }
    }

    private void redraw(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SegmentBlock block) {
            BlockState linked = block.withLinks(state, face -> graph.linked(pos.asLong(), pos.relative(face).asLong()));
            if (linked != state) {
                level.setBlock(pos, linked, Block.UPDATE_ALL);
            }
        }
    }

    /** The joined nodes beside {@code pos} that open the face {@code mask} opens towards them. */
    private long[] neighbours(BlockPos pos, int mask) {
        List<Long> found = new ArrayList<>();
        for (Direction face : Direction.values()) {
            if ((mask & bit(face)) == 0) {
                continue;
            }
            long other = pos.relative(face).asLong();
            Spec spec = specs.get(other);
            if (spec != null && !waiting.contains(other) && (spec.mask() & bit(face.getOpposite())) != 0) {
                found.add(other);
            }
        }
        return found.stream().mapToLong(Long::longValue).toArray();
    }

    private List<NodeRecord> nodeRecords() {
        return specs.entrySet().stream()
                .map(e -> new NodeRecord(e.getKey(), e.getValue().capacity(), e.getValue().mask(), waiting.contains(e.getKey())))
                .sorted(Comparator.comparingLong(NodeRecord::node))
                .toList();
    }

    private List<ContentsRecord> contentsRecords() {
        return graph.segmentData().stream().map(s -> new ContentsRecord(s.node(), s.fluid(), s.amount())).toList();
    }

    private static String key(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    /** The registered fluid of this id, or null for an id that is malformed, unregistered or empty. */
    private static @Nullable Fluid fluid(String key) {
        Identifier id = Identifier.tryParse(key);
        Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        return fluid == Fluids.EMPTY ? null : fluid;
    }

    /**
     * Undoes an aborted transaction on one segment, which outlives the transaction because
     * {@link #settle} changes no segment while one is open (ADR 0003).
     */
    private final class Journal extends SnapshotJournal<SegmentGraph.Contents> {

        private final int segment;

        Journal(int segment) {
            this.segment = segment;
        }

        @Override
        protected SegmentGraph.Contents createSnapshot() {
            return graph.segment(segment);
        }

        @Override
        protected void revertToSnapshot(SegmentGraph.Contents snapshot) {
            graph.setContents(segment, snapshot.fluid(), snapshot.amount());
            setDirty();
        }
    }

    private interface Move {
        long apply(long node, String fluid, long amount, boolean simulate);
    }

    /**
     * A segment seen from one of its nodes, as one slot of its fluid. Once the node leaves it holds
     * nothing and takes nothing, since a capability cache may still hand it out.
     */
    private final class Handler implements ResourceHandler<FluidResource> {

        private final long node;

        Handler(long node) {
            this.node = node;
        }

        private SegmentGraph.@Nullable Contents contents() {
            return joined(node) ? graph.contents(node) : null;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            SegmentGraph.Contents contents = contents();
            Fluid fluid = contents == null || contents.fluid() == null ? null : fluid(contents.fluid());
            return fluid == null ? FluidResource.EMPTY : FluidResource.of(fluid);
        }

        @Override
        public long getAmountAsLong(int index) {
            SegmentGraph.Contents contents = contents();
            return contents == null ? 0 : contents.amount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return isValid(index, resource) ? graph.contents(node).capacity() : 0;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            SegmentGraph.Contents contents = contents();
            return contents != null && index == 0 && plain(resource)
                    && (contents.fluid() == null || contents.fluid().equals(key(resource.getFluid())));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return move(graph::insert, index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return move(graph::extract, index, resource, amount, transaction);
        }

        private int move(Move move, int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (index != 0 || !plain(resource) || !joined(node)) {
                return 0;
            }
            String fluid = key(resource.getFluid());
            long moved = move.apply(node, fluid, amount, true);
            if (moved > 0) {
                journals.computeIfAbsent(graph.segmentOf(node), Journal::new).updateSnapshots(transaction);
                move.apply(node, fluid, moved, false);
                setDirty();
            }
            return (int) moved;
        }

        /** A segment does not keep a fluid with data components apart from the plain one (ADR 0003). */
        private boolean plain(FluidResource resource) {
            return !resource.isEmpty() && resource.getComponentsPatch().isEmpty();
        }
    }
}
