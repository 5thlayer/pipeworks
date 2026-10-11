// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Scans what a Library ships for another suite mod's namespace, which stops it playing alone on Groundworks (ADR-0127). */
final class PlaysAlone {

    private static final Set<String> SUITE = Set.of("beltworks", "pipeworks", "wireworks", "craftworks", "fieldworks", "factoryworks");

    private PlaysAlone() {
    }

    /** Each literal {@code namespace:path} id of another suite mod in a file under {@code root}. */
    static List<String> foreignIds(Path root, String own) throws IOException {
        String others = SUITE.stream().filter(ns -> !ns.equals(own)).sorted().collect(Collectors.joining("|"));
        Pattern id = Pattern.compile("\\b(" + others + "):[a-z0-9_./-]+");
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                // Latin-1 maps every byte to a char, so a binary file still reads and an id in it still matches.
                Matcher ids = id.matcher(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
                while (ids.find()) {
                    found.add(root.relativize(file).toString().replace('\\', '/') + " names " + ids.group());
                }
            }
        }
        return found;
    }

    /** Each {@code data/} or {@code assets/} folder under {@code resources} in another suite mod's namespace. */
    static List<String> foreignNamespaces(Path resources, String own) throws IOException {
        List<String> found = new ArrayList<>();
        for (String kind : List.of("data", "assets")) {
            Path dir = resources.resolve(kind);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> namespaces = Files.list(dir)) {
                namespaces.map(p -> p.getFileName().toString()).filter(ns -> SUITE.contains(ns) && !ns.equals(own)).sorted()
                        .forEach(ns -> found.add(kind + "/" + ns));
            }
        }
        return found;
    }
}
