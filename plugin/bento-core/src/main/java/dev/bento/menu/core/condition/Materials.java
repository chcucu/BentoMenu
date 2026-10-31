package dev.bento.menu.core.condition;

import org.bukkit.Material;

/**
 * Material parsing by name, tolerant of namespaced keys and future additions.
 */
public final class Materials {

    private Materials() {
    }

    public static Material match(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String value = name.trim();
        if (value.contains(":")) {
            value = value.substring(value.indexOf(':') + 1);
        }
        Material material = Material.matchMaterial(value);
        if (material == null) {
            material = Material.matchMaterial(value.toUpperCase(java.util.Locale.ROOT));
        }
        return material;
    }
}
