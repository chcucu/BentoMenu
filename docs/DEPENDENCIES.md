# 外部依赖设计

## 1. 两个核心依赖

插件在 `plugin.yml` 中声明：

```yaml
depend: [Vault, PlaceholderAPI]
```

两者都是**硬依赖**，缺少任一插件时 BentoMenu 不会启用。这是有意设计，不是疏忽。

| 依赖 | 版本基线 | 作用 | 为什么必须 |
|---|---|---|---|
| **Vault** | VaultAPI 1.7.1（`provided`） | 经济抽象：余额查询、存取、格式化、`money` 条件、`buy` 动作 | 需求 4/5/11 明确要求资金判断、购买按钮、动态余额。Vault 是 Bukkit 生态唯一跨经济插件的稳定抽象；不硬依赖就无法保证商店菜单在任何经济插件下工作，也无法在启用期判断经济不可用并给出明确错误。Folia 下使用 VaultUnlocked（插件名仍为 `Vault`）。 |
| **PlaceholderAPI** | 2.11.6（`provided`；1.21+/26.x 建议 2.12.3） | 占位符扩展：`%player_level%`、`%vault_eco_balance%` 及任意第三方占位符 | 需求 5/11 要求内容动态化、玩家信息页演示占位符集成、所有文本可配置。PAPI 是唯一被数百个插件实现的占位符协议；不硬依赖则“玩家信息页”和任意第三方数据展示无法成立，异步占位符缓存也失去意义。 |

### 1.1 如何保证“必要性”不是借口

- **功能层面**：`money` 条件、`buy` 动作、`{balance}` 占位符、余额物品都直接调用 `EconomyService`；`profile` 菜单与 `%bento_*%` 之外的动态文本都直接调用 `PlaceholderService`。没有这两个服务，这些功能会明确报错，而不是假装可用。
- **启动层面**：硬依赖使服务端在缺少插件时拒绝加载 BentoMenu，避免管理员在“菜单能开但购买静默失败”的状态下运行。
- **诊断层面**：`/bento info` 显示 `Vault economy` 提供者名称与 `PlaceholderAPI` 是否存在；缺失时给出可操作信息。
- **降级层面**：Vault 存在但没有经济提供者时，插件仍可启动（经济功能返回失败），因为“装了 Vault 但没有经济插件”是常见部署；PlaceholderAPI 存在但某个扩展缺失时，原样保留 `%token%` 仅一瞬，随后缓存或回退。

### 1.2 Folia 说明

Vault 1.7.3 未声明 Folia 支持，Folia 会拒绝加载。解决方案是使用 **VaultUnlocked**：它是 Vault 的社区续作，插件名仍为 `Vault`，声明 `folia-supported: true`，API 仍是 `net.milkbowl.vault.economy.Economy`。BentoMenu 的硬依赖名不变，服主只需在 Folia 上安装 VaultUnlocked。

## 2. 平台依赖

Spigot-API / Paper-API 仅作为 `provided` 编译依赖，不会打进 jar。运行期只使用公开 Bukkit API，不依赖 NMS、ProtocolLib、Adventure 或 Paper 专有类。

## 3. 自带第三方库

发行 jar 已 shade 并 relocate 必要的自身模块：

- `bento-api`、`bento-core`、`bento-kotlin`
- Kotlin 标准库（Kotlin DSL 需要）

服主不需要安装任何 Java 库、Kotlin 运行时或额外 jar。`bento-example` 是独立示例，不包含在发行 jar 中。

## 4. 可选集成

| 集成 | 缺失时行为 |
|---|---|
| 资源包 | 关闭 `ui.resource-pack`，边框回退染色玻璃，控件回退原版材质 |
| Paper 聊天事件 | 回退旧版 `AsyncPlayerChatEvent`；搜索仍可用 |
| Paper 标题 API | 回退 Bungee Chat 颜色/标题序列化 |
| Folia 调度 | 探测失败时回退安全路径并警告一次 |
| 其他经济/等级/统计插件 | 通过 Vault 或 PAPI 间接接入，无需专门代码 |

除 Vault 与 PlaceholderAPI 外，BentoMenu 不硬依赖任何其他插件；所有其他集成都经上述两个抽象或运行期能力探测完成。

## 5. 版本与仓库

- 编译依赖使用 `provided`，运行期由服务端插件提供。
- Maven 仓库：Spigot、Paper、PlaceholderAPI、JitPack（仅构建期）。
- 发行 jar 不包含 Vault/PAPI 的类，避免与服主安装的版本冲突。
- API 模块只依赖 `spigot-api` 与 JDK，扩展方编译时只需 BentoMenu API（可 `provided` 引用发行 jar）。

## 6. 为什么不是“可选依赖”

若把 Vault/PAPI 改为 `softdepend`，会出现：

1. 服主忘记安装 → 菜单打开、购买按钮无反应、占位符显示原始 token，难以排查；
2. 插件需要在每个功能点写“无经济/无占位符”分支，增加复杂度且掩盖配置错误；
3. 演示菜单（商店/玩家信息）无法兑现需求。

硬依赖把部署错误提前到启动期，代价只是两个几乎所有服务器都会安装的轻量插件。
