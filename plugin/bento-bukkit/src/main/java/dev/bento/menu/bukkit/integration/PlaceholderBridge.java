package dev.bento.menu.bukkit.integration;

import dev.bento.menu.api.v1.service.PlaceholderService;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asynchronous, never-blocking PlaceholderAPI cache.
 *
 * <p>Placeholders are resolved on a single worker thread to avoid unsafe
 * concurrent expansion calls. Callers always receive the last cached value
 * immediately; the first resolution is scheduled and appears on the next
 * refresh, keeping ticks and click feedback fast.</p>
 */
public final class PlaceholderBridge implements PlaceholderService {

    private static final Pattern TOKEN = Pattern.compile("%([^%]+)%");

    private final org.bukkit.plugin.Plugin plugin;
    private final ExecutorService executor;
    private final long ttlNanos;
    private final Map<UUID, Map<String, CacheEntry>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<String>> inFlight = new ConcurrentHashMap<>();
    private final AtomicBoolean warned = new AtomicBoolean();
    private final boolean available;

    public PlaceholderBridge(org.bukkit.plugin.Plugin plugin, ExecutorService executor, Duration ttl) {
        this.plugin = plugin;
        this.executor = executor;
        this.ttlNanos = Math.max(1, ttl == null ? 3_000_000_000L : ttl.toNanos());
        this.available = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    @Override
    public String resolve(Player player, String text) {
        if (text == null || text.isEmpty() || !available || player == null) {
            return text;
        }
        Matcher matcher = TOKEN.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int last = 0;
        matcher.reset();
        while (matcher.find()) {
            out.append(text, last, matcher.start());
            String token = matcher.group();
            CacheEntry entry = entry(player.getUniqueId(), token);
            if (entry != null) {
                // Serve the previous value even when stale so a refresh never
                // flashes a raw %placeholder% token to the client.
                out.append(entry.value);
                if (System.nanoTime() - entry.resolvedAt > ttlNanos) {
                    refresh(player, token);
                }
            } else {
                out.append(token);
                refresh(player, token);
            }
            last = matcher.end();
        }
        out.append(text, last, text.length());
        return out.toString();
    }

    @Override
    public void preload(Player player, Collection<String> texts) {
        if (!available || player == null || texts == null) {
            return;
        }
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = TOKEN.matcher(text);
            while (matcher.find()) {
                String token = matcher.group();
                CacheEntry entry = entry(player.getUniqueId(), token);
                if (entry == null || System.nanoTime() - entry.resolvedAt > ttlNanos) {
                    refresh(player, token);
                }
            }
        }
    }

    @Override
    public void invalidate(Player player, String text) {
        if (!available || player == null || text == null) {
            return;
        }
        Map<String, CacheEntry> entries = cache.get(player.getUniqueId());
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (entries != null) {
                entries.remove(token);
            }
            refresh(player, token);
        }
    }

    public void clear(Player player) {
        if (player != null) {
            cache.remove(player.getUniqueId());
            inFlight.remove(player.getUniqueId());
        }
    }

    private CacheEntry entry(UUID playerId, String token) {
        Map<String, CacheEntry> entries = cache.get(playerId);
        return entries == null ? null : entries.get(token);
    }

    private void refresh(Player player, String token) {
        if (!available || player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        java.util.Set<String> pending = inFlight.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet());
        if (!pending.add(token)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    if (!player.isOnline()) {
                        return;
                    }
                    String value = PlaceholderAPI.setPlaceholders(player, token);
                    cache.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                            .put(token, new CacheEntry(value, System.nanoTime()));
                } catch (RuntimeException exception) {
                    if (warned.compareAndSet(false, true)) {
                        plugin.getLogger().warning("PlaceholderAPI resolution failed: " + exception.getMessage());
                    }
                } finally {
                    pending.remove(token);
                }
            });
        } catch (RuntimeException exception) {
            pending.remove(token);
        }
    }

    private record CacheEntry(String value, long resolvedAt) {
    }
}
