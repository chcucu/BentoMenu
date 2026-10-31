package dev.bento.menu.core.theme;

import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.core.condition.Materials;
import dev.bento.menu.core.config.ConfigView;
import dev.bento.menu.core.item.ItemParser;
import dev.bento.menu.core.report.ValidationReport;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

/**
 * Parses theme YAML files.
 */
public final class ThemeLoader {

    private ThemeLoader() {
    }

    public static Theme parse(String id, FileConfiguration config, ItemParser itemParser,
                              ValidationReport report, String file) {
        Theme.Builder builder = Theme.builder(id, config.getString("display-name", id));
        builder.description(config.getString("description"));
        String permission = config.getString("permission");
        if (permission != null && !permission.isBlank()) {
            builder.permission(permission);
        }
        ConfigurationSection colors = config.getConfigurationSection("colors");
        if (colors != null) {
            for (String key : colors.getKeys(false)) {
                String value = colors.getString(key);
                if (value != null) {
                    if (dev.bento.menu.core.util.HexColors.parse(value) == null) {
                        report.warning(file, "colors." + key, "Colour must be #RRGGBB: " + value);
                    }
                    builder.color(key, value);
                }
            }
        }
        List<String> gradient = config.getStringList("title.gradient");
        if (!gradient.isEmpty()) {
            builder.titleGradient(gradient);
        }
        String borderStyle = config.getString("border.style", "gradient");
        try {
            builder.borderStyle(Theme.BorderStyle.valueOf(borderStyle.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            report.warning(file, "border.style", "Unknown border style: " + borderStyle);
        }
        String material = config.getString("border.material");
        if (material != null && !material.isBlank()) {
            if (Materials.match(material) == null) {
                report.warning(file, "border.material", "Unknown material: " + material);
            }
            builder.borderMaterial(material);
        }
        if (config.contains("border.custom-model-data")) {
            builder.borderCustomModelData(config.getInt("border.custom-model-data"));
        }
        String packMaterial = config.getString("border.resource-pack-material");
        if (packMaterial != null && !packMaterial.isBlank()) {
            if (Materials.match(packMaterial) == null) {
                report.warning(file, "border.resource-pack-material", "Unknown material: " + packMaterial);
            }
            builder.resourcePackBorder(packMaterial, config.contains("border.resource-pack-model-data")
                    ? config.getInt("border.resource-pack-model-data") : null);
        }

        ConfigurationSection icons = config.getConfigurationSection("icons");
        if (icons != null) {
            for (String key : icons.getKeys(false)) {
                Object raw = icons.get(key);
                MenuItem item = parseIcon(raw, itemParser, report, file, "icons." + key);
                if (item != null) {
                    builder.icon(key, item);
                }
            }
        }
        ConfigurationSection sounds = config.getConfigurationSection("sounds");
        if (sounds != null) {
            for (String key : sounds.getKeys(false)) {
                String value = sounds.getString(key);
                if (value != null && !value.isBlank()) {
                    builder.sound(key, value);
                }
            }
        }
        return builder.build();
    }

    private static MenuItem parseIcon(Object raw, ItemParser itemParser,
                                      ValidationReport report, String file, String path) {
        if (raw instanceof String materialName) {
            Material material = Materials.match(materialName);
            if (material == null) {
                report.warning(file, path, "Unknown icon material: " + materialName);
                return null;
            }
            return MenuItem.builder(material).build();
        }
        if (raw instanceof ConfigurationSection section) {
            return itemParser.parse(ConfigView.of(section), report, file);
        }
        if (raw instanceof Map<?, ?> map) {
            return itemParser.parse(ConfigView.of(ConfigView.toStringMap(map)), report, file);
        }
        report.warning(file, path, "Icon must be a material or an item map");
        return null;
    }
}
