// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The pipe and the storage tank are craftable alone, by a vanilla recipe type (ADR-0127). */
class SurvivalRecipesTest {

    @ParameterizedTest
    @ValueSource(strings = {"pipe", "storage_tank"})
    void theBlockHasAVanillaCraftingRecipe(String name) throws IOException {
        Path recipe = Path.of("src/main/resources/data/pipeworks/recipe", name + ".json");
        assertTrue(Files.isRegularFile(recipe), recipe + " is missing");
        String json = Files.readString(recipe);
        assertTrue(json.contains("\"type\": \"minecraft:crafting_shaped\""), json);
        assertTrue(json.contains("\"id\": \"pipeworks:" + name + "\""), json);
    }
}
