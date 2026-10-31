package dev.bento.menu.api.v1;

import dev.bento.menu.api.v1.action.ActionFactory;
import dev.bento.menu.api.v1.action.ConditionFactory;
import dev.bento.menu.api.v1.data.DataSourceFactory;
import dev.bento.menu.api.v1.service.Scheduler;
import dev.bento.menu.api.v1.theme.Theme;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

/**
 * Stable BentoMenu service API (v1).
 *
 * <p>Obtain an instance through {@link BentoApiProvider#get()} or Bukkit's
 * {@code ServicesManager}. Registration methods are thread-safe and may be
 * called during another plugin's {@code onEnable}. Methods that target a
 * player (open, close, refresh, theme, language) marshal themselves to that
 * player's owning thread, so they are safe to call from any thread.</p>
 *
 * <p>ABI policy: within major version 1, existing methods and types are never
 * removed or changed incompatibly. New functionality is added through new
 * default methods or new types.</p>
 */
public interface BentoApi {

    /** API major/minor version, for example {@code 1.0}. */
    String apiVersion();

    /** Creates a fluent builder for a programmatic menu definition. */
    MenuBuilder menuBuilder(String id);

    /** Registers or replaces a programmatically built menu. */
    void registerMenu(MenuDefinition definition);

    /** Removes a menu by id. Open views are closed on the owner threads. */
    boolean unregisterMenu(String id);

    Optional<MenuDefinition> menu(String id);

    Collection<MenuDefinition> menus();

    /** Opens a menu for a player, using their saved theme and page 1. */
    void open(Player player, String menuId);

    /** Opens a menu with explicit initial state. */
    void open(Player player, String menuId, MenuOpenOptions options);

    void close(Player player);

    boolean isOpen(Player player);

    Optional<String> openMenu(Player player);

    /** Current page of the open menu, or 0 when closed. */
    default int openPage(Player player) {
        return 0;
    }

    /** Page count of the open menu, or 0 when closed. */
    default int openPages(Player player) {
        return 0;
    }

    /** Requests a coalesced re-render of the player's open menu. */
    void refresh(Player player);

    /** Refreshes all viewers of a menu, on each viewer's owning thread. */
    void refreshViewers(String menuId);

    void refreshAll();

    void registerAction(String id, ActionFactory factory);

    void registerCondition(String id, ConditionFactory factory);

    void registerDataSource(String id, DataSourceFactory factory);

    void registerTheme(Theme theme);

    Collection<Theme> themes();

    Optional<Theme> theme(String id);

    void setTheme(Player player, String themeId);

    /** Returns the effective theme id (saved preference or global default). */
    String theme(Player player);

    void setLanguage(Player player, String languageCode);

    String language(Player player);

    /** Scheduler bound to the player's owning thread/region. */
    Scheduler scheduler(Player player);
}
