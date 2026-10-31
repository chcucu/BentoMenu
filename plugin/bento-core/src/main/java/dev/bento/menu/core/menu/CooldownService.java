package dev.bento.menu.core.menu;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player cooldowns and click debounce. Kept in memory so a click never
 * performs I/O; values survive menu navigation but not server restarts.
 */
public final class CooldownService {

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public long remainingMillis(UUID playerId, String key) {
        Map<String, Long> map = cooldowns.get(playerId);
        if (map == null) {
            return 0;
        }
        Long expiry = map.get(key);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.nanoTime();
        if (remaining <= 0) {
            map.remove(key);
            return 0;
        }
        return Math.max(1, remaining / 1_000_000L);
    }

    public void set(UUID playerId, String key, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        long expiry = System.nanoTime() + duration.toNanos();
        cooldowns.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(key, expiry);
    }

    public void clear(UUID playerId) {
        cooldowns.remove(playerId);
        lastClick.remove(playerId);
    }

    public boolean acquireClick(UUID playerId, Duration debounce) {
        long now = System.nanoTime();
        long window = debounce == null ? 0 : Math.max(0, debounce.toNanos());
        Long previous = lastClick.get(playerId);
        if (previous != null && now - previous < window) {
            return false;
        }
        return lastClick.merge(playerId, now, (old, value) -> now - old < window ? old : value) == now;
    }

    public int trackedPlayers() {
        return cooldowns.size();
    }
}
