// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.block;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.pipeworks.FluidSegments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A pipe that keeps its segment full of one fluid, the dev tool a segment is checked with: place
 * it, place pipes beside it, set its fluid in its screen. It is a pipe in every way but its refill:
 * the same 100 millibuckets, shape, arms and linking. Using it opens the screen, with no bucket
 * shortcut on the block. Breaking it stops the refill, and the fluid is saved with the level, in
 * {@link FluidSegments}, since like the pipe it is a plain block.
 *
 * <p>It has no recipe, which keeps it out of survival, as Wireworks' creative pole does.
 */
public class CreativePipeBlock extends FluidPipeBlock {

    public static final MapCodec<CreativePipeBlock> CODEC = simpleCodec(CreativePipeBlock::new);

    public CreativePipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // The server opens the menu, which brings the client's screen.
        if (player instanceof ServerPlayer server) {
            server.openMenu(menuProvider(pos), data -> data.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    private static SimpleMenuProvider menuProvider(BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, who) -> new CreativePipeMenu(id, inventory, pos),
                Component.translatable("block.pipeworks.creative_pipe"));
    }
}
