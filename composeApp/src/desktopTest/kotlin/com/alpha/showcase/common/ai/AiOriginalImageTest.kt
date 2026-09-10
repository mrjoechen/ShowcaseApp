package com.alpha.showcase.common.ai

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import com.alpha.showcase.common.ui.ai.AiGenerationInput
import com.alpha.showcase.common.ui.ai.readAiOriginal
import com.alpha.showcase.common.ui.play.DataWithType
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.*

class AiOriginalImageTest {
    @Test fun archiveReadsExactOriginalBytesInsteadOfTheDecodedPreview() = runBlocking {
        val original = Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(180, 120); bitmap.erase(0xffabcdef.toInt())
            org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
            }
        }
        val file = File.createTempFile("ai-original-test-", ".png")
        val thumbnail = Bitmap().apply { allocN32Pixels(30, 20); erase(0xff123456.toInt()) }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            file.writeBytes(original)
            val archived = readAiOriginal(AiGenerationInput(thumbnail.asImage(), DataWithType(file.absolutePath, "png"), "Photo.png"), loader, PlatformContext.INSTANCE)
            assertNotNull(archived)
            assertEquals("png", archived.extension)
            assertContentEquals(original, archived.bytes)
            assertNull(readAiOriginal(AiGenerationInput(thumbnail.asImage()), loader, PlatformContext.INSTANCE))
        } finally {
            loader.shutdown()
            thumbnail.close()
            file.delete()
        }
    }
}
