package dev.bento.menu.api.v1.action;

import dev.bento.menu.api.v1.config.ActionConfig;

/**
 * Creates a YAML-configurable action registered by an addon.
 */
@FunctionalInterface
public interface ActionFactory {

    Action create(ActionConfig config) throws Exception;
}
