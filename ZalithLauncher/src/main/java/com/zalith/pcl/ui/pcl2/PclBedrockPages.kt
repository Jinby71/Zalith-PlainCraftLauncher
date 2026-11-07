/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「基岩版」页 —— PCL2 `PageLaunchLeft.xaml` 顶部那两枚 `Java 版 / 基岩版` 胶囊的落地。
 *
 * 启动器这边只做**它自己能做**的四件事：
 *   1. 认出已经装好的 Minecraft 基岩版（包名 + 版本号）
 *   2. 启动它
 *   3. 跳官方商店
 *   4. 选本地 APK 交给系统安装器
 *
 * 版本列表、网盘下载那些属于**插件**的事：基岩版管理器是一个独立 APK，它通过
 * `plugin.json` 的 `entry` 声明自己带界面，这里只负责给它一个入口，不写死它的包名。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zalith.pcl.game.plugin.ZlPlugin
import com.zalith.pcl.game.plugin.ZlPluginManager
import com.zalith.pcl.setting.launcherMMKV
import com.zalith.pcl.ui.pcl2.components.PclBottomSpacer
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclPillButton
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 首页的两种版本类型（PCL2 左栏顶部的 `PanTypeOne`）。 */
enum class PclEdition(val key: String, val label: String) {
    Java("java", "Java 版"),
    Bedrock("bedrock", "基岩版"),
}

/**
 * 当前选中的版本类型。
 *
 * 左右两栏都要读它，所以放在文件级单例里 —— `nav.push()` 进出二级页、切版块
 * 都会让组合重建，`remember` 保不住；落盘在 MMKV，重启后还是上次那一页。
 */
object PclEditionState {
    private const val MMKV_KEY = "pcl2.launch.edition"
    private var loaded = false

    var edition by mutableStateOf(PclEdition.Java)
        private set

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val saved = runCatching { launcherMMKV().getString(MMKV_KEY, null) }.getOrNull()
        edition = PclEdition.values().firstOrNull { it.key == saved } ?: PclEdition.Java
    }

    fun set(target: PclEdition) {
        edition = target
        runCatching { launcherMMKV().putString(MMKV_KEY, target.key) }
    }
}

/** 顶部两枚胶囊：`Java 版` / `基岩版`。 */
@Composable
fun PclEditionSwitch(modifier: Modifier = Modifier) {
    PclEditionState.ensureLoaded()
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclEdition.values().forEach { item ->
            PclPillButton(
                text = item.label,
                highlight = true,
                checked = PclEditionState.edition == item,
                modifier = Modifier.padding(horizontal = 4.dp),
                onClick = { PclEditionState.set(item) },
            )
        }
    }
}

/* ---------------------------------------------------------------- 数据 */

/** 一个已安装的基岩版。 */
data class PclBedrockInstall(
    val pkg: String,
    val versionName: String,
    val versionCode: Long,
    val installedAt: Long,
)

/**
 * 基岩版这一侧的全部系统交互。
 *
 * 只认系统里装着的游戏包，没有任何下载 / 分发逻辑 —— 那是插件的事。
 */
object PclBedrock {
    /**
     * 基岩版的包名。
     *
     * `com.mojang.minecraftpe` 是 Google Play / 官方渠道包；`com.microsoft.minecraftpe`
     * 是部分微软商店移植版本在安卓上的包名。两个都看一眼，装哪个认哪个。
     */
    private val PACKAGES = listOf("com.mojang.minecraftpe", "com.microsoft.minecraftpe")

    /** 商店详情页。 */
    const val STORE_PACKAGE = "com.mojang.minecraftpe"

    fun installed(context: Context): List<PclBedrockInstall> = PACKAGES.mapNotNull { pkg ->
        runCatching {
            val info = context.packageManager.getPackageInfo(pkg, 0)
            PclBedrockInstall(
                pkg = pkg,
                versionName = info.versionName.orEmpty(),
                versionCode = info.versionCodeCompat(),
                installedAt = info.firstInstallTime,
            )
        }.getOrNull()
    }

    /** 启动已安装的基岩版；没装（或没有启动入口）返回 false。 */
    fun launch(context: Context, install: PclBedrockInstall): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(install.pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * 跳应用商店的基岩版详情页。
     *
     * 先试 `market://`（国内 ROM 一般有自己的商店接管）；系统没有商店时退回官网下载页 ——
     * 这里**只跳官方**，不接任何第三方 APK 分发源。
     */
    fun openStore(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$STORE_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(market) }.isSuccess) return
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.minecraft.net/zh-hans/download"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(web) }.isSuccess) return
        PclHintHost.push("没有找到可用的应用商店或浏览器。", PclHintTheme.Red)
    }
}

/** `PackageInfo.versionCode` 在 API 28 起被 `longVersionCode` 取代。 */
@Suppress("DEPRECATION")
private fun PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

/**
 * 本地 APK 安装。
 *
 * 流程和启动器自己的更新器一致（见 `PclLauncherUpdate`）：先把安装包**复制进缓存**
 * （`content://` 的读权限是临时的，系统安装器在另一个进程里读不到），再检查
 * 「安装未知应用」，没有就跳授权页，回来后由 `ON_RESUME` 接着装。
 */
private object PclBedrockInstaller {
    /** 卡在授权页的那份安装包；授权回来后继续装它。 */
    private var pending: File? = null

    private fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()

    /** 把选中的 APK 复制到缓存目录；读不到返回 null。 */
    fun stage(context: Context, uri: Uri): File? = runCatching {
        val dir = File(context.cacheDir, "bedrock-install").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "pcl-bedrock.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        out.takeIf { it.length() > 0 }
    }.getOrNull()

    /** 拉起系统安装器。返回 false 表示缺「安装未知应用」权限。 */
    fun install(context: Context, file: File): Boolean {
        if (needsPermission(context)) {
            pending = file
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            pending = null
            true
        }.getOrDefault(false)
    }

    fun openPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 从授权页回来：有权限了就把待装的那份交出去。 */
    fun retryIfPossible(context: Context) {
        val file = pending ?: return
        if (!needsPermission(context)) install(context, file)
    }
}

/** 选本地 APK 并安装；返回一个「点一下就开始选文件」的动作。 */
@Composable
private fun rememberBedrockApkInstall(): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = PclBedrockInstaller.stage(context, uri)
        if (file == null) {
            PclHintHost.push("读不到这个安装包，换一个再试。", PclHintTheme.Red)
            return@rememberLauncherForActivityResult
        }
        if (!PclBedrockInstaller.install(context, file)) {
            PclHintHost.push(
                "需要先允许「安装未知应用」，授权回来后会自动继续安装。",
                PclHintTheme.Yellow,
            )
            PclBedrockInstaller.openPermissionSettings(context)
        }
    }
    // 用 `*/*`：不少 ROM 不认 application/vnd.android.package-archive，选不到文件
    return remember(picker) { { picker.launch(arrayOf("*/*")) } }
}

/* ---------------------------------------------------------------- 左栏 */

/**
 * 基岩版的左栏：状态 + 启动 + 两个小按钮，版式对齐 Java 版的
 * 「登录面板 / 启动按钮 / 版本选择」三段布局。
 */
@Composable
internal fun ColumnScope.PclBedrockInput() {
    val context = LocalContext.current
    val p = pclPalette
    var installs by remember { mutableStateOf(emptyList<PclBedrockInstall>()) }
    val installApk = rememberBedrockApkInstall()

    LaunchedEffect(Unit) { installs = PclBedrock.installed(context) }

    // 从系统安装器 / 商店回来：刷新一次状态，顺便把卡在授权页的安装继续掉
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PclBedrockInstaller.retryIfPossible(context)
                installs = PclBedrock.installed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- 中间：装了哪些版本
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (installs.isEmpty()) {
            PclText(text = "还没有安装 Minecraft 基岩版", bold = true, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            PclText(
                text = "可以用下面的「安装 APK」选本地的安装包，或者去官方商店安装。",
                fontSize = 12,
                color = p.gray2,
                textAlign = TextAlign.Center,
            )
        } else {
            installs.forEach { item ->
                PclText(text = "Minecraft 基岩版", bold = true, fontSize = 14)
                Spacer(Modifier.height(4.dp))
                PclText(
                    text = "v${item.versionName.ifBlank { "未知版本" }} · ${item.pkg}",
                    fontSize = 12,
                    color = p.gray2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ---- 启动（和 Java 版同一套几何：Height=54，版本提示画在按钮下沿）
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp)) {
        PclButton(
            text = "启动基岩版",
            enabled = installs.isNotEmpty(),
            colorState = PclColorState.Highlight,
            height = 54.dp,
            contentPadding = PaddingValues(start = 30.dp, end = 30.dp, bottom = 15.dp),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val target = installs.firstOrNull()
                if (target == null || !PclBedrock.launch(context, target)) {
                    PclHintHost.push("启动失败：没有找到可启动的基岩版。", PclHintTheme.Red)
                }
            },
        )
        PclText(
            text = installs.firstOrNull()?.let { "已安装 v${it.versionName}" } ?: "尚未安装基岩版",
            fontSize = 11,
            color = p.gray3,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 35.dp, end = 35.dp, bottom = 10.dp),
            textAlign = TextAlign.Center,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PclButton(text = "安装 APK", modifier = Modifier.weight(1f), onClick = installApk)
        PclButton(text = "官方商店", modifier = Modifier.weight(1f)) {
            PclBedrock.openStore(context)
        }
    }
    Spacer(Modifier.height(20.dp))
}

/* ---------------------------------------------------------------- 右栏 */

/**
 * 基岩版右栏：状态 / 安装 / 版本管理（插件）三张卡。
 *
 * 第三张卡是「管理器插件」的入口：谁是插件、叫什么，全由它自己的 `plugin.json` 决定，
 * 启动器里没有写死任何插件包名。
 */
@Composable
fun PclBedrockRight() {
    val context = LocalContext.current
    val p = pclPalette
    var installs by remember { mutableStateOf(emptyList<PclBedrockInstall>()) }
    var entryPlugins by remember { mutableStateOf(emptyList<ZlPlugin>()) }
    val installApk = rememberBedrockApkInstall()

    fun refresh() {
        installs = PclBedrock.installed(context)
    }

    LaunchedEffect(Unit) {
        refresh()
        entryPlugins = withContext(Dispatchers.IO) {
            runCatching { ZlPluginManager.entryPlugins(context) }.getOrDefault(emptyList())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PclContent {
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Minecraft 基岩版") {
            val install = installs.firstOrNull()
            if (install == null) {
                PclText(
                    text = "没有检测到已经安装的基岩版。\n" +
                            "可以用「从本地 APK 安装」选一个安装包，或者去官方商店安装；" +
                            "装好之后回到启动器，这里会自动认出来。",
                    fontSize = 12,
                )
            } else {
                PclText(text = "已安装 v${install.versionName.ifBlank { "未知版本" }}", bold = true)
                Spacer(Modifier.height(6.dp))
                PclText(text = "包名：${install.pkg}", fontSize = 12, color = p.gray2)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PclButton(
                        text = "启动",
                        colorState = PclColorState.Highlight,
                        onClick = {
                            if (!PclBedrock.launch(context, install)) {
                                PclHintHost.push("启动失败：没有找到可启动的入口。", PclHintTheme.Red)
                            }
                        },
                    )
                    PclButton(text = "卸载", colorState = PclColorState.Red) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${install.pkg}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        PclCard(modifier = Modifier.fillMaxWidth(), title = "安装") {
            PclText(
                text = "启动器只负责把安装包交给系统安装器，安装界面和权限都由系统接管。",
                fontSize = 12,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PclButton(
                    text = "从本地 APK 安装",
                    colorState = PclColorState.Highlight,
                    onClick = installApk,
                )
                PclButton(text = "官方商店") { PclBedrock.openStore(context) }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        PclCard(modifier = Modifier.fillMaxWidth(), title = "版本管理") {
            if (entryPlugins.isEmpty()) {
                PclText(
                    text = "没有检测到带界面的插件。装一个基岩版管理器插件（APK）之后，" +
                            "「设置 → 插件管理」会自动认出来，这里就会出现它的入口。",
                    fontSize = 12,
                )
            } else {
                PclText(
                    text = "下面的入口由插件自己声明（`plugin.json` 的 `entry`），启动器不认任何固定包名。",
                    fontSize = 12,
                    color = p.gray2,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    entryPlugins.forEach { plugin ->
                        PclButton(
                            text = "打开${plugin.label.ifBlank { plugin.name }}",
                            colorState = PclColorState.Highlight,
                        ) {
                            if (!ZlPluginManager.launch(context, plugin)) {
                                PclHintHost.push(
                                    "没有找到「${plugin.name}」的应用，重新装一次插件 APK 再试。",
                                    PclHintTheme.Red,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        PclHint(
            text = "基岩版的版本列表、网盘下载与提取码都在**管理器插件**里；启动器这边只有" +
                    "「认出已安装的版本 / 启动 / 跳商店 / 装本地 APK」，不接任何第三方分发源。",
            theme = PclHintTheme.Blue,
        )

        Spacer(Modifier.height(PclDimens.cardGap))
        PclBottomSpacer()
    }
}
