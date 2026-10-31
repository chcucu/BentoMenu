package dev.bento.menu.bukkit.menu;

import org.bukkit.inventory.InventoryView;

import java.lang.reflect.Method;

/**
 * Optional in-place inventory title updates. Paper exposes setTitle on the
 * InventoryView; Spigot does not, so page-dependent titles simply remain the
 * title used when the inventory was opened.
 */
public final class InventoryTitles {

    private InventoryTitles() {
    }

    public static boolean set(InventoryView view, String title) {
        if (view == null || title == null) {
            return false;
        }
        try {
            Method method = InventoryView.class.getMethod("setTitle", String.class);
            method.invoke(view, title);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
