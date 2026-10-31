package dev.bento.menu.bukkit.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Identifies BentoMenu inventories without relying on title strings.
 */
public final class BentoInventoryHolder implements InventoryHolder {

    public enum Kind {
        MENU,
        INPUT
    }

    private final Kind kind;
    private final UUID playerId;
    private final String menuId;
    private Inventory inventory;

    public BentoInventoryHolder(Kind kind, UUID playerId, String menuId) {
        this.kind = kind;
        this.playerId = playerId;
        this.menuId = menuId;
    }

    public Kind kind() {
        return kind;
    }

    public UUID playerId() {
        return playerId;
    }

    public String menuId() {
        return menuId;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
