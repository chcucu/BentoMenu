# BentoMenu 配置与使用手册

> 所有玩家可见文本都可以在 `messages/<语言>.yml` 中修改；本手册中的示例为默认简体中文。

## 1. 安装

1. 服务端安装 **Vault**（Folia 请使用 VaultUnlocked，插件名同为 `Vault`）与 **PlaceholderAPI**。
2. 把 `BentoMenu-1.0.0.jar` 放入 `plugins/`，启动服务端。
3. 首次启动会释放以下默认文件：

```
plugins/BentoMenu/
├── config.yml
├── messages/
│   ├── zh_CN.yml
│   └── en_US.yml
├── themes/        aurora.yml ember.yml mono.yml sakura.yml
├── layouts/       main.yml shop.yml profile.yml themes.yml theme-preview.yml standard.yml
├── menus/         main.yml shop.yml profile.yml themes.yml theme-preview.yml
└── players/       玩家主题/语言数据（运行后生成）
```

## 2. 命令

主命令 `/bento`，别名 `/bm`。

| 命令 | 说明 | 权限 |
|---|---|---|
| `/bento` | 打开默认菜单 | `bento.command.use` |
| `/bento open <菜单> [玩家]` | 打开菜单，可指定其他玩家 | `bento.command.open` / `.open.other` |
| `/bento list` | 列出有权访问的菜单 | `bento.command.list` |
| `/bento theme [主题\|list]` | 打开主题菜单 / 列出 / 切换主题 | `bento.command.theme` |
| `/bento language [语言]` | 查看或切换语言 | `bento.command.language` |
| `/bento close` | 关闭当前菜单 | `bento.command.close` |
| `/bento info` | 版本、服务端、Folia、经济、PAPI、资源包状态 | `bento.command.info` |
| `/bento reload` | 异步热重载全部 YAML | `bento.command.reload` |
| `/bento validate` | 只校验不应用，报告文件/路径/级别 | `bento.command.validate` |
| `/bento stats` | 运行时指标（渲染/点击耗时、物品更新） | `bento.command.stats` |

权限 `bento.bypass.cooldown` 可绕过按钮冷却；`bento.admin` 聚合管理权限。所有权限默认值见 `plugin.yml`。

## 3. config.yml

```yaml
language:
  default: zh_CN          # 内置 zh_CN / en_US，可在 messages/ 增加
  allow-player-switch: true

theme:
  default: aurora         # 全局默认主题

menu:
  default: main           # /bento 打开的菜单

ui:
  resource-pack: false    # 启用示例/自定义资源包时改为 true
  click-debounce-ms: 120  # 全局点击去抖
  click-sound: true
  open-sound: true

performance:
  data-threads: 2         # 异步数据源线程
  search-threads: 1       # 异步搜索/过滤线程
  placeholder-cache-ttl: 3s
  metrics: true

economy:
  format: "$%.2f"         # Vault 无格式化实现时的回退

search:
  input: auto             # auto / anvil / chat
  max-length: 32

actions:
  on-error: stop          # stop / continue

auto-reload:
  enabled: false
  debounce: 2s

debug: false
```

## 4. 布局 layouts/*.yml

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

规则：

- `rows` 为 1–6；`pattern` 行数必须等于 rows，每行 9 字符。
- 一个布局可被多个菜单引用；内容区 `C` 的数量与顺序决定每页槽位。
- 未映射字符视为空白；`'.'`/`'~'` 表示背景（非边框）。
- 可用区域：`border`、`content`、`background`、`search`、`previous`、`next`、`page`、`category`、`close`。
- 菜单不写 `layout` 时，按行数自动生成标准布局。

## 5. 主题 themes/*.yml

```yaml
id: aurora
display-name: "<gradient:primary:secondary>极光 Aurora</gradient>"
description: "<muted>深海蓝与极光紫的玻璃质感</muted>"
permission: ""                 # 可选：限制玩家使用
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
  custom-model-data: 9001
icons:
  search:   { material: COMPASS,     custom-model-data: 9101 }
  previous: { material: ARROW,       custom-model-data: 9102 }
  next:     { material: ARROW,       custom-model-data: 9103 }
  page:     { material: PAPER,       custom-model-data: 9104 }
  category: { material: NAME_TAG,    custom-model-data: 9105 }
  close:    { material: BARRIER,     custom-model-data: 9106 }
  loading:  { material: CLOCK,       custom-model-data: 9107 }
  error:    { material: BARRIER,     custom-model-data: 9108 }
sounds:
  open: block.chest.open
  click: ui.button.click
  page: item.book.page_turn
  error: entity.villager.no
  success: entity.player.levelup
```

无资源包时 `resource-pack-material` 不生效，边框自动使用与渐变 RGB 最接近的染色玻璃板。

## 6. 菜单 menus/*.yml

### 6.1 顶层字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 菜单 ID，默认取文件名 |
| `enabled` | bool | 是否启用 |
| `title` | text | 支持渐变/颜色标签 |
| `rows` | 1–6 | 行数；与布局冲突时以布局为准并给出警告 |
| `layout` | string | 引用 `layouts/<name>.yml` |
| `permission` | string | 打开所需权限 |
| `theme` | string | 强制主题；空则用玩家主题 |
| `items` | map 或 list | 静态物品/按钮，见 6.2 |
| `components` | map | 覆盖边框、背景、搜索、翻页、分类、关闭、加载、错误的默认外观 |
| `entries` | list | 内容条目，见 6.3 |
| `page-size` | int | 每页条数，默认取内容槽数量 |
| `sort` | string | `none`、`price_asc`、`price_desc`、`name_asc`、`name_desc`、`id` |
| `refresh` | duration | 自动刷新间隔，如 `5s`；空则不刷新 |
| `open-actions` / `close-actions` | list | 打开/关闭时执行的动作链 |

### 6.2 静态物品 / 按钮

```yaml
items:
  balance:
    slot: 4
    material: SUNFLOWER
    amount: 1
    name: "<muted>余额</muted>"
    lore:
      - "<color:accent>{balance}</color>"
    glow: false
    custom-model-data: -1
    enchantments: [DURABILITY:1]
    item-flags: [HIDE_ENCHANTS]
    head: Notch              # 玩家头颅
    leather-color: "#FF0000"
    click-types: [LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT]
    cooldown: 1s
    permission: ""
    conditions: []
    feedback:
      sound: ui.button.click
      title: "<color:primary>✦</color>"
      subtitle: ""
      frame-interval: 120ms
      actionbar: ""
    actions:
      - type: message
        message: "你好，{player}"
```

`items` 也支持列表形式，每个元素带 `slot`。静态物品默认不响应点击（除非配置了 `actions`）。

### 6.3 内容条目

```yaml
data:
  source: yaml            # yaml 或已注册的数据源 ID
  search: true
  refresh: 5s
  sort: price_asc
  categories:
    enabled: true
    order: [food, tools, fun, magic]
    names: { all: 全部, food: 食物, tools: 工具, fun: 娱乐, magic: 魔法 }

entries:
  - id: apple
    category: food
    tags: [food, cheap, fruit]
    search-text: "苹果 apple"   # 可选，参与搜索
    material: APPLE
    amount: 2
    name: "<color:text>苹果</color>"
    price: 6                    # 元数据，供 {price} 与 buy 使用
    lore: [...]
    click-types: [LEFT]
    cooldown: 1s
    feedback: { sound: ui.button.click }
    actions:
      - type: buy
        price: 6
        item: { material: APPLE, amount: 2 }
        success-message: "..."
        failure-message: "..."
```

条目同样支持 `items` 中列出的全部展示字段。`category`/`tags` 用于分类和搜索；`price` 会进入 `{price}`、`{price_raw}`。

### 6.4 组件覆盖

```yaml
components:
  border:
    material: BLACK_STAINED_GLASS_PANE
    name: " "
  empty:
    material: GRAY_STAINED_GLASS_PANE
    name: " "
  search:
    material: COMPASS
    name: "<color:accent>搜索</color>"
  loading:
    item: { material: CLOCK, name: "<color:warning>加载中…</color>" }
```

组件可以只覆盖部分字段；缺失字段回退到主题图标，再回退到消息文件中的默认文案。

## 7. 动作

动作链按顺序执行；每个动作是一个 map，`type` 决定其余字段。

| 动作 | 关键字段 | 说明 |
|---|---|---|
| `player-command` | `command` | 以玩家身份执行，自动去掉开头 `/` |
| `console-command` | `command` | 控制台执行，支持 `{player}` 占位 |
| `give-item` / `give` | `item`/`material`, `amount`, `drop` | 给予物品，背包满时失败 |
| `take-item` / `take` | `item`/`material`, `amount` | 扣除物品，不足时失败 |
| `give-money` | `amount`, `message` | 增加余额（Vault） |
| `take-money` | `amount`, `message` | 扣除余额（Vault） |
| `open-menu` / `open` | `menu`, `page`, `category`, `query`, `theme` | 打开另一菜单 |
| `close-menu` / `close` | — | 关闭当前菜单 |
| `page` | `mode`（next/prev/first/last/goto）, `page` | 切换页码 |
| `message` | `text` / `message` | 发送聊天消息 |
| `actionbar` | `text` / `message` | 动作栏 |
| `title` | `title`, `subtitle`, `frames`, `frame-interval` | 发送标题/字幕动画 |
| `sound` | `sound`, `volume`, `pitch` | 播放声音 |
| `theme` | `theme`, `mode`（apply/preview）, `preview-menu` | 切换或预览主题 |
| `buy` | `price`, `item`, `success-message`, `failure-message` | 原子购买：先校验余额与背包，再扣款/给物，失败回滚 |
| `refresh` | — | 请求刷新当前菜单 |
| `search` | `query` | 打开搜索输入或设置搜索词 |
| `category` | `category` | 切换分类，`all` 表示全部 |
| `condition` / `if` | `conditions`, `then`/`actions`, `else` | 条件分支 |

动作可带 `conditions`、`click-types`；条件不满足时执行同级的 `deny-actions`。动作失败时按 `actions.on-error` 决定停止或继续。

内置动作名保留；插件可通过 API 注册新的动作类型。

## 8. 条件

```yaml
conditions:
  - type: permission
    permission: "shop.vip"
  - type: money
    amount: 100
    operator: ">="
  - type: item
    material: DIAMOND
    amount: 1
  - type: time
    mode: real
    from: "08:00"
    to: "22:00"
  - type: chance
    percent: 25
  - type: placeholder
    placeholder: "%player_level%"
    operator: ">="
    expected: "10"
```

比较运算支持 `>=`、`>`、`<=`、`<`、`==`、`!=`。`chance` 使用 0–100 百分比；`time` 的 `mode: world` 使用游戏 tick，`real` 使用服务器/指定时区时间；跨午夜区间（如 `22:00`–`06:00`）合法。`negate: true` 可反转条件；`deny-message` 可自定义条件不满足时的提示。多个条件之间为“全部满足”；需要分支时使用 `condition` 动作的 `then`/`else`。

## 9. 文本语法

| 语法 | 示例 | 说明 |
|---|---|---|
| 传统颜色 | `&a`、`§a` | 1.18+ 全部可用 |
| RGB | `&#7AD7FF` | 单个 hex |
| RGB 长格式 | `&x&7&A&D&7&F&F` | 与服务端 `§x` 等价 |
| 主题颜色 | `<color:primary>`、`<color:muted>` | 取当前主题颜色 |
| 渐变 | `<gradient:primary:secondary>文本</gradient>` | 逐字符插值 |
| 彩虹 | `<rainbow>文本</rainbow>` | 循环色相 |
| 格式 | `<bold>`、`<italic>`、`<underlined>`、`<strikethrough>`、`<obfuscated>` | 可嵌套 |
| 换行 | `<br>` | 消息/lore 内换行 |

主题颜色键：`primary`、`secondary`、`accent`、`text`、`muted`、`success`、`warning`、`error`、`background-start`、`background-end`、`border-start`、`border-end`。

## 10. 占位符

内部占位符（由 BentoMenu 解析）：

| 占位符 | 说明 |
|---|---|
| `{player}`、`{uuid}`、`{world}`、`{ping}` | 玩家信息 |
| `{balance}` | 经济余额（Vault 格式化） |
| `{level}` | 玩家等级 |
| `{menu}`、`{theme}` | 当前菜单/主题 |
| `{page}`、`{pages}`、`{entries}` | 分页信息 |
| `{category}`、`{category_name}`、`{query}` | 分类/搜索状态 |
| `{slot}`、`{index}`、`{entry}`、`{entry_id}`、`{entry_name}` | 条目上下文 |
| `{price}`、`{price_raw}` | 条目价格 |
| `{item}`、`{amount}` | 购买结果 |

PlaceholderAPI 占位符（如 `%player_level%`、`%vault_eco_balance%`）可直接写入任何文本；渲染前由异步缓存解析，不会阻塞主线程。BentoMenu 也注册了 `%bento_theme%`、`%bento_language%`、`%bento_menu%`、`%bento_page%`、`%bento_pages%`、`%bento_sessions%`。

## 11. 消息文件

`messages/zh_CN.yml`、`en_US.yml` 覆盖所有玩家可见文本：

```yaml
prefix: "<gradient:primary:secondary>Bento</gradient> <muted>»</muted> "
no-permission: "<color:error>你没有权限。</color>"
command:
  usage-open: "<muted>用法：</muted><color:text>/bento open <菜单> [玩家]</color>"
  unknown: "<color:error>未知子命令：{sub}</color>"
menu:
  unknown: "<color:error>菜单不存在：{menu}</color>"
  not-open: "<muted>你当前没有打开菜单。</muted>"
  closed: "<muted>菜单已关闭。</muted>"
  data-error: "<color:error>数据加载失败：{error}</color>"
theme:
  applied: "<color:success>已切换主题：</color><color:text>{theme}</color>"
  unknown: "<color:error>主题不存在：{theme}</color>"
language:
  current: "<muted>当前语言：</muted><color:text>{language}</color> <muted>可用：{available}</muted>"
component:
  search: { name: "<color:accent>搜索</color>", lore: "<muted>点击输入关键词</muted>" }
  # previous / next / page / category / close / loading / error 同理
category:
  all: 全部
  food: 食物
```

- 缺失键回退到内置默认文本，不会出现空消息。
- 用户文件与内置文件合并，升级新增键自动可用。
- 支持 `zh_CN`、`en_US`，也可新增任意语言文件并用 `/bento language <代码>` 切换。
- 命令反馈使用同一套前缀与主题颜色，因此换主题后所有消息同步变色。

## 12. 热重载

- `/bento reload`：异步读取全部 YAML，逐文件校验；成功则整体替换，失败的文件保留上一版本并报告。
- `auto-reload.enabled: true`：监听 `menus/`、`layouts/`、`themes/`、`messages/`、`config.yml`，防抖后自动重载。
- 已打开菜单会刷新；行数/尺寸变化的菜单会在下次打开时生效，当前会话尽量原地更新。
- `/bento validate`：只校验不应用，适合修改前检查。
- 资源包开关变更后建议重载并让玩家重进；资源包本身需由服主自行分发。

## 13. 常见问题

| 现象 | 排查 |
|---|---|
| 插件因缺少 Vault/PlaceholderAPI 不启用 | 两个核心依赖必须安装；Folia 请装 VaultUnlocked（插件名仍为 Vault） |
| 经济余额显示 0 或购买失败 | `/bento info` 查看 `Vault economy`；确认有经济插件注册了 Vault `Economy` 服务 |
| 边框没有渐变 | 无资源包时只能近似为染色玻璃；启用资源包后使用皮革马铠 + RGB 染色 |
| 搜索点击后没反应 | 检查 `search.input`；某些服务端/版本不支持铁砧输入时自动回退聊天 |
| 修改 YAML 后未生效 | 执行 `/bento reload` 或 `/bento validate` 查看错误；失败文件会保留旧版本 |
