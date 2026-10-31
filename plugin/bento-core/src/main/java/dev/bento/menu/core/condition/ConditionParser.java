package dev.bento.menu.core.condition;

import dev.bento.menu.api.v1.action.Condition;
import dev.bento.menu.api.v1.action.ConditionFactory;
import dev.bento.menu.api.v1.config.ConfigurationView;
import dev.bento.menu.api.v1.service.EconomyService;
import dev.bento.menu.api.v1.service.PlaceholderService;
import dev.bento.menu.api.v1.action.ConditionContext;
import dev.bento.menu.core.config.ConfigView;
import dev.bento.menu.core.report.ValidationReport;
import dev.bento.menu.core.util.Comparison;
import dev.bento.menu.core.util.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Parses built-in and addon conditions.
 */
public final class ConditionParser {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("H:mm");

    private final ConditionRegistry registry;

    public ConditionParser(ConditionRegistry registry) {
        this.registry = registry;
    }

    public List<ConfiguredCondition> parse(ConfigurationSection section, String path,
                                           ValidationReport report, String file) {
        if (section == null || !section.contains(path)) {
            return List.of();
        }
        Object raw = section.get(path);
        if (raw instanceof List<?> list) {
            List<ConfiguredCondition> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof ConfigurationSection child) {
                    parseOne(ConfigView.of(child), report, file, out);
                } else if (item instanceof Map<?, ?> map) {
                    parseOne(ConfigView.of(ConfigView.toStringMap(map)), report, file, out);
                } else if (item instanceof String shorthand) {
                    parseShorthand(shorthand, report, file, out);
                } else if (item != null) {
                    report.warning(file, path, "Unsupported condition entry: " + item);
                }
            }
            return List.copyOf(out);
        }
        if (raw instanceof ConfigurationSection child) {
            List<ConfiguredCondition> out = new ArrayList<>();
            parseOne(ConfigView.of(child), report, file, out);
            return List.copyOf(out);
        }
        if (raw instanceof Map<?, ?> map) {
            List<ConfiguredCondition> out = new ArrayList<>();
            parseOne(ConfigView.of(ConfigView.toStringMap(map)), report, file, out);
            return List.copyOf(out);
        }
        if (raw instanceof String shorthand) {
            List<ConfiguredCondition> out = new ArrayList<>();
            parseShorthand(shorthand, report, file, out);
            return List.copyOf(out);
        }
        report.warning(file, path, "conditions must be a list of maps");
        return List.of();
    }

    public List<ConfiguredCondition> parseViews(List<? extends ConfigurationView> views,
                                                ValidationReport report, String file) {
        if (views == null || views.isEmpty()) {
            return List.of();
        }
        List<ConfiguredCondition> out = new ArrayList<>();
        for (ConfigurationView view : views) {
            if (view instanceof ConfigView configView) {
                parseOne(configView, report, file, out);
            }
        }
        return List.copyOf(out);
    }

    private void parseOne(ConfigView view, ValidationReport report, String file, List<ConfiguredCondition> out) {
        String type = view.getString("type");
        if (type == null || type.isBlank()) {
            report.warning(file, view.key(), "Condition has no type");
            return;
        }
        String key = type.toLowerCase(Locale.ROOT);
        boolean negate = view.getBoolean("negate", false);
        String denyMessage = view.getString("deny-message");
        Condition condition;
        try {
            condition = switch (key) {
                case "permission" -> permission(view);
                case "money", "funds" -> money(view);
                case "item", "has-item" -> item(view);
                case "time", "server-time", "world-time" -> time(view);
                case "chance" -> chance(view);
                case "placeholder" -> placeholder(view);
                default -> custom(view, key, report, file);
            };
        } catch (Exception exception) {
            report.error(file, view.key(), "Invalid " + type + " condition: " + exception.getMessage());
            condition = context -> false;
        }
        if (condition == null) {
            return;
        }
        if (negate) {
            Condition base = condition;
            condition = context -> !base.test(context);
        }
        out.add(new ConfiguredCondition(condition, denyMessage));
    }

    private void parseShorthand(String value, ValidationReport report, String file, List<ConfiguredCondition> out) {
        int colon = value.indexOf(':');
        String type = colon < 0 ? value : value.substring(0, colon);
        String argument = colon < 0 ? "" : value.substring(colon + 1);
        Condition condition = switch (type.toLowerCase(Locale.ROOT)) {
            case "permission", "perm" -> context -> argument.isEmpty()
                    || context.player().hasPermission(argument);
            case "chance" -> {
                double percent = parseDouble(argument, 0);
                yield context -> ThreadLocalRandom.current().nextDouble(100.0) < percent;
            }
            default -> null;
        };
        if (condition == null) {
            report.warning(file, "conditions", "Unknown condition shorthand: " + value);
            return;
        }
        out.add(new ConfiguredCondition(condition, null));
    }

    private Condition permission(ConfigView view) {
        String node = view.getString("permission", view.getString("node", ""));
        return context -> node.isBlank() || context.player().hasPermission(node);
    }

    private Condition money(ConfigView view) {
        double amount = view.getDouble("amount", view.getDouble("value", 0));
        Comparison comparison = Comparison.parse(view.getString("operator"), Comparison.GREATER_OR_EQUAL);
        return context -> {
            EconomyService economy = context.economy();
            return economy.available() && comparison.test(economy.balance(context.player()), amount);
        };
    }

    private Condition item(ConfigView view) {
        String materialName = view.getString("material", view.getString("item"));
        Material material = Materials.match(materialName);
        int amount = Math.max(1, view.getInt("amount", 1));
        Comparison comparison = Comparison.parse(view.getString("operator"), Comparison.GREATER_OR_EQUAL);
        return context -> {
            if (material == null) {
                return false;
            }
            int count = 0;
            for (ItemStack stack : context.player().getInventory().all(material).values()) {
                count += stack.getAmount();
            }
            return comparison.test(count, amount);
        };
    }

    private Condition time(ConfigView view) {
        String mode = view.getString("mode", view.getString("clock", "real")).toLowerCase(Locale.ROOT);
        String fromRaw = view.getString("from", mode.startsWith("world") ? "0" : "00:00");
        String toRaw = view.getString("to", mode.startsWith("world") ? "24000" : "23:59");
        String world = view.getString("world");
        String timezone = view.getString("timezone");
        if (mode.startsWith("world")) {
            long from = parseTicks(fromRaw);
            long to = parseTicks(toRaw);
            return context -> {
                Player player = context.player();
                if (world != null && !world.isBlank() && !player.getWorld().getName().equalsIgnoreCase(world)) {
                    return false;
                }
                long time = player.getWorld().getTime();
                return inWorldRange(time, from, to);
            };
        }
        ZoneId zone;
        try {
            zone = timezone == null || timezone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            zone = ZoneId.systemDefault();
        }
        LocalTime from = parseLocalTime(fromRaw, LocalTime.MIN);
        LocalTime to = parseLocalTime(toRaw, LocalTime.MAX);
        ZoneId finalZone = zone;
        return context -> inTimeRange(LocalTime.now(finalZone), from, to);
    }

    private Condition chance(ConfigView view) {
        double percent = clamp(view.getDouble("percent", view.getDouble("chance", 100)), 0, 100);
        return context -> ThreadLocalRandom.current().nextDouble(100.0) < percent;
    }

    private Condition placeholder(ConfigView view) {
        String placeholder = view.getString("placeholder", view.getString("value", ""));
        String expected = view.getString("expected", view.getString("equals", ""));
        Comparison comparison = Comparison.parse(view.getString("operator"), Comparison.EQUAL);
        boolean numeric = isNumeric(expected);
        return context -> {
            PlaceholderService service = context.placeholders();
            String resolved = service.resolve(context.player(), placeholder);
            if (numeric) {
                try {
                    return comparison.test(Double.parseDouble(resolved.trim()), Double.parseDouble(expected.trim()));
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            boolean equal = resolved.equalsIgnoreCase(expected);
            return comparison == Comparison.NOT_EQUAL ? !equal : equal;
        };
    }

    private Condition custom(ConfigView view, String key, ValidationReport report, String file) {
        ConditionFactory factory = registry.get(key);
        if (factory == null) {
            report.error(file, view.key(), "Unknown condition type: " + key);
            return context -> false;
        }
        try {
            Condition condition = factory.create(view.asCondition(key));
            if (condition == null) {
                report.error(file, view.key(), "Condition factory returned null: " + key);
                return context -> false;
            }
            return condition;
        } catch (Exception exception) {
            report.error(file, view.key(), "Condition factory failed: " + exception.getMessage());
            return context -> false;
        }
    }

    private static Material matchMaterial(String name) {
        return Materials.match(name);
    }

    private static LocalTime parseLocalTime(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim(), HH_MM);
        } catch (RuntimeException exception) {
            try {
                int hour = Integer.parseInt(raw.trim());
                return LocalTime.of(Math.floorMod(hour, 24), 0);
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
    }

    private static long parseTicks(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.floorMod(Long.parseLong(raw.trim()), 24000L);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean inTimeRange(LocalTime now, LocalTime from, LocalTime to) {
        if (from.equals(to)) {
            return true;
        }
        if (from.isBefore(to)) {
            return !now.isBefore(from) && now.isBefore(to);
        }
        return !now.isBefore(from) || now.isBefore(to);
    }

    private static boolean inWorldRange(long time, long from, long to) {
        long normalized = Math.floorMod(time, 24000L);
        if (from == to) {
            return true;
        }
        if (from < to) {
            return normalized >= from && normalized < to;
        }
        return normalized >= from || normalized < to;
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean isNumeric(String value) {
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
