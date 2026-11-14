/*
 * Pixelforge Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 插件自定义页面的**渲染器**：把 `pages.json` 里的积木翻译成 PCL 的原生控件。
 *
 * 刻意全部用现成的 `PclCard` / `PclButton` / `PclSwitchItem` / `PclTextStyle`，
 * 而不是自己画一套 —— 这样插件页面和内置页面**长得一模一样**，
 * 换皮肤、换缩放、换语言都自动跟着走，不需要插件操心。
 */

package com.zalith.pcl.ui.pcl2.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zalith.pcl.ui.pcl2.LocalPclNav
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.PclSection
import com.zalith.pcl.ui.pcl2.PclSubPage
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclSwitchItem
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.components.PclListItemType
import com.zalith.pcl.ui.pcl2.components.PclSearchBox
import com.zalith.pcl.ui.pcl2.components.PclSelectDialog
import com.zalith.pcl.ui.pcl2.components.rememberPclRowChunks
import com.zalith.pcl.ui.pcl2.data.PclPluginAction
import com.zalith.pcl.ui.pcl2.data.PclPluginBlock
import com.zalith.pcl.game.plugin.ZlPluginManager
import com.zalith.pcl.ui.pcl2.data.PclPluginPageSpec
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclUiRuntime
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把一个插件页面包装成启动器的二级页面。
 *
 * @param all 同一份 `pages.json` 里的**全部**页面 —— `{"type":"page"}` 动作要靠它找目标。
 */
fun pclPluginSubPage(spec: PclPluginPageSpec): PclSubPage = PclSubPage(
    title = spec.title,
    leftLabels = spec.leftLabels,
    leftContent = if (spec.leftLabels.isEmpty()) null else ({ PclPluginLeft(spec.leftLabels) }),
    content = { PclPluginPageInline(spec) },
)

@Composable
private fun PclPluginLeft(labels: List<String>) {
    val p = pclPalette
    Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
        labels.forEach {
            Text(
                text = it,
                style = PclTextStyle.listTitle(p),
                modifier = Modifier.padding(vertical = 9.dp),
            )
        }
    }
}

/**
 * 把一页积木渲染到当前容器里。
 *
 * 两个地方用它：二级页（[pclPluginSubPage]）和**布局槽位**（插件把某个版块的
 * 左栏 / 内容区换成自己的页面，见 `PclUiTheme.layout`）。
 * 页面清单从 [PclUiRuntime.pages] 取 —— 那里是全局加载好的，不用每次读文件。
 */
@Composable
fun PclPluginPageInline(spec: PclPluginPageSpec) {
    val all = PclUiRuntime.pages
    val nav = LocalPclNav.current
    val context = LocalContext.current
    // 「安装本地 APK」是通用动作：挑文件 → 复制进缓存 → 交给系统安装器。
    // 走系统文件选择器，不需要任何存储权限。
    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) PclPluginApk.install(context, uri)
    }
    val onInstall = { pickApk.launch(arrayOf("*/*")) }
    if (spec.layout == "panel" && spec.footerBlocks.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PclPluginBlocksView(
                blocks = spec.blocks,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                all = all,
                pluginId = spec.pluginId,
                onInstall = onInstall,
                onPush = nav::push,
                onSection = nav::go,
            )
            PclPluginBlocksView(
                blocks = spec.footerBlocks,
                modifier = Modifier.fillMaxWidth(),
                all = all,
                pluginId = spec.pluginId,
                onInstall = onInstall,
                onPush = nav::push,
                onSection = nav::go,
            )
        }
    } else {
        PclPluginBlocksView(
            blocks = spec.blocks + spec.footerBlocks,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            all = all,
            pluginId = spec.pluginId,
            onInstall = onInstall,
            onPush = nav::push,
            onSection = nav::go,
        )
    }
}

@Composable
private fun PclPluginBlocksView(
    blocks: List<PclPluginBlock>,
    modifier: Modifier,
    all: List<PclPluginPageSpec>,
    pluginId: String,
    onInstall: () -> Unit,
    onPush: (PclSubPage) -> Unit,
    onSection: (PclSection) -> Unit,
    horizontalAlignment: androidx.compose.ui.Alignment.Horizontal = androidx.compose.ui.Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
) {
    Column(modifier, verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment) {
        blocks.forEach {
            PclPluginBlockView(
                block = it,
                all = all,
                pluginId = pluginId,
                onInstall = onInstall,
                onPush = onPush,
                onSection = onSection,
            )
        }
    }
}

@Composable
private fun PclPluginBlockView(
    block: PclPluginBlock,
    all: List<PclPluginPageSpec>,
    pluginId: String,
    onInstall: () -> Unit,
    onPush: (PclSubPage) -> Unit,
    onSection: (PclSection) -> Unit,
) {
    val p = pclPalette
    when (block) {
        is PclPluginBlock.Title -> Text(
            text = block.text,
            style = PclTextStyle.listTitle(p).copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.color1,
            ),
        )

        is PclPluginBlock.Text -> Text(
            text = block.text,
            style = PclTextStyle.base(p).copy(color = if (block.muted) p.gray2 else p.color1),
        )

        is PclPluginBlock.Card -> PclCard(
            title = block.title,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.blocks.forEach { PclPluginBlockView(it, all, pluginId, onInstall, onPush, onSection) }
            }
        }

        is PclPluginBlock.Row -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.blocks.forEach { child ->
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    PclPluginBlockView(child, all, pluginId, onInstall, onPush, onSection)
                }
            }
        }

        is PclPluginBlock.Button -> PclButton(
            text = block.text,
            colorState = if (block.primary) PclColorState.Highlight else PclColorState.Normal,
            height = block.height?.dp ?: PclDimens.buttonHeight,
            modifier = Modifier.fillMaxWidth(),
            onClick = { runPclPluginAction(block.action, all, pluginId, onInstall, onPush, onSection) },
        )

        is PclPluginBlock.Switch -> {
            // 状态只活在这一次页面停留期间：插件没有落盘能力，
            // 需要记住的偏好应该让插件自己写进它自己的目录（files/ 下）。
            var checked by remember(block.text) { mutableStateOf(block.checked) }
            PclSwitchItem(
                title = block.text,
                checked = checked,
                modifier = Modifier.fillMaxWidth(),
                onCheckedChange = {
                    checked = it
                    runPclPluginAction(block.action, all, pluginId, onInstall, onPush, onSection)
                },
            )
        }

        PclPluginBlock.Divider -> Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(p.color6),
        )

        is PclPluginBlock.Space -> Spacer(Modifier.height(block.height.dp))

        // 应用安装状态：从安装器/商店回来会自己刷新
        is PclPluginBlock.Status -> {
            val ctx = LocalContext.current
            val owner = LocalLifecycleOwner.current
            var value by remember(block.packageName) {
                mutableStateOf(packageStatus(ctx, block.packageName))
            }
            DisposableEffect(owner, block.packageName) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        value = packageStatus(ctx, block.packageName)
                    }
                }
                owner.lifecycle.addObserver(observer)
                onDispose { owner.lifecycle.removeObserver(observer) }
            }
            Text(
                text = listOfNotNull(block.title, value).joinToString(" · "),
                style = PclTextStyle.base(p).copy(
                    color = if (value.startsWith("已安装")) p.color3 else p.gray2,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }

        is PclPluginBlock.Icon -> genericPluginIconResource(block.resource)?.let { resource ->
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Image(
                    painter = painterResource(resource),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(block.size.dp),
                )
            }
        }

        is PclPluginBlock.VersionCollection -> PclPluginVersionCollection(
            block = block,
            pluginId = pluginId,
        )
    }
}

private data class PluginVersionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val group: String,
    val latest: Boolean,
)

private data class PluginDownloadItem(val title: String, val url: String, val password: String)

@Composable
private fun PclPluginVersionCollection(block: PclPluginBlock.VersionCollection, pluginId: String) {
    val context = LocalContext.current
    val plugin = remember(pluginId) {
        runCatching { ZlPluginManager.list(context).firstOrNull { it.id == pluginId } }.getOrNull()
    }
    var items by remember(block, plugin?.pkg) { mutableStateOf<List<PluginVersionItem>>(emptyList()) }
    var sources by remember { mutableStateOf<List<PluginDownloadItem>>(emptyList()) }
    var selected by remember { mutableStateOf<PluginVersionItem?>(null) }
    var selectedOption by remember(block) { mutableStateOf(block.options.firstOrNull()?.first.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var loading by remember(block, plugin?.pkg) { mutableStateOf(true) }
    var error by remember(block, plugin?.pkg) { mutableStateOf<String?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var loadingDetails by remember { mutableStateOf(false) }
    var detailReloadKey by remember { mutableStateOf(0) }
    var reloadKey by remember(block, plugin?.pkg) { mutableStateOf(0) }
    fun chooseVersion(item: PluginVersionItem) {
        selected = item
        sources = emptyList()
        detailError = null
        loadingDetails = true
    }
    val groupLogos = remember(block.groupIcons) {
        block.groupIcons.mapNotNull { (group, resourceName) ->
            genericPluginIconResource(resourceName)?.let { group to it }
        }.toMap()
    }

    suspend fun request(method: String, argument: String = ""): String = withContext(Dispatchers.IO) {
        val pkg = plugin?.pkg?.ifBlank { plugin.id } ?: throw IllegalStateException("插件数据提供方不可用。")
        val uri = Uri.parse("content://$pkg.pclplugin")
        context.contentResolver.call(uri, method, argument, null)
            ?.let { result ->
                result.getString("error")?.let { throw IllegalStateException(it) }
                result.getString("json") ?: throw IllegalStateException("插件没有返回数据。")
            } ?: throw IllegalStateException("插件没有返回数据。")
    }

    LaunchedEffect(block, plugin?.pkg, reloadKey) {
        if (plugin == null) {
            error = "数据提供方不可用。"
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching {
            JSONObject(request(block.listMethod)).optJSONArray("items").orEmptyJsonArray().mapNotNull { value ->
                val item = value as? JSONObject ?: return@mapNotNull null
                val id = item.optString("id").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                PluginVersionItem(
                    id = id,
                    title = item.optString("title", id).ifBlank { id },
                    subtitle = item.optString("subtitle"),
                    group = item.optString("group"),
                    latest = item.optBoolean("latest"),
                )
            }
        }.onSuccess { items = it }
            .onFailure { error = it.message ?: "无法加载版本列表。" }
        loading = false
    }

    val visible = remember(items, query) {
        if (query.isBlank()) items else items.filter {
            it.title.contains(query.trim(), true) || it.subtitle.contains(query.trim(), true)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (selected == null && !loading && error == null) {
            PclSearchBox(
                value = query,
                modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                hint = "搜索版本号",
                onValueChange = { query = it },
            )
        }
        if (block.options.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                block.options.forEach { (value, label) ->
                    PclButton(
                        text = label,
                        colorState = if (selectedOption == value) PclColorState.Highlight else PclColorState.Normal,
                        onClick = { selectedOption = value; sources = emptyList(); detailError = null },
                    )
                }
            }
        }
        val selectedVersion = selected
        if (selectedVersion != null) {
            LaunchedEffect(selectedVersion.id, selectedOption, detailReloadKey) {
                loadingDetails = true
                detailError = null
                runCatching {
                    val result = JSONObject(request(block.detailMethod, "${selectedVersion.id}\n$selectedOption"))
                    result.optJSONArray("items").orEmptyJsonArray().mapNotNull { value ->
                        val item = value as? JSONObject ?: return@mapNotNull null
                        val url = item.optString("url").trim()
                        if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
                        PluginDownloadItem(
                            title = item.optString("title", "下载源").ifBlank { "下载源" },
                            url = url,
                            password = item.optString("password"),
                        )
                    }
                }.onSuccess { sources = it }
                    .onFailure { detailError = it.message ?: "无法加载下载源。" }
                loadingDetails = false
            }
            when {
                loadingDetails -> PclCard(title = "选择下载源（${selectedVersion.title}）", modifier = Modifier.fillMaxWidth()) {
                    Text("正在加载下载源…", style = PclTextStyle.base(pclPalette).copy(color = pclPalette.gray2))
                }

                detailError != null -> PclCard(title = "选择下载源（${selectedVersion.title}）", modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(detailError.orEmpty(), style = PclTextStyle.base(pclPalette).copy(color = pclPalette.gray2))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PclButton(text = "重试", onClick = { detailReloadKey++ })
                            PclButton(text = "返回版本列表", onClick = { selected = null; detailError = null })
                        }
                    }
                }

                sources.isEmpty() -> PclCard(title = "选择下载源（${selectedVersion.title}）", modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("没有可用的下载源。", style = PclTextStyle.base(pclPalette).copy(color = pclPalette.gray2))
                        PclButton(text = "返回版本列表", onClick = { selected = null })
                    }
                }

                else -> PclSelectDialog(
                    title = "选择下载源（${selectedVersion.title}）",
                    options = sources.map { source ->
                        val label = if (source.password.isBlank()) source.title else "${source.title} · 提取码 ${source.password}"
                        source.url to label
                    },
                    current = sources.firstOrNull()?.url,
                    onSelect = { url ->
                        sources.firstOrNull { it.url == url }?.let { source ->
                            if (source.password.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("提取码", source.password))
                                PclHintHost.push("提取码已复制。", PclHintTheme.Green)
                            }
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                            }.onFailure { PclHintHost.push("没有可打开此链接的应用。", PclHintTheme.Red) }
                        }
                        selected = null
                        sources = emptyList()
                        detailError = null
                    },
                    onDismiss = {
                        selected = null
                        sources = emptyList()
                        detailError = null
                    },
                )
            }
        } else if (loading) {
            PclCard(title = block.title, modifier = Modifier.fillMaxWidth()) {
                Text("正在加载…", style = PclTextStyle.base(pclPalette).copy(color = pclPalette.gray2))
            }
        } else if (error != null) {
            PclCard(title = block.title, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error.orEmpty(), style = PclTextStyle.base(pclPalette).copy(color = pclPalette.gray2))
                    PclButton(text = "重试", onClick = {
                        loading = true
                        error = null
                        reloadKey++
                    })
                }
            }
        } else if (query.isNotBlank()) {
            PclPluginVersionGroup(
                title = "搜索结果 (${visible.size})",
                values = visible,
                logoRes = block.groups.firstNotNullOfOrNull { groupLogos[it] },
            ) { item ->
                chooseVersion(item)
            }
        } else {
            val latest = visible.filter { it.latest }
            if (visible.isEmpty()) {
                PclCard(title = block.title, modifier = Modifier.fillMaxWidth()) {
                    Text("没有可显示的版本。", style = PclTextStyle.base(pclPalette).copy(color = pclPalette.gray2))
                }
            }
            if (latest.isNotEmpty()) {
                PclPluginVersionGroup(
                    title = "最新版本",
                    values = latest,
                    logoRes = block.groups.firstNotNullOfOrNull { groupLogos[it] },
                ) { item ->
                    chooseVersion(item)
                }
            }
            block.groups.forEach { group ->
                val values = visible.filter { !it.latest && it.group == group }
                if (values.isNotEmpty()) {
                    PclPluginVersionGroup(title = "$group (${values.size})", values = values, collapsed = true, logoRes = groupLogos[group]) { item ->
                        chooseVersion(item)
                    }
                }
            }
            val ungrouped = visible.filter { !it.latest && it.group !in block.groups }
            if (ungrouped.isNotEmpty()) {
                PclPluginVersionGroup(title = "其他 (${ungrouped.size})", values = ungrouped, collapsed = true) { item ->
                    chooseVersion(item)
                }
            }
        }

    }
}

@Composable
private fun PclPluginVersionGroup(
    title: String,
    values: List<PluginVersionItem>,
    collapsed: Boolean = false,
    logoRes: Int? = null,
    onPick: (PluginVersionItem) -> Unit,
) {
    val collapsedState = remember(title) { mutableStateOf(collapsed) }
    val chunks = rememberPclRowChunks(total = values.size, collapsed = collapsedState)
    PclCard(
        title = title,
        modifier = Modifier.fillMaxWidth().padding(top = if (collapsed) 0.dp else 15.dp, bottom = 15.dp),
        collapsible = collapsed,
        initiallyCollapsed = collapsed,
        swapLogoRight = collapsed,
        collapsedState = if (collapsed) collapsedState else null,
        onToggle = if (collapsed) chunks.onToggle else null,
        contentPadding = PaddingValues(
            start = 20.dp,
            top = PclDimens.cardContentTop,
            end = 18.dp,
            bottom = 18.dp,
        ),
    ) {
        for (index in 0 until chunks.shown) {
            val item = values[index]
            PclListItem(
                title = item.title,
                info = item.subtitle.takeIf { it.isNotBlank() },
                logoContent = logoRes?.let { res ->
                    {
                        Image(
                            painter = painterResource(res),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
                type = PclListItemType.Clickable,
                height = 42.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPick(item) },
            )
        }
    }
}

private fun JSONArray?.orEmptyJsonArray(): List<Any?> =
    if (this == null) emptyList() else (0 until length()).map { opt(it) }

private fun genericPluginIconResource(name: String): Int? = when (name) {
    "pcl_block_grass" -> R.drawable.pcl_block_grass
    "pcl_block_command_block" -> R.drawable.pcl_block_command_block
    "pcl_block_cobblestone" -> R.drawable.pcl_block_cobblestone
    "pcl_block_gold_block" -> R.drawable.pcl_block_gold_block
    else -> null
}

private fun runPclPluginAction(
    action: PclPluginAction?,
    all: List<PclPluginPageSpec>,
    pluginId: String,
    onInstall: () -> Unit,
    onPush: (PclSubPage) -> Unit,
    onSection: (PclSection) -> Unit,
) {
    when (action) {
        null -> Unit
        is PclPluginAction.Hint -> PclHintHost.push(action.text, PclHintTheme.Blue)
        is PclPluginAction.Page -> all.firstOrNull { it.id == action.id }
            ?.let { onPush(pclPluginSubPage(it)) }
        is PclPluginAction.Section -> when (action.value.lowercase()) {
            "home" -> onSection(PclSection.Home)
            "download" -> onSection(PclSection.Download)
            "link" -> onSection(PclSection.Link)
            "setup" -> onSection(PclSection.Setup)
            "other" -> onSection(PclSection.Other)
            else -> Unit
        }

        // 打开插件自己的界面：页面是数据，真正的 UI 在插件的 Activity 里，
        // 启动器只负责把它拉到前台，不参与它做什么。
        is PclPluginAction.Launch -> {
            val ctx = PclUiRuntime.appContext
            val plugin = ctx?.let { c ->
                runCatching { ZlPluginManager.list(c).firstOrNull { it.id == pluginId } }.getOrNull()
            }
            if (ctx == null || plugin == null || !ZlPluginManager.launch(ctx, plugin)) {
                PclHintHost.push("没有找到这个插件的界面，重新装一次插件 APK 再试。", PclHintTheme.Red)
            }
        }

        is PclPluginAction.OpenUrl -> openUrl(PclUiRuntime.appContext, action.url)
        is PclPluginAction.Copy -> copyText(PclUiRuntime.appContext, action.text)
        is PclPluginAction.InstallApk -> onInstall()
        is PclPluginAction.LaunchApp -> launchPackage(PclUiRuntime.appContext, action.packageName)
        is PclPluginAction.AppInfo -> openAppInfo(PclUiRuntime.appContext, action.packageName)
    }
}

/* ---------------------------------------------------------------- 通用动作 */

/** 包名 → 「已安装 v1.2.3」/「未安装」。 */
private fun packageStatus(context: Context, pkg: String): String = runCatching {
    val info = context.packageManager.getPackageInfo(pkg, 0)
    "已安装 v" + (info.versionName?.takeIf { it.isNotBlank() } ?: "未知版本")
}.getOrDefault("未安装")

private fun openUrl(context: Context?, url: String) {
    if (context == null) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { PclHintHost.push("没有可以打开这个链接的应用。", PclHintTheme.Red) }
}

private fun copyText(context: Context?, text: String) {
    if (context == null) return
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("插件", text))
    }.onSuccess { PclHintHost.push("已复制：$text", PclHintTheme.Green) }
        .onFailure { PclHintHost.push("复制失败。", PclHintTheme.Red) }
}

private fun openAppInfo(context: Context?, pkg: String) {
    if (context == null) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { PclHintHost.push("打不开这个应用的信息页。", PclHintTheme.Red) }
}

private fun launchPackage(context: Context?, pkg: String) {
    if (context == null) return
    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
    if (intent == null) {
        PclHintHost.push("没有安装 $pkg。", PclHintTheme.Red)
        return
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { PclHintHost.push("启动失败。", PclHintTheme.Red) }
}

/**
 * 把用户选的 APK 交给系统安装器。
 *
 * 先复制进缓存：`content://` 的读权限是临时的，安装器在另一个进程里读不到。
 */
private object PclPluginApk {
    fun install(context: Context, uri: Uri) {
        val file = runCatching {
            val dir = File(context.cacheDir, "plugin-install").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "plugin.apk")
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out
        }.getOrNull()

        if (file == null || file.length() == 0L) {
            PclHintHost.push("读不到这个安装包，换一个再试。", PclHintTheme.Red)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            PclHintHost.push("需要先允许「安装未知应用」，授权后重新点一次。", PclHintTheme.Yellow)
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        runCatching {
            val apk = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apk, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { PclHintHost.push("没有可用的安装器。", PclHintTheme.Red) }
    }
}
