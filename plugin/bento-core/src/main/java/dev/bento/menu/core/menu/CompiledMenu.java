package dev.bento.menu.core.menu;

import dev.bento.menu.api.v1.Layout;
import dev.bento.menu.api.v1.MenuDefinition;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.SortMode;
import dev.bento.menu.api.v1.data.DataSource;
import dev.bento.menu.core.config.ConfigView;
import dev.bento.menu.core.data.DataSourceRegistry;
import dev.bento.menu.core.report.ValidationReport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validated, runtime-ready menu. Resolves layout regions into slot lists and
 * lazily obtains addon data sources.
 */
public final class CompiledMenu {

    private final MenuDefinition definition;
    private final Layout layout;
    private final List<Integer> contentSlots;
    private final List<Integer> searchSlots;
    private final List<Integer> previousSlots;
    private final List<Integer> nextSlots;
    private final List<Integer> pageSlots;
    private final List<Integer> categorySlots;
    private final List<Integer> closeSlots;
    private final List<Integer> borderSlots;
    private final List<Integer> backgroundSlots;
    private final Map<Integer, MenuItem> staticItems;
    private final Map<String, MenuItem> components;
    private final int pageSize;
    private final boolean searchEnabled;
    private final boolean categoriesEnabled;
    private final String sourceId;
    private final ConfigView sourceConfig;
    private final DataSource directSource;
    private final List<MenuEntry> staticEntries;
    private final List<String> categoryOrder;
    private final Map<String, String> categoryNames;
    private final SortMode sortMode;
    private final Duration refreshInterval;

    private DataSourceRegistry dataSourceRegistry;
    private volatile DataSource resolvedSource;
    private volatile boolean sourceResolved;

    private CompiledMenu(MenuDefinition definition, Layout layout,
                         List<Integer> contentSlots, List<Integer> searchSlots,
                         List<Integer> previousSlots, List<Integer> nextSlots,
                         List<Integer> pageSlots, List<Integer> categorySlots,
                         List<Integer> closeSlots, List<Integer> borderSlots,
                         List<Integer> backgroundSlots, Map<Integer, MenuItem> staticItems,
                         Map<String, MenuItem> components, int pageSize,
                         boolean searchEnabled, boolean categoriesEnabled,
                         String sourceId, ConfigView sourceConfig, DataSource directSource,
                         List<MenuEntry> staticEntries, List<String> categoryOrder,
                         Map<String, String> categoryNames, SortMode sortMode,
                         Duration refreshInterval) {
        this.definition = definition;
        this.layout = layout;
        this.contentSlots = contentSlots;
        this.searchSlots = searchSlots;
        this.previousSlots = previousSlots;
        this.nextSlots = nextSlots;
        this.pageSlots = pageSlots;
        this.categorySlots = categorySlots;
        this.closeSlots = closeSlots;
        this.borderSlots = borderSlots;
        this.backgroundSlots = backgroundSlots;
        this.staticItems = staticItems;
        this.components = components;
        this.pageSize = pageSize;
        this.searchEnabled = searchEnabled;
        this.categoriesEnabled = categoriesEnabled;
        this.sourceId = sourceId;
        this.sourceConfig = sourceConfig;
        this.directSource = directSource;
        this.staticEntries = staticEntries;
        this.categoryOrder = categoryOrder;
        this.categoryNames = categoryNames;
        this.sortMode = sortMode;
        this.refreshInterval = refreshInterval;
    }

    public static CompiledMenu compile(MenuDefinition definition, Layout layout,
                                       String sourceId, ConfigView sourceConfig,
                                       ValidationReport report, String file,
                                       DataSourceRegistry dataSourceRegistry) {
        if (definition == null) {
            return null;
        }
        if (layout == null) {
            report.error(file, "layout", "Menu " + definition.id() + " has no resolvable layout");
            return null;
        }
        List<Integer> content = layout.slotsOf(Layout.RegionType.CONTENT);
        List<Integer> search = layout.slotsOf(Layout.RegionType.SEARCH);
        List<Integer> previous = layout.slotsOf(Layout.RegionType.PREVIOUS);
        List<Integer> next = layout.slotsOf(Layout.RegionType.NEXT);
        List<Integer> page = layout.slotsOf(Layout.RegionType.PAGE);
        List<Integer> category = layout.slotsOf(Layout.RegionType.CATEGORY);
        List<Integer> close = layout.slotsOf(Layout.RegionType.CLOSE);
        List<Integer> border = layout.slotsOf(Layout.RegionType.BORDER);
        List<Integer> background = layout.slotsOf(Layout.RegionType.BACKGROUND);

        List<MenuEntry> entries = definition.entries();
        boolean staticSource = sourceId == null || sourceId.isBlank() || sourceId.equalsIgnoreCase("yaml");
        if (!entries.isEmpty() && content.isEmpty()) {
            report.error(file, "layout", "Menu " + definition.id()
                    + " defines entries but its layout has no CONTENT slots");
            return null;
        }
        if (!staticSource && entries.isEmpty() && definition.dataSource() == null && content.isEmpty()) {
            report.error(file, "layout", "Menu " + definition.id() + " has no CONTENT slots");
            return null;
        }
        int pageSize = definition.pageSize() > 0
                ? Math.min(definition.pageSize(), Math.max(1, content.size()))
                : Math.max(1, content.size());
        if (definition.pageSize() > content.size() && !content.isEmpty()) {
            report.warning(file, "data.page-size",
                    "page-size " + definition.pageSize() + " exceeds content slot count " + content.size()
                            + "; using " + pageSize);
        }
        boolean searchEnabled = definition.searchEnabled();
        if (searchEnabled && search.isEmpty()) {
            report.warning(file, "data.search",
                    "search is enabled but the layout has no SEARCH slots; disabling search");
            searchEnabled = false;
        }
        boolean categoriesEnabled = definition.categoriesEnabled();
        if (categoriesEnabled && category.isEmpty()) {
            report.warning(file, "data.categories",
                    "categories are enabled but the layout has no CATEGORY slots; disabling categories");
            categoriesEnabled = false;
        }

        Map<Integer, MenuItem> staticItems = new LinkedHashMap<>();
        for (Map.Entry<Integer, MenuItem> entry : definition.items().entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= layout.size()) {
                report.warning(file, "items", "Static item slot out of range: " + slot);
                continue;
            }
            staticItems.put(slot, entry.getValue());
            if (content.contains(slot)) {
                report.warning(file, "items", "Static item overrides content slot " + slot);
            }
        }

        Map<String, MenuItem> components = new LinkedHashMap<>();
        definition.components().forEach((key, item) ->
                components.put(key.toLowerCase(Locale.ROOT), item));

        List<String> categoryOrder = new ArrayList<>();
        for (String value : definition.categoryOrder()) {
            String normalized = MenuFilter.normalizeCategory(value);
            if (normalized != null) {
                categoryOrder.add(normalized);
            }
        }
        Map<String, String> categoryNames = new LinkedHashMap<>();
        definition.categoryNames().forEach((key, value) -> {
            String normalized = MenuFilter.normalizeCategory(key);
            if (normalized != null) {
                categoryNames.put(normalized, value);
            }
        });

        Duration refresh = definition.refreshInterval();
        if (refresh == null) {
            refresh = Duration.ZERO;
        }
        CompiledMenu compiled = new CompiledMenu(
                definition, layout,
                List.copyOf(content), List.copyOf(search), List.copyOf(previous),
                List.copyOf(next), List.copyOf(page), List.copyOf(category), List.copyOf(close),
                List.copyOf(border), List.copyOf(background),
                Map.copyOf(staticItems), Map.copyOf(components), pageSize,
                searchEnabled, categoriesEnabled, sourceId, sourceConfig,
                definition.dataSource(), List.copyOf(entries), List.copyOf(categoryOrder),
                Map.copyOf(categoryNames), definition.sortMode(), refresh
        );
        compiled.dataSourceRegistry = dataSourceRegistry;
        return compiled;
    }

    public MenuDefinition definition() {
        return definition;
    }

    public String id() {
        return definition.id();
    }

    public String title() {
        return definition.title();
    }

    public int rows() {
        return layout.rows();
    }

    public int size() {
        return layout.size();
    }

    public Layout layout() {
        return layout;
    }

    public List<Integer> contentSlots() {
        return contentSlots;
    }

    public List<Integer> searchSlots() {
        return searchSlots;
    }

    public List<Integer> previousSlots() {
        return previousSlots;
    }

    public List<Integer> nextSlots() {
        return nextSlots;
    }

    public List<Integer> pageSlots() {
        return pageSlots;
    }

    public List<Integer> categorySlots() {
        return categorySlots;
    }

    public List<Integer> closeSlots() {
        return closeSlots;
    }

    public List<Integer> borderSlots() {
        return borderSlots;
    }

    public List<Integer> backgroundSlots() {
        return backgroundSlots;
    }

    public Map<Integer, MenuItem> staticItems() {
        return staticItems;
    }

    public Map<String, MenuItem> components() {
        return components;
    }

    public int pageSize() {
        return pageSize;
    }

    public boolean searchEnabled() {
        return searchEnabled;
    }

    public boolean categoriesEnabled() {
        return categoriesEnabled;
    }

    public String sourceId() {
        return sourceId;
    }

    public List<MenuEntry> staticEntries() {
        return staticEntries;
    }

    public List<String> categoryOrder() {
        return categoryOrder;
    }

    public Map<String, String> categoryNames() {
        return categoryNames;
    }

    public SortMode sortMode() {
        return sortMode;
    }

    public Duration refreshInterval() {
        return refreshInterval;
    }

    public boolean isStatic() {
        return directSource == null
                && (sourceId == null || sourceId.isBlank() || sourceId.equalsIgnoreCase("yaml"));
    }

    public DataSource directSource() {
        return directSource;
    }

    /** Lazily creates a YAML-registered data source. */
    public DataSource source() {
        if (directSource != null) {
            return directSource;
        }
        if (isStatic()) {
            return null;
        }
        if (sourceResolved) {
            return resolvedSource;
        }
        synchronized (this) {
            if (sourceResolved) {
                return resolvedSource;
            }
            if (dataSourceRegistry != null && sourceId != null) {
                try {
                    resolvedSource = dataSourceRegistry.create(sourceId, sourceConfig);
                } catch (Exception exception) {
                    resolvedSource = null;
                }
            }
            sourceResolved = true;
            return resolvedSource;
        }
    }

    public void retrySource() {
        sourceResolved = false;
        resolvedSource = null;
    }
}
