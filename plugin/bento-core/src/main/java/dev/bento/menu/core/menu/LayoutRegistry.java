package dev.bento.menu.core.menu;

import dev.bento.menu.api.v1.Layout;
import dev.bento.menu.api.v1.MenuDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves reusable layout definitions.
 */
public final class LayoutRegistry {

    private final Map<String, Layout> layouts = new LinkedHashMap<>();

    public synchronized void put(String id, Layout layout) {
        layouts.put(id, layout);
    }

    public synchronized void replaceAll(Map<String, Layout> next) {
        layouts.clear();
        layouts.putAll(next);
    }

    public synchronized Map<String, Layout> snapshot() {
        return Map.copyOf(layouts);
    }

    public synchronized Optional<Layout> get(String id) {
        return Optional.ofNullable(layouts.get(id));
    }

    public synchronized Collection<String> ids() {
        return List.copyOf(layouts.keySet());
    }

    public Layout resolve(MenuDefinition definition) {
        if (definition.layout() != null) {
            return definition.layout();
        }
        if (definition.layoutId() == null || definition.layoutId().isBlank()) {
            return null;
        }
        return get(definition.layoutId()).orElse(null);
    }
}
