package dev.bento.example;

import dev.bento.menu.api.v1.BentoApi;
import dev.bento.menu.api.v1.BentoApiProvider;
import dev.bento.menu.api.v1.Layout;
import dev.bento.menu.api.v1.MenuEntry;
import dev.bento.menu.api.v1.MenuItem;
import dev.bento.menu.api.v1.action.ActionFactory;
import dev.bento.menu.api.v1.action.ConditionFactory;
import dev.bento.menu.api.v1.data.DataSource;
import dev.bento.menu.api.v1.data.DataSourceFactory;
import dev.bento.menu.api.v1.theme.Theme;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reference addon for BentoMenu's stable v1 API.
 *
 * <p>Demonstrates every extension point:</p>
 * <ul>
 *   <li>registering a programmatic menu</li>
 *   <li>a custom YAML action ({@code broadcast})</li>
 *   <li>a custom YAML condition ({@code level})</li>
 *   <li>a dynamic data source ({@code example-crystals})</li>
 *   <li>a custom theme ({@code example})</li>
 * </ul>
 */
public final class BentoExamplePlugin extends JavaPlugin {

    private BentoApi api;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("BentoMenu") == null) {
            getLogger().severe("BentoMenu is required");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            api = BentoApiProvider.get();
        } catch (IllegalStateException exception) {
            getLogger().severe("BentoMenu API is not available: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        api.registerAction("broadcast", (ActionFactory) config -> context -> {
            String text = config.getString("text", "Hello from an addon");
            String resolved = context.messages().render(context.player(), text);
            context.player().getServer().broadcastMessage(resolved);
        });

        api.registerCondition("level", (ConditionFactory) config -> {
            int minimum = config.getInt("min", 0);
            int maximum = config.getInt("max", Integer.MAX_VALUE);
            return context -> {
                int level = context.player().getLevel();
                return level >= minimum && level <= maximum;
            };
        });

        api.registerDataSource("example-crystals", (DataSourceFactory) config -> {
            int count = Math.max(1, config.getInt("count", 9));
            return (DataSource) context -> {
                List<MenuEntry> entries = new ArrayList<>();
                for (int i = 1; i <= count; i++) {
                    int amount = ThreadLocalRandom.current().nextInt(1, 5);
                    entries.add(MenuEntry.builder("crystal-" + i,
                                    MenuItem.builder(Material.AMETHYST_SHARD)
                                            .amount(amount)
                                            .name("<color:primary>Crystal #" + i + "</color>")
                                            .lore("<muted>Live data source</muted>",
                                                    "<muted>Click count:</muted> <color:text>{price}</color>")
                                            .build())
                            .category(i % 2 == 0 ? "even" : "odd")
                            .metadata("price", i * 10)
                            .build());
                }
                return entries;
            };
        });

        api.registerTheme(Theme.builder("example", "<gradient:primary:secondary>Example</gradient>")
                .description("Registered by the example addon")
                .color("primary", "#8AE6C1")
                .color("secondary", "#5AA9FF")
                .borderStyle(Theme.BorderStyle.STRIPED)
                .borderMaterial(Material.CYAN_STAINED_GLASS_PANE)
                .icon("search", Material.COMPASS)
                .icon("previous", Material.ARROW)
                .icon("next", Material.ARROW)
                .icon("page", Material.PAPER)
                .icon("category", Material.NAME_TAG)
                .icon("close", Material.BARRIER)
                .build());

        Layout layout = Layout.builder(4)
                .row("#########")
                .row("#CCCCCCC#")
                .row("#CCCCCCC#")
                .row("#<#P#>#X#")
                .region('#', Layout.RegionType.BORDER)
                .region('C', Layout.RegionType.CONTENT)
                .region('<', Layout.RegionType.PREVIOUS)
                .region('P', Layout.RegionType.PAGE)
                .region('>', Layout.RegionType.NEXT)
                .region('X', Layout.RegionType.CLOSE)
                .build();

        api.registerMenu(api.menuBuilder("example-addon")
                .title("<gradient:primary:secondary>Addon Demo</gradient>")
                .rows(4)
                .layout(layout)
                .dataSource(context -> context.menuId() == null
                        ? List.of()
                        : List.of(
                        MenuEntry.builder("hello", MenuItem.builder(Material.AMETHYST_CLUSTER)
                                .name("<color:primary>Hello, {player}</color>")
                                .lore("<muted>Registered programmatically</muted>")
                                .cooldown(Duration.ofSeconds(1))
                                .build()).build(),
                        MenuEntry.builder("custom-action", MenuItem.builder(Material.BELL)
                                .name("<color:accent>Custom action</color>")
                                .lore("<muted>Runs the addon action</muted>")
                                .build()).build(),
                        MenuEntry.builder("custom-condition", MenuItem.builder(Material.EXPERIENCE_BOTTLE)
                                .name("<color:success>Level condition</color>")
                                .lore("<muted>Requires level 0–100</muted>")
                                .build()).build()))
                .pageSize(21)
                .sortMode(dev.bento.menu.api.v1.SortMode.ID)
                .refreshInterval(Duration.ofSeconds(5))
                .build());

        getLogger().info("BentoMenu example addon registered action, condition, data source, theme and menu.");
    }

    @Override
    public void onDisable() {
        if (api != null) {
            api.unregisterMenu("example-addon");
        }
    }
}
