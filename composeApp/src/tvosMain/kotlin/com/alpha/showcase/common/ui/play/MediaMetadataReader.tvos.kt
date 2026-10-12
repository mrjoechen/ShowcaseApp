@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common.ui.play

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import okio.BufferedSource
import okio.use
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRelease
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import kotlin.math.roundToInt

/** Read bounded image headers using ImageIO; malformed metadata must not prevent playback. */
internal actual fun readSourceMetadata(source: BufferedSource, sourceLength: Long?): MediaMetadata? = try {
    source.use {
        val limit = (sourceLength ?: (4 * 1024 * 1024L)).coerceIn(0, 4 * 1024 * 1024L)
        source.request(limit)
        val bytes = source.readByteArray(minOf(limit, source.buffer.size))
        if (bytes.isEmpty()) null else bytes.usePinned { pinned ->
            val data = CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
                ?: return@usePinned null
            try {
                val image = CGImageSourceCreateWithData(data, null) ?: return@usePinned null
                try {
                    val properties = CGImageSourceCopyPropertiesAtIndex(image, 0u, null)
                        ?: return@usePinned null
                    val map = CFBridgingRelease(properties) as? Map<*, *> ?: return@usePinned null
                    galleryImageMetadata(map, null, sourceLength ?: 0).copy(fileSize = sourceLength)
                } finally { CFRelease(image) }
            } finally { CFRelease(data) }
        }
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

internal actual fun formatExifAperture(value: Double): String = "f/$value"
internal actual fun formatExifExposure(value: Double): String? =
    if (value >= 1) "${value}s" else "1/${(1 / value).roundToInt()}s"
internal actual fun formatExifIso(value: Int): String = "ISO $value"
