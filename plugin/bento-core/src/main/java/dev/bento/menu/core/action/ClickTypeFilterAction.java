package dev.bento.menu.core.action;

import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.action.ActionContext;
import org.bukkit.event.inventory.ClickType;

import java.util.Set;

/**
 * Restricts an action to specific click types (for example left-click applies,
 * right-click previews).
 */
public final class ClickTypeFilterAction implements Action {

    private final Action delegate;
    private final Set<ClickType> clickTypes;

    public ClickTypeFilterAction(Action delegate, Set<ClickType> clickTypes) {
        this.delegate = delegate;
        this.clickTypes = Set.copyOf(clickTypes);
    }

    @Override
    public void execute(ActionContext context) throws Exception {
        if (clickTypes.contains(context.clickType())) {
            delegate.execute(context);
        }
    }
}
