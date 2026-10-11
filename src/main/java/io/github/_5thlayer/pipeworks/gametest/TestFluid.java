// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.Pipeworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * A fluid with no block, for the tests to pipe, since vanilla has none: it is carried like any other
 * and sets nothing down in the world, as a Consumer's gas would. Registered only in the Library's own
 * game test run.
 */
final class TestFluid {

    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Pipeworks.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Pipeworks.MOD_ID);

    static final DeferredHolder<FluidType, FluidType> TYPE = TYPES.register("gametest_blockless", () -> new FluidType(FluidType.Properties.create()));
    static final DeferredHolder<Fluid, Blockless> FLUID = FLUIDS.register("gametest_blockless", Blockless::new);

    private TestFluid() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
    }

    /** The empty fluid's lack of a block, bucket and shape, with a type of its own so it is not empty. */
    static final class Blockless extends EmptyFluid {

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public FluidType getFluidType() {
            return TYPE.get();
        }
    }
}
