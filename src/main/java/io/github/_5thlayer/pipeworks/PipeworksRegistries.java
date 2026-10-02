// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import io.github._5thlayer.pipeworks.block.CreativePipeBlock;
import io.github._5thlayer.pipeworks.block.CreativePipeMenu;
import io.github._5thlayer.pipeworks.block.FluidPipeBlock;
import io.github._5thlayer.pipeworks.block.StorageTankBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The pipe, the storage tank and the creative pipe, their items, the creative pipe's menu and the creative tab. No fluid is registered here. */
public final class PipeworksRegistries {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Pipeworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Pipeworks.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Pipeworks.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Pipeworks.MOD_ID);

    public static final DeferredBlock<FluidPipeBlock> PIPE = BLOCKS.registerBlock("pipe", FluidPipeBlock::new,
            props -> props.strength(1.5F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<StorageTankBlock> STORAGE_TANK = BLOCKS.registerBlock("storage_tank", StorageTankBlock::new,
            props -> props.strength(2.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());
    /** Has no recipe, which keeps it out of survival: it is in the creative tab and {@code /give}. */
    public static final DeferredBlock<CreativePipeBlock> CREATIVE_PIPE = BLOCKS.registerBlock("creative_pipe", CreativePipeBlock::new,
            props -> props.strength(1.5F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<?> PIPE_ITEM = ITEMS.registerSimpleBlockItem("pipe", PIPE);
    public static final DeferredItem<?> STORAGE_TANK_ITEM = ITEMS.registerSimpleBlockItem("storage_tank", STORAGE_TANK);
    public static final DeferredItem<?> CREATIVE_PIPE_ITEM = ITEMS.registerSimpleBlockItem("creative_pipe", CREATIVE_PIPE);

    /** The extra data the server sends on opening is the creative pipe's position. */
    public static final DeferredHolder<MenuType<?>, MenuType<CreativePipeMenu>> CREATIVE_PIPE_MENU =
            MENUS.register("creative_pipe", () -> IMenuTypeExtension.create(CreativePipeMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_TABS.register("items", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pipeworks.items"))
                    .icon(() -> new ItemStack(PIPE_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(PIPE_ITEM.get());
                        output.accept(STORAGE_TANK_ITEM.get());
                        output.accept(CREATIVE_PIPE_ITEM.get());
                    })
                    .build());

    private PipeworksRegistries() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        CREATIVE_TABS.register(modBus);
        modBus.addListener(PipeworksRegistries::registerCapabilities);
        modBus.addListener(PipeworksRegistries::addToCreativeTabs);
    }

    /** The pipes and the tank are fluid faces, so a Consumer's machine reaches a segment as it would any tank. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Fluid.BLOCK,
                (level, pos, state, entity, side) -> level instanceof ServerLevel server
                        ? FluidSegments.get(server).handlerAt(pos) : null,
                PIPE.get(), STORAGE_TANK.get(), CREATIVE_PIPE.get());
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(PIPE_ITEM.get());
            event.accept(STORAGE_TANK_ITEM.get());
        }
    }
}
