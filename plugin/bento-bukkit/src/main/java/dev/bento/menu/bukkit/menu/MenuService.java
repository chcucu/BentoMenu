package dev.bento.menu.bukkit.menu;

import dev.bento.menu.api.v1.BentoApi;
import dev.bento.menu.api.v1.Feedback;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.MenuOpenOptions;
import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.action.ActionContext;
import dev.bento.menu.api.v1.event.BentoMenuClickEvent;
import dev.bento.menu.api.v1.event.BentoMenuCloseEvent;
import dev.bento.menu.api.v1.event.BentoMenuOpenEvent;
import dev.bento.menu.api.v1.service.EconomyService;
import dev.bento.menu.api.v1.service.FeedbackService;
import dev.bento.menu.api.v1.service.ItemService;
import dev.bento.menu.api.v1.service.MessageService;
import dev.bento.menu.api.v1.service.PlaceholderService;
import dev.bento.menu.api.v1.service.Scheduler;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.bukkit.Metrics;
import dev.bento.menu.bukkit.data.PlayerProfileStore;
import dev.bento.menu.bukkit.input.InputService;
import dev.bento.menu.bukkit.integration.EconomyBridge;
import dev.bento.menu.bukkit.integration.FeedbackBridge;
import dev.bento.menu.bukkit.integration.MessageBridge;
import dev.bento.menu.bukkit.integration.PlaceholderBridge;
import dev.bento.menu.bukkit.render.ItemRenderer;
import dev.bento.menu.bukkit.render.MenuRenderer;
import dev.bento.menu.bukkit.scheduler.PlatformScheduler;
import dev.bento.menu.core.action.ActionAbortException;
import dev.bento.menu.core.condition.ConditionEvaluator;
import dev.bento.menu.core.config.PluginConfig;
import dev.bento.menu.core.menu.CompiledMenu;
import dev.bento.menu.core.menu.CooldownService;
import dev.bento.menu.core.menu.MenuFilter;
import dev.bento.menu.core.menu.MenuRepository;
import dev.bento.menu.core.theme.ThemeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Session lifecycle, click routing, cooldowns, actions and hot-reload refresh.
 */
public final class MenuService {

    private final Plugin plugin;
    private final PluginConfig config;
    private final MenuRepository menus;
    private final MenuFilter filter;
    private final PlatformScheduler scheduler;
    private final MenuRenderer renderer;
    private final MessageBridge messages;
    private final PlaceholderBridge placeholders;
    private final EconomyBridge economy;
    private final FeedbackBridge feedback;
    private final ItemRenderer items;
    private final PlayerProfileStore profiles;
    private final ThemeRegistry themes;
    private final CooldownService cooldowns;
    private final Metrics metrics;
    private final ExecutorService dataExecutor;
    private final ExecutorService searchExecutor;
    private final Theme fallbackTheme;
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    private BentoApi api;
    private InputService input;

    public MenuService(Plugin plugin, PluginConfig config, MenuRepository menus, MenuFilter filter,
                       PlatformScheduler scheduler, MenuRenderer renderer, MessageBridge messages,
                       PlaceholderBridge placeholders, EconomyBridge economy, FeedbackBridge feedback,
                       ItemRenderer items, PlayerProfileStore profiles, ThemeRegistry themes,
                       CooldownService cooldowns, Metrics metrics, ExecutorService dataExecutor,
                       ExecutorService searchExecutor, Theme fallbackTheme) {
        this.plugin = plugin;
        this.config = config;
        this.menus = menus;
        this.filter = filter;
        this.scheduler = scheduler;
        this.renderer = renderer;
        this.messages = messages;
        this.placeholders = placeholders;
        this.economy = economy;
        this.feedback = feedback;
        this.items = items;
        this.profiles = profiles;
        this.themes = themes;
        this.cooldowns = cooldowns;
        this.metrics = metrics;
        this.dataExecutor = dataExecutor;
        this.searchExecutor = searchExecutor;
        this.fallbackTheme = fallbackTheme;
    }

    public void setApi(BentoApi api) {
        this.api = api;
    }

    public void setInput(InputService input) {
        this.input = input;
    }

    public void open(Player player, String menuId, MenuOpenOptions options) {
        if (player == null) {
            return;
        }
        if (!scheduler.isOwnerThread(player)) {
            scheduler.runOnPlayer(player, () -> open(player, menuId, options));
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        Optional<CompiledMenu> resolved = menus.get(menuId);
        if (resolved.isEmpty()) {
            messages.send(player, "menu.unknown", Map.of("menu", menuId));
            return;
        }
        CompiledMenu menu = resolved.get();
        String permission = menu.definition().permission();
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            messages.send(player, "no-permission");
            return;
        }
        BentoMenuOpenEvent event = new BentoMenuOpenEvent(player, menu.definition(), options);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        MenuSession existing = sessions.get(player.getUniqueId());
        if (existing != null && existing.state() != MenuSession.State.CLOSED) {
            if (input != null) {
                input.cancel(player.getUniqueId());
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BentoInventoryHolder holder
                    && holder.kind() == BentoInventoryHolder.Kind.INPUT) {
                player.closeInventory();
            }
            existing.close(BentoMenuCloseEvent.CloseReason.REPLACED, false);
            scheduler.runOnPlayerLater(player, () -> openNow(player, menu, options), 1);
            return;
        }
        openNow(player, menu, options);
    }

    private void openNow(Player player, CompiledMenu menu, MenuOpenOptions options) {
        if (!player.isOnline()) {
            return;
        }
        MenuSession session = new MenuSession(this, player, menu, options);
        sessions.put(player.getUniqueId(), session);
        try {
            session.open();
        } catch (RuntimeException exception) {
            sessions.remove(player.getUniqueId(), session);
            plugin.getLogger().severe("Could not open menu '" + menu.id() + "': "
                    + exception.getMessage());
            messages.send(player, "menu.open-failed", Map.of("menu", menu.id()));
            return;
        }
        if (config.openSound() && !options.silent()) {
            feedback.sound(player, session.theme().sound("open", "block.chest.open"), 0.7f, 1.0f);
        }
    }

    public void close(Player player, BentoMenuCloseEvent.CloseReason reason, boolean runActions) {
        if (player == null) {
            return;
        }
        if (!scheduler.isOwnerThread(player)) {
            scheduler.runOnPlayer(player, () -> close(player, reason, runActions));
            return;
        }
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (input != null) {
            input.cancel(player.getUniqueId());
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BentoInventoryHolder holder
                && holder.kind() == BentoInventoryHolder.Kind.INPUT) {
            player.closeInventory();
        }
        session.close(reason, runActions);
    }

    public void refresh(Player player) {
        if (player == null) {
            return;
        }
        if (!scheduler.isOwnerThread(player)) {
            scheduler.runOnPlayer(player, () -> refresh(player));
            return;
        }
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.requestRefresh();
        }
    }

    public void refreshViewers(String menuId) {
        for (MenuSession session : new ArrayList<>(sessions.values())) {
            if (session.menuId().equals(menuId)) {
                scheduler.runOnPlayer(session.player(), session::requestRefresh);
            }
        }
    }

    public void refreshAll() {
        for (MenuSession session : new ArrayList<>(sessions.values())) {
            scheduler.runOnPlayer(session.player(), session::requestRefresh);
        }
    }

    public boolean isOpen(Player player) {
        MenuSession session = player == null ? null : sessions.get(player.getUniqueId());
        return session != null && (session.state() == MenuSession.State.OPEN
                || session.state() == MenuSession.State.INPUT);
    }

    public Optional<String> openMenu(Player player) {
        MenuSession session = player == null ? null : sessions.get(player.getUniqueId());
        return session == null ? Optional.empty() : Optional.of(session.menuId());
    }

    public MenuSession session(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public int sessionCount() {
        return sessions.size();
    }

    public void handleClick(InventoryClickEvent event) {
        org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
        if (config.debug()) {
            plugin.getLogger().info("CLICK raw=" + event.getRawSlot()
                    + " topHolder=" + top.getHolder() + " topType=" + top.getType());
        }
        if (!(top.getHolder() instanceof BentoInventoryHolder holder)) {
            if (input != null) {
                input.handleClick(event);
            }
            return;
        }
        if (holder.kind() == BentoInventoryHolder.Kind.INPUT) {
            if (input != null) {
                input.handleClick(event);
            }
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || session.holder() != holder) {
            return;
        }
        if (session.state() != MenuSession.State.OPEN) {
            return;
        }
        int rawSlot = event.getRawSlot();
        int topSize = top.getSize();
        if (rawSlot < 0 || rawSlot >= topSize) {
            return;
        }
        SlotBinding binding = session.binding(rawSlot);
        if (binding == null || !binding.enabled()) {
            return;
        }
        long started = System.nanoTime();
        MenuItem item = binding.item();
        ClickType clickType = event.getClick();
        if (item != null && !item.clickTypes().contains(clickType)) {
            return;
        }
        if (!cooldowns.acquireClick(player.getUniqueId(),
                Duration.ofMillis(config.clickDebounceMs()))) {
            return;
        }
        BentoMenuClickEvent clickEvent = new BentoMenuClickEvent(
                player, session, rawSlot, item, binding.entry(), clickType);
        Bukkit.getPluginManager().callEvent(clickEvent);
        if (clickEvent.isCancelled()) {
            return;
        }
        metrics.click(event, rawSlot);

        if (item != null && item.permission() != null && !item.permission().isBlank()
                && !player.hasPermission(item.permission())) {
            deny(player, session, null, "no-permission");
            return;
        }
        if (item != null && !item.conditions().isEmpty()) {
            ConditionEvaluator.Failure failure = ConditionEvaluator.firstFailure(item.conditions(),
                    ConditionEvaluator.context(player, session, binding.entry(), rawSlot,
                            api, economy, placeholders));
            if (failure != null) {
                deny(player, session, failure.message(), "condition.denied");
                return;
            }
        }
        String cooldownKey = cooldownKey(session, binding, rawSlot);
        if (item != null && item.cooldown() != null && !item.cooldown().isZero()
                && !player.hasPermission("bento.bypass.cooldown")) {
            long remaining = cooldowns.remainingMillis(player.getUniqueId(), cooldownKey);
            if (remaining > 0) {
                metrics.cooldownHit();
                String seconds = String.format(Locale.ROOT, "%.1f", remaining / 1000.0);
                String message = item.cooldownMessage();
                if (message != null && !message.isBlank()) {
                    messages.sendRaw(player, message.replace("{time}", seconds));
                } else {
                    messages.send(player, "button.cooldown", Map.of("time", seconds));
                }
                feedback.sound(player, session.theme().sound("error", "entity.villager.no"), 1.0f, 1.0f);
                return;
            }
            cooldowns.set(player.getUniqueId(), cooldownKey, item.cooldown());
        }

        playClickFeedback(player, session, item, binding.kind());
        metrics.clickHandled(System.nanoTime() - started);

        if (binding.kind() == SlotBinding.Kind.CLOSE) {
            if (item != null && !item.actions().isEmpty()) {
                executeActions(player, session, binding.entry(), rawSlot, clickType, item.actions());
            }
            close(player, BentoMenuCloseEvent.CloseReason.PLAYER, true);
            return;
        }

        switch (binding.kind()) {
            case SEARCH -> session.requestSearch();
            case PREVIOUS -> session.setPage(session.page() - 1);
            case NEXT -> session.setPage(session.page() + 1);
            case CATEGORY -> session.setCategory(binding.category());
            default -> {
            }
        }
        if (item != null && !item.actions().isEmpty()) {
            executeActions(player, session, binding.entry(), rawSlot, clickType, item.actions());
        }
        if (sessions.get(player.getUniqueId()) == session && player.isOnline()
                && session.state() == MenuSession.State.OPEN) {
            session.requestRefresh();
        }
    }

    private void playClickFeedback(Player player, MenuSession session, MenuItem item,
                                   SlotBinding.Kind kind) {
        Theme theme = session.theme();
        Feedback feedback = item == null ? null : item.feedback();
        boolean explicitSound = feedback != null && feedback.sound() != null;
        if (config.clickSound() && !explicitSound) {
            String sound = switch (kind) {
                case PREVIOUS, NEXT -> theme.sound("page", "item.book.page_turn");
                default -> theme.sound("click", "ui.button.click");
            };
            feedbackService().sound(player, sound, 0.8f, 1.1f);
        }
        if (feedback != null) {
            if (feedback.sound() != null) {
                feedbackService().sound(player, feedback.sound(), feedback.volume(), feedback.pitch());
            }
            feedbackService().playTitle(player, feedback, scheduler.forPlayer(player), messages);
        }
    }

    private FeedbackBridge feedbackService() {
        return feedback;
    }

    private void deny(Player player, MenuSession session, String message, String fallbackKey) {
        metrics.denied();
        if (message != null && !message.isBlank()) {
            messages.sendRaw(player, message);
        } else {
            messages.send(player, fallbackKey);
        }
        feedback.sound(player, session.theme().sound("error", "entity.villager.no"), 1.0f, 1.0f);
    }

    private String cooldownKey(MenuSession session, SlotBinding binding, int slot) {
        String prefix = session.menuId() + ":";
        return switch (binding.kind()) {
            case ENTRY -> binding.entry() == null ? prefix + "slot:" + slot
                    : prefix + "entry:" + binding.entry().id();
            case CATEGORY -> prefix + "category:" + (binding.category() == null ? "all" : binding.category());
            case SEARCH, PREVIOUS, NEXT, PAGE, CLOSE -> prefix + "component:" + binding.kind().name().toLowerCase(Locale.ROOT);
            default -> prefix + "slot:" + slot;
        };
    }

    public void executeActions(Player player, MenuSession session, MenuEntry entry,
                               int slot, ClickType clickType, List<Action> actions) {
        if (actions == null || actions.isEmpty() || !player.isOnline()) {
            return;
        }
        ActionContext context = new BukkitActionContext(player, session, entry, slot, clickType);
        for (Action action : actions) {
            if (!player.isOnline()) {
                return;
            }
            metrics.action();
            try {
                action.execute(context);
            } catch (ActionAbortException abort) {
                return;
            } catch (Exception exception) {
                plugin.getLogger().warning("Action " + action.getClass().getSimpleName()
                        + " failed in menu " + session.menuId() + " slot " + slot + ": "
                        + exception.getMessage());
                messages.send(player, "action.error", Map.of(
                        "action", action.getClass().getSimpleName()));
                if (config.actionErrorMode().equals("stop")) {
                    return;
                }
            }
        }
    }

    public void handlePrepareAnvil(PrepareAnvilEvent event) {
        if (input != null) {
            input.handlePrepare(event);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BentoInventoryHolder holder)) {
            if (input != null) {
                input.handleClose(event);
            }
            return;
        }
        if (holder.kind() == BentoInventoryHolder.Kind.INPUT) {
            if (input != null) {
                input.handleClose(event);
            }
            return;
        }
        MenuSession session = sessions.get(holder.playerId());
        if (session == null || session.holder() != holder) {
            return;
        }
        session.onInventoryClosed();
    }

    public void handleDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BentoInventoryHolder) {
            event.setCancelled(true);
            return;
        }
        if (input != null) {
            input.handleDrag(event);
        }
    }

    public void onQuit(Player player) {
        if (player == null) {
            return;
        }
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.onQuit();
        }
        if (input != null) {
            input.cancel(player.getUniqueId());
        }
        cooldowns.clear(player.getUniqueId());
        placeholders.clear(player);
        profiles.flushAsync();
    }

    public void fireClosed(MenuSession session, BentoMenuCloseEvent.CloseReason reason) {
        try {
            Bukkit.getPluginManager().callEvent(new BentoMenuCloseEvent(
                    session.player(), session.menuId(), reason));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("A menu-close listener threw: " + exception.getMessage());
        }
    }

    public void removeSession(MenuSession session) {
        sessions.remove(session.player().getUniqueId(), session);
    }

    public void onMenusReload(Map<String, CompiledMenu> replacements) {
        for (MenuSession session : new ArrayList<>(sessions.values())) {
            CompiledMenu replacement = replacements.get(session.menuId());
            Player player = session.player();
            if (replacement == null) {
                scheduler.runOnPlayer(player, () -> {
                    messages.send(player, "menu.removed", Map.of("menu", session.menuId()));
                    close(player, BentoMenuCloseEvent.CloseReason.PLUGIN, false);
                });
                continue;
            }
            if (replacement.size() != session.menu().size()) {
                MenuOpenOptions options = MenuOpenOptions.builder()
                        .page(session.page())
                        .category(session.category())
                        .query(session.query())
                        .themeOverride(session.options().themeOverride())
                        .build();
                scheduler.runOnPlayer(player, () -> open(player, session.menuId(), options));
                continue;
            }
            scheduler.runOnPlayer(player, () -> session.replaceMenu(replacement));
        }
    }

    public void onThemesReload() {
        for (MenuSession session : new ArrayList<>(sessions.values())) {
            scheduler.runOnPlayer(session.player(), session::applyTheme);
        }
    }

    public void shutdown() {
        for (MenuSession session : new ArrayList<>(sessions.values())) {
            session.cancelRefreshTask();
            session.state(MenuSession.State.CLOSED);
            sessions.remove(session.player().getUniqueId(), session);
            metrics.sessionClosed(session.player().getUniqueId());
        }
        sessions.clear();
        if (input != null) {
            input.clear();
        }
    }

    public void applyTheme(Player player) {
        if (!scheduler.isOwnerThread(player)) {
            scheduler.runOnPlayer(player, () -> applyTheme(player));
            return;
        }
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.applyTheme();
        }
    }

    public MenuRepository menus() {
        return menus;
    }

    public MenuFilter filter() {
        return filter;
    }

    public PlatformScheduler scheduler() {
        return scheduler;
    }

    public MenuRenderer renderer() {
        return renderer;
    }

    public MessageBridge messages() {
        return messages;
    }

    public PlaceholderBridge placeholders() {
        return placeholders;
    }

    public PlayerProfileStore profiles() {
        return profiles;
    }

    public ThemeRegistry themes() {
        return themes;
    }

    public Metrics metrics() {
        return metrics;
    }

    public ExecutorService dataExecutor() {
        return dataExecutor;
    }

    public ExecutorService searchExecutor() {
        return searchExecutor;
    }

    public InputService input() {
        return input;
    }

    public PluginConfig config() {
        return config;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public ItemRenderer items() {
        return items;
    }

    public FeedbackBridge feedback() {
        return feedback;
    }

    public Theme fallbackTheme() {
        return fallbackTheme;
    }

    public BentoApi api() {
        if (api == null) {
            throw new IllegalStateException("Bento API is not initialized yet");
        }
        return api;
    }

    private final class BukkitActionContext implements ActionContext {
        private final Player player;
        private final MenuSession session;
        private final MenuEntry entry;
        private final int slot;
        private final ClickType clickType;

        private BukkitActionContext(Player player, MenuSession session, MenuEntry entry,
                                    int slot, ClickType clickType) {
            this.player = player;
            this.session = session;
            this.entry = entry;
            this.slot = slot;
            this.clickType = clickType;
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public dev.bento.menu.api.v1.MenuSessionView session() {
            return session;
        }

        @Override
        public MenuEntry entry() {
            return entry;
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public ClickType clickType() {
            return clickType;
        }

        @Override
        public BentoApi api() {
            return MenuService.this.api();
        }

        @Override
        public Scheduler scheduler() {
            return scheduler.forPlayer(player);
        }

        @Override
        public EconomyService economy() {
            return economy;
        }

        @Override
        public PlaceholderService placeholders() {
            return placeholders;
        }

        @Override
        public MessageService messages() {
            return messages;
        }

        @Override
        public FeedbackService feedback() {
            return MenuService.this.feedback;
        }

        @Override
        public ItemService items() {
            return items;
        }

        @Override
        public void refresh() {
            if (session != null) {
                session.requestRefresh();
            } else {
                MenuService.this.refresh(player);
            }
        }
    }
}
