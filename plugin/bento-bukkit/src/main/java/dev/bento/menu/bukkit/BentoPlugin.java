package dev.bento.menu.bukkit;

import dev.bento.menu.api.v1.BentoApi;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.MenuOpenOptions;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.bukkit.api.BentoApiImpl;
import dev.bento.menu.bukkit.command.BentoCommand;
import dev.bento.menu.bukkit.config.AutoReloadWatcher;
import dev.bento.menu.bukkit.config.ConfigManager;
import dev.bento.menu.bukkit.config.DefaultResources;
import dev.bento.menu.bukkit.data.PlayerProfileStore;
import dev.bento.menu.bukkit.input.InputService;
import dev.bento.menu.bukkit.integration.BentoPlaceholderExpansion;
import dev.bento.menu.bukkit.integration.EconomyBridge;
import dev.bento.menu.bukkit.integration.FeedbackBridge;
import dev.bento.menu.bukkit.integration.MessageBridge;
import dev.bento.menu.bukkit.integration.PlaceholderBridge;
import dev.bento.menu.bukkit.listener.MenuListener;
import dev.bento.menu.bukkit.menu.MenuService;
import dev.bento.menu.bukkit.render.ItemRenderer;
import dev.bento.menu.bukkit.render.MenuRenderer;
import dev.bento.menu.bukkit.scheduler.PlatformScheduler;
import dev.bento.menu.core.action.ActionRegistry;
import dev.bento.menu.core.condition.ConditionRegistry;
import dev.bento.menu.core.config.MessageCatalog;
import dev.bento.menu.core.config.PluginConfig;
import dev.bento.menu.core.data.DataSourceRegistry;
import dev.bento.menu.core.menu.CooldownService;
import dev.bento.menu.core.menu.LayoutRegistry;
import dev.bento.menu.core.menu.MenuFilter;
import dev.bento.menu.core.menu.MenuRepository;
import dev.bento.menu.core.report.ValidationReport;
import dev.bento.menu.core.text.RenderedText;
import dev.bento.menu.core.text.TextEngine;
import dev.bento.menu.core.theme.ThemeLoader;
import dev.bento.menu.core.theme.ThemeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BentoMenu plugin entry point. One jar supports Spigot, Paper and Folia from
 * 1.18 through 26.x by using only public Bukkit APIs and runtime feature
 * detection.
 */
public final class BentoPlugin extends JavaPlugin {

    private PluginConfig config;
    private MessageCatalog messageCatalog;
    private TextEngine textEngine;
    private ThemeRegistry themeRegistry;
    private LayoutRegistry layoutRegistry;
    private MenuRepository menuRepository;
    private ActionRegistry actionRegistry;
    private ConditionRegistry conditionRegistry;
    private DataSourceRegistry dataSourceRegistry;
    private PlayerProfileStore profiles;
    private PlatformScheduler scheduler;
    private ScheduledExecutorService asyncScheduler;
    private ExecutorService dataExecutor;
    private ExecutorService searchExecutor;
    private ExecutorService placeholderExecutor;
    private EconomyBridge economy;
    private PlaceholderBridge placeholders;
    private MessageBridge messages;
    private FeedbackBridge feedback;
    private ItemRenderer itemRenderer;
    private MenuRenderer menuRenderer;
    private MenuFilter filter;
    private CooldownService cooldowns;
    private Metrics metrics;
    private MenuService menuService;
    private InputService inputService;
    private BentoApiImpl api;
    private ConfigManager configManager;
    private AutoReloadWatcher watcher;
    private BentoPlaceholderExpansion expansion;
    private Theme fallbackTheme;
    private volatile ConfigManager.LoadedConfig lastLoaded;
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final AtomicBoolean validating = new AtomicBoolean();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create the BentoMenu data folder");
        }
        actionRegistry = new ActionRegistry();
        conditionRegistry = new ConditionRegistry();
        dataSourceRegistry = new DataSourceRegistry();
        configManager = new ConfigManager(this, actionRegistry, conditionRegistry, dataSourceRegistry);
        configManager.saveDefaults();

        ConfigManager.LoadedConfig loaded = configManager.load(null);
        lastLoaded = loaded;
        config = loaded.config();
        messageCatalog = loaded.messages();
        textEngine = new TextEngine();
        themeRegistry = new ThemeRegistry();
        layoutRegistry = new LayoutRegistry();
        menuRepository = new MenuRepository();
        themeRegistry.replaceYaml(loaded.themes());
        layoutRegistry.replaceAll(loaded.layouts());
        Set<String> skipped = menuRepository.replaceYaml(loaded.menus());

        asyncScheduler = Executors.newScheduledThreadPool(2, named("BentoMenu-Async"));
        dataExecutor = Executors.newFixedThreadPool(config.dataThreads(), named("BentoMenu-Data"));
        searchExecutor = Executors.newFixedThreadPool(config.searchThreads(), named("BentoMenu-Search"));
        placeholderExecutor = Executors.newSingleThreadExecutor(named("BentoMenu-PAPI"));
        scheduler = new PlatformScheduler(this, asyncScheduler);
        fallbackTheme = resolveFallbackTheme();

        profiles = new PlayerProfileStore(this, config, themeRegistry, messageCatalog,
                new File(getDataFolder(), "playerdata.yml"), asyncScheduler);
        placeholders = new PlaceholderBridge(this, placeholderExecutor, config.placeholderCacheTtl());
        economy = new EconomyBridge(this, config.currencyFormat());
        messages = new MessageBridge(this, config, messageCatalog, textEngine, profiles,
                placeholders, economy, themeRegistry, fallbackTheme);
        feedback = new FeedbackBridge(this);
        itemRenderer = new ItemRenderer(textEngine, placeholders, economy, profiles,
                themeRegistry, fallbackTheme);
        menuRenderer = new MenuRenderer(this, config, textEngine, itemRenderer, messages,
                placeholders, economy, profiles);
        filter = new MenuFilter(textEngine);
        cooldowns = new CooldownService();
        metrics = new Metrics(config.metrics());
        menuService = new MenuService(this, config, menuRepository, filter, scheduler, menuRenderer,
                messages, placeholders, economy, feedback, itemRenderer, profiles, themeRegistry,
                cooldowns, metrics, dataExecutor, searchExecutor, fallbackTheme);
        inputService = new InputService(this, menuService, config);
        menuService.setInput(inputService);
        api = new BentoApiImpl(this, menuRepository, layoutRegistry, themeRegistry, actionRegistry,
                conditionRegistry, dataSourceRegistry, profiles, menuService, scheduler);
        menuService.setApi(api);
        getServer().getServicesManager().register(BentoApi.class, api, this, ServicePriority.Normal);

        PluginCommand command = getCommand("bento");
        if (command != null) {
            BentoCommand handler = new BentoCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().severe("Command 'bento' is missing from plugin.yml");
        }
        getServer().getPluginManager().registerEvents(new MenuListener(menuService), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new BentoPlaceholderExpansion(api, getDescription().getVersion(),
                    menuService::sessionCount);
            try {
                expansion.register();
            } catch (RuntimeException exception) {
                getLogger().warning("Could not register the BentoMenu PlaceholderAPI expansion: "
                        + exception.getMessage());
            }
        }

        watcher = new AutoReloadWatcher(this, asyncScheduler);
        if (config.autoReload()) {
            watcher.start();
        }

        logReport(loaded.report());
        if (!skipped.isEmpty()) {
            getLogger().warning("YAML menus shadowed by API menus: " + String.join(", ", skipped));
        }
        getLogger().info("BentoMenu enabled: " + menuRepository.size() + " menus, "
                + themeRegistry.size() + " themes, Folia=" + scheduler.isFolia());
    }

    @Override
    public void onDisable() {
        if (watcher != null) {
            watcher.stop();
        }
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (RuntimeException ignored) {
                // PlaceholderAPI may already be disabled.
            }
        }
        if (menuService != null) {
            menuService.shutdown();
        }
        if (profiles != null) {
            profiles.saveNow();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (asyncScheduler != null) {
            asyncScheduler.shutdownNow();
        }
        shutdown(dataExecutor);
        shutdown(searchExecutor);
        shutdown(placeholderExecutor);
        getLogger().info("BentoMenu disabled.");
    }

    public void reloadAsync(CommandSender sender, boolean reportToSender) {
        if (!reloading.compareAndSet(false, true)) {
            if (reportToSender) {
                reply(sender, "reload.in-progress", Map.of());
            }
            return;
        }
        asyncScheduler.execute(() -> {
            ConfigManager.LoadedConfig loaded;
            try {
                loaded = configManager.load(lastLoaded);
            } catch (Throwable throwable) {
                reloading.set(false);
                getLogger().severe("BentoMenu reload failed: " + throwable.getMessage());
                if (reportToSender) {
                    reply(sender, "reload.failed", Map.of(
                            "error", throwable.getMessage() == null
                                    ? throwable.getClass().getSimpleName() : throwable.getMessage()));
                }
                return;
            }
            scheduler.runGlobal(() -> {
                try {
                    applyLoaded(loaded, sender, reportToSender);
                } finally {
                    reloading.set(false);
                }
            });
        });
    }

    public void validateAsync(CommandSender sender) {
        if (!validating.compareAndSet(false, true)) {
            reply(sender, "validate.in-progress", Map.of());
            return;
        }
        asyncScheduler.execute(() -> {
            ValidationReport report;
            try {
                ConfigManager.LoadedConfig loaded = configManager.load(lastLoaded);
                report = loaded.report();
            } catch (Throwable throwable) {
                report = new ValidationReport();
                report.error("config", "", throwable.getMessage() == null
                        ? throwable.getClass().getSimpleName() : throwable.getMessage());
            }
            ValidationReport finalReport = report;
            scheduler.runGlobal(() -> {
                try {
                    sendValidation(sender, finalReport);
                } finally {
                    validating.set(false);
                }
            });
        });
    }

    private void applyLoaded(ConfigManager.LoadedConfig loaded, CommandSender sender,
                             boolean reportToSender) {
        if (!isEnabled()) {
            return;
        }
        config.updateFrom(loaded.config());
        messageCatalog = loaded.messages();
        messages.setCatalog(loaded.messages());
        profiles.setCatalog(loaded.messages());
        themeRegistry.replaceYaml(loaded.themes());
        layoutRegistry.replaceAll(loaded.layouts());
        Set<String> skipped = menuRepository.replaceYaml(loaded.menus());
        lastLoaded = loaded;
        menuService.onMenusReload(menuRepository.map());
        menuService.onThemesReload();
        if (config.autoReload()) {
            watcher.start();
        } else {
            watcher.stop();
        }
        logReport(loaded.report());
        if (!skipped.isEmpty()) {
            getLogger().warning("YAML menus shadowed by API menus: " + String.join(", ", skipped));
        }
        if (reportToSender) {
            int errors = loaded.report().errorCount();
            int warnings = loaded.report().warningCount();
            String key = errors == 0 ? "reload.success" : "reload.partial";
            reply(sender, key, Map.of(
                    "menus", menuRepository.size(),
                    "themes", themeRegistry.size(),
                    "errors", errors,
                    "warnings", warnings
            ));
        }
        getLogger().info("BentoMenu reloaded: " + menuRepository.size() + " menus, "
                + themeRegistry.size() + " themes, errors=" + loaded.report().errorCount()
                + ", warnings=" + loaded.report().warningCount());
    }

    private void sendValidation(CommandSender sender, ValidationReport report) {
        int errors = report.errorCount();
        int warnings = report.warningCount();
        reply(sender, "validate.header", Map.of("errors", errors, "warnings", warnings));
        List<ValidationReport.Issue> issues = report.issues();
        if (issues.isEmpty()) {
            reply(sender, "validate.none", Map.of());
            return;
        }
        int limit = Math.min(25, issues.size());
        for (int i = 0; i < limit; i++) {
            ValidationReport.Issue issue = issues.get(i);
            reply(sender, "validate.issue", Map.of(
                    "severity", issue.severity().name(),
                    "file", issue.file() == null ? "" : issue.file(),
                    "path", issue.path() == null ? "" : issue.path(),
                    "message", issue.message() == null ? "" : issue.message()
            ));
        }
        if (issues.size() > limit) {
            reply(sender, "validate.more", Map.of("count", issues.size() - limit));
        }
    }

    private void logReport(ValidationReport report) {
        for (ValidationReport.Issue issue : report.issues()) {
            if (issue.severity() == ValidationReport.Severity.ERROR) {
                getLogger().severe(issue.toString());
            } else if (issue.severity() == ValidationReport.Severity.WARNING) {
                getLogger().warning(issue.toString());
            }
        }
    }

    public void send(CommandSender sender, String key, Map<String, Object> placeholders) {
        if (sender instanceof Player player) {
            messages.send(player, key, placeholders);
            return;
        }
        String raw = messageCatalog == null ? key : messageCatalog.raw(config.defaultLanguage(), key);
        if (raw == null) {
            raw = key;
        }
        if (placeholders != null) {
            for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        RenderedText rendered = textEngine.render(raw, fallbackTheme, text -> text);
        sender.sendMessage(rendered.plain());
    }

    private void reply(CommandSender sender, String key, Map<String, Object> placeholders) {
        if (sender == null) {
            return;
        }
        if (sender instanceof Player player) {
            scheduler.runOnPlayer(player, () -> messages.send(player, key, placeholders));
        } else {
            send(sender, key, placeholders);
        }
    }

    private Theme resolveFallbackTheme() {
        Theme theme = themeRegistry.get(config.defaultTheme()).orElse(null);
        if (theme != null) {
            return theme;
        }
        return themeRegistry.snapshot().stream().findFirst()
                .orElseGet(() -> Theme.builder("aurora", "Aurora").build());
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void shutdown(ExecutorService executor) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public PluginConfig config() {
        return config;
    }

    public MessageCatalog messageCatalog() {
        return messageCatalog;
    }

    public TextEngine textEngine() {
        return textEngine;
    }

    public ThemeRegistry themes() {
        return themeRegistry;
    }

    public LayoutRegistry layouts() {
        return layoutRegistry;
    }

    public MenuRepository menus() {
        return menuRepository;
    }

    public PlayerProfileStore profiles() {
        return profiles;
    }

    public PlatformScheduler scheduler() {
        return scheduler;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public PlaceholderBridge placeholders() {
        return placeholders;
    }

    public MessageBridge messages() {
        return messages;
    }

    public FeedbackBridge feedback() {
        return feedback;
    }

    public ItemRenderer items() {
        return itemRenderer;
    }

    public MenuService menuService() {
        return menuService;
    }

    public Metrics metrics() {
        return metrics;
    }

    public BentoApi api() {
        return api;
    }

    public Theme fallbackTheme() {
        return fallbackTheme;
    }

    public ActionRegistry actionRegistry() {
        return actionRegistry;
    }

    public ConditionRegistry conditionRegistry() {
        return conditionRegistry;
    }

    public DataSourceRegistry dataSourceRegistry() {
        return dataSourceRegistry;
    }

    public InputService inputService() {
        return inputService;
    }
}
