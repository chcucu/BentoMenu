package dev.bento.menu.bukkit.input;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/**
 * Chat-based search fallback. Kept in its own class so servers that remove the
 * legacy chat event never fail to load the main plugin.
 */
final class ChatInputListener implements Listener {

    private static boolean registered;
    private final InputService inputService;

    private ChatInputListener(InputService inputService) {
        this.inputService = inputService;
    }

    static boolean register(Plugin plugin, InputService inputService) {
        if (registered) {
            return true;
        }
        try {
            Bukkit.getPluginManager().registerEvents(new ChatInputListener(inputService), plugin);
            registered = true;
            return true;
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (inputService.hasChatInput(player.getUniqueId())) {
            event.setCancelled(true);
            inputService.handleChat(player, event.getMessage());
        }
    }
}
