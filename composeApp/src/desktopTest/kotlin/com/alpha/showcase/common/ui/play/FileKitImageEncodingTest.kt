package com.alpha.showcase.common.ui.play

import androidx.compose.ui.graphics.asComposeImageBitmap
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.dialogs.compose.util.encodeToByteArray
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileKitImageEncodingTest {
    @Test
    fun fileKitEncodesPngAndJpegWithTheResolvedSkikoRuntime() = runTest {
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(8, 6)
            bitmap.erase(0xff4488cc.toInt())
            for (format in listOf(ImageFormat.PNG, ImageFormat.JPEG)) {
                val bytes = bitmap.asComposeImageBitmap().encodeToByteArray(format)
                assertTrue(bytes.isNotEmpty(), "FileKit must produce $format bytes")
                Image.makeFromEncoded(bytes).use { decoded ->
                    assertEquals(8, decoded.width)
                    assertEquals(6, decoded.height)
                }
            }
        }
    }
}
