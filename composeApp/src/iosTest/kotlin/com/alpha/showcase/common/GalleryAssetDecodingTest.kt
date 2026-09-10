@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common

import coil3.PlatformContext
import coil3.request.Options
import coil3.size.Size
import coil3.toUri

import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.useContents
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSMutableData
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIRectFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertSame
import kotlin.test.assertContentEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class GalleryAssetDecodingTest {
    @Test
    fun thumbnailCacheDoesNotReplaceFullSizePhoto() {
        val keyer = GalleryAssetKeyer()
        val uri = "phasset://photo/L0/001".toUri()
        val thumbnail = Options(PlatformContext.INSTANCE, size = Size(120, 120))
        val fullSize = thumbnail.copy(size = Size(1200, 1200))
        assertNotEquals(keyer.key(uri, thumbnail), keyer.key(uri, fullSize))
        assertNull(keyer.key("https://example.com/photo.jpg".toUri(), thumbnail))
    }

    @Test
    fun heicIsConvertedInMemoryIntoDecodableImageData() {
        val renderer = UIGraphicsImageRenderer(
            size = CGSizeMake(32.0, 24.0),
            format = UIGraphicsImageRendererFormat().apply { scale = 1.0 },
        )
        val original = renderer.imageWithActions {
            UIColor.redColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, 32.0, 24.0))
        }
        val encoded = NSMutableData()
        val retained = CFBridgingRetain(encoded)!!
        val type = CFStringCreateWithCString(null, "public.heic", kCFStringEncodingUTF8)!!
        try {
            val destination = assertNotNull(CGImageDestinationCreateWithData(retained.reinterpret(), type, 1u, null))
            try {
                CGImageDestinationAddImage(destination, assertNotNull(original.CGImage), null)
                assertTrue(CGImageDestinationFinalize(destination))
            } finally {
                CFRelease(destination)
            }
        } finally {
            CFRelease(type)
            CFRelease(retained)
        }
        val converted = galleryImageDataForDecoder(encoded, "public.heic", maxPixelSize = 16)
        assertTrue(converted.length > 0u)
        assertContentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10),
            converted.bytes!!.reinterpret<ByteVar>().readBytes(8))
        val decoded = assertNotNull(UIImage.imageWithData(converted))
        decoded.size.useContents {
            assertEquals(16.0, width)
            assertEquals(12.0, height)
        }
    }

    @Test
    fun gifBytesArePassedThroughToPreserveAnimation() {
        val data = NSMutableData()
        assertSame(data, galleryImageDataForDecoder(data, "com.compuserve.gif"))
    }
}
