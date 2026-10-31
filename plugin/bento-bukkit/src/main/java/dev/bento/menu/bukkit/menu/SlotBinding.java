package dev.bento.menu.bukkit.menu;

import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;

/**
 * What a rendered slot represents. The click handler uses this instead of
 * re-parsing ItemStacks, which keeps clicks fast and immune to item spoofing.
 */
public final class SlotBinding {

    public enum Kind {
        BORDER,
        BACKGROUND,
        STATIC,
        ENTRY,
        SEARCH,
        PREVIOUS,
        NEXT,
        PAGE,
        CATEGORY,
        CLOSE,
        EMPTY,
        LOADING,
        ERROR
    }

    private final Kind kind;
    private final MenuItem item;
    private final MenuEntry entry;
    private final String category;
    private final boolean enabled;

    public SlotBinding(Kind kind, MenuItem item, MenuEntry entry, String category, boolean enabled) {
        this.kind = kind;
        this.item = item;
        this.entry = entry;
        this.category = category;
        this.enabled = enabled;
    }

    public static SlotBinding of(Kind kind) {
        return new SlotBinding(kind, null, null, null, true);
    }

    public static SlotBinding item(Kind kind, MenuItem item) {
        return new SlotBinding(kind, item, null, null, true);
    }

    public static SlotBinding entry(MenuEntry entry) {
        return new SlotBinding(Kind.ENTRY, entry.item(), entry, null, true);
    }

    public Kind kind() {
        return kind;
    }

    public MenuItem item() {
        return item;
    }

    public MenuEntry entry() {
        return entry;
    }

    public String category() {
        return category;
    }

    public boolean enabled() {
        return enabled;
    }
}
