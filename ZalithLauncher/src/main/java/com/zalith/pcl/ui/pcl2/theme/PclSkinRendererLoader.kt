/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 插件皮肤渲染器的加载器：从插件 APK 里取出渲染器 dex，用 DexClassLoader 加载。
 *
 * 为什么不直接加载插件整个 APK 的 classes.dex：
 *   插件的 app 模块里通常还有一堆 Compose / 第三方库代码，整包加载会把它们
 *   一并拖进启动器进程，依赖版本不一致就可能崩。这里只认插件 `assets/zplugin/`
 *   下那个**单独的** `renderer.dex` —— 它是纯渲染器、几乎零依赖，边界干净。
 */

package com.zalith.pcl.ui.pcl2.theme

import android.content.Context
import com.zalith.pcl.utils.logging.Logger
import dalvik.system.DexClassLoader
import java.io.File

/**
 * 缓存已加载的渲染器，按「插件目录路径 + 渲染器类名」做 key。
 *
 * 插件升级后 dex 会变，这里以 apk 的最后修改时间作为缓存失效依据，
 * 避免热更新后还拿着旧的实现。
 */
object PclSkinRendererLoader {

    private const val TAG = "PclSkinRendererLoader"

    /** `classes.dex` / `classes2.dex` / … */
    private val DEX_NAME = Regex("""classes(\d*)\.dex""")

    @Volatile
    private var cachedClass: Class<*>? = null
    @Volatile
    private var cachedKey: String? = null

    /**
     * 加载并实例化插件声明的渲染器。
     *
     * @param pluginDir 插件目录（内含 plugin.apk）
     * @param rendererClass 渲染器全限定类名（`ui.json` 的 `rendererClass`）
     * @return 实例；加载失败返回 null（启动器退回内置软玻璃，不崩）
     */
    fun load(pluginDir: File?, rendererClass: String?): PclSkinRenderer? {
        if (pluginDir == null || rendererClass.isNullOrBlank()) return null
        val apk = File(pluginDir, "plugin.apk")
        if (!apk.isFile) return null

        val key = apk.absolutePath + "|" + apk.lastModified() + "|" + rendererClass
        if (cachedKey == key && cachedClass != null) {
            return instantiate(cachedClass!!)
        }

        return runCatching {
            val context = PclUiRuntime.appContext ?: return null
            val dexPath = resolveDexPath(pluginDir, apk)
            val loader = DexClassLoader(
                dexPath,
                optimizedDir(context).absolutePath,
                null,
                PclSkinRenderer::class.java.classLoader,
            )
            val cls = loader.loadClass(rendererClass)
            cachedClass = cls
            cachedKey = key
            instantiate(cls)
        }.getOrElse { error ->
            Logger.warning(TAG, "加载插件渲染器失败：$error")
            null
        }
    }

    /**
     * 拼出交给 DexClassLoader 的 dex 路径。
     *
     * 两个坑：
     *  1. `DexClassLoader` 拿到 APK 路径时**只读 `classes.dex`**，多 dex 的
     *     `classes2.dex` / `classes3.dex` 不会被加载 —— 而渲染器类常常正好被
     *     分到副 dex 里（插件的方法数一多就会拆）。所以副 dex 必须抽出来，
     *     用 `File.pathSeparator` 串成一条路径。
     *  2. 主 dex 就没必要抽了，直接用 APK 路径更省事。
     */
    private fun resolveDexPath(pluginDir: File, apk: File): String {
        val dexNames = runCatching {
            java.util.zip.ZipFile(apk).use { zf ->
                zf.entries().asSequence()
                    .map { it.name }
                    .filter { DEX_NAME.matches(it) }
                    .sorted()
                    .toList()
            }
        }.getOrElse { error ->
            Logger.warning(TAG, "读插件 dex 列表失败：$error")
            return apk.absolutePath
        }

        if (dexNames.size <= 1) return apk.absolutePath

        val dir = File(pluginDir, "dex").apply { mkdirs() }
        val paths = dexNames.map { name ->
            val out = File(dir, name)
            if (!out.isFile || out.lastModified() < apk.lastModified()) {
                runCatching {
                    java.util.zip.ZipFile(apk).use { zf ->
                        val entry = zf.getEntry(name) ?: return@use
                        zf.getInputStream(entry).use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    out.setLastModified(apk.lastModified())
                }.onFailure { Logger.warning(TAG, "抽取 $name 失败：$it") }
            }
            out.absolutePath
        }
        return paths.joinToString(File.pathSeparator)
    }

    private fun optimizedDir(context: Context): File =
        // 必须放在应用自己的 code cache 下：插件目录（/sdcard/…）挂在 noexec 上，
        // DexClassLoader 往里写 odex 会直接失败。
        File(context.codeCacheDir, "pcl_skin_odex").apply { mkdirs() }

    private fun instantiate(cls: Class<*>): PclSkinRenderer? = runCatching {
        cls.getDeclaredConstructor().newInstance() as PclSkinRenderer
    }.getOrElse { error ->
        Logger.warning(TAG, "实例化渲染器失败（需要无参构造）：$error")
        null
    }
}
