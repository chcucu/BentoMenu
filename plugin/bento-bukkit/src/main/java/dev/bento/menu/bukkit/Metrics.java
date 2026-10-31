package dev.bento.menu.bukkit;

import dev.bento.menu.api.v1.action.Condition;
import dev.bento.menu.core.condition.ConfiguredCondition;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight counters used by /bento stats and load tests. Disabled counters
 * are still cheap (a single volatile check).
 */
public final class Metrics {

    private final boolean enabled;
    private final AtomicLong opens = new AtomicLong();
    private final AtomicLong closes = new AtomicLong();
    private final AtomicLong clicks = new AtomicLong();
    private final AtomicLong actions = new AtomicLong();
    private final AtomicLong denied = new AtomicLong();
    private final AtomicLong cooldownHits = new AtomicLong();
    private final AtomicLong renders = new AtomicLong();
    private final AtomicLong itemUpdates = new AtomicLong();
    private final AtomicLong renderNanos = new AtomicLong();
    private final AtomicLong maxRenderNanos = new AtomicLong();
    private final AtomicLong clickNanos = new AtomicLong();
    private final AtomicLong maxClickNanos = new AtomicLong();
    private final Map<UUID, Long> sessionOpenNanos = new ConcurrentHashMap<>();

    public Metrics(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void sessionOpened(UUID playerId) {
        if (!enabled) {
            return;
        }
        opens.incrementAndGet();
        sessionOpenNanos.put(playerId, System.nanoTime());
    }

    public void sessionClosed(UUID playerId) {
        if (!enabled) {
            return;
        }
        closes.incrementAndGet();
        sessionOpenNanos.remove(playerId);
    }

    public long sessionAgeMillis(UUID playerId) {
        Long nanos = sessionOpenNanos.get(playerId);
        return nanos == null ? 0 : (System.nanoTime() - nanos) / 1_000_000L;
    }

    public void click(InventoryClickEvent event, int slot) {
        if (!enabled) {
            return;
        }
        clicks.incrementAndGet();
    }

    public void action() {
        if (enabled) {
            actions.incrementAndGet();
        }
    }

    public void denied() {
        if (enabled) {
            denied.incrementAndGet();
        }
    }

    public void cooldownHit() {
        if (enabled) {
            cooldownHits.incrementAndGet();
        }
    }

    public void render(long nanos, int updates) {
        if (!enabled) {
            return;
        }
        renders.incrementAndGet();
        renderNanos.addAndGet(nanos);
        maxRenderNanos.accumulateAndGet(nanos, Math::max);
        itemUpdates.addAndGet(updates);
    }

    public void clickHandled(long nanos) {
        if (!enabled) {
            return;
        }
        clickNanos.addAndGet(nanos);
        maxClickNanos.accumulateAndGet(nanos, Math::max);
    }

    public Map<String, Object> snapshot(int sessions) {
        long opensValue = opens.get();
        long rendersValue = renders.get();
        long clicksValue = clicks.get();
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("sessions", sessions);
        out.put("opens", opensValue);
        out.put("closes", closes.get());
        out.put("clicks", clicksValue);
        out.put("actions", actions.get());
        out.put("denied", denied.get());
        out.put("cooldown-hits", cooldownHits.get());
        out.put("renders", rendersValue);
        out.put("item-updates", itemUpdates.get());
        out.put("avg-render-ms", rendersValue == 0 ? 0.0
                : renderNanos.get() / 1_000_000.0 / rendersValue);
        out.put("max-render-ms", maxRenderNanos.get() / 1_000_000.0);
        out.put("avg-click-ms", clicksValue == 0 ? 0.0
                : clickNanos.get() / 1_000_000.0 / clicksValue);
        out.put("max-click-ms", maxClickNanos.get() / 1_000_000.0);
        return out;
    }

    public void reset() {
        opens.set(0);
        closes.set(0);
        clicks.set(0);
        actions.set(0);
        denied.set(0);
        cooldownHits.set(0);
        renders.set(0);
        itemUpdates.set(0);
        renderNanos.set(0);
        maxRenderNanos.set(0);
        clickNanos.set(0);
        maxClickNanos.set(0);
    }
}
