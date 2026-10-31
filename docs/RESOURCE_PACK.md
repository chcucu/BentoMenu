# 资源包使用说明

## 1. 定位

资源包是**可选增强**，默认不启用。未启用时：

- 边框使用与主题渐变 RGB 最接近的染色玻璃板；
- 搜索/翻页/关闭等控件使用原版材质；
- 标题、lore、消息仍支持 hex/渐变（原版客户端原生支持）。

启用后：

- 边框使用 `LEATHER_HORSE_ARMOR` + RGB 染色，实现真正的逐槽渐变；
- 控件使用 `custom_model_data` 自定义模型与纹理。

## 2. 包内容

```
resourcepack/BentoMenu-Pack/
├── pack.mcmeta
├── README.txt
└── assets/minecraft/
    ├── models/item/       # compass.json, arrow.json, ..., bento_*.json
    └── textures/item/bento/  # 对应 PNG
```

`pack.mcmeta`：

```json
{
  "pack": {
    "pack_format": 8,
    "description": "BentoMenu optional UI pack — gradient glass borders and icons",
    "supported_formats": {
      "min_inclusive": 8,
      "max_inclusive": 100
    }
  }
}
```

- `pack_format: 8` 对应 1.18。
- `supported_formats: 8–100` 覆盖 1.18–26.x；未来版本只需调大上限。
- 没有使用任何客户端模组或 shader，纯原版资源包机制。

## 3. 启用步骤

1. 将 `BentoMenu-Pack` 目录打包为 zip（或直接使用 `release/BentoMenu-ResourcePack.zip`）。
2. 由服主分发：
   - 原版：`server.properties` 的 `resource-pack` URL / SHA1；
   - 代理端：BungeeCord/Velocity 的资源包下发；
   - 或使用其他资源包管理插件。
3. 把 `plugins/BentoMenu/config.yml` 的 `ui.resource-pack` 改为 `true`。
4. `/bento reload` 或重启；玩家重进后加载资源包。
5. 资源包未下载完成时，客户端会先显示原版材质，不影响菜单功能。

## 4. 模型数据约定

| 用途 | 材质 | custom_model_data |
|---|---|---|
| 边框（主题声明 `resource-pack-model-data`） | `LEATHER_HORSE_ARMOR` | 9001 |
| 搜索 | `COMPASS` | 9101 |
| 上一页 | `ARROW` | 9102 |
| 下一页 | `ARROW` | 9103 |
| 页码 | `PAPER` | 9104 |
| 分类 | `NAME_TAG` | 9105 |
| 关闭 | `BARRIER` | 9106 |
| 加载 | `CLOCK` | 9107 |
| 错误 | `BARRIER` | 9108 |

这些值在 `themes/*.yml` 中可改；资源包模型 JSON 的 `overrides` 需与之对应。

## 5. 自定义与替换

- 服主可以复制示例包，替换 PNG 与模型 JSON，保留 `custom_model_data` 编号即可。
- 也可以直接修改主题中的 `resource-pack-material` / `resource-pack-model-data`，指向已有资源包中的模型。
- 不需要重新编译插件。

## 6. 生成脚本

`tools/make-resource-pack.mjs` 用于生成示例 PNG/模型（Node.js）：

```bash
node tools/make-resource-pack.mjs
```

脚本只依赖 Node 内置模块，输出到 `resourcepack/BentoMenu-Pack`。生成物已包含在发行目录中，正常使用无需重新运行。

## 7. 常见问题

| 现象 | 原因 |
|---|---|
| 边框仍是玻璃板 | `ui.resource-pack` 为 false，或玩家未接受资源包 |
| 控件显示成原版指南针/箭 | 资源包未加载，或 `custom_model_data` 与模型 JSON 不一致 |
| 1.21+ 提示资源包版本不兼容 | 检查 `supported_formats` 是否覆盖当前版本 |
| 边框颜色不渐变 | 资源包路径需要 `LEATHER_HORSE_ARMOR` 且主题 `style: gradient`；纯色主题不会渐变 |
