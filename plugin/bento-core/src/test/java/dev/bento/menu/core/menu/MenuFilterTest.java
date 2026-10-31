package dev.bento.menu.core.menu;

import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.SortMode;
import dev.bento.menu.core.text.TextEngine;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuFilterTest {

    private final MenuFilter filter = new MenuFilter(new TextEngine());

    private MenuEntry entry(String id, String category, double price, String name, String... tags) {
        MenuEntry.Builder builder = MenuEntry.builder(id,
                MenuItem.builder(Material.STONE).name(name).build());
        if (category != null) {
            builder.category(category);
        }
        builder.metadata("price", price);
        for (String tag : tags) {
            builder.tag(tag);
        }
        return builder.build();
    }

    private List<MenuEntry> entries() {
        List<MenuEntry> entries = new ArrayList<>();
        entries.add(entry("apple", "food", 6, "Apple", "fruit"));
        entries.add(entry("bread", "food", 8, "Bread"));
        entries.add(entry("pickaxe", "tools", 45, "Iron Pickaxe", "mining"));
        entries.add(entry("pearl", "magic", 35, "Ender Pearl", "teleport"));
        entries.add(entry("torch", "tools", 3, "Torch", "light"));
        return entries;
    }

    @Test
    void paginatesAndKeepsPagesStable() {
        MenuFilter.Result result = filter.apply(entries(),
                new MenuFilter.Query(1, 2, null, "", SortMode.NONE), List.of());
        assertEquals(2, result.pageEntries().size());
        assertEquals(3, result.pages());
        assertEquals(5, result.total());
        MenuFilter.Result second = filter.apply(entries(),
                new MenuFilter.Query(2, 2, null, "", SortMode.NONE), List.of());
        assertEquals(2, second.pageEntries().size());
        MenuFilter.Result beyond = filter.apply(entries(),
                new MenuFilter.Query(99, 2, null, "", SortMode.NONE), List.of());
        assertEquals(3, beyond.page());
        assertEquals(1, beyond.pageEntries().size());
    }

    @Test
    void filtersByCategoryAndTag() {
        MenuFilter.Result tools = filter.apply(entries(),
                new MenuFilter.Query(1, 10, "tools", "", SortMode.NONE), List.of());
        assertEquals(2, tools.total());
        MenuFilter.Result tag = filter.apply(entries(),
                new MenuFilter.Query(1, 10, "mining", "", SortMode.NONE), List.of());
        assertEquals(1, tag.total());
        assertEquals("pickaxe", tag.pageEntries().get(0).id());
    }

    @Test
    void searchesNameIdTagsAndMetadata() {
        assertEquals(1, filter.apply(entries(),
                new MenuFilter.Query(1, 10, null, "apple", SortMode.NONE), List.of()).total());
        assertEquals(1, filter.apply(entries(),
                new MenuFilter.Query(1, 10, null, "teleport", SortMode.NONE), List.of()).total());
        assertEquals(1, filter.apply(entries(),
                new MenuFilter.Query(1, 10, null, "Iron", SortMode.NONE), List.of()).total());
        assertEquals(5, filter.apply(entries(),
                new MenuFilter.Query(1, 10, null, "", SortMode.NONE), List.of()).total());
    }

    @Test
    void sortsByPriceAndName() {
        MenuFilter.Result ascending = filter.apply(entries(),
                new MenuFilter.Query(1, 10, null, "", SortMode.PRICE_ASC), List.of());
        assertEquals("torch", ascending.pageEntries().get(0).id());
        MenuFilter.Result descending = filter.apply(entries(),
                new MenuFilter.Query(1, 10, null, "", SortMode.PRICE_DESC), List.of());
        assertEquals("pickaxe", descending.pageEntries().get(0).id());
        MenuFilter.Result byName = filter.apply(entries(),
                new MenuFilter.Query(1, 10, null, "", SortMode.NAME), List.of());
        assertEquals("apple", byName.pageEntries().get(0).id());
    }

    @Test
    void ordersCategoriesByConfigurationThenAlphabetically() {
        MenuFilter.Result result = filter.apply(entries(),
                new MenuFilter.Query(1, 10, null, "", SortMode.NONE), List.of("tools"));
        assertEquals(List.of("tools", "food", "magic"), result.categories());
        assertEquals(Integer.valueOf(2), result.categoryCounts().get("tools"));
    }

    @Test
    void normalisesAllCategories() {
        assertNull(MenuFilter.normalizeCategory("all"));
        assertNull(MenuFilter.normalizeCategory("全部"));
        assertEquals("food", MenuFilter.normalizeCategory(" Food "));
    }

    @Test
    void handlesEmptySources() {
        MenuFilter.Result result = filter.apply(List.of(),
                new MenuFilter.Query(1, 9, null, "x", SortMode.NONE), List.of());
        assertEquals(1, result.pages());
        assertEquals(0, result.total());
        assertTrue(result.pageEntries().isEmpty());
    }
}
