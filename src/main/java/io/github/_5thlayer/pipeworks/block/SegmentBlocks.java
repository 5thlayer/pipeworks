// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.block;

import io.github._5thlayer.pipeworks.FluidSegments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** What the pipe and the tank share: joining a segment when placed, leaving it when removed. */
final class SegmentBlocks {

    private SegmentBlocks() {
    }

    /** Whether placing here would join two fluids. Only the server can tell, so a client's guess is yes. */
    static boolean refuses(Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && !FluidSegments.get(server).canJoin(pos, FluidSegments.ALL_FACES)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.pipeworks.mixed_fluids"));
            }
            return true;
        }
        return false;
    }

    static void onPlace(Level level, BlockPos pos, BlockState state, BlockState oldState, long capacity) {
        if (level instanceof ServerLevel server && !oldState.is(state.getBlock())) {
            FluidSegments.get(server).join(server, pos, capacity, FluidSegments.ALL_FACES);
        }
    }

    static void onRemoval(ServerLevel level, BlockPos pos) {
        FluidSegments.get(level).leave(level, pos);
    }
}
