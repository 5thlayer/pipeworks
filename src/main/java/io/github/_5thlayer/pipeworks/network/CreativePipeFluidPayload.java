// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.network;

import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.block.CreativePipeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A fluid a recipe viewer dropped on the slot of the creative pipe menu {@code containerId}, or
 * the empty fluid to clear it. A click on the slot needs no packet of ours: the server reads the
 * stack it holds on the cursor. A drop from a viewer's list has no cursor stack to read, so the
 * client names the fluid, and it takes the same path as a click.
 */
public record CreativePipeFluidPayload(int containerId, Fluid fluid) implements CustomPacketPayload {

    public static final Type<CreativePipeFluidPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "creative_pipe_fluid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreativePipeFluidPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreativePipeFluidPayload::containerId,
            ByteBufCodecs.registry(Registries.FLUID), CreativePipeFluidPayload::fluid,
            CreativePipeFluidPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreativePipeFluidPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        // Only the menu the player has open, and only while it is still valid: a packet cannot reach any other pipe.
        if (player.containerMenu instanceof CreativePipeMenu menu && menu.containerId == payload.containerId
                && menu.stillValid(player)) {
            menu.setFluid(payload.fluid);
        }
    }
}
