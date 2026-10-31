package dev.bento.menu.bukkit.config;

import dev.bento.menu.bukkit.BentoPlugin;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional file watcher for unattended hot reloads. Debounces bursts of editor
 * writes and ignores playerdata.yml.
 */
public final class AutoReloadWatcher {

    private final BentoPlugin plugin;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object scheduleLock = new Object();
    private volatile WatchService watchService;
    private volatile Thread thread;
    private ScheduledFuture<?> pending;

    public AutoReloadWatcher(BentoPlugin plugin, ScheduledExecutorService executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            Path root = plugin.getDataFolder().toPath();
            WatchService service = FileSystems.getDefault().newWatchService();
            Map<WatchKey, Path> keys = new HashMap<>();
            register(service, keys, root, true);
            register(service, keys, root.resolve("menus"), false);
            register(service, keys, root.resolve("layouts"), false);
            register(service, keys, root.resolve("themes"), false);
            register(service, keys, root.resolve("messages"), false);
            this.watchService = service;
            Thread watcher = new Thread(() -> loop(service, keys), "BentoMenu-Watch");
            watcher.setDaemon(true);
            this.thread = watcher;
            watcher.start();
        } catch (IOException exception) {
            running.set(false);
            plugin.getLogger().warning("Could not start auto-reload watcher: " + exception.getMessage());
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        WatchService service = watchService;
        if (service != null) {
            try {
                service.close();
            } catch (IOException ignored) {
                // Best effort.
            }
        }
        Thread current = thread;
        if (current != null) {
            current.interrupt();
        }
        synchronized (scheduleLock) {
            if (pending != null) {
                pending.cancel(false);
                pending = null;
            }
        }
        watchService = null;
        thread = null;
    }

    private void register(WatchService service, Map<WatchKey, Path> keys, Path directory,
                          boolean root) throws IOException {
        if (!directory.toFile().exists()) {
            return;
        }
        WatchKey key = directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        keys.put(key, directory);
    }

    private void loop(WatchService service, Map<WatchKey, Path> keys) {
        while (running.get()) {
            try {
                WatchKey key = service.take();
                Path directory = keys.get(key);
                if (directory == null) {
                    continue;
                }
                boolean relevant = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Object context = event.context();
                    if (context == null) {
                        continue;
                    }
                    String name = context.toString().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".yml") || name.endsWith(".tmp")) {
                        continue;
                    }
                    if (directory.equals(plugin.getDataFolder().toPath())
                            && !name.equals("config.yml")) {
                        continue;
                    }
                    relevant = true;
                }
                if (relevant) {
                    scheduleReload();
                }
                if (!key.reset()) {
                    keys.remove(key);
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException exception) {
                break;
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Auto-reload watcher error: " + exception.getMessage());
            }
        }
    }

    private void scheduleReload() {
        Duration debounce = plugin.config().autoReloadDebounce();
        synchronized (scheduleLock) {
            if (pending != null) {
                pending.cancel(false);
            }
            pending = executor.schedule(() -> {
                synchronized (scheduleLock) {
                    pending = null;
                }
                if (running.get() && plugin.config().autoReload() && plugin.isEnabled()) {
                    plugin.reloadAsync(null, false);
                }
            }, Math.max(200, debounce.toMillis()), TimeUnit.MILLISECONDS);
        }
    }
}
