// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.api;

import net.minecraft.core.Direction;

/**
 * Implemented by a block entity that joins a fluid segment: a pump, a boiler, a machine's fluid
 * connection. The block entity calls {@link FluidPorts#join} from {@code onLoad}, its block calls
 * {@link FluidPorts#leave} when it is removed, and the port fills and drains its segment through
 * {@link FluidPorts#segment}.
 */
public interface FluidPort {

    /**
     * Whether this port opens onto a pipe beside it on {@code face}, a face of this block. Read once,
     * when the port joins: a port that changes its faces leaves and joins again.
     */
    boolean connectsOn(Direction face);

    /**
     * The millibuckets this port adds to its segment's capacity. Most ports add none and are only
     * a way in and out of the pipes beside them, which is the default.
     */
    default long capacity() {
        return 0;
    }
}
