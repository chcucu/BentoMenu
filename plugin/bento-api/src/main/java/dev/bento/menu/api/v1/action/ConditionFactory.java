package dev.bento.menu.api.v1.action;

import dev.bento.menu.api.v1.config.ConditionConfig;

/**
 * Creates a YAML-configurable condition registered by an addon.
 */
@FunctionalInterface
public interface ConditionFactory {

    Condition create(ConditionConfig config) throws Exception;
}
