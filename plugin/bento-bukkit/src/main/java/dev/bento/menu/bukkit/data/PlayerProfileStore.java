package dev.bento.menu.bukkit.data;

import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.core.config.MessageCatalog;
import dev.bento.menu.core.config.PluginConfig;
import dev.bento.menu.core.theme.ThemeRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Player preferences (theme, language) persisted asynchronously. Reads are
 * memory-only so menu rendering never performs file I/O.
 */
public final class PlayerProfileStore {

    private static final class Profile {
        private volatile String theme;
        private volatile String language;
    }

    private final Plugin plugin;
    private final PluginConfig config;
    private final ThemeRegistry themes;
    private volatile MessageCatalog messages;
    private final File file;
    private final ScheduledExecutorService executor;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean saving = new AtomicBoolean();

    public PlayerProfileStore(Plugin plugin, PluginConfig config, ThemeRegistry themes,
                              MessageCatalog messages, File file, ScheduledExecutorService executor) {
        this.plugin = plugin;
        this.config = config;
        this.themes = themes;
        this.messages = messages;
        this.file = file;
        this.executor = executor;
        load();
        executor.scheduleAtFixedRate(this::saveIfDirty, 30, 30, TimeUnit.SECONDS);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String rawId : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                ConfigurationSection section = players.getConfigurationSection(rawId);
                if (section == null) {
                    continue;
                }
                Profile profile = new Profile();
                profile.theme = blankToNull(section.getString("theme"));
                profile.language = blankToNull(section.getString("language"));
                profiles.put(id, profile);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid player UUID in playerdata.yml: " + rawId);
            }
        }
    }

    public void setCatalog(MessageCatalog catalog) {
        if (catalog != null) {
            this.messages = catalog;
        }
    }

    public String languageFor(Player player) {
        Profile profile = player == null ? null : profiles.get(player.getUniqueId());
        String candidate = profile == null ? null : profile.language;
        if (candidate != null && messages.locales().contains(candidate)) {
            return candidate;
        }
        String fallback = MessageCatalog.normalizeLocale(config.defaultLanguage());
        if (messages.locales().contains(fallback)) {
            return fallback;
        }
        return messages.locales().stream().findFirst().orElse(fallback);
    }

    public boolean setLanguage(Player player, String locale) {
        String normalized = MessageCatalog.normalizeLocale(locale);
        if (!messages.locales().contains(normalized)) {
            return false;
        }
        profile(player).language = normalized;
        dirty.set(true);
        return true;
    }

    public void setTheme(Player player, String themeId) {
        if (themeId == null || themes.get(themeId).isEmpty()) {
            return;
        }
        profile(player).theme = themeId;
        dirty.set(true);
    }

    public String savedThemeId(Player player) {
        Profile profile = player == null ? null : profiles.get(player.getUniqueId());
        return profile == null ? null : profile.theme;
    }

    public Theme themeFor(Player player) {
        String candidate = savedThemeId(player);
        Theme theme = candidate == null ? null : themes.get(candidate).orElse(null);
        if (!allowed(player, theme)) {
            theme = themes.get(config.defaultTheme()).orElse(null);
        }
        if (!allowed(player, theme)) {
            theme = themes.snapshot().stream()
                    .filter(value -> allowed(player, value))
                    .findFirst()
                    .orElse(null);
        }
        return theme;
    }

    public String themeIdFor(Player player) {
        Theme theme = themeFor(player);
        return theme == null ? config.defaultTheme() : theme.id();
    }

    private boolean allowed(Player player, Theme theme) {
        if (theme == null) {
            return false;
        }
        String permission = theme.permission();
        return permission == null || permission.isBlank()
                || player == null || player.hasPermission(permission);
    }

    public void flushAsync() {
        executor.execute(this::saveIfDirty);
    }

    public void saveNow() {
        saveIfDirty();
    }

    private void saveIfDirty() {
        if (!dirty.get()) {
            return;
        }
        if (!saving.compareAndSet(false, true)) {
            return;
        }
        boolean success = true;
        try {
            dirty.set(false);
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Profile> entry : profiles.entrySet()) {
                Profile profile = entry.getValue();
                String base = "players." + entry.getKey();
                if (profile.theme != null) {
                    yaml.set(base + ".theme", profile.theme);
                }
                if (profile.language != null) {
                    yaml.set(base + ".language", profile.language);
                }
            }
            Path target = file.toPath();
            Path temp = target.resolveSibling(file.getName() + ".tmp");
            try {
                Files.createDirectories(target.getParent());
            } catch (IOException ignored) {
                // The data folder normally exists.
            }
            yaml.save(temp.toFile());
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            success = false;
            dirty.set(true);
            plugin.getLogger().warning("Could not save playerdata.yml: " + exception.getMessage());
        } finally {
            saving.set(false);
        }
        if (!success) {
            // Retried by the periodic task or the next mutation.
        }
    }

    private Profile profile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), ignored -> new Profile());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
