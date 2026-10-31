package dev.bento.menu.bukkit.listener;

import dev.bento.menu.bukkit.menu.MenuService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Routes container events to the session layer. Clicks are always cancelled for
 * BentoMenu inventories; this prevents item theft, shift-click dupe techniques
 * and double-click interactions.
 */
public final class MenuListener implements Listener {

    private final MenuService menus;

    public MenuListener(MenuService menus) {
        this.menus = menus;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        menus.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        menus.handleDrag(event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        menus.handleClose(event);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        menus.handlePrepareAnvil(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menus.onQuit(event.getPlayer());
    }
}
