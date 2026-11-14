/*
 * Pixelforge Launcher —— Zalith Launcher 2 的修改版（非官方）。
 * 上游版权：Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 * 本文件自 2026 年起被修改；修改声明与名称/标识的处理见仓库根目录 NOTICE.md。
 *
 * 本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版（或更高版本）重新发布
 * 和/或修改它。分发时请保留本声明、上游版权与 LICENSE。
 */

package com.zalith.pcl.game.version.mod.reader

import com.zalith.pcl.game.addons.modloader.ModLoader
import com.zalith.pcl.game.version.mod.LocalMod
import com.zalith.pcl.game.version.mod.ModMetadataReader
import com.zalith.pcl.utils.file.UnpackZipException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.zip.ZipFile as JDKZipFile

object OptiFineModReader : ModMetadataReader {
    override suspend fun fromLocal(modFile: File): LocalMod = withContext(Dispatchers.IO) {
        try {
            JDKZipFile(modFile).use { zip ->
                if (zip.checkOptiFine()) {
                    LocalMod(
                        modFile = modFile,
                        fileSize = FileUtils.sizeOf(modFile),
                        id = "optifine",
                        loader = ModLoader.OPTIFINE,
                        name = "OptiFine",
                        description = null,
                        version = zip.tryGetOptiFineVersion(),
                        authors = listOf("sp614x"),
                        icon = null,
                        checkRemote = false,
                    )
                } else throw UnpackZipException("File $modFile is not a OptiFine mod.")
            }
        } catch (e: Exception) {
            if (e !is UnpackZipException) return@withContext readWithApacheZip(modFile)
            else throw e
        }
    }

    private fun readWithApacheZip(modFile: File): LocalMod {
        val zip = ZipFile.Builder()
            .setFile(modFile)
            .get()

        if (zip.checkOptiFine()) {
            return LocalMod(
                modFile = modFile,
                fileSize = FileUtils.sizeOf(modFile),
                id = "optifine",
                loader = ModLoader.OPTIFINE,
                name = "OptiFine",
                description = null,
                version = zip.tryGetOptiFineVersion(),
                authors = listOf("sp614x"),
                icon = null,
                checkRemote = false,
            )
        } else throw UnpackZipException("File $modFile is not a OptiFine mod.")
    }
}