// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.compat;

import java.util.List;

import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.block.CreativePipeMenu;
import io.github._5thlayer.pipeworks.client.CreativePipeScreen;
import io.github._5thlayer.pipeworks.network.CreativePipeFluidPayload;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Lets a fluid, or an item holding one, be dragged from JEI's list onto the slot of a creative
 * pipe's screen. JEI finds the plugin by its annotation, and nothing else names it, so the Library
 * loads without JEI.
 *
 * <p>The drop sends {@link CreativePipeFluidPayload}, which the server answers through the same
 * {@link CreativePipeMenu#setFluid} a click on the slot uses.
 */
@JeiPlugin
public class CreativePipeJeiPlugin implements IModPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "creative_pipe");

    private static final IGhostIngredientHandler<CreativePipeScreen> FLUID_DROP = new IGhostIngredientHandler<>() {
        @Override
        public <I> List<Target<I>> getTargetsTyped(CreativePipeScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
            Fluid fluid = fluidOf(ingredient);
            if (fluid == null) {
                return List.of();
            }
            var slot = screen.getMenu().slots.get(CreativePipeMenu.FLUID_SLOT);
            var area = new Rect2i(screen.getLeftPos() + slot.x, screen.getTopPos() + slot.y, 16, 16);
            return List.of(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I dropped) {
                    ClientPacketDistributor.sendToServer(new CreativePipeFluidPayload(screen.getMenu().containerId, fluid));
                }
            });
        }

        @Override
        public void onComplete() {
        }
    };

    /** The fluid of a fluid ingredient, or of an item that holds one. Null for any other ingredient. */
    private static <I> Fluid fluidOf(ITypedIngredient<I> ingredient) {
        var fluidStack = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK);
        if (fluidStack.isPresent() && !fluidStack.get().isEmpty()) {
            return fluidStack.get().getFluid();
        }
        ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
        return CreativePipeMenu.fluidIn(stack);
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(CreativePipeScreen.class, FLUID_DROP);
    }
}
