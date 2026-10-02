// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The fluid a storage tank is full of, server to client (ADR 0003). The blockstate says how high it
 * stands; this says what it is, for the colour. An empty {@code fluid} says the tank holds none.
 * Sent to the clients tracking the tank when its segment's step or fluid changes, and to a player
 * when a chunk reaches them.
 */
public record TankFluidPayload(BlockPos pos, String fluid) implements CustomPacketPayload {

    public static final Type<TankFluidPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "tank_fluid"));

    static final StreamCodec<ByteBuf, TankFluidPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TankFluidPayload::pos,
            ByteBufCodecs.STRING_UTF8, TankFluidPayload::fluid,
            TankFluidPayload::new);

    /** Bumped when the payload's shape changes, so a client on the old shape is refused rather than misread. */
    private static final String VERSION = "1";

    @Override
    public Type<TankFluidPayload> type() {
        return TYPE;
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToClient(TYPE, STREAM_CODEC, TankFluidPayload::handle);
    }

    private static void handle(TankFluidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TankFluids.accept(context.player().level(), payload.pos(), payload.fluid()));
    }
}
