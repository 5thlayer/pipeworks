// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.compat;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import io.github._5thlayer.pipeworks.block.CreativePipeMenu;
import io.github._5thlayer.pipeworks.client.CreativePipeScreen;
import io.github._5thlayer.pipeworks.network.CreativePipeFluidPayload;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Lets a fluid, or an item holding one, be dragged from EMI's list onto the slot of a creative
 * pipe's screen. EMI finds the plugin by its annotation, and nothing else names it, so the Library
 * loads without EMI.
 *
 * <p>The drop sends {@link CreativePipeFluidPayload}, which the server answers through the same
 * {@link CreativePipeMenu#setFluid} a click on the slot uses.
 */
@EmiEntrypoint
public class CreativePipeEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(CreativePipeScreen.class, new EmiDragDropHandler.BoundsBased<CreativePipeScreen>(
                (screen, bounds) -> {
                    var slot = screen.getMenu().slots.get(CreativePipeMenu.FLUID_SLOT);
                    bounds.accept(new Bounds(screen.getLeftPos() + slot.x, screen.getTopPos() + slot.y, 16, 16), dropped -> {
                        Fluid fluid = fluidOf(dropped);
                        if (fluid != null) {
                            ClientPacketDistributor.sendToServer(new CreativePipeFluidPayload(screen.getMenu().containerId, fluid));
                        }
                    });
                }));
    }

    /** The fluid of a fluid stack, or of an item stack that holds one. Null for any other ingredient. */
    private static Fluid fluidOf(EmiIngredient ingredient) {
        for (EmiStack stack : ingredient.getEmiStacks()) {
            Fluid fluid = stack.getKeyOfType(Fluid.class);
            if (fluid == null) {
                fluid = CreativePipeMenu.fluidIn(stack.getItemStack());
            }
            if (fluid != null) {
                return fluid;
            }
        }
        return null;
    }
}
