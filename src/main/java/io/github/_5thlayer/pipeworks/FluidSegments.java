// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
import io.github._5thlayer.pipeworks.api.FluidPort;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
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
 * the links are not saved. The arms a pipe draws are not saved either: they are in its blockstate,
 * and the capability caches that follow the fluid inventories beside it are rebuilt as its chunk loads.
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

    private record SourceRecord(long node, String fluid) {
        static final Codec<SourceRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(SourceRecord::node),
                Codec.STRING.fieldOf("fluid").forGetter(SourceRecord::fluid)
        ).apply(i, SourceRecord::new));
    }

    public static final Codec<FluidSegments> CODEC = RecordCodecBuilder.create(i -> i.group(
            NodeRecord.CODEC.listOf().fieldOf("nodes").forGetter(FluidSegments::nodeRecords),
            ContentsRecord.CODEC.listOf().fieldOf("contents").forGetter(FluidSegments::contentsRecords),
            SourceRecord.CODEC.listOf().optionalFieldOf("sources", List.of()).forGetter(FluidSegments::sourceRecords)
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
    /** The fluid each creative pipe keeps its segment full of, by node. */
    private final Map<Long, String> sources = new HashMap<>();
    private final Map<Integer, Journal> journals = new HashMap<>();
    /** What the storage tanks show of their segments (ADR 0003). */
    private final TankSteps steps = new TankSteps();
    /** Every node by its chunk, so a chunk sent to a player is answered without a scan of the level. */
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();
    /**
     * The fluid handler of the block beside each pipe, one cache per side that is not linked to a
     * node. Held here since the level holds a cache weakly, so one stays registered only while it is
     * in this map.
     */
    private final Map<Long, Map<Direction, BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>>> caches = new HashMap<>();
    /** The sides of each pipe to check again on the next level tick, as a mask by node. */
    private final Map<Long, Integer> dirty = new HashMap<>();

    private FluidSegments() {
        this.graph = new SegmentGraph();
    }

    private FluidSegments(List<NodeRecord> nodes, List<ContentsRecord> contents, List<SourceRecord> sourceRecords) {
        for (NodeRecord node : nodes) {
            specs.put(node.node(), new Spec(node.capacity(), node.mask()));
            index(node.node());
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
        Set<String> unknownSources = new HashSet<>();
        for (SourceRecord record : sourceRecords) {
            if (fluid(record.fluid()) == null) {
                unknownSources.add(record.fluid());
            } else if (specs.containsKey(record.node())) {
                sources.put(record.node(), record.fluid());
            }
        }
        if (!unknownSources.isEmpty()) {
            LOGGER.warn("Cleared the creative pipes set to fluids this game does not have: {}", unknownSources);
        }
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
                segments.refill();
                segments.redrawDirty(level);
                segments.steps.update(level, segments.graph);
            }
        }
    }

    /** A chunk loaded: its pipes check every side again, since their caches were dropped with it or never built. */
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FluidSegments segments = level.getDataStorage().get(TYPE);
            if (segments != null) {
                for (long node : segments.nodesIn(event.getChunk().getPos())) {
                    segments.dirty.merge(node, ALL_FACES, (a, b) -> a | b);
                }
            }
        }
    }

    /** A client was just sent a chunk: tell it the fluid of the tanks in it, and set any that fell behind. */
    static void onChunkSent(ChunkWatchEvent.Sent event) {
        ServerLevel level = event.getLevel();
        FluidSegments segments = level.getDataStorage().get(TYPE);
        if (segments != null) {
            segments.steps.chunkSent(level, segments.graph, segments.nodesIn(event.getPos()), event.getPlayer());
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

        /** Whether this block draws an arm towards each side where fluid can move, as a pipe does. */
        default boolean hasArms() {
            return false;
        }

        /** Whether {@code state} draws an arm on {@code side}. */
        default boolean armOn(BlockState state, Direction side) {
            return false;
        }

        /** {@code state} with the arms {@code arm} says to draw, for a block that {@linkplain #hasArms has arms}. */
        default BlockState withArms(BlockState state, Predicate<Direction> arm) {
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
     * Whether a node opening every face at {@code pos} would link to the node on {@code side}: it would
     * join without mixing, and that node is in a segment and opens the face between them.
     */
    public boolean wouldLink(BlockPos pos, Direction side) {
        return neighbours(pos, bit(side)).length > 0 && canJoin(pos, ALL_FACES);
    }

    /**
     * Whether a pipe placed at {@code pos} would draw an arm on {@code side}: it would join without
     * mixing, and either links to the node there or has a fluid inventory there. A pipe that would
     * wait draws none.
     */
    public boolean wouldDrawArm(Level level, BlockPos pos, Direction side) {
        return canJoin(pos, ALL_FACES)
                && (neighbours(pos, bit(side)).length > 0 || isFluidInventory(level, pos.relative(side), side.getOpposite()));
    }

    /**
     * Whether the block at {@code pos} is a fluid inventory on {@code face}: loaded, no node of a
     * segment, and exposing a fluid handler there. A Pipeworks node or a {@link FluidPort} is
     * judged as a node and never as one, whatever its block registers. On the client this is a best
     * effort, since a handler may be exposed only on the server.
     */
    public static boolean isFluidInventory(Level level, BlockPos pos, Direction face) {
        return level.isLoaded(pos) && !isNode(level, pos) && level.getCapability(Capabilities.Fluid.BLOCK, pos, face) != null;
    }

    private static boolean isNode(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof SegmentBlock || level.getBlockEntity(pos) instanceof FluidPort;
    }

    /** Checks every side of the pipe at {@code pos} again on the next level tick. */
    public void recheckArms(BlockPos pos) {
        if (specs.containsKey(pos.asLong())) {
            dirty.merge(pos.asLong(), ALL_FACES, (a, b) -> a | b);
        }
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
            index(node);
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
        unindex(node);
        sources.remove(node);
        caches.remove(node);
        dirty.remove(node);
        setDirty();
        if (!waiting.remove(node)) {
            leaving.add(node);
            settle(level);
        }
    }

    /**
     * The fluid the creative pipe at {@code pos} keeps its segment full of, or null if it has none set
     * or its segment holds another fluid.
     */
    public @Nullable Fluid sourceAt(BlockPos pos) {
        long node = pos.asLong();
        String key = sources.get(node);
        if (key == null) {
            return null;
        }
        String contained = joined(node) ? graph.contents(node).fluid() : null;
        return contained != null && !contained.equals(key) ? null : fluid(key);
    }

    /**
     * Sets the fluid the creative pipe at {@code pos} keeps its segment full of, and fills the segment
     * with it. Refused for a pipe in no segment, for one already set to another fluid, and for a
     * segment that holds another fluid or has another creative pipe set to one: a pipe set to a fluid
     * is changed by clearing it first.
     *
     * @return whether the pipe now sources {@code fluid}
     */
    public boolean source(BlockPos pos, Fluid fluid) {
        long node = pos.asLong();
        if (!joined(node)) {
            return false;
        }
        // A flowing fluid is its source's: a segment holds water, never flowing water.
        String key = key(fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid);
        String contained = graph.contents(node).fluid();
        if (contained != null && !contained.equals(key)) {
            return false;
        }
        int segment = graph.segmentOf(node);
        for (Map.Entry<Long, String> other : sources.entrySet()) {
            if (!other.getValue().equals(key) && joined(other.getKey()) && graph.segmentOf(other.getKey()) == segment) {
                return false;
            }
        }
        if (sources.put(node, key) == null) {
            setDirty();
        }
        refill();
        return true;
    }

    /**
     * Stops the creative pipe at {@code pos} keeping its segment full: what the segment holds stays
     * and drains as any fluid does. Does nothing for a pipe that has no fluid set.
     */
    public void clear(BlockPos pos) {
        if (sources.remove(pos.asLong()) != null) {
            setDirty();
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

    /** Whether {@code pos} is a node that waits outside every segment (ADR 0003). */
    public boolean waits(BlockPos pos) {
        return waiting.contains(pos.asLong());
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

    /**
     * Fills each creative pipe's segment with its fluid. Like {@link #settle} it changes nothing while
     * a transaction is open, since an abort restores the segment from a snapshot taken before.
     */
    private void refill() {
        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            return;
        }
        for (Map.Entry<Long, String> source : sources.entrySet()) {
            long node = source.getKey();
            if (joined(node) && graph.insert(node, source.getValue(), Long.MAX_VALUE, false) > 0) {
                setDirty();
                steps.touch(graph.segmentOf(node));
            }
        }
    }

    private void changed(ServerLevel level, long node) {
        journals.clear();
        setDirty();
        steps.forgetAll();
        touchSteps(node);
        BlockPos pos = BlockPos.of(node);
        level.invalidateCapabilities(pos);
        redraw(level, pos);
        for (Direction face : Direction.values()) {
            redraw(level, pos.relative(face));
            touchSteps(pos.relative(face).asLong());
        }
    }

    /** Marks the segment of {@code node}, if it is in one, for its tanks to show its fill. */
    /** The nodes in a chunk, joined or waiting. */
    Set<Long> nodesIn(ChunkPos chunk) {
        return byChunk.getOrDefault(chunk.pack(), Set.of());
    }

    private void index(long node) {
        byChunk.computeIfAbsent(ChunkPos.containing(BlockPos.of(node)).pack(), chunk -> new HashSet<>()).add(node);
    }

    private void unindex(long node) {
        long chunk = ChunkPos.containing(BlockPos.of(node)).pack();
        Set<Long> nodes = byChunk.get(chunk);
        if (nodes != null && nodes.remove(node) && nodes.isEmpty()) {
            byChunk.remove(chunk);
        }
    }

    private void touchSteps(long node) {
        if (graph.contains(node)) {
            steps.touch(graph.segmentOf(node));
        }
    }

    private void redraw(ServerLevel level, BlockPos pos) {
        redraw(level, pos, ALL_FACES);
    }

    /** Redraws the arms of the pipe at {@code pos} on the sides in {@code sides}, a mask. The rest stay as they are. */
    private void redraw(ServerLevel level, BlockPos pos, int sides) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SegmentBlock block && block.hasArms()) {
            BlockState drawn = block.withArms(state, face -> drawsArm(level, pos, face, (sides & bit(face)) != 0, block.armOn(state, face)));
            if (drawn != state) {
                level.setBlock(pos, drawn, Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Whether the pipe at {@code pos} draws an arm on {@code face}: it is linked to the node there,
     * or the block there is a fluid inventory. A waiting pipe draws none, and a neighbour that is a
     * node is judged only by the link. Where the neighbour is not loaded its handler is unknown, so
     * the arm stays {@code current}.
     */
    private boolean drawsArm(ServerLevel level, BlockPos pos, Direction face, boolean check, boolean current) {
        if (!check) {
            return current;
        }
        long node = pos.asLong();
        BlockPos beside = pos.relative(face);
        if (graph.linked(node, beside.asLong())) {
            uncache(node, face);
            return true;
        }
        boolean loaded = level.isLoaded(beside);
        if (!specs.containsKey(node) || waiting.contains(node) || loaded && isNode(level, beside)) {
            uncache(node, face);
            return false;
        }
        // Queried even where the neighbour is not loaded: a cache notifies only once queried.
        boolean handler = cache(level, node, face).getCapability() != null;
        return loaded ? handler : current;
    }

    private BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> cache(ServerLevel level, long node, Direction face) {
        return caches.computeIfAbsent(node, n -> new EnumMap<>(Direction.class)).computeIfAbsent(face,
                f -> BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, BlockPos.of(node).relative(f), f.getOpposite(),
                        () -> caches.containsKey(node),
                        // Only marks the side: a listener may not query the cache or touch the level.
                        () -> dirty.merge(node, bit(f), (a, b) -> a | b)));
    }

    private void uncache(long node, Direction face) {
        Map<Direction, ?> sides = caches.get(node);
        if (sides != null && sides.remove(face) != null && sides.isEmpty()) {
            caches.remove(node);
        }
    }

    /** Redraws the sides marked dirty since the last tick, which queries their caches again and so re-arms them. */
    private void redrawDirty(ServerLevel level) {
        if (dirty.isEmpty()) {
            return;
        }
        Map<Long, Integer> sides = new HashMap<>(dirty);
        dirty.clear();
        sides.forEach((node, mask) -> redraw(level, BlockPos.of(node), mask));
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

    private List<SourceRecord> sourceRecords() {
        return sources.entrySet().stream()
                .map(e -> new SourceRecord(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(SourceRecord::node))
                .toList();
    }

    private static String key(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    /** The registered fluid of this id, or null for an id that is malformed, unregistered or empty. */
    public static @Nullable Fluid fluid(String key) {
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
            steps.touch(segment);
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
                steps.touch(graph.segmentOf(node));
            }
            return (int) moved;
        }

        /** A segment does not keep a fluid with data components apart from the plain one (ADR 0003). */
        private boolean plain(FluidResource resource) {
            return !resource.isEmpty() && resource.getComponentsPatch().isEmpty();
        }
    }
}
