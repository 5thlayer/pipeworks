// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import io.github._5thlayer.libworks.WorksTab;
import io.github._5thlayer.pipeworks.block.CreativePipeBlock;
import io.github._5thlayer.pipeworks.block.CreativePipeMenu;
import io.github._5thlayer.pipeworks.block.FluidPipeBlock;
import io.github._5thlayer.pipeworks.block.StorageTankBlock;
import io.github._5thlayer.pipeworks.outlet.OutletBlock;
import io.github._5thlayer.pipeworks.outlet.OutletBlockEntity;
import io.github._5thlayer.pipeworks.pump.PumpBlock;
import io.github._5thlayer.pipeworks.pump.PumpBlockEntity;
import io.github._5thlayer.pipeworks.pump.PumpItem;
import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The pipe, the storage tank, the Pump, the Outlet and the creative pipe, their items, the creative pipe's menu, and their place in the creative tabs. No fluid is registered here. */
public final class PipeworksRegistries {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Pipeworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Pipeworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Pipeworks.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Pipeworks.MOD_ID);

    /**
     * Solid, though its shape is a thin core with arms, so a flowing fluid stops at it as at a wall rather than
     * washing it away as it would a torch (#34).
     */
    public static final DeferredBlock<FluidPipeBlock> PIPE = BLOCKS.registerBlock("pipe", FluidPipeBlock::new,
            props -> props.strength(1.5F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion().forceSolidOn());
    public static final DeferredBlock<StorageTankBlock> STORAGE_TANK = BLOCKS.registerBlock("storage_tank", StorageTankBlock::new,
            props -> props.strength(2.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());
    /**
     * Has no recipe, which keeps it out of survival: it is in the Works tab and {@code /give}. Solid to a flowing
     * fluid, as the pipe is.
     */
    public static final DeferredBlock<CreativePipeBlock> CREATIVE_PIPE = BLOCKS.registerBlock("creative_pipe", CreativePipeBlock::new,
            props -> props.strength(1.5F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion().forceSolidOn());
    /** Has a crafting recipe from pipes and vanilla items; a pack may replace it. */
    public static final DeferredBlock<PumpBlock> PUMP = BLOCKS.registerBlock("pump", PumpBlock::new,
            props -> props.strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    /**
     * Has a crafting recipe from pipes and vanilla items; a pack may replace it. It narrows in steps to a
     * nozzle, so it occludes nothing, though its hitbox is the whole block.
     */
    public static final DeferredBlock<OutletBlock> OUTLET = BLOCKS.registerBlock("outlet", OutletBlock::new,
            props -> props.strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<?> PIPE_ITEM = ITEMS.registerSimpleBlockItem("pipe", PIPE);
    public static final DeferredItem<?> STORAGE_TANK_ITEM = ITEMS.registerSimpleBlockItem("storage_tank", STORAGE_TANK);
    public static final DeferredItem<?> CREATIVE_PIPE_ITEM = ITEMS.registerSimpleBlockItem("creative_pipe", CREATIVE_PIPE);
    public static final DeferredItem<PumpItem> PUMP_ITEM = ITEMS.registerItem("pump", props -> new PumpItem(PUMP.get(), props.useBlockDescriptionPrefix()));
    public static final DeferredItem<?> OUTLET_ITEM = ITEMS.registerSimpleBlockItem("outlet", OUTLET);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpBlockEntity>> PUMP_ENTITY =
            BLOCK_ENTITIES.register("pump", () -> new BlockEntityType<>(PumpBlockEntity::new, Set.of(PUMP.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OutletBlockEntity>> OUTLET_ENTITY =
            BLOCK_ENTITIES.register("outlet", () -> new BlockEntityType<>(OutletBlockEntity::new, Set.of(OUTLET.get())));

    /** The extra data the server sends on opening is the creative pipe's position. */
    public static final DeferredHolder<MenuType<?>, MenuType<CreativePipeMenu>> CREATIVE_PIPE_MENU =
            MENUS.register("creative_pipe", () -> IMenuTypeExtension.create(CreativePipeMenu::new));

    private PipeworksRegistries() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(PipeworksRegistries::registerCapabilities);
        modBus.addListener(PipeworksRegistries::addToCreativeTabs);
    }

    /**
     * The pipes and the tank are fluid faces, so a Consumer's machine reaches a segment as it would any
     * tank, except on a side a pipe has closed (ADR 0004), where there is none.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Fluid.BLOCK,
                (level, pos, state, entity, side) -> level instanceof ServerLevel server
                        ? FluidSegments.get(server).handlerAt(server, pos, side) : null,
                PIPE.get(), STORAGE_TANK.get(), CREATIVE_PIPE.get());
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == WorksTab.KEY) {
            event.accept(PIPE_ITEM.get());
            event.accept(STORAGE_TANK_ITEM.get());
            event.accept(PUMP_ITEM.get());
            event.accept(OUTLET_ITEM.get());
            event.accept(CREATIVE_PIPE_ITEM.get());
        }
    }
}
