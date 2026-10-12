@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.alpha.showcase.common.ui.settings

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import platform.CoreGraphics.CGImageRelease
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/** Keep the native QR at one pixel per module; the UI scales it without interpolation. */
internal fun donationQrCode(url: String): ImageBitmap {
    val message = url.encodeToByteArray()
    val data = message.usePinned {
        NSData.create(bytes = it.addressOf(0), length = message.size.toULong())
    }
    val filter = checkNotNull(
        CIFilter.filterWithName(
            name = "CIQRCodeGenerator",
            withInputParameters = mapOf("inputMessage" to data, "inputCorrectionLevel" to "M"),
        )
    ) { "Unable to create donation QR filter" }
    val output = checkNotNull(filter.outputImage) { "Unable to generate donation QR code" }
    val image = checkNotNull(CIContext.contextWithOptions(null).createCGImage(output, output.extent)) {
        "Unable to render donation QR code"
    }
    try {
        val png = checkNotNull(UIImagePNGRepresentation(UIImage.imageWithCGImage(image))) {
            "Unable to encode donation QR code"
        }
        val bytes = ByteArray(png.length.toInt())
        bytes.usePinned { memcpy(it.addressOf(0), png.bytes, png.length) }
        return Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
    } finally {
        CGImageRelease(image)
    }
}
