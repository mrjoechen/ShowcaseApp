package com.alpha.showcase.common.ai

import coil3.Image
import coil3.BitmapImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.impl.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

internal actual suspend fun encodeAiImage(image: Image, maxBytes: Long, maxEdge: Int): EncodedAiImage =
    withContext(if (image.shareable) Dispatchers.Default else Dispatchers.Main.immediate) {
        require(image.width > 0 && image.height > 0 && maxBytes > 0 && maxEdge > 0)
        var edge = maxEdge
        while (edge >= minOf(192, maxEdge)) {
            coroutineContext.ensureActive()
            val ratio = minOf(1.0, edge.toDouble() / maxOf(image.width, image.height))
            val width = (image.width * ratio).roundToInt().coerceAtLeast(1)
            val height = (image.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap().use { bitmap ->
                check(bitmap.allocN32Pixels(width, height))
                Canvas(bitmap).use { canvas ->
                    canvas.clear(org.jetbrains.skia.Color.WHITE)
                    if (image is BitmapImage) {
                        // Coil's BitmapImage.draw uses writePixels, which ignores the canvas matrix
                        // and clips to the top-left pixels instead of resizing the complete image.
                        org.jetbrains.skia.Image.makeFromBitmap(image.bitmap).use { source ->
                            canvas.drawImageRect(source,
                                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                                Rect.makeWH(width.toFloat(), height.toFloat()),
                                SamplingMode.LINEAR, null, true)
                        }
                    } else {
                        canvas.scale(width.toFloat() / image.width, height.toFloat() / image.height)
                        image.draw(canvas)
                    }
                }
                org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { snapshot ->
                    for (quality in listOf(85, 75, 65, 55)) {
                        snapshot.encodeToData(EncodedImageFormat.JPEG, quality)?.use { data ->
                            if (data.size <= maxBytes) return@withContext EncodedAiImage(data.bytes)
                        }
                    }
                }
            }
            edge = (edge * 0.75).toInt()
        }
        error("Displayed image exceeds the provider input limit")
    }
