package dev.bento.menu.api.v1.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Read-only view over a YAML configuration node passed to addon factories.
 * The implementation is supplied by BentoMenu; addons never construct it.
 */
public interface ConfigurationView {

    String key();

    Object raw();

    boolean has(String path);

    String getString(String path);

    String getString(String path, String fallback);

    int getInt(String path, int fallback);

    long getLong(String path, long fallback);

    double getDouble(String path, double fallback);

    boolean getBoolean(String path, boolean fallback);

    Duration getDuration(String path, Duration fallback);

    List<String> getStringList(String path);

    List<ConfigurationView> getList(String path);

    /** Snapshot of the raw key/value map at this node. */
    Map<String, Object> values();
}
