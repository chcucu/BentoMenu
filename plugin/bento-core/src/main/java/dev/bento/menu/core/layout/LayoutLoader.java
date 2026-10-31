package dev.bento.menu.core.layout;

import dev.bento.menu.api.v1.Layout;
import dev.bento.menu.core.report.ValidationReport;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses reusable layout files (layouts/&lt;id&gt;.yml).
 */
public final class LayoutLoader {

    private static final Map<Character, Layout.RegionType> DEFAULT_REGIONS = Map.ofEntries(
            Map.entry('#', Layout.RegionType.BORDER),
            Map.entry('B', Layout.RegionType.BACKGROUND),
            Map.entry('C', Layout.RegionType.CONTENT),
            Map.entry('S', Layout.RegionType.SEARCH),
            Map.entry('<', Layout.RegionType.PREVIOUS),
            Map.entry('>', Layout.RegionType.NEXT),
            Map.entry('P', Layout.RegionType.PAGE),
            Map.entry('K', Layout.RegionType.CATEGORY),
            Map.entry('X', Layout.RegionType.CLOSE),
            Map.entry('~', Layout.RegionType.BACKGROUND),
            Map.entry('.', Layout.RegionType.BACKGROUND)
    );

    private LayoutLoader() {
    }

    public static Layout parse(ConfigurationSection config, String file, ValidationReport report) {
        List<String> pattern = config.getStringList("pattern");
        if (pattern.isEmpty()) {
            report.error(file, "pattern", "Layout has no pattern");
            return null;
        }
        int rows = config.getInt("rows", pattern.size());
        if (rows < 1 || rows > 6) {
            report.error(file, "rows", "rows must be between 1 and 6");
            return null;
        }
        if (pattern.size() != rows) {
            report.error(file, "pattern", "pattern has " + pattern.size() + " rows but rows=" + rows);
            return null;
        }
        for (int i = 0; i < pattern.size(); i++) {
            if (pattern.get(i).length() != 9) {
                report.error(file, "pattern." + i,
                        "row " + i + " must contain exactly 9 characters");
                return null;
            }
        }
        Map<Character, Layout.RegionType> regions = new LinkedHashMap<>(DEFAULT_REGIONS);
        ConfigurationSection regionSection = config.getConfigurationSection("regions");
        if (regionSection == null) {
            regionSection = config.getConfigurationSection("symbols");
        }
        if (regionSection != null) {
            for (String rawKey : regionSection.getKeys(false)) {
                if (rawKey.isEmpty()) {
                    continue;
                }
                char symbol = rawKey.charAt(0);
                String value = regionSection.getString(rawKey);
                Layout.RegionType type = parseRegion(value);
                if (type == null) {
                    report.error(file, "regions." + rawKey, "Unknown region type: " + value);
                    return null;
                }
                regions.put(symbol, type);
            }
        }
        try {
            Layout.Builder builder = Layout.builder(rows);
            pattern.forEach(builder::row);
            regions.forEach(builder::region);
            return builder.build();
        } catch (RuntimeException exception) {
            report.error(file, "pattern", exception.getMessage());
            return null;
        }
    }

    public static Layout.RegionType parseRegion(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "border", "edge", "frame", "outline" -> Layout.RegionType.BORDER;
            case "background", "bg", "fill", "backdrop" -> Layout.RegionType.BACKGROUND;
            case "content", "contents", "entries", "items", "list" -> Layout.RegionType.CONTENT;
            case "search", "find", "query", "input" -> Layout.RegionType.SEARCH;
            case "previous", "prev", "back", "left" -> Layout.RegionType.PREVIOUS;
            case "next", "forward", "right" -> Layout.RegionType.NEXT;
            case "page", "pages", "indicator", "info" -> Layout.RegionType.PAGE;
            case "category", "categories", "tag", "tabs" -> Layout.RegionType.CATEGORY;
            case "close", "exit", "quit" -> Layout.RegionType.CLOSE;
            case "empty", "none", "blank", "space" -> Layout.RegionType.EMPTY;
            default -> null;
        };
    }
}
