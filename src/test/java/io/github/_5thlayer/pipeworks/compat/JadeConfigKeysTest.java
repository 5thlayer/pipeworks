// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.pipeworks.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Every config option the Jade plugin registers has its {@code config.jade.plugin_pipeworks.<path>} line in the
 * lang file. Jade asks for each in a dev client the first time the title screen opens, and throws on one that is
 * missing (#25). Jade makes an option of a component provider's uid and of an {@code addConfig}, not of a server
 * data or extension provider's. The plugin needs Minecraft and Jade to load, so this reads its source.
 */
class JadeConfigKeysTest {

    // Gradle runs the tests from the project directory.
    private static final Path PLUGIN = Path.of("src/main/java/io/github/_5thlayer/pipeworks/compat/PipeworksJadePlugin.java");
    private static final Path LANG = Path.of("src/main/resources/assets/pipeworks/lang/en_us.json");

    /** Jade's lang key for an option is this and the option's path. */
    private static final String KEY = "config.jade.plugin_pipeworks.";

    private static final Pattern IDENTIFIER = Pattern.compile(
            "Identifier\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*Identifier\\.fromNamespaceAndPath\\(\\s*Pipeworks\\.MOD_ID\\s*,\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern COMPONENT = Pattern.compile("new\\s+I(Block|Entity)?ComponentProvider(<[^>]*>)?\\(\\)\\s*\\{");
    private static final Pattern UID = Pattern.compile("getUid\\(\\)\\s*\\{\\s*return\\s+([A-Z][A-Z0-9_]*)\\s*;");
    private static final Pattern ADD_CONFIG = Pattern.compile("addConfig\\(\\s*([A-Z][A-Z0-9_]*)\\s*,");
    private static final Pattern PROVIDER = Pattern.compile("new\\s+I[A-Za-z]*Provider\\b");
    private static final Pattern REGISTERED = Pattern.compile("\\.(register(Block|Entity)(Component|Icon)|addConfig)\\(\\s*([A-Za-z_][A-Za-z0-9_]*)");

    @Test
    void everyOptionThePluginRegistersHasItsLangLine() throws IOException {
        String plugin = Files.readString(PLUGIN);
        // A provider written in a shape this reading misses would otherwise pass unseen.
        assertEquals(registrations(plugin), options(plugin).size(), "options read: " + options(plugin));
        assertEquals(List.of(), untranslatedOptions(plugin, Files.readString(LANG)));
    }

    @Test
    void aComponentOrAddedOptionIsReportedUntilTheLangNamesIt() {
        String plugin = """
                Identifier SEGMENT = Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "segment");
                Identifier SHOWN = Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "shown");
                Identifier HIDDEN = Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "hidden");
                Identifier ADDED = Identifier.fromNamespaceAndPath(Pipeworks.MOD_ID, "added");
                IServerExtensionProvider<FluidView.Data> FLUID = new IServerExtensionProvider<>() {
                    public Identifier getUid() { return SEGMENT; } };
                IBlockComponentProvider SHOWN_LINE = new IBlockComponentProvider() {
                    public Identifier getUid() { return SHOWN; } };
                IEntityComponentProvider HIDDEN_LINE = new IEntityComponentProvider() {
                    public Identifier getUid() { return HIDDEN; } };
                void registerClient(IWailaClientRegistration registration) { registration.addConfig(ADDED, true); }
                """;
        assertEquals(List.of(KEY + "added", KEY + "hidden"), untranslatedOptions(plugin, "{ \"" + KEY + "shown\": \"Shown\" }"));
    }

    /** The lang key of each option {@code plugin} registers that {@code lang} does not name. */
    static List<String> untranslatedOptions(String plugin, String lang) {
        return options(plugin).stream().filter(key -> !lang.contains("\"" + key + "\"")).sorted().toList();
    }

    /**
     * The lang key of each option {@code plugin} declares: a component provider's uid, or an added config. A uid
     * whose identifier this reading cannot find is named as such, which no lang file names.
     */
    static List<String> options(String plugin) {
        Map<String, String> paths = IDENTIFIER.matcher(plugin).results()
                .collect(Collectors.toMap(m -> m.group(1), m -> m.group(2)));
        List<String> options = new ArrayList<>();
        Matcher component = COMPONENT.matcher(plugin);
        while (component.find()) {
            Matcher next = PROVIDER.matcher(plugin);
            Matcher uid = UID.matcher(plugin).region(component.end(), next.find(component.end()) ? next.start() : plugin.length());
            if (uid.find()) {
                options.add(uid.group(1));
            }
        }
        ADD_CONFIG.matcher(plugin).results().forEach(m -> options.add(m.group(1)));
        return options.stream().distinct()
                .map(option -> paths.containsKey(option) ? KEY + paths.get(option) : option + " has no Pipeworks identifier read for it")
                .toList();
    }

    /** How many distinct providers and options {@code plugin} hands Jade's client registration as config. */
    static long registrations(String plugin) {
        return REGISTERED.matcher(plugin).results().map(m -> m.group(4)).distinct().count();
    }
}
