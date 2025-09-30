package com.alpha.showcase.common

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.utils.AUDIO_EXT_SUPPORT
import com.alpha.showcase.common.utils.IMAGE_EXT_SUPPORT
import com.alpha.showcase.common.utils.VIDEO_EXT_SUPPORT
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * NetworkFile 扩展函数
 */

/**
 * 下载 NetworkFile 到本地文件
 * @param localFile 本地文件路径
 */
suspend fun NetworkFile.downloadTo(localFile: File): Result<Unit> {
    val reader = NetworkFileReader.getInstance()
    return try {
        val inputStreamResult = reader.readFile(this)
        if (inputStreamResult.isSuccess) {
            val inputStream = inputStreamResult.getOrThrow()
            inputStream.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(Unit)
        } else {
            Result.failure(inputStreamResult.exceptionOrNull() ?: Exception("Failed to read file"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * 读取 NetworkFile 内容为字节数组
 */
suspend fun NetworkFile.readBytes(): Result<ByteArray> {
    val reader = NetworkFileReader.getInstance()
    return try {
        val inputStreamResult = reader.readFile(this)
        if (inputStreamResult.isSuccess) {
            val inputStream = inputStreamResult.getOrThrow()
            val bytes = inputStream.use { it.readBytes() }
            Result.success(bytes)
        } else {
            Result.failure(inputStreamResult.exceptionOrNull() ?: Exception("Failed to read file"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * 读取 NetworkFile 内容为字符串
 * @param charset 字符编码，默认为 UTF-8
 */
suspend fun NetworkFile.readText(charsetName: String = "UTF-8"): Result<String> {
    return readBytes().mapCatching { bytes ->
        String(bytes, charset(charsetName))
    }
}

/**
 * 获取 NetworkFile 的输入流
 */
suspend fun NetworkFile.openInputStream(): Result<InputStream> {
    val reader = NetworkFileReader.getInstance()
    return reader.readFile(this)
}

/**
 * 获取文件扩展名
 */
val NetworkFile.extension: String
    get() = fileName.substringAfterLast('.', "")

/**
 * 检查文件是否为图片类型
 */
fun NetworkFile.isImage(): Boolean {
    return extension.lowercase() in IMAGE_EXT_SUPPORT
}

/**
 * 检查文件是否为视频类型
 */
fun NetworkFile.isVideo(): Boolean {
    return extension.lowercase() in VIDEO_EXT_SUPPORT
}

/**
 * 检查文件是否为音频类型
 */
fun NetworkFile.isAudio(): Boolean {
    return extension.lowercase() in AUDIO_EXT_SUPPORT
}
