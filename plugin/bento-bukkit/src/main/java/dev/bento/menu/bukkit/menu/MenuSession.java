package dev.bento.menu.bukkit.menu;

import dev.bento.menu.api.v1.Layout;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuOpenOptions;
import dev.bento.menu.api.v1.MenuSessionView;
import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.data.DataSource;
import dev.bento.menu.api.v1.data.DataSourceContext;
import dev.bento.menu.api.v1.event.BentoMenuCloseEvent;
import dev.bento.menu.api.v1.service.RepeatingTask;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.bukkit.input.InputService;
import dev.bento.menu.core.menu.CompiledMenu;
import dev.bento.menu.core.menu.MenuFilter;
import dev.bento.menu.core.text.RenderedText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-player menu view. All mutable state is confined to the owner thread;
 * asynchronous results are marshalled back through the platform scheduler.
 */
public final class MenuSession implements MenuSessionView {

    public enum State {
        OPENING,
        OPEN,
        INPUT,
        CLOSING,
        CLOSED
    }

    private final MenuService service;
    private final Player player;
    private CompiledMenu menu;
    private final MenuOpenOptions options;
    private final BentoInventoryHolder holder;
    private String themeId;

    private volatile State state = State.OPENING;
    private Inventory inventory;
    private int page;
    private String category;
    private String query;
    private List<MenuEntry> sourceEntries = List.of();
    private MenuFilter.Result filterResult;
    private boolean loading = true;
    private String error;
    private long loadGeneration;
    private long filterGeneration;
    private boolean refreshScheduled;
    private boolean closeHandled;
    private RepeatingTask refreshTask;
    private RenderResult lastRender;
    private ItemStack[] lastContents = new ItemStack[0];
    private SlotBinding[] bindings = new SlotBinding[0];
    private InputService.InputSession input;

    MenuSession(MenuService service, Player player, CompiledMenu menu, MenuOpenOptions options) {
        this.service = service;
        this.player = player;
        this.menu = menu;
        this.options = options;
        this.holder = new BentoInventoryHolder(BentoInventoryHolder.Kind.MENU,
                player.getUniqueId(), menu.id());
        this.page = Math.max(1, options.page());
        this.category = MenuFilter.normalizeCategory(options.category());
        this.query = options.query() == null ? "" : options.query().trim();
        this.themeId = resolveThemeId();
    }

    private String resolveThemeId() {
        String forced = menu.definition().forcedThemeId();
        if (forced != null && !forced.isBlank() && service.themes().get(forced).isPresent()) {
            return forced;
        }
        if (options.themeOverride() != null && !options.themeOverride().isBlank()
                && service.themes().get(options.themeOverride()).isPresent()) {
            return options.themeOverride();
        }
        return service.profiles().themeIdFor(player);
    }

    public void open() {
        if (state != State.OPENING || !player.isOnline()) {
            return;
        }
        String title = service.renderer().title(menu, this);
        if (title.isEmpty()) {
            title = " ";
        }
        inventory = Bukkit.createInventory(holder, menu.size(), title);
        holder.inventory(inventory);
        RenderResult skeleton = service.renderer().render(this, true, null);
        for (int slot = 0; slot < skeleton.contents().length; slot++) {
            inventory.setItem(slot, skeleton.contents()[slot]);
        }
        lastContents = skeleton.contents().clone();
        bindings = skeleton.bindings().clone();
        lastRender = skeleton;
        player.openInventory(inventory);
        state = State.OPEN;
        service.metrics().sessionOpened(player.getUniqueId());
        startRefreshTask();
        preloadPlaceholders();
        if (!menu.definition().openActions().isEmpty()) {
            service.executeActions(player, this, null, -1, ClickType.LEFT,
                    menu.definition().openActions());
        }
        loadData(true);
    }

    private void startRefreshTask() {
        Duration interval = menu.refreshInterval();
        if (interval == null || interval.isZero()) {
            return;
        }
        long ticks = Math.max(1, (interval.toMillis() + 49) / 50);
        refreshTask = service.scheduler().runOnPlayerTimer(player, this::periodicRefresh, ticks, ticks);
    }

    private void periodicRefresh() {
        if (state != State.OPEN) {
            return;
        }
        if (menu.isStatic()) {
            renderNow();
        } else {
            loadData(false);
        }
    }

    private void loadData(boolean initial) {
        if (state != State.OPEN && state != State.INPUT) {
            return;
        }
        long generation = ++loadGeneration;
        if (initial || filterResult == null) {
            loading = true;
            error = null;
            if (initial) {
                renderNow();
            }
        }
        if (menu.isStatic()) {
            List<MenuEntry> entries = menu.staticEntries();
            runFilter(generation, entries, initial);
            return;
        }
        DataSource source = menu.source();
        if (source == null) {
            menu.retrySource();
            fail(generation, service.messages().renderKey(player, "menu.data-source-missing",
                    Map.of("source", String.valueOf(menu.sourceId()), "menu", menu.id())), true);
            return;
        }
        String capturedCategory = category;
        String capturedQuery = query;
        int capturedPage = page;
        DataSourceContext context = new DataSourceContext() {
            @Override
            public Player player() {
                return player;
            }

            @Override
            public MenuSessionView session() {
                return MenuSession.this;
            }

            @Override
            public String menuId() {
                return menu.id();
            }

            @Override
            public int page() {
                return capturedPage;
            }

            @Override
            public String category() {
                return capturedCategory;
            }

            @Override
            public String query() {
                return capturedQuery;
            }

            @Override
            public dev.bento.menu.api.v1.service.PlaceholderService placeholders() {
                return service.placeholders();
            }
        };
        service.dataExecutor().execute(() -> {
            try {
                List<MenuEntry> entries = source.load(context);
                if (entries == null) {
                    entries = List.of();
                }
                List<MenuEntry> snapshot = List.copyOf(entries);
                runFilter(generation, snapshot, initial);
            } catch (Exception exception) {
                String message = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                service.scheduler().runOnPlayer(player,
                        () -> fail(generation, message, initial));
            }
        });
    }

    private void runFilter(long generation, List<MenuEntry> entries, boolean initial) {
        String capturedCategory = category;
        String capturedQuery = query;
        int capturedPage = page;
        service.searchExecutor().execute(() -> {
            MenuFilter.Result result = service.filter().apply(entries,
                    new MenuFilter.Query(capturedPage, menu.pageSize(), capturedCategory,
                            capturedQuery, menu.sortMode()),
                    menu.categoryOrder());
            service.scheduler().runOnPlayer(player, () -> {
                if (generation != loadGeneration || state != State.OPEN && state != State.INPUT) {
                    return;
                }
                sourceEntries = entries;
                filterResult = result;
                page = result.page();
                loading = false;
                error = null;
                renderNow();
                preloadPlaceholders();
            });
        });
    }

    private void fail(long generation, String message, boolean replaceView) {
        if (generation != loadGeneration || (state != State.OPEN && state != State.INPUT)) {
            return;
        }
        loading = false;
        error = message;
        if (replaceView || filterResult == null) {
            renderNow();
        }
        service.messages().send(player, "menu.data-error", Map.of(
                "menu", menu.id(),
                "error", message == null ? "unknown" : message
        ));
    }

    private void requestFilter() {
        if (state != State.OPEN && state != State.INPUT) {
            return;
        }
        if (loading) {
            return;
        }
        long generation = ++filterGeneration;
        List<MenuEntry> entries = sourceEntries;
        String capturedCategory = category;
        String capturedQuery = query;
        int capturedPage = page;
        service.searchExecutor().execute(() -> {
            MenuFilter.Result result = service.filter().apply(entries,
                    new MenuFilter.Query(capturedPage, menu.pageSize(), capturedCategory,
                            capturedQuery, menu.sortMode()),
                    menu.categoryOrder());
            service.scheduler().runOnPlayer(player, () -> {
                if (generation != filterGeneration || (state != State.OPEN && state != State.INPUT)) {
                    return;
                }
                filterResult = result;
                page = result.page();
                renderNow();
            });
        });
    }

    private void renderNow() {
        if (inventory == null || state == State.CLOSED || state == State.CLOSING) {
            return;
        }
        long started = System.nanoTime();
        RenderResult result = service.renderer().render(this,
                loading && filterResult == null, error);
        int updates = 0;
        ItemStack[] contents = result.contents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack previous = slot < lastContents.length ? lastContents[slot] : null;
            ItemStack next = contents[slot];
            if (!Objects.equals(previous, next)) {
                inventory.setItem(slot, next);
                updates++;
            }
        }
        lastContents = contents.clone();
        bindings = result.bindings().clone();
        lastRender = result;
        if (player.getOpenInventory().getTopInventory() == inventory) {
            InventoryTitles.set(player.getOpenInventory(), result.title());
        }
        service.metrics().render(System.nanoTime() - started, updates);
    }

    public void requestRefresh() {
        if (!service.scheduler().isOwnerThread(player)) {
            service.scheduler().runOnPlayer(player, this::requestRefresh);
            return;
        }
        if (state != State.OPEN || refreshScheduled) {
            return;
        }
        refreshScheduled = true;
        service.scheduler().runOnPlayerLater(player, () -> {
            refreshScheduled = false;
            if (state != State.OPEN) {
                return;
            }
            if (menu.isStatic()) {
                renderNow();
            } else {
                loadData(false);
            }
        }, 1);
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public String menuId() {
        return menu.id();
    }

    @Override
    public String themeId() {
        return themeId;
    }

    @Override
    public int page() {
        return page;
    }

    @Override
    public int pages() {
        return filterResult == null ? 1 : filterResult.pages();
    }

    @Override
    public int pageSize() {
        return menu.pageSize();
    }

    @Override
    public int totalEntries() {
        return filterResult == null ? 0 : filterResult.total();
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public String query() {
        return query;
    }

    @Override
    public List<MenuEntry> visibleEntries() {
        return filterResult == null ? List.of() : filterResult.pageEntries();
    }

    public List<String> categories() {
        return filterResult == null ? List.of() : filterResult.categories();
    }

    @Override
    public void setPage(int page) {
        if (!service.scheduler().isOwnerThread(player)) {
            int target = page;
            service.scheduler().runOnPlayer(player, () -> setPage(target));
            return;
        }
        if (state != State.OPEN && state != State.INPUT) {
            return;
        }
        int pages = pages();
        int target = Math.max(1, Math.min(pages, page));
        if (target == this.page) {
            return;
        }
        this.page = target;
        filterGeneration++;
        if (filterResult != null) {
            filterResult = service.filter().apply(sourceEntries,
                    new MenuFilter.Query(target, menu.pageSize(), category, query, menu.sortMode()),
                    menu.categoryOrder());
            this.page = filterResult.page();
        }
        renderNow();
    }

    @Override
    public void setCategory(String category) {
        if (!service.scheduler().isOwnerThread(player)) {
            service.scheduler().runOnPlayer(player, () -> setCategory(category));
            return;
        }
        String normalized = MenuFilter.normalizeCategory(category);
        if (Objects.equals(normalized, this.category)) {
            return;
        }
        this.category = normalized;
        this.page = 1;
        requestFilter();
    }

    @Override
    public void setQuery(String query) {
        if (!service.scheduler().isOwnerThread(player)) {
            service.scheduler().runOnPlayer(player, () -> setQuery(query));
            return;
        }
        String normalized = query == null ? "" : sanitizeQuery(query);
        if (normalized.equals(this.query)) {
            return;
        }
        this.query = normalized;
        this.page = 1;
        requestFilter();
    }

    @Override
    public void requestSearch() {
        if (!service.scheduler().isOwnerThread(player)) {
            service.scheduler().runOnPlayer(player, this::requestSearch);
            return;
        }
        if (state == State.OPEN) {
            service.input().open(this);
        }
    }

    public void reopenAfterInput() {
        if (state != State.OPEN || !player.isOnline()) {
            return;
        }
        renderNow();
        service.scheduler().runOnPlayerLater(player, () -> {
            if (state == State.OPEN && player.isOnline() && inventory != null) {
                player.openInventory(inventory);
            }
        }, 1);
    }

    public void onInputOpened(InputService.InputSession input) {
        this.input = input;
        this.state = State.INPUT;
    }

    public void onInputClosed(InputService.InputSession input) {
        if (this.input == input) {
            this.input = null;
        }
        if (state == State.INPUT) {
            state = State.OPEN;
        }
    }

    public void applyTheme() {
        String resolved = resolveThemeId();
        if (!Objects.equals(resolved, themeId)) {
            themeId = resolved;
            renderNow();
        }
    }

    public Theme theme() {
        Theme theme = service.themes().get(themeId).orElse(null);
        if (theme != null) {
            return theme;
        }
        theme = service.profiles().themeFor(player);
        return theme == null ? service.fallbackTheme() : theme;
    }

    public CompiledMenu menu() {
        return menu;
    }

    public boolean isLoading() {
        return loading && filterResult == null;
    }

    /**
     * Replaces the compiled menu after a hot reload. The inventory size must
     * match; larger structural changes reopen the view from MenuService.
     */
    public void replaceMenu(CompiledMenu replacement) {
        if (replacement == null) {
            return;
        }
        this.menu = replacement;
        this.themeId = resolveThemeId();
        this.page = Math.min(Math.max(1, this.page), Math.max(1, pages()));
        cancelRefreshTask();
        startRefreshTask();
        if (menu.isStatic()) {
            sourceEntries = menu.staticEntries();
        }
        loadData(false);
        preloadPlaceholders();
    }

    public State state() {
        return state;
    }

    public void state(State state) {
        this.state = state;
    }

    public BentoInventoryHolder holder() {
        return holder;
    }

    public Inventory inventory() {
        return inventory;
    }

    public SlotBinding binding(int slot) {
        return slot >= 0 && slot < bindings.length ? bindings[slot] : null;
    }

    public RenderResult lastRender() {
        return lastRender;
    }

    public InputService.InputSession input() {
        return input;
    }

    public void close(BentoMenuCloseEvent.CloseReason reason, boolean runActions) {
        if (state == State.CLOSED || state == State.CLOSING) {
            return;
        }
        state = State.CLOSING;
        cancelRefreshTask();
        if (inventory != null && player.getOpenInventory().getTopInventory() == inventory) {
            player.closeInventory();
        }
        state = State.CLOSED;
        service.removeSession(this);
        service.metrics().sessionClosed(player.getUniqueId());
        if (!closeHandled) {
            closeHandled = true;
            service.fireClosed(this, reason);
            if (runActions && !menu.definition().closeActions().isEmpty() && player.isOnline()) {
                service.executeActions(player, this, null, -1, ClickType.LEFT,
                        menu.definition().closeActions());
            }
        }
    }

    /** Called from InventoryCloseEvent when the player closes the menu. */
    public void onInventoryClosed() {
        if (state == State.INPUT) {
            return;
        }
        if (state == State.CLOSED || state == State.CLOSING) {
            state = State.CLOSED;
            return;
        }
        state = State.CLOSING;
        cancelRefreshTask();
        state = State.CLOSED;
        service.removeSession(this);
        service.metrics().sessionClosed(player.getUniqueId());
        if (!closeHandled) {
            closeHandled = true;
            service.fireClosed(this, BentoMenuCloseEvent.CloseReason.PLAYER);
            if (!menu.definition().closeActions().isEmpty() && player.isOnline()) {
                service.executeActions(player, this, null, -1, ClickType.LEFT,
                        menu.definition().closeActions());
            }
        }
    }

    public void onQuit() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        cancelRefreshTask();
        service.removeSession(this);
        service.metrics().sessionClosed(player.getUniqueId());
    }

    void cancelRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void preloadPlaceholders() {
        Set<String> texts = new LinkedHashSet<>();
        texts.add(menu.title());
        menu.staticItems().values().forEach(item -> addItemTexts(texts, item));
        menu.components().values().forEach(item -> addItemTexts(texts, item));
        List<MenuEntry> entries = filterResult == null ? menu.staticEntries() : sourceEntries;
        for (MenuEntry entry : entries) {
            addItemTexts(texts, entry.item());
        }
        service.placeholders().preload(player, texts);
    }

    private static void addItemTexts(Set<String> texts, dev.bento.menu.api.v1.MenuItem item) {
        if (item.name() != null) {
            texts.add(item.name());
        }
        texts.addAll(item.lore());
    }

    private static String sanitizeQuery(String query) {
        String value = query.replace('\u00a7', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    public MenuOpenOptions options() {
        return options;
    }

    MenuService service() {
        return service;
    }
}
