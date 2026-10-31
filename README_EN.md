# BentoMenu

A container-menu framework for Minecraft servers. Menus live in YAML, so server owners don't need to write code. When you need more than YAML can give, plugin developers can hook in through the Java API or the Kotlin DSL.

Runs on Spigot, Paper and Folia, from Minecraft 1.18.2 up to 26.x. No NMS, no client mods, no ProtocolLib.

[中文](README.md)

![version](https://img.shields.io/badge/version-1.0.0-informational)
![license](https://img.shields.io/badge/license-MIT-blue)
![minecraft](https://img.shields.io/badge/Minecraft-1.18.2%20–%2026.x-success)
![java](https://img.shields.io/badge/Java-17%2B-orange)

## What you get

- **Menus**: chest GUIs with 1–6 rows. Layouts (`layouts/`) are separate from content (`menus/`), and one layout can be reused by several menus.
- **Themes**: four built in — aurora, ember, mono and sakura. Players can switch at runtime. A theme covers colors, borders, icons and sounds.
- **Content**: entries can be static YAML or supplied at runtime by other plugins. Pagination, search, category filtering and scheduled refresh are built in.
- **Actions**: a click can run several actions in order — player command, console command, withdraw/deposit, give/take items, open/close a menu, change page, message/title/sound, switch theme. Actions can be conditional.
- **Conditions**: permission, balance, held items, server time, random chance, plus your own.
- **Cooldowns**: per button, so people can't spam-click their way through an action chain.
- **Text**: gradient and color tags, plus placeholders. Every player-facing string lives in `messages/` and can be replaced wholesale.
- **Resource pack**: optional. With it you get real gradient borders and custom icons; without it the borders fall back to stained glass and the UI still works.

## Requirements

| | |
|---|---|
| Java | 17 / 21 / 25 (match your server) |
| Server | Spigot, Paper, Folia; hybrid servers are best-effort |
| Required | Vault (VaultUnlocked on Folia) |
| Required | PlaceholderAPI |
| Economy | Any plugin that registers a Vault `Economy` service |

Vault and PlaceholderAPI are hard dependencies in `plugin.yml`. The plugin refuses to enable without them. The reasoning is simple: a menu that opens but silently fails to charge the player is worse than a plugin that complains at startup. Details are in [DEPENDENCIES.md](docs/DEPENDENCIES.md).

## Installation

1. Stop the server. Install Vault (or VaultUnlocked on Folia) and an economy plugin.
2. Install PlaceholderAPI.
3. Drop `BentoMenu-1.0.0.jar` into `plugins/`.
4. Start the server. You should see:

   ```
   [BentoMenu] BentoMenu enabled: 5 menus, 4 themes, Folia=false
   ```

5. Run `/bento` in game. The default menu should open.

On first start the plugin creates:

```
config.yml
messages/   zh_CN.yml  en_US.yml
themes/     aurora.yml  ember.yml  mono.yml  sakura.yml
layouts/    main.yml  shop.yml  profile.yml  themes.yml  theme-preview.yml  standard.yml
menus/      main.yml  shop.yml  profile.yml  themes.yml  theme-preview.yml
players/    per-player theme and language data (created at runtime)
```

Keep the whole `plugins/BentoMenu/` folder when upgrading — new default files never overwrite files you edited.

After editing YAML, run `/bento reload`. No restart needed.

## Commands

Main command `/bento`, alias `/bm`.

| Command | What it does | Permission |
|---|---|---|
| `/bento` | Open the default menu | `bento.command.use` |
| `/bento open <menu> [player]` | Open a menu, optionally for another player | `bento.command.open` / `.open.other` |
| `/bento list` | List the menus you can access | `bento.command.list` |
| `/bento theme [theme\|list]` | Open the theme menu, list themes, or switch | `bento.command.theme` |
| `/bento language [language]` | Show or switch language | `bento.command.language` |
| `/bento close` | Close the current menu | `bento.command.close` |
| `/bento info` | Version, server, economy, PAPI and resource-pack status | `bento.command.info` |
| `/bento reload` | Async reload of all YAML | `bento.command.reload` |
| `/bento validate` | Validate without applying; reports file, path and severity | `bento.command.validate` |
| `/bento stats` | Render/click timings and item-update counters | `bento.command.stats` |

`bento.bypass.cooldown` skips button cooldowns, and `bento.admin` bundles the administrative permissions. Menus and themes can also carry their own `permission` in YAML.

## Configuration

The frequently touched parts of `config.yml`:

```yaml
language:
  default: zh_CN          # zh_CN / en_US built in; add more in messages/
  allow-player-switch: true

theme:
  default: aurora

menu:
  default: main           # what /bento opens

ui:
  resource-pack: false    # set true after you ship a resource pack
  click-debounce-ms: 120
  click-sound: true

performance:
  data-threads: 2
  search-threads: 1
  placeholder-cache-ttl: 3s

economy:
  format: "$%.2f"

search:
  input: auto             # auto / anvil / chat

auto-reload:
  enabled: false
  debounce: 2s
```

## Writing a menu

The layout decides the shape, the menu decides the content. A small `layouts/main.yml`:

```yaml
rows: 3
pattern:
  - '#########'
  - '#..C.C..#'
  - '#########'
regions:
  '#': border
  'C': content
```

And `menus/main.yml` only lists items and actions:

```yaml
id: main
title: "<gradient:primary:secondary>BentoMenu</gradient> <muted>Main menu</muted>"
layout: main
items:
  shop:
    slot: 11
    material: EMERALD
    name: "<gradient:primary:secondary>Shop</gradient>"
    lore:
      - "<muted>Browse items by category</muted>"
    actions:
      - type: open-menu
        menu: shop
    feedback:
      sound: ui.button.click
  close:
    slot: 22
    material: BARRIER
    name: "<color:error>Close</color>"
    actions:
      - type: close-menu
```

When there is more content than fits, put entries under `entries` with `page-size` and pagination takes care of itself. Search accepts both anvil and chat input; `search.input: auto` prefers the anvil. The full field reference is in [CONFIGURATION.md](docs/CONFIGURATION.md).

## Developer API

The stable package is `dev.bento.menu.api.v1`, entry point `BentoApiProvider.get()`. The Kotlin DSL and Kotlin stdlib are already shaded into the jar, so consuming plugins don't need to add them.

```java
BentoApi api = BentoApiProvider.get();

MenuDefinition menu = api.menuBuilder("my-menu")
        .title("<gradient:primary:secondary>My menu</gradient>")
        .rows(3)
        .layout(Layout.builder(3)
                .row("#########")
                .row("#CCCCCCC#")
                .row("#########")
                .region('#', Layout.RegionType.BORDER)
                .region('C', Layout.RegionType.CONTENT)
                .build())
        .staticItem(4, MenuItem.builder(Material.SUNFLOWER)
                .name("<color:accent>Balance: {balance}</color>")
                .build())
        .pageSize(7)
        .refreshInterval(Duration.ofSeconds(5))
        .build();

api.registerMenu(menu);
```

You can register custom actions, conditions, data sources and themes, and listen to the plugin's open/close events. The threading model (including Folia region scheduling) is described in [API.md](docs/API.md), and `plugin/bento-example/` is a runnable example.

## Compatibility

| Server | Version | Java | Status |
|---|---|---|---|
| Spigot | 1.18.2 | 17 | Passing |
| Paper | 1.18.2 | 17 | Passing (200-bot load test) |
| Paper | 1.21.11 | 21 | Passing |
| Paper | 26.1.2 | 25 | Passing |
| Paper | 26.3 | 25 | Starts and enables |
| Folia | 1.21.8 | 21 | Passing |
| Folia | 26.3 | 25 | Starts and enables |
| Hybrid (Mohist, Arclight, …) | — | — | Best effort, untested |

The compile baseline is Spigot 1.18.2 with runtime capability detection. Only public Bukkit API is used: no NMS, no ProtocolLib, no packet manipulation. More in [COMPATIBILITY.md](docs/COMPATIBILITY.md).

## Performance

Measured on a 4-core / 8 GB box running Paper 1.18.2 with 200 mineflayer bots (clients on the same machine, so the numbers are conservative):

- 200 players with menus open, TPS steady at 20.00, no sustained tick drop.
- 2,225 random clicks in 30 seconds (~74/s), TPS 19.25–20.00.
- Average click handling 0.061 ms, worst 3.23 ms.
- Average render 4.00 ms, worst 64.94 ms (first render / JIT warm-up).
- 5,632 renders produced only 38,680 item updates — 6.9 slots per render on average, no full-inventory resends.
- With 200 ms injected latency, click-to-sound feedback was 217–220 ms, 5 out of 5 clicks answered.

Method and known edges are in [PERFORMANCE.md](docs/PERFORMANCE.md).

## Building from source

JDK 17+ and Maven 3.9+.

```bash
cd plugin
mvn clean package
```

Artifacts:

- `plugin/bento-bukkit/target/BentoMenu-1.0.0.jar`
- `plugin/bento-example/target/BentoMenu-Example-1.0.0.jar`

`bento-example` is an optional extension demo; the main plugin works without it.

## Documentation

| Document | Contents |
|---|---|
| [Operation manual](docs/OPERATION_MANUAL.md) | Install, commands, YAML, actions, conditions, themes, resource pack, troubleshooting |
| [Manual test checklist](docs/MANUAL_TEST_CHECKLIST.md) | Pre-release acceptance checklist |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, core classes, data flow, cross-version strategy |
| [CONFIGURATION.md](docs/CONFIGURATION.md) | Commands, config, layouts, themes, menus, actions, conditions, text syntax |
| [API.md](docs/API.md) | Java API, Kotlin DSL, extension points, events |
| [COMPATIBILITY.md](docs/COMPATIBILITY.md) | Server/version matrix |
| [DEPENDENCIES.md](docs/DEPENDENCIES.md) | Why Vault and PlaceholderAPI are hard requirements |
| [PERFORMANCE.md](docs/PERFORMANCE.md) | Load and latency data |
| [TEST_REPORT.md](docs/TEST_REPORT.md) | Test matrix, defects and fixes |
| [RESOURCE_PACK.md](docs/RESOURCE_PACK.md) | Optional resource pack setup and model conventions |

## Known limitations

- Hybrid servers (Mohist, Arclight, …) were not set up for testing. They should work, but Folia region scheduling and some Paper events may behave differently.
- Paper/Folia 26.3 was only checked for startup and enabling; mineflayer has no matching protocol yet, so no bot-level interaction test.
- The resource pack has to be delivered by the server owner (`resource-pack` in `server.properties` or through the proxy). The plugin only supplies model data.
- 1.18 clients don't know some newer sound and material names; those fall back to defaults without breaking anything.
- On Folia, edge cases such as cross-region teleports and portals were not individually load-tested.

## License

MIT, see [LICENSE](LICENSE).

Downloads are on the [Releases](https://github.com/chcucu/BentoMenu/releases) page.
