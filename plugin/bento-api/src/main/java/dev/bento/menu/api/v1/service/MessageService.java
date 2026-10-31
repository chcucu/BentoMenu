package dev.bento.menu.api.v1.service;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Localised, fully configurable message service.
 */
public interface MessageService {

    void send(Player player, String key);

    void send(Player player, String key, Map<String, Object> placeholders);

    void sendRaw(Player player, String text);

    /** Renders any text template for the player, including prefix if requested. */
    String render(Player player, String text);

    /** Renders a configured key without sending it. */
    String renderKey(Player player, String key, Map<String, Object> placeholders);
}
