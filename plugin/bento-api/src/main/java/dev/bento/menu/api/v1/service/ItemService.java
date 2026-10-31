package dev.bento.menu.api.v1.service;

import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.MenuSessionView;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Renders immutable item blueprints for a specific viewer and theme.
 */
public interface ItemService {

    /**
     * @param session may be {@code null} when rendering outside an open menu
     */
    ItemStack render(Player player, MenuItem item, MenuSessionView session);

    /**
     * Entry-aware variant used for content items; enables {@code {price}},
     * {@code {entry}} and similar placeholders.
     */
    default ItemStack render(Player player, MenuItem item, MenuSessionView session, MenuEntry entry) {
        return render(player, item, session);
    }
}
