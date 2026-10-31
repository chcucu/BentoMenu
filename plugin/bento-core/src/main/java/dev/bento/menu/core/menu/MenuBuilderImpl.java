package dev.bento.menu.core.menu;

import dev.bento.menu.api.v1.Layout;
import dev.bento.menu.api.v1.MenuBuilder;
import dev.bento.menu.api.v1.MenuDefinition;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.SortMode;
import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.data.DataSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Mutable builder for programmatic menus. Also used by the YAML loader so both
 * paths share validation and runtime representation.
 */
public final class MenuBuilderImpl implements MenuBuilder {

    private final String id;
    private String title;
    private int rows = 3;
    private Layout layout;
    private String layoutId;
    private String permission;
    private String forcedTheme;
    private int pageSize;
    private boolean searchEnabled;
    private boolean categoriesEnabled;
    private DataSource dataSource;
    private final List<MenuEntry> entries = new ArrayList<>();
    private final Map<Integer, MenuItem> items = new LinkedHashMap<>();
    private final Map<String, MenuItem> components = new LinkedHashMap<>();
    private final List<String> categoryOrder = new ArrayList<>();
    private final Map<String, String> categoryNames = new LinkedHashMap<>();
    private SortMode sortMode = SortMode.NONE;
    private final List<Action> openActions = new ArrayList<>();
    private final List<Action> closeActions = new ArrayList<>();
    private Duration refreshInterval = Duration.ZERO;

    public MenuBuilderImpl(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("menu id must not be blank");
        }
        this.id = id;
        this.title = id;
    }

    @Override
    public MenuBuilder title(String title) {
        this.title = title;
        return this;
    }

    @Override
    public MenuBuilder rows(int rows) {
        this.rows = rows;
        return this;
    }

    @Override
    public MenuBuilder layout(Layout layout) {
        this.layout = layout;
        this.layoutId = null;
        return this;
    }

    @Override
    public MenuBuilder layoutId(String layoutId) {
        this.layoutId = layoutId;
        this.layout = null;
        return this;
    }

    @Override
    public MenuBuilder permission(String permission) {
        this.permission = permission;
        return this;
    }

    @Override
    public MenuBuilder forcedTheme(String themeId) {
        this.forcedTheme = themeId;
        return this;
    }

    @Override
    public MenuBuilder pageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    @Override
    public MenuBuilder searchEnabled(boolean enabled) {
        this.searchEnabled = enabled;
        return this;
    }

    @Override
    public MenuBuilder categoriesEnabled(boolean enabled) {
        this.categoriesEnabled = enabled;
        return this;
    }

    @Override
    public MenuBuilder dataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    @Override
    public MenuBuilder entry(MenuEntry entry) {
        if (entry != null) {
            this.entries.add(entry);
        }
        return this;
    }

    @Override
    public MenuBuilder entries(Collection<MenuEntry> entries) {
        this.entries.clear();
        if (entries != null) {
            this.entries.addAll(entries);
        }
        return this;
    }

    @Override
    public MenuBuilder item(int slot, MenuItem item) {
        if (slot < 0 || slot > 53) {
            throw new IllegalArgumentException("slot must be between 0 and 53, got " + slot);
        }
        if (item != null) {
            this.items.put(slot, item);
        }
        return this;
    }

    @Override
    public MenuBuilder component(String key, MenuItem item) {
        if (key != null && item != null) {
            this.components.put(key.toLowerCase(java.util.Locale.ROOT), item);
        }
        return this;
    }

    @Override
    public MenuBuilder categoryOrder(Collection<String> categories) {
        this.categoryOrder.clear();
        if (categories != null) {
            this.categoryOrder.addAll(categories);
        }
        return this;
    }

    @Override
    public MenuBuilder categoryNames(Map<String, String> displayNames) {
        this.categoryNames.clear();
        if (displayNames != null) {
            this.categoryNames.putAll(displayNames);
        }
        return this;
    }

    @Override
    public MenuBuilder sortMode(SortMode sortMode) {
        this.sortMode = sortMode == null ? SortMode.NONE : sortMode;
        return this;
    }

    @Override
    public MenuBuilder openActions(Collection<Action> actions) {
        this.openActions.clear();
        if (actions != null) {
            this.openActions.addAll(actions);
        }
        return this;
    }

    @Override
    public MenuBuilder closeActions(Collection<Action> actions) {
        this.closeActions.clear();
        if (actions != null) {
            this.closeActions.addAll(actions);
        }
        return this;
    }

    @Override
    public MenuBuilder refreshInterval(Duration interval) {
        this.refreshInterval = interval == null ? Duration.ZERO : interval;
        return this;
    }

    @Override
    public MenuDefinition build() {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6, got " + rows);
        }
        if (layout == null && (layoutId == null || layoutId.isBlank())) {
            layout = AutoLayouts.bordered(rows);
        }
        if (layout != null && layout.rows() != rows) {
            // The layout is authoritative; keep rows aligned for inventory size.
            rows = layout.rows();
        }
        if (searchEnabled && (layout == null || layout.slotsOf(Layout.RegionType.SEARCH).isEmpty()) && layoutId == null) {
            searchEnabled = false;
        }
        if (categoriesEnabled && (layout == null || layout.slotsOf(Layout.RegionType.CATEGORY).isEmpty()) && layoutId == null) {
            categoriesEnabled = false;
        }
        return new MenuDefinitionImpl(
                id,
                title == null || title.isBlank() ? id : title,
                rows,
                layout,
                layoutId,
                permission,
                forcedTheme,
                pageSize,
                searchEnabled,
                categoriesEnabled,
                dataSource,
                List.copyOf(entries),
                Map.copyOf(items),
                Map.copyOf(components),
                List.copyOf(categoryOrder),
                Map.copyOf(categoryNames),
                sortMode,
                List.copyOf(openActions),
                List.copyOf(closeActions),
                refreshInterval
        );
    }
}
