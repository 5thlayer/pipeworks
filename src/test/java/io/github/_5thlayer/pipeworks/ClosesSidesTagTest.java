// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** A player on Groundworks alone can close a pipe side with a stick (ADR 0004). */
class ClosesSidesTagTest {

    @Test
    void theShippedTagHoldsAStick() throws IOException {
        String tag = Files.readString(Path.of("src/main/resources/data/pipeworks/tags/item/closes_sides.json"));
        assertTrue(tag.contains("\"minecraft:stick\""), tag);
    }
}
