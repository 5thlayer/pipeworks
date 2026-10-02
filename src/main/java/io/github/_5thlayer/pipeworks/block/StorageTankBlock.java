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
import org.jspecify.annotations.Nullable;

/**
 * A storage tank: a node of 25,000 millibuckets that opens every face. It is a plain block, since
 * its fluid is its segment's and the segment is saved with the level.
 */
public class StorageTankBlock extends Block implements FluidSegments.SegmentBlock {

    public static final MapCodec<StorageTankBlock> CODEC = simpleCodec(StorageTankBlock::new);

    public StorageTankBlock(Properties properties) {
        super(properties);
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
