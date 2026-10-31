# BentoMenu

一个给 Minecraft 服务器用的容器菜单框架。菜单写在 YAML 里，服主不用写代码；需要更复杂的东西时，插件开发者可以用 Java API 或 Kotlin DSL 接进来。

支持 Spigot / Paper / Folia，Minecraft 1.18.2 到 26.x。不碰 NMS，不用客户端模组，也不依赖 ProtocolLib。

[English](README_EN.md)

![version](https://img.shields.io/badge/version-1.0.0-informational)
![license](https://img.shields.io/badge/license-MIT-blue)
![minecraft](https://img.shields.io/badge/Minecraft-1.18.2%20–%2026.x-success)
![java](https://img.shields.io/badge/Java-17%2B-orange)

## 它能做什么

- **菜单**：箱子界面，1–6 行。布局（`layouts/`）和内容（`menus/`）分开，一个布局可以被多个菜单复用。
- **主题**：内置 aurora、ember、mono、sakura 四套，玩家可以自己切换。主题管颜色、边框、图标和音效。
- **内容**：条目可以写在 YAML 里，也可以由其他插件通过 API 动态提供。自带分页、搜索、分类筛选和定时刷新。
- **动作**：一次点击可以按顺序执行多个动作——玩家命令、控制台命令、扣款/加款、给物品/收物品、打开/关闭菜单、翻页、消息/标题/音效、切换主题。动作可以带条件。
- **条件**：权限、余额、持有物品、服务器时间、随机概率，也可由插件注册自定义条件。
- **冷却**：按钮级别，防止连点刷动作。
- **文本**：内置渐变、颜色标签和占位符语法，所有玩家可见文本都在 `messages/` 里，可整份替换。
- **资源包**：可选。装了有真正的渐变边框和自定义图标；不装就用染色玻璃，界面照样能用。

## 环境要求

| 项目 | 要求 |
|---|---|
| Java | 17 / 21 / 25（跟随服务端） |
| 服务端 | Spigot、Paper、Folia；混合端尽力兼容 |
| 必装插件 | Vault（Folia 用 VaultUnlocked） |
| 必装插件 | PlaceholderAPI |
| 经济 | 任意注册了 Vault `Economy` 服务的经济插件 |

Vault 和 PlaceholderAPI 在 `plugin.yml` 里是硬依赖，少装任何一个插件都不会启用。这么做的原因很实在：与其让菜单能打开、购买却悄悄失败，不如启动时就直接报错。更完整的说明见 [DEPENDENCIES.md](docs/DEPENDENCIES.md)。

## 安装

1. 关闭服务端，装好 Vault（Folia 用 VaultUnlocked）和经济插件。
2. 装好 PlaceholderAPI。
3. 把 `BentoMenu-1.0.0.jar` 放进 `plugins/`。
4. 启动服务端，控制台应出现：

   ```
   [BentoMenu] BentoMenu enabled: 5 menus, 4 themes, Folia=false
   ```

5. 进游戏执行 `/bento`，应该能看到主菜单。

首次启动会在 `plugins/BentoMenu/` 生成：

```
config.yml
messages/   zh_CN.yml  en_US.yml
themes/     aurora.yml  ember.yml  mono.yml  sakura.yml
layouts/    main.yml  shop.yml  profile.yml  themes.yml  theme-preview.yml  standard.yml
menus/      main.yml  shop.yml  profile.yml  themes.yml  theme-preview.yml
players/    玩家主题与语言（运行时生成）
```

升级时保留整个 `plugins/BentoMenu/` 目录即可，新增的默认文件不会覆盖你改过的文件。

改完 YAML 执行 `/bento reload`，不用重启服务端。

## 命令

主命令 `/bento`，别名 `/bm`。

| 命令 | 作用 | 权限 |
|---|---|---|
| `/bento` | 打开默认菜单 | `bento.command.use` |
| `/bento open <菜单> [玩家]` | 打开菜单，可指定其他玩家 | `bento.command.open` / `.open.other` |
| `/bento list` | 列出你有权访问的菜单 | `bento.command.list` |
| `/bento theme [主题\|list]` | 打开主题菜单 / 列出 / 直接切换 | `bento.command.theme` |
| `/bento language [语言]` | 查看或切换语言 | `bento.command.language` |
| `/bento close` | 关闭当前菜单 | `bento.command.close` |
| `/bento info` | 版本、服务端、经济、PAPI、资源包状态 | `bento.command.info` |
| `/bento reload` | 异步热重载全部 YAML | `bento.command.reload` |
| `/bento validate` | 只校验不应用，报告文件、路径、级别 | `bento.command.validate` |
| `/bento stats` | 渲染/点击耗时、物品更新等运行时指标 | `bento.command.stats` |

`bento.bypass.cooldown` 可以绕过按钮冷却，`bento.admin` 聚合了上述管理权限。菜单和主题也可以在各自的 YAML 里用 `permission` 单独限制。

## 配置

`config.yml` 里常用的几项：

```yaml
language:
  default: zh_CN          # 内置 zh_CN / en_US，也可以在 messages/ 里加
  allow-player-switch: true

theme:
  default: aurora

menu:
  default: main           # /bento 打开的菜单

ui:
  resource-pack: false    # 启用资源包后改为 true
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

## 写一个菜单

布局负责形状，菜单负责内容。举个例子，`layouts/main.yml`：

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

`menus/main.yml` 只写物品和动作：

```yaml
id: main
title: "<gradient:primary:secondary>BentoMenu</gradient> <muted>主菜单</muted>"
layout: main
items:
  shop:
    slot: 11
    material: EMERALD
    name: "<gradient:primary:secondary>商店</gradient>"
    lore:
      - "<muted>浏览分类商品</muted>"
    actions:
      - type: open-menu
        menu: shop
    feedback:
      sound: ui.button.click
  close:
    slot: 22
    material: BARRIER
    name: "<color:error>关闭</color>"
    actions:
      - type: close-menu
```

内容多的时候把条目放进 `entries`，配上 `page-size` 就会自动分页。搜索支持铁砧和聊天两种输入，`search.input: auto` 会优先用铁砧。完整的字段说明见 [CONFIGURATION.md](docs/CONFIGURATION.md)。

## 开发者 API

稳定包是 `dev.bento.menu.api.v1`，入口 `BentoApiProvider.get()`。插件里已经打包了 Kotlin DSL 和 Kotlin 标准库，扩展方不用再装。

```java
BentoApi api = BentoApiProvider.get();

MenuDefinition menu = api.menuBuilder("my-menu")
        .title("<gradient:primary:secondary>我的菜单</gradient>")
        .rows(3)
        .layout(Layout.builder(3)
                .row("#########")
                .row("#CCCCCCC#")
                .row("#########")
                .region('#', Layout.RegionType.BORDER)
                .region('C', Layout.RegionType.CONTENT)
                .build())
        .staticItem(4, MenuItem.builder(Material.SUNFLOWER)
                .name("<color:accent>余额：{balance}</color>")
                .build())
        .pageSize(7)
        .refreshInterval(Duration.ofSeconds(5))
        .build();

api.registerMenu(menu);
```

动作、条件、数据源、主题都可以自定义注册，也可以监听插件的打开/关闭事件。上下文和线程模型（Folia 区域调度）见 [API.md](docs/API.md)，可直接运行的示例在 `plugin/bento-example/`。

## 兼容性

| 服务端 | 版本 | Java | 状态 |
|---|---|---|---|
| Spigot | 1.18.2 | 17 | 已通过 |
| Paper | 1.18.2 | 17 | 已通过（含 200 bot 压测） |
| Paper | 1.21.11 | 21 | 已通过 |
| Paper | 26.1.2 | 25 | 已通过 |
| Paper | 26.3 | 25 | 启动通过 |
| Folia | 1.21.8 | 21 | 已通过 |
| Folia | 26.3 | 25 | 启动通过 |
| 混合端（Mohist、Arclight 等） | — | — | 尽力兼容，未实测 |

编译基线是 Spigot 1.18.2，运行期做能力探测。插件只用公开 Bukkit API，没有 NMS、ProtocolLib 或数据包操作；跨版本策略见 [COMPATIBILITY.md](docs/COMPATIBILITY.md)。

## 性能

在 4 核 8 GB 的机器上跑 Paper 1.18.2 + 200 个 mineflayer 机器人（客户端和服务端同一台机器，数据偏保守）：

- 200 人同时打开菜单，TPS 保持 20.00，没有持续掉刻。
- 30 秒内 2,225 次随机点击（约 74 次/秒），TPS 19.25–20.00。
- 平均点击处理 0.061 ms，最大 3.23 ms。
- 平均渲染 4.00 ms，最大 64.94 ms（首次/JIT 冷启动）。
- 5,632 次渲染只发了 38,680 次物品更新，平均每屏 6.9 个槽位，没有整包重发。
- 注入 200 ms 网络延迟后，点击到声音反馈 217–220 ms，5/5 次都收到。

测试细节和已知边界在 [PERFORMANCE.md](docs/PERFORMANCE.md)。

## 从源码构建

需要 JDK 17+ 和 Maven 3.9+。

```bash
cd plugin
mvn clean package
```

产物：

- `plugin/bento-bukkit/target/BentoMenu-1.0.0.jar`
- `plugin/bento-example/target/BentoMenu-Example-1.0.0.jar`

`bento-example` 是可选的扩展示例，不装不影响主插件。

## 文档

| 文档 | 内容 |
|---|---|
| [操作手册](docs/操作手册.md) | 安装、命令、YAML、动作、条件、主题、资源包、故障排查 |
| [人工测试清单](docs/人工测试清单.md) | 发布前的逐项验收清单 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 模块划分、核心类职责、数据流、跨版本策略 |
| [CONFIGURATION.md](docs/CONFIGURATION.md) | 命令、config、布局、主题、菜单、动作、条件、文本语法 |
| [API.md](docs/API.md) | Java API、Kotlin DSL、扩展点、事件 |
| [COMPATIBILITY.md](docs/COMPATIBILITY.md) | 服务端/版本矩阵 |
| [DEPENDENCIES.md](docs/DEPENDENCIES.md) | 为什么硬依赖 Vault 与 PlaceholderAPI |
| [PERFORMANCE.md](docs/PERFORMANCE.md) | 压测与延迟数据 |
| [TEST_REPORT.md](docs/TEST_REPORT.md) | 测试矩阵、缺陷清单与修复记录 |
| [RESOURCE_PACK.md](docs/RESOURCE_PACK.md) | 可选资源包的启用与模型约定 |

## 已知限制

- 混合端没有在本机搭环境，理论可用，但 Folia 区域调度和部分 Paper 事件可能表现不同。
- Paper/Folia 26.3 只验证了启动和插件启用，mineflayer 还没有对应的协议，做不了机器人交互测试。
- 资源包需要服主自己下发（`server.properties` 的 `resource-pack` 或代理端），插件只负责提供模型数据。
- 1.18 客户端不认识部分新版音效和材质名，插件会回退到默认值，不影响使用。
- Folia 上玩家跨区域传送、传送门这类极端场景没有逐一压测。

## 许可

MIT，见 [LICENSE](LICENSE)。

下载见仓库的 Releases 页面。
