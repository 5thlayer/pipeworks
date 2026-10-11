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
 * {@link FluidType#BUCKET_VOLUME}, and the space the outlet faces is empty or holds a replaceable block
 * and no source block, it takes the bucket and sets one source block of the fluid there. A fluid with no
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
        FluidResource fluid = segment.getResource(0);
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        BlockState toPlace = sourceBlock(fluid.getFluid());
        BlockPos front = worldPosition.relative(facing());
        BlockState replaced = server.getBlockState(front);
        if (toPlace == null || !canPlaceInto(replaced)) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            if (segment.extract(fluid, FluidType.BUCKET_VOLUME, tx) != FluidType.BUCKET_VOLUME
                    || !server.setBlock(front, toPlace, Block.UPDATE_ALL_IMMEDIATE)) {
                return;
            }
            // A replaceable block drops what it would if broken, once the source has taken its place.
            if (!replaced.isAir() && !replaced.liquid()) {
                Block.dropResources(replaced, server, front);
            }
            tx.commit();
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
     * Empty, or a replaceable block such as grass, snow or a flowing fluid, but never a source block,
     * which would be lost to the one set down. A bucket's wider rule, any block that is not solid,
     * would break a pipe, a torch or a rail.
     */
    private static boolean canPlaceInto(BlockState replaced) {
        return replaced.canBeReplaced() && !replaced.getFluidState().isSource();
    }
}
