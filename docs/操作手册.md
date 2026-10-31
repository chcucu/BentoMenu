# BentoMenu 操作手册

版本：1.0.0  
适用：Spigot / Paper / Folia 1.18.2 – 26.x  
主命令：`/bento`，别名 `/bm`

---

## 1. 运行环境

| 项目 | 要求 |
|---|---|
| Java | 17 / 21 / 25（跟随服务端） |
| 服务端 | Spigot、Paper、Folia；混合端尽力兼容 |
| 必装插件 | Vault（Folia 用 VaultUnlocked） |
| 必装插件 | PlaceholderAPI（1.21+/26.x 建议 2.12.3 或更新） |
| 经济插件 | 任意注册 Vault `Economy` 服务的经济插件 |

> BentoMenu 在 `plugin.yml` 中硬依赖 `Vault` 与 `PlaceholderAPI`，缺少任一插件时不会启用。这是为了避免“菜单能打开、购买却静默失败”的部署错误。

---

## 2. 安装

1. 关闭服务端。
2. 安装 Vault（Folia 使用 VaultUnlocked，插件名仍为 `Vault`）与经济插件。
3. 安装 PlaceholderAPI。
4. 把 `BentoMenu-1.0.0.jar` 放入 `plugins/`。
5. 启动服务端，确认控制台出现：

```
[BentoMenu] BentoMenu enabled: 5 menus, 4 themes, Folia=false
```

6. 进入游戏执行 `/bento`，应打开主菜单。

首次启动会在 `plugins/BentoMenu/` 释放：

```
config.yml
messages/zh_CN.yml
messages/en_US.yml
themes/       aurora.yml ember.yml mono.yml sakura.yml
layouts/      main.yml shop.yml profile.yml themes.yml theme-preview.yml standard.yml
menus/        main.yml shop.yml profile.yml themes.yml theme-preview.yml
players/      玩家主题与语言（运行时生成）
```

升级插件时保留 `plugins/BentoMenu/` 目录即可；新增默认文件不会覆盖你已修改的文件。

---

## 3. 命令与权限

| 命令 | 作用 | 默认权限 |
|---|---|---|
| `/bento` | 打开默认菜单（`menu.default`） | `bento.command.use`（所有人） |
| `/bento open <菜单> [玩家]` | 打开菜单；指定其他玩家需要管理权限 | `bento.command.open` / `bento.command.open.other` |
| `/bento list` | 列出你有权访问的菜单 | `bento.command.list` |
| `/bento theme [主题\|list]` | 打开主题菜单 / 列出 / 直接切换 | `bento.command.theme` |
| `/bento language [语言]` | 查看或切换语言 | `bento.command.language` |
| `/bento close` | 关闭当前菜单 | `bento.command.close` |
| `/bento info` | 版本、服务端、经济、PAPI、资源包状态 | `bento.command.info` |
| `/bento reload` | 异步热重载全部 YAML | `bento.command.reload`（OP） |
| `/bento validate` | 只校验不应用，输出错误/警告 | `bento.command.validate`（OP） |
| `/bento stats` | 渲染/点击耗时、物品更新等指标 | `bento.command.stats`（OP） |

其他权限：

- `bento.bypass.cooldown`：绕过按钮冷却（默认 OP）。
- `bento.admin`：聚合上述管理权限（默认 OP）。
- 菜单可在 YAML 中用 `permission:` 单独限制。
- 主题可在主题 YAML 中用 `permission:` 限制使用。

---

## 4. config.yml 说明

```yaml
language:
  default: zh_CN          # 内置 zh_CN / en_US；可新增消息文件
  allow-player-switch: true

theme:
  default: aurora         # 全局默认主题

menu:
  default: main           # /bento 打开的菜单

ui:
  resource-pack: false    # 启用资源包后改为 true
  click-debounce-ms: 120  # 全局防连点
  click-sound: true
  open-sound: true

performance:
  data-threads: 2         # 异步数据源线程数
  search-threads: 1       # 异步搜索/过滤线程数
  placeholder-cache-ttl: 3s
  metrics: true

economy:
  format: "$%.2f"         # Vault 无格式化能力时的回退

search:
  input: auto             # auto / anvil / chat
  max-length: 32

actions:
  on-error: stop          # stop / continue

auto-reload:
  enabled: false          # 监视 YAML 变化并自动重载
  debounce: 2s

debug: false
```

修改后执行 `/bento reload`；若开启 `auto-reload`，保存文件后会自动生效。

---

## 5. 菜单 YAML

文件位置：`plugins/BentoMenu/menus/<id>.yml`。文件名即默认菜单 ID。

### 5.1 最小示例

```yaml
id: example
title: "<gradient:primary:secondary>示例菜单</gradient>"
rows: 3
layout: main
items:
  hello:
    slot: 13
    material: DIAMOND
    name: "<color:accent>你好，{player}</color>"
    lore:
      - "<muted>这是静态物品</muted>"
    actions:
      - type: message
        message: "<color:success>你点击了钻石</color>"
```

### 5.2 顶层字段

| 字段 | 说明 |
|---|---|
| `id` | 菜单 ID；缺省取文件名 |
| `enabled` | 是否加载，默认 `true` |
| `title` | 标题，支持颜色/渐变标签 |
| `rows` | 1–6 行；若使用布局，以布局为准 |
| `layout` | 引用 `layouts/<name>.yml` |
| `permission` | 打开所需权限 |
| `theme` | 强制主题；留空使用玩家主题 |
| `items` | 静态物品/按钮（map 或 list） |
| `components` | 覆盖边框、背景、搜索、翻页等默认外观 |
| `entries` | 内容条目（静态数据） |
| `page-size` | 每页条目数，默认取内容槽数量 |
| `sort` | `none` / `price_asc` / `price_desc` / `name_asc` / `name_desc` / `id` |
| `refresh` | 自动刷新间隔，如 `5s`；留空不刷新 |
| `open-actions` / `close-actions` | 打开/关闭时执行的动作链（也可写 `actions.open` / `actions.close`） |

### 5.3 data 段与内容

```yaml
data:
  source: yaml            # yaml 或已注册的数据源 ID
  search: true
  categories:
    enabled: true
    order: [food, tools, fun, magic]
    names:
      all: 全部
      food: 食物
      tools: 工具
      fun: 娱乐
      magic: 魔法
  page-size: 14
  sort: price_asc
  refresh: 5s
```

`entries` 推荐写在顶层；也可写在 `data.entries`。当 `data.source` 不是 `yaml` 时，`entries` 会被忽略。

### 5.4 条目与购买按钮

```yaml
entries:
  - id: apple
    category: food
    tags: [food, cheap, fruit]
    material: APPLE
    amount: 2
    name: "<color:text>苹果</color>"
    price: 6
    lore:
      - "<muted>价格</muted> <color:accent>{price}</color>"
      - "<muted>余额</muted> <color:text>{balance}</color>"
    click-types: [LEFT]
    cooldown: 1s
    feedback:
      sound: ui.button.click
    actions:
      - type: buy
        price: 6
        item:
          material: APPLE
          amount: 2
        success-message: "<color:success>购买成功</color> <muted>·</muted> <color:text>{item} ×{amount}</color>"
        failure-message: "<color:error>余额不足或背包已满</color>"
```

条目支持与静态物品相同的展示字段（`material`、`amount`、`name`、`lore`、`glow`、`custom-model-data`、`head`、`leather-color`、`enchantments`、`item-flags`、`unbreakable` 等）。

---

## 6. 动作

动作链按顺序执行，每个动作以 `type` 开头。

| type | 关键字段 | 说明 |
|---|---|---|
| `message` / `msg` | `text` 或 `message` | 聊天消息 |
| `actionbar` | `text` / `message` | 动作栏 |
| `title` | `title`、`subtitle`、`frames`/`title-frames`、`frame-interval`、`fade-in`、`stay`、`fade-out` | 标题/字幕动画 |
| `sound` | `sound`/`name`、`volume`、`pitch` | 播放声音 |
| `player-command` / `player` | `command` | 玩家身份执行 |
| `console-command` / `console` | `command` | 控制台执行 |
| `give-item` / `give` | `item` 或 `material`、`amount`、`drop`、`message` | 给物品 |
| `take-item` / `take` | `material` 或 `item.material`、`amount`、`failure-message` | 扣物品 |
| `give-money` | `amount`、`message` | 加钱（Vault） |
| `take-money` | `amount`、`message` | 扣钱（Vault） |
| `open-menu` / `open` | `menu`、`page`、`category`、`query`、`theme` | 打开另一个菜单 |
| `close-menu` / `close` | — | 关闭当前菜单 |
| `page` | `mode`、`page` | `next` / `prev` / `first` / `last` / `goto` |
| `theme` | `theme`、`mode`、`preview-menu` | `apply` 应用 / `preview` 预览 |
| `refresh` | — | 请求刷新 |
| `search` | `query` | 打开搜索或设置搜索词 |
| `category` | `category` | 切换分类，`all` 表示全部 |
| `buy` | `price`、`item` 或 `material`、`amount`、`success-message`、`failure-message` | 购买（先校验余额与背包，再扣款/给物） |
| `condition` / `if` | `conditions`、`then`/`actions`、`else` | 条件分支 |

通用附加字段（除 `condition` 外）：

```yaml
actions:
  - type: message
    message: "<color:success>已领取</color>"
    click-types: [LEFT, SHIFT_LEFT]
    conditions:
      - type: permission
        permission: "server.vip"
    deny-actions:
      - type: message
        message: "<color:error>需要 VIP 权限</color>"
```

条件分支示例：

```yaml
actions:
  - type: condition
    conditions:
      - type: money
        amount: 100
        operator: ">="
    then:
      - type: message
        message: "<color:success>余额充足</color>"
    else:
      - type: message
        message: "<color:error>余额不足</color>"
```

---

## 7. 条件

```yaml
conditions:
  - type: permission
    permission: "my.permission"

  - type: money
    amount: 100
    operator: ">="

  - type: item
    material: DIAMOND
    amount: 1
    operator: ">="

  - type: time
    mode: real          # real 或 world
    from: "08:00"
    to: "22:00"
    timezone: "Asia/Shanghai"

  - type: chance
    percent: 25         # 0–100

  - type: placeholder
    placeholder: "%player_level%"
    operator: ">="
    expected: "10"
```

| 字段 | 说明 |
|---|---|
| `type` | `permission`、`money`、`item`、`time`、`chance`、`placeholder`，或扩展注册的条件 ID |
| `operator` | `>=`（默认）、`>`、`<=`、`<`、`==`、`!=` |
| `negate` | `true` 时取反 |
| `deny-message` | 条件不满足时发送的消息 |

`time` 的 `mode: world` 使用游戏 tick（0–24000）；`real` 使用服务器/指定时区时间。跨午夜区间（如 22:00–06:00）有效。

简写条件：`permission:node`、`chance:25`。

---

## 8. 文本语法与占位符

### 8.1 颜色与格式

| 写法 | 作用 |
|---|---|
| `&a` / `§a` | 传统颜色 |
| `&#7AD7FF` | RGB 颜色 |
| `&x&7&A&D&7&F&F` | RGB 长格式 |
| `<color:primary>` | 主题颜色键 |
| `<gradient:primary:secondary>文字</gradient>` | 渐变 |
| `<rainbow>文字</rainbow>` | 彩虹 |
| `<bold>`、`<italic>`、`<underlined>`、`<strikethrough>`、`<obfuscated>` | 格式 |
| `<br>` | 换行 |

主题颜色键：`primary`、`secondary`、`accent`、`text`、`muted`、`success`、`warning`、`error`、`background-start`、`background-end`、`border-start`、`border-end`。

### 8.2 内置占位符

| 占位符 | 内容 |
|---|---|
| `{player}`、`{uuid}`、`{world}` | 玩家信息 |
| `{ping}`、`{level}`、`{health}`、`{food}`、`{online}` | 状态 |
| `{balance}`、`{balance_raw}` | 经济余额 |
| `{menu}`、`{theme}` | 当前菜单/主题 |
| `{page}`、`{pages}`、`{entries}` | 分页 |
| `{category}`、`{category_name}`、`{query}` | 筛选/搜索 |
| `{slot}`、`{index}`、`{entry}`、`{entry_id}`、`{entry_name}` | 条目上下文 |
| `{price}`、`{price_raw}` | 条目价格 |

### 8.3 PlaceholderAPI

任何 `%...%` 占位符都会在渲染前异步解析。BentoMenu 自身注册：

- `%bento_theme%`
- `%bento_language%`
- `%bento_menu%`
- `%bento_page%`
- `%bento_pages%`
- `%bento_sessions%`

首次打开或缓存过期后的极短时间内，未解析的 token 不会被显示成原始文本（会使用上一次缓存值）。

---

## 9. 布局

`layouts/<name>.yml`：

```yaml
rows: 5
pattern:
  - '#########'
  - '#CCCCCCC#'
  - '#CCCCCCC#'
  - '#KKKKKKK#'
  - '#S#<P>#X#'
regions:
  '#': border
  'C': content
  'K': category
  'S': search
  '<': previous
  'P': page
  '>': next
  'X': close
```

| 符号/区域 | 含义 |
|---|---|
| `#` / `border` | 边框 |
| `.`、`~` / `background` | 背景 |
| `C` / `content` | 内容区（分页条目） |
| `S` / `search` | 搜索按钮 |
| `<` / `previous` | 上一页 |
| `>` / `next` | 下一页 |
| `P` / `page` | 页码显示 |
| `K` / `category` | 分类按钮 |
| `X` / `close` | 关闭 |

规则：

- `rows` 1–6，`pattern` 行数必须一致，每行 9 字符。
- 同一布局可被多个菜单复用；内容与布局解耦。
- 不写 `layout` 时按 `rows` 自动生成标准布局。

---

## 10. 主题

`themes/<id>.yml`：

```yaml
id: aurora
display-name: "<gradient:primary:secondary>极光 Aurora</gradient>"
description: "<muted>深海蓝与极光紫</muted>"
permission: ""                 # 可选
colors:
  primary: "#7AD7FF"
  secondary: "#B39DFF"
  accent: "#FFD166"
  text: "#EAF2FF"
  muted: "#8FA3BF"
  success: "#5BE49B"
  warning: "#FFC857"
  error: "#FF6B6B"
  background-start: "#0B1220"
  background-end: "#1B2A4A"
  border-start: "#2A3D66"
  border-end: "#5B4B8A"
title:
  gradient: [primary, secondary]
border:
  style: gradient             # solid / striped / gradient
  material: GRAY_STAINED_GLASS_PANE
  resource-pack-material: LEATHER_HORSE_ARMOR
  resource-pack-model-data: 9001
icons:
  search:   { material: COMPASS,  custom-model-data: 9101 }
  previous: { material: ARROW,    custom-model-data: 9102 }
  next:     { material: ARROW,    custom-model-data: 9103 }
  page:     { material: PAPER,    custom-model-data: 9104 }
  category: { material: NAME_TAG, custom-model-data: 9105 }
  close:    { material: BARRIER,  custom-model-data: 9106 }
  loading:  { material: CLOCK,    custom-model-data: 9107 }
  error:    { material: BARRIER,  custom-model-data: 9108 }
sounds:
  open: block.chest.open
  click: ui.button.click
  page: item.book.page_turn
  error: entity.villager.no
  success: entity.player.levelup
```

玩家选择：

- `/bento theme` 打开主题菜单（默认 `themes`）。
- `/bento theme sakura` 直接切换。
- 主题菜单左键应用、右键预览。

未启用资源包时，边框使用与渐变 RGB 最接近的染色玻璃板。

---

## 11. 可选资源包

1. 分发 `BentoMenu-ResourcePack.zip`（`server.properties`、代理端或资源包管理插件）。
2. 把 `config.yml` 的 `ui.resource-pack` 改为 `true`。
3. `/bento reload`，玩家重进后生效。

资源包提供真渐变边框（`LEATHER_HORSE_ARMOR` + RGB）与自定义控件图标。未启用时所有菜单仍完整可用，只是边框退化为染色玻璃。

---

## 12. 热重载与校验

| 操作 | 说明 |
|---|---|
| `/bento reload` | 异步读取全部 YAML；成功整体替换，失败文件保留上一版本 |
| `/bento validate` | 只校验不应用，输出文件、路径、级别 |
| `auto-reload` | 监视 `menus/`、`layouts/`、`themes/`、`messages/`、`config.yml`，防抖后重载 |

推荐流程：先 `/bento validate`，再 `/bento reload`。重载后已打开的菜单会刷新；行数/结构变化的菜单在下次打开时生效。

---

## 13. 玩家数据

- 玩家主题与语言保存在 `plugins/BentoMenu/players/`。
- 异步防抖写入，正常关服不会丢失。
- 删除某玩家文件会恢复默认主题/语言。
- 冷却与去抖状态只在内存，重启后清空。

---

## 14. 故障排查

| 现象 | 处理 |
|---|---|
| 插件未启用，提示缺少 Vault/PlaceholderAPI | 安装两个核心依赖；Folia 使用 VaultUnlocked |
| `/bento` 提示菜单不存在 | 检查 `menus/` 文件名与 `menu.default`，执行 `/bento validate` |
| 购买无反应 | `/bento info` 查看 `Vault economy`；确认经济插件已注册 Vault 服务 |
| 余额显示 0 | 同上；或玩家确实没有余额 |
| `%bento_theme%` 显示为原始文本 | 确认 PlaceholderAPI 已启用；首次解析有极短缓存延迟；仍异常时 `/bento reload` |
| 搜索点击后进入聊天 | 该服务端/版本不支持铁砧输入，自动回退聊天；输入 `cancel` 取消 |
| 修改 YAML 后无效 | `/bento validate` 查看错误；失败文件保留旧版本 |
| 边框没有渐变 | 未启用资源包时只能近似染色玻璃；启用资源包并使用 `style: gradient` |
| Folia 上菜单不打开 | 确认使用 VaultUnlocked 与 BentoMenu 1.0.0；查看控制台是否有调度警告 |
| 26.x 上 PAPI 不加载 | 升级 PlaceholderAPI 到 2.12.3+ |

---

## 15. 备份与升级

1. 升级前备份 `plugins/BentoMenu/`（特别是 `menus/`、`themes/`、`layouts/`、`messages/`）。
2. 替换 jar，重启或 `/bento reload`。
3. 新增默认文件不会覆盖已有文件；合并新键时可参考发行包 `docs/`。
4. 大版本升级不会删除 v1 API；扩展插件无需重新编译。

---

## 16. 性能建议

- 静态菜单（无动态数据）不要设置 `refresh`，或设置较长间隔。
- 条目很多时使用分页与分类，避免单页渲染数百个物品。
- 动态数据源应使用异步 API；不要在 `DataSource.load` 中访问 Bukkit 世界。
- 需要大量 PAPI 占位符时，适当增大 `performance.placeholder-cache-ttl`。
- 200 人在线时建议 `data-threads: 2`、`search-threads: 1` 起步，再按 CPU 调整。
- 资源包关闭时渲染路径更轻；但边框仍保留主题色。

---

## 17. 目录速查

```
plugins/BentoMenu/
├── config.yml
├── messages/
├── themes/
├── layouts/
├── menus/
└── players/
```

发行包中 `docs/` 还包含架构、API、兼容性、性能与测试报告，供服主和开发者参考。
