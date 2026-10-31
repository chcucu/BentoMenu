package dev.bento.menu.api.v1.data;

import dev.bento.menu.api.v1.MenuEntry;

import java.util.List;

/**
 * Supplies entries for a menu. Implementations must be thread-safe: load() is
 * called from a bounded worker pool, never from the server tick thread.
 */
@FunctionalInterface
public interface DataSource {

    List<MenuEntry> load(DataSourceContext context) throws Exception;

    /**
     * Default implementation treats the source as static and caches nothing;
     * the framework coalesces refresh requests and diffs rendered items.
     */
    default boolean isDynamic() {
        return false;
    }
}
