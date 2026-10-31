package dev.bento.menu.bukkit.config;

import java.util.List;

/**
 * Bundled files copied to the data folder when absent. Existing files are
 * never overwritten.
 */
public final class DefaultResources {

    public static final List<String> FILES = List.of(
            "config.yml",
            "messages/zh_CN.yml",
            "messages/en_US.yml",
            "themes/aurora.yml",
            "themes/ember.yml",
            "themes/mono.yml",
            "themes/sakura.yml",
            "layouts/main.yml",
            "layouts/standard.yml",
            "layouts/shop.yml",
            "layouts/profile.yml",
            "layouts/themes.yml",
            "layouts/theme-preview.yml",
            "menus/main.yml",
            "menus/shop.yml",
            "menus/profile.yml",
            "menus/themes.yml",
            "menus/theme-preview.yml"
    );

    private DefaultResources() {
    }
}
