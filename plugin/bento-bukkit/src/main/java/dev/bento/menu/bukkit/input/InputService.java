package dev.bento.menu.bukkit.input;

import dev.bento.menu.bukkit.menu.BentoInventoryHolder;
import dev.bento.menu.bukkit.menu.MenuSession;
import dev.bento.menu.bukkit.menu.MenuService;
import dev.bento.menu.core.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menu search input. Anvil input is used by default because it keeps the menu
 * context and is fully public-API; chat input is an explicit/fallback mode.
 */
public final class InputService {

    public static final class InputSession {
        private final MenuSession session;
        private final boolean chat;
        private final Inventory inventory;
        private boolean submitted;

        private InputSession(MenuSession session, boolean chat, Inventory inventory) {
            this.session = session;
            this.chat = chat;
            this.inventory = inventory;
        }

        public MenuSession session() {
            return session;
        }

        public boolean chat() {
            return chat;
        }

        public Inventory inventory() {
            return inventory;
        }

        public boolean submitted() {
            return submitted;
        }
    }

    private final Plugin plugin;
    private final MenuService service;
    private final PluginConfig config;
    private final Map<UUID, InputSession> inputs = new ConcurrentHashMap<>();

    public InputService(Plugin plugin, MenuService service, PluginConfig config) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
    }

    public void open(MenuSession session) {
        Player player = session.player();
        if (!service.scheduler().isOwnerThread(player)) {
            service.scheduler().runOnPlayer(player, () -> open(session));
            return;
        }
        if (session.state() != MenuSession.State.OPEN || inputs.containsKey(player.getUniqueId())) {
            return;
        }
        String mode = config.searchInput();
        if (mode.equalsIgnoreCase("chat")) {
            openChat(session);
            return;
        }
        try {
            openAnvil(session);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Anvil input unavailable, falling back to chat: "
                    + exception.getMessage());
            openChat(session);
        }
    }

    private void openAnvil(MenuSession session) {
        Player player = session.player();
        String title = service.messages().renderKey(player, "search.title", Map.of());
        if (title == null || title.isBlank()) {
            title = "Search";
        }
        // A real anvil inventory (no custom holder) keeps AnvilInventory semantics
        // on Spigot/Paper, including getRenameText()/PrepareAnvilEvent.
        Inventory inventory = Bukkit.createInventory(null, InventoryType.ANVIL, title);
        if (!(inventory instanceof AnvilInventory anvil)) {
            // Some hybrid/API implementations expose ANVIL as a generic inventory.
            // Fall back to chat input instead of shipping a non-working prompt.
            openChat(session);
            return;
        }
        String initial = session.query() == null ? "" : session.query();
        ItemStack inputItem = new ItemStack(Material.PAPER);
        ItemMeta meta = inputItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(initial.isBlank()
                    ? service.messages().renderKey(player, "search.placeholder", Map.of())
                    : initial);
            inputItem.setItemMeta(meta);
        }
        inventory.setItem(0, inputItem);
        anvil.setRepairCost(0);
        anvil.setMaximumRepairCost(Integer.MAX_VALUE);
        InputSession input = new InputSession(session, false, inventory);
        inputs.put(player.getUniqueId(), input);
        session.onInputOpened(input);
        player.openInventory(inventory);
        if (config.debug()) {
            plugin.getLogger().info("Search anvil opened for " + player.getName()
                    + " query=" + initial);
        }
    }

    private void openChat(MenuSession session) {
        Player player = session.player();
        InputSession input = new InputSession(session, true, null);
        inputs.put(player.getUniqueId(), input);
        session.onInputOpened(input);
        if (session.inventory() != null && player.getOpenInventory().getTopInventory() == session.inventory()) {
            player.closeInventory();
        }
        boolean registered;
        try {
            registered = ChatInputListener.register(plugin, this);
        } catch (NoClassDefFoundError error) {
            registered = false;
        }
        boolean modern;
        try {
            modern = ModernChatInput.register(plugin, this);
        } catch (NoClassDefFoundError error) {
            modern = false;
        }
        registered = registered || modern;
        if (registered) {
            service.messages().send(player, "search.chat-prompt");
        } else {
            service.messages().send(player, "search.unavailable");
            inputs.remove(player.getUniqueId());
            session.onInputClosed(input);
            session.reopenAfterInput();
        }
    }

    public void handleClick(InventoryClickEvent event) {
        org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InputSession input = inputs.get(player.getUniqueId());
        if (input == null || input.chat || input.inventory != top) {
            return;
        }
        event.setCancelled(true);
        if (config.debug()) {
            plugin.getLogger().info("INPUT_CLICK raw=" + event.getRawSlot()
                    + " topType=" + top.getType() + " anvil=" + (top instanceof AnvilInventory));
        }
        if (event.getRawSlot() == 2 && top instanceof AnvilInventory anvil) {
            String text = anvil.getRenameText();
            if (config.debug()) {
                plugin.getLogger().info("Search result clicked for " + player.getName()
                        + " text=" + text);
            }
            submit(input, text == null ? "" : text);
        }
    }

    public void handlePrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        InputSession input = inputs.get(player.getUniqueId());
        if (input == null || input.chat || input.inventory != event.getInventory()) {
            return;
        }
        String rename = event.getInventory().getRenameText();
        if (config.debug()) {
            plugin.getLogger().info("PREPARE_ANVIL rename=" + rename
                    + " anvil=" + (event.getInventory() instanceof AnvilInventory));
        }
        if (config.debug()) {
            plugin.getLogger().info("PrepareAnvil for " + player.getName()
                    + " rename=" + rename + " holder=" + event.getInventory().getHolder());
        }
        String display = rename == null || rename.isBlank()
                ? service.messages().renderKey(player, "search.confirm", Map.of())
                : service.messages().renderKey(player, "search.confirm", Map.of("query", rename));
        ItemStack result = new ItemStack(Material.COMPASS);
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(display == null ? "Search" : display);
            meta.setLore(java.util.List.of(service.messages().renderKey(
                    player, "search.confirm-lore", Map.of())));
            result.setItemMeta(meta);
        }
        event.setResult(result);
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InputSession input = inputs.get(player.getUniqueId());
        if (input == null || input.chat || input.inventory != event.getView().getTopInventory()) {
            return;
        }
        if (input.submitted) {
            inputs.remove(player.getUniqueId());
            return;
        }
        inputs.remove(player.getUniqueId());
        input.session.onInputClosed(input);
        if (input.session.state() == MenuSession.State.CLOSED) {
            return;
        }
        service.scheduler().runOnPlayerLater(player, input.session::reopenAfterInput, 1);
    }

    public boolean hasChatInput(UUID playerId) {
        InputSession input = inputs.get(playerId);
        return input != null && input.chat;
    }

    public void handleDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        InputSession input = inputs.get(player.getUniqueId());
        if (input != null && !input.chat && input.inventory == event.getView().getTopInventory()) {
            event.setCancelled(true);
        }
    }

    public void handleChat(Player player, String message) {
        InputSession input = inputs.get(player.getUniqueId());
        if (input == null || !input.chat) {
            return;
        }
        inputs.remove(player.getUniqueId());
        String text = message == null ? "" : message.trim();
        if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("取消")) {
            service.scheduler().runOnPlayer(player, () -> {
                input.session.onInputClosed(input);
                input.session.reopenAfterInput();
            });
            return;
        }
        service.scheduler().runOnPlayer(player, () -> submit(input, text));
    }

    private void submit(InputSession input, String rawText) {
        if (input.submitted) {
            return;
        }
        input.submitted = true;
        Player player = input.session.player();
        String text = sanitize(rawText);
        if (text.length() > config.maxSearchLength()) {
            text = text.substring(0, config.maxSearchLength());
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BentoInventoryHolder holder
                && holder.kind() == BentoInventoryHolder.Kind.INPUT) {
            player.closeInventory();
        }
        input.session.onInputClosed(input);
        input.session.setQuery(text);
        if (config.debug()) {
            plugin.getLogger().info("Search submitted for " + player.getName()
                    + " query=" + text + " state=" + input.session.state());
        }
        service.feedback().sound(player, input.session.theme().sound("page", "item.book.page_turn"), 0.8f, 1.2f);
        input.session.reopenAfterInput();
        if (config.debug()) {
            plugin.getLogger().info("Search reopen scheduled for " + player.getName());
        }
    }

    public void cancel(UUID playerId) {
        inputs.remove(playerId);
    }

    public void clear() {
        inputs.clear();
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00a7', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
