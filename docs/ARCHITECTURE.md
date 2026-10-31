# BentoMenu 架构设计

> 本文说明模块划分、核心类职责、关键数据流、调度模型、跨版本策略与性能设计。
> 需求与产品决定的唯一依据见 `PROJECT_MEMORY.md`。

## 1. 总体结构

BentoMenu 是 Maven 多模块工程，最终由 `bento-bukkit` 通过 shade 产出单一发行 jar（`BentoMenu-1.0.0.jar`）。服主只需安装一个 jar，第三方库（Kotlin 标准库等）已内置。

```
plugin/
├── bento-api      公开、稳定、版本化 API（无平台实现依赖）
├── bento-core     纯 Java 引擎：文本、菜单编译、过滤、动作、条件、主题、校验
├── bento-kotlin   Kotlin DSL（编译进发行 jar）
├── bento-bukkit   Bukkit 平台实现：调度、会话、渲染、命令、输入、集成
└── bento-example  示例扩展插件（演示 API 用法，不随主 jar 发布）
```

依赖方向严格单向：

```
bento-bukkit → bento-kotlin → bento-core → bento-api
bento-example → bento-api（仅编译期）
```

`bento-core` 不引用任何 `org.bukkit` 类（`ItemParser` 仅解析字符串字段），因此可以在无服务端的 JVM 中做单元测试；全部 Bukkit 交互集中在 `bento-bukkit`。

## 2. 编译基线、运行基线与跨版本策略

| 项 | 决定 |
|---|---|
| 编译 API | Spigot-API 1.18.2（`provided`） |
| Java 编译目标 | Java 17 |
| 运行服务端 | Spigot / Paper / Folia 1.18+，含 1.21.x、26.x |
| 运行 Java | 17 / 21 / 25（由服务端决定） |
| NMS / ProtocolLib | 不使用 |
| Adventure / MiniMessage | 不作为硬依赖；运行期按需反射 |

跨版本策略的核心是**只使用在 1.18–26.x 都存在的公开 API 形状，并把可能变化的 API 收敛到少数适配点**：

1. **文本走 legacy 字符串**：所有物品名、lore、标题、消息最终序列化为 `§` + hex（`§x§R§R§G§G§B§B`）。该格式从 1.16 起被所有目标版本接受，因此不依赖 Adventure 的组件类型。标题通过 `Bukkit.createInventory(holder, size, String)` 与反射调用 `InventoryView#setTitle`（若存在）更新。
2. **反射只用于“可选增强”**：Folia 调度器、Paper 聊天事件、Paper `sendTitle`、`Bukkit#getTPS`、附魔注册表查找、`InventoryView#setTitle`。每一处都有能力探测与降级路径；探测失败时功能退化而不是崩溃。
3. **不用枚举/常量硬编码新版本内容**：材料统一经 `Materials` 按名称查找，失败时回退到安全的原版材料；附魔经 `CompatEnchantments` 先尝试 `Registry`，再尝试旧常量。
4. **API 版本化**：公开包名 `dev.bento.menu.api.v1`。新增能力只做加法（接口默认方法、新接口），不改既有签名；平台内部实现可以重写，不影响已编译插件。
5. **事件与调度抽象**：业务代码只调用 `Scheduler` 接口；`PlatformScheduler` 在运行期选择 Bukkit 调度器或 Folia 的 Region/Entity/Async/Global 调度器。服务端升级不会改变这一抽象。

## 3. bento-api：稳定契约

| 类 | 职责 |
|---|---|
| `BentoApi` | 门面：注册/注销菜单，打开/关闭，刷新，注册动作/条件/数据源/主题，主题与语言切换，调度器与服务访问 |
| `BentoApiProvider` | 通过 `ServicesManager` 获取实现；插件卸载后返回空 |
| `MenuBuilder` / `MenuDefinition` | 程序化构建菜单：标题、行数、布局、静态物品、条目、数据源、分页、搜索、分类、刷新、打开/关闭动作 |
| `MenuEntry` / `MenuItem` | 内容条目与物品展示模型（纯数据，不依赖 Bukkit） |
| `Layout` | 行数 + ASCII 图案 + 区域映射，构建期校验尺寸 |
| `MenuOpenOptions` | 指定页码、分类、搜索词、主题覆盖 |
| `MenuSessionView` | 动作/条件在运行期读取/修改会话：页码、分类、搜索、可见条目、刷新、搜索输入 |
| `Feedback` | 点击反馈：声音、标题帧、副标题、帧间隔、动作栏 |
| `Theme` | 不可变主题：配色、标题渐变、边框样式/材质/模型、控件图标、音效 |
| `action.Action` / `Condition` / 工厂 | 扩展点：自定义动作与条件，通过 `ActionContext` / `ConditionContext` 访问玩家、会话、配置 |
| `data.DataSource` / 工厂 | 动态内容源：`List<MenuEntry> load(ctx)`，在受限线程池执行 |
| `service.*` | 平台服务契约：经济、占位符、消息、调度、反馈、物品渲染 |
| `event.*` | 打开/关闭/点击事件，供其他插件监听 |
| `config.*` | 给扩展用的只读配置视图 |

`BentoApi` 的所有方法都允许从任意线程调用（实现负责切回玩家所属线程）；文档中标注了线程语义。

## 4. bento-core：纯引擎

### 4.1 文本

- `text/TextEngine`：解析 `&`、`§`、`&#RRGGBB`、`&x&R&R&G&G&B&B`、`<gradient:primary:secondary>`、`<rainbow>`、`<color:key>`、格式标签、`<br>`；解析结果缓存（默认 4096 条）。
- `text/RenderedText`：同时保留 legacy 字符串与去色纯文本，供标题、lore、搜索匹配使用。
- `util/HexColors`：RGB 插值、最近染色玻璃映射、legacy hex 序列化（小写，1.18 客户端稳定接受）。
- 主题颜色键（`primary`、`secondary`、`muted`…）在渲染时替换，因此同一份 YAML 文本可随主题变色。

### 4.2 菜单编译与数据

- `menu/MenuLoader`：读取 `menus/*.yml`，解析标题、行数、布局、静态物品、组件、条目、数据源、搜索、分类、排序、刷新、打开/关闭动作；产出 `CompiledMenu`。
- `menu/CompiledMenu`：不可变编译结果；预计算边框槽、内容槽、背景槽、搜索/翻页/分类/关闭槽；校验页码与页大小。
- `menu/MenuRepository`：分离 YAML 菜单与 API 菜单，热重载只替换前者，不覆盖插件注册的菜单。
- `menu/MenuFilter`：纯函数式过滤/搜索/排序/分页；可在任意线程执行，输入输出均为不可变数据。
- `menu/CooldownService`：每玩家、每按钮冷却与全局点击去抖；无磁盘 I/O。
- `menu/LayoutRegistry` / `AutoLayouts`：布局复用与缺省布局生成。
- `layout/LayoutLoader`：ASCII 图案 → 槽位集合；`#` 边框、`.`/`~` 背景、`C` 内容、`S` 搜索、`K` 分类、`<`/`P`/`>` 翻页、`X` 关闭。
- `data/DataSourceRegistry`：内置 `yaml` 源与 API 数据源注册表。

### 4.3 动作与条件

- `action/ActionParser`：把 YAML/API 配置解析为动作链；支持 `conditional`（条件分支）、`branch`（多分支）、点击类型过滤、`deny-actions`。
- `action/BuiltInActions`：`player-command`、`console-command`、`give`、`take`、`open`、`close`、`page`、`message`、`title`、`sound`、`theme`、`buy`、`refresh`、`search`、`category`。
- `action/ActionRegistry`：内置动作名保留，扩展只能注册新名字，避免版本升级冲突。
- `condition/ConditionParser` + `ConditionEvaluator`：权限、资金、持有物品、服务器时间、随机概率、比较运算；条件可组合（all/any/not）。
- 条件不满足时执行 `deny-actions`，并可通过消息键反馈；动作异常按配置 `actions.on-error` 停止或继续。

### 4.4 配置、主题、校验

- `config/PluginConfig`：`volatile` 快照，热重载整体替换；读取失败保留上一份可用配置。
- `config/MessageCatalog`：合并内置与用户消息文件，缺键回退到内置默认，支持多语言。
- `theme/ThemeRegistry`：内置/YAML/API 主题分离；API 主题在重载时不被覆盖。
- `report/ValidationReport`：`/bento validate` 输出文件名、路径、严重级别与消息，不应用改动。

## 5. bento-bukkit：平台实现

| 类 | 职责 |
|---|---|
| `BentoPlugin` | 生命周期：加载配置快照、线程池、服务、API、命令、监听器、PAPI 扩展、自动重载；异步 reload/validate 后回主线程应用 |
| `scheduler/PlatformScheduler` | Bukkit / Folia 调度适配；运行期探测 `Entity#getScheduler`、全局调度器、`isOwnedByCurrentRegion`；失败时降级到主线程/异步执行器并只警告一次 |
| `menu/MenuService` | 会话注册表；打开/关闭/替换；点击路由；权限/条件/冷却校验；反馈；动作链；购买事务；热重载刷新；关闭清理 |
| `menu/MenuSession` | 每玩家视图状态：页码/分类/搜索、异步加载代次、过滤代次、差异渲染、标题更新、搜索输入、主题解析、刷新任务 |
| `menu/BentoInventoryHolder` | 菜单/输入两种 holder，防止与其它插件 GUI 冲突 |
| `render/MenuRenderer` | 生成完整槽位数组与语义绑定：背景/边框渐变、内容、控件、分类、翻页、关闭；标题渲染 |
| `render/ItemRenderer` | 物品构建：名称/lore 模板、模型数据、发光、不可破坏、附魔、皮革颜色、头颅、tile |
| `input/InputService` | 搜索输入：优先真实铁砧 + `PrepareAnvilEvent`，失败回退聊天；关闭后恢复菜单 |
| `input/ChatInputListener` / `ModernChatInput` | 旧版异步聊天事件 + Paper 新版聊天事件（反射注册） |
| `integration/EconomyBridge` | 懒解析 Vault 经济提供者（限频重试），余额/存取/格式化安全降级 |
| `integration/PlaceholderBridge` | PAPI 异步缓存（TTL），渲染线程永不阻塞在扩展上 |
| `integration/MessageBridge` | 语言/主题感知的消息渲染、前缀、内部占位符、PAPI 解析 |
| `integration/FeedbackBridge` | 声音、标题（Paper 反射 / Spigot Bungee 回退）、动作栏 |
| `integration/BentoPlaceholderExpansion` | `%bento_theme%`、`%bento_language%`、`%bento_menu%`、`%bento_page%` 等 |
| `data/PlayerProfileStore` | 玩家主题/语言持久化；异步防抖保存 |
| `config/ConfigManager` | 加载 config/messages/themes/layouts/menus；单文件失败回退上一成功版本；内置默认释放 |
| `command/BentoCommand` | `/bento` 子命令、权限、Tab 补全 |
| `listener/MenuListener` | 点击/拖拽/关闭/铁砧准备/退出路由 |
| `Metrics` | 会话数、打开/关闭/点击/拒绝/冷却命中、渲染与点击耗时、物品更新数 |

## 6. 关键数据流

### 6.1 打开菜单

```
/bento open shop
  → BentoCommand → BentoApi.open → MenuService.open
  → 创建 MenuSession（解析主题）
  → Bukkit.createInventory(holder, size, title)
  → MenuRenderer.render（骨架：边框/背景/控件）
  → player.openInventory
  → 异步数据源（如非 yaml）
  → 异步过滤/分页
  → 回玩家线程：差异渲染 + 标题
```

### 6.2 点击

```
InventoryClickEvent
  → MenuListener 过滤 holder/顶部背包
  → MenuService.click
  → 权限 → 条件 → 冷却/去抖
  → 反馈（声音/标题/动作栏）
  → 动作链（顺序、条件分支）
  → 需要时 refresh → 每 tick 合并 → 差异渲染
```

### 6.3 搜索

```
点击搜索槽 → InputService.open
  → 真实铁砧（holder=null，身份跟踪）
  → PrepareAnvilEvent → 结果槽确认
  → 提交 → MenuSession.setQuery
  → 异步过滤（search 线程）→ 回玩家线程渲染
  → 聊天输入回退（旧版/新版事件）
```

## 7. 性能设计

| 机制 | 作用 |
|---|---|
| 编译期预计算 | 槽位区域、静态物品模板、分页大小在加载时算好，点击路径无解析 |
| 异步数据/搜索 | 有界线程池（默认 data=2、search=1）；结果按代次丢弃过期任务 |
| 渲染差异更新 | 逐槽 `Objects.equals`，只对变化槽调用 `setItem`，避免整包重发 |
| 会话级刷新合并 | `requestRefresh` 在 1 tick 内合并多次请求 |
| 占位符缓存 | PAPI 单线程异步缓存（TTL，默认 3s），渲染从不阻塞 |
| 经济懒解析 | 不在启用阶段阻塞等待 Vault；提供者缺失时安全降级 |
| 点击去抖 + 按钮冷却 | 默认 120ms 去抖；每按钮独立冷却 |
| 指标 | `/bento stats` 输出平均/最大渲染与点击耗时、物品更新数，便于现场归因 |

## 8. 资源包与无资源包路径

- `ui.resource-pack: true` 且主题声明资源包模型时，边框使用 `LEATHER_HORSE_ARMOR` + RGB 染色实现真渐变，控件使用 `custom_model_data`。
- 默认 `false`：边框回退到最近颜色的染色玻璃板，控件回退到原版材质，标题仍保留 hex 渐变。
- 示例资源包在 `resourcepack/BentoMenu-Pack`，`pack_format: 8`，`supported_formats: 8–100`，覆盖 1.18–26.x。

## 9. 失败隔离

- 单个 YAML 文件解析失败：保留上一成功版本，报告错误，其余菜单继续工作。
- 单个数据源抛异常：会话进入错误态，发送可配置错误消息，不影响其他会话。
- 未知动作/条件/数据源：编译为 `UnsupportedAction` 或条件恒假，校验报告指出位置。
- 扩展注册名冲突：拒绝覆盖内置名称并记录警告。
- Folia 反射失败：降级到安全路径并只警告一次，菜单仍可用（可能退化为全局线程）。
