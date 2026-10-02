// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.block;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.Nullable;

/**
 * A storage tank: a node of 25,000 millibuckets that opens every face. It is a plain block, since
 * its fluid is its segment's and the segment is saved with the level. Its {@link #LEVEL} is the
 * segment's fill, which the server sets and which is all the client knows of it (ADR 0003).
 */
public class StorageTankBlock extends Block implements FluidSegments.SegmentBlock {

    public static final MapCodec<StorageTankBlock> CODEC = simpleCodec(StorageTankBlock::new);

    /** How full its segment is, 0 to 15: {@code SegmentGraph.step}. Drawn as the height of the fluid in the glass. */
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;

    public StorageTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public long capacity() {
        return Pipeworks.TANK_CAPACITY;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return SegmentBlocks.refuses(context.getLevel(), context.getClickedPos(), context.getPlayer()) ? null : defaultBlockState();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        SegmentBlocks.onPlace(level, pos, state, oldState, capacity());
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        SegmentBlocks.onRemoval(level, pos);
    }
}
