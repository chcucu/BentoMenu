package dev.bento.menu.api.v1.service;

import java.time.Duration;

/**
 * Task scheduling bound to a player's owning thread/region. On Folia this maps
 * to the entity scheduler; on Spigot/Paper to the main or async scheduler.
 */
public interface Scheduler {

    void run(Runnable task);

    void runLater(Runnable task, Duration delay);

    RepeatingTask runTimer(Runnable task, Duration delay, Duration period);

    void runAsync(Runnable task);

    RepeatingTask runAsyncTimer(Runnable task, Duration delay, Duration period);

    boolean isOwnerThread();
}
