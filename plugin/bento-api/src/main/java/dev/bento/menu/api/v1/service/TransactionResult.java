package dev.bento.menu.api.v1.service;

/**
 * Result of a Vault economy operation. {@code amount} is the requested amount,
 * not necessarily the applied amount.
 */
public final class TransactionResult {

    private final boolean success;
    private final double amount;
    private final String error;

    private TransactionResult(boolean success, double amount, String error) {
        this.success = success;
        this.amount = amount;
        this.error = error;
    }

    public static TransactionResult success(double amount) {
        return new TransactionResult(true, amount, null);
    }

    public static TransactionResult failure(double amount, String error) {
        return new TransactionResult(false, amount, error == null ? "economy-transaction-failed" : error);
    }

    public boolean isSuccess() {
        return success;
    }

    public double amount() {
        return amount;
    }

    public String error() {
        return error;
    }
}
