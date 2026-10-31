package dev.bento.menu.bukkit.render;

import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.bukkit.data.PlayerProfileStore;
import dev.bento.menu.bukkit.integration.EconomyBridge;
import dev.bento.menu.bukkit.integration.MessageBridge;
import dev.bento.menu.bukkit.integration.PlaceholderBridge;
import dev.bento.menu.bukkit.menu.MenuSession;
import dev.bento.menu.bukkit.menu.RenderResult;
import dev.bento.menu.bukkit.menu.SlotBinding;
import dev.bento.menu.core.condition.Materials;
import dev.bento.menu.core.config.PluginConfig;
import dev.bento.menu.core.menu.CompiledMenu;
import dev.bento.menu.core.menu.MenuFilter;
import dev.bento.menu.core.text.RenderedText;
import dev.bento.menu.core.text.TextEngine;
import dev.bento.menu.core.util.HexColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Builds the full slot array, semantic bindings and title for a menu view.
 * Pure rendering on the player's owning thread; no I/O or blocking calls.
 */
public final class MenuRenderer {

    private final Plugin plugin;
    private final PluginConfig config;
    private final TextEngine textEngine;
    private final ItemRenderer items;
    private final MessageBridge messages;
    private final PlaceholderBridge placeholders;
    private final EconomyBridge economy;
    private final PlayerProfileStore profiles;

    public MenuRenderer(Plugin plugin, PluginConfig config, TextEngine textEngine,
                        ItemRenderer items, MessageBridge messages, PlaceholderBridge placeholders,
                        EconomyBridge economy, PlayerProfileStore profiles) {
        this.plugin = plugin;
        this.config = config;
        this.textEngine = textEngine;
        this.items = items;
        this.messages = messages;
        this.placeholders = placeholders;
        this.economy = economy;
        this.profiles = profiles;
    }

    public RenderResult render(MenuSession session, boolean loading, String error) {
        CompiledMenu menu = session.menu();
        Player player = session.player();
        Theme theme = session.theme();
        int size = menu.size();
        ItemStack[] contents = new ItemStack[size];
        SlotBinding[] bindings = new SlotBinding[size];
        String title = title(menu, session);

        for (int slot : menu.borderSlots()) {
            MenuItem component = menu.components().get("border");
            if (component != null) {
                contents[slot] = items.render(player, component, session, null, Map.of("slot", String.valueOf(slot)));
                bindings[slot] = SlotBinding.item(SlotBinding.Kind.BORDER, component);
            } else {
                contents[slot] = tile(theme, menu, slot, false);
                bindings[slot] = SlotBinding.of(SlotBinding.Kind.BORDER);
            }
        }
        MenuItem backgroundComponent = menu.components().get("background");
        for (int slot : menu.backgroundSlots()) {
            if (backgroundComponent != null) {
                contents[slot] = items.render(player, backgroundComponent, session, null,
                        Map.of("slot", String.valueOf(slot)));
                bindings[slot] = SlotBinding.item(SlotBinding.Kind.BACKGROUND, backgroundComponent);
            } else {
                contents[slot] = tile(theme, menu, slot, true);
                bindings[slot] = SlotBinding.of(SlotBinding.Kind.BACKGROUND);
            }
        }

        for (Map.Entry<Integer, MenuItem> entry : menu.staticItems().entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= size) {
                continue;
            }
            contents[slot] = items.render(player, entry.getValue(), session, null,
                    Map.of("slot", String.valueOf(slot)));
            bindings[slot] = SlotBinding.item(SlotBinding.Kind.STATIC, entry.getValue());
        }

        List<MenuEntry> visible = session.visibleEntries();
        int page = session.page();
        int pages = session.pages();
        List<String> categories = session.categories();
        String category = session.category();
        String query = session.query();

        for (int index = 0; index < menu.contentSlots().size(); index++) {
            int slot = menu.contentSlots().get(index);
            if (loading) {
                Presentation loadingItem = component(menu, session, "loading",
                        Messages.LOADING_NAME, Messages.LOADING_LORE, false, Map.of());
                contents[slot] = render(loadingItem, session, null, Map.of());
                bindings[slot] = SlotBinding.of(SlotBinding.Kind.LOADING);
                continue;
            }
            if (error != null) {
                Presentation errorItem = component(menu, session, "error",
                        Messages.ERROR_NAME, Messages.ERROR_LORE, false, Map.of("error", error));
                contents[slot] = render(errorItem, session, null, Map.of());
                bindings[slot] = SlotBinding.of(SlotBinding.Kind.ERROR);
                continue;
            }
            if (index < visible.size()) {
                MenuEntry entry = visible.get(index);
                Map<String, String> extras = entryExtras(menu, session, entry, slot, index);
                contents[slot] = items.render(player, entry.item(), session, entry, extras);
                bindings[slot] = SlotBinding.entry(entry);
            } else {
                MenuItem emptyComponent = menu.components().get("empty");
                if (emptyComponent != null) {
                    contents[slot] = items.render(player, emptyComponent, session, null,
                            Map.of("slot", String.valueOf(slot)));
                    bindings[slot] = SlotBinding.item(SlotBinding.Kind.EMPTY, emptyComponent);
                } else {
                    contents[slot] = tile(theme, menu, slot, true);
                    bindings[slot] = SlotBinding.of(SlotBinding.Kind.EMPTY);
                }
            }
        }

        if (menu.searchEnabled()) {
            for (int slot : menu.searchSlots()) {
                Presentation search = component(menu, session, "search",
                        Messages.SEARCH_NAME, Messages.SEARCH_LORE, !query.isEmpty(),
                        Map.of("query", query));
                contents[slot] = render(search, session, null, Map.of("query", query));
                bindings[slot] = SlotBinding.item(SlotBinding.Kind.SEARCH, search.item);
            }
        }
        if (!menu.previousSlots().isEmpty()) {
            boolean enabled = page > 1;
            for (int slot : menu.previousSlots()) {
                Presentation previous = component(menu, session, "previous",
                        Messages.PREVIOUS_NAME,
                        enabled ? Messages.PREVIOUS_LORE : Messages.PREVIOUS_DISABLED,
                        enabled ? null : "component.previous.disabled-lore",
                        false, Map.of("page", String.valueOf(page), "pages", String.valueOf(pages)));
                contents[slot] = render(previous, session, null, Map.of());
                bindings[slot] = new SlotBinding(SlotBinding.Kind.PREVIOUS, previous.item, null, null, enabled);
            }
        }
        if (!menu.nextSlots().isEmpty()) {
            boolean enabled = page < pages;
            for (int slot : menu.nextSlots()) {
                Presentation next = component(menu, session, "next",
                        Messages.NEXT_NAME,
                        enabled ? Messages.NEXT_LORE : Messages.NEXT_DISABLED,
                        enabled ? null : "component.next.disabled-lore",
                        false, Map.of("page", String.valueOf(page), "pages", String.valueOf(pages)));
                contents[slot] = render(next, session, null, Map.of());
                bindings[slot] = new SlotBinding(SlotBinding.Kind.NEXT, next.item, null, null, enabled);
            }
        }
        for (int slot : menu.pageSlots()) {
            Map<String, String> extras = new LinkedHashMap<>();
            extras.put("page", String.valueOf(page));
            extras.put("pages", String.valueOf(pages));
            extras.put("entries", String.valueOf(session.totalEntries()));
            extras.put("category", category == null ? "" : category);
            extras.put("category_name", categoryName(menu, session, category));
            extras.put("query", query);
            Presentation pageItem = component(menu, session, "page", Messages.PAGE_NAME,
                    Messages.PAGE_LORE, false, extras);
            contents[slot] = render(pageItem, session, null, extras);
            bindings[slot] = SlotBinding.item(SlotBinding.Kind.PAGE, pageItem.item);
        }

        if (menu.categoriesEnabled()) {
            List<CategoryChoice> choices = categoryChoices(menu, session, categories);
            List<Integer> slots = menu.categorySlots();
            for (int index = 0; index < slots.size(); index++) {
                int slot = slots.get(index);
                if (index >= choices.size()) {
                    MenuItem emptyComponent = menu.components().get("empty");
                    if (emptyComponent == null) {
                        contents[slot] = tile(theme, menu, slot, true);
                        bindings[slot] = SlotBinding.of(SlotBinding.Kind.CATEGORY);
                    }
                    continue;
                }
                CategoryChoice choice = choices.get(index);
                Map<String, String> extras = new LinkedHashMap<>();
                extras.put("category", choice.id == null ? "all" : choice.id);
                extras.put("category_name", choice.name);
                extras.put("active", String.valueOf(choice.active));
                Presentation item = component(menu, session, "category",
                        Messages.CATEGORY_NAME,
                        choice.active ? Messages.CATEGORY_ACTIVE : Messages.CATEGORY_LORE,
                        choice.active ? "component.category.active-lore" : null,
                        choice.active, extras);
                contents[slot] = render(item, session, null, extras);
                bindings[slot] = new SlotBinding(SlotBinding.Kind.CATEGORY, item.item, null,
                        choice.id, true);
            }
        }

        for (int slot : menu.closeSlots()) {
            Presentation close = component(menu, session, "close",
                    Messages.CLOSE_NAME, Messages.CLOSE_LORE, false, Map.of());
            contents[slot] = render(close, session, null, Map.of());
            bindings[slot] = SlotBinding.item(SlotBinding.Kind.CLOSE, close.item);
        }

        return new RenderResult(contents, bindings, visible, page, pages, categories, title);
    }

    public String title(CompiledMenu menu, MenuSession session) {
        Player player = session == null ? null : session.player();
        if (player == null) {
            return "";
        }
        Theme theme = session.theme();
        UnaryOperator<String> resolver = text -> {
            String resolved = internalTitle(text, player, session);
            return placeholders.resolve(player, resolved);
        };
        RenderedText rendered = textEngine.render(menu.title(), theme, resolver);
        return rendered.legacy();
    }

    private ItemStack render(Presentation presentation, MenuSession session,
                             MenuEntry entry, Map<String, String> extras) {
        return items.render(session.player(), presentation.item, session, entry, extras,
                presentation.name, presentation.lore, presentation.glow);
    }

    private Presentation component(CompiledMenu menu, MenuSession session, String key,
                                   String fallbackName, String fallbackLore,
                                   boolean forceGlow, Map<String, String> extras) {
        return component(menu, session, key, fallbackName, fallbackLore, null, forceGlow, extras);
    }

    private Presentation component(CompiledMenu menu, MenuSession session, String key,
                                   String fallbackName, String fallbackLore, String loreKey,
                                   boolean forceGlow, Map<String, String> extras) {
        Player player = session.player();
        MenuItem item = menu.components().get(key);
        if (item == null) {
            item = session.theme().icons().get(key);
        }
        if (item == null) {
            item = MenuItem.builder(defaultMaterial(key)).build();
        }
        String name = item.name();
        if (name == null) {
            name = message(player, "component." + key + ".name", fallbackName);
        }
        List<String> lore;
        if (!item.lore().isEmpty()) {
            lore = item.lore();
        } else {
            String raw = message(player, loreKey == null ? "component." + key + ".lore" : loreKey,
                    fallbackLore);
            lore = splitLines(raw);
        }
        return new Presentation(item, name, lore, forceGlow);
    }

    private String message(Player player, String key, String fallback) {
        String raw = messages.rawKey(player, key);
        return raw == null ? fallback : raw;
    }

    private static List<String> splitLines(String raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        String[] lines = raw.split("\n", -1);
        List<String> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(line);
        }
        return out;
    }

    private ItemStack tile(Theme theme, CompiledMenu menu, int slot, boolean background) {
        int start = color(theme, background ? "background-start" : "border-start",
                background ? 0x0B1220 : 0x2A3D66);
        int end = color(theme, background ? "background-end" : "border-end",
                background ? 0x1B2A4A : 0x5B4B8A);
        double ratio = gradientRatio(menu, slot);
        int rgb;
        if (theme.borderStyle() == Theme.BorderStyle.SOLID) {
            rgb = start;
        } else if (theme.borderStyle() == Theme.BorderStyle.STRIPED) {
            rgb = ((slot / 9) + (slot % 9)) % 2 == 0 ? start : end;
        } else {
            rgb = HexColors.interpolate(start, end, ratio);
        }
        if (config.resourcePack() && theme.usesResourcePackBorder()) {
            Material material = Materials.match(theme.packBorderMaterial());
            if (material != null) {
                return items.tile(material, theme.packBorderModelData(), rgb, true);
            }
        }
        Material material = Materials.match(theme.borderMaterial());
        if (material == null) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }
        if (theme.borderStyle() != Theme.BorderStyle.SOLID
                && material.name().contains("STAINED_GLASS_PANE")) {
            Material nearest = Materials.match(HexColors.nearestStainedGlassPane(rgb));
            if (nearest != null) {
                material = nearest;
            }
        }
        Integer modelData = config.resourcePack() ? theme.borderCustomModelData() : null;
        return items.tile(material, modelData, null, true);
    }

    private static double gradientRatio(CompiledMenu menu, int slot) {
        int row = slot / 9;
        int col = slot % 9;
        int max = Math.max(1, (menu.rows() - 1) + 8);
        return (double) (row + col) / (double) max;
    }

    private static int color(Theme theme, String key, int fallback) {
        String raw = theme.color(key, null);
        Integer parsed = HexColors.parse(raw);
        return parsed == null ? fallback : parsed;
    }

    private List<CategoryChoice> categoryChoices(CompiledMenu menu, MenuSession session,
                                                 List<String> categories) {
        List<CategoryChoice> out = new ArrayList<>();
        String active = session.category();
        String allName = categoryName(menu, session, null);
        out.add(new CategoryChoice(null, allName, active == null));
        for (String category : categories) {
            out.add(new CategoryChoice(category, categoryName(menu, session, category),
                    category.equalsIgnoreCase(active)));
        }
        return out;
    }

    public String categoryName(CompiledMenu menu, MenuSession session, String category) {
        return categoryName(menu, session.player(), category);
    }

    private String categoryName(CompiledMenu menu, Player player, String category) {
        if (category == null) {
            String configured = menu.categoryNames().get("all");
            if (configured != null) {
                return configured;
            }
            return message(player, "category.all", "全部");
        }
        String configured = menu.categoryNames().get(category);
        if (configured != null) {
            return configured;
        }
        return message(player, "category." + category, category);
    }

    private Map<String, String> entryExtras(CompiledMenu menu, MenuSession session,
                                            MenuEntry entry, int slot, int index) {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("slot", String.valueOf(slot));
        extras.put("index", String.valueOf(index));
        extras.put("entry", entry.id());
        extras.put("entry_id", entry.id());
        extras.put("entry_name", entry.item().name() == null ? entry.id()
                : textEngine.strip(entry.item().name()));
        extras.put("category", entry.category() == null ? "" : entry.category());
        extras.put("category_name", entry.category() == null ? ""
                : categoryName(menu, session.player(), entry.category()));
        Object price = entry.metadata().get("price");
        if (price != null) {
            String raw = String.valueOf(price);
            extras.put("price_raw", raw);
            try {
                double value = Double.parseDouble(raw);
                extras.put("price", economy.available() ? economy.format(value)
                        : String.format(Locale.ROOT, "%.2f", value));
            } catch (NumberFormatException ignored) {
                extras.put("price", raw);
            }
        }
        entry.metadata().forEach((key, value) -> {
            if (key != null && value != null && !extras.containsKey(key)) {
                extras.put(key, String.valueOf(value));
            }
        });
        return extras;
    }

    private String internalTitle(String text, Player player, MenuSession session) {
        if (text == null) {
            return null;
        }
        String out = text
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{world}", player.getWorld().getName())
                .replace("{balance}", economy.available() ? economy.format(economy.balance(player)) : "0")
                .replace("{menu}", session.menu().id())
                .replace("{theme}", session.themeId() == null ? "" : session.themeId())
                .replace("{page}", String.valueOf(session.page()))
                .replace("{pages}", String.valueOf(session.pages()))
                .replace("{category}", session.category() == null ? "" : session.category())
                .replace("{category_name}", categoryName(session.menu(), session, session.category()))
                .replace("{query}", session.query() == null ? "" : session.query())
                .replace("{entries}", String.valueOf(session.totalEntries()));
        return out;
    }

    private static Material defaultMaterial(String key) {
        return switch (key) {
            case "search" -> Material.COMPASS;
            case "previous", "next" -> Material.ARROW;
            case "page" -> Material.PAPER;
            case "category" -> Material.NAME_TAG;
            case "close" -> Material.BARRIER;
            case "loading" -> Material.CLOCK;
            case "error" -> Material.BARRIER;
            case "empty", "background", "border" -> Material.GRAY_STAINED_GLASS_PANE;
            default -> Material.STONE;
        };
    }

    private record Presentation(MenuItem item, String name, List<String> lore, boolean glow) {
    }

    private record CategoryChoice(String id, String name, boolean active) {
    }

    private static final class Messages {
        static final String SEARCH_NAME = "<color:accent>搜索</color>";
        static final String SEARCH_LORE = "<muted>点击输入关键词</muted>";
        static final String PREVIOUS_NAME = "<color:text>← 上一页</color>";
        static final String PREVIOUS_LORE = "<muted>第 {page} / {pages} 页</muted>";
        static final String PREVIOUS_DISABLED = "<muted>已到第一页</muted>";
        static final String NEXT_NAME = "<color:text>下一页 →</color>";
        static final String NEXT_LORE = "<muted>第 {page} / {pages} 页</muted>";
        static final String NEXT_DISABLED = "<muted>已到最后一页</muted>";
        static final String PAGE_NAME = "<color:primary>第 {page} / {pages} 页</color>";
        static final String PAGE_LORE = "<muted>共 {entries} 项</muted>";
        static final String CATEGORY_NAME = "<color:text>{category_name}</color>";
        static final String CATEGORY_LORE = "<muted>点击筛选</muted>";
        static final String CATEGORY_ACTIVE = "<color:success>当前分类</color>";
        static final String CLOSE_NAME = "<color:error>关闭</color>";
        static final String CLOSE_LORE = "<muted>点击关闭菜单</muted>";
        static final String LOADING_NAME = "<color:warning>正在加载…</color>";
        static final String LOADING_LORE = "<muted>数据正在异步准备</muted>";
        static final String ERROR_NAME = "<color:error>加载失败</color>";
        static final String ERROR_LORE = "<muted>{error}</muted>";
    }
}
