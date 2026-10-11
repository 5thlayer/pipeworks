// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.List;

import io.github._5thlayer.pipeworks.PipeworksRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** The pipe and the storage tank are craftable by hand on a server with nothing else installed (FactoryWorks ADR-0127). */
final class RecipeTests {

    private RecipeTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("the_pipe_has_a_crafting_recipe", 1, helper -> crafts(helper, PipeworksRegistries.PIPE_ITEM.get()));
        tests.test("the_storage_tank_has_a_crafting_recipe", 1, helper -> crafts(helper, PipeworksRegistries.STORAGE_TANK_ITEM.get()));
    }

    private static void crafts(GameTestHelper helper, Item item) {
        for (RecipeHolder<?> holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            if (holder.value() instanceof ShapedRecipe recipe) {
                CraftingInput grid = filled(recipe);
                if (recipe.matches(grid, helper.getLevel()) && recipe.assemble(grid).is(item)) {
                    helper.succeed();
                    return;
                }
            }
        }
        helper.fail("no crafting recipe makes " + BuiltInRegistries.ITEM.getKey(item));
    }

    private static CraftingInput filled(ShapedRecipe recipe) {
        List<ItemStack> slots = recipe.pattern.ingredients().stream()
                .map(slot -> slot.flatMap(ingredient -> ingredient.items().findFirst()).map(ItemStack::new).orElse(ItemStack.EMPTY))
                .toList();
        return CraftingInput.of(recipe.getWidth(), recipe.getHeight(), slots);
    }
}
