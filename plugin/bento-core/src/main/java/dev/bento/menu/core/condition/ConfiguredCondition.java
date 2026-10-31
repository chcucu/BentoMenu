package dev.bento.menu.core.condition;

import dev.bento.menu.api.v1.action.Condition;

/**
 * Internal wrapper carrying a per-condition denial message while remaining a
 * plain {@link Condition} for addons.
 */
public final class ConfiguredCondition implements Condition {

    private final Condition delegate;
    private final String denyMessage;

    public ConfiguredCondition(Condition delegate, String denyMessage) {
        this.delegate = delegate;
        this.denyMessage = denyMessage;
    }

    @Override
    public boolean test(dev.bento.menu.api.v1.action.ConditionContext context) throws Exception {
        return delegate.test(context);
    }

    public String denyMessage() {
        return denyMessage;
    }

    public Condition delegate() {
        return delegate;
    }
}
