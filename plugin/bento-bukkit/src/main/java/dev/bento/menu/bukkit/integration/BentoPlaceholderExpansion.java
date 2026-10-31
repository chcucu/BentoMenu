package dev.bento.menu.bukkit.integration;

import dev.bento.menu.api.v1.BentoApi;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Optional PlaceholderAPI expansion exposing BentoMenu state as
 * {@code %bento_theme%}, {@code %bento_menu%}, {@code %bento_page%},
 * {@code %bento_pages%}, {@code %bento_language%} and
 * {@code %bento_sessions%}.
 */
public final class BentoPlaceholderExpansion extends PlaceholderExpansion {

    private final BentoApi api;
    private final String version;
    private final java.util.function.IntSupplier sessions;

    public BentoPlaceholderExpansion(BentoApi api, String version,
                                     java.util.function.IntSupplier sessions) {
        this.api = api;
        this.version = version;
        this.sessions = sessions;
    }

    @Override
    public String getIdentifier() {
        return "bento";
    }

    @Override
    public String getAuthor() {
        return "BentoMenu";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return null;
        }
        if (params.equalsIgnoreCase("sessions")) {
            return String.valueOf(sessions.getAsInt());
        }
        Player online = player.getPlayer();
        if (online == null) {
            return null;
        }
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "theme" -> api.theme(online);
            case "language" -> api.language(online);
            case "menu" -> api.openMenu(online).orElse("");
            case "page" -> String.valueOf(api.openPage(online));
            case "pages" -> String.valueOf(api.openPages(online));
            default -> null;
        };
    }
}
