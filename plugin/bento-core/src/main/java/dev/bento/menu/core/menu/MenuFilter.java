package dev.bento.menu.core.menu;

import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.SortMode;
import dev.bento.menu.core.text.TextEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure filtering/search/sort/pagination pipeline. Safe to execute on worker
 * threads; never touches Bukkit state.
 */
public final class MenuFilter {

    public record Query(int page, int pageSize, String category, String text, SortMode sortMode) {
        public Query {
            page = Math.max(1, page);
            pageSize = Math.max(1, pageSize);
            category = normalizeCategory(category);
            text = text == null ? "" : text.trim();
            sortMode = sortMode == null ? SortMode.NONE : sortMode;
        }
    }

    public record Result(List<MenuEntry> pageEntries, List<MenuEntry> filtered, int page, int pages,
                         int total, String category, String text, List<String> categories,
                         Map<String, Integer> categoryCounts) {
        public boolean isEmpty() {
            return pageEntries.isEmpty();
        }
    }

    private final TextEngine textEngine;

    public MenuFilter(TextEngine textEngine) {
        this.textEngine = textEngine;
    }

    public Result apply(Collection<MenuEntry> allEntries, Query query, List<String> categoryOrder) {
        List<MenuEntry> source = new ArrayList<>(allEntries);
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        for (MenuEntry entry : source) {
            String category = categoryOf(entry);
            if (category != null) {
                categoryCounts.merge(category, 1, Integer::sum);
            }
        }
        List<String> categories = orderedCategories(categoryCounts.keySet(), categoryOrder);

        String queryText = query.text().toLowerCase(Locale.ROOT);
        List<MenuEntry> filtered = new ArrayList<>();
        for (MenuEntry entry : source) {
            if (!matchesCategory(entry, query.category())) {
                continue;
            }
            if (!queryText.isEmpty() && !matchesText(entry, queryText)) {
                continue;
            }
            filtered.add(entry);
        }
        sort(filtered, query.sortMode());

        int pageSize = query.pageSize();
        int pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        int page = Math.min(query.page(), pages);
        int from = (page - 1) * pageSize;
        int to = Math.min(filtered.size(), from + pageSize);
        List<MenuEntry> pageEntries = from >= filtered.size()
                ? List.of()
                : List.copyOf(filtered.subList(from, to));
        return new Result(
                pageEntries,
                List.copyOf(filtered),
                page,
                pages,
                filtered.size(),
                query.category(),
                query.text(),
                categories,
                Map.copyOf(categoryCounts)
        );
    }

    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank() || category.equalsIgnoreCase("all")
                || category.equalsIgnoreCase("全部") || category.equals("*")) {
            return null;
        }
        return category.trim().toLowerCase(Locale.ROOT);
    }

    public static String categoryOf(MenuEntry entry) {
        if (entry.category() != null && !entry.category().isBlank()) {
            return entry.category().trim().toLowerCase(Locale.ROOT);
        }
        for (String tag : entry.tags()) {
            if (tag != null && !tag.isBlank()) {
                return tag.trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private boolean matchesCategory(MenuEntry entry, String category) {
        if (category == null) {
            return true;
        }
        String entryCategory = categoryOf(entry);
        if (category.equals(entryCategory)) {
            return true;
        }
        for (String tag : entry.tags()) {
            if (category.equals(normalizeCategory(tag))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesText(MenuEntry entry, String query) {
        if (contains(entry.id(), query)) {
            return true;
        }
        if (entry.searchText() != null && contains(entry.searchText(), query)) {
            return true;
        }
        MenuItem item = entry.item();
        if (item.name() != null && contains(textEngine.strip(item.name()), query)) {
            return true;
        }
        for (String line : item.lore()) {
            if (contains(textEngine.strip(line), query)) {
                return true;
            }
        }
        for (String tag : entry.tags()) {
            if (contains(tag, query)) {
                return true;
            }
        }
        for (Object value : entry.metadata().values()) {
            if (contains(String.valueOf(value), query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void sort(List<MenuEntry> entries, SortMode mode) {
        Comparator<MenuEntry> comparator = switch (mode) {
            case ID -> Comparator.comparing(MenuEntry::id, String.CASE_INSENSITIVE_ORDER);
            case NAME -> Comparator.comparing(this::plainName, String.CASE_INSENSITIVE_ORDER);
            case PRICE_ASC -> Comparator.comparingDouble(this::price);
            case PRICE_DESC -> Comparator.comparingDouble(this::price).reversed();
            case NONE -> null;
        };
        if (comparator != null) {
            entries.sort(comparator.thenComparing(MenuEntry::id, String.CASE_INSENSITIVE_ORDER));
        }
    }

    private String plainName(MenuEntry entry) {
        String name = entry.item().name();
        return name == null ? entry.id() : textEngine.strip(name);
    }

    private double price(MenuEntry entry) {
        Object value = entry.metadata().get("price");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static List<String> orderedCategories(Set<String> present, List<String> order) {
        List<String> out = new ArrayList<>();
        if (order != null) {
            for (String category : order) {
                String normalized = normalizeCategory(category);
                if (normalized != null && present.contains(normalized) && !out.contains(normalized)) {
                    out.add(normalized);
                }
            }
        }
        List<String> remaining = new ArrayList<>();
        for (String category : present) {
            if (!out.contains(category)) {
                remaining.add(category);
            }
        }
        remaining.sort(String.CASE_INSENSITIVE_ORDER);
        out.addAll(remaining);
        return List.copyOf(out);
    }
}
