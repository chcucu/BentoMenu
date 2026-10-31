package dev.bento.menu.core.action;

import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.action.ActionContext;

import java.util.Map;

/**
 * Reports a configuration problem at click time and stops the chain.
 */
public final class UnsupportedAction implements Action {

    private final String type;

    public UnsupportedAction(String type) {
        this.type = type;
    }

    @Override
    public void execute(ActionContext context) {
        context.messages().send(context.player(), "action.unknown", Map.of("type", String.valueOf(type)));
        throw new ActionAbortException("unsupported action: " + type);
    }
}
