package dev.bento.menu.api.v1;

import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.data.DataSource;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;

/**
 * Fluent builder for {@link MenuDefinition}.
 */
public interface MenuBuilder {

    MenuBuilder title(String title);

    MenuBuilder rows(int rows);

    /** Uses an explicit layout, usually built with {@link Layout.Builder}. */
    MenuBuilder layout(Layout layout);

    /** Uses a layout YAML file managed by BentoMenu (layouts/&lt;id&gt;.yml). */
    MenuBuilder layoutId(String layoutId);

    /** Permission required to open the menu. {@code null} means no permission. */
    MenuBuilder permission(String permission);

    /** Forces a theme, ignoring the player's preference. {@code null} clears it. */
    MenuBuilder forcedTheme(String themeId);

    /** Number of entries per page. Values &lt;= 0 use the content region size. */
    MenuBuilder pageSize(int pageSize);

    MenuBuilder searchEnabled(boolean enabled);

    MenuBuilder categoriesEnabled(boolean enabled);

    /** Uses a directly supplied data source instance for this menu. */
    MenuBuilder dataSource(DataSource dataSource);

    MenuBuilder entry(MenuEntry entry);

    MenuBuilder entries(Collection<MenuEntry> entries);

    /** Binds a static item to an absolute slot. */
    MenuBuilder item(int slot, MenuItem item);

    /**
     * Binds a semantic component. Known keys: {@code border}, {@code background},
     * {@code search}, {@code previous}, {@code next}, {@code page}, {@code category},
     * {@code close}, {@code loading}, {@code empty}, {@code error}.
     */
    MenuBuilder component(String key, MenuItem item);

    MenuBuilder categoryOrder(Collection<String> categories);

    MenuBuilder categoryNames(Map<String, String> displayNames);

    MenuBuilder sortMode(SortMode sortMode);

    MenuBuilder openActions(Collection<Action> actions);

    MenuBuilder closeActions(Collection<Action> actions);

    /** Periodic refresh; {@link Duration#ZERO} disables it. */
    MenuBuilder refreshInterval(Duration interval);

    MenuDefinition build();
}
