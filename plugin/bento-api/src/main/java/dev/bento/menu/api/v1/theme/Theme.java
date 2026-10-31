package dev.bento.menu.api.v1.theme;

import dev.bento.menu.api.v1.MenuItem;
import org.bukkit.Material;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable visual theme: palette, title gradient, border material, icons and
 * sounds. Themes may be supplied by YAML or registered through the API.
 */
public final class Theme {

    public enum BorderStyle {
        GRADIENT,
        SOLID,
        STRIPED
    }

    private final String id;
    private final String displayName;
    private final String description;
    private final String permission;
    private final Map<String, String> colors;
    private final List<String> titleGradient;
    private final BorderStyle borderStyle;
    private final String borderMaterial;
    private final Integer borderCustomModelData;
    private final String packBorderMaterial;
    private final Integer packBorderModelData;
    private final Map<String, MenuItem> icons;
    private final Map<String, String> sounds;

    private Theme(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.permission = builder.permission;
        this.colors = Collections.unmodifiableMap(new LinkedHashMap<>(builder.colors));
        this.titleGradient = List.copyOf(builder.titleGradient);
        this.borderStyle = builder.borderStyle;
        this.borderMaterial = builder.borderMaterial;
        this.borderCustomModelData = builder.borderCustomModelData;
        this.packBorderMaterial = builder.packBorderMaterial;
        this.packBorderModelData = builder.packBorderModelData;
        this.icons = Collections.unmodifiableMap(new LinkedHashMap<>(builder.icons));
        this.sounds = Collections.unmodifiableMap(new LinkedHashMap<>(builder.sounds));
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String permission() {
        return permission;
    }

    public Map<String, String> colors() {
        return colors;
    }

    public String color(String key, String fallback) {
        return colors.getOrDefault(key, fallback);
    }

    public List<String> titleGradient() {
        return titleGradient;
    }

    public BorderStyle borderStyle() {
        return borderStyle;
    }

    public String borderMaterial() {
        return borderMaterial;
    }

    public Integer borderCustomModelData() {
        return borderCustomModelData;
    }

    /** Material used for smooth RGB borders when a resource pack is active. */
    public String packBorderMaterial() {
        return packBorderMaterial;
    }

    public Integer packBorderModelData() {
        return packBorderModelData;
    }

    public boolean usesResourcePackBorder() {
        return packBorderMaterial != null && !packBorderMaterial.isBlank();
    }

    public Map<String, MenuItem> icons() {
        return icons;
    }

    public String sound(String key, String fallback) {
        return sounds.getOrDefault(key, fallback);
    }

    public static final class Builder {
        private final String id;
        private final Map<String, String> colors = new LinkedHashMap<>();
        private final Map<String, MenuItem> icons = new LinkedHashMap<>();
        private final Map<String, String> sounds = new LinkedHashMap<>();
        private final java.util.ArrayList<String> titleGradient = new java.util.ArrayList<>();
        private String displayName;
        private String description;
        private String permission;
        private BorderStyle borderStyle = BorderStyle.GRADIENT;
        private String borderMaterial = "GRAY_STAINED_GLASS_PANE";
        private Integer borderCustomModelData;
        private String packBorderMaterial;
        private Integer packBorderModelData;

        private Builder(String id, String displayName) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("theme id must not be blank");
            }
            this.id = id;
            this.displayName = displayName == null ? id : displayName;
            colors.put("primary", "#7AD7FF");
            colors.put("secondary", "#B39DFF");
            colors.put("accent", "#FFD166");
            colors.put("text", "#EAF2FF");
            colors.put("muted", "#8FA3BF");
            colors.put("success", "#5BE49B");
            colors.put("warning", "#FFC857");
            colors.put("error", "#FF6B6B");
            colors.put("background-start", "#0B1220");
            colors.put("background-end", "#1B2A4A");
            colors.put("border-start", "#2A3D66");
            colors.put("border-end", "#5B4B8A");
            titleGradient.add("primary");
            titleGradient.add("secondary");
            // Icons are supplied by themes/managers at runtime; keeping this
            // builder free of ItemStack construction makes the API usable in
            // plain unit tests and on the platform's configuration thread.
            sounds.put("open", "block.chest.open");
            sounds.put("click", "ui.button.click");
            sounds.put("error", "entity.villager.no");
            sounds.put("success", "entity.player.levelup");
            sounds.put("page", "item.book.page_turn");
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder color(String key, String hexOrThemeToken) {
            if (key != null && hexOrThemeToken != null) {
                colors.put(key, hexOrThemeToken);
            }
            return this;
        }

        public Builder colors(Map<String, String> colors) {
            if (colors != null) {
                this.colors.putAll(colors);
            }
            return this;
        }

        public Builder titleGradient(String... tokens) {
            this.titleGradient.clear();
            Collections.addAll(this.titleGradient, tokens);
            return this;
        }

        public Builder titleGradient(List<String> tokens) {
            this.titleGradient.clear();
            if (tokens != null) {
                this.titleGradient.addAll(tokens);
            }
            return this;
        }

        public Builder borderStyle(BorderStyle style) {
            this.borderStyle = style == null ? BorderStyle.GRADIENT : style;
            return this;
        }

        public Builder borderMaterial(String materialName) {
            this.borderMaterial = materialName;
            return this;
        }

        public Builder borderMaterial(Material material) {
            this.borderMaterial = material.name();
            return this;
        }

        public Builder borderCustomModelData(Integer data) {
            this.borderCustomModelData = data;
            return this;
        }

        /**
         * Enables smooth per-slot RGB borders when the optional resource pack
         * is active. The material must exist on the server.
         */
        public Builder resourcePackBorder(String materialName, Integer customModelData) {
            this.packBorderMaterial = materialName;
            this.packBorderModelData = customModelData;
            return this;
        }

        public Builder icon(String key, MenuItem item) {
            if (key != null && item != null) {
                icons.put(key, item);
            }
            return this;
        }

        public Builder icon(String key, Material material) {
            return icon(key, MenuItem.builder(material).build());
        }

        public Builder icon(String key, Material material, Integer customModelData) {
            return icon(key, MenuItem.builder(material).customModelData(customModelData).build());
        }

        public Builder sound(String key, String soundName) {
            if (key != null && soundName != null) {
                sounds.put(key, soundName);
            }
            return this;
        }

        public Theme build() {
            return new Theme(this);
        }
    }
}
