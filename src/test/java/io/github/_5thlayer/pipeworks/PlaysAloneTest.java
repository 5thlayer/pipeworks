// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Library plays alone on Groundworks (ADR-0127): nothing it ships names another suite mod's id or
 * puts data in its namespace. The tags it consumes are checked by the game tests, which resolve them.
 */
class PlaysAloneTest {

    private static final List<Path> SHIPPED = List.of(Path.of("src/main/java"), Path.of("src/main/resources"));

    @Test
    void theShippedLibraryNamesNoOtherSuiteMod() throws IOException {
        for (Path root : SHIPPED) {
            assertEquals(List.of(), PlaysAlone.foreignIds(root, Pipeworks.MOD_ID));
        }
        assertEquals(List.of(), PlaysAlone.foreignNamespaces(Path.of("src/main/resources"), Pipeworks.MOD_ID));
    }

    @Test
    void aForeignIdInJavaSourceIsReported(@TempDir Path root) throws IOException {
        write(root.resolve("x/Belts.java"), "class Belts { String id = \"beltworks:belt\"; }");
        assertEquals(List.of("x/Belts.java names beltworks:belt"), PlaysAlone.foreignIds(root, Pipeworks.MOD_ID));
    }

    @Test
    void aForeignIdInAnAssetOrTheModsTomlIsReported(@TempDir Path root) throws IOException {
        write(root.resolve("assets/pipeworks/models/item/pipe.json"), "{\"parent\":\"wireworks:item/pole\"}");
        write(root.resolve("META-INF/neoforge.mods.toml"), "[[dependencies.pipeworks]]\nmodId=\"craftworks\"\n# craftworks:assembler\n");
        assertEquals(List.of("META-INF/neoforge.mods.toml names craftworks:assembler",
                "assets/pipeworks/models/item/pipe.json names wireworks:item/pole"), PlaysAlone.foreignIds(root, Pipeworks.MOD_ID));
    }

    @Test
    void theLibrarysOwnGroundworksAndTheSharedNamespacesAreAllowed(@TempDir Path root) throws IOException {
        write(root.resolve("data/pipeworks/recipe/x.json"),
                "{\"a\":\"minecraft:glass\",\"b\":\"#c:glass_blocks\",\"c\":\"groundworks:x\",\"d\":\"neoforge:y\",\"e\":\"pipeworks:pipe\"}");
        write(root.resolve("data/groundworks/tags/item/dismantles.json"), "{}");
        assertEquals(List.of(), PlaysAlone.foreignIds(root, Pipeworks.MOD_ID));
        assertEquals(List.of(), PlaysAlone.foreignNamespaces(root, Pipeworks.MOD_ID));
    }

    @Test
    void aFolderInAnotherSuiteModsNamespaceIsReported(@TempDir Path root) throws IOException {
        write(root.resolve("data/fieldworks/recipe/x.json"), "{}");
        write(root.resolve("assets/factoryworks/lang/en_us.json"), "{}");
        assertEquals(List.of("data/fieldworks", "assets/factoryworks"), PlaysAlone.foreignNamespaces(root, Pipeworks.MOD_ID));
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
