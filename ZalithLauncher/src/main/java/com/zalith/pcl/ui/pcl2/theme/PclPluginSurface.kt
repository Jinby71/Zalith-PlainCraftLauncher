/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 把「插件渲染器画好的位图」贴到控件上。
 *
 * 这一步刻意做得很薄：插件负责全部材质（模糊、折射、高光、厚度），
 * 启动器只做两件事 —— 把底片和坐标递进去，把结果裁成主题要求的形状。
 * 这样以后改玻璃观感只重发插件，不用重编启动器。
 */

package com.zalith.pcl.ui.pcl2.theme

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * 插件托管的玻璃表面。
 *
 * 与 `pclSoftGlass` 的关系：位置槽、底片映射、裁剪方式一致，
 * 只是「怎么画」这一整块交给了 [renderer]。
 *
 * 渲染结果按「尺寸 + 屏幕原点」缓存：静止的卡片不会每帧都进插件，
 * 滚动时原点变了才重画 —— 低版本机型上这是省电也省帧的关键。
 *
 * @param renderer 插件提供的渲染器；调用方应保证非空（为空就走内置软玻璃）
 * @param role 角色名，插件可据此对不同控件用不同材质
 */
fun Modifier.pclPluginSurface(
    renderer: PclSkinRenderer,
    role: String,
    params: PclSkinGlassParams,
    shape: Shape,
    soft: PclSoftBackdrop,
): Modifier = composed {
    // 位置槽：和内置软玻璃一样用 FloatArray，只触发重绘、不触发重组
    val pos = remember { FloatArray(2) }
    val screenOffset = LocalPclScreenOffset.current
    val cache = remember { PluginSurfaceCache() }

    this
        .onGloballyPositioned {
            val p = it.positionInRoot()
            pos[0] = p.x
            pos[1] = p.y
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val androidClip = Path().apply { addOutline(outline) }.asAndroidPath()
            val dst = RectF(0f, 0f, size.width, size.height)
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            val backdropBitmap = soft.image.asAndroidBitmap()

            onDrawBehind {
                val w = size.width.toInt()
                val h = size.height.toInt()
                if (w <= 0 || h <= 0) return@onDrawBehind

                val bitmap = cache.obtain(
                    renderer = renderer,
                    role = role,
                    width = w,
                    height = h,
                    originX = pos[0] + screenOffset.x,
                    originY = pos[1] + screenOffset.y,
                    backdrop = backdropBitmap,
                    backdropScreenW = soft.screenSize.width,
                    backdropScreenH = soft.screenSize.height,
                    params = params,
                ) ?: return@onDrawBehind

                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.clipPath(androidClip)
                canvas.drawBitmap(bitmap, null, dst, paint)
                canvas.restore()
            }
        }
}

/**
 * 「上一次画好的表面」。
 *
 * 键是尺寸 + 屏幕原点 + 参数。参数用 data class 的 equals，
 * 主题热更新换了玻璃参数时缓存自动失效。
 */
private class PluginSurfaceCache {

    private var width = -1
    private var height = -1
    private var originX = Float.NaN
    private var originY = Float.NaN
    private var params: PclSkinGlassParams? = null
    private var bitmap: Bitmap? = null

    fun obtain(
        renderer: PclSkinRenderer,
        role: String,
        width: Int,
        height: Int,
        originX: Float,
        originY: Float,
        backdrop: Bitmap,
        backdropScreenW: Int,
        backdropScreenH: Int,
        params: PclSkinGlassParams,
    ): Bitmap? {
        val fresh = bitmap != null &&
                this.width == width &&
                this.height == height &&
                this.originX == originX &&
                this.originY == originY &&
                this.params == params
        if (fresh) return bitmap

        // 插件里抛什么都只能吞掉 —— 一块玻璃画不出来不该让整个界面挂掉，
        // 返回 null 后调用方这次不画，下一帧重新试。
        val drawn = runCatching {
            renderer.renderSurface(
                role = role,
                width = width,
                height = height,
                backdrop = backdrop,
                backdropScreenW = backdropScreenW,
                backdropScreenH = backdropScreenH,
                originX = originX,
                originY = originY,
                // 形状裁剪由启动器负责，插件只管画满整块矩形
                cornerPx = 0f,
                params = params,
            )
        }.getOrNull() ?: return null

        bitmap?.takeIf { it !== drawn && !it.isRecycled }?.recycle()
        bitmap = drawn
        this.width = width
        this.height = height
        this.originX = originX
        this.originY = originY
        this.params = params
        return drawn
    }
}
