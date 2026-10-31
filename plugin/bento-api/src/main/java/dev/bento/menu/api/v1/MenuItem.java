package dev.bento.menu.api.v1;

import dev.bento.menu.api.v1.action.Action;
import dev.bento.menu.api.v1.action.Condition;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable item blueprint used by static slots, layout components and entries.
 * Display name/lore strings are templates rendered per viewer; they support
 * legacy colours, hex colours, gradients, theme tokens and placeholders.
 */
public final class MenuItem {

    private final ItemStack base;
    private final String name;
    private final List<String> lore;
    private final int amount;
    private final boolean glow;
    private final Integer customModelData;
    private final String headOwner;
    private final String leatherColor;
    private final Map<String, Integer> enchantments;
    private final Set<String> itemFlags;
    private final boolean unbreakable;
    private final List<Action> actions;
    private final List<Condition> conditions;
    private final Duration cooldown;
    private final String cooldownMessage;
    private final Set<ClickType> clickTypes;
    private final String permission;
    private final Feedback feedback;

    private MenuItem(Builder builder) {
        this.base = builder.base.clone();
        this.name = builder.name;
        this.lore = List.copyOf(builder.lore);
        this.amount = builder.amount;
        this.glow = builder.glow;
        this.customModelData = builder.customModelData;
        this.headOwner = builder.headOwner;
        this.leatherColor = builder.leatherColor;
        this.enchantments = Collections.unmodifiableMap(new LinkedHashMap<>(builder.enchantments));
        this.itemFlags = Collections.unmodifiableSet(new LinkedHashSet<>(builder.itemFlags));
        this.unbreakable = builder.unbreakable;
        this.actions = List.copyOf(builder.actions);
        this.conditions = List.copyOf(builder.conditions);
        this.cooldown = builder.cooldown;
        this.cooldownMessage = builder.cooldownMessage;
        this.clickTypes = Collections.unmodifiableSet(EnumSet.copyOf(builder.clickTypes));
        this.permission = builder.permission;
        this.feedback = builder.feedback;
    }

    public static Builder builder(Material material) {
        return new Builder(new ItemStack(material));
    }

    public static Builder from(ItemStack item) {
        return new Builder(item);
    }

    public ItemStack base() {
        return base.clone();
    }

    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    public int amount() {
        return amount;
    }

    public boolean glow() {
        return glow;
    }

    public Integer customModelData() {
        return customModelData;
    }

    public String headOwner() {
        return headOwner;
    }

    public String leatherColor() {
        return leatherColor;
    }

    public Map<String, Integer> enchantments() {
        return enchantments;
    }

    public Set<String> itemFlags() {
        return itemFlags;
    }

    public boolean unbreakable() {
        return unbreakable;
    }

    public List<Action> actions() {
        return actions;
    }

    public List<Condition> conditions() {
        return conditions;
    }

    public Duration cooldown() {
        return cooldown;
    }

    public String cooldownMessage() {
        return cooldownMessage;
    }

    public Set<ClickType> clickTypes() {
        return clickTypes;
    }

    public String permission() {
        return permission;
    }

    public Feedback feedback() {
        return feedback;
    }

    public static final class Builder {
        private final ItemStack base;
        private final List<String> lore = new ArrayList<>();
        private final List<Action> actions = new ArrayList<>();
        private final List<Condition> conditions = new ArrayList<>();
        private final Set<ClickType> clickTypes = EnumSet.of(
                ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);
        private final Map<String, Integer> enchantments = new LinkedHashMap<>();
        private final Set<String> itemFlags = new LinkedHashSet<>();
        private String name;
        private int amount;
        private boolean glow;
        private Integer customModelData;
        private String headOwner;
        private String leatherColor;
        private boolean unbreakable;
        private Duration cooldown;
        private String cooldownMessage;
        private String permission;
        private Feedback feedback;

        private Builder(ItemStack base) {
            if (base == null || base.getType() == Material.AIR) {
                throw new IllegalArgumentException("base item must not be AIR");
            }
            this.base = base.clone();
            this.amount = Math.max(1, Math.min(64, base.getAmount()));
        }

        public Builder amount(int amount) {
            this.amount = Math.max(1, Math.min(64, amount));
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder lore(String... lore) {
            return lore(Arrays.asList(lore));
        }

        public Builder lore(Collection<String> lore) {
            this.lore.clear();
            if (lore != null) {
                this.lore.addAll(lore);
            }
            return this;
        }

        public Builder addLore(String line) {
            this.lore.add(line);
            return this;
        }

        public Builder glow(boolean glow) {
            this.glow = glow;
            return this;
        }

        public Builder customModelData(Integer data) {
            this.customModelData = data;
            return this;
        }

        public Builder headOwner(String playerNameOrPlaceholder) {
            this.headOwner = playerNameOrPlaceholder;
            return this;
        }

        public Builder leatherColor(String hexColor) {
            this.leatherColor = hexColor;
            return this;
        }

        public Builder enchantment(String key, int level) {
            this.enchantments.put(key, level);
            return this;
        }

        public Builder itemFlags(String... flags) {
            this.itemFlags.addAll(Arrays.asList(flags));
            return this;
        }

        public Builder unbreakable(boolean unbreakable) {
            this.unbreakable = unbreakable;
            return this;
        }

        public Builder action(Action action) {
            if (action != null) {
                this.actions.add(action);
            }
            return this;
        }

        public Builder actions(Collection<Action> actions) {
            this.actions.clear();
            if (actions != null) {
                this.actions.addAll(actions);
            }
            return this;
        }

        public Builder condition(Condition condition) {
            if (condition != null) {
                this.conditions.add(condition);
            }
            return this;
        }

        public Builder conditions(Collection<Condition> conditions) {
            this.conditions.clear();
            if (conditions != null) {
                this.conditions.addAll(conditions);
            }
            return this;
        }

        public Builder cooldown(Duration cooldown) {
            this.cooldown = cooldown;
            return this;
        }

        public Builder cooldownMessage(String message) {
            this.cooldownMessage = message;
            return this;
        }

        public Builder clickTypes(ClickType... types) {
            this.clickTypes.clear();
            this.clickTypes.addAll(Arrays.asList(types));
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder feedback(Feedback feedback) {
            this.feedback = feedback;
            return this;
        }

        public MenuItem build() {
            return new MenuItem(this);
        }
    }
}
