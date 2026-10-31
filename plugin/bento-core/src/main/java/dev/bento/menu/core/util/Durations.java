package dev.bento.menu.core.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Human-friendly duration parser: {@code 500ms}, {@code 3s}, {@code 1m30s},
 * {@code 2h}, ISO-8601 ({@code PT3S}) or a plain number of seconds.
 */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ms|s|m|h|d)");

    private Durations() {
    }

    public static Duration parse(String raw, Duration fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("off") || value.equals("none") || value.equals("0")) {
            return Duration.ZERO;
        }
        if (value.startsWith("p")) {
            try {
                return Duration.parse(raw.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        Matcher matcher = PART.matcher(value);
        double millis = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            double number = Double.parseDouble(matcher.group(1));
            switch (matcher.group(2)) {
                case "ms" -> millis += number;
                case "s" -> millis += number * 1000;
                case "m" -> millis += number * 60_000;
                case "h" -> millis += number * 3_600_000;
                case "d" -> millis += number * 86_400_000;
                default -> {
                }
            }
        }
        if (found && matcher.replaceAll("").trim().isEmpty()) {
            return Duration.ofMillis(Math.max(0, Math.round(millis)));
        }
        try {
            return Duration.ofMillis(Math.max(0, Math.round(Double.parseDouble(value) * 1000)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static long toTicks(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return 0;
        }
        return Math.max(1, (duration.toMillis() + 49) / 50);
    }
}
