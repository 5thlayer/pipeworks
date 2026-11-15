// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.inlinepump;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.api.FluidPipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The in-line pump: no node, but the block between two segments that moves fluid from the one behind
 * it to the one in front, and so from the fluid inventories the one behind touches to those the one
 * in front touches (ADR 0009). It opens onto pipes on its back and front only. It faces the way the
 * player looked when placing it, and an item in {@link FluidPipes#CLOSES_SIDES} turns it round.
 */
public class InlinePumpBlock extends BaseEntityBlock implements FluidSegments.OpensToPipes {

    /** The face it pumps out of; it draws from the opposite one. */
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;

    public static final MapCodec<InlinePumpBlock> CODEC = simpleCodec(InlinePumpBlock::new);

    public InlinePumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public boolean opensOn(BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    /** Turns the pump round on a plain click with an item that closes a pipe's sides; a sneak-click is Dismantle's. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.is(FluidPipes.CLOSES_SIDES) && !player.isSecondaryUseActive()) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getOpposite()), Block.UPDATE_ALL);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InlinePumpBlockEntity(pos, state);
    }
}
