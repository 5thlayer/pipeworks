// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github._5thlayer.pipeworks.Pipeworks;
import io.github._5thlayer.pipeworks.api.FluidPipes;
import io.github._5thlayer.pipeworks.dismantle.PipeFamily;
import io.github._5thlayer.pipeworks.pump.PumpBlockEntity;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.ModList;

/**
 * The Library plays alone on Groundworks (FactoryWorks ADR-0127): nothing else is loaded beside it but the
 * libworks runtime it nests, and every tag its code
 * reads or its data names holds an entry of Minecraft or Pipeworks.
 */
final class PlaysAloneTests {

    private static final Set<String> LOADED_ALONE = Set.of("minecraft", "neoforge", "groundworks", "libworks", Pipeworks.MOD_ID);

    private static final Set<String> FILLS_ALONE = Set.of("minecraft", Pipeworks.MOD_ID);

    private static final Pattern HASHED_TAG = Pattern.compile("\"#([a-z0-9_.-]+:[a-z0-9_./-]+)\"");
    private static final Pattern TAG_FIELD = Pattern.compile("\"tag\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");

    // A tag entry marked "required": false, such as the wrenches Pipeworks offers Groundworks' dismantles, adds what a
    // pack may fill and consumes nothing alone.
    private static final Pattern OPTIONAL_ENTRY = Pattern.compile(
            "\\{\\s*(\"id\"\\s*:\\s*\"#[^\"]+\"\\s*,\\s*\"required\"\\s*:\\s*false|\"required\"\\s*:\\s*false\\s*,\\s*\"id\"\\s*:\\s*\"#[^\"]+\")\\s*}");

    private PlaysAloneTests() {
    }

    static void register(PipeworksGameTests.Registrar tests) {
        tests.test("only_groundworks_is_loaded_beside_the_library", 1, PlaysAloneTests::onlyGroundworks);
        tests.test("every_tag_pipeworks_reads_holds_an_entry_of_minecraft_or_pipeworks", 1, PlaysAloneTests::tagsPlayAlone);
        tests.test("every_tag_the_librarys_data_names_holds_an_entry_of_minecraft_or_pipeworks", 1, PlaysAloneTests::dataTagsFillAlone);
    }

    private static void onlyGroundworks(GameTestHelper helper) {
        List<String> extra = ModList.get().getMods().stream().map(m -> m.getModId()).filter(id -> !LOADED_ALONE.contains(id)).toList();
        helper.assertTrue(extra.isEmpty(), "the run loads " + extra + " beside Groundworks");
        helper.succeed();
    }

    private static void tagsPlayAlone(GameTestHelper helper) {
        List<TagKey<?>> consumed = List.of(FluidPipes.CLOSES_SIDES, PumpBlockEntity.INFINITE_SOURCES, PipeFamily.PIPES);
        var registries = helper.getLevel().registryAccess();
        for (TagKey<?> tag : consumed) {
            helper.assertTrue(fillsAlone(registries.lookupOrThrow(tag.registry()), tag.location()), tag.location() + " holds no entry of " + FILLS_ALONE);
        }
        helper.succeed();
    }

    /**
     * A recipe that names a tag no mod fills does not load, and one whose tag holds only another mod's items is
     * dropped as unplaceable, so neither reaches the recipe manager: the tags are read from the jar's data.
     */
    private static void dataTagsFillAlone(GameTestHelper helper) {
        Set<Identifier> tags = new TreeSet<>();
        try {
            for (Path root : ModList.get().getModFileById(Pipeworks.MOD_ID).getFile().getContents().getContentRoots()) {
                Path data = root.resolve("data");
                if (!Files.isDirectory(data)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(data)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                        String json = OPTIONAL_ENTRY.matcher(Files.readString(file)).replaceAll("");
                        for (Pattern named : List.of(HASHED_TAG, TAG_FIELD)) {
                            named.matcher(json).results().forEach(tag -> tags.add(Identifier.parse(tag.group(1))));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var registries = helper.getLevel().registryAccess();
        List<Identifier> empty = tags.stream()
                .filter(tag -> registries.registries().noneMatch(registry -> fillsAlone(registry.value(), tag))).toList();
        helper.assertTrue(empty.isEmpty(), "these tags hold no entry of " + FILLS_ALONE + ": " + empty);
        helper.succeed();
    }

    private static <T> boolean fillsAlone(Registry<T> registry, Identifier tag) {
        return registry.get(TagKey.create(registry.key(), tag)).stream().flatMap(HolderSet::stream)
                .anyMatch(entry -> entry.unwrapKey().filter(key -> FILLS_ALONE.contains(key.identifier().getNamespace())).isPresent());
    }
}
