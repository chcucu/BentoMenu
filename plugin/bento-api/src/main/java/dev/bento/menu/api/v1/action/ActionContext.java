package dev.bento.menu.api.v1.action;

import dev.bento.menu.api.v1.BentoApi;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuSessionView;
import dev.bento.menu.api.v1.service.EconomyService;
import dev.bento.menu.api.v1.service.FeedbackService;
import dev.bento.menu.api.v1.service.ItemService;
import dev.bento.menu.api.v1.service.MessageService;
import dev.bento.menu.api.v1.service.PlaceholderService;
import dev.bento.menu.api.v1.service.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Context available to an action. For actions that are not triggered by a
 * click (open/close hooks), {@link #session()} or {@link #entry()} may be null
 * and {@link #slot()} is {@code -1}.
 */
public interface ActionContext {

    Player player();

    MenuSessionView session();

    MenuEntry entry();

    int slot();

    ClickType clickType();

    BentoApi api();

    Scheduler scheduler();

    EconomyService economy();

    PlaceholderService placeholders();

    MessageService messages();

    FeedbackService feedback();

    ItemService items();

    /** Coalesced re-render of the current view. */
    void refresh();
}
