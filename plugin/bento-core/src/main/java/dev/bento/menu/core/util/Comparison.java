package dev.bento.menu.core.util;

import java.util.Locale;

/**
 * Numeric comparison operators used by conditions.
 */
public enum Comparison {

    GREATER(">"),
    GREATER_OR_EQUAL(">="),
    EQUAL("=="),
    LESS_OR_EQUAL("<="),
    LESS("<"),
    NOT_EQUAL("!=");

    private final String symbol;

    Comparison(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean test(double left, double right) {
        return switch (this) {
            case GREATER -> left > right;
            case GREATER_OR_EQUAL -> left >= right;
            case EQUAL -> Math.abs(left - right) < 0.000001;
            case LESS_OR_EQUAL -> left <= right;
            case LESS -> left < right;
            case NOT_EQUAL -> Math.abs(left - right) >= 0.000001;
        };
    }

    public static Comparison parse(String raw, Comparison fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case ">", "gt", "greater" -> GREATER;
            case ">=", "ge", "gte", "greater_or_equal" -> GREATER_OR_EQUAL;
            case "==", "=", "eq", "equal" -> EQUAL;
            case "<=", "le", "lte", "less_or_equal" -> LESS_OR_EQUAL;
            case "<", "lt", "less" -> LESS;
            case "!=", "ne", "not_equal" -> NOT_EQUAL;
            default -> fallback;
        };
    }
}
