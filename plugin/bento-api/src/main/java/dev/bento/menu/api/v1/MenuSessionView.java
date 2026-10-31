package dev.bento.menu.api.v1;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Read/write view of an open menu session. Mutating methods are safe to call
 * from actions and schedule the corresponding work on the owner thread.
 */
public interface MenuSessionView {

    Player player();

    String menuId();

    String themeId();

    int page();

    int pages();

    int pageSize();

    int totalEntries();

    String category();

    String query();

    List<MenuEntry> visibleEntries();

    void setPage(int page);

    /** Sets the category filter; {@code null} or {@code all} shows everything. */
    void setCategory(String category);

    void setQuery(String query);

    /** Opens the configured text input (anvil/chat) for menu search. */
    void requestSearch();

    void requestRefresh();
}
