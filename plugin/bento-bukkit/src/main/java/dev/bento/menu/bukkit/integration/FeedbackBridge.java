package dev.bento.menu.bukkit.integration;

import dev.bento.menu.api.v1.Feedback;
import dev.bento.menu.api.v1.service.FeedbackService;
import dev.bento.menu.api.v1.service.MessageService;
import dev.bento.menu.api.v1.service.Scheduler;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/**
 * Version-compatible sound/title/action-bar output.
 */
public final class FeedbackBridge implements FeedbackService {

    private final org.bukkit.plugin.Plugin plugin;

    public FeedbackBridge(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void sound(Player player, String soundKey, float volume, float pitch) {
        if (player == null || soundKey == null || soundKey.isBlank() || soundKey.equalsIgnoreCase("none")) {
            return;
        }
        try {
            player.playSound(player.getLocation(), soundKey, volume, pitch);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid sound '" + soundKey + "': " + exception.getMessage());
        }
    }

    @Override
    public void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) {
            return;
        }
        try {
            player.sendTitle(title == null ? "" : title, subtitle == null ? "" : subtitle,
                    Math.max(0, fadeIn), Math.max(1, stay), Math.max(0, fadeOut));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not send title: " + exception.getMessage());
        }
    }

    @Override
    public void actionBar(Player player, String text) {
        if (player == null) {
            return;
        }
        String value = text == null ? "" : text;
        try {
            java.lang.reflect.Method method = player.getClass().getMethod("sendActionBar", String.class);
            method.invoke(player, value);
            return;
        } catch (ReflectiveOperationException ignored) {
            // Spigot 1.18 exposes the action bar through the Bungee chat API.
        }
        try {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(value));
        } catch (RuntimeException ignored) {
            // Action bars are optional feedback.
        }
    }

    /**
     * Plays a button feedback object. Sound is played immediately; title frames
     * are rendered and scheduled on the player's owning thread.
     */
    public void play(Player player, Feedback feedback, Scheduler scheduler, MessageService messages) {
        if (player == null || feedback == null) {
            return;
        }
        if (feedback.sound() != null) {
            sound(player, feedback.sound(), feedback.volume(), feedback.pitch());
        }
        playTitle(player, feedback, scheduler, messages);
    }

    public void playTitle(Player player, Feedback feedback, Scheduler scheduler, MessageService messages) {
        if (player == null || feedback == null) {
            return;
        }
        String subtitle = feedback.subtitle() == null
                ? null : messages.render(player, feedback.subtitle());
        List<String> frames = feedback.titleFrames();
        if (frames == null || frames.isEmpty()) {
            if (feedback.subtitle() != null) {
                title(player, "", subtitle, feedback.fadeIn(), feedback.stay(), feedback.fadeOut());
            }
            return;
        }
        Duration interval = feedback.frameInterval();
        for (int i = 0; i < frames.size(); i++) {
            String frame = frames.get(i);
            int index = i;
            Runnable task = () -> title(player,
                    messages.render(player, frame),
                    subtitle,
                    feedback.fadeIn(),
                    feedback.stay(),
                    feedback.fadeOut());
            if (index == 0) {
                task.run();
            } else {
                scheduler.runLater(task, interval.multipliedBy(index));
            }
        }
    }
}
