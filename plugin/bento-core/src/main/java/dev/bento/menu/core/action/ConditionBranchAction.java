package dev.bento.menu.core.action;

import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.action.ActionContext;
import dev.bento.menu.api.v1.action.Condition;
import dev.bento.menu.api.v1.action.ConditionContext;
import dev.bento.menu.core.condition.ConditionEvaluator;

import java.util.List;

/**
 * Branching action ({@code type: condition}).
 */
public final class ConditionBranchAction implements Action {

    private final List<Condition> conditions;
    private final List<Action> thenActions;
    private final List<Action> elseActions;

    public ConditionBranchAction(List<Condition> conditions, List<Action> thenActions, List<Action> elseActions) {
        this.conditions = List.copyOf(conditions);
        this.thenActions = List.copyOf(thenActions);
        this.elseActions = List.copyOf(elseActions);
    }

    @Override
    public void execute(ActionContext context) throws Exception {
        ConditionContext conditionContext = ConditionEvaluator.context(
                context.player(), context.session(), context.entry(), context.slot(),
                context.api(), context.economy(), context.placeholders());
        ConditionEvaluator.Failure failure = ConditionEvaluator.firstFailure(conditions, conditionContext);
        List<Action> branch = failure == null ? thenActions : elseActions;
        for (Action action : branch) {
            action.execute(context);
        }
    }
}
