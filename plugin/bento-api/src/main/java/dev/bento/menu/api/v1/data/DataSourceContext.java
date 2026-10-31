package dev.bento.menu.api.v1.data;

import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuSessionView;
import dev.bento.menu.api.v1.service.PlaceholderService;
import org.bukkit.entity.Player;

/**
 * Context passed to a data source. The data source is invoked off the owning
 * thread and may block; results are applied on the owning thread.
 */
public interface DataSourceContext {

    Player player();

    MenuSessionView session();

    String menuId();

    int page();

    String category();

    String query();

    PlaceholderService placeholders();
}
