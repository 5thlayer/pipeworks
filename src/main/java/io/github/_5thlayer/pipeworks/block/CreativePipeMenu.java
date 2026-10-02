// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.block;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.PipeworksRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * The menu of a creative pipe's screen: the player's inventory, and a view of the one fluid the
 * pipe is set to, which {@link FluidSegments} keeps and the server alone writes.
 *
 * <p>The fluid is shown as a slot of one item, its bucket, so vanilla's own slot sync keeps the
 * client's screen true, including when another player changes the pipe. The client changes nothing:
 * the slot's click is read from the cursor stack the server holds, so a client cannot claim to hold
 * what it does not. {@link #setFluid} is the one way the fluid is set, for the click and for a
 * recipe viewer's drop alike.
 */
public final class CreativePipeMenu extends AbstractContainerMenu {

    /** The fluid's ghost slot, the menu's first: a click sets it from the cursor stack and takes nothing. */
    public static final int FLUID_SLOT = 0;

    private final BlockPos pipe;
    private final Player player;
    private final Container shown = new SimpleContainer(1);

    /** Opened on the client from the position the server sent. */
    public CreativePipeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public CreativePipeMenu(int containerId, Inventory inventory, BlockPos pipe) {
        super(PipeworksRegistries.CREATIVE_PIPE_MENU.get(), containerId);
        this.pipe = pipe;
        player = inventory.player;
        addSlot(new FluidSlot(shown, 80, 20));
        addStandardInventorySlots(inventory, 8, 51);
        refresh();
    }

    /** The fluid the pipe is set to as its bucket, or empty for none, or for one with no bucket. */
    public ItemStack shown() {
        return shown.getItem(0);
    }

    /** The fluid a stack holds: the one in a bucket, or in any item that exposes a fluid. Null if it holds none. */
    public static @Nullable Fluid fluidIn(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof BucketItem bucket && bucket.content != Fluids.EMPTY) {
            return bucket.content;
        }
        ItemStack one = stack.copyWithCount(1);
        ResourceHandler<FluidResource> handler = one.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(one));
        if (handler != null) {
            for (int i = 0; i < handler.size(); i++) {
                FluidResource resource = handler.getResource(i);
                if (!resource.isEmpty() && handler.getAmountAsLong(i) > 0) {
                    return resource.getFluid();
                }
            }
        }
        return null;
    }

    private boolean pipeStands(Player who) {
        return who.level().hasChunkAt(pipe) && who.level().getBlockState(pipe).getBlock() instanceof CreativePipeBlock;
    }

    // Reads the pipe's fluid into the slot, which the next broadcast sends to the client.
    private void refresh() {
        if (!(player.level() instanceof ServerLevel level) || !pipeStands(player)) {
            return;
        }
        Fluid fluid = FluidSegments.get(level).sourceAt(pipe);
        ItemStack bucket = fluid == null || fluid.getBucket() == Items.AIR
                ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
        if (!ItemStack.matches(shown(), bucket)) {
            shown.setItem(0, bucket);
        }
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    /**
     * Sets the pipe to {@code fluid}, or clears it with {@link Fluids#EMPTY}. A fluid that would mix
     * with its segment's, or with another creative pipe's, is refused with a message to the player.
     * Nothing is taken from anyone. On the server only.
     */
    public void setFluid(Fluid fluid) {
        if (!(player.level() instanceof ServerLevel level) || !pipeStands(player)) {
            return;
        }
        FluidSegments segments = FluidSegments.get(level);
        if (fluid == Fluids.EMPTY) {
            segments.clear(pipe);
        } else if (!segments.source(pipe, fluid) && player instanceof ServerPlayer server) {
            server.sendOverlayMessage(Component.translatable("message.pipeworks.mixed_fluids"));
        }
        refresh();
    }

    /**
     * A click on the ghost slot sets the fluid from the stack on the cursor, or clears it with an
     * empty cursor, and changes neither stack. A cursor stack that holds no fluid, and every other
     * input on the slot, does nothing. The client does nothing at all: the server's answer arrives
     * as the slot's contents.
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player who) {
        if (slotId != FLUID_SLOT) {
            super.clicked(slotId, button, input, who);
            return;
        }
        if (input != ContainerInput.PICKUP || (button != 0 && button != 1)) {
            return;
        }
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            setFluid(Fluids.EMPTY);
        } else {
            Fluid carriedFluid = fluidIn(carried);
            if (carriedFluid != null) {
                setFluid(carriedFluid);
            }
        }
    }

    // Nothing shift-clicks into the ghost slot, and a pipe holds no items.
    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        return ItemStack.EMPTY;
    }

    /** Valid while the pipe stands and the player is within reach of it. */
    @Override
    public boolean stillValid(Player who) {
        return pipeStands(who) && who.isWithinBlockInteractionRange(pipe, 1);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof FluidSlot) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof FluidSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /** The fluid shown, which the player can neither put into nor take out of. */
    private static final class FluidSlot extends Slot {

        FluidSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
