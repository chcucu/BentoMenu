# 兼容性与跨版本策略

## 1. 支持矩阵

| 服务端 | 版本 | Java | 状态 | 验证内容 |
|---|---|---|---|---|
| Spigot | 1.18.2 | 17 | 已通过 | 启动、菜单、分页、分类、购买、搜索、冷却 |
| Paper | 1.18.2 | 17 | 已通过 | 同上 + 200 bot 压测 |
| Paper | 1.21.11 | 21 | 已通过 | 启动、菜单、分页、分类、购买、搜索、冷却 |
| Paper | 26.1.2 | 25 | 已通过 | 启动、菜单、分页、分类、购买、搜索、冷却 |
| Paper | 26.3 | 25 | 启动通过 | 启动、插件启用、菜单/主题加载（无可用 26.3 bot 协议） |
| Folia | 1.21.8 | 21 | 已通过 | 启动、菜单、分页、分类、购买、聊天搜索、经济 |
| Folia | 26.3 | 25 | 启动通过 | 启动、插件启用、经济（VaultUnlocked）；无可用 26.3 bot 协议 |
| 混合端（Mohist/Arclight 等） | — | — | 尽力兼容 | 只使用公开 Bukkit API；未在本机验证混合端 |

插件 `api-version: '1.18'`，编译目标 Java 17，因此 Java 17/21/25 均可运行。

## 2. 跨版本策略

### 2.1 编译基线选 Spigot 1.18.2

用最低目标版本的 Spigot-API 编译，可以避免误用更高版本才有的方法。所有向上兼容的能力都通过运行期探测启用：

- Paper 的 `sendTitle` / `AsyncChatEvent` / `getTPS`
- Folia 的 `EntityScheduler`、`GlobalRegionScheduler`、`isOwnedByCurrentRegion`
- 附魔注册表、`InventoryView#setTitle`

### 2.2 不使用 NMS

项目没有 `net.minecraft.*`、`CraftBukkit`、ProtocolLib 或数据包操作。所有容器、物品、声音、标题都经 Bukkit 公开 API。

对于“必须改客户端”的能力（渐变物品模型、纹理边框），使用资源包 + `custom_model_data`，而不是 NMS。

### 2.3 文本层与版本解耦

- 物品名/lore/标题统一序列化为 legacy 字符串，1.16+ 全版本可读。
- RGB 使用 `§x§R§R§G§G§B§B`；1.18–26.x 客户端均支持。
- 不依赖 Adventure 类型：Paper 存在 Adventure 时内部仍用 legacy；聊天/标题反射只处理可选的发送路径。

### 2.4 材料与附魔

- `Materials` 按名称查找；新版本新增材料自动可用，旧版本不存在时回退到 `GRAY_STAINED_GLASS_PANE`。
- `CompatEnchantments` 先尝试 1.20.5+ 的 `Registry` 查找，再尝试旧常量；找不到时忽略该附魔，不阻断渲染。

### 2.5 调度抽象

`Scheduler` 是唯一业务调度入口：

| 环境 | 实现 |
|---|---|
| Spigot / Paper | BukkitScheduler（主线程/异步） |
| Folia | EntityScheduler（玩家）、RegionScheduler（位置）、GlobalRegionScheduler、AsyncScheduler |
| 探测失败 | 回退到安全路径并只警告一次 |

`PlatformScheduler` 在类初始化时一次性探测 Folia API，缓存方法句柄；后续版本新增方法不会破坏旧路径。

### 2.6 事件兼容

- 点击/关闭：`InventoryClickEvent`、`InventoryCloseEvent`（全版本稳定）。
- 搜索输入：真实铁砧 + `PrepareAnvilEvent`；不支持时回退聊天。
- 聊天输入：旧版 `AsyncPlayerChatEvent`（Spigot）与 Paper `AsyncChatEvent`（反射动态注册，二者互斥）。

### 2.7 未来 26.x+

- 不硬编码版本号判断，全部能力探测。
- 不依赖内部类名/字段名；反射只调用公开方法名（`getScheduler`、`isOwnedByCurrentRegion`、`sendTitle`）。
- 新版本若移除某个公开方法，探测失败会走降级路径，插件仍可加载。
- 新增内容（材料、附魔、声音）通过名称/注册表查找，旧版本缺少时安全忽略。

### 2.8 资源包

`pack_format: 8`，`supported_formats: 8–100`。1.18–26.x 均可加载；未来版本超出 100 时只需提升 `supported_formats`，不影响插件逻辑。

## 3. 未验证项与限制

- 混合端（Mohist、Arclight、Youer 等）未在本机搭建；它们实现 Bukkit API，理论上可用，但 Folia 区域调度与部分 Paper 事件可能不同。
- Folia 的实体调度要求玩家始终在区域线程；插件已按此实现，但极端的跨区域传送/传送门场景未逐一压测。
- 资源包需要服主自行分发（`server.properties` 的 `resource-pack` 或代理端下发），插件只负责发送模型数据。
- 1.18 客户端不支持部分新版声音/材质名，插件会退回默认值；不影响菜单可用性。
