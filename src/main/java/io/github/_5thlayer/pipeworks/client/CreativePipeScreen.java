// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.client;

import io.github._5thlayer.pipeworks.block.CreativePipeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A creative pipe's screen, in vanilla's colours: one slot for the fluid on top, the player's
 * inventory below. A click on the slot with a bucket, or any item holding a fluid, sets the fluid,
 * and with an empty cursor clears it.
 *
 * <p>It holds no state of its own: the fluid is the slot's contents, which the server keeps true.
 * Public so the recipe viewer compat, which drops fluids on the slot, can name it.
 */
public final class CreativePipeScreen extends AbstractContainerScreen<CreativePipeMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 133;
    // Vanilla's container colours: a light panel lit from the top left, slots sunk into it.
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADE = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADE = 0xFF373737;
    private static final int EDGE = 0xFF000000;
    private static final int LABEL = 0xFF404040;

    public CreativePipeScreen(CreativePipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        panel(graphics, leftPos, topPos, leftPos + WIDTH, topPos + HEIGHT);
        graphics.text(font, title, leftPos + 8, topPos + 6, LABEL, false);
        graphics.text(font, playerInventoryTitle, leftPos + 8, topPos + 39, LABEL, false);
        for (var slot : menu.slots) {
            var x = leftPos + slot.x;
            var y = topPos + slot.y;
            // Shaded on the top and left, lit on the bottom and right, its other two corners the slot's own grey.
            graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SHADE);
            graphics.fill(x, y, x + 17, y + 17, LIGHT);
            graphics.fill(x, y, x + 16, y + 16, SLOT);
            graphics.fill(x + 16, y - 1, x + 17, y, SLOT);
            graphics.fill(x - 1, y + 16, x, y + 17, SLOT);
        }
    }

    /** A vanilla window: a black outline, lit on the top and left, shaded on the bottom and right. */
    private static void panel(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0, y0, x1, y1, EDGE);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SHADE);
        graphics.fill(x0 + 1, y0 + 1, x1 - 2, y1 - 2, LIGHT);
        graphics.fill(x0 + 3, y0 + 3, x1 - 2, y1 - 2, SHADE);
        graphics.fill(x0 + 3, y0 + 3, x1 - 3, y1 - 3, PANEL);
    }

    // The labels are drawn with the background, in colours that read on it.
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        var slot = hoveredSlot;
        if (slot != null && slot.index == CreativePipeMenu.FLUID_SLOT && !slot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.setTooltipForNextFrame(font, Component.translatable("screen.pipeworks.creative_pipe.fluid"), mouseX, mouseY);
        }
    }
}
