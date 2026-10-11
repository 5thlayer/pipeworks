// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Scans a mod's shipped JSON data for what stops it playing alone on Groundworks (ADR-0127). */
final class PlaysAlone {

    private static final Set<String> SUITE = Set.of("beltworks", "pipeworks", "wireworks", "craftworks", "fieldworks", "factoryworks");

    /**
     * The {@code c:} tags NeoForge ships with a vanilla member. The data cannot resolve a {@code c:} tag
     * without the game, so a tag outside this list is reported until a game test resolves it.
     */
    private static final Set<String> VANILLA_COMMON_TAGS = Set.of(
            "c:ingots/iron", "c:ingots/copper", "c:ingots/gold", "c:storage_blocks/iron", "c:storage_blocks/redstone",
            "c:dusts/redstone", "c:glass_blocks");

    private static final Pattern STRING_ID = Pattern.compile("\"(#?)([a-z0-9_.-]+):([a-z0-9_./-]+)\"");
    private static final Pattern TAG_FIELD = Pattern.compile("\"tag\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");

    private PlaysAlone() {
    }

    static List<String> violations(Path data, String own) throws IOException {
        List<String> found = new ArrayList<>();
        if (!Files.isDirectory(data)) {
            return found;
        }
        try (Stream<Path> namespaces = Files.list(data)) {
            namespaces.map(p -> p.getFileName().toString())
                    .filter(ns -> SUITE.contains(ns) && !ns.equals(own))
                    .forEach(ns -> found.add("ships data in the namespace " + ns));
        }
        try (Stream<Path> files = Files.walk(data)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                String text = Files.readString(file);
                Matcher ids = STRING_ID.matcher(text);
                while (ids.find()) {
                    String ns = ids.group(2);
                    if (SUITE.contains(ns) && !ns.equals(own)) {
                        found.add(data.relativize(file) + " names " + ns + ":" + ids.group(3));
                    }
                }
            }
        }
        Path recipes = data.resolve(own).resolve("recipe");
        if (Files.isDirectory(recipes)) {
            try (Stream<Path> files = Files.walk(recipes)) {
                for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                    String text = Files.readString(file);
                    Set<String> tags = new HashSet<>();
                    Matcher ids = STRING_ID.matcher(text);
                    while (ids.find()) {
                        if (!ids.group(1).isEmpty()) {
                            tags.add(ids.group(2) + ":" + ids.group(3));
                        }
                    }
                    Matcher fields = TAG_FIELD.matcher(text);
                    while (fields.find()) {
                        tags.add(fields.group(1));
                    }
                    for (String tag : tags) {
                        if (!hasVanillaMember(data, tag, own, new HashSet<>())) {
                            found.add(data.relativize(file) + " consumes " + tag + ", which has no vanilla member");
                        }
                    }
                }
            }
        }
        return found;
    }

    private static boolean hasVanillaMember(Path data, String tag, String own, Set<String> seen) {
        String ns = tag.substring(0, tag.indexOf(':'));
        String path = tag.substring(tag.indexOf(':') + 1);
        if (ns.equals("minecraft")) {
            return true;
        }
        if (ns.equals("c")) {
            return VANILLA_COMMON_TAGS.contains(tag);
        }
        if (!ns.equals(own) || !seen.add(tag)) {
            return false;
        }
        Path file = data.resolve(ns).resolve("tags/item").resolve(path + ".json");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            Matcher members = STRING_ID.matcher(Files.readString(file));
            while (members.find()) {
                String member = members.group(2) + ":" + members.group(3);
                if (members.group(1).isEmpty() ? members.group(2).equals("minecraft") : hasVanillaMember(data, member, own, seen)) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }
}
