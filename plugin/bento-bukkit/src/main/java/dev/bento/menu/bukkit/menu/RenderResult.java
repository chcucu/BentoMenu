package dev.bento.menu.bukkit.menu;

import dev.bento.menu.api.v1.MenuEntry;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Immutable render output used for diffing and click binding.
 */
public record RenderResult(ItemStack[] contents, SlotBinding[] bindings,
                           List<MenuEntry> pageEntries, int page, int pages,
                           List<String> categories, String title) {
}
