@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common

import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.test.runTest
import okio.Buffer
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
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIRectFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleHeifDecoderTest {
    @Test fun decodesHeicThroughTheIosImageLoader() = runTest {
        val loader = loader()
        try {
            val image = success(loader, twoColorImage()).image
            assertEquals(64, image.width)
            assertEquals(48, image.height)
            assertColor(image, 8, 24, red = true)
            assertColor(image, 56, 24, red = false)
        } finally { loader.shutdown() }
    }

    @Test fun appliesOrientation() = runTest {
        val loader = loader()
        try {
            // Orientation 6 rotates 90 degrees clockwise: the red left half ends up on top.
            val image = success(loader, twoColorImage(orientation = 6)).image
            assertEquals(48, image.width)
            assertEquals(64, image.height)
            assertColor(image, 24, 8, red = true)
            assertColor(image, 24, 56, red = false)
        } finally { loader.shutdown() }
    }

    @Test fun downsamplesToTheRequestedSize() = runTest {
        val loader = loader()
        try {
            val result = success(loader, twoColorImage()) { size(32, 24) }
            assertEquals(32, result.image.width)
            assertEquals(24, result.image.height)
            assertColor(result.image, 4, 12, red = true)
            assertColor(result.image, 28, 12, red = false)
        } finally { loader.shutdown() }
    }

    @Test fun malformedHeicReturnsAnErrorAndDoesNotBlockTheNextDecode() = runTest {
        val loader = loader()
        try {
            val valid = twoColorImage()
            assertIs<ErrorResult>(loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(valid.copyOf(64)).build()))
            success(loader, valid)
        } finally { loader.shutdown() }
    }

    @Test fun decodesAvifThroughTheIosImageLoader() = runTest {
        val loader = loader()
        try {
            val image = success(loader, twoColorImage(AVIF)).image
            assertEquals(64, image.width)
            assertEquals(48, image.height)
            assertColor(image, 8, 24, red = true)
            assertColor(image, 56, 24, red = false)
            // ImageIO's AVIF encoder ignores Orientation, so write the container rotation directly.
            val rotated = success(loader, withRotation(twoColorImage(AVIF), quarterTurnsCounterClockwise = 3)).image
            assertEquals(48, rotated.width)
            assertEquals(64, rotated.height)
            assertColor(rotated, 24, 8, red = true)
        } finally { loader.shutdown() }
    }

    @Test fun recognizesOnlyHeifBrands() {
        assertTrue(AppleHeifDecoder.isHeif(Buffer().write(twoColorImage())))
        assertTrue(AppleHeifDecoder.isHeif(Buffer().write(twoColorImage(AVIF))))
        assertTrue(AppleHeifDecoder.isHeif(header("mif1", "heic")))
        assertTrue(AppleHeifDecoder.isHeif(header("avif", "mif1")))
        assertFalse(AppleHeifDecoder.isHeif(header("isom", "mp41")))
        assertFalse(AppleHeifDecoder.isHeif(Buffer().writeUtf8("GIF89a")))
    }

    private fun loader() = ImageLoader.Builder(PlatformContext.INSTANCE)
        .components { addPlatformComponents() }
        .memoryCache { MemoryCache.Builder().maxSizeBytes(1024 * 1024).build() }.build()

    private suspend fun success(
        loader: ImageLoader, bytes: ByteArray, configure: ImageRequest.Builder.() -> Unit = {},
    ): SuccessResult {
        val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(bytes).apply(configure).build()
        val result = loader.execute(request)
        return assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
    }

    private fun header(major: String, compatible: String) = Buffer()
        .writeInt(20).writeUtf8("ftyp").writeUtf8(major).writeInt(0).writeUtf8(compatible)

    /** 64x48 image encoded by ImageIO as [type]: red left half, blue right half. */
    private fun twoColorImage(type: String = HEIC, orientation: Int = 1): ByteArray {
        val renderer = UIGraphicsImageRenderer(
            size = CGSizeMake(64.0, 48.0),
            format = UIGraphicsImageRendererFormat().apply { scale = 1.0 },
        )
        val original = renderer.imageWithActions {
            UIColor.redColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, 32.0, 48.0))
            UIColor.blueColor.setFill()
            UIRectFill(CGRectMake(32.0, 0.0, 32.0, 48.0))
        }
        val encoded = NSMutableData()
        val retained = CFBridgingRetain(encoded)!!
        val uti = CFStringCreateWithCString(null, type, kCFStringEncodingUTF8)!!
        try {
            val destination = CGImageDestinationCreateWithData(retained.reinterpret(), uti, 1u, null)!!
            try {
                val properties = CFBridgingRetain(mapOf("Orientation" to orientation))!!
                try { CGImageDestinationAddImage(destination, original.CGImage, properties.reinterpret()) }
                finally { CFRelease(properties) }
                assertTrue(CGImageDestinationFinalize(destination))
            } finally {
                CFRelease(destination)
            }
        } finally {
            CFRelease(uti)
            CFRelease(retained)
        }
        return encoded.bytes!!.reinterpret<ByteVar>().readBytes(encoded.length.toInt())
    }

    /** Sets the ISO BMFF `irot` angle, which display code applies counterclockwise. */
    private fun withRotation(bytes: ByteArray, quarterTurnsCounterClockwise: Int): ByteArray {
        val irot = "irot".encodeToByteArray()
        val offset = (0..bytes.size - 5).single { index -> irot.indices.all { bytes[index + it] == irot[it] } }
        return bytes.copyOf().also { it[offset + 4] = quarterTurnsCounterClockwise.toByte() }
    }

    private fun assertColor(image: coil3.Image, x: Int, y: Int, red: Boolean) {
        val color = assertIs<BitmapImage>(image).bitmap.getColor(x, y)
        val r = color ushr 16 and 255
        val g = color ushr 8 and 255
        val b = color and 255
        assertTrue(g < 40 && if (red) r > 210 && b < 40 else b > 210 && r < 40,
            "Unexpected pixel ${color.toUInt().toString(16)} at ($x, $y)")
    }

    private companion object {
        const val HEIC = "public.heic"
        const val AVIF = "public.avif"
    }
}
