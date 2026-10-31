package dev.bento.menu.api.v1.event;

import dev.bento.menu.api.v1.MenuDefinition;
import dev.bento.menu.api.v1.MenuOpenOptions;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the player's owning thread before a menu is opened.
 */
public class BentoMenuOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final MenuDefinition menu;
    private final MenuOpenOptions options;
    private boolean cancelled;

    public BentoMenuOpenEvent(Player player, MenuDefinition menu, MenuOpenOptions options) {
        this.player = player;
        this.menu = menu;
        this.options = options;
    }

    public Player getPlayer() {
        return player;
    }

    public MenuDefinition getMenu() {
        return menu;
    }

    public MenuOpenOptions getOptions() {
        return options;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
