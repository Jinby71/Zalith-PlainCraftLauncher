# 插件开发指南

适用启动器版本：**1.4.x（协议 1.4.6 / 200063 起）**

插件不是代码模块，而是一个**普通 APK**：启动器不加载它的 dex、不共享进程，  
只从它的 `assets/zplugin/` 里读配置，再用原生控件把内容画出来。  
所以插件能做多少，取决于它自己那个 app 能做多少；启动器只提供「入口 + 渲染 + 几个系统动作」。

---

## 一、三类插件

| 类型     | 标记                               | 作用                     |
| ------ | -------------------------------- | ---------------------- |
| 资源插件   | `target: "minecraft"` + `files/` | 把文件树覆盖进游戏目录（模组、资源包、配置） |
| 皮肤插件   | `ui: "<标识>"` + `ui.json`         | 只换外观：配色、圆角、玻璃渲染        |
| 自带界面插件 | `entry: true` + `label`          | 本身是个装好的 app，启动器给一个入口   |

三类可以叠在一个 APK 里。本文主要讲第三类（它同时能用页面协议）。

## 二、开放接口（对外契约）

插件能依赖的只有下面这些。标 ✅ 的是稳定契约，改名会走大版本；🧪 能用但细节可能补；
启动器内部实现（`PclUiRuntime`、渲染器、皮肤字段、`plugins/<id>/` 目录布局）**不是接口**，别依赖。

### 2.1 插件 → 启动器：配置接口

| 接口 | 位置 | 自 | 状态 |
|---|---|---|---|
| `plugin.json` 清单（`name`/`version`/`author`/`description`/`target`/`enabled`/`ui`/`entry`/`label`） | APK 资源 | 1.4.0 | ✅ |
| `pages` 页面（`id`/`title`/`left`/`blocks`/`footer`/`layout`） | pages.json | 1.4.0 | ✅（`footer`、`layout` 🧪） |
| `sections` 整块接管版块 | pages.json | 1.4.1 | ✅ |
| `entries` 左栏追加条目（`position`） | pages.json | 1.4.1 | ✅ |
| `entries[].display = content` 画在主栏 | pages.json | 1.4.3 | ✅ |
| 积木 11 种 | pages.json | 1.4.0~1.4.2 | ✅（`icon`、`version_collection` 🧪） |
| 动作 9 种 | pages.json | 1.4.0~1.4.1 | ✅ |
| `button.primary` 主按钮 | pages.json | 1.4.1 | ✅ |
| `status` 按包名显示安装状态 | pages.json | 1.4.1 | ✅ |

### 2.2 插件 → 启动器：运行时数据

| 接口 | 形式 | 自 | 状态 |
|---|---|---|---|
| 版本列表 `method` | `content://<包名>.pclplugin` 的 `call(method, "")` → `Bundle.json` | 1.4.2 | 🧪 |
| 下载源 `detail_method` | `call(method, "<版本id>\n<option>")` → `Bundle.json` | 1.4.2 | 🧪 |
| 错误返回 | Bundle 里放 `error`，启动器原样显示 | 1.4.2 | 🧪 |

### 2.3 启动器 → 插件

| 接口 | 形式 | 自 | 状态 |
|---|---|---|---|
| 打开插件界面 | `getLaunchIntentForPackage(包名)` | 1.4.0 | ✅ |
| 启动任意应用 | 动作 `{"type":"app","package":"…"}` | 1.4.1 | ✅ |
| 打开应用信息页 | 动作 `{"type":"app_info","package":"…"}` | 1.4.1 | ✅ |

启动器**不会**加载插件 dex、不绑定 AIDL、不反向调用你 Activity 里的方法、不传任何内部对象。

### 2.4 行为契约

| 行为 | 说明 | 自 |
|---|---|---|
| 插件识别 | APK 内有 `assets/zplugin/plugin.json` 即认定 | 1.4.0 |
| 启用状态 | 停用不删文件，`pages.json` 不加载 | 1.4.0 |
| 升级重解包 | 插件 APK 的 **versionCode 变了**才重新解包 | 1.4.6 |
| 页面热加载 | 进首页重读 `pages.json`，无需重启 | 1.4.1 |
| 动作白名单 | 只执行 2.1 列出的 9 种动作，其余忽略 | 1.4.0 |

### 2.5 版本矩阵

| 启动器 | 新增的开放接口 |
|---|---|
| 1.4.0 | 清单、页面、基础积木与动作 |
| 1.4.1 | `launch`/`status`/`open`/`copy`/`install`/`app`/`app_info`、`sections`、`entries`、`primary`、热加载 |
| 1.4.2 | `version_collection`、ContentProvider 数据通道 |
| 1.4.3 | `entries[].display = content` |
| 1.4.4 | 下载源原生选择（数据契约不变） |
| 1.4.5 | `entries` 支持 `setup` 版块 |
| 1.4.6 | 升级重解包（行为修复） |

## 三、目录结构

```
assets/zplugin/
├── plugin.json      # 必需：清单
├── pages.json       # 可选：页面 / 版块接管 / 左栏入口
├── ui.json          # 可选：皮肤
├── files/           # 可选：要覆盖到游戏目录的文件树
└── res/             # 可选：页面里 icon 积木引用的图片
```

启动器判定「这是不是一个插件」的唯一标准：APK 里有没有 `assets/zplugin/plugin.json`。  
没有就拒收，并提示「不是有效的插件 APK」。

## 四、plugin.json

```json
{
  "name": "基岩版管理器",
  "version": "1.0.12",
  "author": "Zalith",
  "description": "管理、安装并启动 Minecraft 基岩版的独立应用。",
  "target": "launcher",
  "enabled": true,
  "entry": true,
  "label": "基岩版"
}
```

| 字段                       | 类型                           | 说明                                     |
| ------------------------ | ---------------------------- | -------------------------------------- |
| `name`                   | string                       | 插件名，列表与提示里用                            |
| `version`                | string                       | 展示用版本号；**升级判定不看它，看 APK 的 versionCode** |
| `author` / `description` | string                       | 展示                                     |
| `target`                 | `"minecraft"` | `"launcher"` | 装给谁                                    |
| `enabled`                | bool                         | 安装后是否立即启用，默认 `true`                    |
| `ui`                     | string                       | 皮肤标识；给了就按皮肤插件处理                        |
| `entry`                  | bool                         | 自带界面，允许没有 `files/`                     |
| `label`                  | string                       | 入口显示名（首页胶囊、插件列表「打开」）                   |



> `entry: true` 时，启动器调用 `getLaunchIntentForPackage` 打开你的 app，不读你的代码。

## 五、pages.json

顶层三个数组：`pages`（页面）、`sections`（整块接管某版块）、`entries`（往某版块左栏加一条）。

```json
{
  "pages":    [ { "id": "home", "title": "基岩版", "blocks": [ ... ] } ],
  "sections": [ { "section": "home", "left": "side", "content": "home" } ],
  "entries":  [ { "section": "download", "label": "下载基岩版", "page": "bedrock-download",
                  "position": "top", "display": "content" } ]
}
```

### 5.1 页面

| 字段       | 说明                                         |
| -------- | ------------------------------------------ |
| `id`     | 页面 id，`page` 动作与 `sections`/`entries` 靠它引用 |
| `title`  | 二级页标题                                      |
| `left`   | 字符串数组，给了就在左栏列出来（纯文本，不可点）                   |
| `blocks` | 积木数组，见下                                    |
| `footer` | 贴在底部的积木（不随滚动）                              |
| `layout` | 布局名，默认 `scroll`                            |

### 5.2 sections —— 整块接管

```json
{ "section": "home", "left": "side", "content": "home" }
```

`section` 取 `home` / `download` / `link` / `setup` / `other`。  
`left`、`content` 各指向一个页面 id，都可以省；省了就用启动器原来的。

> **只在插件模式生效**（首页选中了该插件）。`sections` 是**整块替换**：  
> 一旦接管了 `download`，这个版块的系统左栏就没了，`entries` 里的下载入口也不会显示。  
> 两者对同一版块互斥。

### 5.3 entries —— 往左栏加一条

```json
{ "section": "download", "label": "下载基岩版", "page": "bedrock-download",
  "position": "top", "display": "content" }
```

| 字段         | 说明                                           |
| ---------- | -------------------------------------------- |
| `section`  | 加到哪个版块的左栏                                    |
| `label`    | 条目文字                                         |
| `page`     | 点开显示哪一页                                      |
| `position` | `top` = 紧跟第一个系统条目（与原版游戏并列）；`end` = 追加到末尾（默认） |
| `display`  | `content` = 画在右侧主栏；`page` = push 二级页（默认）     |

`entries` 不受插件模式限制：装了就出现，点开就是插件那一页。

### 5.4 积木（blocks）

| `type`               | 关键字段                               | 说明                                         |
| -------------------- | ---------------------------------- | ------------------------------------------ |
| `title`              | `text`                             | 段落标题，加粗大一号                                 |
| `text`               | `text`、`muted`                     | 正文；`muted: true` 灰一点                       |
| `card`               | `title`、`blocks`                   | 卡片容器，可嵌套                                   |
| `row`                | `blocks`                           | 横排，子元素等宽                                   |
| `button`             | `text`、`action`、`primary`、`height` | 按钮；`primary: true` 为主按钮（高亮）                |
| `switch`             | `text`、`checked`、`action`          | 开关；状态只在本次停留期间有效                            |
| `divider`            | —                                  | 分割线                                        |
| `space`              | `height`                           | 竖向留白，单位 dp（0–200）                          |
| `status`             | `package`、`text`                   | 显示某应用的安装状态：`已安装 v1.21.90` / `未安装`；回到前台自动刷新 |
| `icon`               | `resource`、`size`                  | 引用 `assets/zplugin/res/` 里的图片              |
| `version_collection` | 见第六节                               | 插件供数据的版本列表，启动器用原生样式渲染                      |

### 5.5 动作（action）

| `type`     | 字段        | 行为                                              |
| ---------- | --------- | ----------------------------------------------- |
| `hint`     | `text`    | 底部弹一条提示（3 秒消失）                                  |
| `page`     | `id`      | 打开同一次 `pages.json` 里的另一页                        |
| `section`  | `value`   | 切到内置版块：`home`/`download`/`link`/`setup`/`other` |
| `launch`   | —         | 打开**本插件**的界面（拉起它自己的 Activity）                   |
| `open`     | `url`     | 用系统浏览器/商店打开链接                                   |
| `copy`     | `text`    | 复制到剪贴板并提示                                       |
| `install`  | —         | 让用户选一个本地 APK，交给系统安装器（不需要存储权限）                   |
| `app`      | `package` | 启动另一个已安装的应用                                     |
| `app_info` | `package` | 打开系统的「应用信息」页                                    |

## 六、数据通道（版本列表 / 网盘源）

页面是静态数据，拿不到「此刻有多少个版本」。需要动态数据时，用 `version_collection` 积木，  
由插件在自己的 app 里实现一个 **ContentProvider** 提供数据：

```xml
<provider
    android:name=".PluginProvider"
    android:authorities="${applicationId}.pclplugin"
    android:exported="true" />
```

启动器这样调用（`arg` 可以为空字符串）：

```kotlin
val bundle = contentResolver.call(
    Uri.parse("content://<插件包名>.pclplugin"), method, arg, null
)
val json  = bundle?.getString("json")     // 成功
val error = bundle?.getString("error")    // 失败，会原样显示给用户
```

### 5.1 积木声明

```json
{
  "type": "version_collection",
  "title": "版本列表",
  "method": "list",
  "detail_method": "details",
  "groups": ["正式版", "预览版"],
  "group_icons": { "正式版": "pcl_block_grass", "预览版": "pcl_block_command_block" },
  "options": [
    { "value": "v8a", "label": "ARM64" },
    { "value": "v7a", "label": "ARM32" }
  ]
}
```

### 5.2 `method`（列表）返回

```json
{ "items": [
  { "id": "1.21.90", "title": "1.21.90", "subtitle": "2025-06-10 · 620 MB",
    "group": "正式版", "latest": true }
] }
```

`id` 必需且唯一；`group` 对应 `groups` 里的名字；`latest: true` 会标「最新」。

### 5.3 `detail_method`（下载源）返回

调用时 `arg` 是两行文本：`"<版本 id>\n<option 的 value>"`。

```json
{ "items": [
  { "title": "夸克网盘", "url": "https://pan.quark.cn/s/xxxx", "password": "a1b2" },
  { "title": "123 云盘",  "url": "https://www.123pan.com/s/xxxx" }
] }
```

- `url` 必须是 `http://` 或 `https://`，否则该条被丢弃；
- 有 `password` 的条目在列表里显示「提取码 xxx」，选中时**自动复制**再打开链接；
- 返回空数组 = 无可用源，页面会显示「没有可用的下载源」。

> 启动器只发这两种调用、只渲染返回的条目。它不内置任何下载源，也不认识任何具体站点。

## 七、安装、启用、升级

| 行为      | 说明                                                                        |
| ------- | ------------------------------------------------------------------------- |
| 安装来源    | ① 系统里装了带 `assets/zplugin/` 的 APK，启动器自动识别；② 插件管理里「从文件选择 APK」               |
| 落盘位置    | `<外部 files>/plugins/<id>/`：`plugin.apk`、`meta.json`、`files/`、`pages.json` |
| 启用 / 停用 | 停用只改 `meta.json`，不删文件；`pages.json` 只在启用时加载                                |
| 升级      | 装了新版 APK 后，下次扫描发现 **versionCode 变了**就重新解包（页面、素材一起更新）                      |
| 页面刷新    | 进首页会重读一次 `pages.json`，一般不需要重启启动器                                          |

**升级时注意**：versionCode 一定要涨。只改 `plugin.json` 的 `version` 而 versionCode 不变，  
启动器判定为「同一个版本」，不会重新解包。

## 八、最小可运行示例

`assets/zplugin/plugin.json`

```json
{ "name": "示例插件", "version": "1.0.0", "target": "launcher",
  "entry": true, "label": "示例" }
```

`assets/zplugin/pages.json`

```json
{
  "sections": [ { "section": "home", "content": "home" } ],
  "pages": [ {
    "id": "home", "title": "示例",
    "blocks": [
      { "type": "title", "text": "示例插件" },
      { "type": "status", "package": "com.example.app", "text": "安装状态" },
      { "type": "space", "height": 8 },
      { "type": "button", "text": "启动", "primary": true,
        "action": { "type": "app", "package": "com.example.app" } },
      { "type": "button", "text": "打开插件界面", "action": { "type": "launch" } }
    ]
  } ]
}
```

打包时把这两个文件放进 `assets/zplugin/`，签名后安装即可：首页会出现「示例」胶囊，  
点开就是这一页。

## 九、排错表

| 症状                | 多半是                                                                         |
| ----------------- | --------------------------------------------------------------------------- |
| 导入时报「不是有效的插件 APK」 | APK 里没有 `assets/zplugin/plugin.json`（旧的资源包不算插件）                             |
| 装了但首页没胶囊          | `entry` 没写，或 `plugin.json` 的 `label/name` 为空，或插件被停用                         |
| 界面还是老样子           | 插件 APK 的 versionCode 没涨，启动器没重新解包；或该版块已被 `sections` 整块接管                     |
| 页面一片空白            | `sections`/`entries` 指向的 `page` id 在 `pages` 里不存在                           |
| 版本列表一直转圈          | Provider 的 `authorities` 不是 `<包名>.pclplugin`，或 `call` 返回的 Bundle 里没有 `json` |
| 提示「插件没有返回数据」      | Provider 返回了 null Bundle，或把错误写进了 `json` 而不是 `error`                         |
| 下载源列表为空           | `url` 不是 http/https，或返回结构不是 `{"items":[...]}`                               |

## 十、边界

- 启动器**不加载**插件的代码，插件也拿不到启动器的进程内数据；
- 页面能触发的动作就是上面那张表，没有文件读写、没有任意代码执行；
- 插件能做什么由它自己的 app 决定——启动器只负责「发现 + 拉起 + 渲染」。
