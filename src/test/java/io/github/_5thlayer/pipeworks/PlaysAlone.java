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

    private static final Pattern DEPENDENCY_MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern DEPENDENCY_TYPE = Pattern.compile("(?m)^\\s*type\\s*=\\s*\"([^\"]+)\"");

    private PlaysAlone() {
    }

    /** Each id in another Module's namespace that a file under {@code root} names, a structure's saved block ids included. */
    static List<String> otherModulesIds(Path root) throws IOException {
        // No word boundary before the namespace: in a structure's NBT a string's length byte can be a letter.
        Pattern ids = Pattern.compile("(" + String.join("|", forbidden()) + "):[a-z0-9_./-]+");
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

    /** Each string in the Java source under {@code code} that is another Module's namespace, as an id built in code names it. */
    static List<String> otherModulesNamespaceStrings(Path code) throws IOException {
        Pattern namespaces = Pattern.compile("\"(" + String.join("|", forbidden()) + ")\"");
        List<String> found = new ArrayList<>();
        for (Path file : files(code)) {
            if (!file.toString().endsWith(".java")) {
                continue;
            }
            Matcher namespace = namespaces.matcher(Files.readString(file));
            while (namespace.find()) {
                found.add(relative(code, file) + " names " + namespace.group());
            }
        }
        return found;
    }

    /** Each other Module that the mods.toml under {@code resources} requires; an optional dependency is allowed. */
    static List<String> otherModulesRequired(Path resources) throws IOException {
        Path toml = resources.resolve("META-INF/neoforge.mods.toml");
        List<String> found = new ArrayList<>();
        if (!Files.isRegularFile(toml)) {
            return found;
        }
        for (String block : Files.readString(toml).split("(?m)^(?=\\[)")) {
            Matcher modId = DEPENDENCY_MOD_ID.matcher(block);
            Matcher type = DEPENDENCY_TYPE.matcher(block);
            // NeoForge reads a dependency with no type as required.
            boolean required = !type.find() || type.group(1).equalsIgnoreCase("required");
            if (block.startsWith("[[dependencies.") && modId.find() && forbidden().contains(modId.group(1)) && required) {
                found.add(relative(resources, toml) + " requires " + modId.group(1));
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
