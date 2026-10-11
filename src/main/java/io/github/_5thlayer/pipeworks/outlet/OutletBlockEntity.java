// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.outlet;

import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPort;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A port that empties its segment into the world a bucket at a time: once the segment holds
 * {@link FluidType#BUCKET_VOLUME}, and the space the outlet faces is empty or replaceable and holds no
 * source block, it takes the bucket and sets one source block of the fluid there. A fluid with no
 * block stays in the pipes. It never picks fluid up, and its front is no port face (ADR 0007).
 */
public class OutletBlockEntity extends BlockEntity implements FluidPort {

    public OutletBlockEntity(BlockPos pos, BlockState state) {
        super(PipeworksRegistries.OUTLET_ENTITY.get(), pos, state);
    }

    private Direction facing() {
        return getBlockState().getValue(OutletBlock.FACING);
    }

    @Override
    public boolean connectsOn(Direction face) {
        return face != facing();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        FluidPorts.join(this);
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ResourceHandler<FluidResource> segment = FluidPorts.segment(server, worldPosition);
        if (segment == null) {
            return;
        }
        FluidResource held = segment.getResource(0);
        if (held == null || held.isEmpty()) {
            return;
        }
        BlockState source = sourceBlock(held.getFluid());
        BlockPos front = worldPosition.relative(facing());
        BlockState there = server.getBlockState(front);
        if (source == null || !canPlaceInto(there, held.getFluid())) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            if (segment.extract(held, FluidType.BUCKET_VOLUME, tx) != FluidType.BUCKET_VOLUME) {
                return;
            }
            // A replaceable block breaks with its drops, as under a bucket.
            if (!there.isAir() && !there.liquid()) {
                server.destroyBlock(front, true);
            }
            if (server.setBlock(front, source, Block.UPDATE_ALL_IMMEDIATE)) {
                tx.commit();
            }
        }
    }

    /** The still source block of {@code fluid}, or null for a fluid that has none. */
    private static @Nullable BlockState sourceBlock(Fluid fluid) {
        FluidState still = fluid.defaultFluidState();
        if (!still.isSource()) {
            return null;
        }
        BlockState block = still.createLegacyBlock();
        return block.isAir() ? null : block;
    }

    /**
     * Empty, or a block a bucket of {@code fluid} would replace, but never a source block, which
     * would be lost to the one set down.
     */
    private static boolean canPlaceInto(BlockState there, Fluid fluid) {
        return there.isAir() || (there.canBeReplaced(fluid) && !there.getFluidState().isSource());
    }
}
