package dev.bento.menu.core.action;

import dev.bento.menu.api.v1.action.ActionFactory;
import dev.bento.menu.api.v1.exception.BentoException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Addon-supplied action factories. Built-in ids are reserved.
 */
public final class ActionRegistry {

    static final Set<String> BUILT_INS = Set.of(
            "message", "msg", "actionbar", "title", "sound", "player-command", "player",
            "console-command", "console", "give-item", "give", "take-item", "take",
            "give-money", "take-money", "open-menu", "open", "close-menu", "close",
            "page", "goto-page", "theme", "refresh", "search", "category", "buy",
            "condition", "if"
    );

    private final Map<String, ActionFactory> factories = new ConcurrentHashMap<>();

    public void register(String id, ActionFactory factory) {
        if (id == null || id.isBlank()) {
            throw new BentoException("Action id must not be blank");
        }
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if (BUILT_INS.contains(key)) {
            throw new BentoException("Action id is reserved: " + id);
        }
        ActionFactory previous = factories.putIfAbsent(key, factory);
        if (previous != null) {
            throw new BentoException("Action already registered: " + id);
        }
    }

    public ActionFactory get(String id) {
        return id == null ? null : factories.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public Map<String, ActionFactory> snapshot() {
        return Map.copyOf(factories);
    }
}
