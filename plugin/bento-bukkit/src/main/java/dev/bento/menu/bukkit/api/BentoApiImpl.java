package dev.bento.menu.bukkit.api;

import dev.bento.menu.api.v1.BentoApi;
import dev.bento.menu.api.v1.MenuBuilder;
import dev.bento.menu.api.v1.MenuDefinition;
import dev.bento.menu.api.v1.MenuOpenOptions;
import dev.bento.menu.api.v1.action.ActionFactory;
import dev.bento.menu.api.v1.action.ConditionFactory;
import dev.bento.menu.api.v1.data.DataSourceFactory;
import dev.bento.menu.api.v1.event.BentoMenuCloseEvent;
import dev.bento.menu.api.v1.exception.BentoException;
import dev.bento.menu.api.v1.service.Scheduler;
import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.bukkit.data.PlayerProfileStore;
import dev.bento.menu.bukkit.menu.MenuService;
import dev.bento.menu.bukkit.scheduler.PlatformScheduler;
import dev.bento.menu.core.action.ActionRegistry;
import dev.bento.menu.core.condition.ConditionRegistry;
import dev.bento.menu.core.data.DataSourceRegistry;
import dev.bento.menu.core.menu.CompiledMenu;
import dev.bento.menu.core.menu.LayoutRegistry;
import dev.bento.menu.core.menu.MenuBuilderImpl;
import dev.bento.menu.core.menu.MenuRepository;
import dev.bento.menu.core.report.ValidationReport;
import dev.bento.menu.core.theme.ThemeRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Public API implementation. Registration methods are thread-safe; player
 * operations are marshalled to the player's owning thread by MenuService.
 */
public final class BentoApiImpl implements BentoApi {

    private final Plugin plugin;
    private final MenuRepository menus;
    private final LayoutRegistry layouts;
    private final ThemeRegistry themes;
    private final ActionRegistry actions;
    private final ConditionRegistry conditions;
    private final DataSourceRegistry dataSources;
    private final PlayerProfileStore profiles;
    private final MenuService menuService;
    private final PlatformScheduler scheduler;

    public BentoApiImpl(Plugin plugin, MenuRepository menus, LayoutRegistry layouts,
                        ThemeRegistry themes, ActionRegistry actions, ConditionRegistry conditions,
                        DataSourceRegistry dataSources, PlayerProfileStore profiles,
                        MenuService menuService, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.menus = menus;
        this.layouts = layouts;
        this.themes = themes;
        this.actions = actions;
        this.conditions = conditions;
        this.dataSources = dataSources;
        this.profiles = profiles;
        this.menuService = menuService;
        this.scheduler = scheduler;
    }

    @Override
    public String apiVersion() {
        return "1.0";
    }

    @Override
    public MenuBuilder menuBuilder(String id) {
        return new MenuBuilderImpl(id);
    }

    @Override
    public void registerMenu(MenuDefinition definition) {
        if (definition == null) {
            throw new BentoException("Menu definition must not be null");
        }
        ValidationReport report = new ValidationReport();
        CompiledMenu compiled = CompiledMenu.compile(definition,
                layouts.resolve(definition), null, null, report, "API", dataSources);
        if (compiled == null) {
            throw new BentoException("Invalid menu " + definition.id() + ": " + report.issues());
        }
        menus.putApi(compiled);
        menuService.onMenusReload(menus.map());
    }

    @Override
    public boolean unregisterMenu(String id) {
        Optional<CompiledMenu> removed = menus.removeApi(id);
        if (removed.isEmpty()) {
            return false;
        }
        menuService.onMenusReload(menus.map());
        return true;
    }

    @Override
    public Optional<MenuDefinition> menu(String id) {
        return menus.get(id).map(CompiledMenu::definition);
    }

    @Override
    public Collection<MenuDefinition> menus() {
        return menus.snapshot().stream().map(CompiledMenu::definition).toList();
    }

    @Override
    public void open(Player player, String menuId) {
        menuService.open(player, menuId, MenuOpenOptions.builder().build());
    }

    @Override
    public void open(Player player, String menuId, MenuOpenOptions options) {
        menuService.open(player, menuId, options == null ? MenuOpenOptions.builder().build() : options);
    }

    @Override
    public void close(Player player) {
        menuService.close(player, BentoMenuCloseEvent.CloseReason.PLUGIN, true);
    }

    @Override
    public boolean isOpen(Player player) {
        return menuService.isOpen(player);
    }

    @Override
    public Optional<String> openMenu(Player player) {
        return menuService.openMenu(player);
    }

    @Override
    public int openPage(Player player) {
        var session = menuService.session(player);
        return session == null ? 0 : session.page();
    }

    @Override
    public int openPages(Player player) {
        var session = menuService.session(player);
        return session == null ? 0 : session.pages();
    }

    @Override
    public void refresh(Player player) {
        menuService.refresh(player);
    }

    @Override
    public void refreshViewers(String menuId) {
        menuService.refreshViewers(menuId);
    }

    @Override
    public void refreshAll() {
        menuService.refreshAll();
    }

    @Override
    public void registerAction(String id, ActionFactory factory) {
        actions.register(id, factory);
    }

    @Override
    public void registerCondition(String id, ConditionFactory factory) {
        conditions.register(id, factory);
    }

    @Override
    public void registerDataSource(String id, DataSourceFactory factory) {
        dataSources.register(id, factory);
    }

    @Override
    public void registerTheme(Theme theme) {
        if (theme == null) {
            throw new BentoException("Theme must not be null");
        }
        themes.putApi(theme);
        menuService.onThemesReload();
    }

    @Override
    public Collection<Theme> themes() {
        return List.copyOf(themes.snapshot());
    }

    @Override
    public Optional<Theme> theme(String id) {
        return themes.get(id);
    }

    @Override
    public void setTheme(Player player, String themeId) {
        Theme theme = themes.get(themeId).orElse(null);
        if (theme == null) {
            return;
        }
        String permission = theme.permission();
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            return;
        }
        profiles.setTheme(player, themeId);
        menuService.applyTheme(player);
    }

    @Override
    public String theme(Player player) {
        return profiles.themeIdFor(player);
    }

    @Override
    public void setLanguage(Player player, String languageCode) {
        if (!profiles.setLanguage(player, languageCode)) {
            throw new BentoException("Unsupported language: " + languageCode);
        }
    }

    @Override
    public String language(Player player) {
        return profiles.languageFor(player);
    }

    @Override
    public Scheduler scheduler(Player player) {
        if (player == null) {
            throw new BentoException("Player must not be null");
        }
        return scheduler.forPlayer(player);
    }
}
