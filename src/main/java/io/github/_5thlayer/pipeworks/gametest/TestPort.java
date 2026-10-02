// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.Set;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.api.FluidPort;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A machine for the tests to fill, since Pipeworks ships none: a port that opens every face, as a
 * Consumer's block does through the {@link FluidPort} API. Registered only in the Library's own
 * game test run.
 */
final class TestPort {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Pipeworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Pipeworks.MOD_ID);

    static final DeferredBlock<PortBlock> BLOCK = BLOCKS.registerBlock("gametest_port", PortBlock::new);
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortEntity>> TYPE =
            BLOCK_ENTITIES.register("gametest_port", () -> new BlockEntityType<>(PortEntity::new, Set.of(BLOCK.get())));

    private TestPort() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    static final class PortBlock extends BaseEntityBlock {

        private static final MapCodec<PortBlock> CODEC = simpleCodec(PortBlock::new);

        PortBlock(Properties properties) {
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
            return new PortEntity(pos, state);
        }

        @Override
        protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
            FluidPorts.leave(level, pos);
        }
    }

    static final class PortEntity extends BlockEntity implements FluidPort {

        PortEntity(BlockPos pos, BlockState state) {
            super(TYPE.get(), pos, state);
        }

        @Override
        public boolean connectsOn(Direction face) {
            return true;
        }

        @Override
        public void onLoad() {
            super.onLoad();
            FluidPorts.join(this);
        }
    }
}
