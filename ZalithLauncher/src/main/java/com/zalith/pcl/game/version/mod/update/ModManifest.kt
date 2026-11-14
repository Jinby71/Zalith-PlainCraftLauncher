/*
 * ZPCL Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

package com.zalith.pcl.game.version.mod.update

import com.zalith.pcl.game.download.assets.platform.PlatformVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 更新模组需用到的清单
 * @param new 获取到的新模组版本信息
 */
data class ModManifest(
    val data: ModData,
    val new: PlatformVersion,
)

/**
 * 获取清单中全部的模组新版本信息
 */
fun List<ModManifest>.allNews() = map { it.new }

fun List<ModManifest>.toSelectableList() = map { manifest ->
    SelectableModManifest(
        data = manifest.data,
        new = manifest.new,
    )
}

/**
 * 可记录选择状态的更新模组清单
 * @see ModManifest
 * @property selected 是否选中该清单
 */
class SelectableModManifest(
    val data: ModData,
    val new: PlatformVersion,
) {
    private val _selected = MutableStateFlow(true)
    val selected = _selected.asStateFlow()

    fun updateSelected(value: Boolean) {
        _selected.update { value }
    }

    fun selected() = _selected.value
}

fun List<SelectableModManifest>.toFinalList() = mapNotNull { manifest ->
    if (manifest.selected()) {
        ModManifest(
            data = manifest.data,
            new = manifest.new,
        )
    } else null
}