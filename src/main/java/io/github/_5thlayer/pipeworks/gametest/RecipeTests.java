// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import io.github._5thlayer.pipeworks.PipeworksRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** The pipe and the storage tank are craftable by hand on a server with nothing else installed (ADR-0127). */
final class RecipeTests {

    private RecipeTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("the_pipe_has_a_crafting_recipe", 1, helper -> hasRecipe(helper, PipeworksRegistries.PIPE_ITEM.get()));
        tests.test("the_storage_tank_has_a_crafting_recipe", 1, helper -> hasRecipe(helper, PipeworksRegistries.STORAGE_TANK_ITEM.get()));
    }

    private static void hasRecipe(GameTestHelper helper, Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        for (RecipeHolder<?> holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            if (holder.id().identifier().equals(id) && holder.value() instanceof CraftingRecipe) {
                helper.succeed();
                return;
            }
        }
        helper.fail("no crafting recipe for " + id);
    }
}
