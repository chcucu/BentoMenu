package dev.bento.menu.api.v1.action;

import dev.bento.menu.api.v1.BentoApi;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuSessionView;
import dev.bento.menu.api.v1.service.EconomyService;
import dev.bento.menu.api.v1.service.PlaceholderService;
import org.bukkit.entity.Player;

/**
 * Context available to a condition.
 */
public interface ConditionContext {

    Player player();

    MenuSessionView session();

    MenuEntry entry();

    int slot();

    BentoApi api();

    EconomyService economy();

    PlaceholderService placeholders();
}
