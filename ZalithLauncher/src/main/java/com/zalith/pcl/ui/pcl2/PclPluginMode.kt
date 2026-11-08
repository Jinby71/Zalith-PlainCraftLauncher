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
import com.zalith.pcl.setting.launcherMMKV
import com.zalith.pcl.ui.pcl2.data.PclPluginPageSpec
import com.zalith.pcl.ui.pcl2.data.PclPluginSection
import com.zalith.pcl.ui.pcl2.theme.PclUiRuntime

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
