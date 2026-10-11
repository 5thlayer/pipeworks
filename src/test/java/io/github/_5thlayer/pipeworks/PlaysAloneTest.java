// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Library plays alone on Groundworks (FactoryWorks ADR-0127): nothing it ships names another Module's id or
 * holds data or assets in its namespace. The tags it consumes are checked by {@code PlaysAloneTests} in game,
 * where they resolve.
 */
class PlaysAloneTest {

    private static final Path CODE = Path.of("src/main/java");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void theShippedCodeAndResourcesNameNoOtherModulesId() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesIds(CODE));
        assertEquals(List.of(), PlaysAlone.otherModulesIds(RESOURCES));
    }

    @Test
    void theShippedCodeNamesNoOtherModulesNamespace() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesNamespaceStrings(CODE));
    }

    @Test
    void theShippedModsTomlRequiresNoOtherModule() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesRequired(RESOURCES));
    }

    @Test
    void theShippedResourcesHoldNothingInAnotherModulesNamespace() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesNamespaces(RESOURCES));
    }

    @Test
    void aDataOrAssetFolderInAnotherModulesNamespaceIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/fieldworks/recipe/x.json"), "{}");
        write(resources.resolve("assets/voidworks/lang/en_us.json"), "{}");
        write(resources.resolve("data/pipeworks/recipe/x.json"), "{}");
        write(resources.resolve("data/groundworks/tags/item/dismantles.json"), "{}");
        assertEquals(List.of("assets/voidworks", "data/fieldworks"), PlaysAlone.otherModulesNamespaces(resources));
    }

    @Test
    void anIdInDataIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/pipeworks/recipe/x.json"), "{\"key\":{\"a\":\"gearworks:gear\"}}");
        assertEquals(List.of("data/pipeworks/recipe/x.json names gearworks:gear"), PlaysAlone.otherModulesIds(resources));
    }

    @Test
    void aTagOfTheRetiredPackInARecipeIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/pipeworks/recipe/x.json"), "{\"key\":{\"a\":\"#factoryworks:belts\"}}");
        assertEquals(List.of("data/pipeworks/recipe/x.json names factoryworks:belts"), PlaysAlone.otherModulesIds(resources));
    }

    @Test
    void anIdInJavaSourceIsReported(@TempDir Path code) throws IOException {
        write(code.resolve("x/Belts.java"), "class Belts { String id = \"beltworks:belt\"; }");
        assertEquals(List.of("x/Belts.java names beltworks:belt"), PlaysAlone.otherModulesIds(code));
    }

    @Test
    void anIdInTheModsTomlIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), "description = \"fills wireworks:cable\"");
        assertEquals(List.of("META-INF/neoforge.mods.toml names wireworks:cable"), PlaysAlone.otherModulesIds(resources));
    }

    @Test
    void anIdInAStructureIsReportedWhenItsLengthByteIsALetter(@TempDir Path resources) throws IOException {
        String id = "fieldworks:" + "o".repeat('A' - "fieldworks:".length());
        Path nbt = resources.resolve("data/pipeworks/structure/x.nbt");
        Files.createDirectories(nbt.getParent());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(nbt))) {
            out.write(("\u0008\u0000\u0004Name\u0000" + (char) id.length() + id).getBytes(StandardCharsets.ISO_8859_1));
        }
        assertEquals(List.of("data/pipeworks/structure/x.nbt names " + id), PlaysAlone.otherModulesIds(resources));
    }

    @Test
    void anotherModulesNamespaceInJavaSourceIsReported(@TempDir Path code) throws IOException {
        write(code.resolve("x/Belts.java"), "class Belts { Identifier id = Identifier.fromNamespaceAndPath(\"beltworks\", \"belt\"); }");
        write(code.resolve("x/Own.java"), "class Own { String ns = \"pipeworks\"; String gw = \"groundworks\"; }");
        assertEquals(List.of("x/Belts.java names \"beltworks\""), PlaysAlone.otherModulesNamespaceStrings(code));
    }

    @Test
    void aRequiredDependencyOnAnotherModuleIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), """
                [[mods]]
                modId = "pipeworks"

                [[dependencies.pipeworks]]
                modId = "groundworks"
                type = "required"

                [[dependencies.pipeworks]]
                modId = "craftworks"
                type = "required"

                [[dependencies.pipeworks]]
                modId="wireworks"
                """);
        assertEquals(List.of("META-INF/neoforge.mods.toml requires craftworks", "META-INF/neoforge.mods.toml requires wireworks"),
                PlaysAlone.otherModulesRequired(resources));
    }

    @Test
    void anOptionalDependencyOnAnotherModuleIsAllowed(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), """
                [[dependencies.pipeworks]]
                modId = "beltworks"
                type = "optional"
                """);
        assertEquals(List.of(), PlaysAlone.otherModulesRequired(resources));
    }

    @Test
    void theModsOwnIdsGroundworksAndTheSharedNamespacesAreAllowed(@TempDir Path resources) throws IOException {
        write(resources.resolve("assets/pipeworks/models/x.json"),
                "{\"a\":\"pipeworks:block/x\",\"b\":\"minecraft:glass\",\"c\":\"#c:glass_blocks\",\"d\":\"groundworks:x\",\"e\":\"neoforge:y\"}");
        assertEquals(List.of(), PlaysAlone.otherModulesIds(resources));
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
