package dev.bento.menu.core.condition;

import dev.bento.menu.api.v1.BentoApi;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuSessionView;
import dev.bento.menu.api.v1.action.Condition;
import dev.bento.menu.api.v1.action.ConditionContext;
import dev.bento.menu.api.v1.service.EconomyService;
import dev.bento.menu.api.v1.service.PlaceholderService;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Condition evaluation helpers shared by clicks and action chains.
 */
public final class ConditionEvaluator {

    public record Failure(String message) {
    }

    private ConditionEvaluator() {
    }

    public static Failure firstFailure(List<Condition> conditions, ConditionContext context) {
        if (conditions == null) {
            return null;
        }
        for (Condition condition : conditions) {
            try {
                if (!condition.test(context)) {
                    String message = condition instanceof ConfiguredCondition configured
                            ? configured.denyMessage()
                            : null;
                    return new Failure(message);
                }
            } catch (Exception exception) {
                return new Failure(exception.getMessage());
            }
        }
        return null;
    }

    public static ConditionContext context(Player player, MenuSessionView session, MenuEntry entry,
                                            int slot, BentoApi api, EconomyService economy,
                                            PlaceholderService placeholders) {
        return new SimpleConditionContext(player, session, entry, slot, api, economy, placeholders);
    }

    private record SimpleConditionContext(Player player, MenuSessionView session, MenuEntry entry, int slot,
                                          BentoApi api, EconomyService economy,
                                          PlaceholderService placeholders) implements ConditionContext {
    }
}
