package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.asPainter
import coil3.compose.LocalPlatformContext
import coil3.PlatformContext
import coil3.request.ImageRequest
import kotlin.math.max
import kotlin.math.roundToInt

// Keep enough detail when enlarged, while capping retained RGBA pixels at 64 KiB.
private const val BLUR_BACKGROUND_MAX_EDGE = 128

// Software drawing must be allowed on Android; other platforms need no request changes.
internal expect fun ImageRequest.Builder.prepareBlurSource()

@Composable
internal fun MediaBlurBackground(image: coil3.Image, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (width > 0 && height > 0 && image.width > 0 && image.height > 0 &&
            image.width.toLong() * height != image.height.toLong() * width) {
            // Rasterize only a tiny viewport. No second request, full-size copy or GPU blur layer.
            val scale = (BLUR_BACKGROUND_MAX_EDGE.toFloat() / max(width, height)).coerceAtMost(1f)
            val w = (width * scale).roundToInt().coerceIn(1, BLUR_BACKGROUND_MAX_EDGE)
            val h = (height * scale).roundToInt().coerceIn(1, BLUR_BACKGROUND_MAX_EDGE)
            val background = remember(image, context, w, h) { createMediaBlur(image, context, w, h) }
            Image(background, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        }
    }
}

/** Static first-frame background, scoped to the displayed image rather than a global cache. */
internal fun createMediaBlur(image: coil3.Image, context: PlatformContext, width: Int, height: Int): ImageBitmap {
    require(width in 1..BLUR_BACKGROUND_MAX_EDGE && height in 1..BLUR_BACKGROUND_MAX_EDGE)
    val bitmap = ImageBitmap(width, height)
    val canvas = Canvas(bitmap)
    val scope = CanvasDrawScope()
    val size = Size(width.toFloat(), height.toFloat())
    val painter = image.asPainter(context)
    val scale = max(width.toFloat() / image.width, height.toFloat() / image.height)
    val cropSize = Size(image.width * scale, image.height * scale)
    scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
        translate((width - cropSize.width) / 2, (height - cropSize.height) / 2) {
            with(painter) { draw(cropSize) }
        }
    }
    var pixels = IntArray(width * height)
    var scratch = IntArray(pixels.size)
    bitmap.readPixels(pixels)
    // A small radius preserves recognizable outlines in the enlarged background.
    // Two separable box passes approximate a Gaussian. Clamped edges avoid dark borders.
    val radius = 2
    val sampleCount = radius * 2 + 1
    repeat(2) {
        for (horizontal in listOf(true, false)) {
            for (y in 0 until height) for (x in 0 until width) {
                var a = 0; var r = 0; var g = 0; var b = 0
                for (delta in -radius..radius) {
                    val sx = if (horizontal) (x + delta).coerceIn(0, width - 1) else x
                    val sy = if (horizontal) y else (y + delta).coerceIn(0, height - 1)
                    val pixel = pixels[sy * width + sx]
                    a += pixel ushr 24
                    r += (pixel ushr 16) and 255
                    g += (pixel ushr 8) and 255
                    b += pixel and 255
                }
                scratch[y * width + x] = ((a / sampleCount) shl 24) or ((r / sampleCount) shl 16) or
                    ((g / sampleCount) shl 8) or (b / sampleCount)
            }
            val swap = pixels; pixels = scratch; scratch = swap
        }
    }
    scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
        for (y in 0 until height) for (x in 0 until width) {
            drawRect(Color(pixels[y * width + x]), Offset(x.toFloat(), y.toFloat()), Size(1f, 1f),
                blendMode = androidx.compose.ui.graphics.BlendMode.Src)
        }
    }
    return bitmap
}
