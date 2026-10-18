@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)

package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.util.component1
import coil3.util.component2
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.BufferedSource
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.impl.use
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal expect object HeicBridge {
    fun decode(id: String, encoded: String, success: (Int, Int, String) -> Unit, failure: (String) -> Unit)
    /** Browser-native AVIF decoding, hosted by the same worker package. */
    fun decodeAvif(id: String, encoded: String, success: (Int, Int, String) -> Unit, failure: (String) -> Unit)
    fun cancel(id: String)
}

internal class WebHeicDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult = slots.withPermit {
        decodeWithBridge("HEIC", source, options, HeicBridge::decode)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
            if (isHeic(result.source.source())) WebHeicDecoder(result.source, options) else null
    }

    companion object {
        private val slots = Semaphore(1)
        private val brands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis")

        internal fun isHeic(source: BufferedSource): Boolean = hasFtypBrand(source, brands)
    }
}

private var nextBridgeId = 0L

/** Decodes [source] in a bridge worker, then samples its RGBA pixels like any other Coil bitmap. */
internal suspend fun decodeWithBridge(
    format: String,
    source: ImageSource,
    options: Options,
    decode: (id: String, encoded: String, success: (Int, Int, String) -> Unit, failure: (String) -> Unit) -> Unit,
): DecodeResult {
    val input = source.source()
    require(!input.request(64L * 1024 * 1024 + 1)) { "$format exceeds 64 MiB" }
    val encoded = input.readByteString().base64()
    val pixels = suspendCancellableCoroutine<BridgePixels> { continuation ->
        // Bridge jobs share one cancellation table, so ids are unique across formats.
        val id = (++nextBridgeId).toString()
        continuation.invokeOnCancellation { HeicBridge.cancel(id) }
        decode(id, encoded, { width, height, data ->
            if (continuation.isActive) continuation.resume(BridgePixels(width, height, data))
        }, { message ->
            if (continuation.isActive) continuation.resumeWithException(IllegalArgumentException(message))
        })
    }
    val bytes = requireNotNull(pixels.data.decodeBase64()).toByteArray()
    require(pixels.width > 0 && pixels.height > 0 &&
        pixels.width.toLong() * pixels.height * 4 == bytes.size.toLong()) { "Invalid $format pixels" }
    val info = ImageInfo(pixels.width, pixels.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    return Image.makeRaster(info, bytes, pixels.width * 4).use { image ->
        val (targetWidth, targetHeight) = DecodeUtils.computeDstSize(
            image.width, image.height, options.size, options.scale, options.maxBitmapSize,
        )
        var ratio = DecodeUtils.computeSizeMultiplier(
            image.width, image.height, targetWidth, targetHeight, options.scale, options.maxBitmapSize,
        )
        if (options.precision == Precision.INEXACT) ratio = ratio.coerceAtMost(1.0)
        val width = (image.width * ratio).toInt().coerceAtLeast(1)
        val height = (image.height * ratio).toInt().coerceAtLeast(1)
        val bitmap = Bitmap()
        try {
            check(bitmap.allocN32Pixels(width, height))
            Canvas(bitmap).use { canvas ->
                canvas.drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                    Rect.makeWH(width.toFloat(), height.toFloat()), SamplingMode.DEFAULT, null, false)
            }
            bitmap.setImmutable()
            DecodeResult(bitmap.asImage(), width < image.width || height < image.height)
        } catch (error: Throwable) {
            bitmap.close()
            throw error
        }
    }
}

private val ftyp = "ftyp".encodeUtf8()

/** Peeks at the ISO BMFF `ftyp` major and compatible brands without consuming [source]. */
internal fun hasFtypBrand(source: BufferedSource, brands: Set<String>): Boolean {
    if (!source.request(16) || !source.rangeEquals(4, ftyp)) return false
    val header = source.peek()
    var size = header.readInt().toLong() and 0xffffffffL
    header.skip(4)
    val headerSize = if (size == 1L) { size = header.readLong(); 16L } else 8L
    if (size !in (headerSize + 8)..4096 || (size - headerSize) % 4 != 0L || !source.request(size)) return false
    if (header.readUtf8(4) in brands) return true
    header.skip(4)
    repeat(((size - headerSize - 8) / 4).toInt()) {
        if (header.readUtf8(4) in brands) return true
    }
    return false
}

private data class BridgePixels(val width: Int, val height: Int, val data: String)
