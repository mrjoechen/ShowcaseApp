@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)

package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.BufferedSource
import org.glavo.avif.AvifImageReaderFactory
import org.glavo.avif.AvifPixelFormat
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Desktop-only AVIF decoding; Skiko has no AV1 codec. Fetching, authentication and caching remain in Coil. */
internal class DesktopAvifDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult = withContext(Dispatchers.Default) {
        // The pure-Java AV1 decoder is single-threaded and keeps full-resolution planes.
        // Limit simultaneous AVIF decodes independently of HEIC, ordinary images and GIFs.
        decodeSlots.withPermit {
            ensureActive()
            val bytes = source.source().use {
                require(!it.request(MAX_FILE_BYTES + 1)) { "AVIF file exceeds 64 MiB" }
                it.readByteArray()
            }
            // The reader resolves the primary item (or a sequence's first frame), grids,
            // clap, irot and imir. It returns display pixels, not alpha, depth or gain-map planes.
            val (info, frame) = READER.open(bytes).use { reader ->
                val info = reader.info()
                require(info.width() > 0 && info.height() > 0 && info.width().toLong() * info.height() <= MAX_PIXELS) {
                    "AVIF image exceeds 64 megapixels"
                }
                info to checkNotNull(reader.readFrame()) { "AVIF has no display pixels" }
            }
            ensureActive()
            val width = frame.width()
            val height = frame.height()
            // Little-endian ARGB ints are the BGRA byte order Skia expects.
            val pixels = ByteBuffer.allocate(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
                .apply { asIntBuffer().put(frame.intPixelBuffer()) }.array()
            val alphaType = when {
                !info.alphaPresent() -> ColorAlphaType.OPAQUE
                info.alphaPremultiplied() -> ColorAlphaType.PREMUL
                else -> ColorAlphaType.UNPREMUL
            }
            Image.makeRaster(ImageInfo(width, height, ColorType.BGRA_8888, alphaType), pixels, width * 4).use { image ->
                val bounds = Rect.makeWH(width.toFloat(), height.toFloat())
                val bitmap = sampledBitmap(image, bounds, options, rotateHalfTurn = false)
                bitmap.setImmutable()
                DecodeResult(bitmap.asImage(), bitmap.width < width || bitmap.height < height)
            }
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
            if (isAvif(result.source.source())) DesktopAvifDecoder(result.source, options) else null
    }

    companion object {
        private val decodeSlots = Semaphore(1)
        private const val MAX_FILE_BYTES = 64L * 1024 * 1024
        private const val MAX_PIXELS = 64L * 1024 * 1024
        // High-bit-depth images are reduced to 8 bits per channel, like other Skia bitmaps.
        private val READER = AvifImageReaderFactory.DEFAULT
            .withOutputPixelFormat(AvifPixelFormat.ARGB_8888)
            .withInputSizeLimit(MAX_FILE_BYTES)
        private val AVIF_BRANDS = setOf("avif", "avis")

        internal fun isAvif(source: BufferedSource): Boolean = hasFtypBrand(source, AVIF_BRANDS)
    }
}
