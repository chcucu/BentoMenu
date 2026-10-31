package dev.bento.menu.core.condition;

import dev.bento.menu.api.v1.action.ConditionFactory;
import dev.bento.menu.api.v1.exception.BentoException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Addon-supplied condition factories. Built-in ids are reserved.
 */
public final class ConditionRegistry {

    static final Set<String> BUILT_INS = Set.of(
            "permission", "money", "item", "time", "chance", "placeholder",
            "world-time", "server-time"
    );

    private final Map<String, ConditionFactory> factories = new ConcurrentHashMap<>();

    public void register(String id, ConditionFactory factory) {
        if (id == null || id.isBlank()) {
            throw new BentoException("Condition id must not be blank");
        }
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if (BUILT_INS.contains(key)) {
            throw new BentoException("Condition id is reserved: " + id);
        }
        ConditionFactory previous = factories.putIfAbsent(key, factory);
        if (previous != null) {
            throw new BentoException("Condition already registered: " + id);
        }
    }

    public ConditionFactory get(String id) {
        return id == null ? null : factories.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public Map<String, ConditionFactory> snapshot() {
        return Map.copyOf(factories);
    }
}
