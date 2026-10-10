// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/** The six blocks beside a position, and the face one block meets another on. */
public final class Adjacent {

    private Adjacent() {
    }

    public static List<BlockPos> around(BlockPos pos) {
        List<BlockPos> around = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            around.add(pos.relative(side));
        }
        return around;
    }

    /** The face of {@code from} that {@code to} stands on, or null if they do not touch. */
    public static @Nullable Direction face(BlockPos from, BlockPos to) {
        for (Direction side : Direction.values()) {
            if (from.relative(side).equals(to)) {
                return side;
            }
        }
        return null;
    }
}
