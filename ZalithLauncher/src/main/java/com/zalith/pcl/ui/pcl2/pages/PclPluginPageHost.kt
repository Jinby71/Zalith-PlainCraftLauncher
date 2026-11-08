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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zalith.pcl.ui.pcl2.LocalPclNav
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.PclSection
import com.zalith.pcl.ui.pcl2.PclSubPage
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclSwitchItem
import com.zalith.pcl.ui.pcl2.data.PclPluginAction
import com.zalith.pcl.ui.pcl2.data.PclPluginBlock
import com.zalith.pcl.game.plugin.ZlPluginManager
import com.zalith.pcl.ui.pcl2.data.PclPluginPageSpec
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.PclUiRuntime
import com.zalith.pcl.ui.pcl2.theme.pclPalette

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        spec.blocks.forEach {
            PclPluginBlockView(
                block = it,
                all = all,
                pluginId = spec.pluginId,
                onInstall = { pickApk.launch(arrayOf("*/*")) },
                onPush = nav::push,
                onSection = nav::go,
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
    }
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
