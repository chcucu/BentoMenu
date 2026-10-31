package dev.bento.menu.core.action;

import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.action.ActionContext;
import dev.bento.menu.api.v1.action.Condition;
import dev.bento.menu.api.v1.action.ConditionContext;
import dev.bento.menu.core.condition.ConditionEvaluator;

import java.util.List;

/**
 * Runs an action only when all attached conditions pass. A failing step with
 * deny actions stops the chain; a failing step without deny actions skips only
 * this step.
 */
public final class ConditionalAction implements Action {

    private final Action delegate;
    private final List<Condition> conditions;
    private final List<Action> denyActions;

    public ConditionalAction(Action delegate, List<Condition> conditions, List<Action> denyActions) {
        this.delegate = delegate;
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
        this.denyActions = denyActions == null ? List.of() : List.copyOf(denyActions);
    }

    @Override
    public void execute(ActionContext context) throws Exception {
        if (conditions.isEmpty()) {
            delegate.execute(context);
            return;
        }
        ConditionContext conditionContext = ConditionEvaluator.context(
                context.player(), context.session(), context.entry(), context.slot(),
                context.api(), context.economy(), context.placeholders());
        ConditionEvaluator.Failure failure = ConditionEvaluator.firstFailure(conditions, conditionContext);
        if (failure == null) {
            delegate.execute(context);
            return;
        }
        if (!denyActions.isEmpty()) {
            for (Action action : denyActions) {
                action.execute(context);
            }
            throw new ActionAbortException("condition failed");
        }
        if (failure.message() != null && !failure.message().isBlank()) {
            context.messages().sendRaw(context.player(), failure.message());
        } else {
            context.messages().send(context.player(), "condition.denied");
        }
    }
}
