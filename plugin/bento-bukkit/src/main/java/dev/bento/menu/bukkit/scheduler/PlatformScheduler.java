package dev.bento.menu.bukkit.scheduler;

import dev.bento.menu.api.v1.service.RepeatingTask;
import dev.bento.menu.api.v1.service.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Scheduler abstraction for Spigot/Paper/Folia.
 *
 * <p>Folia paths use reflection over the public Bukkit API
 * ({@code Entity#getScheduler()}, {@code Bukkit#getGlobalRegionScheduler()}),
 * so the same jar still loads on 1.18 where those types do not exist. No NMS
 * and no Folia-only hard references are used.</p>
 */
public final class PlatformScheduler {

    private static final long TICK_MS = 50L;

    private final Plugin plugin;
    private final boolean folia;
    private final ScheduledExecutorService async;
    private final Object globalScheduler;
    private final AtomicBoolean warned = new AtomicBoolean();

    private final Method entitySchedulerGetter;
    private final Method entityRun;
    private final Method entityRunDelayed;
    private final Method entityRunAtFixedRate;
    private final Method ownedByCurrentRegionEntity;

    public PlatformScheduler(Plugin plugin, ScheduledExecutorService async) {
        this.plugin = plugin;
        this.async = async;
        String name = Bukkit.getServer().getName();
        this.folia = (name != null && name.toLowerCase(java.util.Locale.ROOT).contains("folia"))
                || classExists("io.papermc.paper.threadedregions.RegionizedServer");
        this.globalScheduler = folia ? resolveGlobalScheduler() : null;
        this.entitySchedulerGetter = folia ? findNoArgMethod(Entity.class, "getScheduler") : null;
        this.entityRun = folia ? findEntityMethod("run") : null;
        this.entityRunDelayed = folia ? findEntityMethod("runDelayed") : null;
        this.entityRunAtFixedRate = folia ? findEntityMethod("runAtFixedRate") : null;
        this.ownedByCurrentRegionEntity = folia ? findRegionCheck() : null;
    }

    public boolean isFolia() {
        return folia;
    }

    public void runOnPlayer(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (isOwnerThread(player)) {
            task.run();
            return;
        }
        if (folia) {
            scheduleEntity(player, task, 0, 0, false);
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public TaskHandle runOnPlayerLater(Player player, Runnable task, long ticks) {
        if (player == null || !player.isOnline()) {
            return TaskHandle.CANCELLED;
        }
        long delay = Math.max(1, ticks);
        if (folia) {
            return scheduleEntity(player, task, delay, 0, false);
        }
        return new BukkitHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    public TaskHandle runOnPlayerTimer(Player player, Runnable task, long delayTicks, long periodTicks) {
        if (player == null || !player.isOnline()) {
            return TaskHandle.CANCELLED;
        }
        long delay = Math.max(0, delayTicks);
        long period = Math.max(1, periodTicks);
        if (folia) {
            return scheduleEntity(player, task, delay, period, true);
        }
        return new BukkitHandle(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    public void runGlobal(Runnable task) {
        if (folia && globalScheduler != null) {
            if (invokeGlobal("execute", task)) {
                return;
            }
            if (invokeGlobalConsumer("run", task)) {
                return;
            }
            async.execute(task);
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public TaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        if (folia && globalScheduler != null) {
            for (Method method : globalScheduler.getClass().getMethods()) {
                if (!method.getName().equals("runAtFixedRate") || method.getParameterCount() != 4) {
                    continue;
                }
                try {
                    Object scheduled = method.invoke(globalScheduler, plugin,
                            (Consumer<Object>) ignored -> task.run(),
                            Math.max(0, delayTicks), Math.max(1, periodTicks));
                    return new ReflectiveHandle(scheduled);
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    warn("global timer", exception);
                }
            }
        }
        return new BukkitHandle(Bukkit.getScheduler().runTaskTimer(
                plugin, task, Math.max(0, delayTicks), Math.max(1, periodTicks)));
    }

    public void runAsync(Runnable task) {
        async.execute(task);
    }

    public TaskHandle runAsyncLater(Runnable task, Duration delay) {
        long millis = Math.max(0, delay == null ? 0 : delay.toMillis());
        return new FutureHandle(async.schedule(task, millis, TimeUnit.MILLISECONDS));
    }

    public TaskHandle runAsyncTimer(Runnable task, Duration delay, Duration period) {
        long initial = Math.max(0, delay == null ? 0 : delay.toMillis());
        long every = Math.max(1, period == null ? 50 : period.toMillis());
        return new FutureHandle(async.scheduleAtFixedRate(task, initial, every, TimeUnit.MILLISECONDS));
    }

    public boolean isOwnerThread(Player player) {
        if (!folia) {
            return Bukkit.isPrimaryThread();
        }
        if (ownedByCurrentRegionEntity == null || player == null) {
            return false;
        }
        try {
            Object result = ownedByCurrentRegionEntity.invoke(null, player);
            return result instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public Scheduler forPlayer(Player player) {
        return new BoundScheduler(this, player);
    }

    public void shutdown() {
        // Bukkit tasks are cancelled by the plugin; Folia scheduled tasks retire
        // automatically when the plugin disables.
    }

    private TaskHandle scheduleEntity(Player player, Runnable task, long delay, long period,
                                      boolean repeating) {
        if (entitySchedulerGetter == null) {
            warn("entity scheduler", new IllegalStateException("Entity#getScheduler is unavailable"));
            return TaskHandle.CANCELLED;
        }
        Object entityScheduler;
        try {
            entityScheduler = entitySchedulerGetter.invoke(player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warn("entity scheduler", exception);
            return TaskHandle.CANCELLED;
        }
        if (entityScheduler == null) {
            return TaskHandle.CANCELLED;
        }
        Consumer<Object> consumer = ignored -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Folia task failed: " + exception.getMessage());
            }
        };
        Runnable retired = () -> {
        };
        try {
            Object scheduled;
            if (repeating && entityRunAtFixedRate != null) {
                scheduled = entityRunAtFixedRate.invoke(entityScheduler, plugin, consumer, retired,
                        Math.max(0, delay), Math.max(1, period));
            } else if (delay <= 0 && entityRun != null) {
                scheduled = entityRun.invoke(entityScheduler, plugin, consumer, retired);
            } else if (entityRunDelayed != null) {
                scheduled = entityRunDelayed.invoke(entityScheduler, plugin, consumer, retired,
                        Math.max(1, delay));
            } else if (entityRun != null) {
                scheduled = entityRun.invoke(entityScheduler, plugin, consumer, retired);
            } else {
                return TaskHandle.CANCELLED;
            }
            return new ReflectiveHandle(scheduled);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warn("entity schedule", exception);
            return TaskHandle.CANCELLED;
        }
    }

    private boolean invokeGlobal(String methodName, Runnable task) {
        for (Method method : globalScheduler.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (!Plugin.class.isAssignableFrom(types[0]) || !Runnable.class.isAssignableFrom(types[1])) {
                continue;
            }
            try {
                method.invoke(globalScheduler, plugin, task);
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                warn("global execute", exception);
            }
        }
        return false;
    }

    private boolean invokeGlobalConsumer(String methodName, Runnable task) {
        for (Method method : globalScheduler.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (!Plugin.class.isAssignableFrom(types[0]) || !Consumer.class.isAssignableFrom(types[1])) {
                continue;
            }
            try {
                method.invoke(globalScheduler, plugin, (Consumer<Object>) ignored -> task.run());
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                warn("global run", exception);
            }
        }
        return false;
    }

    private Object resolveGlobalScheduler() {
        for (Method method : Bukkit.class.getMethods()) {
            if (method.getName().equals("getGlobalRegionScheduler") && method.getParameterCount() == 0) {
                try {
                    return method.invoke(null);
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    warn("global scheduler", exception);
                }
            }
        }
        return null;
    }

    private static Method findEntityMethod(String name) {
        try {
            Class<?> schedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            for (Method method : schedulerClass.getMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Older server; never called.
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    private static Method findRegionCheck() {
        for (Method method : Bukkit.class.getMethods()) {
            if (!method.getName().equals("isOwnedByCurrentRegion") || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(Player.class)) {
                return method;
            }
        }
        return null;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private void warn(String stage, Throwable error) {
        if (warned.compareAndSet(false, true)) {
            plugin.getLogger().warning("Folia scheduling issue at " + stage + ": "
                    + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : " - " + error.getMessage()));
        }
    }

    public interface TaskHandle extends RepeatingTask {
        TaskHandle CANCELLED = new TaskHandle() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };
    }

    private record BukkitHandle(org.bukkit.scheduler.BukkitTask task) implements TaskHandle {
        @Override
        public void cancel() {
            if (task != null) {
                task.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return task == null || task.isCancelled();
        }
    }

    private record FutureHandle(java.util.concurrent.ScheduledFuture<?> future) implements TaskHandle {
        @Override
        public void cancel() {
            if (future != null) {
                future.cancel(false);
            }
        }

        @Override
        public boolean isCancelled() {
            return future == null || future.isCancelled() || future.isDone();
        }
    }

    private static final class ReflectiveHandle implements TaskHandle {
        private final Object task;

        private ReflectiveHandle(Object task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (task == null) {
                return;
            }
            try {
                Method cancel = task.getClass().getMethod("cancel");
                cancel.invoke(task);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Best effort.
            }
        }

        @Override
        public boolean isCancelled() {
            if (task == null) {
                return true;
            }
            try {
                Method cancelled = task.getClass().getMethod("isCancelled");
                Object value = cancelled.invoke(task);
                return value instanceof Boolean bool && bool;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
    }

    private static final class BoundScheduler implements Scheduler {
        private final PlatformScheduler platform;
        private final Player player;

        private BoundScheduler(PlatformScheduler platform, Player player) {
            this.platform = platform;
            this.player = player;
        }

        @Override
        public void run(Runnable task) {
            platform.runOnPlayer(player, task);
        }

        @Override
        public void runLater(Runnable task, Duration delay) {
            platform.runOnPlayerLater(player, task, ticks(delay));
        }

        @Override
        public RepeatingTask runTimer(Runnable task, Duration delay, Duration period) {
            return platform.runOnPlayerTimer(player, task, ticks(delay), Math.max(1, ticks(period)));
        }

        @Override
        public void runAsync(Runnable task) {
            platform.runAsync(task);
        }

        @Override
        public RepeatingTask runAsyncTimer(Runnable task, Duration delay, Duration period) {
            return platform.runAsyncTimer(task, delay, period);
        }

        @Override
        public boolean isOwnerThread() {
            return platform.isOwnerThread(player);
        }

        private static long ticks(Duration duration) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                return 1;
            }
            return Math.max(1, (duration.toMillis() + TICK_MS - 1) / TICK_MS);
        }
    }
}
