package dev.bento.menu.api.v1.service;

import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Cached PlaceholderAPI resolver. resolve() never blocks: it returns the last
 * known value (or the untouched placeholder) and refreshes asynchronously.
 */
public interface PlaceholderService {

    String resolve(Player player, String text);

    void preload(Player player, Collection<String> texts);

    /** Forces an asynchronous refresh of placeholders found in the text. */
    void invalidate(Player player, String text);
}
