// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.pump;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import io.github._5thlayer.groundworks.Refusal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.jspecify.annotations.Nullable;

/**
 * Placing a Pump, and refusing to where no still source of any fluid stands beside it, with a message
 * and nothing consumed. The preview and the click ask the one plan (ADR 0006).
 */
public class PumpItem extends BlockItem implements PlansPlacement {

    private static final String NO_SOURCE_KEY = "message.pipeworks.pump.no_source";

    /** The Pump's own refusal. */
    public enum Siting implements Refusal {
        NO_SOURCE
    }

    public PumpItem(net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        PlacementPlan plan = Placements.vanillaPlan(this, context);
        if (plan == null || plan.isRefused()) {
            return plan;
        }
        PlacementPlan.Placed at = plan.blocks().getFirst();
        if (PumpBlock.sourcesBeside(context.getLevel(), at.pos()).isEmpty()) {
            return PlacementPlan.refused(at.pos(), at.state(), Siting.NO_SOURCE);
        }
        return plan;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        PlacementPlan plan = Placements.planFor(this, context);
        if (plan == null || plan.isRefused()) {
            if (plan != null && plan.refusal() == Siting.NO_SOURCE && context.getPlayer() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable(NO_SOURCE_KEY), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
