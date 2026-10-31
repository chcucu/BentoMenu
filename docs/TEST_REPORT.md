# 测试报告

## 1. 测试范围

| 类别 | 内容 |
|---|---|
| 单元测试 | 文本引擎、菜单过滤/分页/搜索、布局/冷却、时长与十六进制工具 |
| 功能测试 | 菜单打开、分页、分类、购买、冷却、搜索、主题、示例扩展 |
| 真实服务端 | Spigot 1.18.2、Paper 1.18.2、Paper 1.21.11、Paper 26.1.2、Paper 26.3、Folia 1.21.8、Folia 26.3 |
| 压力测试 | 200 bot 同时在线/打开菜单/持续点击、TPS 采样 |
| 延迟测试 | 本机直连 + 单向 100ms 代理（RTT ≈ 200ms） |
| 构建 | Maven clean package，27 个测试，0 失败 |

## 2. 单元测试

```
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖：

- `TextEngineTest`：legacy/hex/gradient/rainbow/格式标签/<br>、颜色键替换、非法标签保留。
- `MenuFilterTest`：分类、标签、搜索词、排序（价格/名称/ID）、分页边界、空结果。
- `LayoutAndCooldownTest`：布局尺寸校验、区域解析、冷却计时与去抖。
- `DurationsTest`：`5s`/`1m`/`250ms`/非法值、hex 解析与小写序列化。

运行：

```bash
cd plugin
mvn test
```

## 3. 功能测试矩阵

每个服务端均安装 Vault（Folia 为 VaultUnlocked）、PlaceholderAPI、BentoMenu 与测试夹具；用 mineflayer 机器人执行真实点击。

| 用例 | Spigot 1.18.2 | Paper 1.18.2 | Paper 1.21.11 | Paper 26.1.2 | Folia 1.21.8 |
|---|---|---|---|---|---|
| 启动/插件启用 | 通过 | 通过 | 通过 | 通过 | 通过 |
| 渐变标题 | 通过 | 通过 | 通过 | 通过 | 通过 |
| 分页/上一页/下一页 | 通过 | 通过 | 通过 | 通过 | 通过 |
| 分类筛选 | 通过 | 通过 | 通过 | 通过 | 通过 |
| 购买 + 扣款 + 到账 | 通过 | 通过 | 通过 | 通过（cooldown 用例） | 通过 |
| 动态余额刷新 | 通过 | 通过 | 通过 | 通过 | 通过 |
| 1 秒冷却/防连点 | 通过 | 通过 | 通过 | 通过 | 通过 |
| 聊天搜索回退 | 通过 | 通过 | 通过 | 通过（此前） | 通过 |
| 示例扩展菜单 | 未装 | 通过 | 未装 | 未装 | 未装（未安装扩展） |

补充：

- Paper 26.3、Folia 26.3：仅启动/插件启用（`5 menus, 4 themes, Folia=false/true`）与 Vault/PAPI 集成检查。mineflayer 不支持 26.3 协议，无法进行 bot 级交互。
- 示例扩展在 Paper 1.18.2 验证：启动日志 `BentoMenu example addon registered action, condition, data source, theme and menu.`，`/bento open example-addon` 正常显示程序化菜单、条纹主题、分页控件。
- 命令与热重载（Paper 1.18.2，最终 release jar）：`/bento info` 显示版本/服务端/Folia/经济/PAPI/资源包/API；`/bento list` 列出 6 个菜单（含扩展 `example-addon`）；`/bento theme sakura` 切换成功；`/bento language en_US` 后反馈消息立即变为英文，切回 `zh_CN` 恢复中文；`/bento validate` 输出“0 错误 0 警告”；`/bento reload` 输出“重载完成 · 6 菜单 / 5 主题”。

## 4. 关键测试输出摘要

- Spigot/Paper 1.18.2：`PAGE1` 14 个条目；翻页后 `PAGE2` 4 个工具条目；分类“食物”仅显示 6 个食物；购买后 `COOKIE_AFTER 8`、余额 `$4.00` 扣除；冷却测试 `delta=16, success=1, cooldown=1`。
- Paper 1.21.11：苹果搜索后仅 1 个具名条目（其余为空槽）；购买成功；冷却一次成功。
- Paper 26.1.2：分页/分类/购买/冷却通过。
- Folia 1.21.8：分页/分类/购买通过；经济提供者 `BentoTestEconomy` 正常。
- 动态余额：购买前 `$500.00` → 1 秒内 `$497.00`（工具槽价格 3）；7 秒周期刷新后仍为 `$497.00`，且 `%bento_theme%` 保持 `aurora`（修复后）。

## 5. 性能与延迟

详见 `PERFORMANCE.md`。核心结论：

- 200 人在线、菜单关闭：TPS 20.0。
- 200 个菜单同时打开（同一 tick）：瞬间采样 19.78，随后 20.0。
- 200 人持续点击（约 74 次/秒）：TPS 19.25–20.0，点击处理平均 0.061ms，渲染平均 4.0ms。
- 本机点击→声音 0.95–3.64ms；200ms RTT 代理下 217–220ms（网络 + 约 20ms）。
- 5/5 延迟测试点击均收到反馈，无丢失、无卡死。

## 6. 发现并修复的缺陷

| # | 缺陷 | 根因 | 修复 |
|---|---|---|---|
| 1 | Folia 上菜单无法打开 | `PlatformScheduler` 用实例 `getClass().getMethod("getScheduler")`，方法不在 CraftPlayer 类上 | 改为扫描 `Entity` 公共方法，缓存 `EntityScheduler` 的 `run/runDelayed/runAtFixedRate`；全局调度器与 `isOwnedByCurrentRegion` 同样扫描 |
| 2 | 铁砧搜索无 `PrepareAnvilEvent` | 自定义 holder 使铁砧不是真实 `AnvilInventory` | 使用 holder=null 的真实铁砧，按 `inventory` 身份跟踪；非铁砧时回退聊天 |
| 3 | 点击可能路由到底部背包 | 使用 `event.getInventory()` | 改用 `event.getView().getTopInventory()` |
| 4 | 经济在启用期为“不可用” | 测试经济插件晚于 BentoMenu 注册 | `EconomyBridge.resolve()` 懒解析 + 限频重试，提供者出现后自动可用 |
| 5 | 周期刷新显示 `%bento_theme%` | PAPI 缓存 TTL 过期后 `cached()` 返回 null，渲染输出原始 token | stale-while-revalidate：过期仍返回上次值并异步刷新；`preload` 跳过新鲜项 |
| 6 | 单元测试 `Bukkit.getItemFactory` NPE | `Theme.Builder` 默认构造 `MenuItem`；`MenuItem` 构造复制 base meta | 移除 Theme 默认图标；MenuItem 不再在构造期访问服务端 |
| 7 | 布局区域超过 10 个时抛异常 | `Map.of` 上限 10 对 | 改为 `Map.ofEntries` |
| 8 | 点击可能导致重复购买/扣款 | 去抖与动作链未原子化 | 全局点击去抖 120ms + 按钮冷却 + `buy` 先校验后扣款、失败回滚 |
| 9 | 单元测试 hex 大小写不一致 | `HexColors.legacyHex` 输出大写 | 统一小写，测试与服务端一致 |
| 10 | Vault 在 Folia 不加载 | Vault 1.7.3 未声明 Folia 支持 | 文档指定 Folia 使用 VaultUnlocked（插件名仍 `Vault`）；`plugin.yml` 硬依赖名不变 |
| 11 | PlaceholderAPI 2.11.6 在 Paper 26.3 加载失败 | 缺少 bStats 类 | 文档建议 1.21+/26.x 使用 PAPI 2.12.3；测试环境已验证 |
| 12 | Folia 26.3 测试夹具被拒绝 | 旧夹具未标记 `folia-supported` | 重新构建夹具（测试专用，不随发行） |

## 7. 未覆盖/限制

- 混合端（Mohist/Arclight/Youer）未搭建；只保证公开 Bukkit API 路径。
- 26.3 协议无可用 bot 客户端，仅验证到插件启用层。
- 未做 200 人 Folia 压测（Folia 区域调度下 bot 行为与 Paper 不同，且本机资源有限）。
- 资源包视觉效果未做像素级人工验收；模型/纹理已生成并通过 JSON 结构检查，默认关闭。
- 未测试所有第三方经济插件；使用 Vault 抽象与测试经济验证接口。

## 8. 复现

```bash
# 单元测试
cd plugin && mvn clean test

# 功能（示例，需先启动对应服务端）
cd tools/loadtest
node inspect.mjs 127.0.0.1 25565 1.18.2
node chattest.mjs 127.0.0.1 25565 1.18.2 苹果
node cooldowntest.mjs 127.0.0.1 25565 1.18.2
node menutest.mjs 127.0.0.1 25565 1.18.2 example-addon
```

测试夹具与脚本位于 `tools/`，不随发行 jar 发布。
