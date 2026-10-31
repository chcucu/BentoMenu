package dev.bento.menu.api.v1.event;

import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.MenuSessionView;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;

/**
 * Fired on the player's owning thread before click feedback and actions.
 * Cancelling prevents all effects for that click.
 */
public class BentoMenuClickEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final MenuSessionView session;
    private final int slot;
    private final MenuItem item;
    private final MenuEntry entry;
    private final ClickType clickType;
    private boolean cancelled;

    public BentoMenuClickEvent(Player player, MenuSessionView session, int slot,
                               MenuItem item, MenuEntry entry, ClickType clickType) {
        this.player = player;
        this.session = session;
        this.slot = slot;
        this.item = item;
        this.entry = entry;
        this.clickType = clickType;
    }

    public Player getPlayer() {
        return player;
    }

    public MenuSessionView getSession() {
        return session;
    }

    public int getSlot() {
        return slot;
    }

    public MenuItem getItem() {
        return item;
    }

    /** Entry bound to the slot, or {@code null} for static/component buttons. */
    public MenuEntry getEntry() {
        return entry;
    }

    public ClickType getClickType() {
        return clickType;
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
