package dev.bento.menu.core.data;

import dev.bento.menu.api.v1.data.DataSourceFactory;
import dev.bento.menu.api.v1.exception.BentoException;
import dev.bento.menu.core.config.ConfigView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factories for YAML-registered data sources. Resolution is lazy so addons can
 * register after BentoMenu has loaded its YAML menus.
 */
public final class DataSourceRegistry {

    private final Map<String, DataSourceFactory> factories = new ConcurrentHashMap<>();

    public void register(String id, DataSourceFactory factory) {
        if (id == null || id.isBlank()) {
            throw new BentoException("Data source id must not be blank");
        }
        String key = id.toLowerCase(java.util.Locale.ROOT);
        DataSourceFactory previous = factories.putIfAbsent(key, factory);
        if (previous != null) {
            throw new BentoException("Data source already registered: " + id);
        }
    }

    public DataSourceFactory get(String id) {
        return id == null ? null : factories.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public Map<String, DataSourceFactory> snapshot() {
        return Map.copyOf(factories);
    }

    public dev.bento.menu.api.v1.data.DataSource create(String id, ConfigView config) throws Exception {
        DataSourceFactory factory = get(id);
        if (factory == null) {
            return null;
        }
        ConfigView view = config == null ? ConfigView.of(Map.of()) : config;
        return factory.create(view.asDataSource());
    }
}
