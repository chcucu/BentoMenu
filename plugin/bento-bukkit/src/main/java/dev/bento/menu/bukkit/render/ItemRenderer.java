package dev.bento.menu.bukkit.render;

import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.MenuSessionView;
import dev.bento.menu.api.v1.service.ItemService;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.bukkit.data.PlayerProfileStore;
import dev.bento.menu.bukkit.integration.EconomyBridge;
import dev.bento.menu.bukkit.integration.PlaceholderBridge;
import dev.bento.menu.core.text.RenderedText;
import dev.bento.menu.core.text.TextEngine;
import dev.bento.menu.core.theme.ThemeRegistry;
import dev.bento.menu.core.util.HexColors;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Renders item templates into real ItemStacks, resolving text, theme colours,
 * PlaceholderAPI values and stable item meta.
 */
public final class ItemRenderer implements ItemService {

    private final TextEngine textEngine;
    private final PlaceholderBridge placeholders;
    private final EconomyBridge economy;
    private final PlayerProfileStore profiles;
    private final ThemeRegistry themes;
    private final Theme fallbackTheme;

    public ItemRenderer(TextEngine textEngine, PlaceholderBridge placeholders,
                        EconomyBridge economy, PlayerProfileStore profiles,
                        ThemeRegistry themes, Theme fallbackTheme) {
        this.textEngine = textEngine;
        this.placeholders = placeholders;
        this.economy = economy;
        this.profiles = profiles;
        this.themes = themes;
        this.fallbackTheme = fallbackTheme;
    }

    @Override
    public ItemStack render(Player player, MenuItem item, MenuSessionView session) {
        return render(player, item, session, null, Map.of());
    }

    @Override
    public ItemStack render(Player player, MenuItem item, MenuSessionView session, MenuEntry entry) {
        return render(player, item, session, entry, Map.of());
    }

    public ItemStack render(Player player, MenuItem item, MenuSessionView session,
                            MenuEntry entry, Map<String, String> extras) {
        return render(player, item, session, entry, extras, null, null, false);
    }

    public ItemStack render(Player player, MenuItem item, MenuSessionView session,
                            MenuEntry entry, Map<String, String> extras,
                            String nameOverride, List<String> loreOverride, boolean forceGlow) {
        if (item == null) {
            return null;
        }
        ItemStack stack = item.base();
        stack.setAmount(Math.max(1, Math.min(64, item.amount())));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        Theme theme = theme(player, session);
        UnaryOperator<String> resolver = resolver(player, session, entry, extras);
        String name = nameOverride != null ? nameOverride : item.name();
        if (name != null) {
            RenderedText rendered = textEngine.render(name, theme, resolver);
            meta.setDisplayName(rendered.legacy());
        }
        List<String> loreLines = loreOverride != null ? loreOverride : item.lore();
        if (!loreLines.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                RenderedText rendered = textEngine.render(line, theme, resolver);
                for (String split : rendered.legacy().split("\n", -1)) {
                    lore.add(split);
                }
            }
            meta.setLore(lore);
        }
        if (item.customModelData() != null) {
            meta.setCustomModelData(item.customModelData());
        }
        if (item.unbreakable()) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        for (String flag : item.itemFlags()) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flag.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
            } catch (IllegalArgumentException ignored) {
                // Invalid flags are reported at parse time by the platform.
            }
        }
        for (Map.Entry<String, Integer> entryEnchant : item.enchantments().entrySet()) {
            Enchantment enchantment = CompatEnchantments.get(entryEnchant.getKey());
            if (enchantment != null) {
                meta.addEnchant(enchantment, Math.max(1, entryEnchant.getValue()), true);
            }
        }
        if (item.glow() || forceGlow) {
            Enchantment glow = CompatEnchantments.glowEnchantment();
            if (glow != null) {
                meta.addEnchant(glow, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (item.leatherColor() != null && meta instanceof LeatherArmorMeta leather) {
            Integer rgb = HexColors.parse(item.leatherColor());
            if (rgb != null) {
                leather.setColor(Color.fromRGB(rgb));
            }
        }
        if (item.headOwner() != null && meta instanceof SkullMeta skull) {
            String owner = resolver.apply(item.headOwner()).trim();
            if (!owner.isEmpty()) {
                if (owner.equalsIgnoreCase(player.getName())) {
                    skull.setOwningPlayer(player);
                } else {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                }
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Builds a border/background tile with an RGB tint where supported. */
    public ItemStack tile(Material material, Integer customModelData, Integer rgb, boolean blankName) {
        ItemStack stack = new ItemStack(material == null ? Material.GRAY_STAINED_GLASS_PANE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }
        if (blankName) {
            meta.setDisplayName(" ");
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (rgb != null && meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.fromRGB(rgb));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public Theme theme(Player player, MenuSessionView session) {
        if (session != null && session.themeId() != null) {
            Theme theme = themes.get(session.themeId()).orElse(null);
            if (theme != null) {
                return theme;
            }
        }
        Theme theme = profiles.themeFor(player);
        return theme == null ? fallbackTheme : theme;
    }

    private UnaryOperator<String> resolver(Player player, MenuSessionView session,
                                           MenuEntry entry, Map<String, String> extras) {
        return text -> {
            String resolved = internal(text, player, session, entry);
            if (extras != null) {
                for (Map.Entry<String, String> extra : extras.entrySet()) {
                    resolved = resolved.replace("{" + extra.getKey() + "}", extra.getValue());
                }
            }
            return placeholders.resolve(player, resolved);
        };
    }

    private String internal(String text, Player player, MenuSessionView session, MenuEntry entry) {
        if (text == null || player == null) {
            return text;
        }
        String out = text
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{display_name}", player.getDisplayName())
                .replace("{world}", player.getWorld().getName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{ping}", String.valueOf(player.getPing()))
                .replace("{level}", String.valueOf(player.getLevel()))
                .replace("{health}", String.valueOf(Math.ceil(player.getHealth())))
                .replace("{food}", String.valueOf(player.getFoodLevel()))
                .replace("{balance}", economy.available() ? economy.format(economy.balance(player)) : "0")
                .replace("{balance_raw}", economy.available()
                        ? String.format(Locale.ROOT, "%.2f", economy.balance(player)) : "0");
        if (session != null) {
            out = out.replace("{menu}", session.menuId())
                    .replace("{theme}", session.themeId() == null ? "" : session.themeId())
                    .replace("{page}", String.valueOf(session.page()))
                    .replace("{pages}", String.valueOf(session.pages()))
                    .replace("{category}", session.category() == null ? "" : session.category())
                    .replace("{query}", session.query() == null ? "" : session.query())
                    .replace("{entries}", String.valueOf(session.totalEntries()));
        }
        if (entry != null) {
            out = out.replace("{entry}", entry.id())
                    .replace("{entry_id}", entry.id())
                    .replace("{entry_name}", entry.item().name() == null
                            ? entry.id() : textEngine.strip(entry.item().name()))
                    .replace("{category}", entry.category() == null
                            ? (session != null && session.category() != null ? session.category() : "")
                            : entry.category());
            Object price = entry.metadata().get("price");
            if (price != null) {
                String raw = String.valueOf(price);
                String formatted = raw;
                try {
                    double value = Double.parseDouble(raw);
                    formatted = economy.available() ? economy.format(value)
                            : String.format(Locale.ROOT, "%.2f", value);
                } catch (NumberFormatException ignored) {
                    // Keep the configured value.
                }
                out = out.replace("{price}", formatted).replace("{price_raw}", raw);
            }
        }
        return out;
    }
}
