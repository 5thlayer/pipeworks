// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.block;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A pipe: a node of 100 millibuckets that joins every pipe, tank and port beside it (ADR 0002).
 * It draws an arm towards each side where fluid can move: a node its segment links it to, or a
 * fluid inventory beside it. A pipe waiting outside every segment draws none (ADR 0003).
 */
public class FluidPipeBlock extends Block implements FluidSegments.SegmentBlock {

    public static final MapCodec<FluidPipeBlock> CODEC = simpleCodec(FluidPipeBlock::new);

    static final Map<Direction, BooleanProperty> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP,
            Direction.DOWN, BlockStateProperties.DOWN));

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARM_SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5),
            Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 5, 11, 11),
            Direction.EAST, Block.box(11, 5, 5, 16, 11, 11),
            Direction.UP, Block.box(5, 11, 5, 11, 16, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11)));

    public FluidPipeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty arm : ARMS.values()) {
            state = state.setValue(arm, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public long capacity() {
        return Pipeworks.PIPE_CAPACITY;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ARMS.values().forEach(builder::add);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return SegmentBlocks.refuses(context.getLevel(), context.getClickedPos(), context.getPlayer()) ? null : defaultBlockState();
    }

    /**
     * Whether {@code state} is a pipe that draws an arm on {@code side}: it is linked to the node
     * there, or a fluid inventory is (ADR 0003). Whether two nodes are linked is for the segment
     * graph to say, not the blockstate.
     */
    public static boolean drawsArm(BlockState state, Direction side) {
        return state.getBlock() instanceof FluidPipeBlock && state.getValue(ARMS.get(side));
    }

    @Override
    public boolean hasArms() {
        return true;
    }

    @Override
    public boolean armOn(BlockState state, Direction side) {
        return state.getValue(ARMS.get(side));
    }

    @Override
    public BlockState withArms(BlockState state, Predicate<Direction> arm) {
        for (Map.Entry<Direction, BooleanProperty> property : ARMS.entrySet()) {
            state = state.setValue(property.getValue(), arm.test(property.getKey()));
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> arm : ARMS.entrySet()) {
            if (state.getValue(arm.getValue())) {
                shape = Shapes.or(shape, ARM_SHAPES.get(arm.getKey()));
            }
        }
        return shape;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        SegmentBlocks.onPlace(level, pos, state, oldState, capacity());
    }

    /** A neighbour changed, so a fluid inventory may have come or gone: the segments check the sides again on the next tick. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level instanceof ServerLevel server) {
            FluidSegments.get(server).recheckArms(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        SegmentBlocks.onRemoval(level, pos);
    }
}
