// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.Pipeworks;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.ItemAccessFluidHandler;

/**
 * Another mod's portable tank, for the tests to fill and empty, since vanilla has only the bucket:
 * an item of 4,000 mB holding any part of it, exposed through {@code Capabilities.Fluid.ITEM} as a
 * Consumer's would be. Registered only in the Module's own game test run.
 */
final class TestCanister {

    static final int CAPACITY = 4_000;

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Pipeworks.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Pipeworks.MOD_ID);

    static final DeferredItem<Item> ITEM = ITEMS.registerSimpleItem("gametest_canister", properties -> properties.stacksTo(1));
    static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> CONTENT =
            COMPONENTS.registerComponentType("gametest_canister_content",
                    builder -> builder.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    private TestCanister() {
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(TestCanister::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new ItemAccessFluidHandler(access, CONTENT.get(), CAPACITY),
                ITEM.get());
    }

    /** A canister holding {@code amount} mB of {@code fluid}. */
    static ItemStack holding(Fluid fluid, int amount) {
        ItemStack stack = new ItemStack(ITEM.get());
        stack.set(CONTENT.get(), SimpleFluidContent.copyOf(new FluidStack(fluid, amount)));
        return stack;
    }

    /** What {@code stack} holds, empty for a canister that holds nothing. */
    static FluidStack content(ItemStack stack) {
        return stack.getOrDefault(CONTENT.get(), SimpleFluidContent.EMPTY).copy();
    }
}
