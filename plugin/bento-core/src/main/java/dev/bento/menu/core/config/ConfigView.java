package dev.bento.menu.core.config;

import dev.bento.menu.api.v1.config.ActionConfig;
import dev.bento.menu.api.v1.config.ConditionConfig;
import dev.bento.menu.api.v1.config.ConfigurationView;
import dev.bento.menu.api.v1.config.DataSourceConfig;
import dev.bento.menu.core.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter exposing Bukkit configuration sections (and plain maps) to addon
 * factories without leaking implementation classes.
 */
public class ConfigView implements ConfigurationView {

    protected final Object root;
    private final String key;

    protected ConfigView(Object root, String key) {
        this.root = root;
        this.key = key;
    }

    public static ConfigView of(ConfigurationSection section) {
        return new ConfigView(section, section.getName());
    }

    public static ConfigView of(Map<String, Object> map) {
        return new ConfigView(map, "map");
    }

    /**
     * Returns a child view when the value at {@code path} is a section or map.
     */
    public ConfigView child(String path) {
        Object value = value(path);
        if (value instanceof ConfigurationSection section) {
            return new ConfigView(section, section.getName());
        }
        if (value instanceof Map<?, ?> map) {
            return new ConfigView(toStringMap(map), path);
        }
        return null;
    }

    public ActionConfig asAction(String type) {
        return new ActionConfiguration(this, type);
    }

    public ConditionConfig asCondition(String type) {
        return new ConditionConfiguration(this, type);
    }

    public DataSourceConfig asDataSource() {
        return new DataSourceConfiguration(this);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Object raw() {
        return root;
    }

    public Object rawAt(String path) {
        return value(path);
    }

    @Override
    public boolean has(String path) {
        return value(path) != null;
    }

    @Override
    public String getString(String path) {
        Object value = value(path);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : String.valueOf(list.get(0));
        }
        return String.valueOf(value);
    }

    @Override
    public String getString(String path, String fallback) {
        String value = getString(path);
        return value == null ? fallback : value;
    }

    @Override
    public int getInt(String path, int fallback) {
        Object value = value(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @Override
    public long getLong(String path, long fallback) {
        Object value = value(path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @Override
    public double getDouble(String path, double fallback) {
        Object value = value(path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        Object value = value(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if (string.equalsIgnoreCase("true")) {
                return true;
            }
            if (string.equalsIgnoreCase("false")) {
                return false;
            }
        }
        return fallback;
    }

    @Override
    public Duration getDuration(String path, Duration fallback) {
        Object value = value(path);
        if (value == null) {
            return fallback;
        }
        return Durations.parse(String.valueOf(value), fallback);
    }

    @Override
    public List<String> getStringList(String path) {
        Object value = value(path);
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        if (value instanceof String string) {
            return List.of(string);
        }
        return List.of();
    }

    @Override
    public List<ConfigurationView> getList(String path) {
        Object value = value(path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<ConfigurationView> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof ConfigurationSection section) {
                out.add(new ConfigView(section, section.getName()));
            } else if (item instanceof Map<?, ?> map) {
                out.add(new ConfigView(toStringMap(map), "map"));
            }
        }
        return out;
    }

    @Override
    public Map<String, Object> values() {
        if (root instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        if (root instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(toStringMap(map));
        }
        return Map.of();
    }

    protected Object value(String path) {
        if (path == null || path.isBlank()) {
            return root;
        }
        Object current = root;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (current instanceof ConfigurationSection section) {
                current = section.get(part);
            } else if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    public static Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private static final class ActionConfiguration extends ConfigView implements ActionConfig {
        private final String type;

        private ActionConfiguration(ConfigView delegate, String type) {
            super(delegate.root, delegate.key());
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }
    }

    private static final class ConditionConfiguration extends ConfigView implements ConditionConfig {
        private final String type;

        private ConditionConfiguration(ConfigView delegate, String type) {
            super(delegate.root, delegate.key());
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }
    }

    private static final class DataSourceConfiguration extends ConfigView implements DataSourceConfig {
        private DataSourceConfiguration(ConfigView delegate) {
            super(delegate.root, delegate.key());
        }
    }
}
