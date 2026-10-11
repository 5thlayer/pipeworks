// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.List;
import java.util.Set;

import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.api.FluidPipes;
import io.github._5thlayer.pipeworks.dismantle.PipeFamily;
import io.github._5thlayer.pipeworks.pump.PumpBlockEntity;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

/** The Library plays alone on Groundworks (ADR-0127): nothing else is loaded beside it, and what it consumes needs nothing else. */
final class PlaysAloneTests {

    private static final Set<String> BASE = Set.of("minecraft", "neoforge", "groundworks", Pipeworks.MOD_ID);

    private static final Set<String> ALONE = Set.of("minecraft", Pipeworks.MOD_ID);

    private PlaysAloneTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("only_groundworks_is_loaded_beside_the_library", 1, PlaysAloneTests::onlyGroundworks);
        tests.test("every_tag_pipeworks_reads_holds_an_entry_of_minecraft_or_pipeworks", 1, PlaysAloneTests::tagsPlayAlone);
        tests.test("every_ingredient_of_a_pipeworks_recipe_holds_an_item_of_minecraft_or_pipeworks", 1, PlaysAloneTests::recipesPlayAlone);
    }

    private static void onlyGroundworks(GameTestHelper helper) {
        List<String> extra = ModList.get().getMods().stream().map(m -> m.getModId()).filter(id -> !BASE.contains(id)).toList();
        helper.assertTrue(extra.isEmpty(), "the run loads " + extra + " beside Groundworks");
        helper.succeed();
    }

    /** A tag filled only by other mods would leave the Library inert. */
    private static void tagsPlayAlone(GameTestHelper helper) {
        List<TagKey<?>> read = List.of(FluidPipes.CLOSES_SIDES, PumpBlockEntity.INFINITE_SOURCES, PipeFamily.PIPES);
        for (TagKey<?> tag : read) {
            if (!reachesAlone(helper, tag)) {
                helper.fail(tag.location() + " holds no entry of " + ALONE);
                return;
            }
        }
        helper.succeed();
    }

    /** A recipe's tags are resolved through its ingredients, so a tag no file names is checked too. */
    private static void recipesPlayAlone(GameTestHelper helper) {
        for (RecipeHolder<?> recipe : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            if (!recipe.id().identifier().getNamespace().equals(Pipeworks.MOD_ID)) {
                continue;
            }
            for (Ingredient ingredient : recipe.value().placementInfo().ingredients()) {
                if (ingredient.items().noneMatch(PlaysAloneTests::isAlone)) {
                    helper.fail(recipe.id().identifier() + " has an ingredient with no item of " + ALONE);
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static <T> boolean reachesAlone(GameTestHelper helper, TagKey<T> tag) {
        return helper.getLevel().registryAccess().lookupOrThrow(tag.registry()).get(tag).stream()
                .flatMap(set -> set.stream())
                .anyMatch(PlaysAloneTests::isAlone);
    }

    private static boolean isAlone(Holder<?> holder) {
        return holder.unwrapKey().filter(key -> ALONE.contains(key.identifier().getNamespace())).isPresent();
    }
}
