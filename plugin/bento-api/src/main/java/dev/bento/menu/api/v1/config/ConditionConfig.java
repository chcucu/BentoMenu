package dev.bento.menu.api.v1.config;

/**
 * Configuration passed to a {@link dev.bento.menu.api.v1.action.ConditionFactory}.
 */
public interface ConditionConfig extends ConfigurationView {

    String type();
}
