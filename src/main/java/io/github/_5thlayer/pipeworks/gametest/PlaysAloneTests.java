// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.util.List;
import java.util.Set;

import io.github._5thlayer.pipeworks.Pipeworks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;

/** The Mod plays alone on Groundworks (ADR-0127): nothing else is loaded beside it. */
final class PlaysAloneTests {

    private static final Set<String> BASE = Set.of("minecraft", "neoforge", "groundworks", Pipeworks.MOD_ID);

    private PlaysAloneTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("only_groundworks_is_loaded_beside_the_mod", 1, PlaysAloneTests::onlyGroundworks);
    }

    private static void onlyGroundworks(GameTestHelper helper) {
        List<String> extra = ModList.get().getMods().stream().map(m -> m.getModId()).filter(id -> !BASE.contains(id)).toList();
        helper.assertTrue(extra.isEmpty(), "the run loads " + extra + " beside Groundworks");
        helper.succeed();
    }
}
