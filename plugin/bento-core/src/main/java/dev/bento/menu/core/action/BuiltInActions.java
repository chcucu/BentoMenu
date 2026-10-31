package dev.bento.menu.core.action;

import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.MenuOpenOptions;
import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.action.ActionContext;
import dev.bento.menu.api.v1.service.TransactionResult;
import dev.bento.menu.api.v1.theme.Theme;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in action implementations. All of them run on the player's owning
 * thread; no blocking I/O is performed.
 */
public final class BuiltInActions {

    private BuiltInActions() {
    }

    public static Action message(String text) {
        return context -> {
            if (text != null && !text.isBlank()) {
                context.messages().sendRaw(context.player(), text);
            }
        };
    }

    public static Action actionBar(String text) {
        return context -> {
            if (text != null && !text.isBlank()) {
                context.feedback().actionBar(context.player(), context.messages().render(context.player(), text));
            }
        };
    }

    public static Action sound(String sound, float volume, float pitch) {
        return context -> {
            if (sound != null && !sound.isBlank()) {
                context.feedback().sound(context.player(), sound, volume, pitch);
            }
        };
    }

    public static Action title(String title, String subtitle, List<String> frames, Duration frameInterval,
                               int fadeIn, int stay, int fadeOut) {
        List<String> actualFrames = new ArrayList<>();
        if (frames != null && !frames.isEmpty()) {
            actualFrames.addAll(frames);
        } else if (title != null && !title.isBlank()) {
            actualFrames.add(title);
        }
        Duration interval = frameInterval == null || frameInterval.isNegative()
                ? Duration.ofMillis(120) : frameInterval;
        return context -> {
            Player player = context.player();
            if (actualFrames.isEmpty()) {
                context.feedback().title(player, "", render(context, subtitle), fadeIn, stay, fadeOut);
                return;
            }
            for (int i = 0; i < actualFrames.size(); i++) {
                String frame = actualFrames.get(i);
                Runnable task = () -> context.feedback().title(
                        player,
                        render(context, frame),
                        render(context, subtitle),
                        fadeIn,
                        stay,
                        fadeOut
                );
                if (i == 0) {
                    task.run();
                } else {
                    context.scheduler().runLater(task, interval.multipliedBy(i));
                }
            }
        };
    }

    public static Action playerCommand(String command) {
        return context -> {
            String resolved = resolveCommand(context, command);
            if (resolved.isEmpty()) {
                return;
            }
            if (!context.player().performCommand(resolved)) {
                context.messages().send(context.player(), "action.command-failed",
                        Map.of("command", resolved));
            }
        };
    }

    public static Action consoleCommand(String command) {
        return context -> {
            String resolved = resolveCommand(context, command);
            if (resolved.isEmpty()) {
                return;
            }
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved)) {
                context.messages().send(context.player(), "action.command-failed",
                        Map.of("command", resolved));
            }
        };
    }

    public static Action giveItem(MenuItem item, Material fallbackMaterial, int fallbackAmount,
                                  boolean drop, String message) {
        return context -> {
            ItemStack stack = renderItem(context, item, fallbackMaterial, fallbackAmount);
            if (stack == null || stack.getType().isAir()) {
                return;
            }
            Map<Integer, ItemStack> leftover = context.player().getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                if (drop) {
                    for (ItemStack remaining : leftover.values()) {
                        context.player().getWorld().dropItemNaturally(context.player().getLocation(), remaining);
                    }
                } else {
                    context.messages().send(context.player(), "action.give.inventory-full");
                }
            }
            if (message != null && !message.isBlank()) {
                context.messages().sendRaw(context.player(), fillMessage(context, message,
                        Map.of("amount", stack.getAmount(), "item", stack.getType().name())));
            }
        };
    }

    public static Action takeItem(Material material, int amount, String failureMessage) {
        return context -> {
            if (material == null) {
                return;
            }
            int count = countMaterial(context.player().getInventory(), material);
            if (count < amount) {
                String message = failureMessage == null ? "action.take.insufficient" : failureMessage;
                if (failureMessage == null) {
                    context.messages().send(context.player(), message, Map.of(
                            "amount", amount,
                            "have", count,
                            "item", material.name()
                    ));
                } else {
                    context.messages().sendRaw(context.player(), fillMessage(context, message, Map.of(
                            "amount", amount,
                            "have", count,
                            "item", material.name()
                    )));
                }
                throw new ActionAbortException("insufficient items");
            }
            removeMaterial(context.player().getInventory(), material, amount);
        };
    }

    public static Action giveMoney(double amount, String message) {
        return context -> {
            TransactionResult result = context.economy().deposit(context.player(), amount);
            if (!result.isSuccess()) {
                context.messages().send(context.player(), "economy.unavailable");
                throw new ActionAbortException("deposit failed");
            }
            if (message != null && !message.isBlank()) {
                context.messages().sendRaw(context.player(), fillMessage(context, message,
                        Map.of("amount", context.economy().format(amount))));
            }
        };
    }

    public static Action takeMoney(double amount, String message) {
        return context -> {
            TransactionResult result = context.economy().withdraw(context.player(), amount);
            if (!result.isSuccess()) {
                context.messages().send(context.player(), "economy.insufficient-funds",
                        Map.of("amount", context.economy().format(amount)));
                throw new ActionAbortException("withdraw failed");
            }
            if (message != null && !message.isBlank()) {
                context.messages().sendRaw(context.player(), fillMessage(context, message,
                        Map.of("amount", context.economy().format(amount))));
            }
        };
    }

    public static Action openMenu(String menuId, MenuOpenOptions options) {
        return context -> context.api().open(context.player(), menuId, options);
    }

    public static Action closeMenu() {
        return context -> context.api().close(context.player());
    }

    public static Action page(String mode, int page) {
        return context -> {
            if (context.session() == null) {
                return;
            }
            int current = context.session().page();
            int pages = context.session().pages();
            int target = switch (mode == null ? "" : mode.toLowerCase(Locale.ROOT)) {
                case "next", "next-page" -> current + 1;
                case "previous", "prev", "previous-page" -> current - 1;
                case "first" -> 1;
                case "last" -> pages;
                case "set", "goto" -> page;
                default -> current;
            };
            context.session().setPage(target);
        };
    }

    public static Action theme(String themeId, String mode, String previewMenu) {
        return context -> {
            String resolved = resolveThemeId(context, themeId);
            if (resolved == null || resolved.isBlank()) {
                return;
            }
            Optional<Theme> theme = context.api().theme(resolved);
            if (theme.isEmpty()) {
                context.messages().send(context.player(), "theme.unknown", Map.of("theme", resolved));
                throw new ActionAbortException("unknown theme");
            }
            String permission = theme.get().permission();
            if (permission != null && !permission.isBlank() && !context.player().hasPermission(permission)) {
                context.messages().send(context.player(), "no-permission");
                throw new ActionAbortException("theme permission");
            }
            if ("preview".equalsIgnoreCase(mode)) {
                String menu = previewMenu == null || previewMenu.isBlank() ? "theme-preview" : previewMenu;
                MenuOpenOptions options = MenuOpenOptions.builder().themeOverride(resolved).build();
                context.api().open(context.player(), menu, options);
                return;
            }
            context.api().setTheme(context.player(), resolved);
            context.messages().send(context.player(), "theme.applied", Map.of("theme", resolved));
        };
    }

    public static Action refresh() {
        return ActionContext::refresh;
    }

    public static Action search(String query) {
        return context -> {
            if (context.session() != null) {
                if (query != null && !query.isBlank()) {
                    context.session().setQuery(query);
                }
                context.session().requestSearch();
            }
        };
    }

    public static Action category(String category) {
        return context -> {
            if (context.session() != null) {
                context.session().setCategory(category);
            }
        };
    }

    public static Action buy(double price, MenuItem item, Material fallbackMaterial, int fallbackAmount,
                             String successMessage, String failureMessage) {
        double cost = Math.max(0, price);
        return context -> {
            Player player = context.player();
            ItemStack stack = renderItem(context, item, fallbackMaterial, fallbackAmount);
            if (stack == null || stack.getType().isAir()) {
                context.messages().send(player, "action.buy.invalid-item");
                throw new ActionAbortException("invalid buy item");
            }
            String priceText = context.economy().available()
                    ? context.economy().format(cost)
                    : String.format(Locale.ROOT, "%.2f", cost);
            if (cost > 0) {
                if (!context.economy().available()) {
                    context.messages().send(player, "economy.unavailable");
                    throw new ActionAbortException("economy unavailable");
                }
                if (context.economy().balance(player) + 0.000001 < cost) {
                    sendFailure(context, failureMessage, cost, priceText, stack);
                    throw new ActionAbortException("insufficient funds");
                }
            }
            Inventory inventory = player.getInventory();
            ItemStack[] snapshot = cloneContents(inventory.getContents());
            if (cost > 0) {
                TransactionResult result = context.economy().withdraw(player, cost);
                if (!result.isSuccess()) {
                    sendFailure(context, failureMessage, cost, priceText, stack);
                    throw new ActionAbortException("withdraw failed");
                }
            }
            Map<Integer, ItemStack> leftover = inventory.addItem(stack);
            if (!leftover.isEmpty()) {
                inventory.setContents(snapshot);
                if (cost > 0) {
                    TransactionResult refund = context.economy().deposit(player, cost);
                    if (!refund.isSuccess()) {
                        context.messages().send(player, "action.buy.refund-failed",
                                Map.of("price", priceText));
                    }
                }
                sendFailure(context, failureMessage, cost, priceText, stack);
                throw new ActionAbortException("inventory full");
            }
            String message = successMessage == null ? "action.buy.success" : successMessage;
            if (successMessage == null) {
                context.messages().send(player, message, Map.of(
                        "price", priceText,
                        "item", stack.getType().name(),
                        "amount", stack.getAmount()
                ));
            } else {
                context.messages().sendRaw(player, fillMessage(context, message, Map.of(
                        "price", priceText,
                        "item", stack.getType().name(),
                        "amount", stack.getAmount(),
                        "balance", context.economy().available()
                                ? context.economy().format(context.economy().balance(player)) : ""
                )));
            }
        };
    }

    private static void sendFailure(ActionContext context, String message, double cost,
                                    String priceText, ItemStack stack) {
        String text = message == null ? "action.buy.failure" : message;
        if (message == null) {
            context.messages().send(context.player(), text, Map.of(
                    "price", priceText,
                    "item", stack.getType().name(),
                    "amount", stack.getAmount()
            ));
        } else {
            context.messages().sendRaw(context.player(), fillMessage(context, message, Map.of(
                    "price", priceText,
                    "item", stack.getType().name(),
                    "amount", stack.getAmount()
            )));
        }
    }

    private static ItemStack renderItem(ActionContext context, MenuItem item,
                                        Material fallbackMaterial, int fallbackAmount) {
        if (item != null) {
            return context.items().render(context.player(), item, context.session(), context.entry());
        }
        if (fallbackMaterial == null) {
            return null;
        }
        return new ItemStack(fallbackMaterial, Math.max(1, Math.min(64, fallbackAmount)));
    }

    private static String resolveCommand(ActionContext context, String command) {
        if (command == null) {
            return "";
        }
        Player player = context.player();
        String resolved = command
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{world}", player.getWorld().getName())
                .replace("{menu}", context.session() == null ? "" : context.session().menuId())
                .replace("{page}", context.session() == null ? "" : String.valueOf(context.session().page()))
                .replace("{pages}", context.session() == null ? "" : String.valueOf(context.session().pages()))
                .replace("{category}", context.session() == null || context.session().category() == null
                        ? "" : context.session().category())
                .replace("{query}", context.session() == null || context.session().query() == null
                        ? "" : context.session().query())
                .replace("{entry}", context.entry() == null ? "" : context.entry().id())
                .replace("{entry_id}", context.entry() == null ? "" : context.entry().id())
                .replace("{theme}", context.session() == null ? "" : context.session().themeId())
                .replace("{balance}", context.economy().available()
                        ? String.format(Locale.ROOT, "%.2f", context.economy().balance(player)) : "0")
                .replace("{balance_formatted}", context.economy().available()
                        ? context.economy().format(context.economy().balance(player)) : "0");
        resolved = context.placeholders().resolve(player, resolved);
        resolved = resolved.replace('\n', ' ').replace('\r', ' ').trim();
        if (resolved.startsWith("/")) {
            resolved = resolved.substring(1);
        }
        return resolved;
    }

    private static String render(ActionContext context, String text) {
        if (text == null) {
            return "";
        }
        return context.messages().render(context.player(), text);
    }

    private static String fillMessage(ActionContext context, String text, Map<String, ?> values) {
        String result = text;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        if (context.entry() != null) {
            result = result.replace("{entry}", context.entry().id());
            Object price = context.entry().metadata().get("price");
            if (price != null) {
                result = result.replace("{price}", String.valueOf(price));
            }
        }
        if (context.session() != null) {
            result = result.replace("{page}", String.valueOf(context.session().page()))
                    .replace("{pages}", String.valueOf(context.session().pages()))
                    .replace("{query}", context.session().query() == null ? "" : context.session().query());
        }
        return result;
    }

    private static String resolveThemeId(ActionContext context, String template) {
        MenuEntry entry = context.entry();
        Object metadata = null;
        if (entry != null) {
            metadata = entry.metadata().get("theme_id");
            if (metadata == null) {
                metadata = entry.metadata().get("theme");
            }
        }
        String sessionTheme = context.session() == null ? null : context.session().themeId();
        String value = template;
        if (value == null || value.isBlank()
                || value.equals("{theme}") || value.equals("{theme_id}") || value.equals("{entry}")) {
            if (metadata != null) {
                return String.valueOf(metadata);
            }
            if (sessionTheme != null && !sessionTheme.isBlank()) {
                return sessionTheme;
            }
            return entry == null ? null : entry.id();
        }
        if (metadata != null) {
            value = value.replace("{theme_id}", String.valueOf(metadata))
                    .replace("{theme}", String.valueOf(metadata));
        } else if (sessionTheme != null) {
            value = value.replace("{theme}", sessionTheme)
                    .replace("{theme_id}", sessionTheme);
        }
        if (entry != null) {
            value = value.replace("{entry}", entry.id());
        }
        return value;
    }

    private static int countMaterial(Inventory inventory, Material material) {
        int count = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static void removeMaterial(Inventory inventory, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            if (take >= stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
                inventory.setItem(slot, stack);
            }
            remaining -= take;
        }
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        return Arrays.stream(contents)
                .map(stack -> stack == null ? null : stack.clone())
                .toArray(ItemStack[]::new);
    }
}
