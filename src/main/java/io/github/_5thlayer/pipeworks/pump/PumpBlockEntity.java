// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.pump;

import java.util.List;

import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import io.github._5thlayer.pipeworks.api.FluidPort;
import io.github._5thlayer.pipeworks.api.FluidPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A port that fills its segment from a source block beside it. A source in
 * {@link #INFINITE_SOURCES} is never taken, and the pump adds {@link PumpRule#milliBucketsPerTick} a
 * tick as far as the segment has room. Any other source is taken whole, as a bucket would, once the
 * segment has room for the block, and the pump then waits {@link PumpRule#ticksPerBlock} (ADR 0006).
 * It takes no power.
 */
public class PumpBlockEntity extends BlockEntity implements FluidPort {

    /** The fluids whose still sources the Pump leaves in place. A pack adds its own, and ships water. */
    public static final TagKey<Fluid> INFINITE_SOURCES = TagKey.create(Registries.FLUID,
            Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "infinite_sources"));

    private int waiting;

    public PumpBlockEntity(BlockPos pos, BlockState state) {
        super(PipeworksRegistries.PUMP_ENTITY.get(), pos, state);
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

    void serverTick() {
        if (waiting > 0) {
            waiting--;
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ResourceHandler<FluidResource> segment = FluidPorts.segment(server, worldPosition);
        if (segment == null) {
            return;
        }
        FluidResource held = segment.getResource(0);
        var sources = PumpBlock.sourcesBeside(server, worldPosition);
        var chosen = PumpRule.choose(sources.stream().map(PumpBlock.Source::fluid).toList(),
                held == null || held.isEmpty() ? null : held.getFluid());
        if (chosen.isEmpty()) {
            return;
        }
        Fluid fluid = chosen.get();
        if (fluid.defaultFluidState().is(INFINITE_SOURCES)) {
            try (Transaction tx = Transaction.openRoot()) {
                segment.insert(FluidResource.of(fluid), PumpRule.milliBucketsPerTick(), tx);
                tx.commit();
            }
            return;
        }
        take(server, segment, fluid, sources);
    }

    // The block goes only when its whole bucket fits, so a block taken is never lost (ADR 0006).
    private void take(ServerLevel server, ResourceHandler<FluidResource> segment, Fluid fluid,
            List<PumpBlock.Source> sources) {
        try (Transaction tx = Transaction.openRoot()) {
            if (segment.insert(FluidResource.of(fluid), PumpRule.BUCKET_MB, tx) != PumpRule.BUCKET_MB) {
                return;
            }
            for (PumpBlock.Source found : sources) {
                BlockPos source = found.pos();
                var state = server.getBlockState(source);
                var fluidState = state.getFluidState();
                if (fluidState.isSource() && fluidState.getType() == fluid
                        && state.getBlock() instanceof BucketPickup pickup
                        && !pickup.pickupBlock(null, server, source, state).isEmpty()) {
                    tx.commit();
                    waiting = PumpRule.ticksPerBlock();
                    return;
                }
            }
        }
    }
}
