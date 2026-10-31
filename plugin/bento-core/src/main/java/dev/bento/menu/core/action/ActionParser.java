package dev.bento.menu.core.action;

import dev.bento.menu.api.v1.MenuOpenOptions;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.action.ActionFactory;
import dev.bento.menu.api.v1.action.Condition;
import dev.bento.menu.api.v1.config.ConfigurationView;
import dev.bento.menu.core.condition.ConditionParser;
import dev.bento.menu.core.condition.Materials;
import dev.bento.menu.core.config.ConfigView;
import dev.bento.menu.core.item.ItemParser;
import dev.bento.menu.core.report.ValidationReport;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses YAML action chains into executable actions.
 */
public final class ActionParser {

    private final ActionRegistry registry;
    private final ConditionParser conditionParser;
    private ItemParser itemParser;

    public ActionParser(ActionRegistry registry, ConditionParser conditionParser) {
        this.registry = registry;
        this.conditionParser = conditionParser;
    }

    public void setItemParser(ItemParser itemParser) {
        this.itemParser = itemParser;
    }

    public List<Action> parse(ConfigurationSection section, String path,
                              ValidationReport report, String file) {
        if (section == null || !section.contains(path)) {
            return List.of();
        }
        return parseFromView(ConfigView.of(section), path, report, file);
    }

    public List<Action> parseFromView(ConfigView parent, String path,
                                      ValidationReport report, String file) {
        if (parent == null || !parent.has(path)) {
            return List.of();
        }
        List<ConfigurationView> views = parent.getList(path);
        if (!views.isEmpty()) {
            return parseViews(views, report, file);
        }
        ConfigView single = parent.child(path);
        if (single != null) {
            return List.of(parseOne(single, report, file));
        }
        return List.of();
    }

    public List<Action> parseViews(List<? extends ConfigurationView> views,
                                   ValidationReport report, String file) {
        if (views == null || views.isEmpty()) {
            return List.of();
        }
        List<Action> out = new ArrayList<>(views.size());
        for (ConfigurationView view : views) {
            if (view instanceof ConfigView configView) {
                out.add(parseOne(configView, report, file));
            }
        }
        return List.copyOf(out);
    }

    private Action parseOne(ConfigView view, ValidationReport report, String file) {
        String type = view.getString("type");
        if (type == null || type.isBlank()) {
            report.error(file, view.key(), "Action has no type");
            return new UnsupportedAction("<missing>");
        }
        String key = type.toLowerCase(Locale.ROOT);
        try {
            Action action = switch (key) {
                case "message", "msg" -> BuiltInActions.message(text(view));
                case "actionbar" -> BuiltInActions.actionBar(text(view));
                case "title" -> title(view);
                case "sound" -> sound(view);
                case "player-command", "player" -> BuiltInActions.playerCommand(view.getString("command"));
                case "console-command", "console" -> BuiltInActions.consoleCommand(view.getString("command"));
                case "give-item", "give" -> giveItem(view, report, file);
                case "take-item", "take" -> takeItem(view, report, file);
                case "give-money" -> BuiltInActions.giveMoney(view.getDouble("amount", 0),
                        view.getString("message"));
                case "take-money" -> BuiltInActions.takeMoney(view.getDouble("amount", 0),
                        view.getString("message"));
                case "open-menu", "open" -> openMenu(view, report, file);
                case "close-menu", "close" -> BuiltInActions.closeMenu();
                case "page", "goto-page" -> BuiltInActions.page(
                        view.getString("mode", "next"), view.getInt("page", 1));
                case "theme" -> BuiltInActions.theme(
                        view.getString("theme"),
                        view.getString("mode", "apply"),
                        view.getString("preview-menu"));
                case "refresh" -> BuiltInActions.refresh();
                case "search" -> BuiltInActions.search(view.getString("query"));
                case "category" -> BuiltInActions.category(view.getString("category"));
                case "buy" -> buy(view, report, file);
                case "condition", "if" -> conditionBranch(view, report, file);
                default -> custom(view, key, report, file);
            };
            if (!key.equals("condition") && !key.equals("if")) {
                List<Condition> conditions = new ArrayList<>(conditionParser.parseViews(
                        view.getList("conditions"), report, file));
                List<Action> denyActions = parseFromView(view, "deny-actions", report, file);
                if (!conditions.isEmpty() || !denyActions.isEmpty()) {
                    action = new ConditionalAction(action, conditions, denyActions);
                }
            }
            java.util.Set<org.bukkit.event.inventory.ClickType> clickTypes =
                    parseClickTypes(view.getStringList("click-types"), report, file, view.key());
            if (!clickTypes.isEmpty()) {
                action = new ClickTypeFilterAction(action, clickTypes);
            }
            return action;
        } catch (Exception exception) {
            report.error(file, view.key(), "Failed to parse action " + type + ": " + exception.getMessage());
            return new UnsupportedAction(type);
        }
    }

    private Action title(ConfigView view) {
        List<String> frames = view.getStringList("frames");
        if (frames.isEmpty()) {
            frames = view.getStringList("title-frames");
        }
        return BuiltInActions.title(
                view.getString("title"),
                view.getString("subtitle"),
                frames,
                view.getDuration("frame-interval", Duration.ofMillis(120)),
                view.getInt("fade-in", 5),
                view.getInt("stay", 18),
                view.getInt("fade-out", 5)
        );
    }

    private Action sound(ConfigView view) {
        return BuiltInActions.sound(
                view.getString("sound", view.getString("name")),
                (float) view.getDouble("volume", 1.0),
                (float) view.getDouble("pitch", 1.0)
        );
    }

    private Action giveItem(ConfigView view, ValidationReport report, String file) {
        ConfigView itemView = view.child("item");
        MenuItem item = itemView == null ? null : itemParser.parse(itemView, report, file);
        Material material = Materials.match(view.getString("material"));
        if (itemView == null && material == null) {
            report.error(file, view.key(), "give-item needs item or material");
        }
        return BuiltInActions.giveItem(
                item,
                material,
                view.getInt("amount", 1),
                view.getBoolean("drop", true),
                view.getString("message")
        );
    }

    private Action takeItem(ConfigView view, ValidationReport report, String file) {
        Material material = Materials.match(view.getString("material"));
        if (material == null) {
            ConfigView itemView = view.child("item");
            if (itemView != null) {
                material = Materials.match(itemView.getString("material"));
            }
        }
        if (material == null) {
            report.error(file, view.key(), "take-item needs a material");
        }
        return BuiltInActions.takeItem(
                material,
                Math.max(1, view.getInt("amount", 1)),
                view.getString("failure-message")
        );
    }

    private Action openMenu(ConfigView view, ValidationReport report, String file) {
        String menu = view.getString("menu");
        if (menu == null || menu.isBlank()) {
            report.error(file, view.key(), "open-menu needs a menu id");
        }
        MenuOpenOptions options = MenuOpenOptions.builder()
                .page(view.getInt("page", 1))
                .category(view.getString("category"))
                .query(view.getString("query"))
                .themeOverride(view.getString("theme"))
                .build();
        return BuiltInActions.openMenu(menu == null ? "" : menu, options);
    }

    private Action buy(ConfigView view, ValidationReport report, String file) {
        ConfigView itemView = view.child("item");
        MenuItem item = itemView == null ? null : itemParser.parse(itemView, report, file);
        Material material = Materials.match(view.getString("material"));
        if (item == null && material == null && itemView != null) {
            report.error(file, view.key(), "buy item could not be parsed");
        }
        return BuiltInActions.buy(
                view.getDouble("price", 0),
                item,
                material,
                view.getInt("amount", 1),
                view.getString("success-message"),
                view.getString("failure-message")
        );
    }

    private Action conditionBranch(ConfigView view, ValidationReport report, String file) {
        List<Condition> conditions = new ArrayList<>(conditionParser.parseViews(
                view.getList("conditions"), report, file));
        List<Action> thenActions = parseFromView(view, "then", report, file);
        if (thenActions.isEmpty()) {
            thenActions = parseFromView(view, "actions", report, file);
        }
        List<Action> elseActions = parseFromView(view, "else", report, file);
        return new ConditionBranchAction(conditions, thenActions, elseActions);
    }

    private Action custom(ConfigView view, String key, ValidationReport report, String file) {
        ActionFactory factory = registry.get(key);
        if (factory == null) {
            report.error(file, view.key(), "Unknown action type: " + key);
            return new UnsupportedAction(key);
        }
        try {
            Action action = factory.create(view.asAction(key));
            if (action == null) {
                report.error(file, view.key(), "Action factory returned null: " + key);
                return new UnsupportedAction(key);
            }
            return action;
        } catch (Exception exception) {
            report.error(file, view.key(), "Action factory failed: " + exception.getMessage());
            return new UnsupportedAction(key);
        }
    }

    private static String text(ConfigView view) {
        String text = view.getString("text");
        return text == null ? view.getString("message") : text;
    }

    private static java.util.Set<org.bukkit.event.inventory.ClickType> parseClickTypes(
            List<String> values, ValidationReport report, String file, String path) {
        if (values == null || values.isEmpty()) {
            return java.util.Set.of();
        }
        java.util.EnumSet<org.bukkit.event.inventory.ClickType> out =
                java.util.EnumSet.noneOf(org.bukkit.event.inventory.ClickType.class);
        for (String value : values) {
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (normalized.equals("ALL")) {
                out.addAll(java.util.EnumSet.allOf(org.bukkit.event.inventory.ClickType.class));
                continue;
            }
            try {
                out.add(org.bukkit.event.inventory.ClickType.valueOf(normalized));
            } catch (IllegalArgumentException exception) {
                report.warning(file, path, "Unknown action click type: " + value);
            }
        }
        return out;
    }
}
