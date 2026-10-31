package dev.bento.menu.api.v1.config;

/**
 * Configuration passed to an {@link dev.bento.menu.api.v1.action.ActionFactory}.
 */
public interface ActionConfig extends ConfigurationView {

    /** Registered action id, for example {@code message} or {@code my-action}. */
    String type();
}
