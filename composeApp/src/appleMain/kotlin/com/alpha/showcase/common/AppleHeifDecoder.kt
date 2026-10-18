@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, coil3.annotation.ExperimentalCoilApi::class)

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
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.kCGInterpolationHigh
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCache
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import kotlin.math.ceil

/**
 * Skia on Apple platforms has no HEVC or AV1 codec, so Coil's default decoder fails HEIC
 * and AVIF with "Failed to Image::makeFromEncoded". ImageIO decodes both HEIF flavours
 * with the system codecs (AVIF needs iOS/tvOS 16) and applies orientation while
 * downsampling; fetching and caching remain in Coil.
 */
internal class AppleHeifDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult = withContext(Dispatchers.Default) {
        decodeSlots.withPermit {
            ensureActive()
            val input = source.source()
            require(!input.request(MAX_FILE_BYTES + 1)) { "HEIF file exceeds 64 MiB" }
            val bytes = input.readByteArray()
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            val retainedData = CFBridgingRetain(data) ?: error("Unable to read HEIF")
            try {
                val imageSource = CGImageSourceCreateWithData(retainedData.reinterpret(), null)
                    ?: error("Unsupported HEIF data")
                try {
                    val (sourceWidth, sourceHeight) = orientedSize(imageSource)
                    val thumbnail = thumbnail(imageSource, maxPixelSize(sourceWidth, sourceHeight))
                    try {
                        ensureActive()
                        val bitmap = bitmap(thumbnail)
                        DecodeResult(bitmap.asImage(), bitmap.width < sourceWidth || bitmap.height < sourceHeight)
                    } finally {
                        CGImageRelease(thumbnail)
                    }
                } finally {
                    CFRelease(imageSource)
                }
            } finally {
                CFRelease(retainedData)
            }
        }
    }

    /** Display size after EXIF/irot orientation, used to pick the downsampled decode size. */
    private fun orientedSize(imageSource: CGImageSourceRef): Pair<Int, Int> {
        val properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0u, null)
            ?.let { CFBridgingRelease(it) as? Map<*, *> } ?: error("Unable to read HEIF properties")
        val width = (properties["PixelWidth"] as? Number)?.toInt() ?: 0
        val height = (properties["PixelHeight"] as? Number)?.toInt() ?: 0
        require(width > 0 && height > 0) { "HEIF has no primary image" }
        val rotated = (properties["Orientation"] as? Number)?.toInt() in 5..8
        return if (rotated) height to width else width to height
    }

    private fun maxPixelSize(sourceWidth: Int, sourceHeight: Int): Int {
        val (targetWidth, targetHeight) = DecodeUtils.computeDstSize(
            sourceWidth, sourceHeight, options.size, options.scale, options.maxBitmapSize,
        )
        var ratio = DecodeUtils.computeSizeMultiplier(
            sourceWidth, sourceHeight, targetWidth, targetHeight, options.scale, options.maxBitmapSize,
        )
        // ImageIO thumbnails never upscale; exact upscaling happens when drawing the bitmap.
        ratio = ratio.coerceAtMost(1.0)
        return ceil(maxOf(sourceWidth, sourceHeight) * ratio).toInt().coerceAtLeast(1)
    }

    private fun thumbnail(imageSource: CGImageSourceRef, maxPixelSize: Int): CGImageRef = memScoped {
        val settings = CFDictionaryCreateMutable(null, 0, null, null) ?: error("Unable to configure HEIF decoder")
        val pixels = alloc<IntVar> { value = maxPixelSize }
        val limit = CFNumberCreate(null, kCFNumberIntType, pixels.ptr) ?: error("Unable to configure HEIF size")
        try {
            CFDictionaryAddValue(settings, kCGImageSourceShouldCache, kCFBooleanFalse)
            CFDictionaryAddValue(settings, kCGImageSourceShouldCacheImmediately, kCFBooleanTrue)
            CFDictionaryAddValue(settings, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
            CFDictionaryAddValue(settings, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
            CFDictionaryAddValue(settings, kCGImageSourceThumbnailMaxPixelSize, limit)
            CGImageSourceCreateThumbnailAtIndex(imageSource, 0u, settings) ?: error("Unable to decode HEIF; AVIF needs iOS/tvOS 16")
        } finally {
            CFRelease(limit)
            CFRelease(settings)
        }
    }

    /** Draws into Skia's native BGRA premultiplied layout, converting wide-gamut/10-bit input to sRGB. */
    private fun bitmap(image: CGImageRef): Bitmap {
        val imageWidth = CGImageGetWidth(image).toInt()
        val imageHeight = CGImageGetHeight(image).toInt()
        require(imageWidth > 0 && imageHeight > 0) { "HEIF has no display pixels" }
        val (targetWidth, targetHeight) = DecodeUtils.computeDstSize(
            imageWidth, imageHeight, options.size, options.scale, options.maxBitmapSize,
        )
        var ratio = DecodeUtils.computeSizeMultiplier(
            imageWidth, imageHeight, targetWidth, targetHeight, options.scale, options.maxBitmapSize,
        )
        if (options.precision == Precision.INEXACT) ratio = ratio.coerceAtMost(1.0)
        val width = (imageWidth * ratio).toInt().coerceAtLeast(1)
        val height = (imageHeight * ratio).toInt().coerceAtLeast(1)
        require(width.toLong() * height * 4 <= Int.MAX_VALUE) { "HEIF output is too large" }
        val pixels = ByteArray(width * height * 4)
        val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB) ?: error("Unable to create sRGB color space")
        try {
            pixels.usePinned { pinned ->
                val context = CGBitmapContextCreate(
                    pinned.addressOf(0), width.toULong(), height.toULong(), 8u, (width * 4).toULong(), colorSpace,
                    CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little,
                ) ?: error("Unable to allocate HEIF output")
                try {
                    CGContextSetInterpolationQuality(context, kCGInterpolationHigh)
                    CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
                } finally {
                    CGContextRelease(context)
                }
            }
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
        val bitmap = Bitmap()
        try {
            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
            check(bitmap.installPixels(info, pixels, width * 4)) { "Unable to allocate HEIF bitmap" }
            bitmap.setImmutable()
            return bitmap
        } catch (error: Throwable) {
            bitmap.close()
            throw error
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
            if (isHeif(result.source.source())) AppleHeifDecoder(result.source, options) else null
    }

    companion object {
        private val decodeSlots = Semaphore(2)
        private const val MAX_FILE_BYTES = 64L * 1024 * 1024
        private val FTYP = "ftyp".encodeUtf8()
        // HEVC (HEIC), AV1 (AVIF) and generic HEIF brands; Skia decodes none of them, ImageIO all.
        private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "avif", "avis", "mif1", "msf1")

        internal fun isHeif(source: BufferedSource): Boolean {
            if (!source.request(16) || !source.rangeEquals(4, FTYP)) return false
            val header = source.peek()
            var boxSize = header.readInt().toLong() and 0xffffffffL
            header.skip(4)
            val headerSize = if (boxSize == 1L) {
                boxSize = header.readLong()
                16L
            } else 8L
            // Bound lookahead and only accept HEIF brands; Skia keeps handling everything else (e.g. MP4).
            if (boxSize !in (headerSize + 8)..4096 || (boxSize - headerSize) % 4 != 0L || !source.request(boxSize)) return false
            if (header.readUtf8(4) in HEIF_BRANDS) return true
            header.skip(4) // minor version
            repeat(((boxSize - headerSize - 8) / 4).toInt()) {
                if (header.readUtf8(4) in HEIF_BRANDS) return true
            }
            return false
        }
    }
}
