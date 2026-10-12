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
 * The Library plays alone on Groundworks (FactoryWorks ADR-0127): every tag its code reads is checked by
 * {@code PlaysAloneTests} in game, where they resolve.
 * That nothing it ships names another Module is libworks-plugin's {@code checkPlaysAlone} (libworks ADR 0005).
 */
class PlaysAloneTest {

    private static final Path CODE = Path.of("src/main/java");
    private static final Path TAG_CHECK = CODE.resolve("io/github/_5thlayer/pipeworks/gametest/PlaysAloneTests.java");

    /** A tag the code starts reading fails here until {@code PlaysAloneTests} checks it in game. */
    @Test
    void everyTagTheCodeReadsIsCheckedInGame() throws IOException {
        assertEquals(List.of(), PlaysAlone.tagsReadButUnchecked(CODE, TAG_CHECK));
    }

    @Test
    void aTagTheCodeReadsOrDeclaresIsReportedUntilTheCheckNamesIt(@TempDir Path code) throws IOException {
        write(code.resolve("a/Fuel.java"), "class Fuel { static final TagKey<Item> FUEL = ItemTags.create(id(\"fuel\")); }");
        write(code.resolve("a/Burn.java"), "class Burn { boolean b(ItemStack s) { return s.is(ItemTags.LOGS) || s.is(Fuel.FUEL); } }");
        write(code.resolve("a/Mine.java"), "class Mine { boolean m(BlockState s) { return s.is(Tags.Blocks.ORES) && !s.is(BlockTags.LOGS); } }");
        write(code.resolve("a/Flow.java"), "class Flow { boolean f(FluidState s) { return s.is(FluidTags.LAVA); } }");
        write(code.resolve("a/Lazy.java"), "class Lazy { boolean l(ItemStack s) { return s.is(TagKey.create(Registries.ITEM, id(\"x\"))); } }");
        write(code.resolve("a/gametest/Check.java"), "class Check { List<TagKey<?>> checked = List.of(FluidTags.LAVA, BlockTags.LOGS); }");
        assertEquals(List.of("a/Burn.java reads ItemTags.LOGS", "a/Fuel.java declares Fuel.FUEL", "a/Lazy.java creates a TagKey outside a constant",
                "a/Mine.java reads Tags.Blocks.ORES"), PlaysAlone.tagsReadButUnchecked(code, code.resolve("a/gametest/Check.java")));
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
