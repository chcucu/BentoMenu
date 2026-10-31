package dev.bento.menu.bukkit.config;

import dev.bento.menu.api.v1.Layout;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.core.action.ActionParser;
import dev.bento.menu.core.action.ActionRegistry;
import dev.bento.menu.core.condition.ConditionParser;
import dev.bento.menu.core.condition.ConditionRegistry;
import dev.bento.menu.core.config.MessageCatalog;
import dev.bento.menu.core.config.PluginConfig;
import dev.bento.menu.core.data.DataSourceRegistry;
import dev.bento.menu.core.item.ItemParser;
import dev.bento.menu.core.layout.LayoutLoader;
import dev.bento.menu.core.menu.CompiledMenu;
import dev.bento.menu.core.menu.LayoutRegistry;
import dev.bento.menu.core.menu.MenuLoader;
import dev.bento.menu.core.menu.MenuRepository;
import dev.bento.menu.core.report.ValidationReport;
import dev.bento.menu.core.theme.ThemeLoader;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads config.yml, messages, themes, layouts and menus into an immutable
 * snapshot. No player or world state is touched, so this can run off-thread.
 */
public final class ConfigManager {

    public record LoadedConfig(PluginConfig config, MessageCatalog messages,
                               Map<String, Theme> themes, Map<String, Layout> layouts,
                               Map<String, CompiledMenu> menus, ValidationReport report) {
    }

    private final Plugin plugin;
    private final ActionRegistry actions;
    private final ConditionRegistry conditions;
    private final DataSourceRegistry dataSources;

    public ConfigManager(Plugin plugin, ActionRegistry actions,
                         ConditionRegistry conditions, DataSourceRegistry dataSources) {
        this.plugin = plugin;
        this.actions = actions;
        this.conditions = conditions;
        this.dataSources = dataSources;
    }

    public void saveDefaults() {
        for (String path : DefaultResources.FILES) {
            File file = new File(plugin.getDataFolder(), path);
            if (file.exists()) {
                continue;
            }
            try {
                plugin.saveResource(path, false);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not save bundled resource " + path + ": "
                        + exception.getMessage());
            }
        }
    }

    public LoadedConfig load(LoadedConfig previous) {
        ValidationReport report = new ValidationReport();
        FileConfiguration configFile = loadYaml(new File(plugin.getDataFolder(), "config.yml"),
                report, "config.yml");
        PluginConfig config = configFile == null || configFile.getKeys(false).isEmpty()
                ? (previous == null ? PluginConfig.defaults() : previous.config())
                : PluginConfig.load(configFile);
        MessageCatalog messages = loadMessages(config, report);

        ConditionParser conditionParser = new ConditionParser(conditions);
        ActionParser actionParser = new ActionParser(actions, conditionParser);
        ItemParser itemParser = new ItemParser(actionParser, conditionParser);
        actionParser.setItemParser(itemParser);

        Map<String, Layout> layouts = loadLayouts(previous, report);
        Map<String, Theme> themes = loadThemes(previous, itemParser, report);
        Map<String, CompiledMenu> menus = loadMenus(previous, layouts, itemParser,
                actionParser, conditionParser, report);
        return new LoadedConfig(config, messages, themes, layouts, menus, report);
    }

    private MessageCatalog loadMessages(PluginConfig config, ValidationReport report) {
        Map<String, Map<String, String>> bundled = loadBundledMessages();
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();
        bundled.forEach((locale, values) -> merged.put(locale, new LinkedHashMap<>(values)));

        File directory = new File(plugin.getDataFolder(), "messages");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                String locale = MessageCatalog.normalizeLocale(
                        file.getName().substring(0, file.getName().length() - 4));
                FileConfiguration yaml = loadYaml(file, report, "messages/" + file.getName());
                if (yaml == null) {
                    continue;
                }
                Map<String, String> values = merged.computeIfAbsent(locale, ignored -> new LinkedHashMap<>());
                values.putAll(MessageCatalog.flatten(yaml));
            }
        }
        if (merged.isEmpty()) {
            merged.put("zh_CN", new LinkedHashMap<>(Map.of(
                    "prefix", "<gradient:primary:secondary>Bento</gradient> <muted>»</muted> ",
                    "no-permission", "<color:error>你没有权限执行此操作。</color>"
            )));
        }
        String fallback = MessageCatalog.normalizeLocale(config.defaultLanguage());
        if (!merged.containsKey(fallback)) {
            fallback = merged.keySet().iterator().next();
        }
        return new MessageCatalog(merged, fallback);
    }

    private Map<String, Map<String, String>> loadBundledMessages() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (String locale : List.of("zh_CN", "en_US")) {
            String path = "messages/" + locale + ".yml";
            InputStream stream = plugin.getResource(path);
            if (stream == null) {
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                out.put(locale, MessageCatalog.flatten(yaml));
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not read bundled messages " + path + ": "
                        + exception.getMessage());
            }
        }
        return out;
    }

    private Map<String, Layout> loadLayouts(LoadedConfig previous, ValidationReport report) {
        Map<String, Layout> layouts = new LinkedHashMap<>();
        File directory = new File(plugin.getDataFolder(), "layouts");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                String id = stripExtension(file.getName());
                FileConfiguration yaml = loadYaml(file, report, "layouts/" + file.getName());
                Layout layout = yaml == null ? null
                        : LayoutLoader.parse(yaml, "layouts/" + file.getName(), report);
                if (layout == null) {
                    Layout old = previous == null ? null : previous.layouts().get(id);
                    if (old != null) {
                        layouts.put(id, old);
                    }
                    continue;
                }
                if (layouts.put(id, layout) != null) {
                    report.error("layouts/" + file.getName(), "id", "Duplicate layout id: " + id);
                }
            }
        }
        if (previous != null) {
            previous.layouts().forEach((id, layout) -> {
                if (new File(directory, id + ".yml").exists()) {
                    layouts.putIfAbsent(id, layout);
                }
            });
        }
        return layouts;
    }

    private Map<String, Theme> loadThemes(LoadedConfig previous, ItemParser itemParser,
                                          ValidationReport report) {
        Map<String, Theme> themes = new LinkedHashMap<>();
        File directory = new File(plugin.getDataFolder(), "themes");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                String id = stripExtension(file.getName());
                FileConfiguration yaml = loadYaml(file, report, "themes/" + file.getName());
                if (yaml == null) {
                    continue;
                }
                try {
                    Theme theme = ThemeLoader.parse(id, yaml, itemParser, report,
                            "themes/" + file.getName());
                    if (theme != null) {
                        if (themes.put(theme.id(), theme) != null) {
                            report.error("themes/" + file.getName(), "id",
                                    "Duplicate theme id: " + theme.id());
                        }
                    }
                } catch (RuntimeException exception) {
                    report.error("themes/" + file.getName(), "", "Theme failed: " + exception.getMessage());
                }
            }
        }
        if (previous != null) {
            previous.themes().forEach((id, theme) -> {
                if (new File(directory, id + ".yml").exists()) {
                    themes.putIfAbsent(id, theme);
                }
            });
        }
        if (themes.isEmpty()) {
            themes.put("aurora", Theme.builder("aurora", "Aurora").build());
        }
        return themes;
    }

    private Map<String, CompiledMenu> loadMenus(LoadedConfig previous, Map<String, Layout> layouts,
                                                ItemParser itemParser, ActionParser actionParser,
                                                ConditionParser conditionParser, ValidationReport report) {
        LayoutRegistry layoutRegistry = new LayoutRegistry();
        layoutRegistry.replaceAll(layouts);
        Map<String, CompiledMenu> compiled = new LinkedHashMap<>();
        File directory = new File(plugin.getDataFolder(), "menus");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return compiled;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            String guessedId = stripExtension(file.getName());
            int errorsBefore = report.errorCount();
            MenuLoader.LoadedMenu loaded;
            try {
                loaded = MenuLoader.load(file, layoutRegistry, itemParser, actionParser,
                        conditionParser, dataSources, report);
            } catch (RuntimeException exception) {
                report.error("menus/" + file.getName(), "", "Menu failed: " + exception.getMessage());
                loaded = null;
            }
            if (loaded == null) {
                if (report.errorCount() > errorsBefore && previous != null) {
                    CompiledMenu old = previous.menus().get(guessedId);
                    if (old != null) {
                        compiled.put(guessedId, old);
                    }
                }
                continue;
            }
            ValidationReport menuReport = report;
            CompiledMenu menu = CompiledMenu.compile(loaded.definition(), loaded.layout(),
                    loaded.sourceId(), loaded.sourceConfig(), menuReport,
                    loaded.file(), dataSources);
            if (menu == null) {
                if (previous != null) {
                    CompiledMenu old = previous.menus().get(loaded.id());
                    if (old != null) {
                        compiled.put(loaded.id(), old);
                    }
                }
                continue;
            }
            if (compiled.put(loaded.id(), menu) != null) {
                report.error(loaded.file(), "id", "Duplicate menu id: " + loaded.id());
            }
        }
        return compiled;
    }

    private FileConfiguration loadYaml(File file, ValidationReport report, String label) {
        if (!file.exists()) {
            return null;
        }
        try {
            FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (file.length() > 0 && yaml.getKeys(false).isEmpty()) {
                report.error(label, "", "File is empty or could not be parsed");
                return null;
            }
            return yaml;
        } catch (RuntimeException exception) {
            report.error(label, "", "YAML error: " + exception.getMessage());
            return null;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
