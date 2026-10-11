// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/** What a Module ships that stops it playing alone on Groundworks (FactoryWorks ADR-0127), one scan per rule. */
final class PlaysAlone {

    /** The Modules of FactoryWorks' glossary but Pipeworks and Groundworks, which every Module may require. */
    private static final Set<String> OTHER_MODULES = Set.of("beltworks", "craftworks", "fieldworks", "gearworks", "voidworks", "wireworks");

    /** Not a Module since FactoryWorks Core retired (FactoryWorks ADR-0128), and no Module may name it either. */
    private static final String RETIRED_PACK = "factoryworks";

    private PlaysAlone() {
    }

    /** Each id in another Module's namespace that a file under {@code root} names, a structure's saved block ids included. */
    static List<String> otherModulesIds(Path root) throws IOException {
        Pattern ids = Pattern.compile("\\b(" + String.join("|", forbidden()) + "):[a-z0-9_./-]+");
        List<String> found = new ArrayList<>();
        for (Path file : files(root)) {
            Matcher id = ids.matcher(text(file));
            while (id.find()) {
                found.add(relative(root, file) + " names " + id.group());
            }
        }
        return found;
    }

    /** Each {@code data/<namespace>} or {@code assets/<namespace>} folder under {@code resources} that is another Module's. */
    static List<String> otherModulesNamespaces(Path resources) throws IOException {
        List<String> found = new ArrayList<>();
        for (String kind : List.of("assets", "data")) {
            Path dir = resources.resolve(kind);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> namespaces = Files.list(dir)) {
                namespaces.map(p -> p.getFileName().toString()).filter(forbidden()::contains).sorted()
                        .forEach(ns -> found.add(kind + "/" + ns));
            }
        }
        return found;
    }

    private static List<String> forbidden() {
        return Stream.concat(OTHER_MODULES.stream(), Stream.of(RETIRED_PACK)).sorted().toList();
    }

    private static List<Path> files(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String text(Path file) throws IOException {
        if (file.toString().endsWith(".nbt")) {
            try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
                return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
        }
        // Latin-1 maps every byte to a char, so a binary file such as a texture still reads.
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
    }
}
