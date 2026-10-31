package dev.bento.menu.api.v1.action;

/**
 * A predicate evaluated before actions. Conditions must be side-effect free.
 */
@FunctionalInterface
public interface Condition {

    boolean test(ConditionContext context) throws Exception;
}
