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
 * 插件皮肤渲染器接口（同进程动态加载）。
 *
 * 背景：
 *   启动器的「软玻璃」是给没有 RenderEffect 的机器（Android 11 及以下）准备的，
 *   用一张预模糊底片 + BitmapShader 采样画出玻璃。但采样坐标、模糊强度这些
 *   观感细节如果写死在启动器里，每调一次都要重编启动器。
 *
 * 出路：
 *   皮肤插件可以在自己的 APK 里放一个实现了本接口的类（`ui.json` 的
 *   `rendererClass` 指定全限定名），启动器用 DexClassLoader 把它加载进进程，
 *   软玻璃的实际绘制就交给插件。以后修低版本玻璃、调效果，只重发插件。
 *
 * 为什么用 Bitmap / 基础类型做参数，不用 Compose 类型：
 *   插件渲染器在「插件自己的 dex」里编译，它要引用启动器的类。
 *   Compose 的 ImageBitmap / Shape 类型跨 dex 边界容易因版本不一致出问题，
 *   这里刻意收窄成 android.graphics 的 Bitmap / Rect / Paint 那一层，
 *   边界越薄越不容易断。
 */

package com.zalith.pcl.ui.pcl2.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 插件实现「软玻璃表面」的契约。
 *
 * 启动器在需要画一块玻璃表面时，把下面这些原料交给插件，插件画好返回一张
 * 已经裁成目标形状、带透明通道的位图；启动器只负责把它贴到控件上。
 *
 * 实现类必须有一个**无参构造**，且类名要与 `ui.json` 的 `rendererClass` 一致。
 */
interface PclSkinRenderer {

    /**
     * 画一块玻璃表面。
     *
     * @param role 角色名（`card` / `button` / `input` / `panel` / `dialog` …）
     * @param width 目标像素宽
     * @param height 目标像素高
     * @param backdrop 预模糊的背景底片（已经是整屏模糊后的 Bitmap）
     * @param backdropScreenW 底片对应的屏幕宽（用于把控件坐标映射回底片）
     * @param backdropScreenH 底片对应的屏幕高
     * @param originX 控件左上角在屏幕上的 X（像素）
     * @param originY 控件左上角在屏幕上的 Y（像素）
     * @param cornerPx 圆角半径（像素）
     * @param params 玻璃参数（blur / refraction / highlight 等，见 [PclSkinGlassParams]）
     * @return 画好的位图；返回 null 表示「这个角色我不画」，启动器退回内置软玻璃。
     */
    fun renderSurface(
        role: String,
        width: Int,
        height: Int,
        backdrop: Bitmap,
        backdropScreenW: Int,
        backdropScreenH: Int,
        originX: Float,
        originY: Float,
        cornerPx: Float,
        params: PclSkinGlassParams,
    ): Bitmap?
}

/**
 * 传给插件渲染器的玻璃参数快照（脱离 Compose 的纯数据）。
 *
 * 字段与 `ui.json` 的 `glass` 一节一一对应；启动器把解析好的值塞进来，
 * 插件不用自己去读 json。
 */
data class PclSkinGlassParams(
    val blur: Float = 0f,
    val refraction: Float = 0f,
    val refractionHeight: Float = 0f,
    val highlight: Float = 0f,
    val highlightWidth: Float = 1f,
    val edgeGlow: Float = 1f,
    val haze: Float = 0f,
    val shadow: Float = 0f,
    /** 表面色的 ARGB（通常是白色）；插件把它按 [fillAlpha] 叠在底片上。 */
    val fillArgb: Int = 0xFFFFFFFF.toInt(),
    /** 表面色的不透明度（`roles.<role>.alpha`）；0 = 全透明玻璃，不叠色。 */
    val fillAlpha: Float = 0f,
    /**
     * 这块表面此刻处于什么状态（按下、选中、暗色 …）。
     *
     * 放在参数里而不是方法签名里，是为了**不破坏已发布的插件**：
     * 老渲染器只读它认识的字段，新字段加进来它当没看见，照样能用。
     */
    val state: PclSkinState = PclSkinState(),
)

/**
 * 表面的交互状态快照。
 *
 * 插件可以据此画出「按下去会陷下去」「选中态边缘更亮」这类反馈 ——
 * 这些光靠静态参数做不到，但它又不需要插件参与事件分发。
 */
data class PclSkinState(
    val pressed: Boolean = false,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    /** 当前是否暗色主题。 */
    val dark: Boolean = false,
    /** 屏幕密度（dp → px），插件画 1dp 的线要用它。 */
    val density: Float = 1f,
)

/**
 * 把主题里解析好的玻璃参数拍平成给插件看的快照。
 *
 * 插件不该自己去读 `ui.json` —— 一份参数两种读法迟早会对不上，
 * 这里由启动器统一解析完再交出去。
 */
fun PclGlassParams.toSkinParams(
    fill: Color,
    fillAlpha: Float,
    state: PclSkinState = PclSkinState(),
): PclSkinGlassParams =
    PclSkinGlassParams(
        blur = blur,
        refraction = refraction,
        refractionHeight = refractionHeight,
        highlight = highlight,
        highlightWidth = highlightWidth,
        edgeGlow = edgeGlow,
        haze = haze,
        shadow = shadow,
        fillArgb = fill.toArgb(),
        fillAlpha = fillAlpha,
        state = state,
    )
