/*
 * ZPCL Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「插件模式」—— 启动器侧的最后一层接口：选中某个自带界面的插件之后，
 * 首页 / 下载 / 版本设置这些版块可以整页交给插件声明的页面。
 *
 * 启动器这边只有三件事：
 *   1. 记住当前选中了哪个插件（MMKV）
 *   2. 按插件的 `pages.json` 里 `sections` 找到对应的页面 id
 *   3. 把那一页渲染出来（内容全是插件写的）
 *
 * 它不认识任何一个具体插件，也不知道那些页面里写的是什么。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zalith.pcl.game.plugin.ZlPluginManager
import com.zalith.pcl.setting.launcherMMKV
import com.zalith.pcl.ui.pcl2.data.PclPluginPageSpec
import com.zalith.pcl.ui.pcl2.data.PclPluginSection
import com.zalith.pcl.ui.pcl2.data.parsePclPluginPages
import com.zalith.pcl.ui.pcl2.data.parsePclPluginEntries
import com.zalith.pcl.ui.pcl2.data.parsePclPluginSections
import com.zalith.pcl.ui.pcl2.theme.PclUiRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 当前生效的插件模式。
 *
 * 用文件级单例而不是 `remember`：切版块、进二级页都会让页面离开组合，
 * `remember` 存不住；落盘在 MMKV 里，重启之后还停在同一页。
 */
object PclPluginMode {
    private const val MMKV_KEY = "pcl2.launch.entry"
    private var loaded = false

    /** 当前选中的插件 id（空串 = 不启用任何插件模式）。 */
    var activeId by mutableStateOf("")
        private set

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        activeId = runCatching { launcherMMKV().getString(MMKV_KEY, "").orEmpty() }.getOrDefault("")
    }

    fun set(id: String) {
        activeId = id
        runCatching { launcherMMKV().putString(MMKV_KEY, id) }
    }

    /** 这个版块是不是被当前插件接管了。 */
    fun section(section: PclSection): PclPluginSection? {
        if (activeId.isBlank()) return null
        return PclUiRuntime.sectionsByPlugin[activeId]?.get(section.name.lowercase())
    }

    /** 按 id 在当前插件的页面里找一页。 */
    fun page(id: String?): PclPluginPageSpec? {
        if (activeId.isBlank() || id.isNullOrBlank()) return null
        return PclUiRuntime.pages.firstOrNull { it.pluginId == activeId && it.id == id }
    }

    /** 首页那页（`sections` 里 `home` 的 content）。 */
    fun homePage(): PclPluginPageSpec? = page(section(PclSection.Home)?.content)
}

/**
 * 插件页面 / 版块声明的加载器。
 *
 * 以前只在 `PclShell` 启动时读一次 —— 装完或升级插件之后不重启启动器就一直是旧的，
 * 表现是「导入插件了但页面没变」。现在安装完、回到前台都会重读一次。
 */
object PclPluginLoader {

    /** 重新读一遍所有启用插件的 pages.json。必须在 IO 线程调用。 */
    suspend fun reload(context: android.content.Context) = withContext(Dispatchers.IO) {
        val specs = runCatching { ZlPluginManager.activePages(context) }.getOrDefault(emptyList())
        PclUiRuntime.pages = specs.flatMap { parsePclPluginPages(it.json, it.pluginId) }
        PclUiRuntime.sectionsByPlugin = specs
            .associate { it.pluginId to parsePclPluginSections(it.json) }
            .filterValues { it.isNotEmpty() }
        PclUiRuntime.entries = specs.flatMap { parsePclPluginEntries(it.json, it.pluginId) }
        // 当前选中的插件如果已经没有页面了（卸载/停用），退回 Java 版
        if (PclPluginMode.activeId.isNotBlank() &&
            specs.none { it.pluginId == PclPluginMode.activeId }
        ) {
            PclPluginMode.set("")
        }
    }
}
