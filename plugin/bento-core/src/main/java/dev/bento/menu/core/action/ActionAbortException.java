package dev.bento.menu.core.action;

/**
 * Stops the current action chain without being treated as an internal error.
 */
public final class ActionAbortException extends RuntimeException {

    public ActionAbortException(String message) {
        super(message);
    }

    public ActionAbortException(String message, Throwable cause) {
        super(message, cause);
    }
}
