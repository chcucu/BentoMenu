package dev.bento.menu.bukkit.integration;

import dev.bento.menu.api.v1.service.MessageService;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.bukkit.data.PlayerProfileStore;
import dev.bento.menu.core.config.MessageCatalog;
import dev.bento.menu.core.config.PluginConfig;
import dev.bento.menu.core.text.RenderedText;
import dev.bento.menu.core.text.TextEngine;
import dev.bento.menu.core.theme.ThemeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Fully configurable message rendering. Uses the player's saved language and
 * theme, resolves internal placeholders plus the asynchronous PAPI cache, and
 * applies the same text syntax as menu titles and item lore.
 */
public final class MessageBridge implements MessageService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Plugin plugin;
    private final PluginConfig config;
    private volatile MessageCatalog catalog;
    private final TextEngine textEngine;
    private final PlayerProfileStore profiles;
    private final PlaceholderBridge placeholders;
    private final EconomyBridge economy;
    private final ThemeRegistry themes;
    private final Theme fallbackTheme;
    private final Set<String> missingKeys = ConcurrentHashMap.newKeySet();

    public MessageBridge(Plugin plugin, PluginConfig config, MessageCatalog catalog,
                         TextEngine textEngine, PlayerProfileStore profiles,
                         PlaceholderBridge placeholders, EconomyBridge economy,
                         ThemeRegistry themes, Theme fallbackTheme) {
        this.plugin = plugin;
        this.config = config;
        this.catalog = catalog;
        this.textEngine = textEngine;
        this.profiles = profiles;
        this.placeholders = placeholders;
        this.economy = economy;
        this.themes = themes;
        this.fallbackTheme = fallbackTheme;
    }

    @Override
    public void send(Player player, String key) {
        send(player, key, Map.of());
    }

    @Override
    public void send(Player player, String key, Map<String, Object> placeholders) {
        if (player == null) {
            return;
        }
        String raw = raw(player, key);
        String text = applyValues(raw, placeholders);
        RenderedText rendered = textEngine.render(text, theme(player), resolver(player));
        String prefix = prefix(player);
        player.sendMessage(prefix + rendered.legacy());
    }

    @Override
    public void sendRaw(Player player, String text) {
        if (player == null || text == null) {
            return;
        }
        RenderedText rendered = textEngine.render(text, theme(player), resolver(player));
        player.sendMessage(rendered.legacy());
    }

    @Override
    public String render(Player player, String text) {
        if (text == null) {
            return "";
        }
        return textEngine.render(text, theme(player), resolver(player)).legacy();
    }

    @Override
    public String renderKey(Player player, String key, Map<String, Object> placeholders) {
        if (player == null) {
            return "";
        }
        String raw = raw(player, key);
        return textEngine.render(applyValues(raw, placeholders), theme(player), resolver(player)).legacy();
    }

    public MessageCatalog catalog() {
        return catalog;
    }

    public void setCatalog(MessageCatalog catalog) {
        if (catalog != null) {
            this.catalog = catalog;
            missingKeys.clear();
        }
    }

    /** Raw configured message text (no prefix, no rendering). */
    public String rawKey(Player player, String key) {
        if (player == null) {
            return null;
        }
        return catalog.raw(profiles.languageFor(player), key);
    }

    /** Rendered prefix for the player's language and theme. */
    public String prefix(Player player) {
        if (player == null) {
            return "";
        }
        String raw = catalog.raw(profiles.languageFor(player), "prefix");
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return textEngine.render(raw, theme(player), resolver(player)).legacy();
    }

    private String raw(Player player, String key) {
        String raw = catalog.raw(profiles.languageFor(player), key);
        if (raw == null) {
            if (missingKeys.add(key)) {
                plugin.getLogger().warning("Missing message key: " + key);
            }
            return key;
        }
        return raw;
    }

    private Theme theme(Player player) {
        Theme theme = player == null ? null : profiles.themeFor(player);
        if (theme != null) {
            return theme;
        }
        return fallbackTheme;
    }

    private UnaryOperator<String> resolver(Player player) {
        return text -> placeholders.resolve(player, internal(player, text));
    }

    private String internal(Player player, String text) {
        if (text == null || player == null) {
            return text;
        }
        String themeId = profiles.themeIdFor(player);
        String balanceFormatted = economy.available()
                ? economy.format(economy.balance(player)) : "0";
        String balanceRaw = economy.available()
                ? String.format(java.util.Locale.ROOT, "%.2f", economy.balance(player)) : "0";
        LocalDateTime now = LocalDateTime.now();
        return text
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{display_name}", player.getDisplayName())
                .replace("{world}", player.getWorld().getName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{ping}", String.valueOf(player.getPing()))
                .replace("{level}", String.valueOf(player.getLevel()))
                .replace("{health}", String.valueOf(Math.ceil(player.getHealth())))
                .replace("{food}", String.valueOf(player.getFoodLevel()))
                .replace("{balance}", balanceFormatted)
                .replace("{balance_raw}", balanceRaw)
                .replace("{theme}", themeId)
                .replace("{time}", now.format(TIME))
                .replace("{date}", now.format(DATE));
    }

    private static String applyValues(String raw, Map<String, Object> values) {
        if (raw == null) {
            return "";
        }
        if (values == null || values.isEmpty()) {
            return raw;
        }
        String out = raw;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return out;
    }
}
