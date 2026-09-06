package com.alpha.showcase.common.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import coil3.BitmapImage
import android.os.Build
import coil3.Image
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

internal actual suspend fun encodeAiImage(image: Image, maxBytes: Long, maxEdge: Int): EncodedAiImage =
    withContext(if (image.shareable) Dispatchers.Default else Dispatchers.Main.immediate) {
        require(image.width > 0 && image.height > 0 && maxBytes > 0 && maxEdge > 0)
        val hardwareCopy = if (Build.VERSION.SDK_INT >= 26 && image is BitmapImage && image.bitmap.config == Bitmap.Config.HARDWARE) {
            checkNotNull(image.bitmap.copy(Bitmap.Config.ARGB_8888, false))
        } else null
        try {
        var edge = maxEdge
        while (edge >= minOf(192, maxEdge)) {
            coroutineContext.ensureActive()
            val ratio = minOf(1.0, edge.toDouble() / maxOf(image.width, image.height))
            val width = (image.width * ratio).roundToInt().coerceAtLeast(1)
            val height = (image.height * ratio).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.scale(width.toFloat() / image.width, height.toFloat() / image.height)
                if (hardwareCopy != null) canvas.drawBitmap(hardwareCopy, 0f, 0f, null) else image.draw(canvas)
                for (quality in listOf(85, 75, 65, 55)) {
                    val output = ByteArrayOutputStream()
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                    val bytes = output.toByteArray()
                    if (bytes.size <= maxBytes) return@withContext EncodedAiImage(bytes)
                }
            } finally { bitmap.recycle() }
            edge = (edge * 0.75).toInt()
        }
        error("Displayed image exceeds the provider input limit")
        } finally { hardwareCopy?.recycle() }
    }
