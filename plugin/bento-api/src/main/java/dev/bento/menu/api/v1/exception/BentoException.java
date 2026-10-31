package dev.bento.menu.api.v1.exception;

/**
 * Unchecked error for invalid registrations or menu operations.
 */
public class BentoException extends RuntimeException {

    public BentoException(String message) {
        super(message);
    }

    public BentoException(String message, Throwable cause) {
        super(message, cause);
    }
}
