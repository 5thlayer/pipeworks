// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.compat;

import java.util.List;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.api.FluidPort;
import io.github._5thlayer.pipeworks.segment.SegmentGraph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * What a node's segment holds, on the HUD: Jade's own fluid bar for the segment's fluid, amount and
 * capacity, or a line saying the node waits. Segments are the server's (ADR 0003), so both are asked for.
 *
 * <p>For any node, not a block of this Library's: a pipe or a tank, but also a Consumer's port or
 * whatever else {@link FluidSegments#contentsAt} knows. Jade finds this class by its own annotation
 * and nothing else here refers to it, so the jar is a compile-time dependency only and the Library
 * loads without Jade.
 */
@WailaPlugin
public class PipeworksJadePlugin implements IWailaPlugin {

    private static final Identifier SEGMENT = Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "segment");
    private static final Identifier WAITING = Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "waiting");

    private static final String WAITING_KEY = "SegmentWaiting";

    /** Only a node has a segment, and the client can tell a node's block from another's, if not whether it waits. */
    private static boolean mayBeNode(Accessor<?> accessor) {
        return accessor instanceof BlockAccessor block
                && (block.getBlock() instanceof FluidSegments.SegmentBlock || block.getBlockEntity() instanceof FluidPort);
    }

    /** The level's segments, read without creating or settling them, as a HUD query should. */
    private static @Nullable FluidSegments segments(Accessor<?> accessor) {
        return accessor.getLevel() instanceof ServerLevel level ? level.getDataStorage().get(FluidSegments.TYPE) : null;
    }

    private static final IServerExtensionProvider<FluidView.Data> FLUID = new IServerExtensionProvider<>() {
        @Override
        public @Nullable List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
            FluidSegments segments = segments(accessor);
            SegmentGraph.Contents contents = segments == null || !(accessor instanceof BlockAccessor block)
                    ? null : segments.contentsAt(block.getPosition());
            if (contents == null) {
                return null;
            }
            Fluid fluid = contents.fluid() == null ? null : FluidSegments.fluid(contents.fluid());
            JadeFluidObject shown = fluid == null ? JadeFluidObject.empty() : JadeFluidObject.of(fluid, contents.amount());
            return List.of(new ViewGroup<>(List.of(new FluidView.Data(shown, contents.capacity()))));
        }

        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return mayBeNode(accessor);
        }

        @Override
        public Identifier getUid() {
            return SEGMENT;
        }
    };

    private static final IClientExtensionProvider<FluidView.Data, FluidView> FLUID_CLIENT = new IClientExtensionProvider<>() {
        @Override
        public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<FluidView.Data>> groups) {
            return ClientViewGroup.map(groups, FluidView::readDefault, null);
        }

        @Override
        public Identifier getUid() {
            return SEGMENT;
        }
    };

    private static final IServerDataProvider<BlockAccessor> WAITING_DATA = new IServerDataProvider<>() {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            FluidSegments segments = segments(accessor);
            if (segments != null && segments.waits(accessor.getPosition())) {
                tag.putBoolean(WAITING_KEY, true);
            }
        }

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            return mayBeNode(accessor);
        }

        @Override
        public Identifier getUid() {
            return WAITING;
        }
    };

    private static final IBlockComponentProvider WAITING_LINE = new IBlockComponentProvider() {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getServerData().getBooleanOr(WAITING_KEY, false)) {
                tooltip.add(Component.translatable("tooltip.pipeworks.jade.waiting"));
            }
        }

        @Override
        public Identifier getUid() {
            return WAITING;
        }
    };

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerFluidStorage(FLUID, Block.class);
        registration.registerBlockDataProvider(WAITING_DATA, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerFluidStorageClient(FLUID_CLIENT);
        registration.registerBlockComponent(WAITING_LINE, Block.class);
    }
}
