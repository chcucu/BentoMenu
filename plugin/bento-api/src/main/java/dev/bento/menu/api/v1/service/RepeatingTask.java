package dev.bento.menu.api.v1.service;

/**
 * Handle for a repeating scheduled task.
 */
public interface RepeatingTask {

    void cancel();

    boolean isCancelled();
}
