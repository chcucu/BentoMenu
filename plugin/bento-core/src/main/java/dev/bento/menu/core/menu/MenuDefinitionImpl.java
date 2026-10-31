package dev.bento.menu.core.menu;

import dev.bento.menu.api.v1.Layout;
import dev.bento.menu.api.v1.MenuDefinition;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.SortMode;
import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.data.DataSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

final class MenuDefinitionImpl implements MenuDefinition {

    private final String id;
    private final String title;
    private final int rows;
    private final Layout layout;
    private final String layoutId;
    private final String permission;
    private final String forcedThemeId;
    private final int pageSize;
    private final boolean searchEnabled;
    private final boolean categoriesEnabled;
    private final DataSource dataSource;
    private final List<MenuEntry> entries;
    private final Map<Integer, MenuItem> items;
    private final Map<String, MenuItem> components;
    private final List<String> categoryOrder;
    private final Map<String, String> categoryNames;
    private final SortMode sortMode;
    private final List<Action> openActions;
    private final List<Action> closeActions;
    private final Duration refreshInterval;

    MenuDefinitionImpl(String id, String title, int rows, Layout layout, String layoutId,
                       String permission, String forcedThemeId, int pageSize,
                       boolean searchEnabled, boolean categoriesEnabled, DataSource dataSource,
                       List<MenuEntry> entries, Map<Integer, MenuItem> items,
                       Map<String, MenuItem> components, List<String> categoryOrder,
                       Map<String, String> categoryNames, SortMode sortMode,
                       List<Action> openActions, List<Action> closeActions, Duration refreshInterval) {
        this.id = id;
        this.title = title;
        this.rows = rows;
        this.layout = layout;
        this.layoutId = layoutId;
        this.permission = permission;
        this.forcedThemeId = forcedThemeId;
        this.pageSize = pageSize;
        this.searchEnabled = searchEnabled;
        this.categoriesEnabled = categoriesEnabled;
        this.dataSource = dataSource;
        this.entries = entries;
        this.items = items;
        this.components = components;
        this.categoryOrder = categoryOrder;
        this.categoryNames = categoryNames;
        this.sortMode = sortMode;
        this.openActions = openActions;
        this.closeActions = closeActions;
        this.refreshInterval = refreshInterval;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int rows() {
        return rows;
    }

    @Override
    public Layout layout() {
        return layout;
    }

    @Override
    public String layoutId() {
        return layoutId;
    }

    @Override
    public String permission() {
        return permission;
    }

    @Override
    public String forcedThemeId() {
        return forcedThemeId;
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    @Override
    public boolean searchEnabled() {
        return searchEnabled;
    }

    @Override
    public boolean categoriesEnabled() {
        return categoriesEnabled;
    }

    @Override
    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<MenuEntry> entries() {
        return entries;
    }

    @Override
    public Map<Integer, MenuItem> items() {
        return items;
    }

    @Override
    public Map<String, MenuItem> components() {
        return components;
    }

    @Override
    public List<String> categoryOrder() {
        return categoryOrder;
    }

    @Override
    public Map<String, String> categoryNames() {
        return categoryNames;
    }

    @Override
    public SortMode sortMode() {
        return sortMode;
    }

    @Override
    public List<Action> openActions() {
        return openActions;
    }

    @Override
    public List<Action> closeActions() {
        return closeActions;
    }

    @Override
    public Duration refreshInterval() {
        return refreshInterval;
    }
}
