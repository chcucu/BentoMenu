package dev.bento.menu.core.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable localisation catalog. Bundled defaults are merged with user files
 * by the platform loader, so missing keys never show raw key names.
 */
public final class MessageCatalog {

    private final Map<String, Map<String, String>> locales;
    private final String fallbackLocale;

    public MessageCatalog(Map<String, Map<String, String>> locales, String fallbackLocale) {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        locales.forEach((locale, messages) -> copy.put(locale, Map.copyOf(messages)));
        this.locales = Map.copyOf(copy);
        this.fallbackLocale = fallbackLocale;
    }

    public Set<String> locales() {
        return locales.keySet();
    }

    public String raw(String locale, String key) {
        if (key == null) {
            return null;
        }
        String value = lookup(locale, key);
        if (value != null) {
            return value;
        }
        value = lookup(fallbackLocale, key);
        if (value != null) {
            return value;
        }
        value = lookup("zh_CN", key);
        if (value != null) {
            return value;
        }
        return lookup("en_US", key);
    }

    private String lookup(String locale, String key) {
        if (locale == null) {
            return null;
        }
        Map<String, String> messages = locales.get(locale);
        return messages == null ? null : messages.get(key);
    }

    public static Map<String, String> flatten(ConfigurationSection section) {
        Map<String, String> out = new LinkedHashMap<>();
        flatten(section, "", out);
        return out;
    }

    private static void flatten(ConfigurationSection section, String prefix, Map<String, String> out) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object raw = section.get(key);
            if (raw instanceof ConfigurationSection child) {
                flatten(child, path, out);
            } else if (raw instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    out.put(path + "." + i, String.valueOf(list.get(i)));
                }
                out.put(path, String.join("\n", list.stream().map(String::valueOf).toList()));
            } else if (raw != null) {
                out.put(path, String.valueOf(raw));
            }
        }
    }

    public static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "zh_CN";
        }
        String value = locale.replace('-', '_');
        if (value.equalsIgnoreCase("zh")) {
            return "zh_CN";
        }
        if (value.equalsIgnoreCase("en")) {
            return "en_US";
        }
        for (String candidate : List.of("zh_CN", "en_US")) {
            if (candidate.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return value;
    }

    public static List<String> sortedLocales(Set<String> locales) {
        List<String> out = new ArrayList<>(locales);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}
