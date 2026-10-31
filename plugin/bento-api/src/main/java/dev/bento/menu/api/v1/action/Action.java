package dev.bento.menu.api.v1.action;

/**
 * A single executable step. Actions run sequentially on the player's owning
 * thread; implementations may offload slow work through the provided scheduler.
 */
@FunctionalInterface
public interface Action {

    void execute(ActionContext context) throws Exception;
}
