/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「插件自定义页面」协议 —— 启动器侧的第二套扩展点。
 *
 * 第一套是 `ui.json`（皮肤：改颜色、改材质），它只能动**已有**界面的长相。
 * 这一套解决的是另一个问题：插件想往启动器里**加东西**。
 *
 * 插件在自己的 APK 里放 `assets/zplugin/pages.json`：
 * ```json
 * {
 *   "pages": [
 *     {
 *       "id": "hello",
 *       "title": "示例页面",
 *       "left": ["说明", "动作"],
 *       "blocks": [
 *         { "type": "title", "text": "你好" },
 *         { "type": "text",  "text": "这一页是插件加的。", "muted": true },
 *         { "type": "card",  "title": "卡片", "blocks": [
 *             { "type": "text", "text": "卡片里的内容" },
 *             { "type": "button", "text": "点我",
 *               "action": { "type": "hint", "text": "点到了" } }
 *         ]}
 *       ]
 *     }
 *   ]
 * }
 * ```
 *
 * 为什么是「积木」而不是让插件写 Compose：
 * 插件是数据包，启动器从不加载它的 dex（见 `ZlPluginManager` 的说明）。
 * 所以插件能表达的只有**被启动器认识的积木**；加一种新积木要改启动器，
 * 但改色、改内容、拼页面都不用。
 *
 * 安全边界：积木只有「显示」和「跳转 / 提示」两类动作，没有文件读写、
 * 没有任意代码执行 —— 插件拿不到比它本来就能做的更多东西。
 */

package com.zalith.pcl.ui.pcl2.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/** 插件的一个页面。 */
data class PclPluginPageSpec(
    val id: String,
    val title: String,
    /** 左栏条目（可选）；为空时这一页没有左栏。 */
    val leftLabels: List<String> = emptyList(),
    val blocks: List<PclPluginBlock> = emptyList(),
    val layout: String = "scroll",
    val footerBlocks: List<PclPluginBlock> = emptyList(),
    /**
     * 这一页是哪个插件给的（[ZlPlugin] 的 id）。
     *
     * `{"type":"launch"}` 要靠它找到插件自己的 app —— 页面本身不知道谁写的，
     * 只能由加载方标上去。
     */
    val pluginId: String = "",
)

/** 页面内容的基本单元。 */
sealed interface PclPluginBlock {
    /** 段落标题（比正文大一号、加粗）。 */
    data class Title(val text: String) : PclPluginBlock

    /** 正文。[muted] = 次要信息（灰一点）。 */
    data class Text(val text: String, val muted: Boolean = false) : PclPluginBlock

    /** 卡片容器，可以嵌套任意积木。 */
    data class Card(val title: String?, val blocks: List<PclPluginBlock>) : PclPluginBlock

    /** 横排。 */
    data class Row(val blocks: List<PclPluginBlock>) : PclPluginBlock

    /** 按钮。[primary] = 主按钮（高亮色），用来表达「启动游戏」这类主要动作。 */
    data class Button(
        val text: String,
        val action: PclPluginAction?,
        val primary: Boolean = false,
        val height: Int? = null,
    ) : PclPluginBlock

    data class Icon(val resource: String, val size: Int = 48) : PclPluginBlock

    /** 开关。状态由插件页面自己保存（运行期），不落盘。 */
    data class Switch(
        val text: String,
        val checked: Boolean,
        val action: PclPluginAction?,
    ) : PclPluginBlock

    /** 分割线。 */
    data object Divider : PclPluginBlock

    /** 竖向留白，[height] 单位 dp（0..200）。 */
    data class Space(val height: Int) : PclPluginBlock

    /**
     * 某个应用的安装状态：`已安装 v1.21.90` / `未安装`。
     *
     * 只认包名，不认它是谁 —— 插件想让启动器显示什么应用的版本，就在这里写哪个包名。
     * 从系统安装器/商店回到启动器会自动刷新。
     */
    data class Status(
        val packageName: String,
        /** 前面那截标题；为空就只显示版本。 */
        val title: String? = null,
    ) : PclPluginBlock

    /** A plugin-backed version collection rendered with native launcher controls. */
    data class VersionCollection(
        val title: String,
        val listMethod: String,
        val detailMethod: String,
        val groups: List<String>,
        val groupIcons: Map<String, String>,
        val options: List<Pair<String, String>>,
    ) : PclPluginBlock
}

/** 积木能触发的动作。 */
sealed interface PclPluginAction {
    /** 弹一条底部提示。 */
    data class Hint(val text: String) : PclPluginAction

    /** 打开另一个插件页面（同一次 pages.json 里的 id）。 */
    data class Page(val id: String) : PclPluginAction

    /** 切到启动器的内置版块（`home` / `download` / `link` / `setup` / `other`）。 */
    data class Section(val value: String) : PclPluginAction

    /**
     * 打开这个插件自己的界面（它本身是一个装好的 app）。
     *
     * 页面是**数据**，只能表达「跳到哪一页」；真正要干活的那套 UI 在插件自己的
     * Activity 里。这个动作就是两者之间的门：启动器只负责把插件拉到前台。
     */
    data object Launch : PclPluginAction

    /** 打开一个网址（交给系统的浏览器/应用商店）。 */
    data class OpenUrl(val url: String) : PclPluginAction

    /** 复制一段文字到剪贴板（提取码这类）。 */
    data class Copy(val text: String) : PclPluginAction

    /** 让用户挑一个本地 APK，交给系统安装器（不需要存储权限）。 */
    data object InstallApk : PclPluginAction

    /** 启动另一个已安装的应用。 */
    data class LaunchApp(val packageName: String) : PclPluginAction

    /** 打开系统的「应用信息」页（版本、权限、卸载都在那儿）。 */
    data class AppInfo(val packageName: String) : PclPluginAction
}

/* ---------------------------------------------------------------- 解析 */

private data class ActionJson(
    @SerializedName("type") val type: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("package") val pkg: String? = null,
)

private data class OptionJson(
    @SerializedName("value") val value: String? = null,
    @SerializedName("label") val label: String? = null,
)

private data class BlockJson(
    @SerializedName("type") val type: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("muted") val muted: Boolean? = null,
    @SerializedName("checked") val checked: Boolean? = null,
    @SerializedName("primary") val primary: Boolean? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("blocks") val blocks: List<BlockJson>? = null,
    @SerializedName("footer") val footer: List<BlockJson>? = null,
    @SerializedName("layout") val layout: String? = null,
    @SerializedName("action") val action: ActionJson? = null,
    @SerializedName("package") val pkg: String? = null,
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("size") val size: Int? = null,
    @SerializedName("method") val method: String? = null,
    @SerializedName("detail_method") val detailMethod: String? = null,
    @SerializedName("groups") val groups: List<String>? = null,
    @SerializedName("group_icons") val groupIcons: Map<String, String>? = null,
    @SerializedName("options") val options: List<OptionJson>? = null,
)

/**
 * 插件往某个版块的左栏**追加**一条入口。
 *
 * 与 `sections` 的区别：sections 是「整块接管」，这里是「排在系统条目后面加一条」。
 */
data class PclPluginEntry(
    val pluginId: String,
    val section: String,
    val label: String,
    val page: String,
    /**
     * 排在哪：`top` = 紧跟版块的第一个系统条目（比如「原版游戏」后面），
     * `end` = 追加到最后（默认）。
     */
    val position: String = "end",
    val display: String = "page",
)

/** 一个版块的接管声明：左栏和内容区各指向一个页面 id（都可以缺）。 */
data class PclPluginSection(
    val left: String? = null,
    val content: String? = null,
)

private data class PageJson(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("left") val left: List<String>? = null,
    @SerializedName("blocks") val blocks: List<BlockJson>? = null,
    @SerializedName("layout") val layout: String? = null,
    @SerializedName("footer") val footer: List<BlockJson>? = null,
)

private data class SectionJson(
    @SerializedName("section") val section: String? = null,
    @SerializedName("left") val left: String? = null,
    @SerializedName("content") val content: String? = null,
)

private data class EntryJson(
    @SerializedName("section") val section: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("page") val page: String? = null,
    @SerializedName("position") val position: String? = null,
    @SerializedName("display") val display: String? = null,
)

private data class PagesJson(
    @SerializedName("pages") val pages: List<PageJson>? = null,
    @SerializedName("sections") val sections: List<SectionJson>? = null,
    @SerializedName("entries") val entries: List<EntryJson>? = null,
)

private val pagesGson = Gson()

/** 单个页面允许的积木总数，防止插件写出一棵把界面撑爆的树。 */
private const val MAX_BLOCKS = 400

/** 解析 `pages.json`；解析失败返回空表（插件写错不该让启动器崩）。 */
fun parsePclPluginPages(json: String, pluginId: String = ""): List<PclPluginPageSpec> {
    val raw = runCatching { pagesGson.fromJson(json, PagesJson::class.java) }.getOrNull()
    val pages = raw?.pages ?: return emptyList()
    var budget = MAX_BLOCKS
    fun parseBlocks(source: List<BlockJson>?): List<PclPluginBlock> = source.orEmpty().mapNotNull { block ->
        if (budget <= 0) return@mapNotNull null
        budget--
        block.toBlock()
    }
    return pages.mapIndexedNotNull { index, page ->
        val title = page.title?.trim().takeUnless { it.isNullOrBlank() } ?: return@mapIndexedNotNull null
        val id = page.id?.trim().takeUnless { it.isNullOrBlank() } ?: "page-$index"
        PclPluginPageSpec(
            id = id,
            title = title,
            pluginId = pluginId,
            leftLabels = page.left?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            blocks = parseBlocks(page.blocks),
            layout = page.layout?.trim()?.lowercase()?.takeIf { it == "panel" } ?: "scroll",
            footerBlocks = parseBlocks(page.footer),
        )
    }
}

/**
 * 解析 `pages.json` 顶层的 `sections`：
 *
 * ```json
 * "sections": [ { "section": "download", "left": "dl-left", "content": "dl-main" } ]
 * ```
 *
 * 含义是「这个版块由我接管」。启动器只管按声明的 id 去渲染，不关心版块里是什么内容。
 */
fun parsePclPluginSections(json: String): Map<String, PclPluginSection> {
    val raw = runCatching { pagesGson.fromJson(json, PagesJson::class.java) }.getOrNull()
        ?: return emptyMap()
    return raw.sections.orEmpty().mapNotNull { item ->
        val name = item.section?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val left = item.left?.trim()?.takeIf { it.isNotEmpty() }
        val content = item.content?.trim()?.takeIf { it.isNotEmpty() }
        if (left == null && content == null) return@mapNotNull null
        name to PclPluginSection(left = left, content = content)
    }.toMap()
}

/**
 * 解析 `pages.json` 顶层的 `entries`：
 *
 * ```json
 * "entries": [ { "section": "download", "label": "插件入口", "page": "download" } ]
 * ```
 *
 * 启动器把它排在对应版块左栏的系统条目后面。
 */
fun parsePclPluginEntries(json: String, pluginId: String): List<PclPluginEntry> {
    val raw = runCatching { pagesGson.fromJson(json, PagesJson::class.java) }.getOrNull()
        ?: return emptyList()
    return raw.entries.orEmpty().mapNotNull { item ->
        val section = item.section?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val label = item.label?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val page = item.page?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        PclPluginEntry(
            pluginId = pluginId,
            section = section,
            label = label,
            page = page,
            position = item.position?.trim()?.lowercase()?.takeIf { it == "top" } ?: "end",
            display = item.display?.trim()?.lowercase()?.takeIf { it == "content" } ?: "page",
        )
    }
}

private fun BlockJson.toBlock(): PclPluginBlock? = when (type?.trim()?.lowercase()) {
    "title", "heading" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginBlock.Title(it) }
    "text", "paragraph" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let {
        PclPluginBlock.Text(it, muted = muted == true)
    }
    "card" -> PclPluginBlock.Card(
        title = title?.trim()?.takeIf { it.isNotEmpty() },
        blocks = blocks.orEmpty().mapNotNull { it.toBlock() },
    )
    "row" -> PclPluginBlock.Row(blocks.orEmpty().mapNotNull { it.toBlock() })
    "button" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let {
        PclPluginBlock.Button(it, action?.toAction(), primary == true, height?.coerceIn(32, 72))
    }
    "switch", "toggle" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let {
        PclPluginBlock.Switch(it, checked == true, action?.toAction())
    }
    "divider", "line" -> PclPluginBlock.Divider
    "space", "gap" -> PclPluginBlock.Space((height ?: 12).coerceIn(0, 200))
    "status", "package_info" -> pkg?.trim()?.takeIf { it.isNotEmpty() }?.let {
        PclPluginBlock.Status(packageName = it, title = text?.trim()?.takeIf { t -> t.isNotEmpty() })
    }
    "icon" -> resource?.trim()?.takeIf { it.isNotEmpty() }?.let {
        PclPluginBlock.Icon(it, (size ?: 48).coerceIn(16, 128))
    }
    "version_collection" -> {
        val method = method?.trim()?.takeIf { it.isNotEmpty() }
        val detail = detailMethod?.trim()?.takeIf { it.isNotEmpty() }
        if (method == null || detail == null) null else PclPluginBlock.VersionCollection(
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: "版本列表",
            listMethod = method,
            detailMethod = detail,
            groups = groups.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            groupIcons = groupIcons.orEmpty().mapNotNull { (name, resource) ->
                val key = name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val value = resource.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                key to value
            }.toMap(),
            options = options.orEmpty().mapNotNull { option ->
                val value = option.value?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val label = option.label?.trim()?.takeIf { it.isNotEmpty() } ?: value
                value to label
            },
        )
    }
    else -> null
}

private fun ActionJson.toAction(): PclPluginAction? = when (type?.trim()?.lowercase()) {
    "hint", "toast" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.Hint(it) }
    "page" -> id?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.Page(it) }
    "section" -> value?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.Section(it) }
    "launch" -> PclPluginAction.Launch
    "install" -> PclPluginAction.InstallApk
    // 带 url 的 open 是「开网页」，不带 url 的 open 才是「开插件自己的界面」
    "open", "url" -> url?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.OpenUrl(it) }
        ?: PclPluginAction.Launch
    "copy" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.Copy(it) }
    "app", "launch_app" -> pkg?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.LaunchApp(it) }
    "app_info", "settings" -> pkg?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.AppInfo(it) }
    else -> null
}
