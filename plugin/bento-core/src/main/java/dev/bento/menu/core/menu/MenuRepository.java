package dev.bento.menu.core.menu;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thread-safe registry of compiled menus. YAML reloads replace only YAML menus;
 * API-registered menus survive reloads and take precedence on id conflicts.
 */
public final class MenuRepository {

    private final Map<String, CompiledMenu> menus = new LinkedHashMap<>();
    private final Set<String> apiIds = new HashSet<>();

    public synchronized CompiledMenu putApi(CompiledMenu menu) {
        apiIds.add(menu.id());
        return menus.put(menu.id(), menu);
    }

    public synchronized Optional<CompiledMenu> removeApi(String id) {
        if (!apiIds.remove(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(menus.remove(id));
    }

    /** Replaces all YAML menus while preserving API menus. */
    public synchronized Set<String> replaceYaml(Map<String, CompiledMenu> yamlMenus) {
        menus.keySet().removeIf(id -> !apiIds.contains(id));
        Set<String> skipped = new HashSet<>();
        for (Map.Entry<String, CompiledMenu> entry : yamlMenus.entrySet()) {
            if (apiIds.contains(entry.getKey())) {
                skipped.add(entry.getKey());
            } else {
                menus.put(entry.getKey(), entry.getValue());
            }
        }
        return skipped;
    }

    public synchronized Optional<CompiledMenu> get(String id) {
        return Optional.ofNullable(menus.get(id));
    }

    public synchronized Collection<CompiledMenu> snapshot() {
        return List.copyOf(menus.values());
    }

    public synchronized List<String> ids() {
        return List.copyOf(menus.keySet());
    }

    public synchronized Map<String, CompiledMenu> map() {
        return Map.copyOf(menus);
    }

    public synchronized int size() {
        return menus.size();
    }
}
