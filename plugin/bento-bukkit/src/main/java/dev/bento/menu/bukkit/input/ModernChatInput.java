package dev.bento.menu.bukkit.input;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Dynamic bridge for Paper's Adventure AsyncChatEvent. No compile-time
 * reference keeps the same jar loadable on Spigot 1.18 and on 26.x.
 */
final class ModernChatInput implements Listener {

    private static boolean registered;

    private ModernChatInput() {
    }

    static boolean register(Plugin plugin, InputService inputService) {
        if (registered) {
            return true;
        }
        try {
            Class<?> eventClass = Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            if (!Event.class.isAssignableFrom(eventClass)) {
                return false;
            }
            Listener listener = new ModernChatInput();
            Bukkit.getPluginManager().registerEvent(
                    eventClass.asSubclass(Event.class),
                    listener,
                    EventPriority.LOWEST,
                    (ignored, event) -> handle(event, inputService),
                    plugin
            );
            registered = true;
            return true;
        } catch (ClassNotFoundException | RuntimeException | LinkageError error) {
            return false;
        }
    }

    private static void handle(Event event, InputService inputService) {
        try {
            Method playerMethod = event.getClass().getMethod("getPlayer");
            Object playerValue = playerMethod.invoke(event);
            if (!(playerValue instanceof Player player)) {
                return;
            }
            if (!inputService.hasChatInput(player.getUniqueId())) {
                return;
            }
            if (event instanceof Cancellable cancellable) {
                cancellable.setCancelled(true);
            }
            Method messageMethod = event.getClass().getMethod("message");
            String text = plain(messageMethod.invoke(event));
            if (text != null) {
                inputService.handleChat(player, text);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Legacy listener may still handle the message.
        }
    }

    private static String plain(Object component) {
        if (component == null) {
            return null;
        }
        try {
            Class<?> serializerClass = Class.forName(
                    "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Object serializer = serializerClass.getMethod("plainText").invoke(null);
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Method serialize = serializerClass.getMethod("serialize", componentClass);
            Object result = serialize.invoke(serializer, component);
            return result == null ? null : result.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
