// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.fluiditem;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.fluiditem.FluidItemRule.Box;
import io.github._5thlayer.pipeworks.fluiditem.FluidItemRule.Move;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A fluid item used on a pipe or tank: any item with {@code Capabilities.Fluid.ITEM}, a bucket or
 * another mod's portable tank, fills from or empties into the node's segment as {@link
 * FluidItemRule} decides (5thlayer/factoryworks ADR-0121). The held item changes as vanilla's
 * cauldron changes it: left as it is in creative, and one of a stack swapped, the filled or emptied
 * item going to the inventory. An item of several slots is used through the first that holds fluid,
 * or its first if none does, as the creative pipe's screen reads it. The item works on the node, so
 * a pipe's closed side is no bar to it.
 */
public final class FluidItems {

    private FluidItems() {
    }

    /**
     * Uses the item in {@code hand} on the node at {@code pos}. An item that is no fluid item gets
     * {@link InteractionResult#TRY_WITH_EMPTY_HAND}, for the block to do what it does otherwise. A
     * fluid item is never passed on, so a refused bucket does not set its fluid down beside the node.
     */
    public static InteractionResult use(Level level, BlockPos pos, Player player, InteractionHand hand) {
        ResourceHandler<FluidResource> item = ItemAccess.forPlayerInteraction(player, hand).oneByOne()
                .getCapability(Capabilities.Fluid.ITEM);
        if (item == null || item.size() == 0) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // The client cannot tell what the segment holds, so it leaves the answer to the server.
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        ResourceHandler<FluidResource> segment = FluidSegments.get(server).handlerAt(server, pos);
        if (segment == null) {
            return InteractionResult.CONSUME;
        }
        int slot = slotOf(item);
        FluidResource held = item.getResource(slot);
        FluidResource contained = segment.getResource(0);
        // An empty item's room, and a segment's, is for the fluid that would move.
        FluidResource moving = held.isEmpty() ? contained : held;
        Move move = FluidItemRule.decide(box(item, slot, moving), box(segment, 0, moving));
        if (move.way() == FluidItemRule.Way.NONE) {
            return InteractionResult.CONSUME;
        }
        boolean fill = move.way() == FluidItemRule.Way.FILL;
        ResourceHandler<FluidResource> from = fill ? segment : item;
        ResourceHandler<FluidResource> to = fill ? item : segment;
        int amount = (int) Math.min(move.amount(), Integer.MAX_VALUE);
        try (Transaction transaction = Transaction.openRoot()) {
            // A bucket takes or gives only 1,000 mB, so a part of it moves nothing.
            if (from.extract(fill ? 0 : slot, moving, amount, transaction) != amount
                    || to.insert(fill ? slot : 0, moving, amount, transaction) != amount) {
                return InteractionResult.CONSUME;
            }
            transaction.commit();
        }
        FluidUtil.triggerSoundAndGameEvent(moving, level, Vec3.atCenterOf(pos), player, fill);
        return InteractionResult.SUCCESS;
    }

    /** The first slot of {@code item} that holds fluid, or its first if none does. */
    private static int slotOf(ResourceHandler<FluidResource> item) {
        for (int i = 0; i < item.size(); i++) {
            if (!item.getResource(i).isEmpty() && item.getAmountAsLong(i) > 0) {
                return i;
            }
        }
        return 0;
    }

    /** What slot {@code index} of {@code handler} holds, and its room for {@code moving}. */
    private static Box<FluidResource> box(ResourceHandler<FluidResource> handler, int index, FluidResource moving) {
        FluidResource fluid = handler.getResource(index);
        return new Box<>(fluid.isEmpty() ? null : fluid, handler.getAmountAsLong(index),
                moving.isEmpty() ? 0 : handler.getCapacityAsLong(index, moving));
    }
}
