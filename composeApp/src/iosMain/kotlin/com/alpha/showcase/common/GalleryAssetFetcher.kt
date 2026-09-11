@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common

import com.alpha.showcase.common.ui.play.MediaSourceMetadata
import com.alpha.showcase.common.ui.play.PhotoCoordinates
import com.alpha.showcase.common.ui.play.galleryImageMetadata
import com.alpha.showcase.common.ui.play.readsMediaMetadata
import kotlinx.cinterop.useContents
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.pxOrElse
import com.alpha.showcase.common.ui.source.accessibleGalleryAssetIdentifiers
import com.alpha.showcase.common.ui.source.galleryAssetIdentifier
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.alloc
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Buffer
import platform.Photos.*
import platform.Foundation.NSData
import platform.Foundation.CFBridgingRetain
import platform.CoreFoundation.*
import platform.CoreGraphics.CGImageRelease
import platform.ImageIO.*
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Loads PhotoKit data into memory on demand; never writes an app-owned original file. */
internal class GalleryAssetFetcher(private val identifier: String, private val options: Options) : Fetcher {
    override suspend fun fetch(): SourceFetchResult = requests.withPermit {
        check(identifier in accessibleGalleryAssetIdentifiers(listOf(identifier))) { "Photo is no longer accessible" }
        val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(identifier), null).firstObject as? PHAsset
            ?: error("Photo is no longer available")
        val manager = PHImageManager.defaultManager()
        val requestOptions = PHImageRequestOptions().apply {
            networkAccessAllowed = true
            deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
        }
        val (data, type) = suspendCancellableCoroutine<Pair<NSData, String?>> { continuation ->
            val request = manager.requestImageDataAndOrientationForAsset(asset, requestOptions) { data, type, _, info ->
                if (!continuation.isActive) return@requestImageDataAndOrientationForAsset
                if (data == null) {
                    continuation.resumeWithException(IllegalStateException("Unable to read photo: ${info?.get(PHImageErrorKey)}"))
                } else if (data.length > Int.MAX_VALUE.toULong()) {
                    continuation.resumeWithException(IllegalStateException("Photo is too large to decode"))
                } else {
                    continuation.resume(data to type)
                }
            }
            continuation.invokeOnCancellation { manager.cancelImageRequest(request) }
        }
        val metadata = if (options.readsMediaMetadata) withContext(Dispatchers.Default) {
            try {
                val resource = PHAssetResource.assetResourcesForAsset(asset).filterIsInstance<PHAssetResource>()
                    .let { resources -> resources.firstOrNull { it.type == PHAssetResourceTypePhoto } ?: resources.firstOrNull() }
                val date = asset.creationDate?.let {
                    NSDateFormatter().apply {
                        locale = NSLocale("en_US_POSIX")
                        dateFormat = "yyyy-MM-dd HH:mm:ss ZZZZZ"
                    }.stringFromDate(it)
                }
                val coordinates = asset.location?.coordinate?.useContents { PhotoCoordinates(latitude, longitude) }
                galleryImageMetadata(readGalleryImageProperties(data), resource?.originalFilename, data.length.toLong(),
                    asset.pixelWidth.toInt(), asset.pixelHeight.toInt(), date, coordinates)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Optional metadata must not prevent the image itself from being displayed.
                null
            }
        } else null
        val bytes = withContext(Dispatchers.Default) {
            val bitmapLimit = minOf(
                options.maxBitmapSize.width.pxOrElse { 4096 },
                options.maxBitmapSize.height.pxOrElse { 4096 },
            )
            val requestedLimit = maxOf(
                options.size.width.pxOrElse { bitmapLimit },
                options.size.height.pxOrElse { bitmapLimit },
            ).coerceIn(1, bitmapLimit)
            val encoded = galleryImageDataForDecoder(data, type, requestedLimit)
            check(encoded.length <= Int.MAX_VALUE.toULong()) { "Photo is too large to decode" }
            ByteArray(encoded.length.toInt()).also { result ->
                if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), encoded.bytes, encoded.length) }
            }
        }
        SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem, metadata = metadata?.let(::MediaSourceMetadata)),
            mimeType = null,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            galleryAssetIdentifier(data.toString())?.let { GalleryAssetFetcher(it, options) }
    }

    companion object {
        private val requests = Semaphore(2)
    }
}

/** Fetcher-level downsampling must not reuse a small thumbnail as an original-size cache entry. */
internal class GalleryAssetKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? =
        galleryAssetIdentifier(data.toString())?.let { "$data:${options.size}:${options.maxBitmapSize}" }
}

/** Skia cannot decode HEIC. UIKit handles it without creating a permanent photo copy. */
internal fun galleryImageDataForDecoder(data: NSData, type: String?, maxPixelSize: Int = 4096): NSData {
    // Preserve animation and EXIF for formats the existing decoder already supports.
    if (type in setOf("public.jpeg", "public.png", "com.compuserve.gif", "org.webmproject.webp")) return data
    require(maxPixelSize > 0)
    // ImageIO downsamples during decoding, avoiding a full-resolution UIImage and render surface.
    val retainedData = CFBridgingRetain(data) ?: error("Unable to read photo")
    try {
        return memScoped {
            val settings = CFDictionaryCreateMutable(null, 0, null, null) ?: error("Unable to configure decoder")
            val pixels = alloc<IntVar> { value = maxPixelSize }
            val limit = CFNumberCreate(null, kCFNumberIntType, pixels.ptr) ?: error("Unable to configure image size")
            try {
                CFDictionaryAddValue(settings, kCGImageSourceShouldCache, kCFBooleanFalse)
                CFDictionaryAddValue(settings, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                CFDictionaryAddValue(settings, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
                CFDictionaryAddValue(settings, kCGImageSourceThumbnailMaxPixelSize, limit)
                val source = CGImageSourceCreateWithData(retainedData.reinterpret(), settings)
                    ?: error("Unsupported photo format: $type")
                try {
                    val thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0u, settings)
                        ?: error("Unable to decode photo: $type")
                    try {
                        UIImagePNGRepresentation(UIImage.imageWithCGImage(thumbnail))
                            ?: error("Unable to encode photo: $type")
                    } finally {
                        CGImageRelease(thumbnail)
                    }
                } finally {
                    CFRelease(source)
                }
            } finally {
                CFRelease(limit)
                CFRelease(settings)
            }
        }
    } finally {
        CFRelease(retainedData)
    }
}

/** Read the original container without decoding pixels or retaining raw properties in the cache. */
internal fun readGalleryImageProperties(data: NSData): Map<*, *> {
    val retained = CFBridgingRetain(data) ?: return emptyMap<Any, Any>()
    try {
        val source = CGImageSourceCreateWithData(retained.reinterpret(), null) ?: return emptyMap<Any, Any>()
        try {
            val properties = CGImageSourceCopyPropertiesAtIndex(source, 0u, null) ?: return emptyMap<Any, Any>()
            return CFBridgingRelease(properties) as? Map<*, *> ?: emptyMap<Any, Any>()
        } finally { CFRelease(source) }
    } finally { CFRelease(retained) }
}
