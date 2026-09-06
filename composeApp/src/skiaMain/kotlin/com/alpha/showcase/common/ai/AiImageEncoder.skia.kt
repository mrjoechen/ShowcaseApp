package com.alpha.showcase.common.ai

import coil3.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.impl.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

internal actual suspend fun encodeAiImage(image: Image, maxBytes: Long): EncodedAiImage =
    withContext(if (image.shareable) Dispatchers.Default else Dispatchers.Main.immediate) {
        require(image.width > 0 && image.height > 0 && maxBytes > 0)
        var edge = 1536
        while (edge >= 192) {
            coroutineContext.ensureActive()
            val ratio = minOf(1.0, edge.toDouble() / maxOf(image.width, image.height))
            val width = (image.width * ratio).roundToInt().coerceAtLeast(1)
            val height = (image.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap().use { bitmap ->
                check(bitmap.allocN32Pixels(width, height))
                Canvas(bitmap).use { canvas ->
                    canvas.clear(org.jetbrains.skia.Color.WHITE)
                    canvas.scale(width.toFloat() / image.width, height.toFloat() / image.height)
                    image.draw(canvas)
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
