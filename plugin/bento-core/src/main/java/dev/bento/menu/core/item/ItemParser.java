package dev.bento.menu.core.item;

import dev.bento.menu.api.v1.Feedback;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.core.action.ActionParser;
import dev.bento.menu.core.condition.ConditionParser;
import dev.bento.menu.core.condition.Materials;
import dev.bento.menu.core.config.ConfigView;
import dev.bento.menu.core.report.ValidationReport;
import dev.bento.menu.core.util.Durations;
import dev.bento.menu.core.util.HexColors;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses item blueprints from YAML. Item display names and lore remain raw
 * templates so they can be rendered per viewer.
 */
public final class ItemParser {

    private final ActionParser actionParser;
    private final ConditionParser conditionParser;

    public ItemParser(ActionParser actionParser, ConditionParser conditionParser) {
        this.actionParser = actionParser;
        this.conditionParser = conditionParser;
    }

    public MenuItem parse(ConfigView view, ValidationReport report, String file) {
        if (view == null) {
            return null;
        }
        String materialName = view.getString("material", view.getString("item"));
        if (materialName == null || materialName.isBlank()) {
            report.error(file, view.key(), "Item has no material");
            return null;
        }
        Material material = Materials.match(materialName);
        if (material == null || material.isAir()) {
            report.error(file, view.key(), "Unknown material: " + materialName);
            return null;
        }
        MenuItem.Builder builder = MenuItem.builder(material);
        builder.amount(view.getInt("amount", 1));
        String name = view.getString("name");
        if (name != null) {
            builder.name(name);
        }
        if (view.has("lore")) {
            builder.lore(view.getStringList("lore"));
        }
        builder.glow(view.getBoolean("glow", false));
        if (view.has("custom-model-data")) {
            int data = view.getInt("custom-model-data", -1);
            if (data >= 0) {
                builder.customModelData(data);
            } else {
                report.warning(file, view.key(), "custom-model-data must be >= 0");
            }
        }
        String head = view.getString("head", view.getString("skull-owner", view.getString("head-player")));
        if (head != null && !head.isBlank()) {
            builder.headOwner(head);
        }
        String color = view.getString("color", view.getString("leather-color"));
        if (color != null && !color.isBlank()) {
            if (HexColors.parse(color) == null) {
                report.warning(file, view.key(), "Invalid colour: " + color);
            } else {
                builder.leatherColor(color);
            }
        }
        parseEnchantments(view, builder, report, file);
        List<String> flags = view.getStringList("item-flags");
        if (flags.isEmpty()) {
            flags = view.getStringList("flags");
        }
        if (!flags.isEmpty()) {
            builder.itemFlags(flags.toArray(String[]::new));
        }
        builder.unbreakable(view.getBoolean("unbreakable", false));

        List<ClickType> clickTypes = parseClickTypes(view.getStringList("click-types"), report, file, view.key());
        if (!clickTypes.isEmpty()) {
            builder.clickTypes(clickTypes.toArray(ClickType[]::new));
        }
        Duration cooldown = view.getDuration("cooldown", null);
        if (cooldown != null && !cooldown.isZero()) {
            builder.cooldown(cooldown);
        }
        String cooldownMessage = view.getString("cooldown-message");
        if (cooldownMessage != null) {
            builder.cooldownMessage(cooldownMessage);
        }
        String permission = view.getString("permission");
        if (permission != null && !permission.isBlank()) {
            builder.permission(permission);
        }
        ConfigView feedbackView = view.child("feedback");
        if (feedbackView != null) {
            builder.feedback(parseFeedback(feedbackView, report, file));
        }

        List<dev.bento.menu.api.v1.action.Action> actions = actionParser.parseFromView(view, "actions", report, file);
        if (!actions.isEmpty()) {
            builder.actions(actions);
        }
        List<dev.bento.menu.api.v1.action.Condition> conditions = new ArrayList<>(
                conditionParser.parseViews(view.getList("conditions"), report, file));
        builder.conditions(conditions);
        return builder.build();
    }

    public Feedback parseFeedback(ConfigView view, ValidationReport report, String file) {
        if (view == null) {
            return null;
        }
        String sound = view.getString("sound");
        Feedback.Builder builder = Feedback.builder();
        if (sound != null && !sound.isBlank()) {
            builder.sound(sound,
                    (float) view.getDouble("volume", 1.0),
                    (float) view.getDouble("pitch", 1.0));
        }
        String title = view.getString("title");
        if (title != null) {
            builder.title(title);
        }
        List<String> frames = view.getStringList("title-frames");
        if (frames.isEmpty()) {
            frames = view.getStringList("frames");
        }
        if (!frames.isEmpty()) {
            builder.titleFrames(frames);
        }
        String subtitle = view.getString("subtitle");
        if (subtitle != null) {
            builder.subtitle(subtitle);
        }
        builder.times(
                view.getInt("fade-in", 5),
                view.getInt("stay", 18),
                view.getInt("fade-out", 5)
        );
        Duration interval = view.getDuration("frame-interval", Duration.ofMillis(120));
        builder.frameInterval(interval);
        return builder.build();
    }

    private void parseEnchantments(ConfigView view, MenuItem.Builder builder,
                                   ValidationReport report, String file) {
        if (!view.has("enchantments")) {
            return;
        }
        ConfigView section = view.child("enchantments");
        if (section != null) {
            for (Map.Entry<String, Object> entry : section.values().entrySet()) {
                int level = parseLevel(entry.getValue(), report, file, entry.getKey());
                if (level > 0) {
                    builder.enchantment(entry.getKey(), level);
                }
            }
            return;
        }
        List<String> list = view.getStringList("enchantments");
        for (String value : list) {
            int colon = value.indexOf(':');
            if (colon <= 0) {
                report.warning(file, view.key(), "Enchantment must be name:level: " + value);
                continue;
            }
            String key = value.substring(0, colon);
            int level = parseLevel(value.substring(colon + 1), report, file, key);
            if (level > 0) {
                builder.enchantment(key, level);
            }
        }
    }

    private int parseLevel(Object raw, ValidationReport report, String file, String key) {
        int level = 1;
        if (raw instanceof Number number) {
            level = number.intValue();
        } else {
            try {
                level = Integer.parseInt(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                report.warning(file, "enchantments", "Invalid level for " + key);
            }
        }
        return Math.max(0, level);
    }

    private List<ClickType> parseClickTypes(List<String> values, ValidationReport report,
                                            String file, String path) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        EnumSet<ClickType> out = EnumSet.noneOf(ClickType.class);
        for (String value : values) {
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (normalized.equals("ALL")) {
                out.addAll(EnumSet.allOf(ClickType.class));
                continue;
            }
            if (normalized.equals("LEFT") || normalized.equals("RIGHT")) {
                try {
                    out.add(ClickType.valueOf(normalized));
                } catch (IllegalArgumentException exception) {
                    report.warning(file, path, "Unknown click type: " + value);
                }
                continue;
            }
            try {
                out.add(ClickType.valueOf(normalized));
            } catch (IllegalArgumentException exception) {
                report.warning(file, path, "Unknown click type: " + value);
            }
        }
        return new ArrayList<>(out);
    }
}
