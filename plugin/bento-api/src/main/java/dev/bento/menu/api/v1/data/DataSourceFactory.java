package dev.bento.menu.api.v1.data;

import dev.bento.menu.api.v1.config.DataSourceConfig;

/**
 * Creates a YAML-registerable data source.
 */
@FunctionalInterface
public interface DataSourceFactory {

    DataSource create(DataSourceConfig config) throws Exception;
}
