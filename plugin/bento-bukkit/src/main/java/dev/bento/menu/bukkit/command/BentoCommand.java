package dev.bento.menu.bukkit.command;

import dev.bento.menu.api.v1.MenuDefinition;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.bukkit.BentoPlugin;
import dev.bento.menu.bukkit.menu.MenuService;
import dev.bento.menu.core.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Short, predictable command surface: /bento (alias /bm).
 */
public final class BentoCommand implements CommandExecutor, TabCompleter {

    private final BentoPlugin plugin;

    public BentoCommand(BentoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bento.command.use")) {
            noPermission(sender);
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openDefault(player);
            } else {
                help(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "?" -> help(sender);
            case "open" -> open(sender, args);
            case "list", "menus" -> list(sender);
            case "theme", "themes" -> theme(sender, args);
            case "language", "lang" -> language(sender, args);
            case "close" -> close(sender);
            case "reload" -> reload(sender);
            case "validate", "check" -> validate(sender);
            case "info", "version" -> info(sender);
            case "stats" -> stats(sender);
            default -> plugin.send(sender, "command.unknown", Map.of("sub", sub));
        }
        return true;
    }

    private void openDefault(Player player) {
        String menu = plugin.config().defaultMenu();
        if (menu == null || menu.isBlank()) {
            help(player);
            return;
        }
        if (plugin.menus().get(menu).isEmpty()) {
            plugin.send(player, "menu.unknown", Map.of("menu", menu));
            return;
        }
        plugin.menuService().open(player, menu, dev.bento.menu.api.v1.MenuOpenOptions.builder().build());
    }

    private void open(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bento.command.open")) {
            noPermission(sender);
            return;
        }
        if (args.length < 2) {
            plugin.send(sender, "command.usage-open", Map.of());
            return;
        }
        String menuId = args[1];
        Player target;
        if (args.length >= 3) {
            if (!sender.hasPermission("bento.command.open.other")) {
                noPermission(sender);
                return;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.send(sender, "player.not-found", Map.of("player", args[2]));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.send(sender, "command.player-only", Map.of());
            return;
        }
        if (plugin.menus().get(menuId).isEmpty()) {
            plugin.send(sender, "menu.unknown", Map.of("menu", menuId));
            return;
        }
        plugin.menuService().open(target, menuId,
                dev.bento.menu.api.v1.MenuOpenOptions.builder().build());
        if (!target.equals(sender)) {
            plugin.send(sender, "command.open.other", Map.of(
                    "menu", menuId,
                    "player", target.getName()
            ));
        }
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission("bento.command.list")) {
            noPermission(sender);
            return;
        }
        List<MenuDefinition> visible = plugin.menus().snapshot().stream()
                .map(menu -> menu.definition())
                .filter(menu -> menu.permission() == null || menu.permission().isBlank()
                        || sender.hasPermission(menu.permission()))
                .sorted(Comparator.comparing(MenuDefinition::id))
                .toList();
        if (visible.isEmpty()) {
            plugin.send(sender, "menu.list.empty", Map.of());
            return;
        }
        plugin.send(sender, "menu.list.header", Map.of("count", visible.size()));
        for (MenuDefinition menu : visible) {
            plugin.send(sender, "menu.list.entry", Map.of(
                    "menu", menu.id(),
                    "title", menu.title()
            ));
        }
    }

    private void theme(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "command.player-only", Map.of());
            return;
        }
        if (!sender.hasPermission("bento.command.theme")) {
            noPermission(sender);
            return;
        }
        if (args.length == 1) {
            if (plugin.menus().get("themes").isPresent()) {
                plugin.menuService().open(player, "themes",
                        dev.bento.menu.api.v1.MenuOpenOptions.builder().build());
            } else {
                listThemes(player);
            }
            return;
        }
        if (args[1].equalsIgnoreCase("list")) {
            listThemes(player);
            return;
        }
        String id = args[1];
        Theme theme = plugin.themes().get(id).orElse(null);
        if (theme == null) {
            plugin.send(player, "theme.unknown", Map.of("theme", id));
            return;
        }
        String permission = theme.permission();
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            noPermission(player);
            return;
        }
        plugin.profiles().setTheme(player, id);
        plugin.menuService().applyTheme(player);
        plugin.send(player, "theme.applied", Map.of("theme", theme.displayName()));
    }

    private void listThemes(Player player) {
        Collection<Theme> themes = plugin.themes().snapshot();
        if (themes.isEmpty()) {
            plugin.send(player, "theme.list.empty", Map.of());
            return;
        }
        plugin.send(player, "theme.list.header", Map.of("count", themes.size()));
        for (Theme theme : themes.stream().sorted(Comparator.comparing(Theme::id)).toList()) {
            String permission = theme.permission();
            if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
                continue;
            }
            plugin.send(player, "theme.list.entry", Map.of(
                    "theme", theme.id(),
                    "name", theme.displayName(),
                    "description", theme.description() == null ? "" : theme.description()
            ));
        }
    }

    private void language(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "command.player-only", Map.of());
            return;
        }
        if (!sender.hasPermission("bento.command.language")) {
            noPermission(sender);
            return;
        }
        PluginConfig config = plugin.config();
        if (args.length == 1) {
            plugin.send(player, "language.current", Map.of(
                    "language", plugin.profiles().languageFor(player),
                    "available", String.join(", ", plugin.messages().catalog().locales())
            ));
            return;
        }
        if (!config.allowLanguageSwitch()) {
            plugin.send(player, "language.disabled", Map.of());
            return;
        }
        String code = args[1];
        if (!plugin.profiles().setLanguage(player, code)) {
            plugin.send(player, "language.unknown", Map.of(
                    "language", code,
                    "available", String.join(", ", plugin.messages().catalog().locales())
            ));
            return;
        }
        plugin.send(player, "language.changed", Map.of("language", plugin.profiles().languageFor(player)));
    }

    private void close(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "command.player-only", Map.of());
            return;
        }
        if (!sender.hasPermission("bento.command.close")) {
            noPermission(sender);
            return;
        }
        if (!plugin.menuService().isOpen(player)) {
            plugin.send(player, "menu.not-open", Map.of());
            return;
        }
        plugin.menuService().close(player,
                dev.bento.menu.api.v1.event.BentoMenuCloseEvent.CloseReason.PLAYER, true);
        plugin.send(player, "menu.closed", Map.of());
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("bento.command.reload")) {
            noPermission(sender);
            return;
        }
        plugin.reloadAsync(sender, true);
    }

    private void validate(CommandSender sender) {
        if (!sender.hasPermission("bento.command.validate")) {
            noPermission(sender);
            return;
        }
        plugin.validateAsync(sender);
    }

    private void info(CommandSender sender) {
        if (!sender.hasPermission("bento.command.info")) {
            noPermission(sender);
            return;
        }
        plugin.send(sender, "info.header", Map.of());
        plugin.send(sender, "info.line", Map.of("key", "Version", "value", plugin.getDescription().getVersion()));
        plugin.send(sender, "info.line", Map.of("key", "Server", "value",
                Bukkit.getName() + " " + Bukkit.getVersion()));
        plugin.send(sender, "info.line", Map.of("key", "Folia", "value",
                plugin.scheduler().isFolia() ? "yes" : "no"));
        plugin.send(sender, "info.line", Map.of("key", "Menus", "value",
                String.valueOf(plugin.menus().size())));
        plugin.send(sender, "info.line", Map.of("key", "Themes", "value",
                String.valueOf(plugin.themes().size())));
        plugin.send(sender, "info.line", Map.of("key", "Sessions", "value",
                String.valueOf(plugin.menuService().sessionCount())));
        plugin.send(sender, "info.line", Map.of("key", "Vault economy", "value",
                plugin.economy().available() ? plugin.economy().providerName() : "unavailable"));
        plugin.send(sender, "info.line", Map.of("key", "PlaceholderAPI", "value",
                Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null ? "missing" : "present"));
        plugin.send(sender, "info.line", Map.of("key", "Resource pack", "value",
                plugin.config().resourcePack() ? "enabled" : "disabled"));
        plugin.send(sender, "info.line", Map.of("key", "API", "value", plugin.api().apiVersion()));
    }

    private void stats(CommandSender sender) {
        if (!sender.hasPermission("bento.command.stats")) {
            noPermission(sender);
            return;
        }
        plugin.send(sender, "stats.header", Map.of());
        plugin.metrics().snapshot(plugin.menuService().sessionCount())
                .forEach((key, value) -> plugin.send(sender, "stats.line", Map.of(
                        "key", key,
                        "value", String.valueOf(value)
                )));
    }

    private void help(CommandSender sender) {
        plugin.send(sender, "command.help.header", Map.of());
        List<String> lines = new ArrayList<>();
        lines.add("command.help.open");
        lines.add("command.help.list");
        lines.add("command.help.theme");
        lines.add("command.help.language");
        lines.add("command.help.close");
        lines.add("command.help.info");
        if (sender.hasPermission("bento.command.reload")) {
            lines.add("command.help.reload");
            lines.add("command.help.validate");
        }
        if (sender.hasPermission("bento.command.stats")) {
            lines.add("command.help.stats");
        }
        for (String key : lines) {
            plugin.send(sender, key, Map.of());
        }
    }

    private void noPermission(CommandSender sender) {
        plugin.send(sender, "no-permission", Map.of());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help", "open", "list", "theme", "language",
                    "close", "info"));
            if (sender.hasPermission("bento.command.reload")) {
                subs.add("reload");
                subs.add("validate");
            }
            if (sender.hasPermission("bento.command.stats")) {
                subs.add("stats");
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("open")) {
                return filter(plugin.menus().snapshot().stream()
                        .map(menu -> menu.id())
                        .filter(id -> sender.hasPermission("bento.command.open"))
                        .collect(Collectors.toCollection(ArrayList::new)), args[1]);
            }
            if (args[0].equalsIgnoreCase("theme")) {
                List<String> ids = plugin.themes().snapshot().stream()
                        .map(Theme::id)
                        .collect(Collectors.toCollection(ArrayList::new));
                ids.add("list");
                return filter(ids, args[1]);
            }
            if (args[0].equalsIgnoreCase("language")) {
                return filter(new ArrayList<>(plugin.messages().catalog().locales()), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("open")
                && sender.hasPermission("bento.command.open.other")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .collect(Collectors.toCollection(ArrayList::new)), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(Collection<String> values, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                unique.add(value);
            }
        }
        return new ArrayList<>(unique);
    }
}
