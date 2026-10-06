// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.Set;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.pipeworks.Pipeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * A fluid inventory for the tests to put beside a pipe, since Pipeworks ships none: a block that is
 * no node and exposes a fluid handler on one face at a time, which a test switches on and off as
 * Craftworks' fluid connections do, with {@code invalidateCapabilities} and no block update.
 * Registered only in the Library's own game test run.
 */
final class TestInventory {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Pipeworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Pipeworks.MOD_ID);

    static final DeferredBlock<InventoryBlock> BLOCK = BLOCKS.registerBlock("gametest_inventory", InventoryBlock::new);
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InventoryEntity>> TYPE =
            BLOCK_ENTITIES.register("gametest_inventory", () -> new BlockEntityType<>(InventoryEntity::new, Set.of(BLOCK.get())));

    private TestInventory() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(TestInventory::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, TYPE.get(), InventoryEntity::handlerOn);
    }

    static final class InventoryBlock extends BaseEntityBlock {

        private static final MapCodec<InventoryBlock> CODEC = simpleCodec(InventoryBlock::new);

        InventoryBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return CODEC;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new InventoryEntity(pos, state);
        }
    }

    static final class InventoryEntity extends BlockEntity {

        private final ResourceHandler<FluidResource> handler = new FluidStacksResourceHandler(1, 1000);
        private @Nullable Direction exposedOn;

        InventoryEntity(BlockPos pos, BlockState state) {
            super(TYPE.get(), pos, state);
        }

        /** Exposes the handler on {@code face} only, or on none for null, and tells the level its capability changed. */
        void expose(@Nullable Direction face) {
            exposedOn = face;
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
            }
        }

        @Nullable ResourceHandler<FluidResource> handlerOn(@Nullable Direction side) {
            return side != null && side == exposedOn ? handler : null;
        }
    }
}
