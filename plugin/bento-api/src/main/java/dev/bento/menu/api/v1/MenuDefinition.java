package dev.bento.menu.api.v1;

import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.data.DataSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable, validated menu definition. Instances are created through
 * {@link MenuBuilder}; menus loaded from YAML use the same representation.
 */
public interface MenuDefinition {

    String id();

    String title();

    int rows();

    /** Explicit layout, or {@code null} when {@link #layoutId()} is used. */
    Layout layout();

    /** Layout file id, or {@code null} when an explicit layout is used. */
    String layoutId();

    String permission();

    String forcedThemeId();

    int pageSize();

    boolean searchEnabled();

    boolean categoriesEnabled();

    DataSource dataSource();

    List<MenuEntry> entries();

    Map<Integer, MenuItem> items();

    Map<String, MenuItem> components();

    List<String> categoryOrder();

    Map<String, String> categoryNames();

    SortMode sortMode();

    List<Action> openActions();

    List<Action> closeActions();

    Duration refreshInterval();
}
