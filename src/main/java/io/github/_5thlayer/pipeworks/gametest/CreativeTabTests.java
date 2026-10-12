// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.List;

import io.github._5thlayer.libworks.WorksTab;
import io.github._5thlayer.pipeworks.Pipeworks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Every item the Library registers shows in the suite's shared Works tab and in no other tab, and the Library keeps no
 * tab of its own (FactoryWorks ADR-0128, #17).
 */
final class CreativeTabTests {

    private CreativeTabTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("the_works_tab_holds_every_item_the_library_registers", 1, CreativeTabTests::worksTabHoldsEveryItem);
        tests.test("no_other_tab_holds_a_library_item", 1, CreativeTabTests::noOtherTab);
        tests.test("the_library_registers_no_creative_tab", 1, CreativeTabTests::noOwnTab);
    }

    private static void worksTabHoldsEveryItem(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        List<Item> shown = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(WorksTab.KEY).getDisplayItems().stream()
                .map(ItemStack::getItem)
                .toList();
        List<Item> missing = BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Pipeworks.MOD_ID))
                // What only this run registers, such as the test canister, ships in no jar.
                .filter(item -> !BuiltInRegistries.ITEM.getKey(item).getPath().startsWith("gametest_"))
                .filter(item -> !shown.contains(item))
                .toList();
        if (!missing.isEmpty()) {
            helper.fail("the Works tab does not hold " + missing);
            return;
        }
        helper.succeed();
    }

    private static void noOtherTab(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        // Empty tabs would pass the loop below without the Library's items reaching any tab.
        if (ownItems(BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(WorksTab.KEY)).isEmpty()) {
            helper.fail("the Works tab shows no Pipeworks item, so no tab was filled");
            return;
        }
        for (var entry : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            CreativeModeTab tab = entry.getValue();
            // The search tab lists every other tab's items.
            if (entry.getKey() == WorksTab.KEY || tab.getType() == CreativeModeTab.Type.SEARCH) {
                continue;
            }
            List<Item> own = ownItems(tab);
            if (!own.isEmpty()) {
                helper.fail("the tab " + entry.getKey().identifier() + " shows " + own);
                return;
            }
        }
        helper.succeed();
    }

    private static List<Item> ownItems(CreativeModeTab tab) {
        return tab.getDisplayItems().stream()
                .map(ItemStack::getItem)
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Pipeworks.MOD_ID))
                .toList();
    }

    private static void noOwnTab(GameTestHelper helper) {
        var own = BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream()
                .filter(id -> id.getNamespace().equals(Pipeworks.MOD_ID))
                .toList();
        if (!own.isEmpty()) {
            helper.fail("the Library registers its own creative tab: " + own);
            return;
        }
        helper.succeed();
    }
}
