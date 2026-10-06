// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.api;

import io.github._5thlayer.pipeworks.FluidSegments;
import io.github._5thlayer.pipeworks.Pipeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * What a Consumer that plans pipes before placing them asks, such as a drag that lays a run, and
 * what it calls to close and open a pipe's sides with a tool of its own (ADR 0004).
 */
public final class FluidPipes {

    /**
     * The items a player closes and opens a pipe's sides with, by a plain right-click on the pipe. It
     * is empty: Pipeworks ships no tool, and a pack fills it, for example with {@code #c:tools/wrench}.
     * A sneak-click is left alone, for Groundworks' Dismantle.
     */
    public static final TagKey<Item> CLOSES_SIDES = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "closes_sides"));

    private FluidPipes() {
    }

    /**
     * Whether a pipe placed at {@code pos} would link to the node on {@code side}. The server answers
     * as placement does, so a pipe would not link to a node waiting outside every segment, and a pipe
     * that would join two fluids links nowhere. The client cannot tell and answers whether the block
     * on that side is a pipe or tank, or a port that opens towards it. Only the world is read, so a
     * planner adds the links between the pipes it plans itself, and a run that would join two fluids
     * only once laid whole is not caught here.
     *
     * <p>A link is between two nodes: a fluid inventory is none, so this answers false toward one. To
     * know the arm a pipe would draw, ask {@link #wouldDrawArm}.
     */
    public static boolean wouldLink(Level level, BlockPos pos, Direction side) {
        if (level instanceof ServerLevel server) {
            return FluidSegments.get(server).wouldLink(pos, side);
        }
        BlockPos beside = pos.relative(side);
        return level.getBlockState(beside).getBlock() instanceof FluidSegments.SegmentBlock
                || level.getBlockEntity(beside) instanceof FluidPort port && port.connectsOn(side.getOpposite());
    }

    /**
     * Whether a pipe placed at {@code pos} would draw an arm on {@code side}: it would link there, or
     * a fluid inventory is there. The server answers as placement does, so a pipe that would wait
     * draws none. The client looks the fluid handler up as it is, and a handler a server exposes
     * only to itself is missed, which a planner's preview can live with. Only the world is read, as
     * in {@link #wouldLink}.
     */
    public static boolean wouldDrawArm(Level level, BlockPos pos, Direction side) {
        if (level instanceof ServerLevel server) {
            return FluidSegments.get(server).wouldDrawArm(level, pos, side);
        }
        return wouldLink(level, pos, side) || FluidSegments.isFluidInventory(level, pos.relative(side), side.getOpposite());
    }

    /**
     * Whether the pipe at {@code pos} has closed {@code side}: it links to no node there, moves no
     * fluid with a fluid inventory and exposes no fluid handler. Only the server knows.
     */
    public static boolean isClosed(ServerLevel level, BlockPos pos, Direction side) {
        return FluidSegments.get(level).closed(pos, side);
    }

    /**
     * Closes {@code side} of the pipe at {@code pos} if it is open, and opens it if it is closed, as a
     * click with an item in {@link #CLOSES_SIDES} does. See {@link #close} and {@link #open}.
     *
     * @return whether the side changed
     */
    public static boolean toggle(ServerLevel level, BlockPos pos, Direction side) {
        return FluidSegments.get(level).toggleSide(level, pos, side) == FluidSegments.SideChange.CHANGED;
    }

    /**
     * Closes {@code side} of the pipe at {@code pos}, which may have nothing beside it. If a pipe or
     * tank is there its face toward this pipe closes too, so the two part and their segment splits,
     * its fluid kept. A port's faces are its own and never change. The change is held back while a
     * transaction is open (ADR 0003), and tells the level its capabilities changed.
     *
     * @return whether the side changed: false for a position with no pipe and for a side already closed
     */
    public static boolean close(ServerLevel level, BlockPos pos, Direction side) {
        return FluidSegments.get(level).setSide(level, pos, side, false) == FluidSegments.SideChange.CHANGED;
    }

    /**
     * Opens {@code side} of the pipe at {@code pos}, and the face of a pipe or tank beside it. The two
     * link and their segments merge, unless that would mix two fluids, which is refused as a player's
     * placement is, and nothing changes. A Consumer's tool tells its player.
     *
     * @return whether the side changed: false for a position with no pipe, for a side already open
     *         and for a refusal
     */
    public static boolean open(ServerLevel level, BlockPos pos, Direction side) {
        return FluidSegments.get(level).setSide(level, pos, side, true) == FluidSegments.SideChange.CHANGED;
    }
}
