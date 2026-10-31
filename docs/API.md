# BentoMenu 开发者 API

> 稳定包：`dev.bento.menu.api.v1`。插件内已内置 Kotlin DSL 与 Kotlin 标准库，扩展方无需额外安装。
> 获取 API：`BentoApiProvider.get()`（BentoMenu 未启用时抛出 `IllegalStateException`）。

## 1. 线程模型与 ABI 约定

- `registerMenu` / `unregisterMenu` / `registerAction` / `registerCondition` / `registerDataSource` / `registerTheme` / `themes` / `menus`：线程安全，可在任意插件 `onEnable` 中调用。
- `open` / `close` / `refresh` / `setTheme` / `setLanguage` 等针对玩家的方法：实现会把调用切到该玩家的归属线程（Folia 为区域线程）。从异步线程调用是安全的。
- 数据源 `load` 在受限异步线程池执行；`MenuEntry`/`MenuItem` 必须是不可变快照，不要从数据源线程访问 Bukkit 世界。
- 动作在玩家归属线程执行；`Action` 内可以安全调用 Bukkit API。
- 版本内只做加法：不删除/不修改既有签名；新增通过接口默认方法或新类型完成。

## 2. Java API 快速开始

```java
import dev.bento.menu.api.v1.*;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
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
                .refreshInterval(java.time.Duration.ofSeconds(5))
                .build();

        api.registerMenu(menu);
    }

    @Override
    public void onDisable() {
        BentoApiProvider.get().unregisterMenu("my-menu");
    }
}
```

### 2.1 打开与状态

```java
api.open(player, "my-menu");
api.open(player, "my-menu", MenuOpenOptions.builder()
        .page(2)
        .category("food")
        .themeOverride("sakura")
        .build());

boolean open = api.isOpen(player);
api.openMenu(player).ifPresent(id -> getLogger().info("open: " + id));
int page = api.openPage(player);
int pages = api.openPages(player);
api.refresh(player);          // 合并到下一 tick
api.refreshViewers("my-menu");
api.refreshAll();
api.close(player);
```

### 2.2 主题与语言

```java
api.themes().forEach(theme -> getLogger().info(theme.id()));
api.setTheme(player, "ember");
String current = api.theme(player);
api.setLanguage(player, "en_US");
```

### 2.3 调度器

`api.scheduler(player)` 返回与玩家归属线程绑定的 `Scheduler`：

```java
Scheduler scheduler = api.scheduler(player);
scheduler.run(() -> player.sendMessage("on owner thread"));
scheduler.runLater(() -> {}, 20);
RepeatingTask task = scheduler.runTimer(() -> {}, 20, 20);
task.cancel();
```

在 Spigot/Paper 上等价于主线程调度；在 Folia 上等价于 Entity/Region 调度。

## 3. 注册自定义内容

### 3.1 自定义动作

```java
api.registerAction("broadcast", (ActionFactory) config -> context -> {
    String text = config.getString("text", "Hello");
    context.player().getServer().broadcastMessage(
            context.messages().render(context.player(), text));
});
```

YAML 中即可使用：

```yaml
actions:
  - type: broadcast
    text: "<color:warning>{player} 触发了广播</color>"
```

`ActionContext` 提供：`player()`、`session()`、`menuId()`、`slot()`、`entry()`、`clickType()`、`item()`、`messages()`、`items()`、`refresh()`、`openMenu()`、`closeMenu()`、`setTheme()` 等。

### 3.2 自定义条件

```java
api.registerCondition("level", (ConditionFactory) config -> context -> {
    int min = config.getInt("min", 0);
    int max = config.getInt("max", Integer.MAX_VALUE);
    int level = context.player().getLevel();
    return level >= min && level <= max;
});
```

```yaml
conditions:
  - type: level
    min: 10
    max: 50
```

### 3.3 自定义数据源

```java
api.registerDataSource("crystals", (DataSourceFactory) config -> context -> {
    int count = config.getInt("count", 9);
    List<MenuEntry> entries = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
        entries.add(MenuEntry.builder("crystal-" + i,
                        MenuItem.builder(Material.AMETHYST_SHARD)
                                .amount(i)
                                .name("<color:primary>Crystal #" + i + "</color>")
                                .lore("<muted>动态生成</muted>")
                                .build())
                .category(i % 2 == 0 ? "even" : "odd")
                .metadata("price", i * 10)
                .build());
    }
    return entries;
});
```

```yaml
data:
  source: crystals
  search: true
  refresh: 5s
```

`DataSourceContext` 提供 `player()`、`session()`、`menuId()`、`page()`、`category()`、`query()`、`placeholders()`。数据源可以在任意线程执行，但不得访问 Bukkit 世界。

### 3.4 自定义主题

```java
api.registerTheme(Theme.builder("example", "<gradient:primary:secondary>Example</gradient>")
        .description("示例主题")
        .permission("myplugin.theme.example")
        .color("primary", "#8AE6C1")
        .color("secondary", "#5AA9FF")
        .borderStyle(Theme.BorderStyle.STRIPED)
        .borderMaterial(Material.CYAN_STAINED_GLASS_PANE)
        .icon("close", Material.BARRIER)
        .sound("click", "ui.button.click")
        .build());
```

## 4. 程序化菜单模型

| 类型 | 主要方法 |
|---|---|
| `MenuBuilder` | `title`、`rows`、`layout`、`staticItem`、`component`、`entries`、`dataSource`、`pageSize`、`sortMode`、`search`、`categories`、`refreshInterval`、`openActions`、`closeActions`、`permission`、`forcedTheme` |
| `MenuItem.Builder` | `name`、`lore`、`amount`、`glow`、`customModelData`、`headOwner`、`leatherColor`、`enchantment`、`itemFlags`、`unbreakable`、`clickTypes`、`cooldown`、`permission`、`conditions`、`feedback`、`actions` |
| `MenuEntry.Builder` | `category`、`tags`、`metadata`、`searchText`、`price` |
| `MenuOpenOptions.Builder` | `page`、`category`、`query`、`themeOverride` |
| `Feedback` | `sound`、`title`、`titleFrames`、`subtitle`、`actionBar`、`frameInterval` |
| `Layout.Builder` | `row`、`rows`、`region`（`BORDER`/`BACKGROUND`/`CONTENT`/`SEARCH`/`PREVIOUS`/`NEXT`/`PAGE`/`CATEGORY`/`CLOSE`） |

`MenuDefinition`、`MenuItem`、`MenuEntry`、`Theme` 都是不可变对象，可以跨线程传递。

## 5. Kotlin DSL

```kotlin
import dev.bento.menu.api.v1.BentoApiProvider
import dev.bento.menu.api.v1.kotlin.*

val menu = bentoMenu("kotlin-demo") {
    title("<gradient:primary:secondary>Kotlin Demo</gradient>")
    rows(3)
    layout(bentoLayout(3) {
        row("#########")
        row("#CCCCCCC#")
        row("#########")
        border('#')
        content('C')
    })
    staticItem(4, bentoItem(Material.DIAMOND) {
        name("<color:accent>Hello, {player}</color>")
        lore("<muted>Built with the Kotlin DSL</muted>")
        cooldown(Duration.ofSeconds(1))
        action(action { ctx -> ctx.player().sendMessage("clicked") })
    })
    build()
}

BentoApiProvider.get().registerMenu(menu)
```

DSL 入口：`bentoMenu`、`bentoLayout`、`bentoItem`、`bentoEntry`、`action`、`condition`。

## 6. 事件

```java
@EventHandler
public void onOpen(BentoMenuOpenEvent event) {
    // event.player(), event.menuId()
}

@EventHandler
public void onClick(BentoMenuClickEvent event) {
    // event.player(), event.menuId(), event.slot(), event.clickType()
    // event.entry(), event.item(); event.setCancelled(true) 可拦截动作链
}

@EventHandler
public void onClose(BentoMenuCloseEvent event) {
    // event.reason(): PLAYER / PLUGIN / QUIT / REPLACED / SERVER
}
```

事件在玩家归属线程触发。

## 7. 服务扩展

`ItemService` 默认提供 `render(...)`，可包装/替换渲染逻辑（通过平台内部注入点，高级用法）。常规扩展建议只注册动作/条件/数据源/主题，避免依赖内部实现。

`MessageService` 可用于渲染任意文本（含主题颜色与 PAPI）：

```java
String text = context.messages().render(player, "<gradient:primary:secondary>Hi {player}</gradient>");
```

## 8. 示例工程

`plugin/bento-example` 是完整可运行示例：

- 注册 `broadcast` 动作、`level` 条件、`example-crystals` 数据源、`example` 主题；
- 通过 `MenuBuilder` 注册 `example-addon` 菜单；
- 卸载时注销菜单。

构建：`mvn -pl bento-example -am package`，产物 `BentoMenu-Example-1.0.0.jar`。

## 9. 兼容与弃用策略

- v1 内不删除方法；如必须替换，保留旧方法并标记 `@Deprecated`，至少保留一个大版本。
- 新增平台能力通过 `Scheduler`、`PlaceholderService` 等接口的默认方法暴露。
- 扩展注册名与内置名冲突时拒绝覆盖，并在控制台说明。
- 扩展抛出的异常被隔离：动作报错按配置停止/继续；数据源报错只影响当前菜单会话。
