package dev.bento.menu.core.theme;

import dev.bento.menu.api.v1.theme.Theme;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thread-safe theme registry. API themes survive YAML reloads and take
 * precedence on id conflicts.
 */
public final class ThemeRegistry {

    private final Map<String, Theme> themes = new LinkedHashMap<>();
    private final Set<String> apiIds = new HashSet<>();

    public synchronized void putApi(Theme theme) {
        apiIds.add(theme.id());
        themes.put(theme.id(), theme);
    }

    public synchronized Optional<Theme> removeApi(String id) {
        if (!apiIds.remove(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(themes.remove(id));
    }

    public synchronized Set<String> replaceYaml(Map<String, Theme> next) {
        themes.keySet().removeIf(id -> !apiIds.contains(id));
        Set<String> skipped = new HashSet<>();
        for (Map.Entry<String, Theme> entry : next.entrySet()) {
            if (apiIds.contains(entry.getKey())) {
                skipped.add(entry.getKey());
            } else {
                themes.put(entry.getKey(), entry.getValue());
            }
        }
        return skipped;
    }

    public synchronized Optional<Theme> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(themes.get(id));
    }

    public synchronized Collection<Theme> snapshot() {
        return List.copyOf(themes.values());
    }

    public synchronized Map<String, Theme> map() {
        return Map.copyOf(themes);
    }

    public synchronized List<String> ids() {
        return List.copyOf(themes.keySet());
    }

    public synchronized int size() {
        return themes.size();
    }
}
