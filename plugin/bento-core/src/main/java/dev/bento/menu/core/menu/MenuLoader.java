package dev.bento.menu.core.menu;

import dev.bento.menu.api.v1.Layout;
import dev.bento.menu.api.v1.MenuDefinition;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.SortMode;
import dev.bento.menu.core.action.ActionParser;
import dev.bento.menu.core.condition.ConditionParser;
import dev.bento.menu.core.condition.Materials;
import dev.bento.menu.core.config.ConfigView;
import dev.bento.menu.core.data.DataSourceRegistry;
import dev.bento.menu.core.item.ItemParser;
import dev.bento.menu.core.layout.LayoutLoader;
import dev.bento.menu.core.report.ValidationReport;
import dev.bento.menu.core.util.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads a menu YAML file into a validated definition plus lazy data-source
 * binding.
 */
public final class MenuLoader {

    public record LoadedMenu(String id, MenuDefinition definition, Layout layout,
                             String sourceId, ConfigView sourceConfig, String file) {
    }

    private MenuLoader() {
    }

    public static LoadedMenu load(File file, LayoutRegistry layouts, ItemParser itemParser,
                                  ActionParser actionParser, ConditionParser conditionParser,
                                  DataSourceRegistry dataSources, ValidationReport report) {
        String fileName = file.getName();
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (file.length() > 0 && config.getKeys(false).isEmpty()) {
            report.error(fileName, "", "File is empty or could not be parsed");
            return null;
        }
        if (!config.getBoolean("enabled", true)) {
            return null;
        }
        String id = config.getString("id", stripExtension(fileName));
        if (id == null || id.isBlank()) {
            report.error(fileName, "id", "Menu id must not be blank");
            return null;
        }
        int fallbackRows = Math.max(1, Math.min(6, config.getInt("rows", 3)));

        Layout layout = null;
        String layoutId = null;
        Object layoutRaw = config.get("layout");
        if (layoutRaw instanceof String value) {
            layoutId = value;
            layout = layouts.get(value).orElse(null);
            if (layout == null) {
                report.error(fileName, "layout", "Unknown layout id: " + value
                        + "; using a generated layout until reload");
                layout = AutoLayouts.bordered(fallbackRows);
                layoutId = null;
            }
        } else if (layoutRaw instanceof ConfigurationSection section) {
            layout = LayoutLoader.parse(section, fileName, report);
        }
        if (layout == null) {
            layout = AutoLayouts.bordered(fallbackRows);
        }
        int rows = layout.rows();
        if (config.contains("rows") && config.getInt("rows") != rows) {
            report.warning(fileName, "rows", "rows=" + config.getInt("rows")
                    + " differs from layout rows=" + rows + "; using layout rows");
        }

        MenuBuilderImpl builder = new MenuBuilderImpl(id);
        builder.title(config.getString("title", id));
        builder.rows(rows);
        if (layoutId != null) {
            builder.layoutId(layoutId);
        } else {
            builder.layout(layout);
        }
        String permission = config.getString("permission");
        if (permission != null && !permission.isBlank()) {
            builder.permission(permission);
        }
        String theme = config.getString("theme");
        if (theme != null && !theme.isBlank()) {
            builder.forcedTheme(theme);
        }

        parseStaticItems(config, builder, itemParser, report, fileName);
        parseComponents(config, builder, itemParser, report, fileName);

        ConfigurationSection data = config.getConfigurationSection("data");
        List<?> rawEntries = data != null && data.contains("entries")
                ? data.getList("entries")
                : config.getList("entries");
        String sourceId = data == null ? null : data.getString("source");
        if (sourceId == null || sourceId.isBlank()) {
            sourceId = "yaml";
        }
        boolean customSource = !sourceId.equalsIgnoreCase("yaml");
        if (customSource && rawEntries != null && !rawEntries.isEmpty()) {
            report.warning(fileName, "data.entries",
                    "entries are ignored because data.source is " + sourceId);
        }
        if (!customSource) {
            builder.entries(parseEntries(rawEntries, itemParser, report, fileName));
        } else {
            builder.dataSource(null);
        }
        ConfigView sourceConfig = data == null
                ? ConfigView.of(Map.of("source", sourceId))
                : ConfigView.of(data);

        boolean hasSearchSlots = !layout.slotsOf(Layout.RegionType.SEARCH).isEmpty();
        boolean hasCategorySlots = !layout.slotsOf(Layout.RegionType.CATEGORY).isEmpty();
        boolean search = data == null ? hasSearchSlots : data.getBoolean("search", hasSearchSlots);
        builder.searchEnabled(search);

        boolean categories = data == null
                ? hasCategorySlots
                : data.getBoolean("categories", hasCategorySlots);
        ConfigurationSection categoriesSection = data == null ? null
                : data.getConfigurationSection("categories");
        if (categoriesSection != null) {
            categories = categoriesSection.getBoolean("enabled", true);
            List<String> order = categoriesSection.getStringList("order");
            if (!order.isEmpty()) {
                builder.categoryOrder(order);
            }
            ConfigurationSection names = categoriesSection.getConfigurationSection("names");
            if (names != null) {
                Map<String, String> display = new LinkedHashMap<>();
                for (String key : names.getKeys(false)) {
                    display.put(key, names.getString(key));
                }
                builder.categoryNames(display);
            }
        }
        builder.categoriesEnabled(categories);

        int pageSize = data == null ? config.getInt("page-size", 0) : data.getInt("page-size", 0);
        if (pageSize > 0) {
            builder.pageSize(pageSize);
        }
        String sortRaw = data == null ? config.getString("sort") : data.getString("sort", config.getString("sort"));
        builder.sortMode(parseSort(sortRaw));
        String refreshRaw = data == null ? config.getString("refresh") : data.getString("refresh");
        Duration refresh = Durations.parse(refreshRaw, Duration.ZERO);
        builder.refreshInterval(refresh);

        List<dev.bento.menu.api.v1.action.Action> open = actionParser.parseFromView(
                ConfigView.of(config), "actions.open", report, fileName);
        if (open.isEmpty()) {
            open = actionParser.parse(config, "open-actions", report, fileName);
        }
        builder.openActions(open);
        List<dev.bento.menu.api.v1.action.Action> close = actionParser.parseFromView(
                ConfigView.of(config), "actions.close", report, fileName);
        if (close.isEmpty()) {
            close = actionParser.parse(config, "close-actions", report, fileName);
        }
        builder.closeActions(close);

        MenuDefinition definition;
        try {
            definition = builder.build();
        } catch (RuntimeException exception) {
            report.error(fileName, "", "Could not build menu: " + exception.getMessage());
            return null;
        }
        return new LoadedMenu(id, definition, layout, customSource ? sourceId : null,
                sourceConfig, fileName);
    }

    private static void parseStaticItems(FileConfiguration config, MenuBuilderImpl builder,
                                         ItemParser itemParser, ValidationReport report, String file) {
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                Object raw = items.get(key);
                if (raw instanceof ConfigurationSection section) {
                    int slot = section.getInt("slot", -1);
                    if (slot < 0) {
                        report.error(file, "items." + key, "Static item needs a slot");
                        continue;
                    }
                    MenuItem item = itemParser.parse(ConfigView.of(section), report, file);
                    if (item != null) {
                        builder.item(slot, item);
                    }
                }
            }
            return;
        }
        List<Map<?, ?>> list = config.getMapList("items");
        for (Map<?, ?> map : list) {
            ConfigView view = ConfigView.of(ConfigView.toStringMap(map));
            int slot = view.getInt("slot", -1);
            if (slot < 0) {
                report.error(file, "items", "Static item needs a slot");
                continue;
            }
            MenuItem item = itemParser.parse(view, report, file);
            if (item != null) {
                builder.item(slot, item);
            }
        }
    }

    private static void parseComponents(FileConfiguration config, MenuBuilderImpl builder,
                                        ItemParser itemParser, ValidationReport report, String file) {
        ConfigurationSection components = config.getConfigurationSection("components");
        if (components == null) {
            return;
        }
        for (String key : components.getKeys(false)) {
            Object raw = components.get(key);
            ConfigView view = null;
            if (raw instanceof ConfigurationSection section) {
                ConfigurationSection itemSection = section.getConfigurationSection("item");
                view = ConfigView.of(itemSection == null ? section : itemSection);
            } else if (raw instanceof Map<?, ?> map) {
                view = ConfigView.of(ConfigView.toStringMap(map));
            } else if (raw instanceof String materialName) {
                Material material = Materials.match(materialName);
                if (material == null) {
                    report.warning(file, "components." + key, "Unknown material: " + materialName);
                    continue;
                }
                builder.component(key, MenuItem.builder(material).build());
                continue;
            }
            if (view == null) {
                report.warning(file, "components." + key, "Component must be a material or item map");
                continue;
            }
            MenuItem item = itemParser.parse(view, report, file);
            if (item != null) {
                builder.component(key, item);
            }
        }
    }

    private static List<MenuEntry> parseEntries(List<?> rawEntries, ItemParser itemParser,
                                                ValidationReport report, String file) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return List.of();
        }
        List<MenuEntry> out = new ArrayList<>();
        for (Object raw : rawEntries) {
            ConfigView view;
            if (raw instanceof ConfigurationSection section) {
                view = ConfigView.of(section);
            } else if (raw instanceof Map<?, ?> map) {
                view = ConfigView.of(ConfigView.toStringMap(map));
            } else {
                report.warning(file, "data.entries", "Entry must be a map");
                continue;
            }
            String id = view.getString("id");
            if (id == null || id.isBlank()) {
                report.error(file, "data.entries", "Entry has no id");
                continue;
            }
            MenuItem item = itemParser.parse(view, report, file);
            if (item == null) {
                continue;
            }
            MenuEntry.Builder entry = MenuEntry.builder(id, item);
            String category = view.getString("category");
            if (category != null) {
                entry.category(category);
            }
            List<String> tags = view.getStringList("tags");
            if (!tags.isEmpty()) {
                entry.tags(tags);
            }
            String searchText = view.getString("search-text");
            if (searchText != null) {
                entry.searchText(searchText);
            }
            Object price = view.has("price") ? view.rawAt("price") : null;
            if (price != null) {
                entry.metadata("price", price);
            }
            ConfigView metadata = view.child("metadata");
            if (metadata != null) {
                metadata.values().forEach(entry::metadata);
            }
            out.add(entry.build());
        }
        return List.copyOf(out);
    }

    private static SortMode parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return SortMode.NONE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("PRICE")) {
            return SortMode.PRICE_ASC;
        }
        try {
            return SortMode.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return SortMode.NONE;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
