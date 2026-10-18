package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.alpha.showcase.common.ui.ext.getSimpleMessage
import com.alpha.showcase.common.ui.play.mediaLoadFailureMessage
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImageDecodeFailuresTest {
    @Test fun undecodableBytesAreReportedAsADecodeFailure() = runTest {
        val loader = loader()
        try {
            val error = assertIs<ErrorResult>(execute(loader, "not an image".encodeToByteArray())).throwable
            assertTrue(error.isImageDecodeFailure(), error.toString())
            assertEquals(UNDECODABLE, mediaLoadFailureMessage(error, UNDECODABLE))
        } finally { loader.shutdown() }
    }

    @Test fun decodersStillProduceImagesThroughTheReporter() = runTest {
        val loader = loader()
        try {
            val png = Bitmap().use { bitmap ->
                check(bitmap.allocN32Pixels(3, 2))
                Image.makeFromBitmap(bitmap).use { checkNotNull(it.encodeToData(EncodedImageFormat.PNG)).bytes }
            }
            assertEquals(3, assertIs<SuccessResult>(execute(loader, png)).image.width)
            val heic = checkNotNull(javaClass.getResourceAsStream("/heic/two-colors.heic")).use { it.readBytes() }
            assertEquals(64, assertIs<SuccessResult>(execute(loader, heic)).image.width)
        } finally { loader.shutdown() }
    }

    @Test fun loadFailuresKeepTheirOwnMessage() = runTest {
        val loader = loader()
        try {
            val error = assertIs<ErrorResult>(execute(loader, "/definitely/missing/photo.jpg".toPath())).throwable
            assertFalse(error.isImageDecodeFailure(), error.toString())
            assertEquals(error.getSimpleMessage(), mediaLoadFailureMessage(error, UNDECODABLE))
        } finally { loader.shutdown() }
    }

    @Test fun missingDecoderCountsAsADecodeFailure() {
        assertTrue(IllegalStateException("Unable to create a decoder that supports: photo.avif").isImageDecodeFailure())
        assertTrue(RuntimeException("wrapped", ImageDecodeException(IllegalArgumentException("bad"))).isImageDecodeFailure())
        assertFalse(IllegalStateException("Photo is no longer accessible").isImageDecodeFailure())
    }

    private fun loader() = ImageLoader.Builder(PlatformContext.INSTANCE)
        .components {
            add(DecodeFailureReporter())
            addPlatformComponents()
        }
        .memoryCache(null).diskCache(null).build()

    private suspend fun execute(loader: ImageLoader, data: Any) =
        loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(data).build())

    private companion object {
        const val UNDECODABLE = "Unable to display this image"
    }
}
