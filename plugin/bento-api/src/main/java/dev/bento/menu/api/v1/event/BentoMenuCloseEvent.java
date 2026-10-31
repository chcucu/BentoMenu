package dev.bento.menu.api.v1.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the player's owning thread after a menu view is closed.
 */
public class BentoMenuCloseEvent extends Event {

    public enum CloseReason {
        PLAYER,
        PLUGIN,
        QUIT,
        REPLACED,
        ERROR
    }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String menuId;
    private final CloseReason reason;

    public BentoMenuCloseEvent(Player player, String menuId, CloseReason reason) {
        this.player = player;
        this.menuId = menuId;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    public String getMenuId() {
        return menuId;
    }

    public CloseReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
