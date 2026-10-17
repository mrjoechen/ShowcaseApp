package com.alpha.showcase.common

import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WebAvifDecoderTest {
    @Test fun decodesAndSamplesThroughCoilAndReusesMemoryCache() = runTest {
        val loader = loader()
        try {
            val request = ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(fixture(TWO_COLORS)).size(32, 24).memoryCacheKey("web-avif-test").build()
            val result = loader.execute(request)
            val first = assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
            assertEquals(32, first.image.width)
            assertEquals(24, first.image.height)
            assertTrue(first.isSampled)
            assertColor(first.image, 4, 12, RED)
            assertColor(first.image, 28, 12, BLUE)
            assertSame(first.image, assertIs<SuccessResult>(loader.execute(request)).image)
        } finally { loader.shutdown() }
    }

    @Test fun appliesContainerRotationAndMirror() = runTest {
        val loader = loader()
        try {
            val rotated = success(loader, ROTATED)
            assertEquals(48, rotated.image.width)
            assertEquals(64, rotated.image.height)
            assertColor(rotated.image, 24, 8, RED)
            assertColor(rotated.image, 24, 56, BLUE)

            // irot 3 with imir 0; corners verified independently with libavif.
            val transformed = success(loader, ORIENTATION_7)
            assertEquals(64, transformed.image.width)
            assertEquals(128, transformed.image.height)
            assertColor(transformed.image, 8, 8, YELLOW)
            assertColor(transformed.image, 55, 8, GREEN)
            assertColor(transformed.image, 8, 119, BLUE)
            assertColor(transformed.image, 55, 119, RED)
        } finally { loader.shutdown() }
    }

    @Test fun malformedAvifReturnsAnError() = runTest {
        val loader = loader()
        try {
            val valid = fixture(TWO_COLORS)
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(valid.copyOf(40)).build())
            assertIs<ErrorResult>(result)
            success(loader, TWO_COLORS)
        } finally { loader.shutdown() }
    }

    @Test fun recognizesAvifBrandsWithoutConsumingInput() {
        val input = Buffer().write(fixture(TWO_COLORS))
        val original = input.snapshot()
        assertTrue(WebAvifDecoder.isAvif(input))
        assertEquals(original, input.snapshot())
        assertFalse(WebHeicDecoder.isHeic(input))
        assertTrue(WebAvifDecoder.isAvif(Buffer().writeInt(24).writeUtf8("ftypmif1").writeInt(0).writeUtf8("mif1avif")))
        assertTrue(WebAvifDecoder.isAvif(Buffer().writeInt(16).writeUtf8("ftypavis").writeInt(0)))
    }

    @Test fun leavesOtherFormatsAndMalformedHeadersToOtherDecoders() {
        for (brand in listOf("heic", "mif1", "jpeg")) {
            assertFalse(WebAvifDecoder.isAvif(Buffer().writeInt(16).writeUtf8("ftyp$brand").writeInt(0)))
        }
        assertFalse(WebAvifDecoder.isAvif(Buffer().writeUtf8("GIF89a")))
        assertFalse(WebAvifDecoder.isAvif(Buffer().writeInt(64).writeUtf8("ftypavif").writeInt(0)))
    }

    private fun loader() = ImageLoader.Builder(PlatformContext.INSTANCE)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(1024 * 1024).build() }
        .components { addPlatformComponents() }.build()

    private fun fixture(base64: String) = requireNotNull(base64.decodeBase64()).toByteArray()

    private suspend fun success(loader: ImageLoader, base64: String): SuccessResult {
        val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(fixture(base64)).build())
        return assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
    }

    private fun assertColor(image: coil3.Image, x: Int, y: Int, expected: Int) {
        val actual = assertIs<BitmapImage>(image).bitmap.getColor(x, y)
        for (shift in listOf(0, 8, 16)) {
            assertTrue(abs((actual ushr shift and 255) - (expected ushr shift and 255)) < 25,
                "Unexpected pixel ${actual.toUInt().toString(16)} at ($x, $y)")
        }
    }

    private companion object {
        const val RED = 0xff0000
        const val GREEN = 0x00ff00
        const val BLUE = 0x0000ff
        const val YELLOW = 0xffff00

        // Same generated fixtures as desktopTest/resources/avif/.
        const val TWO_COLORS = "AAAAIGZ0eXBhdmlmAAAAAGF2aWZtaWYxbWlhZk1BMUIAAADrbWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAcGljdAAAAAAAAAAAAAAAAAAAAAAOcGl0bQAAAAAAAQAAAB5pbG9jAAAAAEQAAAEAAQAAAAEAAAETAAAALgAAAChpaW5mAAAAAAABAAAAGmluZmUCAAAAAAEAAGF2MDFDb2xvcgAAAABqaXBycAAAAEtpcGNvAAAAFGlzcGUAAAAAAAAAQAAAADAAAAAQcGl4aQAAAAADCAgIAAAADGF2MUOBAAwAAAAAE2NvbHJuY2x4AAEADQAGgAAAABdpcG1hAAAAAAAAAAEAAQQBAoMEAAAANm1kYXQSAAoGGBV/vYEIMiIRgAAAQLRVVTTbslSNnBtxu673TrDcZqEYdEV5T9x/AH9g"
        const val ROTATED = "AAAAIGZ0eXBhdmlmAAAAAGF2aWZtaWYxbWlhZk1BMUIAAAD1bWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAcGljdAAAAAAAAAAAAAAAAAAAAAAOcGl0bQAAAAAAAQAAAB5pbG9jAAAAAEQAAAEAAQAAAAEAAAEdAAAALgAAAChpaW5mAAAAAAABAAAAGmluZmUCAAAAAAEAAGF2MDFDb2xvcgAAAAB0aXBycAAAAFRpcGNvAAAAFGlzcGUAAAAAAAAAQAAAADAAAAAQcGl4aQAAAAADCAgIAAAADGF2MUOBAAwAAAAAE2NvbHJuY2x4AAEADQAGgAAAAAlpcm90AwAAABhpcG1hAAAAAAAAAAEAAQUBAoMEhQAAADZtZGF0EgAKBhgVf72BCDIiEYAAAEC0VVU027JUjZwbcbuu906w3GahGHRFeU/cfwB/YA=="
        const val ORIENTATION_7 = "AAAAIGZ0eXBhdmlmAAAAAGF2aWZtaWYxbWlhZk1BMUIAAAD/bWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAcGljdAAAAAAAAAAAAAAAAAAAAAAOcGl0bQAAAAAAAQAAAB5pbG9jAAAAAEQAAAEAAQAAAAEAAAEnAAAAQgAAAChpaW5mAAAAAAABAAAAGmluZmUCAAAAAAEAAGF2MDFDb2xvcgAAAAB+aXBycAAAAF1pcGNvAAAAFGlzcGUAAAAAAAAAgAAAAEAAAAAQcGl4aQAAAAADCAgIAAAADGF2MUOBAAwAAAAAE2NvbHJuY2x4AAEADQAGgAAAAAlpcm90AwAAAAlpbWlyAAAAABlpcG1hAAAAAAAAAAEAAQYBAoMEhYYAAABKbWRhdBIACgYYGX/+wIQyNhDAAAAgtFVVNNuyVI2cG3G7vD6urpCCwBICWm3xXJ3EP4F+40kl2CkbkfdjxJCmJxhPQigugA=="
    }
}
