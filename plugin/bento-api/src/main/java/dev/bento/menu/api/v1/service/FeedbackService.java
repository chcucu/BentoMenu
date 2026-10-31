package dev.bento.menu.api.v1.service;

import org.bukkit.entity.Player;

/**
 * Version-compatible player feedback (sounds, titles, action bars).
 */
public interface FeedbackService {

    void sound(Player player, String soundKey, float volume, float pitch);

    void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut);

    void actionBar(Player player, String text);
}
