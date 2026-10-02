// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.compat;

import java.text.NumberFormat;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.api.FluidPort;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a node's segment holds, on the HUD: its fluid, amount and capacity, or that the node waits.
 * Segments are the server's (ADR 0003), so the numbers are asked for.
 *
 * <p>For any node, not a block of this Library's: a pipe or a tank, but also a Consumer's port or
 * whatever else {@link FluidSegments#contentsAt} knows. Jade finds this class by its own annotation
 * and nothing else here refers to it, so the jar is a compile-time dependency only and the Library
 * loads without Jade.
 */
@WailaPlugin
public class PipeworksJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "segment");

    private static final String FLUID = "SegmentFluid";
    private static final String AMOUNT = "SegmentAmount";
    private static final String CAPACITY = "SegmentCapacity";
    private static final String WAITING = "SegmentWaiting";

    /** Only a node has a segment, and the client can tell a node's block from another's, if not whether it waits. */
    private static boolean mayBeNode(BlockAccessor accessor) {
        return accessor.getBlock() instanceof FluidSegments.SegmentBlock || accessor.getBlockEntity() instanceof FluidPort;
    }

    private static final IServerDataProvider<BlockAccessor> DATA = new IServerDataProvider<>() {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            // Read without creating the level's segments or settling them, as a HUD query should.
            FluidSegments segments = level.getDataStorage().get(FluidSegments.TYPE);
            if (segments == null) {
                return;
            }
            SegmentGraph.Contents contents = segments.contentsAt(accessor.getPosition());
            if (contents != null) {
                if (contents.fluid() != null) {
                    tag.putString(FLUID, contents.fluid());
                }
                tag.putLong(AMOUNT, contents.amount());
                tag.putLong(CAPACITY, contents.capacity());
            } else if (segments.waits(accessor.getPosition())) {
                tag.putBoolean(WAITING, true);
            }
        }

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            return mayBeNode(accessor);
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    };

    private static final IBlockComponentProvider TOOLTIP = new IBlockComponentProvider() {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.getBooleanOr(WAITING, false)) {
                tooltip.add(Component.translatable("tooltip.pipeworks.jade.waiting"));
            } else if (data.contains(CAPACITY)) {
                NumberFormat numbers = NumberFormat.getIntegerInstance();
                String amount = numbers.format(data.getLongOr(AMOUNT, 0L));
                String capacity = numbers.format(data.getLongOr(CAPACITY, 0L));
                tooltip.add(data.contains(FLUID)
                        ? Component.translatable("tooltip.pipeworks.jade.contents", fluidName(data.getStringOr(FLUID, "")), amount, capacity)
                        : Component.translatable("tooltip.pipeworks.jade.empty", amount, capacity));
            }
        }

        /** The fluid's own name, or its id for one this client does not have. */
        private Component fluidName(String id) {
            Identifier key = Identifier.tryParse(id);
            Fluid fluid = key == null ? null : BuiltInRegistries.FLUID.getOptional(key).orElse(null);
            return fluid == null ? Component.literal(id) : fluid.getFluidType().getDescription();
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    };

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DATA, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TOOLTIP, Block.class);
    }
}
