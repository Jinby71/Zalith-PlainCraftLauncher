/*
 * ZPCL Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

package com.zalith.pcl.ui.pcl2.theme

import com.zalith.pcl.ui.pcl2.components.PclGaussianBlur
import kotlin.math.roundToInt

/** 工作图保持窗口比例；画质控制像素预算，blur 控制真实卷积强度。 */
internal data class PclSoftBlurPlan(val width: Int, val height: Int, val sigma: Float)

internal object PclSoftBlur {
    fun plan(width: Int, height: Int, quality: PclGlassQuality, blurPx: Float): PclSoftBlurPlan {
        require(width > 0 && height > 0)
        val (divisor, maxDimension) = when (quality) {
            PclGlassQuality.Low -> 4f to 384
            PclGlassQuality.Normal -> 3f to 512
            PclGlassQuality.High -> 2f to 768
        }
        // 两个方向共用缩放比例，不能分别钳位后把宽屏底片挤成方形。
        val scale = minOf(1f / divisor, maxDimension.toFloat() / maxOf(width, height))
        val workWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val workHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val sigma = if (blurPx.isFinite() && blurPx > 0f) {
            (blurPx * scale).coerceIn(0.35f, 21f)
        } else 0f
        return PclSoftBlurPlan(workWidth, workHeight, sigma)
    }

    fun blurPixels(pixels: IntArray, plan: PclSoftBlurPlan) {
        require(pixels.size == plan.width * plan.height)
        if (plan.sigma <= 0f) return
        PclGaussianBlur.convolve(
            pixels, plan.width, plan.height, PclGaussianBlur.gaussianKernel(plan.sigma),
        )
    }
}
