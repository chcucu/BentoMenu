package dev.bento.menu.core.config;

import dev.bento.menu.core.util.Durations;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.Locale;

/**
 * Global plugin settings loaded from config.yml.
 */
public final class PluginConfig {

    private volatile String defaultLanguage = "zh_CN";
    private volatile boolean allowLanguageSwitch = true;
    private volatile String defaultTheme = "aurora";
    private volatile String defaultMenu = "main";
    private volatile boolean autoReload;
    private volatile Duration autoReloadDebounce = Duration.ofSeconds(2);
    private volatile boolean resourcePack;
    private volatile int clickDebounceMs = 120;
    private volatile int dataThreads = 2;
    private volatile int searchThreads = 1;
    private volatile Duration placeholderCacheTtl = Duration.ofSeconds(3);
    private volatile boolean metrics = true;
    private volatile String currencyFormat = "$%.2f";
    private volatile String searchInput = "auto";
    private volatile int maxSearchLength = 32;
    private volatile boolean clickSound = true;
    private volatile boolean openSound = true;
    private volatile boolean debug;
    private volatile String actionErrorMode = "stop";

    public static PluginConfig load(FileConfiguration config) {
        PluginConfig out = new PluginConfig();
        out.defaultLanguage = normalizeLanguage(config.getString("language.default", "zh_CN"));
        out.allowLanguageSwitch = config.getBoolean("language.allow-player-switch", true);
        out.defaultTheme = config.getString("theme.default", "aurora");
        out.defaultMenu = config.getString("menu.default", "main");
        out.autoReload = config.getBoolean("auto-reload.enabled", false);
        out.autoReloadDebounce = Durations.parse(
                config.getString("auto-reload.debounce", "2s"), Duration.ofSeconds(2));
        out.resourcePack = config.getBoolean("ui.resource-pack", false);
        out.clickDebounceMs = Math.max(0, Math.min(1000, config.getInt("ui.click-debounce-ms", 120)));
        out.clickSound = config.getBoolean("ui.click-sound", true);
        out.openSound = config.getBoolean("ui.open-sound", true);
        out.dataThreads = Math.max(1, Math.min(8, config.getInt("performance.data-threads", 2)));
        out.searchThreads = Math.max(1, Math.min(4, config.getInt("performance.search-threads", 1)));
        out.placeholderCacheTtl = Durations.parse(
                config.getString("performance.placeholder-cache-ttl", "3s"), Duration.ofSeconds(3));
        out.metrics = config.getBoolean("performance.metrics", true);
        out.currencyFormat = config.getString("economy.format", "$%.2f");
        out.searchInput = config.getString("search.input", "auto").toLowerCase(Locale.ROOT);
        out.maxSearchLength = Math.max(4, Math.min(64, config.getInt("search.max-length", 32)));
        out.debug = config.getBoolean("debug", false);
        out.actionErrorMode = config.getString("actions.on-error", "stop").toLowerCase(Locale.ROOT);
        return out;
    }

    public static PluginConfig defaults() {
        return new PluginConfig();
    }

    /** Applies a freshly loaded config in place so live services see changes. */
    public void updateFrom(PluginConfig other) {
        if (other == null) {
            return;
        }
        defaultLanguage = other.defaultLanguage;
        allowLanguageSwitch = other.allowLanguageSwitch;
        defaultTheme = other.defaultTheme;
        defaultMenu = other.defaultMenu;
        autoReload = other.autoReload;
        autoReloadDebounce = other.autoReloadDebounce;
        resourcePack = other.resourcePack;
        clickDebounceMs = other.clickDebounceMs;
        dataThreads = other.dataThreads;
        searchThreads = other.searchThreads;
        placeholderCacheTtl = other.placeholderCacheTtl;
        metrics = other.metrics;
        currencyFormat = other.currencyFormat;
        searchInput = other.searchInput;
        maxSearchLength = other.maxSearchLength;
        clickSound = other.clickSound;
        openSound = other.openSound;
        debug = other.debug;
        actionErrorMode = other.actionErrorMode;
    }

    private static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "zh_CN";
        }
        return raw.trim();
    }

    public String defaultLanguage() {
        return defaultLanguage;
    }

    public boolean allowLanguageSwitch() {
        return allowLanguageSwitch;
    }

    public String defaultTheme() {
        return defaultTheme;
    }

    public String defaultMenu() {
        return defaultMenu;
    }

    public boolean autoReload() {
        return autoReload;
    }

    public Duration autoReloadDebounce() {
        return autoReloadDebounce;
    }

    public boolean resourcePack() {
        return resourcePack;
    }

    public int clickDebounceMs() {
        return clickDebounceMs;
    }

    public int dataThreads() {
        return dataThreads;
    }

    public int searchThreads() {
        return searchThreads;
    }

    public Duration placeholderCacheTtl() {
        return placeholderCacheTtl;
    }

    public boolean metrics() {
        return metrics;
    }

    public String currencyFormat() {
        return currencyFormat;
    }

    public String searchInput() {
        return searchInput;
    }

    public int maxSearchLength() {
        return maxSearchLength;
    }

    public boolean clickSound() {
        return clickSound;
    }

    public boolean openSound() {
        return openSound;
    }

    public boolean debug() {
        return debug;
    }

    public String actionErrorMode() {
        return actionErrorMode;
    }
}
