package dev.bento.menu.bukkit.render;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Enchantment lookup that survives the enum → registry refactor in newer
 * versions without compile-time references to removed constants.
 */
public final class CompatEnchantments {

    private CompatEnchantments() {
    }

    public static Enchantment get(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String name = rawName.trim().toLowerCase(Locale.ROOT)
                .replace("minecraft:", "").replace(' ', '_');
        NamespacedKey key = NamespacedKey.minecraft(name);
        Enchantment direct = byKey(key);
        if (direct != null) {
            return direct;
        }
        for (String field : List.of(name.toUpperCase(Locale.ROOT), "UNBREAKING", "DURABILITY", "MENDING")) {
            try {
                Field candidate = Enchantment.class.getField(field);
                Object value = candidate.get(null);
                if (value instanceof Enchantment enchantment) {
                    return enchantment;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next fallback.
            }
        }
        return null;
    }

    public static Enchantment glowEnchantment() {
        for (String name : List.of("unbreaking", "durability", "mending")) {
            Enchantment enchantment = get(name);
            if (enchantment != null) {
                return enchantment;
            }
        }
        return null;
    }

    private static Enchantment byKey(NamespacedKey key) {
        try {
            Method method = Enchantment.class.getMethod("getByKey", NamespacedKey.class);
            Object value = method.invoke(null, key);
            if (value instanceof Enchantment enchantment) {
                return enchantment;
            }
        } catch (ReflectiveOperationException ignored) {
            // Older/newer API shape; try Registry below.
        }
        for (String registryField : List.of("ENCHANTMENT", "ENCHANTMENTS")) {
            try {
                Class<?> registryClass = Class.forName("org.bukkit.Registry");
                Field field = registryClass.getField(registryField);
                Object registry = field.get(null);
                Method get = registry.getClass().getMethod("get", NamespacedKey.class);
                Object value = get.invoke(registry, key);
                if (value instanceof Enchantment enchantment) {
                    return enchantment;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next option.
            }
        }
        return null;
    }
}
