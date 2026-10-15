// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.List;

import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.libworks.testkit.WorksTabCheck;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Every item the Module registers shows in the suite's shared Works tab, in order, and in no other tab, and the
 * Module keeps no tab of its own (FactoryWorks ADR-0128, #17).
 */
final class CreativeTabTests {

    private static final List<String> WORKS_TAB_ORDER = List.of(
            "pipe", "storage_tank", "pump", "outlet", "creative_pipe");

    private CreativeTabTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("the_works_tab_holds_every_item_in_order", 1,
                helper -> WorksTabCheck.inOrder(helper, Pipeworks.MOD_ID, WORKS_TAB_ORDER));
        tests.test("no_other_tab_holds_a_library_item", 1,
                helper -> WorksTabCheck.noOtherTab(helper, Pipeworks.MOD_ID));
        tests.test("the_library_registers_no_creative_tab", 1, CreativeTabTests::noOwnTab);
    }

    private static void noOwnTab(GameTestHelper helper) {
        var own = BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream()
                .filter(id -> id.getNamespace().equals(Pipeworks.MOD_ID))
                .toList();
        if (!own.isEmpty()) {
            helper.fail("the Module registers its own creative tab: " + own);
            return;
        }
        helper.succeed();
    }
}
