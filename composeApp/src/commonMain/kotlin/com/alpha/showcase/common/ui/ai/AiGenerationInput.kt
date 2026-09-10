package com.alpha.showcase.common.ui.ai

import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.request.CachePolicy
import com.alpha.showcase.common.ai.AiOriginalImage
import com.alpha.showcase.common.ui.ext.buildImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Decoded upload pixels and the original source have separate lifetimes and purposes. */
internal class AiGenerationInput(val image: Image, val data: Any? = null, val name: String? = null)

/** Fetch through the same authenticated source pipeline; never send these bytes to the AI provider. */
internal suspend fun readAiOriginal(input: AiGenerationInput, loader: ImageLoader, context: PlatformContext): AiOriginalImage? {
    val data = input.data ?: return null
    return try {
        withTimeoutOrNull(15_000) {
            var original: AiOriginalImage? = null
            val request = buildImageRequest(context, data).newBuilder()
                .memoryCachePolicy(CachePolicy.DISABLED)
                .decoderFactory(Decoder.Factory { result, _, _ ->
                    Decoder {
                        val source = result.source.source()
                        check(!source.request(32L * 1024 * 1024 + 1)) { "Original image exceeds archive limit" }
                        val bytes = source.readByteArray()
                        originalImageExtension(bytes, result.mimeType)?.let { original = AiOriginalImage(bytes, it) }
                        DecodeResult(input.image, isSampled = false)
                    }
                }).build()
            loader.execute(request)
            original
        }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }
}

internal fun originalImageExtension(bytes: ByteArray, mimeType: String?): String? {
    val head = bytes.take(12).map { it.toInt() and 255 }
    return when {
        head.take(3) == listOf(255, 216, 255) -> "jpg"
        head.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10) -> "png"
        head.take(3) == listOf(71, 73, 70) -> "gif"
        head.take(4) == listOf(82, 73, 70, 70) && head.drop(8) == listOf(87, 69, 66, 80) -> "webp"
        else -> when (mimeType?.substringBefore(';')) {
            "image/tiff" -> "tiff"
            "image/bmp" -> "bmp"
            "image/avif" -> "avif"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> null
        }
    }
}
