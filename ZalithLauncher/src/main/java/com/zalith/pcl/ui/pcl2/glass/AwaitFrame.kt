/*
 * Pixelforge Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

/*
 * 移植自 Kyant 的 AndroidLiquidGlass（Apache-2.0）
 *
 * 原仓库用 `expect/actual` 提供 awaitFrame（skiko 走另一套实现）；
 * 本工程只有 Android 端，直接落成实际实现。
 */

package com.zalith.pcl.ui.pcl2.glass

import kotlinx.coroutines.android.awaitFrame as androidAwaitFrame

/** 等到下一帧（动画里用来逐帧推进）。 */
suspend fun awaitFrame() {
    androidAwaitFrame()
}
