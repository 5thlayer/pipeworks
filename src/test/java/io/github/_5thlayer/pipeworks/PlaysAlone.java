// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The tags a Module's code reads, each of which its game test must check for it to play alone on Groundworks
 * (FactoryWorks ADR-0127). That nothing it ships names another Module is libworks-plugin's {@code checkPlaysAlone}
 * (libworks ADR 0005).
 */
final class PlaysAlone {

    private static final Pattern TAG_READ = Pattern.compile("\\b([A-Z][A-Za-z0-9]*)?Tags(\\.(Items|Blocks|Fluids|EntityTypes|Biomes))?\\.[A-Z][A-Z0-9_]*\\b");
    private static final Pattern TAG_CONSTANT = Pattern.compile("TagKey<[^>]*>\\s+([A-Z][A-Z0-9_]*)\\s*=");
    private static final Pattern TAG_CREATED = Pattern.compile("\\b(TagKey|([A-Z][A-Za-z0-9]*)?Tags)\\.create\\(");

    private PlaysAlone() {
    }

    /**
     * Each tag the code under {@code code} reads or declares, outside its game tests, that the source of
     * {@code check} does not name: a constant of a {@code ...Tags} class, a {@code TagKey} constant it
     * declares, or a {@code TagKey} it creates where no constant can name it.
     */
    static List<String> tagsReadButUnchecked(Path code, Path check) throws IOException {
        String checked = Files.readString(check);
        List<String> found = new ArrayList<>();
        for (Path file : files(code)) {
            String path = relative(code, file);
            if (!path.endsWith(".java") || path.contains("/gametest/")) {
                continue;
            }
            String source = Files.readString(file);
            String type = file.getFileName().toString().replace(".java", "");
            TAG_READ.matcher(source).results().map(MatchResult::group).distinct()
                    .filter(tag -> !checked.contains(tag)).forEach(tag -> found.add(path + " reads " + tag));
            List<String> constants = TAG_CONSTANT.matcher(source).results().map(m -> type + "." + m.group(1)).toList();
            constants.stream().filter(tag -> !checked.contains(tag)).forEach(tag -> found.add(path + " declares " + tag));
            if (TAG_CREATED.matcher(source).results().count() > constants.size()) {
                found.add(path + " creates a TagKey outside a constant");
            }
        }
        return found.stream().sorted().toList();
    }

    private static List<Path> files(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
