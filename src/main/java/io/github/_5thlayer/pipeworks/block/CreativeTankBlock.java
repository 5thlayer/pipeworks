// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.block;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.pipeworks.FluidSegments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A storage tank that keeps its segment full of one fluid, the dev tool a segment is checked with:
 * place it, place pipes beside it, put a bucket to it. Using a fluid bucket sets its fluid and the
 * bucket stays full; an empty bucket fills with that fluid, as often as it is used. Breaking it is
 * the only way to clear it, and the fluid is saved with the level, in {@link FluidSegments}, since
 * like the storage tank it is a plain block.
 *
 * <p>It has no recipe, which keeps it out of survival, as Wireworks' creative pole does.
 */
public class CreativeTankBlock extends StorageTankBlock {

    public static final MapCodec<CreativeTankBlock> CODEC = simpleCodec(CreativeTankBlock::new);

    public CreativeTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        boolean empty = stack.is(Items.BUCKET);
        if (!empty && !(stack.getItem() instanceof BucketItem bucket && bucket.content != Fluids.EMPTY)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        FluidSegments segments = FluidSegments.get(server);
        if (empty) {
            return fill(segments.sourceAt(pos), stack, player, hand);
        }
        if (!segments.source(pos, ((BucketItem) stack.getItem()).content)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.pipeworks.mixed_fluids"));
            }
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    /** An empty bucket becomes a bucket of {@code fluid}; one with no fluid, or none to give, is left empty. */
    private static InteractionResult fill(@Nullable Fluid fluid, ItemStack stack, Player player, InteractionHand hand) {
        if (fluid == null || fluid.getBucket() == Items.AIR) {
            return InteractionResult.FAIL;
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(fluid.getBucket())));
        return InteractionResult.SUCCESS;
    }
}
