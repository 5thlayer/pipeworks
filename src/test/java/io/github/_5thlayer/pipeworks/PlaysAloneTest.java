// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The mod plays alone on Groundworks (ADR-0127): its data names no other suite mod's id, ships none
 * in its namespace, and consumes no tag a vanilla item cannot fill.
 */
class PlaysAloneTest {

    private static final Path DATA = Path.of("src/main/resources/data");

    @Test
    void theShippedDataPlaysAlone() throws IOException {
        assertEquals(List.of(), PlaysAlone.violations(DATA, Pipeworks.MOD_ID));
    }

    @Test
    void aForeignNamespaceFolderIsReported(@TempDir Path data) throws IOException {
        write(data.resolve("wireworks/recipe/x.json"), "{}");
        assertTrue(PlaysAlone.violations(data, Pipeworks.MOD_ID).stream().anyMatch(v -> v.contains("wireworks")));
    }

    @Test
    void aForeignIdIsReported(@TempDir Path data) throws IOException {
        write(data.resolve("pipeworks/recipe/x.json"), "{\"result\":{\"id\":\"factoryworks:crude_oil\"}}");
        assertTrue(PlaysAlone.violations(data, Pipeworks.MOD_ID).stream().anyMatch(v -> v.contains("factoryworks:crude_oil")));
    }

    @Test
    void aForeignTagIsReported(@TempDir Path data) throws IOException {
        write(data.resolve("pipeworks/recipe/x.json"), "{\"key\":{\"a\":\"#beltworks:belts\"}}");
        assertTrue(PlaysAlone.violations(data, Pipeworks.MOD_ID).stream().anyMatch(v -> v.contains("beltworks:belts")));
    }

    @Test
    void groundworksAndTheSharedNamespacesAreAllowed(@TempDir Path data) throws IOException {
        write(data.resolve("groundworks/tags/item/dismantles.json"), "{}");
        write(data.resolve("pipeworks/recipe/x.json"),
                "{\"a\":\"minecraft:glass\",\"b\":\"#c:glass_blocks\",\"c\":\"groundworks:x\",\"d\":\"neoforge:y\"}");
        assertEquals(List.of(), PlaysAlone.violations(data, Pipeworks.MOD_ID));
    }

    @Test
    void anOwnTagWithoutAVanillaMemberIsReported(@TempDir Path data) throws IOException {
        write(data.resolve("pipeworks/tags/item/rods.json"), "{\"values\":[\"pipeworks:rod\"]}");
        write(data.resolve("pipeworks/recipe/x.json"), "{\"key\":{\"a\":\"#pipeworks:rods\"}}");
        assertTrue(PlaysAlone.violations(data, Pipeworks.MOD_ID).stream().anyMatch(v -> v.contains("pipeworks:rods")));
    }

    @Test
    void anOwnTagReachesAVanillaMemberThroughAnotherTag(@TempDir Path data) throws IOException {
        write(data.resolve("pipeworks/tags/item/outer.json"), "{\"values\":[\"#pipeworks:inner\"]}");
        write(data.resolve("pipeworks/tags/item/inner.json"), "{\"values\":[\"minecraft:stick\"]}");
        write(data.resolve("pipeworks/recipe/x.json"), "{\"key\":{\"a\":\"#pipeworks:outer\"}}");
        assertEquals(List.of(), PlaysAlone.violations(data, Pipeworks.MOD_ID));
    }

    @Test
    void aCommonTagOutsideTheKnownDefaultsIsReported(@TempDir Path data) throws IOException {
        write(data.resolve("pipeworks/recipe/x.json"), "{\"key\":{\"a\":{\"tag\":\"c:gems/ruby\"}}}");
        assertTrue(PlaysAlone.violations(data, Pipeworks.MOD_ID).stream().anyMatch(v -> v.contains("c:gems/ruby")));
        assertFalse(PlaysAlone.violations(data, Pipeworks.MOD_ID).isEmpty());
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
